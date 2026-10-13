package com.example.controller;

import com.example.dto.ModelSuggestion;
import com.example.security.JwtService;
import com.example.security.SecurityConfig;
import com.example.security.SecurityErrorWriter;
import com.example.service.CarModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CarModelController.class)
@Import({SecurityConfig.class, SecurityErrorWriter.class})
class CarModelControllerTest {
    @Autowired
    MockMvc mockMvc;
    @MockitoBean
    CarModelCatalog catalog;
    @MockitoBean
    JwtService jwtService;
    @MockitoBean
    UserDetailsService userDetailsService;

    // Подсказки открыты без входа, как лента. Пробелы вокруг запроса отрезаются до поиска
    @Test
    void anonymousGetsSuggestions() throws Exception
    {
        when(catalog.suggest("toyta", 10)).thenReturn(List.of(new ModelSuggestion("Toyota", "Camry")));

        mockMvc.perform(get("/api/models").param("q", "  toyta "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].brand").value("Toyota"))
                .andExpect(jsonPath("$[0].model").value("Camry"));
    }

    @Test
    void limitIsPassedThrough() throws Exception
    {
        when(catalog.suggest("camr", 3)).thenReturn(List.of());

        mockMvc.perform(get("/api/models").param("q", "camr").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"t", "  t  ", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void queryOutsideTwoToFiftyIs400(String q) throws Exception
    {
        mockMvc.perform(get("/api/models").param("q", q))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Запрос подсказки от 2 до 50 символов"));

        verify(catalog, never()).suggest(anyString(), anyInt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "21"})
    void limitOutsideOneToTwentyIs400(String limit) throws Exception
    {
        mockMvc.perform(get("/api/models").param("q", "toyota").param("limit", limit))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Подсказок от 1 до 20"));
    }

    @Test
    void missingQueryIs400() throws Exception
    {
        mockMvc.perform(get("/api/models"))
                .andExpect(status().isBadRequest());
    }
}
