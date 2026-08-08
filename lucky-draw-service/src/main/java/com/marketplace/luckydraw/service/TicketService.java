package com.marketplace.luckydraw.service;

import com.marketplace.luckydraw.domain.Ticket;
import com.marketplace.luckydraw.domain.port.TicketRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {
    private final TicketRepository tickets;

    public TicketService(TicketRepository tickets) {
        this.tickets = tickets;
    }

    @Transactional
    public void issueForOrder(String orderId, String userId) {
        tickets.issueForOrder(orderId, userId);
    }

    public List<Ticket> list(String userId) {
        return tickets.findByUser(userId);
    }
}

