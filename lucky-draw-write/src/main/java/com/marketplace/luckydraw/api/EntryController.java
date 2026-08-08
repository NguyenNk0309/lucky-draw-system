package com.marketplace.luckydraw.api;

import com.marketplace.luckydraw.domain.Entry;
import com.marketplace.luckydraw.service.EntryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/campaigns/{campaignId}/entries")
public class EntryController {
    private final EntryService service;

    public EntryController(EntryService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Entry submit(
            @PathVariable String campaignId,
            @RequestHeader("X-Demo-User") String userId,
            @RequestHeader("X-Demo-Role") String role,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @Valid @RequestBody SubmitEntry request) {
        DemoAuth.require(role, "CUSTOMER");
        return service.submit(userId, campaignId, request.ticketId(),
                correlationId == null ? UUID.randomUUID().toString() : correlationId);
    }

    public record SubmitEntry(@NotBlank String ticketId) {}
}

