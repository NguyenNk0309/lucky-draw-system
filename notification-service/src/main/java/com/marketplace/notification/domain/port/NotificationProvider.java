package com.marketplace.notification.domain.port;

import com.marketplace.notification.domain.Notification;
import java.util.List;

public interface NotificationProvider {
    void send(Notification notification);
    List<Notification> findByUser(String userId);
}

