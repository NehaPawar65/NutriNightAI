package com.nutrinight.service;

import com.nutrinight.TestFixtures;
import com.nutrinight.client.AiServiceClient;
import com.nutrinight.domain.CategorySource;
import com.nutrinight.domain.FoodCategory;
import com.nutrinight.domain.LateNightWindow;
import com.nutrinight.domain.OrderTimeResolver;
import com.nutrinight.dto.ImportResultDto;
import com.nutrinight.entity.ImportBatch;
import com.nutrinight.exception.AiServiceUnavailableException;
import com.nutrinight.exception.InvalidRequestException;
import com.nutrinight.repository.FoodItemRepository;
import com.nutrinight.repository.FoodOrderRepository;
import com.nutrinight.service.OrderImportWriter.NewFoodItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderImportServiceTest {

    private static final String CSV = """
            order_id,order_time,food_item,food_category,quantity,city
            A1,2026-07-01T23:00:00,Vada Pav,FRIED_SNACKS,1,Mumbai
            A2,2026-07-01T12:00:00,Mystery Dish,,1,Pune
            A3,2026-07-02T03:59:59,Vada Pav,,2,Mumbai
            A4,bad-time,Vada Pav,,1,Mumbai
            """;

    @Mock FoodOrderRepository orderRepository;
    @Mock FoodItemRepository foodItemRepository;
    @Mock OrderImportWriter writer;
    @Mock AiServiceClient aiClient;
    @Mock RecommendationService recommendationService;

    private OrderImportService service;

    @BeforeEach
    void setUp() {
        OrderTimeResolver resolver = new OrderTimeResolver(ZoneId.of("Asia/Kolkata"),
                new LateNightWindow(LocalTime.of(22, 0), LocalTime.of(4, 0)));
        service = new OrderImportService(TestFixtures.properties(), resolver, orderRepository, foodItemRepository,
                writer, aiClient, recommendationService);
    }

    private static MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("file", name, "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void dryRunValidatesWithoutWritingOrCallingAi() {
        when(orderRepository.findExistingExternalIds(anyList())).thenReturn(List.of());
        when(foodItemRepository.findByNormalizedNameIn(any())).thenReturn(List.of());

        ImportResultDto result = service.importCsv(file("orders.csv", CSV), true);

        assertThat(result.dryRun()).isTrue();
        assertThat(result.totalRows()).isEqualTo(4);
        assertThat(result.validRows()).isEqualTo(3);
        assertThat(result.invalidRows()).isEqualTo(1);
        assertThat(result.lateNightRows()).isEqualTo(2);
        assertThat(result.errors()).singleElement().satisfies(e -> {
            assertThat(e.row()).isEqualTo(5);
            assertThat(e.column()).isEqualTo("order_time");
        });
        assertThat(result.preview()).hasSize(3);
        assertThat(result.warnings()).anySatisfy(w -> assertThat(w).contains("will be classified"));
        verify(writer, never()).write(anyString(), anyInt(), anyInt(), anyInt(), anyMap(), anyMap(), anyList());
        verify(aiClient, never()).classify(anyList(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void classifiesUncategorisedItemsWithAi() {
        when(orderRepository.findExistingExternalIds(anyList())).thenReturn(List.of());
        when(foodItemRepository.findByNormalizedNameIn(any())).thenReturn(List.of());
        when(aiClient.classify(List.of("Mystery Dish"), "auto")).thenReturn(new AiServiceClient.ClassifyResponse(
                List.of(new AiServiceClient.Prediction("Mystery Dish", "BALANCED_MEALS", "ML_MODEL", 0.7, List.of(),
                        List.of(), List.of(), "x")), true, "v1"));
        when(writer.write(anyString(), anyInt(), anyInt(), anyInt(), anyMap(), anyMap(), anyList()))
                .thenReturn(new ImportBatch("orders.csv", 4, 1, 0));

        ImportResultDto result = service.importCsv(file("orders.csv", CSV), false);

        ArgumentCaptor<Map<String, NewFoodItem>> newItems = ArgumentCaptor.forClass(Map.class);
        verify(writer).write(eq("orders.csv"), eq(4), eq(1), eq(0), anyMap(), newItems.capture(), anyList());
        // "Vada Pav" keeps its provided category even though a later row omits it
        assertThat(newItems.getValue().get("vada pav").category()).isEqualTo(FoodCategory.FRIED_SNACKS);
        assertThat(newItems.getValue().get("vada pav").categorySource()).isEqualTo(CategorySource.PROVIDED);
        assertThat(newItems.getValue().get("mystery dish").category()).isEqualTo(FoodCategory.BALANCED_MEALS);
        assertThat(newItems.getValue().get("mystery dish").categorySource()).isEqualTo(CategorySource.AI_ML_MODEL);
        assertThat(result.importedRows()).isEqualTo(3);
        assertThat(result.foodItemsClassifiedByAi()).isEqualTo(1);
        verify(recommendationService).refreshAll();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aiOutageFallsBackToOtherWithWarning() {
        when(orderRepository.findExistingExternalIds(anyList())).thenReturn(List.of());
        when(foodItemRepository.findByNormalizedNameIn(any())).thenReturn(List.of());
        when(aiClient.classify(anyList(), anyString()))
                .thenThrow(new AiServiceUnavailableException("down", new RuntimeException()));
        when(writer.write(anyString(), anyInt(), anyInt(), anyInt(), anyMap(), anyMap(), anyList()))
                .thenReturn(new ImportBatch("orders.csv", 4, 1, 0));

        ImportResultDto result = service.importCsv(file("orders.csv", CSV), false);

        ArgumentCaptor<Map<String, NewFoodItem>> newItems = ArgumentCaptor.forClass(Map.class);
        verify(writer).write(anyString(), anyInt(), anyInt(), anyInt(), anyMap(), newItems.capture(), anyList());
        NewFoodItem mystery = newItems.getValue().get("mystery dish");
        assertThat(mystery.category()).isEqualTo(FoodCategory.OTHER);
        assertThat(mystery.categorySource()).isEqualTo(CategorySource.DEFAULT);
        assertThat(result.warnings()).anySatisfy(w -> assertThat(w).contains("AI classification was unavailable"));
        assertThat(result.importedRows()).isEqualTo(3);
    }

    @Test
    void rowsAlreadyInDatabaseAreSkipped() {
        when(orderRepository.findExistingExternalIds(anyList())).thenAnswer(inv -> inv.getArgument(0));

        ImportResultDto result = service.importCsv(file("orders.csv", CSV), false);

        assertThat(result.duplicateRowsInDatabase()).isEqualTo(3);
        assertThat(result.importedRows()).isZero();
        assertThat(result.warnings()).contains("No new valid rows to import");
        verify(writer, never()).write(anyString(), anyInt(), anyInt(), anyInt(), anyMap(), anyMap(), anyList());
    }

    @Test
    void rejectsBadFiles() {
        assertThatThrownBy(() -> service.importCsv(file("orders.xlsx", CSV), false))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining(".csv");
        assertThatThrownBy(() -> service.importCsv(file("empty.csv", ""), false))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("non-empty");
        MockMultipartFile binary = new MockMultipartFile("file", "x.csv", "text/csv", new byte[]{'a', 0, 'b'});
        assertThatThrownBy(() -> service.importCsv(binary, false))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("binary");
        MockMultipartFile image = new MockMultipartFile("file", "x.csv", "image/png", "a,b".getBytes());
        assertThatThrownBy(() -> service.importCsv(image, false))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("content type");
    }
}
