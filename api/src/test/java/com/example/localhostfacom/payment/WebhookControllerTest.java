package com.example.localhostfacom.payment;

import com.example.localhostfacom.order.Order;
import com.example.localhostfacom.order.OrderRepository;
import com.example.localhostfacom.order.OrderService;
import com.example.localhostfacom.order.OrderStatus;
import com.example.localhostfacom.order.WebhookEventRepository;
import com.example.localhostfacom.order.dto.CreateOrderRequest;
import com.example.localhostfacom.product.Product;
import com.example.localhostfacom.product.ProductRepository;
import com.example.localhostfacom.product.ProductService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebhookControllerTest {

    /** Stands in for a real provider so authenticity can be toggled per test. */
    static class StubProvider implements PaymentProvider {
        boolean authentic = true;
        String paymentId = "stub-payment-1";
        PaymentStatus status = PaymentStatus.APPROVED;
        String nextEventId = "event-1";

        @Override public String name() { return "stub"; }
        @Override public PaymentCharge createCharge(ChargeRequest request) {
            return new PaymentCharge(paymentId, "payload", "qr", null, request.expiresAt());
        }
        @Override public PaymentStatus fetchStatus(String providerPaymentId) { return status; }
        @Override public Optional<WebhookNotification> parseAndVerify(Map<String, String> h, String body) {
            return authentic
                    ? Optional.of(new WebhookNotification(nextEventId, paymentId, status))
                    : Optional.empty();
        }
    }

    @TestConfiguration
    static class Config {
        @Bean StubProvider stubProvider() { return new StubProvider(); }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private StubProvider provider;
    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orders;
    @Autowired private ProductService productService;
    @Autowired private ProductRepository products;
    @Autowired private WebhookEventRepository events;

    private Order order;

    @BeforeEach
    void setUp() {
        events.deleteAll();
        orders.deleteAll();
        products.deleteAll();
        provider.authentic = true;
        provider.status = PaymentStatus.APPROVED;
        provider.nextEventId = "event-1";

        Product coffee = productService.create("Café", new BigDecimal("3.50"), null);
        order = orderService.create(List.of(new CreateOrderRequest.Item(coffee.getId(), 1)));
        // Point the order at the stub so the webhook can find it by payment id.
        order = orders.findById(order.getId()).orElseThrow();
        order.attachCharge(provider.paymentId, "payload", "qr", null);
        orders.saveAndFlush(order);
    }

    @Test
    void creditsTheOrderForAnAuthenticNotification() throws Exception {
        mockMvc.perform(post("/api/webhooks/stub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":{\"id\":\"stub-payment-1\"}}"))
                .andExpect(status().isOk());

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    /** An unsigned request must be refused outright and leave no trace. */
    @Test
    void rejectsAnUnverifiedNotificationAndWritesNothing() throws Exception {
        provider.authentic = false;

        mockMvc.perform(post("/api/webhooks/stub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":{\"id\":\"stub-payment-1\"}}"))
                .andExpect(status().isUnauthorized());

        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
        assertThat(events.count()).isZero();
    }

    /**
     * This is the retry shape Mercado Pago actually produces: the same payment event
     * redelivered under a NEW notification id. Deduplicating on the notification id
     * would miss it entirely, so the guard has to be the order's own status.
     */
    @Test
    void creditsExactlyOnceWhenTheSameEventIsRedeliveredUnderANewId() throws Exception {
        mockMvc.perform(post("/api/webhooks/stub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":{\"id\":\"stub-payment-1\"}}"))
                .andExpect(status().isOk());

        provider.nextEventId = "event-2-different-id";

        mockMvc.perform(post("/api/webhooks/stub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":{\"id\":\"stub-payment-1\"}}"))
                .andExpect(status().isOk());

        Order reloaded = orders.findById(order.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(events.count()).isEqualTo(2L);
    }

    @Test
    void returnsOkForAnUnknownPaymentSoTheProviderStopsRetrying() throws Exception {
        provider.paymentId = "payment-we-have-never-seen";

        mockMvc.perform(post("/api/webhooks/stub")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":{\"id\":\"payment-we-have-never-seen\"}}"))
                .andExpect(status().isOk());
    }

    @Test
    void returnsNotFoundForAnUnknownProvider() throws Exception {
        mockMvc.perform(post("/api/webhooks/nonexistent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }
}
