package com.example.controller;

import com.example.dto.CarDto;
import com.example.exception.EntityNotFoundException;
import com.example.service.GarageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CarController.class)
class CarControllerTest {
    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @MockitoBean
    GarageService service;

    @Test
    void returnsCarJson() throws Exception {
        when(service.getCar(1L)).thenReturn(new CarDto(1L, "Toyota", "Supra", "2JZ", 320, 1998));

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

        CarDto invalid = new CarDto(null, "", "Supra", "2JZ", 0, 1998);
        mockMvc.perform(post("/api/cars")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        verify(service, never()).addCar(any());

    }
}