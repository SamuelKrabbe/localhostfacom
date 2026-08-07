package com.example.localhostfacom.auth;

import com.example.localhostfacom.admin.Admin;
import com.example.localhostfacom.admin.AdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AdminRepository admins;
    @Autowired private PasswordEncoder passwordEncoder;

    private Admin admin;

    @BeforeEach
    void setUp() {
        admins.deleteAll();
        admin = admins.save(Admin.create("owner@example.com", passwordEncoder.encode("correct-horse")));
    }

    @Test
    void rejectsAdminRoutesWithoutAToken() throws Exception {
        mockMvc.perform(get("/api/admin/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void issuesATokenForValidCredentialsAndAcceptsIt() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@example.com\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("owner@example.com"))
                .andReturn().getResponse().getContentAsString();

        String token = extractToken(body);

        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("owner@example.com"));
    }

    @Test
    void rejectsAWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsATamperedToken() throws Exception {
        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * The rotating-admin requirement: removing someone must take effect immediately,
     * not whenever their token happens to expire.
     */
    @Test
    void rejectsATokenBelongingToADeactivatedAdmin() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@example.com\",\"password\":\"correct-horse\"}"))
                .andReturn().getResponse().getContentAsString();
        String token = extractToken(body);

        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        admin.deactivate();
        admins.saveAndFlush(admin);

        mockMvc.perform(get("/api/admin/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicRoutesNeedNoToken() throws Exception {
        mockMvc.perform(get("/api/public/products")).andExpect(status().isOk());
    }

    // No com.jayway.jsonpath on the test classpath — Boot 4 dropped the monolithic
    // spring-boot-starter-test in favor of per-feature -test starters. The token is the
    // only field these tests need to pull out, so a regex is simpler than adding a
    // dependency for one line.
    private String extractToken(String json) {
        var matcher = java.util.regex.Pattern.compile("\"token\":\"([^\"]+)\"").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("No token field in response: " + json);
        }
        return matcher.group(1);
    }
}
