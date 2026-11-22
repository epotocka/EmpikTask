package com.example.empiktask.error;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String ERROR_CODE_PROPERTY = "errorCode";

    @ExceptionHandler(BusinessException.class)
    ProblemDetail handleBusinessException(BusinessException exception) {
        log.debug("Business rule violated: {}", exception.getMessage());
        return problemDetail(exception.errorCode(), exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        log.error("Database constraint violated, application-level validation is incomplete", exception);
        return problemDetail(ErrorCode.DATA_CONFLICT,
                "The request conflicts with existing data. Please refresh and try again.");
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ProblemDetail problemDetail = problemDetail(ErrorCode.VALIDATION_FAILED,
                "The request contains invalid fields.");
        problemDetail.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status()).headers(headers).body(problemDetail);
    }

    private static ProblemDetail problemDetail(ErrorCode errorCode, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(errorCode.status(), detail);
        problemDetail.setTitle(errorCode.title());
        problemDetail.setProperty(ERROR_CODE_PROPERTY, errorCode.name());
        return problemDetail;
    }
}
