package com.nutrinight.repository;

import com.nutrinight.entity.Recommendation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    @EntityGraph(attributePaths = {"sourceFoodItem", "alternativeFoodItem"})
    List<Recommendation> findBySourceFoodItemIdOrderByImprovementScoreDesc(Long sourceFoodItemId);

    @EntityGraph(attributePaths = {"sourceFoodItem", "alternativeFoodItem"})
    List<Recommendation> findAllByOrderBySourceFoodItemNameAscImprovementScoreDesc();

    @Modifying
    @Query("delete from Recommendation r")
    void deleteAllInBulk();
}
