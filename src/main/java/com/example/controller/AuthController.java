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
import com.example.ratelimit.SpamGuard;
import com.example.security.AppUserPrincipal;
import com.example.security.JwtService;
import com.example.service.AuthService;
import com.example.service.RefreshTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final SpamGuard spamGuard;
    private final int loginLimit;
    private final boolean secureCookie;
    public AuthController(JwtService service,
                          AuthenticationManager authManager,
                          RateLimiter rateLimiter,
                          AuthService authService,
                          RefreshTokenService refreshTokens,
                          SpamGuard spamGuard,
                          @Value("${rate-limit.login.per-minute}") int loginLimit,
                          @Value("${auth.refresh.cookie-secure:true}") boolean secureCookie)
    {
        this.service=service;
        this.authManager=authManager;
        this.rateLimiter=rateLimiter;
        this.authService=authService;
        this.refreshTokens=refreshTokens;
        this.spamGuard=spamGuard;
        this.loginLimit=loginLimit;
        this.secureCookie=secureCookie;
    }
    @Operation(summary = "Регистрация", description = "Роль всегда USER")
    @ApiResponse(responseCode = "201", description = "Создан")
    @ApiResponse(responseCode = "400", description = "Логин или пароль не подходят")
    @ApiResponse(responseCode = "409", description = "Логин уже занят")
    @ApiResponse(responseCode = "429", description = "Больше 10 регистраций в час с одного адреса")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest)
    {
        spamGuard.registration(servletRequest.getRemoteAddr());
        return authService.register(request);
    }
    @Operation(summary = "Получить токен")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "401", description = "Неверный логин или пароль")
    @ApiResponse(responseCode = "429", description = "Больше 5 попыток в минуту")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest)
    {
        long now = Instant.now().getEpochSecond();
        String key = "rate:login:" + servletRequest.getRemoteAddr() + ":" + (now / 60);
        if(!rateLimiter.allow(key,loginLimit,Duration.ofSeconds(60)))
        {
            throw new TooManyRequestsException("Слишком много попыток входа, повторите через минуту", 60 - now % 60);
        }
        Authentication auth = authManager.authenticate(new UsernamePasswordAuthenticationToken(request.username(),request.password()));
        // Security 7 добавляет к ролям фактор входа FACTOR_PASSWORD, в токен он не нужен
        List<String> roles = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).filter(role -> role.startsWith("ROLE_")).toList();
        AppUserPrincipal principal = (AppUserPrincipal) auth.getPrincipal();
        IssuedRefreshToken refresh = refreshTokens.issue(principal.getId());
        return withRefreshCookie(new LoginResponse(service.generateToken(auth.getName(),roles,principal.getTokenVersion())), refresh);
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
        return withRefreshCookie(new LoginResponse(service.generateToken(session.username(), session.roles(), session.tokenVersion())), session.refresh());
    }
    @Operation(summary = "Выйти", description = "Гасит refresh-токены этого входа и стирает куку. Access-токен клиент выбрасывает сам")
    @ApiResponse(responseCode = "204", description = "Вышли, в том числе если куки не было")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken)
    {
        if(refreshToken != null)
        {
            refreshTokens.logout(refreshToken);
        }
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clearedRefreshCookie()).build();
    }
    @Operation(summary = "Выйти отовсюду", description = "Гасит все refresh-токены пользователя и все выданные ему access-токены")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "204", description = "OK")
    @ApiResponse(responseCode = "401", description = "Нет токена")
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@Parameter(hidden = true) @AuthenticationPrincipal AppUserPrincipal principal)
    {
        refreshTokens.logoutEverywhere(principal.getId());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clearedRefreshCookie()).build();
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
    // Те же атрибуты, что при выдаче: иначе браузер сочтёт это другой кукой и старую не сотрёт
    private String clearedRefreshCookie()
    {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/auth")
                .maxAge(0)
                .build()
                .toString();
    }
}
