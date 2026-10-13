package com.nutrinight.controller;

import com.nutrinight.dto.OrderFilter;
import com.nutrinight.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/export")
    @Operation(summary = "Export CSV: type = summary | orders | categories | hourly | nutrition | geographic")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "summary") String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean lateNightOnly) {
        ReportService.ReportType reportType = ReportService.parseType(type);
        boolean lateDefault = reportType == ReportService.ReportType.CATEGORIES
                || reportType == ReportService.ReportType.NUTRITION;
        OrderFilter filter = OrderFilter.of(from, to, city, category, lateNightOnly == null ? lateDefault : lateNightOnly);
        StringWriter out = new StringWriter();
        out.write('﻿'); // BOM so spreadsheet apps detect UTF-8
        reportService.write(reportType, filter, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(reportService.fileName(reportType)).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(out.toString().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/metadata")
    @Operation(summary = "Report header data: period, data sources, nutrition sources and limitations")
    public Map<String, Object> metadata(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String category) {
        return reportService.metadata(OrderFilter.of(from, to, city, category, false));
    }
}
