package com.example.localhostfacom.order.dto;

import com.example.localhostfacom.order.Order;
import java.time.Instant;

public record OrderStatusResponse(String status, Instant paidAt) {

    public static OrderStatusResponse of(Order order) {
        return new OrderStatusResponse(order.getStatus().name(), order.getPaidAt());
    }
}
