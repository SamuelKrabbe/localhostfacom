package com.example.localhostfacom.payment;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MercadoPagoSignatureVerifierTest {

    private static final String SECRET = "webhook-secret";

    private final MercadoPagoSignatureVerifier verifier = new MercadoPagoSignatureVerifier(SECRET);

    private String sign(String dataId, String requestId, long ts) throws Exception {
        String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
    }

    private Map<String, String> headers(String signature, String requestId, long ts) {
        return Map.of(
                "x-signature", "ts=" + ts + ",v1=" + signature,
                "x-request-id", requestId);
    }

    @Test
    void acceptsACorrectlySignedRequest() throws Exception {
        long ts = Instant.now().getEpochSecond();
        assertThat(verifier.verify(headers(sign("123", "req-1", ts), "req-1", ts), "123")).isTrue();
    }

    @Test
    void rejectsATamperedPaymentId() throws Exception {
        long ts = Instant.now().getEpochSecond();
        assertThat(verifier.verify(headers(sign("123", "req-1", ts), "req-1", ts), "999")).isFalse();
    }

    @Test
    void rejectsAWrongSignature() {
        long ts = Instant.now().getEpochSecond();
        assertThat(verifier.verify(headers("deadbeef", "req-1", ts), "123")).isFalse();
    }

    /** An old capture must not be replayable. */
    @Test
    void rejectsAStaleTimestamp() throws Exception {
        long ts = Instant.now().minusSeconds(3600).getEpochSecond();
        assertThat(verifier.verify(headers(sign("123", "req-1", ts), "req-1", ts), "123")).isFalse();
    }

    @Test
    void rejectsAMissingSignatureHeader() {
        assertThat(verifier.verify(Map.of("x-request-id", "req-1"), "123")).isFalse();
    }

    @Test
    void rejectsAMalformedSignatureHeader() {
        assertThat(verifier.verify(Map.of("x-signature", "garbage", "x-request-id", "r"), "123")).isFalse();
    }
}
