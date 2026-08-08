package com.marketplace.luckydraw.domain.port;

import com.marketplace.luckydraw.domain.Ticket;
import java.util.List;

public interface TicketRepository {
    void issueForOrder(String orderId, String userId);
    boolean consume(String ticketId, String userId, String campaignId, String entryId);
    List<Ticket> findByUser(String userId);
}

