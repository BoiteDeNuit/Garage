package com.example.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(EntityNotFoundException e, HttpServletRequest request)
    {
        return build(HttpStatus.NOT_FOUND,e.getMessage(),request);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> notValid(MethodArgumentNotValidException e , HttpServletRequest request)
    {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.isBindingFailure() ? bindingFailure(error) : error.getDefaultMessage())
                .collect(Collectors.joining("; "));
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
                case METHOD_NOT_ALLOWED -> "Метод " + request.getMethod() + " не поддерживается для данного адреса";
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
        if(e.getRequiredType() == String.class)
        {
            return build(HttpStatus.BAD_REQUEST,nulCharacter(e.getName()),request);
        }
        String message = "Запрос " + request.getRequestURI() + " сформулирован неверно, необходимо: " + e.getName() + ", прислали: " + e.getValue();
        return build(HttpStatus.BAD_REQUEST,message,request);
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> notReadable(HttpMessageNotReadableException e,HttpServletRequest request)
    {
        // Значение не из списка: подсказываем, что можно прислать. Само значение не повторяем
        if(e.getCause() instanceof InvalidFormatException invalid
                && invalid.getTargetType() != null && invalid.getTargetType().isEnum() && !invalid.getPath().isEmpty())
        {
            return build(HttpStatus.BAD_REQUEST,allowedValues(invalid.getPath().getLast().getPropertyName(),invalid.getTargetType()),request);
        }
        // Строку отвергает только NoNulStringsModule
        if(e.getCause() instanceof InvalidFormatException invalid
                && invalid.getTargetType() == String.class && !invalid.getPath().isEmpty())
        {
            return build(HttpStatus.BAD_REQUEST,nulCharacter(invalid.getPath().getLast().getPropertyName()),request);
        }
        return build(HttpStatus.BAD_REQUEST,"Некорректный формат запроса",request);
    }
    // Без этого обработчика отказ из сервиса попадал бы в Exception.class и становился 500.
    // isAuthenticated, а не !isAnonymous: на пустом контексте isAnonymous(null) = false, и вышел бы 403 вместо 401
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> accessDenied(AccessDeniedException e, HttpServletRequest request)
    {
        if(!trustResolver.isAuthenticated(SecurityContextHolder.getContext().getAuthentication()))
        {
            return build(HttpStatus.UNAUTHORIZED,"Требуется аутентификация",request);
        }
        return build(HttpStatus.FORBIDDEN,"Недостаточно прав",request);
    }
    @ExceptionHandler(ListingStateException.class)
    public ResponseEntity<ErrorResponse> listingConflict(ListingStateException e, HttpServletRequest request)
    {
        return build(HttpStatus.CONFLICT,e.getMessage(),request);
    }
    // Два одновременных изменения одного объявления: второе упирается в @Version
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> concurrentChange(OptimisticLockingFailureException e, HttpServletRequest request)
    {
        return build(HttpStatus.CONFLICT,"Объявление изменили одновременно с вами, обновите и повторите",request);
    }
    // Две загрузки фото одновременно заняли одно место: проверка в сервисе пропустила обе, а уникальность
    // (listing_id, position) остановила вторую. Она отложенная, отказ приходит уже при коммите.
    // Остальные нарушения ограничений — ошибка у нас, 500 с логом
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> constraintViolation(DataIntegrityViolationException e, HttpServletRequest request)
    {
        String reason = e.getMostSpecificCause().getMessage();
        if(reason != null && (reason.contains("\"uq_listing_photos_position\"") || reason.contains("\"chk_listing_photos_position\"")))
        {
            return build(HttpStatus.CONFLICT,"Фото загружают одновременно, повторите",request);
        }
        return unexpected(e,request);
    }
    @ExceptionHandler(UsernameTakenException.class)
    public ResponseEntity<ErrorResponse> usernameTaken(UsernameTakenException e, HttpServletRequest request)
    {
        return build(HttpStatus.CONFLICT,e.getMessage(),request);
    }
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> invalidRequest(InvalidRequestException e, HttpServletRequest request)
    {
        return build(HttpStatus.BAD_REQUEST,e.getMessage(),request);
    }
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorResponse> unknownSortField(PropertyReferenceException e, HttpServletRequest request)
    {
        return build(HttpStatus.BAD_REQUEST,"Нельзя сортировать по полю: " + e.getPropertyName(),request);
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
    // Query-параметр не превратился в свой тип (?yearFrom=abc, ?fuelType=COAL). Текст Spring по-английски
    // и с присланным значением, поэтому свой
    private String bindingFailure(FieldError error)
    {
        Class<?> type = error.contains(TypeMismatchException.class) ? error.unwrap(TypeMismatchException.class).getRequiredType() : null;
        if(type != null && type.isEnum())
        {
            return allowedValues(error.getField(),type);
        }
        // Строка в строку не превращается только у NoNulParametersAdvice
        if(type == String.class)
        {
            return nulCharacter(error.getField());
        }
        return "Поле " + error.getField() + ": неверный формат";
    }
    private String nulCharacter(String field)
    {
        return "Поле " + field + ": нулевой символ не допускается";
    }
    private String allowedValues(String field,Class<?> enumType)
    {
        String allowed = Arrays.stream(enumType.getEnumConstants())
                .map(Object::toString)
                .collect(Collectors.joining(", "));
        return "Поле " + field + ": допустимые значения " + allowed;
    }
    private ResponseEntity<ErrorResponse> build(HttpStatus status,String message,HttpServletRequest request)
    {
        ErrorResponse body = new ErrorResponse(LocalDateTime.now(),status.value(),status.getReasonPhrase(),message,request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
