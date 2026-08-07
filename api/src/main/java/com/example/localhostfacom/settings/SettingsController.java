package com.example.localhostfacom.settings;

import com.example.localhostfacom.settings.dto.SettingsRequest;
import com.example.localhostfacom.settings.dto.SettingsResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/settings")
public class SettingsController {

    private final SettingsService service;

    public SettingsController(SettingsService service) {
        this.service = service;
    }

    @GetMapping
    public SettingsResponse get() {
        return SettingsResponse.of(service.get());
    }

    @PutMapping
    public SettingsResponse update(@Valid @RequestBody SettingsRequest request) {
        return SettingsResponse.of(service.update(request.goalTarget(), request.crowdfundingUrl()));
    }
}
