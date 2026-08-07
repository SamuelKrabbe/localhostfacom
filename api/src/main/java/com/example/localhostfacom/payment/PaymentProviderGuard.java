package com.example.localhostfacom.payment;

import com.example.localhostfacom.config.AppProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Refuses to start a production instance wired to the fake provider, which would accept
 * orders and mark them paid without any money ever moving.
 */
@Configuration
@Profile("prod")
public class PaymentProviderGuard {

    public PaymentProviderGuard(AppProperties properties) {
        if ("fake".equals(properties.payments().activeProvider())) {
            throw new IllegalStateException(
                    "app.payments.active-provider must not be 'fake' under the prod profile");
        }
    }
}
