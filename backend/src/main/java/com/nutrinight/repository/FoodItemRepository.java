package com.nutrinight.repository;

import com.nutrinight.entity.FoodItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface FoodItemRepository extends JpaRepository<FoodItem, Long> {

    List<FoodItem> findByNormalizedNameIn(Collection<String> normalizedNames);

    @EntityGraph(attributePaths = "nutritionSource")
    List<FoodItem> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = "nutritionSource")
    List<FoodItem> findByIdIn(Collection<Long> ids);
}
