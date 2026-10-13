package com.nutrinight.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One anonymised food order line. Inserted in bulk by the CSV importer; read via JPA. */
@Entity
@Table(name = "food_orders")
public class FoodOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** One-way hash of the source order id. */
    @Column(name = "external_order_id", nullable = false, unique = true)
    private String externalOrderId;

    @Column(name = "order_timestamp", nullable = false)
    private Instant orderTimestamp;

    @Column(name = "local_date_time", nullable = false)
    private LocalDateTime localDateTime;

    @Column(name = "local_hour", nullable = false)
    private short localHour;

    @Column(name = "analysis_date", nullable = false)
    private LocalDate analysisDate;

    @Column(name = "is_late_night", nullable = false)
    private boolean lateNight;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "food_item_id")
    private FoodItem foodItem;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private String city;

    @Column(name = "data_source", nullable = false)
    private String dataSource;

    @Column(name = "import_batch_id")
    private Long importBatchId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected FoodOrder() {
    }

    public FoodOrder(String externalOrderId, Instant orderTimestamp, LocalDateTime localDateTime, int localHour,
                     LocalDate analysisDate, boolean lateNight, FoodItem foodItem, int quantity, String city,
                     String dataSource) {
        this.externalOrderId = externalOrderId;
        this.orderTimestamp = orderTimestamp;
        this.localDateTime = localDateTime;
        this.localHour = (short) localHour;
        this.analysisDate = analysisDate;
        this.lateNight = lateNight;
        this.foodItem = foodItem;
        this.quantity = quantity;
        this.city = city;
        this.dataSource = dataSource;
    }

    public Long getId() {
        return id;
    }

    public String getExternalOrderId() {
        return externalOrderId;
    }

    public Instant getOrderTimestamp() {
        return orderTimestamp;
    }

    public LocalDateTime getLocalDateTime() {
        return localDateTime;
    }

    public int getLocalHour() {
        return localHour;
    }

    public LocalDate getAnalysisDate() {
        return analysisDate;
    }

    public boolean isLateNight() {
        return lateNight;
    }

    public FoodItem getFoodItem() {
        return foodItem;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getCity() {
        return city;
    }

    public String getDataSource() {
        return dataSource;
    }

    public Long getImportBatchId() {
        return importBatchId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
