package com.example.localhostfacom.settings.dto;

import com.example.localhostfacom.settings.Settings;
import java.math.BigDecimal;

public record SettingsResponse(BigDecimal goalTarget, String crowdfundingUrl) {

    public static SettingsResponse of(Settings settings) {
        return new SettingsResponse(settings.getGoalTarget(), settings.getCrowdfundingUrl());
    }
}
