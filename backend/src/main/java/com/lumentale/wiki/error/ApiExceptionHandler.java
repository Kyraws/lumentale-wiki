package com.lumentale.wiki.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Centralized error handling for every controller. 404s are typed and quiet;
 * 400s cover malformed input (a non-uuid path var); unexpected errors get a
 * uniform body AND a logged stack trace so bad data is diagnosable.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException ex, HttpServletRequest req, HttpServletResponse res) {
        return error(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), req, res);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> badRequest(BadRequestException ex, HttpServletRequest req, HttpServletResponse res) {
        return error(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), req, res);
    }

    /**
     * An unmapped URL / missing static resource (e.g. {@code /} or
     * {@code /favicon.ico}) is a plain 404 — NOT a 500. Handled explicitly and
     * quietly so the broad handler below doesn't log it as an unhandled error.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> noResource(NoResourceFoundException ex, HttpServletRequest req, HttpServletResponse res) {
        return error(HttpStatus.NOT_FOUND, "Not Found", "No endpoint for " + req.getRequestURI(), req, res);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest req, HttpServletResponse res) {
        log.error("Unhandled error on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
            "An unexpected error occurred.", req, res);
    }

    /**
     * Error bodies must never be cached. {@code setHeader} on the servlet response
     * REPLACES the blanket {@code public, max-age=3600} that the cache interceptor
     * set in preHandle — going through {@code ResponseEntity.header} would instead
     * append a second, conflicting Cache-Control value.
     */
    private static ResponseEntity<ApiError> error(HttpStatus status, String error, String message,
                                                  HttpServletRequest req, HttpServletResponse res) {
        res.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ResponseEntity.status(status).body(new ApiError(status.value(), error, message, req.getRequestURI()));
    }
}
