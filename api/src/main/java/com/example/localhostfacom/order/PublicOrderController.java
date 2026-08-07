package com.example.localhostfacom.order;

import com.example.localhostfacom.common.ApiException;
import com.example.localhostfacom.common.RateLimiter;
import com.example.localhostfacom.order.dto.CreateOrderRequest;
import com.example.localhostfacom.order.dto.OrderChargeResponse;
import com.example.localhostfacom.order.dto.OrderStatusResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/orders")
public class PublicOrderController {

    private final OrderService orders;
    private final RateLimiter rateLimiter;

    public PublicOrderController(OrderService orders, RateLimiter rateLimiter) {
        this.orders = orders;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderChargeResponse create(@Valid @RequestBody CreateOrderRequest request,
                                      HttpServletRequest http) {
        if (!rateLimiter.tryAcquire("order:" + http.getRemoteAddr(), 10, Duration.ofMinutes(1))) {
            throw ApiException.tooManyRequests("rate-limited", "Too many orders; please wait a moment");
        }

        Order order = orders.create(request.items());

        try {
            return OrderChargeResponse.of(orders.ensureCharge(order.getId()));
        } catch (ApiException exception) {
            // The order is committed, so the customer is not stranded: the payment screen
            // retries against /charge rather than starting over.
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_GATEWAY, "Could not create the payment charge");
            problem.setProperty("slug", "charge-creation-failed");
            problem.setProperty("orderId", order.getId().toString());
            throw new ErrorResponseException(HttpStatus.BAD_GATEWAY, problem, exception);
        }
    }

    @PostMapping("/{id}/charge")
    public OrderChargeResponse charge(@PathVariable UUID id) {
        return OrderChargeResponse.of(orders.ensureCharge(id));
    }

    /**
     * The payment screen polls this every few seconds. The provider is queried at most
     * once per throttle window, and the throttle lives in memory rather than in a column,
     * so polling does not turn into a database write per poll.
     */
    @GetMapping("/{id}/status")
    public OrderStatusResponse status(@PathVariable UUID id) {
        Order order = orders.require(id);

        if (order.getStatus() == OrderStatus.PENDING
                && order.hasCharge()
                && rateLimiter.tryAcquire("status-sync:" + id, 1, Duration.ofSeconds(10))) {
            order = orders.syncWithProvider(id);
        }

        return OrderStatusResponse.of(order);
    }
}
