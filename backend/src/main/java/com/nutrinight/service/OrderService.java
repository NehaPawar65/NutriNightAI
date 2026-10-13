package com.nutrinight.service;

import com.nutrinight.dto.OrderDto;
import com.nutrinight.dto.OrderFilter;
import com.nutrinight.dto.PageResponse;
import com.nutrinight.entity.FoodItem;
import com.nutrinight.entity.FoodOrder;
import com.nutrinight.exception.InvalidRequestException;
import com.nutrinight.exception.ResourceNotFoundException;
import com.nutrinight.repository.FoodOrderRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class OrderService {

    public static final int MAX_PAGE_SIZE = 200;
    /** API sort keys mapped to entity paths; anything else is rejected. */
    private static final Map<String, String> SORTABLE = Map.of(
            "orderTimestamp", "orderTimestamp",
            "quantity", "quantity",
            "city", "city",
            "localHour", "localHour",
            "foodItem", "foodItem.name");

    private final FoodOrderRepository orderRepository;

    public OrderService(FoodOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderDto> list(OrderFilter filter, int page, int size, String sort) {
        if (page < 0) {
            throw new InvalidRequestException("'page' must be 0 or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("'size' must be between 1 and " + MAX_PAGE_SIZE);
        }
        Pageable pageable = PageRequest.of(page, size, parseSort(sort));
        return PageResponse.of(orderRepository.findAll(specification(filter), pageable), OrderService::toDto);
    }

    @Transactional(readOnly = true)
    public OrderDto get(Long id) {
        return orderRepository.findWithFoodItemById(id).map(OrderService::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    @Transactional
    public void delete(Long id) {
        if (!orderRepository.existsById(id)) {
            throw new ResourceNotFoundException("Order", id);
        }
        orderRepository.deleteById(id);
    }

    static Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "orderTimestamp");
        }
        String[] parts = sort.split(",");
        String path = SORTABLE.get(parts[0].trim());
        if (path == null) {
            throw new InvalidRequestException("Unsupported sort field '" + parts[0].trim() + "'. Allowed: "
                    + String.join(", ", SORTABLE.keySet()));
        }
        Sort.Direction direction = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, path).and(Sort.by(Sort.Direction.DESC, "id"));
    }

    public static Specification<FoodOrder> specification(OrderFilter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (f.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("analysisDate"), f.from()));
            }
            if (f.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("analysisDate"), f.to()));
            }
            if (f.city() != null) {
                predicates.add(cb.equal(cb.lower(root.get("city")), f.city().toLowerCase(Locale.ROOT)));
            }
            if (Boolean.TRUE.equals(f.lateNightOnly())) {
                predicates.add(cb.isTrue(root.get("lateNight")));
            }
            if (f.category() != null || f.search() != null) {
                Join<FoodOrder, FoodItem> item = root.join("foodItem");
                if (f.category() != null) {
                    predicates.add(cb.equal(item.get("category"), f.category()));
                }
                if (f.search() != null) {
                    String like = "%" + f.search().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
                    predicates.add(cb.like(item.get("normalizedName"), like, '\\'));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static OrderDto toDto(FoodOrder o) {
        FoodItem f = o.getFoodItem();
        BigDecimal q = BigDecimal.valueOf(o.getQuantity());
        return new OrderDto(o.getId(), o.getExternalOrderId().substring(0, 12), o.getOrderTimestamp(),
                o.getLocalDateTime(), o.getLocalHour(), o.getAnalysisDate(), o.isLateNight(), f.getId(), f.getName(),
                f.getCategory().name(), o.getQuantity(), o.getCity(), o.getDataSource(),
                times(f.getCaloriesKcal(), q), times(f.getSugarG(), q), times(f.getSodiumMg(), q),
                times(f.getSaturatedFatG(), q), times(f.getProteinG(), q));
    }

    private static BigDecimal times(BigDecimal perServing, BigDecimal quantity) {
        return perServing == null ? null : perServing.multiply(quantity).stripTrailingZeros();
    }
}
