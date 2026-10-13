package com.nutrinight.repository;

import com.nutrinight.entity.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {

    List<ImportBatch> findTop10ByOrderByCreatedAtDesc();
}
