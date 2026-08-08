package com.marketplace.gateway.api;

import com.marketplace.gateway.auth.DemoAuthService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GatewayExceptionHandler {
    @ExceptionHandler(DemoAuthService.UnauthorizedException.class)
    ResponseEntity<Map<String, String>> unauthorized(RuntimeException e) { return response(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage()); }
    @ExceptionHandler(GatewayController.NotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(RuntimeException e) { return response(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage()); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalid() { return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Username and password are required"); }
    @ExceptionHandler({java.io.IOException.class, InterruptedException.class})
    ResponseEntity<Map<String, String>> unavailable(Exception e) {
        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        return response(HttpStatus.BAD_GATEWAY, "UPSTREAM_UNAVAILABLE", "A backend service is unavailable");
    }
    private ResponseEntity<Map<String, String>> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
    }
}
