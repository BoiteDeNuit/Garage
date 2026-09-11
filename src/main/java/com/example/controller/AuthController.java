package com.example.controller;


import com.example.dto.LoginRequest;
import com.example.dto.LoginResponse;
import com.example.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final JwtService service;
    private final AuthenticationManager authManager;
    public AuthController(JwtService service, AuthenticationManager authManager)
    {
        this.service=service;
        this.authManager=authManager;
    }
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request)
    {
        Authentication auth = authManager.authenticate(new UsernamePasswordAuthenticationToken(request.username(),request.password()));
        List<String> roles = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        return new LoginResponse(service.generateToken(auth.getName(),roles));
    }


}
