package com.canchavoley.backend.controller;

import com.canchavoley.backend.config.SecurityConfig;
import com.canchavoley.backend.model.EstadoPago;
import com.canchavoley.backend.service.PagoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PagoController.class)
@EnableWebSecurity
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "ADMIN_USERNAME=test-admin",
        "ADMIN_PASSWORD=test-password-with-more-than-12-chars"
})
class PagoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PagoService pagoService;

    @Test
    void updatesEveryPaymentToTheSelectedStateAndReturnsAffectedCount() throws Exception {
        when(pagoService.actualizarEstadoDeTodos(EstadoPago.CANCELADO)).thenReturn(3);

        mockMvc.perform(put("/api/pagos/estado/todos")
                        .header(HttpHeaders.AUTHORIZATION, adminCredentials())
                        .param("nuevoEstado", "CANCELADO"))
                .andExpect(status().isOk())
                .andExpect(content().json("3"));

        verify(pagoService).actualizarEstadoDeTodos(EstadoPago.CANCELADO);
    }

    private String adminCredentials() {
        String credentials = "test-admin:test-password-with-more-than-12-chars";
        return "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
