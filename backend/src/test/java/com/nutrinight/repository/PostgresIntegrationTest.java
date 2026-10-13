package com.nutrinight.repository;

import com.nutrinight.client.AiServiceClient;
import com.nutrinight.dto.AnalyticsDtos;
import com.nutrinight.dto.ImportResultDto;
import com.nutrinight.dto.OrderFilter;
import com.nutrinight.entity.FoodOrder;
import com.nutrinight.service.AnalyticsService;
import com.nutrinight.service.OrderImportService;
import com.nutrinight.service.OrderService;
import com.nutrinight.service.RecommendationService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end persistence test against a real PostgreSQL (Testcontainers): Flyway migrations, the CSV import
 * transaction, JPA/SQL queries, late-night boundaries and analytics calculations.
 */
@Testcontainers
@SpringBootTest(properties = "nutrinight.privacy.min-group-size=3")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PostgresIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @MockitoBean AiServiceClient aiClient;

    @Autowired OrderImportService importService;
    @Autowired AnalyticsService analyticsService;
    @Autowired OrderService orderService;
    @Autowired FoodOrderRepository orderRepository;
    @Autowired FoodItemRepository foodItemRepository;
    @Autowired RecommendationService recommendationService;

    private static final String CSV = """
            order_id,order_time,food_item,food_category,quantity,serving_size_g,serving_unit,calories_kcal,sugar_g,sodium_mg,saturated_fat_g,protein_g,city,data_source,nutrition_source
            B1,2026-07-03T21:59:59,Pizza,FAST_FOOD,1,200,g,500,6,1000,10,20,Pune,TEST,Test source
            B2,2026-07-03T22:00:00,Pizza,FAST_FOOD,2,200,g,500,6,1000,10,20,Pune,TEST,Test source
            B3,2026-07-03T23:00:00,Cola,SUGARY_BEVERAGES,1,500,ml,210,53,20,0,0,Pune,TEST,Test source
            B4,2026-07-04T00:00:00,Cola,SUGARY_BEVERAGES,1,500,ml,210,53,20,0,0,Pune,TEST,Test source
            B5,2026-07-04T03:59:59,Momos,OTHER,1,200,g,340,,690,,18,Pune,TEST,Test source
            B6,2026-07-04T04:00:00,Khichdi,BALANCED_MEALS,1,350,g,380,4,650,2.5,14,Pune,TEST,Test source
            B7,2026-07-04T23:30:00,Pizza,FAST_FOOD,1,200,g,500,6,1000,10,20,Nashik,TEST,Test source
            B8,2026-07-03T18:30:00Z,Khichdi,BALANCED_MEALS,1,350,g,380,4,650,2.5,14,Pune,TEST,Test source
            """;

    private ImportResultDto imported;

    @BeforeAll
    void importData() {
        imported = importService.importCsv(new MockMultipartFile("file", "it.csv", "text/csv",
                CSV.getBytes(StandardCharsets.UTF_8)), false);
    }

    @Test
    @Order(1)
    void importStoresAllRowsInOneBatch() {
        assertThat(imported.importedRows()).isEqualTo(8);
        assertThat(imported.newFoodItems()).isEqualTo(4);
        assertThat(imported.batchId()).isNotNull();
        assertThat(orderRepository.count()).isEqualTo(8);
        assertThat(foodItemRepository.findAllByOrderByNameAsc())
                .allSatisfy(i -> assertThat(i.getNutritionSource().getSourceName()).isEqualTo("Test source"));
    }

    @Test
    @Order(2)
    void lateNightBoundariesAndAnalysisDatesArePersisted() {
        Map<LocalDateTime, FoodOrder> byTime = orderRepository.findAll().stream()
                .collect(Collectors.toMap(FoodOrder::getLocalDateTime, o -> o, (a, b) -> a));
        assertThat(byTime.get(LocalDateTime.parse("2026-07-03T21:59:59")).isLateNight()).isFalse();
        assertThat(byTime.get(LocalDateTime.parse("2026-07-03T22:00:00")).isLateNight()).isTrue();
        assertThat(byTime.get(LocalDateTime.parse("2026-07-04T00:00:00")).isLateNight()).isTrue();
        FoodOrder lastMinute = byTime.get(LocalDateTime.parse("2026-07-04T03:59:59"));
        assertThat(lastMinute.isLateNight()).isTrue();
        assertThat(lastMinute.getAnalysisDate()).isEqualTo(LocalDate.parse("2026-07-03"));
        FoodOrder fourAm = byTime.get(LocalDateTime.parse("2026-07-04T04:00:00"));
        assertThat(fourAm.isLateNight()).isFalse();
        assertThat(fourAm.getAnalysisDate()).isEqualTo(LocalDate.parse("2026-07-04"));
        // 18:30Z = 00:00 IST on 4 July -> late night, attributed to 3 July
        FoodOrder utc = orderRepository.findAll().stream()
                .filter(o -> o.getOrderTimestamp().toString().equals("2026-07-03T18:30:00Z")).findFirst().orElseThrow();
        assertThat(utc.getLocalDateTime()).isEqualTo(LocalDateTime.parse("2026-07-04T00:00:00"));
        assertThat(utc.isLateNight()).isTrue();
        // raw order ids are never stored
        assertThat(orderRepository.findAll()).allSatisfy(o -> assertThat(o.getExternalOrderId()).hasSize(64).doesNotStartWith("B"));
    }

    @Test
    @Order(3)
    void dateFilterUsesAnalysisDateSoAfterMidnightOrdersStayWithTheirEvening() {
        OrderFilter july3Night = OrderFilter.of(LocalDate.parse("2026-07-03"), LocalDate.parse("2026-07-03"), null, null, true);
        var page = orderRepository.findAll(OrderService.specification(july3Night), PageRequest.of(0, 50, Sort.by("orderTimestamp")));
        assertThat(page.getContent()).extracting(o -> o.getLocalDateTime().toString())
                .containsExactly("2026-07-03T22:00", "2026-07-03T23:00", "2026-07-04T00:00", "2026-07-04T00:00", "2026-07-04T03:59:59");
    }

    @Test
    @Order(4)
    void analyticsAreComputedFromStoredData() {
        AnalyticsDtos.Overview overview = analyticsService.overview(OrderFilter.of(null, null, null, null, false));
        assertThat(overview.totalOrders()).isEqualTo(8);
        assertThat(overview.lateNightOrders()).isEqualTo(6);
        assertThat(overview.lateNightPercentage()).isEqualTo(75.0);
        assertThat(overview.peakLateNightHour()).isZero();

        AnalyticsDtos.Nutrition nutrition = analyticsService.nutrition(OrderFilter.of(null, null, "pune", null, true));
        assertThat(nutrition.orderCount()).isEqualTo(5);
        var sugar = nutrition.nutrients().stream().filter(n -> n.nutrient().equals("SUGAR")).findFirst().orElseThrow();
        // pizza x2 servings (12 g) + cola + cola (106 g) + khichdi at 00:00 IST (4 g) + momos (missing)
        // = 122 g over 4 order lines with data
        assertThat(sugar.total()).isEqualByComparingTo("122.0");
        assertThat(sugar.ordersWithData()).isEqualTo(4);
        assertThat(sugar.ordersMissing()).isEqualTo(1);
        assertThat(sugar.meanPerOrder()).isEqualByComparingTo("30.50");

        AnalyticsDtos.Hourly hourly = analyticsService.hourly(OrderFilter.of(null, null, null, null, false));
        assertThat(hourly.hours()).hasSize(24);
        assertThat(hourly.hours().get(22).lateNight()).isTrue();
        assertThat(hourly.hours().get(4).lateNight()).isFalse();
        // 3 July 2026 is a Friday (weekday night); 4 July is a Saturday (weekend night)
        var weekend = hourly.dayTypes().stream().filter(d -> d.dayType().equals("WEEKEND")).findFirst().orElseThrow();
        assertThat(weekend.lateNightOrders()).isEqualTo(1);
    }

    @Test
    @Order(5)
    void smallCitiesAreSuppressed() {
        AnalyticsDtos.Geographic geo = analyticsService.geographic(OrderFilter.of(null, null, null, null, false));
        assertThat(geo.suppressedGroups()).isEqualTo(1);
        assertThat(geo.cities()).filteredOn(AnalyticsDtos.CityStats::suppressed).singleElement()
                .satisfies(c -> {
                    assertThat(c.city()).isEqualTo("Nashik");
                    assertThat(c.orders()).isNull();
                });
    }

    @Test
    @Order(6)
    void recommendationsUseStoredNutrition() {
        recommendationService.refreshAll();
        var pizzaId = foodItemRepository.findAllByOrderByNameAsc().stream().filter(i -> i.getName().equals("Pizza"))
                .findFirst().orElseThrow().getId();
        var groups = recommendationService.list(pizzaId);
        assertThat(groups).singleElement().satisfies(g -> assertThat(g.alternatives())
                .extracting(a -> a.comparison().alternative().name()).containsExactly("Khichdi"));
        var momos = recommendationService.list(null).stream().filter(g -> g.source().name().equals("Momos"))
                .findFirst().orElseThrow();
        assertThat(momos.sufficientData()).isFalse();
    }

    @Test
    @Order(7)
    void reimportIsIdempotent() {
        ImportResultDto again = importService.importCsv(new MockMultipartFile("file", "it.csv", "text/csv",
                CSV.getBytes(StandardCharsets.UTF_8)), false);
        assertThat(again.importedRows()).isZero();
        assertThat(again.duplicateRowsInDatabase()).isEqualTo(8);
        assertThat(orderRepository.count()).isEqualTo(8);
        List<String> ids = orderRepository.findExistingExternalIds(List.of("nope"));
        assertThat(ids).isEmpty();
    }
}
