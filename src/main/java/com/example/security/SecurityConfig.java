package com.example.security;

import com.example.dto.ErrorResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;
    private final SecurityErrorWriter errorWriter;
    public SecurityConfig(JwtAuthFilter jwtAuthFilter,SecurityErrorWriter errorWriter)
    {
        this.jwtAuthFilter=jwtAuthFilter;
        this.errorWriter=errorWriter;
    }
    @Bean
    public PasswordEncoder passwordEncoder()
    {
        return new BCryptPasswordEncoder();
    }
    @Bean
    public SecurityFilterChain chain(HttpSecurity http) throws Exception
    {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/cars/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/cars/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/cars/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> errorWriter.writeError(request,response, HttpStatus.UNAUTHORIZED,"Требуется аутентификация"))
                        .accessDeniedHandler(((request, response, accessDeniedException) -> errorWriter.writeError(request,response,HttpStatus.FORBIDDEN,"Недостаточно прав"))))
                .build();
    }
    @Bean
    public AuthenticationManager authManager(AuthenticationConfiguration cfg) throws Exception
    {
        return cfg.getAuthenticationManager();
    }
}
