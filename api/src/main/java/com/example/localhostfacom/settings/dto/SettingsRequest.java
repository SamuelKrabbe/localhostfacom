package com.example.localhostfacom.settings.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record SettingsRequest(
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal goalTarget,
        // Rendered as an <a href> on the public dashboard; restricting the scheme rules
        // out a javascript: URI ever landing there. Null clears the link entirely.
        @Size(max = 1024) @Pattern(regexp = "^https?://.+") String crowdfundingUrl) {}
