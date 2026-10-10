package com.canchavoley.backend.controller;

import com.canchavoley.backend.config.SecurityConfig;
import com.canchavoley.backend.dto.ReservaCreadaResponse;
import com.canchavoley.backend.dto.ReservaGestionResponse;
import com.canchavoley.backend.dto.ReservaGestionTokenResponse;
import com.canchavoley.backend.dto.AccesoReservasResponse;
import com.canchavoley.backend.dto.RecuperacionCorreoResponse;
import com.canchavoley.backend.model.EstadoPago;
import com.canchavoley.backend.service.ReservaService;
import com.canchavoley.backend.service.CorreoRecuperacionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservaController.class)
@EnableWebSecurity
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "ADMIN_USERNAME=test-admin",
        "ADMIN_PASSWORD=test-password-with-more-than-12-chars"
})
class ReservaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservaService reservaService;

    @MockitoBean
    private CorreoRecuperacionService correoRecuperacionService;

    @Test
    void creatingReservationReturnsThePrivateManagementToken() throws Exception {
        when(reservaService.crearConTokenCancelacion(any()))
                .thenReturn(new ReservaCreadaResponse(42L, "private-management-token"));

        mockMvc.perform(post("/api/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fecha\":\"2026-10-10\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idReserva").value(42))
                .andExpect(jsonPath("$.tokenGestion").value("private-management-token"));
    }

    @Test
    void returnsCustomerReservationsOnlyWithThePrivateTokenHeader() throws Exception {
        when(reservaService.obtenerReservasCliente("private-management-token"))
                .thenReturn(java.util.List.of(new ReservaGestionResponse(
                        42L,
                        java.time.LocalDate.of(2026, 10, 10),
                        7L,
                        2,
                        3L,
                        "10:30",
                        java.math.BigDecimal.valueOf(20),
                        EstadoPago.PENDIENTE_VERIFICACION)));

        mockMvc.perform(get("/api/reservas/gestion/cliente")
                        .header("X-Reservation-Token", "private-management-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idReserva").value(42))
                .andExpect(jsonPath("$[0].estadoPago").value("PENDIENTE_VERIFICACION"));
    }

    @Test
    void requestsRecoveryCodeWithoutRevealingWhetherEmailIsRegistered() throws Exception {
        when(correoRecuperacionService.solicitarCodigo("cliente@example.com"))
                .thenReturn(new RecuperacionCorreoResponse(
                        "Si el correo corresponde a una cuenta, recibirás un código para recuperar tu acceso."));

        mockMvc.perform(post("/api/reservas/recuperacion-correo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"cliente@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensaje")
                        .value("Si el correo corresponde a una cuenta, recibirás un código para recuperar tu acceso."));
    }

    @Test
    void verifiesRecoveryCodeWithoutAuthentication() throws Exception {
        when(correoRecuperacionService.verificarCodigo("cliente@example.com", "123456"))
                .thenReturn(new AccesoReservasResponse(java.util.List.of(
                        new ReservaGestionTokenResponse(
                                42L,
                                java.time.LocalDate.of(2026, 10, 10),
                                7L,
                                2,
                                3L,
                                "10:30",
                                java.math.BigDecimal.valueOf(20),
                                EstadoPago.PENDIENTE_VERIFICACION,
                                "first-private-token"),
                        new ReservaGestionTokenResponse(
                                43L,
                                java.time.LocalDate.of(2026, 10, 11),
                                8L,
                                3,
                                4L,
                                "11:30",
                                java.math.BigDecimal.valueOf(30),
                                EstadoPago.REALIZADO,
                                "second-private-token"))));

        mockMvc.perform(post("/api/reservas/recuperacion-correo/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"cliente@example.com\",\"codigo\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservas[0].idReserva").value(42))
                .andExpect(jsonPath("$.reservas[0].tokenGestion").value("first-private-token"))
                .andExpect(jsonPath("$.reservas[1].idReserva").value(43))
                .andExpect(jsonPath("$.reservas[1].tokenGestion").value("second-private-token"));
    }
}
