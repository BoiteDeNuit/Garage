package com.example.security;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
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
                        // Выход отовсюду — по access-токену: нужно знать, чьи сессии гасить
                        .requestMatchers(HttpMethod.POST, "/auth/logout-all").authenticated()
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/listings/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/models").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/moderation/**").hasAnyRole("MODERATOR", "ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> errorWriter.writeError(request,response, HttpStatus.UNAUTHORIZED,"Требуется аутентификация"))
                        .accessDeniedHandler(((request, response, accessDeniedException) -> errorWriter.writeError(request,response,HttpStatus.FORBIDDEN,"Недостаточно прав"))))
                .build();
    }
    // Менеджер для /auth/login. По умолчанию Spring проверяет блокировку до пароля: на любой пароль заблокированный
    // логин отвечал бы 403, а незаблокированный 401, и по ответу без пароля было бы видно, что логин есть
    // и заблокирован. Здесь сначала пароль, потом блокировка: 403 получает только тот, кто знает пароль.
    // Провайдер не бин: бин-провайдер Spring подхватывает в общий менеджер и предупреждает об этом при старте
    @Bean
    public AuthenticationManager authManager(UserDetailsService users, PasswordEncoder encoder)
    {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        provider.setPreAuthenticationChecks(user -> {});
        provider.setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());
        return new ProviderManager(provider);
    }
}
