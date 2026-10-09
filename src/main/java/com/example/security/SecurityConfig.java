package com.example.security;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
// Админка защищена в два уровня: правило по URL здесь и @PreAuthorize на методе сервиса.
// Новый админский эндпоинт без аннотации не окажется открыт любому вошедшему, и наоборот
@EnableWebSecurity
@EnableMethodSecurity
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
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/listings/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/models").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
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
