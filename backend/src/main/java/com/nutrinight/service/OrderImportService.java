package com.nutrinight.service;

import com.nutrinight.client.AiServiceClient;
import com.nutrinight.config.NutriNightProperties;
import com.nutrinight.domain.CategorySource;
import com.nutrinight.domain.FoodCategory;
import com.nutrinight.domain.OrderTimeResolver;
import com.nutrinight.dto.ImportResultDto;
import com.nutrinight.dto.ImportResultDto.PreviewRow;
import com.nutrinight.entity.FoodItem;
import com.nutrinight.entity.ImportBatch;
import com.nutrinight.exception.AiServiceException;
import com.nutrinight.exception.AiServiceUnavailableException;
import com.nutrinight.exception.InvalidRequestException;
import com.nutrinight.repository.FoodItemRepository;
import com.nutrinight.repository.FoodOrderRepository;
import com.nutrinight.service.OrderImportWriter.NewFoodItem;
import com.nutrinight.service.csv.CsvOrderParser;
import com.nutrinight.service.csv.CsvOrderParser.ParseResult;
import com.nutrinight.service.csv.CsvOrderParser.ParsedOrderRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates a CSV import: file checks, parsing/validation, de-duplication, AI classification of
 * uncategorised new food items, and the transactional write.
 */
@Service
public class OrderImportService {

    private static final Logger log = LoggerFactory.getLogger(OrderImportService.class);
    private static final int ID_LOOKUP_CHUNK = 1000;

    private final NutriNightProperties properties;
    private final OrderTimeResolver timeResolver;
    private final FoodOrderRepository orderRepository;
    private final FoodItemRepository foodItemRepository;
    private final OrderImportWriter writer;
    private final AiServiceClient aiClient;
    private final RecommendationService recommendationService;

    public OrderImportService(NutriNightProperties properties, OrderTimeResolver timeResolver,
                              FoodOrderRepository orderRepository, FoodItemRepository foodItemRepository,
                              OrderImportWriter writer, AiServiceClient aiClient,
                              RecommendationService recommendationService) {
        this.properties = properties;
        this.timeResolver = timeResolver;
        this.orderRepository = orderRepository;
        this.foodItemRepository = foodItemRepository;
        this.writer = writer;
        this.aiClient = aiClient;
        this.recommendationService = recommendationService;
    }

    public ImportResultDto importCsv(MultipartFile file, boolean dryRun) {
        String fileName = validateFile(file);
        NutriNightProperties.Upload upload = properties.upload();
        CsvOrderParser parser = new CsvOrderParser(timeResolver, upload.maxRows(), upload.maxReportedErrors());

        ParseResult parsed;
        try (InputStream in = file.getInputStream()) {
            parsed = parser.parse(in);
        } catch (IOException e) {
            throw new InvalidRequestException("The uploaded file could not be read");
        }
        List<String> warnings = new ArrayList<>(parsed.warnings());

        Set<String> existingIds = findExistingOrderIds(parsed.validRows());
        List<ParsedOrderRow> rows = parsed.validRows().stream()
                .filter(r -> !existingIds.contains(r.externalOrderId()))
                .toList();
        int duplicatesInDb = parsed.validRows().size() - rows.size();

        // Resolve food items: existing ones are reused (their stored nutrition is kept), new ones are created.
        Map<String, ParsedOrderRow> firstRowByItem = new LinkedHashMap<>();
        rows.forEach(r -> firstRowByItem.putIfAbsent(FoodItem.normalize(r.foodName()), r));
        Map<String, FoodItem> existingItems = firstRowByItem.isEmpty() ? Map.of() :
                foodItemRepository.findByNormalizedNameIn(firstRowByItem.keySet()).stream()
                        .collect(Collectors.toMap(FoodItem::getNormalizedName, Function.identity()));
        reportNutritionConflicts(rows, existingItems, warnings);

        Map<String, NewFoodItem> newItems = new LinkedHashMap<>();
        List<String> needClassification = new ArrayList<>();
        for (Map.Entry<String, ParsedOrderRow> e : firstRowByItem.entrySet()) {
            if (existingItems.containsKey(e.getKey())) {
                continue;
            }
            FoodCategory provided = rows.stream()
                    .filter(r -> FoodItem.normalize(r.foodName()).equals(e.getKey()) && r.category() != null)
                    .map(ParsedOrderRow::category).findFirst().orElse(null);
            if (provided != null) {
                newItems.put(e.getKey(), new NewFoodItem(e.getValue(), provided, CategorySource.PROVIDED));
            } else {
                needClassification.add(e.getKey());
            }
        }

        int classifiedByAi = 0;
        if (!needClassification.isEmpty()) {
            if (dryRun) {
                warnings.add(needClassification.size() + " new food item(s) have no category and will be classified by the AI service on import");
            } else {
                classifiedByAi = classify(needClassification, firstRowByItem, newItems, warnings);
            }
        }

        int lateNightRows = (int) rows.stream().filter(r -> r.time().lateNight()).count();
        List<PreviewRow> preview = rows.stream().limit(upload.previewRows())
                .map(r -> toPreview(r, existingItems, newItems))
                .toList();

        if (dryRun || rows.isEmpty()) {
            if (!dryRun) {
                warnings.add("No new valid rows to import");
            }
            return new ImportResultDto(dryRun, fileName, null, parsed.totalRows(), parsed.validRows().size(),
                    parsed.invalidRows(), parsed.duplicatesInFile(), duplicatesInDb, 0, lateNightRows,
                    dryRun ? firstRowByItem.size() - existingItems.size() : 0, 0, parsed.errors(), warnings, preview);
        }

        ImportBatch batch = writer.write(fileName, parsed.totalRows(), parsed.invalidRows(),
                parsed.duplicatesInFile() + duplicatesInDb, existingItems, newItems, rows);
        log.info("Import batch {} stored {} orders ({} late-night), {} new food items",
                batch.getId(), rows.size(), lateNightRows, newItems.size());

        if (!newItems.isEmpty()) {
            recommendationService.refreshAll();
        }
        return new ImportResultDto(false, fileName, batch.getId(), parsed.totalRows(), parsed.validRows().size(),
                parsed.invalidRows(), parsed.duplicatesInFile(), duplicatesInDb, rows.size(), lateNightRows,
                newItems.size(), classifiedByAi, parsed.errors(), warnings, preview);
    }

