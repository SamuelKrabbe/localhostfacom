package com.example.localhostfacom.order;

import com.example.localhostfacom.auth.CurrentAdmin;
import com.example.localhostfacom.order.dto.AdminOrderResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService service;
    private final OrderRepository orders;

    public AdminOrderController(OrderService service, OrderRepository orders) {
        this.service = service;
        this.orders = orders;
    }

    @GetMapping
    public Page<AdminOrderResponse> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(page, Math.min(size, 100));
        Page<Order> result = status == null
                ? orders.findAllByOrderByCreatedAtDesc(pageable)
                : orders.findByStatusOrderByCreatedAtDesc(status, pageable);
        return result.map(AdminOrderResponse::of);
    }

    /** Used when the webhook never arrived and the admin can see the money did. */
    @PostMapping("/{id}/mark-paid")
    public AdminOrderResponse markPaid(@PathVariable UUID id) {
        service.markPaid(id, CurrentAdmin.require());
        return AdminOrderResponse.of(service.require(id));
    }

    @PostMapping("/{id}/sync")
    public AdminOrderResponse sync(@PathVariable UUID id) {
        return AdminOrderResponse.of(service.syncWithProvider(id));
    }

    @PostMapping("/{id}/cancel")
    public AdminOrderResponse cancel(@PathVariable UUID id) {
        service.cancel(id);
        return AdminOrderResponse.of(service.require(id));
    }
}
