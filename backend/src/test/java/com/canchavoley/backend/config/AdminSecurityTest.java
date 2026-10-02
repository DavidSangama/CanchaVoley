package com.canchavoley.backend.config;

import com.canchavoley.backend.controller.AdminController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@EnableWebSecurity
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "ADMIN_USERNAME=test-admin",
        "ADMIN_PASSWORD=test-password-with-more-than-12-chars"
})
class AdminSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsAdminAuthenticationWithoutCredentials() throws Exception {
        mockMvc.perform(get("/api/admin/authenticate"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsConfiguredAdminCredentials() throws Exception {
        mockMvc.perform(get("/api/admin/authenticate")
                        .header(HttpHeaders.AUTHORIZATION, basicCredentials("test-admin", "test-password-with-more-than-12-chars")))
                .andExpect(status().isNoContent());
    }

    @Test
    void rejectsIncorrectAdminCredentials() throws Exception {
        mockMvc.perform(get("/api/admin/authenticate")
                        .header(HttpHeaders.AUTHORIZATION, basicCredentials("test-admin", "incorrect-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void permitsPublicBookingAvailabilityReads() throws Exception {
        mockMvc.perform(get("/api/reservas/fecha/2026-10-02"))
                .andExpect(status().isNotFound());
    }

    @Test
    void protectsAdminPaymentData() throws Exception {
        mockMvc.perform(get("/api/pagos"))
                .andExpect(status().isUnauthorized());
    }

    private String basicCredentials(String username, String password) {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