    private String validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("Please choose a non-empty CSV file to upload");
        }
        if (file.getSize() > properties.upload().maxFileSizeBytes()) {
            throw new InvalidRequestException("The file exceeds the maximum size of "
                    + properties.upload().maxFileSizeBytes() / (1024 * 1024) + " MB");
        }
        String original = Objects.requireNonNullElse(file.getOriginalFilename(), "upload.csv");
        // Keep only the base name; never trust client-supplied paths.
        String name = original.replaceAll("^.*[\\\\/]", "");
        if (!name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new InvalidRequestException("Only .csv files are accepted");
        }
        String type = file.getContentType();
        if (type != null && !(type.startsWith("text/") || type.equals("application/vnd.ms-excel")
                || type.equals("application/csv") || type.equals("application/octet-stream"))) {
            throw new InvalidRequestException("Unsupported content type: " + type);
        }
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(4096);
            for (byte b : head) {
                if (b == 0) {
                    throw new InvalidRequestException("The file appears to be binary, not a text CSV");
                }
            }
        } catch (IOException e) {
            throw new InvalidRequestException("The uploaded file could not be read");
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    private Set<String> findExistingOrderIds(List<ParsedOrderRow> rows) {
        Set<String> existing = new HashSet<>();
        List<String> ids = rows.stream().map(ParsedOrderRow::externalOrderId).toList();
        for (int i = 0; i < ids.size(); i += ID_LOOKUP_CHUNK) {
            existing.addAll(orderRepository.findExistingExternalIds(ids.subList(i, Math.min(ids.size(), i + ID_LOOKUP_CHUNK))));
        }
        return existing;
    }

    private static void reportNutritionConflicts(List<ParsedOrderRow> rows, Map<String, FoodItem> existing,
                                                 List<String> warnings) {
        Set<String> reported = new HashSet<>();
        for (ParsedOrderRow r : rows) {
            FoodItem item = existing.get(FoodItem.normalize(r.foodName()));
            if (item == null || !r.hasAnyNutrient() || reported.contains(item.getNormalizedName())) {
                continue;
            }
            if (differs(r.caloriesKcal(), item.getCaloriesKcal()) || differs(r.sugarG(), item.getSugarG())
                    || differs(r.sodiumMg(), item.getSodiumMg()) || differs(r.saturatedFatG(), item.getSaturatedFatG())
                    || differs(r.proteinG(), item.getProteinG())) {
                reported.add(item.getNormalizedName());
                if (reported.size() <= 20) {
                    warnings.add("Nutrition values for '" + item.getName()
                            + "' differ from the stored record; the stored values were kept");
                }
            }
        }
    }

    private static boolean differs(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) != 0;
    }

    private int classify(List<String> names, Map<String, ParsedOrderRow> firstRows, Map<String, NewFoodItem> newItems,
                         List<String> warnings) {
        List<String> texts = names.stream().map(n -> firstRows.get(n).foodName()).toList();
        try {
            AiServiceClient.ClassifyResponse response = aiClient.classify(texts, "auto");
            int count = 0;
            for (int i = 0; i < names.size(); i++) {
                AiServiceClient.Prediction p = response.predictions().get(i);
                FoodCategory category = FoodCategory.parse(p.category()).orElse(FoodCategory.OTHER);
                CategorySource source = switch (p.method()) {
                    case "ML_MODEL" -> CategorySource.AI_ML_MODEL;
                    case "RULE_BASED" -> CategorySource.AI_RULE_BASED;
                    default -> CategorySource.DEFAULT;
                };
                if (source != CategorySource.DEFAULT) {
                    count++;
                }
                newItems.put(names.get(i), new NewFoodItem(firstRows.get(names.get(i)), category, source));
            }
            return count;
        } catch (AiServiceUnavailableException | AiServiceException | IndexOutOfBoundsException e) {
            log.warn("Classification of {} new food items failed; defaulting to OTHER: {}", names.size(), e.getMessage());
            warnings.add("AI classification was unavailable; " + names.size()
                    + " uncategorised food item(s) were stored as OTHER (category source: DEFAULT)");
            names.forEach(n -> newItems.put(n, new NewFoodItem(firstRows.get(n), FoodCategory.OTHER, CategorySource.DEFAULT)));
            return 0;
        }
    }

    private static PreviewRow toPreview(ParsedOrderRow r, Map<String, FoodItem> existing, Map<String, NewFoodItem> newItems) {
        String key = FoodItem.normalize(r.foodName());
        FoodItem item = existing.get(key);
        String category;
        if (item != null) {
            category = item.getCategory().name();
        } else if (newItems.containsKey(key)) {
            category = newItems.get(key).category().name();
        } else {
            category = r.category() == null ? "PENDING_CLASSIFICATION" : r.category().name();
        }
        return new PreviewRow(r.rowNumber(), r.time().localDateTime(), r.time().analysisDate(), r.time().lateNight(),
                r.foodName(), category, r.quantity(), r.city(), r.caloriesKcal(), r.sugarG(), r.sodiumMg(),
                r.saturatedFatG(), r.proteinG(), r.dataSource());
    }
}
