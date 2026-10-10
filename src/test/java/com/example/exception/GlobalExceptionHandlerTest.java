package com.example.exception;

import com.example.dto.ErrorResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/listings/1/publish");

    @AfterEach
    void clearContext()
    {
        SecurityContextHolder.clearContext();
    }

    // 409 только для места фото. Любое другое нарушение ограничения — ошибка у нас
    @Test
    void otherConstraintViolationStays500()
    {
        DataIntegrityViolationException e = new DataIntegrityViolationException("x",
                new SQLException("ERROR: duplicate key value violates unique constraint \"listing_photos_object_key_key\""));

        assertThat(handler.constraintViolation(e, request).getStatusCode().value()).isEqualTo(500);
    }

    @Test
    void deniedWithEmptyContextIs401()
    {
        ResponseEntity<ErrorResponse> response = handler.accessDenied(new AccessDeniedException("нет"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody().message()).isEqualTo("Требуется аутентификация");
    }

    @Test
    void deniedForAnonymousIs401()
    {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(handler.accessDenied(new AccessDeniedException("нет"), request).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void deniedForLoggedInUserIs403()
    {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("seller", null, AuthorityUtils.createAuthorityList("ROLE_USER")));

        ResponseEntity<ErrorResponse> response = handler.accessDenied(new AccessDeniedException("нет"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody().message()).isEqualTo("Недостаточно прав");
        assertThat(response.getBody().path()).isEqualTo("/api/listings/1/publish");
    }
}
