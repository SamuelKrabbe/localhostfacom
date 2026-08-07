package com.example.localhostfacom.admin;

import com.example.localhostfacom.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the very first admin from environment variables. BCrypt hashes cannot be
 * produced inside a Flyway migration, so this runs at startup instead — and only when
 * the table is empty, so it can never silently reset an existing account.
 */
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final AdminRepository admins;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    public BootstrapAdminRunner(AdminRepository admins, PasswordEncoder passwordEncoder,
                                AppProperties properties) {
        this.admins = admins;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (admins.count() > 0) {
            return;
        }

        String email = properties.bootstrapAdmin().email();
        String password = properties.bootstrapAdmin().password();

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("No admins exist and APP_BOOTSTRAP_ADMIN_EMAIL/PASSWORD are not set. "
                    + "The admin panel is unreachable until one is created.");
            return;
        }

        admins.save(Admin.create(email, passwordEncoder.encode(password)));
        log.info("Created the bootstrap admin {}. Change this password after first login.", email);
    }
}
