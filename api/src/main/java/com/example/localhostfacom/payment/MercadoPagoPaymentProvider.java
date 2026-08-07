package com.example.localhostfacom.payment;

import com.example.localhostfacom.common.ApiException;
import com.example.localhostfacom.config.AppProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Talks to the Mercado Pago REST API directly rather than through the official Java SDK:
 * fewer transitive dependencies, and the SDK trails the REST API.
 */
@Component
public class MercadoPagoPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoPaymentProvider.class);

    // Mercado Pago rejects the bare ISO form: it wants milliseconds and a numeric offset,
    // so "Z" is not accepted.
    private static final DateTimeFormatter EXPIRATION_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx");

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final MercadoPagoSignatureVerifier verifier;

    public MercadoPagoPaymentProvider(AppProperties properties, ObjectMapper objectMapper) {
        AppProperties.Payments.MercadoPago config = properties.payments().mercadoPago();
        this.objectMapper = objectMapper;
        this.verifier = new MercadoPagoSignatureVerifier(config.webhookSecret());
        this.client = RestClient.builder()
                .baseUrl(config.baseUrl())
                .defaultHeader("Authorization", "Bearer " + config.accessToken())
                .build();
    }

    @Override
    public String name() {
        return "mercadopago";
    }

    @Override
    public PaymentCharge createCharge(ChargeRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transaction_amount", request.amount());
        body.put("description", request.description());
        body.put("payment_method_id", "pix");
        // Derived from the order row so the provider and the database never disagree
        // about when the charge dies.
        body.put("date_of_expiration", EXPIRATION_FORMAT
                .format(request.expiresAt().atOffset(ZoneOffset.UTC)));
        body.put("payer", Map.of("email", "anonimo@localhostfacom.dev"));

        try {
            JsonNode response = client.post()
                    .uri("/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    // The order id makes the call safely retryable.
                    .header("X-Idempotency-Key", request.orderId().toString())
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                throw ApiException.badGateway("payment-provider-error", "Empty response from Mercado Pago");
            }

            JsonNode transactionData = response
                    .path("point_of_interaction")
                    .path("transaction_data");

            return new PaymentCharge(
                    response.path("id").asText(),
                    transactionData.path("qr_code").asText(null),
                    transactionData.path("qr_code_base64").asText(null),
                    transactionData.path("ticket_url").asText(null),
                    request.expiresAt());
        } catch (ApiException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            // Mercado Pago puts the actionable reason in the body, not in the status line.
            log.error("Mercado Pago rejected the charge for order {}: {} {}",
                    request.orderId(),
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString());
            throw ApiException.badGateway("payment-provider-error",
                    "Could not create the payment charge");
        } catch (RuntimeException exception) {
            log.error("Mercado Pago charge creation failed for order {}", request.orderId(), exception);
            throw ApiException.badGateway("payment-provider-error",
                    "Could not create the payment charge");
        }
    }

    @Override
    public PaymentStatus fetchStatus(String providerPaymentId) {
        try {
            JsonNode response = client.get()
                    .uri("/v1/payments/{id}", providerPaymentId)
                    .retrieve()
                    .body(JsonNode.class);

            return response == null
                    ? PaymentStatus.PENDING
                    : toStatus(response.path("status").asText(""));
        } catch (RuntimeException exception) {
            log.warn("Could not fetch Mercado Pago status for {}", providerPaymentId, exception);
            return PaymentStatus.PENDING;
        }
    }

    @Override
    public Optional<WebhookNotification> parseAndVerify(Map<String, String> headers, String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String dataId = root.path("data").path("id").asText(null);

            if (dataId == null || !verifier.verify(headers, dataId)) {
                return Optional.empty();
            }

            // The notification carries only an id; the authoritative status comes from
            // asking the API, so a forged or stale body cannot dictate the outcome.
            PaymentStatus status = fetchStatus(dataId);
            return Optional.of(new WebhookNotification(root.path("id").asText(null), dataId, status));
        } catch (Exception exception) {
            log.warn("Rejected an unparseable Mercado Pago webhook", exception);
            return Optional.empty();
        }
    }

    private PaymentStatus toStatus(String raw) {
        return switch (raw) {
            case "approved" -> PaymentStatus.APPROVED;
            case "rejected", "cancelled" -> PaymentStatus.REJECTED;
            case "expired" -> PaymentStatus.EXPIRED;
            default -> PaymentStatus.PENDING;
        };
    }
}
