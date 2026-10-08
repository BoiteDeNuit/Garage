package com.example.controller;

import com.example.dto.LoginRequest;
import com.example.dto.RegisterRequest;
import com.example.dto.UserDto;
import com.example.exception.UsernameTakenException;
import com.example.model.Role;
import com.example.ratelimit.RateLimiter;
import com.example.security.JwtService;
import com.example.security.SecurityConfig;
import com.example.security.SecurityErrorWriter;
import com.example.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, SecurityErrorWriter.class})
class AuthControllerTest {
    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @MockitoBean
    AuthService authService;
    @MockitoBean
    JwtService jwtService;
    @MockitoBean
    UserDetailsService userDetailsService;
    @MockitoBean
    RateLimiter rateLimiter;

    @Test
    void registerReturns201WithoutPassword() throws Exception
    {
        when(authService.register(any())).thenReturn(new UserDto(5L, "seller_42", Role.USER));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("seller_42", "correct-horse-battery"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void roleFromBodyIsIgnored() throws Exception
    {
        when(authService.register(any())).thenReturn(new UserDto(5L, "seller_42", Role.USER));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"seller_42\",\"password\":\"correct-horse-battery\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void badUsernamesAndShortPasswordAre400() throws Exception
    {
        for (RegisterRequest bad : new RegisterRequest[]{
                new RegisterRequest("Seller", "correct-horse-battery"),
                new RegisterRequest("seller-42", "correct-horse-battery"),
                new RegisterRequest("ab", "correct-horse-battery"),
                new RegisterRequest("seller_42", "short")})
        {
            mockMvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(bad)))
                    .andExpect(status().isBadRequest());
        }

        verify(authService, never()).register(any());
    }

    @Test
    void takenUsernameIs409() throws Exception
    {
        when(authService.register(any())).thenThrow(new UsernameTakenException());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("seller_42", "correct-horse-battery"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Логин уже занят"));
    }

    @Test
    void loginOverLimitIs429() throws Exception
    {
        when(rateLimiter.allow(anyString(), anyInt(), any())).thenReturn(false);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("boss", "whatever"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(containsString("Слишком много попыток")));

        verify(rateLimiter).allow(argThat(key -> key.startsWith("rate:login:")), anyInt(), any());
    }
}
