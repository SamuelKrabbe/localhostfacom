package com.example.localhostfacom.settings;

import com.example.localhostfacom.common.ApiException;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {

    private final SettingsRepository settings;

    public SettingsService(SettingsRepository settings) {
        this.settings = settings;
    }

    public Settings get() {
        return settings.get();
    }

    @Transactional
    public Settings update(BigDecimal goalTarget, String crowdfundingUrl) {
        if (goalTarget == null || goalTarget.signum() <= 0) {
            // The dashboard divides by this to size the progress bar.
            throw ApiException.badRequest("invalid-goal", "The goal must be greater than zero");
        }
        Settings current = settings.get();
        current.update(goalTarget, crowdfundingUrl);
        return settings.save(current);
    }
}
