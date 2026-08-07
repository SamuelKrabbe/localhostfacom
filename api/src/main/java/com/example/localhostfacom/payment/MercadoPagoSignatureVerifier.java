package com.example.localhostfacom.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Without this, anyone who knows the webhook URL could forge a payment confirmation and
 * have the system record money that never arrived.
 */
public class MercadoPagoSignatureVerifier {

    private static final Duration MAX_AGE = Duration.ofMinutes(5);

    private final String secret;

    public MercadoPagoSignatureVerifier(String secret) {
        this.secret = secret;
    }

    public boolean verify(Map<String, String> headers, String dataId) {
        String signatureHeader = header(headers, "x-signature");
        String requestId = header(headers, "x-request-id");

        if (signatureHeader == null || dataId == null || secret == null || secret.isBlank()) {
            return false;
        }

        String ts = null;
        String v1 = null;
        for (String part : signatureHeader.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            if ("ts".equals(pair[0].trim())) {
                ts = pair[1].trim();
            } else if ("v1".equals(pair[0].trim())) {
                v1 = pair[1].trim();
            }
        }

        if (ts == null || v1 == null) {
            return false;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(ts);
        } catch (NumberFormatException exception) {
            return false;
        }

        if (Duration.between(Instant.ofEpochSecond(timestamp), Instant.now()).abs().compareTo(MAX_AGE) > 0) {
            return false;
        }

        String manifest = "id:" + dataId + ";request-id:" + (requestId == null ? "" : requestId)
                + ";ts:" + ts + ";";

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
            // Constant time, so a timing side channel cannot leak the correct signature.
            return MessageDigest.isEqual(expected, HexFormat.of().parseHex(v1));
        } catch (Exception exception) {
            return false;
        }
    }

    private String header(Map<String, String> headers, String name) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
