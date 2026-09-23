package com.example.controller;

import com.example.dto.CarDto;
import com.example.exception.EntityNotFoundException;
import com.example.security.JwtAuthFilter;
import com.example.security.SecurityErrorWriter;
import com.example.service.GarageService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(CarController.class)
@AutoConfigureMockMvc(addFilters = false)
class CarControllerTest {
    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @MockitoBean
    GarageService service;
    @MockitoBean
    JwtAuthFilter jwtAuthFilter;
    @MockitoBean
    SecurityErrorWriter securityErrorWriter;

    @Test
    void returnsCarJson() throws Exception {
        when(service.getCar(1L)).thenReturn(new CarDto(1L, "Toyota", "Supra", "2JZ", 320, 1998, null));

        mockMvc.perform(get("/api/cars/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand").value("Toyota"))
                .andExpect(jsonPath("$.horsePower").value(320));
    }

    @Test
    void returns404() throws Exception {
        when(service.getCar(99L)).thenThrow(new EntityNotFoundException("Машина с id 99 не найдена"));

        mockMvc.perform(get("/api/cars/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Машина с id 99 не найдена"));
    }

    @Test
    void returns400() throws Exception {

        CarDto invalid = new CarDto(null, "", "Supra", "2JZ", 0, 1998, null);
        mockMvc.perform(post("/api/cars")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        verify(service, never()).addCar(any());

    }

    @Test
    void returnsPageOfCars() throws Exception {
        CarDto car = new CarDto(1L, "Toyota", "Supra", "2JZ", 320, 1998, null);
        when(service.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(car), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/cars"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].brand").value("Toyota"))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.size").value(20));
    }

    @Test
    void limitsPageSizeToMaximum() throws Exception {
        when(service.findAll(any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/api/cars").param("size", "1000"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(service).findAll(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void returns400WhenIdIsNotNumber() throws Exception {
        mockMvc.perform(get("/api/cars/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("abc")));
    }

    @Test
    void returns405ForUnsupportedMethod() throws Exception {
        mockMvc.perform(put("/api/cars/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void returns404ForUnknownPath() throws Exception {
        mockMvc.perform(get("/api/nothing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("/api/nothing")));
    }

    @Test
    void returns415ForNonJsonBody() throws Exception {
        mockMvc.perform(post("/api/cars")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("text"))
                .andExpect(status().isUnsupportedMediaType());

        verify(service, never()).addCar(any());
    }

    @Test
    void returns404WhenDeletingMissingCar() throws Exception {
        doThrow(new EntityNotFoundException("Машина с id: 99 не найдена")).when(service).deleteCar(99L);

        mockMvc.perform(delete("/api/cars/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Машина с id: 99 не найдена"));
    }
}