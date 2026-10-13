package com.nutrinight.controller;

import com.nutrinight.dto.ImportResultDto;
import com.nutrinight.dto.OrderFilter;
import com.nutrinight.dto.PageResponse;
import com.nutrinight.exception.InvalidRequestException;
import com.nutrinight.exception.ResourceNotFoundException;
import com.nutrinight.service.OrderImportService;
import com.nutrinight.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(ControllerTestConfig.class)
class OrderControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean OrderImportService importService;
    @MockitoBean OrderService orderService;

    private final MockMultipartFile file = new MockMultipartFile("file", "o.csv", "text/csv", "a".getBytes());

    private static ImportResultDto result(boolean dryRun, int imported) {
        return new ImportResultDto(dryRun, "o.csv", dryRun ? null : 1L, 1, 1, 0, 0, 0, imported, 1, 1, 0,
                List.of(), List.of(), List.of());
    }

    @Test
    void uploadRequiresAuthentication() throws Exception {
        mvc.perform(multipart("/api/orders/upload").file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Admin credentials are required for this action"));
        verifyNoInteractions(importService);
    }

    @Test
    void uploadRejectsWrongPassword() throws Exception {
        mvc.perform(multipart("/api/orders/upload").file(file).with(httpBasic("admin", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminUploadReturnsCreated() throws Exception {
        when(importService.importCsv(any(), eq(false))).thenReturn(result(false, 1));
        mvc.perform(multipart("/api/orders/upload").file(file).with(httpBasic("admin", "secret")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importedRows").value(1));
    }

    @Test
    void dryRunReturnsOk() throws Exception {
        when(importService.importCsv(any(), eq(true))).thenReturn(result(true, 0));
        mvc.perform(multipart("/api/orders/upload").file(file).param("dryRun", "true").with(httpBasic("admin", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true));
    }

    @Test
    void uploadValidationErrorsAreBadRequest() throws Exception {
        when(importService.importCsv(any(), anyBoolean())).thenThrow(new InvalidRequestException("Only .csv files are accepted"));
        mvc.perform(multipart("/api/orders/upload").file(file).with(httpBasic("admin", "secret")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Only .csv files are accepted"));
    }

    @Test
    void missingFilePartIsBadRequest() throws Exception {
        mvc.perform(multipart("/api/orders/upload").with(httpBasic("admin", "secret")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listPassesFiltersAndIsPublic() throws Exception {
        when(orderService.list(any(), anyInt(), anyInt(), any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0, "UNSORTED"));
        mvc.perform(get("/api/orders").param("from", "2026-07-01").param("to", "2026-07-31")
                        .param("city", "Pune").param("category", "fast food").param("lateNightOnly", "true")
                        .param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        verify(orderService).list(argThat((OrderFilter f) -> f.city().equals("Pune") && f.lateNightOnly()
                && f.category().name().equals("FAST_FOOD") && f.from().toString().equals("2026-07-01")), eq(0), eq(10), isNull());
    }

    @Test
    void invalidFilterValuesAreBadRequest() throws Exception {
        mvc.perform(get("/api/orders").param("from", "not-a-date")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders").param("category", "nonsense"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown category 'nonsense'"));
        mvc.perform(get("/api/orders").param("from", "2026-08-01").param("to", "2026-07-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUnknownOrderIs404() throws Exception {
        when(orderService.get(99L)).thenThrow(new ResourceNotFoundException("Order", 99L));
        mvc.perform(get("/api/orders/99")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order 99 was not found"));
    }

    @Test
    void deleteRequiresAdmin() throws Exception {
        mvc.perform(delete("/api/orders/5")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/orders/5").with(httpBasic("admin", "secret"))).andExpect(status().isNoContent());
        verify(orderService).delete(5L);
        doThrow(new ResourceNotFoundException("Order", 6L)).when(orderService).delete(6L);
        mvc.perform(delete("/api/orders/6").with(httpBasic("admin", "secret"))).andExpect(status().isNotFound());
    }
}
