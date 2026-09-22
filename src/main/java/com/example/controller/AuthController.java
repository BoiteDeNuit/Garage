package com.example.controller;


import com.example.dto.LoginRequest;
import com.example.dto.LoginResponse;
import com.example.exception.TooManyRequestsException;
import com.example.ratelimit.RateLimiter;
import com.example.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final JwtService service;
    private final AuthenticationManager authManager;
    private final RateLimiter rateLimiter;
    private final int loginLimit;
    public AuthController(JwtService service,
                          AuthenticationManager authManager,
                          RateLimiter rateLimiter,
                          @Value("${rate-limit.login.per-minute}") int loginLimit)
    {
        this.service=service;
        this.authManager=authManager;
        this.rateLimiter=rateLimiter;
        this.loginLimit=loginLimit;
    }
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest)
    {
        String key = "rate:login:" + servletRequest.getRemoteAddr() + ":" +(Instant.now().getEpochSecond()/60);
        if(!rateLimiter.allow(key,loginLimit,Duration.ofSeconds(60)))
        {
            throw new TooManyRequestsException("Слишком много попыток");
        }
        Authentication auth = authManager.authenticate(new UsernamePasswordAuthenticationToken(request.username(),request.password()));
        List<String> roles = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        return new LoginResponse(service.generateToken(auth.getName(),roles));
    }


}
