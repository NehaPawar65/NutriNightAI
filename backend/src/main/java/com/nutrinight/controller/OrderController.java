package com.nutrinight.controller;

import com.nutrinight.dto.ImportResultDto;
import com.nutrinight.dto.OrderDto;
import com.nutrinight.dto.OrderFilter;
import com.nutrinight.dto.PageResponse;
import com.nutrinight.service.OrderImportService;
import com.nutrinight.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Food orders")
public class OrderController {

    private final OrderImportService importService;
    private final OrderService orderService;

    public OrderController(OrderImportService importService, OrderService orderService) {
        this.importService = importService;
        this.orderService = orderService;
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload an order CSV (admin)",
            description = "Validates the file; with dryRun=true only validates and previews without storing.",
            security = @SecurityRequirement(name = "basicAuth"))
    public ResponseEntity<ImportResultDto> upload(@RequestPart("file") MultipartFile file,
                                                  @RequestParam(defaultValue = "false") boolean dryRun) {
        ImportResultDto result = importService.importCsv(file, dryRun);
        return ResponseEntity.status(dryRun || result.importedRows() == 0 ? HttpStatus.OK : HttpStatus.CREATED)
                .body(result);
    }

    @GetMapping
    @Operation(summary = "List orders with filters, pagination and sorting",
            description = "sort = field,direction with field in orderTimestamp, quantity, city, localHour, foodItem")
    public PageResponse<OrderDto> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean lateNightOnly,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String sort) {
        return orderService.list(OrderFilter.of(from, to, city, category, lateNightOnly, search), page, size, sort);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one order")
    public OrderDto get(@PathVariable Long id) {
        return orderService.get(id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete one order (admin)", security = @SecurityRequirement(name = "basicAuth"))
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        orderService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
