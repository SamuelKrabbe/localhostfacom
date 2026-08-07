package com.example.localhostfacom.admin;

import com.example.localhostfacom.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AdminManagementTest {

    @Autowired private AdminService service;
    @Autowired private AdminRepository admins;
    @Autowired private PasswordEncoder passwordEncoder;

    private Admin first;

    @BeforeEach
    void setUp() {
        admins.deleteAll();
        first = admins.save(Admin.create("first@example.com", passwordEncoder.encode("password-one")));
    }

    @Test
    void addsAnAdminByEmail() {
        Admin created = service.create("second@example.com", "password-two");

        assertThat(created.getEmail()).isEqualTo("second@example.com");
        assertThat(service.list()).hasSize(2);
    }

    @Test
    void refusesADuplicateEmail() {
        assertThatThrownBy(() -> service.create("FIRST@example.com", "whatever"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already");
    }

    @Test
    void refusesToRemoveYourself() {
        Admin second = service.create("second@example.com", "password-two");

        assertThatThrownBy(() -> service.remove(second.getId(), second.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("yourself");
    }

    /** The role rotates; removing the last account would lock everyone out permanently. */
    @Test
    void refusesToRemoveTheLastActiveAdmin() {
        Admin second = service.create("second@example.com", "password-two");
        service.remove(second.getId(), first.getId());

        assertThatThrownBy(() -> service.remove(first.getId(), second.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("last");
    }

    @Test
    void removingAnAdminDeactivatesRatherThanDeletes() {
        Admin second = service.create("second@example.com", "password-two");
        service.remove(second.getId(), first.getId());

        assertThat(admins.findById(second.getId())).isPresent();
        assertThat(admins.findById(second.getId()).orElseThrow().isActive()).isFalse();
        assertThat(admins.countByActiveTrue()).isEqualTo(1L);
    }
}
