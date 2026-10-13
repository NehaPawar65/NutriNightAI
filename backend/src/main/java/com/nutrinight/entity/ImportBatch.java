package com.nutrinight.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "import_batches")
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "rows_total", nullable = false)
    private int rowsTotal;

    @Column(name = "rows_imported", nullable = false)
    private int rowsImported;

    @Column(name = "rows_invalid", nullable = false)
    private int rowsInvalid;

    @Column(name = "rows_duplicate", nullable = false)
    private int rowsDuplicate;

    @Column(name = "food_items_created", nullable = false)
    private int foodItemsCreated;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ImportBatch() {
    }

    public ImportBatch(String fileName, int rowsTotal, int rowsInvalid, int rowsDuplicate) {
        this.fileName = fileName;
        this.rowsTotal = rowsTotal;
        this.rowsInvalid = rowsInvalid;
        this.rowsDuplicate = rowsDuplicate;
    }

    public void complete(int rowsImported, int foodItemsCreated) {
        this.rowsImported = rowsImported;
        this.foodItemsCreated = foodItemsCreated;
    }

    public Long getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public int getRowsTotal() {
        return rowsTotal;
    }

    public int getRowsImported() {
        return rowsImported;
    }

    public int getRowsInvalid() {
        return rowsInvalid;
    }

    public int getRowsDuplicate() {
        return rowsDuplicate;
    }

    public int getFoodItemsCreated() {
        return foodItemsCreated;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
