package com.example.controller;


import com.example.dto.IssuedRefreshToken;
import com.example.dto.LoginRequest;
import com.example.dto.LoginResponse;
import com.example.dto.RegisterRequest;
import com.example.dto.RotatedSession;
import com.example.dto.UserDto;
import com.example.exception.InvalidRefreshTokenException;
import com.example.exception.TooManyRequestsException;
import com.example.ratelimit.RateLimiter;
import com.example.security.AppUserPrincipal;
import com.example.security.JwtService;
import com.example.service.AuthService;
import com.example.service.RefreshTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
@Tag(name = "Аутентификация",description = "Регистрация и логин")
@RestController
@RequestMapping("/auth")
public class AuthController {
    private final JwtService service;
    private final AuthenticationManager authManager;
    private final RateLimiter rateLimiter;
    private static final String REFRESH_COOKIE = "refresh_token";
    private final AuthService authService;
    private final RefreshTokenService refreshTokens;
    private final int loginLimit;
    private final boolean secureCookie;
    public AuthController(JwtService service,
                          AuthenticationManager authManager,
                          RateLimiter rateLimiter,
                          AuthService authService,
                          RefreshTokenService refreshTokens,
                          @Value("${rate-limit.login.per-minute}") int loginLimit,
                          @Value("${auth.refresh.cookie-secure:true}") boolean secureCookie)
    {
        this.service=service;
        this.authManager=authManager;
        this.rateLimiter=rateLimiter;
        this.authService=authService;
        this.refreshTokens=refreshTokens;
        this.loginLimit=loginLimit;
        this.secureCookie=secureCookie;
    }
    @Operation(summary = "Регистрация", description = "Роль всегда USER")
    @ApiResponse(responseCode = "201", description = "Создан")
    @ApiResponse(responseCode = "400", description = "Логин или пароль не подходят")
    @ApiResponse(responseCode = "409", description = "Логин уже занят")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto register(@Valid @RequestBody RegisterRequest request)
    {
        return authService.register(request);
    }
    @Operation(summary = "Получить токен")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "401", description = "Неверный логин или пароль")
    @ApiResponse(responseCode = "429", description = "Больше 5 попыток в минуту")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest)
    {
        String key = "rate:login:" + servletRequest.getRemoteAddr() + ":" +(Instant.now().getEpochSecond()/60);
        if(!rateLimiter.allow(key,loginLimit,Duration.ofSeconds(60)))
        {
            throw new TooManyRequestsException("Слишком много попыток");
        }
        Authentication auth = authManager.authenticate(new UsernamePasswordAuthenticationToken(request.username(),request.password()));
        // Security 7 добавляет к ролям фактор входа FACTOR_PASSWORD, в токен он не нужен
        List<String> roles = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).filter(role -> role.startsWith("ROLE_")).toList();
        AppUserPrincipal principal = (AppUserPrincipal) auth.getPrincipal();
        IssuedRefreshToken refresh = refreshTokens.issue(principal.getId());
        return withRefreshCookie(new LoginResponse(service.generateToken(auth.getName(),roles)), refresh);
    }
    @Operation(summary = "Новый токен по refresh-куке", description = "Старый refresh гасится, в куке приходит новый. "
            + "Погашенный refresh, присланный снова, отзывает все токены этого входа")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "401", description = "Нет куки, refresh неизвестен, истёк или погашен")
    @ApiResponse(responseCode = "403", description = "Аккаунт заблокирован")
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken)
    {
        if(refreshToken == null)
        {
            throw new InvalidRefreshTokenException();
        }
        RotatedSession session = refreshTokens.rotate(refreshToken);
        return withRefreshCookie(new LoginResponse(service.generateToken(session.username(), session.roles())), session.refresh());
    }
    // HttpOnly: скрипт страницы куку не прочитает, XSS не унесёт долгую сессию. Path=/auth: кука уходит только сюда,
    // не в каждый запрос к API. SameSite=Strict: с чужого сайта браузер её не пришлёт, это защита /auth/refresh от CSRF
    private ResponseEntity<LoginResponse> withRefreshCookie(LoginResponse body, IssuedRefreshToken refresh)
    {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, refresh.value())
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/auth")
                .maxAge(refresh.ttl())
                .build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(body);
    }
}
