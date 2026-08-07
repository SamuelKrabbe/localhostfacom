package com.example.localhostfacom.payment;

import com.example.localhostfacom.common.ApiException;
import com.example.localhostfacom.config.AppProperties;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PaymentProviderRegistry {

    private final Map<String, PaymentProvider> byName;
    private final String activeName;

    public PaymentProviderRegistry(List<PaymentProvider> providers, AppProperties properties) {
        this.byName = providers.stream()
                .collect(Collectors.toMap(PaymentProvider::name, Function.identity()));
        this.activeName = properties.payments().activeProvider();

        if (!byName.containsKey(activeName)) {
            throw new IllegalStateException(
                    "app.payments.active-provider is '" + activeName + "' but only "
                            + byName.keySet() + " are registered");
        }
    }

    public PaymentProvider active() {
        return byName.get(activeName);
    }

    public PaymentProvider byName(String name) {
        PaymentProvider provider = byName.get(name);
        if (provider == null) {
            throw ApiException.notFound("unknown-payment-provider", "Unknown payment provider: " + name);
        }
        return provider;
    }
}
