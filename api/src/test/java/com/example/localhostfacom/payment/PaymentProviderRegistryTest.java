package com.example.localhostfacom.payment;

import com.example.localhostfacom.common.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PaymentProviderRegistryTest {

    @Autowired private PaymentProviderRegistry registry;

    @Test
    void resolvesTheConfiguredActiveProvider() {
        assertThat(registry.active().name()).isEqualTo("fake");
    }

    /**
     * An order records the provider that created its charge, so status checks follow the
     * order rather than current configuration. Otherwise switching providers would strand
     * every in-flight order.
     */
    @Test
    void resolvesAProviderByNameRegardlessOfWhichIsActive() {
        assertThat(registry.byName("fake").name()).isEqualTo("fake");
        assertThat(registry.byName("mercadopago").name()).isEqualTo("mercadopago");
    }

    @Test
    void failsLoudlyForAnUnknownProvider() {
        assertThatThrownBy(() -> registry.byName("nonexistent"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void fakeProviderProducesAScannableQrAndAPayload() {
        PaymentCharge charge = registry.byName("fake").createCharge(new ChargeRequest(
                UUID.randomUUID(), new BigDecimal("12.50"), "Pedido", Instant.now().plusSeconds(600)));

        assertThat(charge.providerPaymentId()).isNotBlank();
        assertThat(charge.payload()).contains("12.50");
        assertThat(charge.qrImageBase64()).isNotBlank();
    }

    @Test
    void fakeProviderReportsPendingBeforeTheConfiguredDelay() {
        PaymentCharge charge = registry.byName("fake").createCharge(new ChargeRequest(
                UUID.randomUUID(), new BigDecimal("1.00"), "Pedido", Instant.now().plusSeconds(600)));

        assertThat(registry.byName("fake").fetchStatus(charge.providerPaymentId()))
                .isEqualTo(PaymentStatus.PENDING);
    }
}
