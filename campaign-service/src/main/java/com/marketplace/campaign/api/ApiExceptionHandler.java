package com.marketplace.campaign.api;

import com.marketplace.campaign.service.CampaignService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(CampaignService.NotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(RuntimeException e) { return response(HttpStatus.NOT_FOUND, "NOT_FOUND", e); }
    @ExceptionHandler(CampaignService.ForbiddenException.class)
    ResponseEntity<Map<String, String>> forbidden(RuntimeException e) { return response(HttpStatus.FORBIDDEN, "FORBIDDEN", e); }
    @ExceptionHandler(CampaignService.ConflictException.class)
    ResponseEntity<Map<String, String>> conflict(RuntimeException e) { return response(HttpStatus.CONFLICT, "INVALID_TRANSITION", e); }
    @ExceptionHandler({CampaignService.InvalidRequestException.class, MethodArgumentNotValidException.class})
    ResponseEntity<Map<String, String>> invalid(RuntimeException e) { return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e); }

    private ResponseEntity<Map<String, String>> response(HttpStatus status, String code, RuntimeException e) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", e.getMessage()));
    }
}
