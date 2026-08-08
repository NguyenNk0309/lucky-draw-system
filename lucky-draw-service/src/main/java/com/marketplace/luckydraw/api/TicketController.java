package com.marketplace.luckydraw.api;

import com.marketplace.luckydraw.domain.Ticket;
import com.marketplace.luckydraw.service.TicketService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tickets")
public class TicketController {
    private final TicketService service;

    public TicketController(TicketService service) {
        this.service = service;
    }

    @GetMapping
    public List<Ticket> list(@RequestHeader("X-Demo-User") String userId,
            @RequestHeader("X-Demo-Role") String role) {
        DemoAuth.require(role, "CUSTOMER");
        return service.list(userId);
    }
}

