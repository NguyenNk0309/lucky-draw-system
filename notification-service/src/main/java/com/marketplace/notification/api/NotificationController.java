package com.marketplace.notification.api;

import com.marketplace.notification.domain.Notification;
import com.marketplace.notification.service.NotificationService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public List<Notification> list(@RequestHeader("X-Demo-User") String userId,
            @RequestHeader("X-Demo-Role") String role) {
        if (!"CUSTOMER".equals(role)) throw new ForbiddenException();
        return service.list(userId);
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    private static class ForbiddenException extends RuntimeException {}
}

