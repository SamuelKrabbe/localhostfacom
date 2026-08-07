package com.example.localhostfacom.settings;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingsRepository extends JpaRepository<Settings, Short> {

    /** The table is constrained to a single row with id = 1. */
    default Settings get() {
        return findById((short) 1)
                .orElseThrow(() -> new IllegalStateException("settings row is missing"));
    }
}
