package com.nutrinight.repository;

import com.nutrinight.entity.NutritionSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NutritionSourceRepository extends JpaRepository<NutritionSource, Long> {

    Optional<NutritionSource> findBySourceName(String sourceName);
}
