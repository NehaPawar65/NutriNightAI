package com.nutrinight.repository;

import com.nutrinight.entity.FoodOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FoodOrderRepository extends JpaRepository<FoodOrder, Long>, JpaSpecificationExecutor<FoodOrder> {

    @Override
    @EntityGraph(attributePaths = "foodItem")
    Page<FoodOrder> findAll(Specification<FoodOrder> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"foodItem", "foodItem.nutritionSource"})
    Optional<FoodOrder> findWithFoodItemById(Long id);

    @Query("select o.externalOrderId from FoodOrder o where o.externalOrderId in :ids")
    List<String> findExistingExternalIds(Collection<String> ids);
}
