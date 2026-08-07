package com.example.localhostfacom.payment;

import com.example.localhostfacom.order.Order;
import com.example.localhostfacom.order.OrderRepository;
import com.example.localhostfacom.order.OrderService;
import com.example.localhostfacom.order.WebhookEvent;
import com.example.localhostfacom.order.WebhookEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final PaymentProviderRegistry providers;
    private final OrderService orderService;
    private final OrderRepository orders;
    private final WebhookEventRepository events;

    public WebhookController(PaymentProviderRegistry providers, OrderService orderService,
                             OrderRepository orders, WebhookEventRepository events) {
        this.providers = providers;
        this.orderService = orderService;
        this.orders = orders;
        this.events = events;
    }

    @PostMapping("/{provider}")
    public ResponseEntity<Void> receive(@PathVariable String provider,
                                        @RequestBody(required = false) String rawBody,
                                        HttpServletRequest request) {

        PaymentProvider paymentProvider = providers.byName(provider);

        Optional<WebhookNotification> verified =
                paymentProvider.parseAndVerify(headersOf(request), rawBody == null ? "" : rawBody);

        if (verified.isEmpty()) {
            // Forging a confirmation would let anyone record money that never arrived,
            // so an unverified request is refused before anything is written.
            log.warn("Rejected an unverified {} webhook", provider);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        WebhookNotification notification = verified.get();
        WebhookEvent event = events.save(WebhookEvent.received(
                provider, notification.eventId(), notification.providerPaymentId(), rawBody == null ? "" : rawBody));

        Optional<Order> order = orders.findByProviderPaymentId(notification.providerPaymentId());
        if (order.isEmpty()) {
            event.markFailed("No order matches this payment id");
            events.save(event);
            // Still 200: retrying will not conjure an order, and we do not want the
            // provider hammering this endpoint forever.
            return ResponseEntity.ok().build();
        }

        try {
            orderService.applyProviderStatus(order.get().getId(), notification.status());
            event.markProcessed();
        } catch (RuntimeException exception) {
            log.error("Failed to apply {} webhook for payment {}",
                    provider, notification.providerPaymentId(), exception);
            event.markFailed(exception.getMessage());
        }
        events.save(event);

        return ResponseEntity.ok().build();
    }

    private Map<String, String> headersOf(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Collections.list(request.getHeaderNames())
                .forEach(name -> headers.put(name.toLowerCase(), request.getHeader(name)));
        return headers;
    }
}
