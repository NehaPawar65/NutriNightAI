package com.nutrinight.repository;

import com.nutrinight.entity.AnalysisResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {

    Page<AnalysisResult> findByAnalysisType(String analysisType, Pageable pageable);
}
