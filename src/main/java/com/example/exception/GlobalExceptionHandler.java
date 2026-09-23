package com.example.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(EntityNotFoundException e, HttpServletRequest request)
    {
        return build(HttpStatus.NOT_FOUND,e.getMessage(),request);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> notValid(MethodArgumentNotValidException e , HttpServletRequest request)
    {
        String message = e.getBindingResult().getFieldErrors().stream().map(FieldError::getDefaultMessage).collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST,message,request);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception e,HttpServletRequest request)
    {
        if(e instanceof org.springframework.web.ErrorResponse springError)
        {
            HttpStatus status = HttpStatus.valueOf(springError.getStatusCode().value());
            log.warn("Ошибка клиента метод: {} адрес: {} статус: {} ошибка: {}",request.getMethod(),request.getRequestURI(),status,e.getMessage());
            String message = switch(status)
            {
                case NOT_FOUND -> "Путь " + request.getRequestURI() + " не найден";
                case METHOD_NOT_ALLOWED -> "Метод" + request.getMethod() + " не поддерживается для данного адреса";
                case UNSUPPORTED_MEDIA_TYPE -> "Ожидается application/json";
                default -> status.getReasonPhrase();
            };
            return build(status,message,request);
        }
        log.error("Необработанная ошибка", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера", request);
    }
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> badCredentials(AuthenticationException e, HttpServletRequest request)
    {
        return build(HttpStatus.UNAUTHORIZED,"Неверный логин или пароль",request);
    }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> mismatchArgument(MethodArgumentTypeMismatchException e, HttpServletRequest request)
    {
        String message = "Запрос " + request.getRequestURI() + " сформулирован неверно, необходимо: " + e.getName() + "прислали: " + e.getValue();
        return build(HttpStatus.BAD_REQUEST,message,request);
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> notReadable(HttpMessageNotReadableException e,HttpServletRequest request)
    {
        return build(HttpStatus.BAD_REQUEST,"Некорректный формат запроса",request);
    }
    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ErrorResponse> currencyServiceUnavailable(ExternalServiceException e, HttpServletRequest request)
    {
        log.warn("Внешний сервис недоступен: {}", e.getMessage(), e);
        return build(HttpStatus.SERVICE_UNAVAILABLE,"Сервис курсов валют недоступен",request);
    }
    @ExceptionHandler(UnknownCurrencyException.class)
    public ResponseEntity<ErrorResponse> unknownCurrency(UnknownCurrencyException e, HttpServletRequest request)
    {
        return build(HttpStatus.BAD_REQUEST,"Неизвестный код валюты: " + e.getMessage(),request);
    }
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> tooManyRequests(TooManyRequestsException e, HttpServletRequest request)
    {
        ErrorResponse body = new ErrorResponse(LocalDateTime.now(),429,"Too many Requests","Слишком много попыток входа повторите через минуту", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER,"60")
                .body(body);
    }
    private ResponseEntity<ErrorResponse> build(HttpStatus status,String message,HttpServletRequest request)
    {
        ErrorResponse body = new ErrorResponse(LocalDateTime.now(),status.value(),status.getReasonPhrase(),message,request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
