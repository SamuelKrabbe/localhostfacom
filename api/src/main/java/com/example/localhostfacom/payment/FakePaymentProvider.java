package com.example.localhostfacom.payment;

import com.example.localhostfacom.config.AppProperties;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Runs the whole customer flow with no credentials. Charges report PENDING until the
 * configured delay elapses, then APPROVED, so the payment screen's polling can be
 * exercised end to end. The application refuses to start with this provider active
 * under the prod profile (see PaymentProviderGuard).
 */
@Component
public class FakePaymentProvider implements PaymentProvider {

    private final Map<String, Instant> createdAt = new ConcurrentHashMap<>();
    private final Duration autoConfirmAfter;

    public FakePaymentProvider(AppProperties properties) {
        this.autoConfirmAfter = properties.payments().fake().autoConfirmAfter();
    }

    @Override
    public String name() {
        return "fake";
    }

    @Override
    public PaymentCharge createCharge(ChargeRequest request) {
        String paymentId = "fake-" + UUID.randomUUID();
        createdAt.put(paymentId, Instant.now());

        String payload = "00020126FAKE-PIX-PAYLOAD"
                + "-order-" + request.orderId()
                + "-amount-" + request.amount().toPlainString()
                + "-6304FAKE";

        return new PaymentCharge(paymentId, payload, qrCodeBase64(payload), null, request.expiresAt());
    }

    @Override
    public PaymentStatus fetchStatus(String providerPaymentId) {
        Instant created = createdAt.get(providerPaymentId);
        if (created == null) {
            return PaymentStatus.PENDING;
        }
        return created.plus(autoConfirmAfter).isAfter(Instant.now())
                ? PaymentStatus.PENDING
                : PaymentStatus.APPROVED;
    }

    @Override
    public Optional<WebhookNotification> parseAndVerify(Map<String, String> headers, String rawBody) {
        // The fake provider never calls back; confirmation arrives through polling.
        return Optional.empty();
    }

    private String qrCodeBase64(String payload) {
        try {
            var matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 256, 256);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (WriterException | IOException exception) {
            throw new IllegalStateException("Could not render the fake QR code", exception);
        }
    }
}
