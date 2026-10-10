package com.canchavoley.backend.service;

import com.canchavoley.backend.model.Cliente;
import com.canchavoley.backend.repository.CodigoRecuperacionRepository;
import com.canchavoley.backend.repository.ClienteRepository;
import com.canchavoley.backend.repository.TokenGestionClienteRepository;
import com.canchavoley.backend.dto.ReservaGestionTokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorreoRecuperacionServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private CodigoRecuperacionRepository codigoRepository;

    @Mock
    private TokenGestionClienteRepository tokenRepository;

    @Mock
    private ReservaService reservaService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private GmailApiEmailSender emailSender;

    private CorreoRecuperacionService correoRecuperacionService;

    @BeforeEach
    void setUp() {
        lenient().when(emailSender.estaConfigurado()).thenReturn(true);
        correoRecuperacionService = new CorreoRecuperacionService(
                clienteRepository,
                codigoRepository,
                tokenRepository,
                reservaService,
                passwordEncoder,
                emailSender);
    }

    @Test
    void sendsAHashedTemporaryCodeToTheRegisteredAddress() {
        Cliente cliente = cliente();
        when(clienteRepository.findByCorreoIgnoreCase("cliente@example.com")).thenReturn(Optional.of(cliente));
        when(passwordEncoder.encode(anyString())).thenReturn("bcrypt-code-hash");
        when(codigoRepository.registrarSolicitud(42L, "bcrypt-code-hash")).thenReturn(true);

        var response = correoRecuperacionService.solicitarCodigo(" Cliente@Example.com ");

        assertEquals(
                "Si el correo corresponde a una cuenta, recibirás un código para recuperar tu acceso.",
                response.mensaje());
        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(emailSender).enviar(
                eq("cliente@example.com"),
                eq("Código para recuperar tus reservas"),
                texto.capture());
        assertTrue(texto.getValue().matches("(?s).*\\b\\d{6}\\b.*"));
        verify(codigoRepository).registrarSolicitud(42L, "bcrypt-code-hash");
    }

    @Test
    void unknownEmailReceivesTheSameResponseWithoutSendingMail() {
        when(clienteRepository.findByCorreoIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());

        var response = correoRecuperacionService.solicitarCodigo("unknown@example.com");

        assertEquals(
                "Si el correo corresponde a una cuenta, recibirás un código para recuperar tu acceso.",
                response.mensaje());
        verify(emailSender, never()).enviar(anyString(), anyString(), anyString());
        verify(codigoRepository, never()).registrarSolicitud(any(), anyString());
    }

    @Test
    void rateLimitedRequestDoesNotSendAnotherEmail() {
        when(clienteRepository.findByCorreoIgnoreCase("cliente@example.com"))
                .thenReturn(Optional.of(cliente()));
        when(passwordEncoder.encode(anyString())).thenReturn("bcrypt-code-hash");
        when(codigoRepository.registrarSolicitud(42L, "bcrypt-code-hash")).thenReturn(false);

        correoRecuperacionService.solicitarCodigo("cliente@example.com");

        verify(emailSender, never()).enviar(anyString(), anyString(), anyString());
    }

    @Test
    void approvedCodeRevokesOldAccessAndReturnsOneTokenForEachReservation() {
        Cliente cliente = cliente();
        when(clienteRepository.findByCorreoIgnoreCase("cliente@example.com")).thenReturn(Optional.of(cliente));
        when(codigoRepository.buscarParaActualizar(42L))
                .thenReturn(Optional.of(new CodigoRecuperacionRepository.CodigoPendiente("bcrypt-code-hash", true, 0)));
        when(passwordEncoder.matches("123456", "bcrypt-code-hash")).thenReturn(true);
        when(reservaService.generarTokensGestionCliente(42L))
                .thenReturn(List.of(new ReservaGestionTokenResponse(
                        7L,
                        java.time.LocalDate.of(2026, 10, 10),
                        1L,
                        1,
                        2L,
                        "10:30",
                        java.math.BigDecimal.valueOf(20),
                        null,
                        "reservation-one-token")));

        var response = correoRecuperacionService.verificarCodigo("cliente@example.com", "123456");

        assertEquals(1, response.reservas().size());
        assertEquals(7L, response.reservas().get(0).idReserva());
        assertEquals("reservation-one-token", response.reservas().get(0).tokenGestion());
        verify(codigoRepository).eliminar(42L);
        verify(tokenRepository).deleteAllByClienteIdCliente(42L);
        verify(reservaService).generarTokensGestionCliente(42L);
    }

    @Test
    void invalidCodeIncrementsAttemptsWithoutRevokingAccess() {
        when(clienteRepository.findByCorreoIgnoreCase("cliente@example.com")).thenReturn(Optional.of(cliente()));
        when(codigoRepository.buscarParaActualizar(42L))
                .thenReturn(Optional.of(new CodigoRecuperacionRepository.CodigoPendiente("bcrypt-code-hash", true, 0)));
        when(passwordEncoder.matches("000000", "bcrypt-code-hash")).thenReturn(false);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> correoRecuperacionService.verificarCodigo("cliente@example.com", "000000"));

        assertEquals(401, error.getStatusCode().value());
        verify(codigoRepository).registrarIntentoFallido(42L);
        verify(reservaService, never()).generarTokensGestionCliente(any());
    }

    @Test
    void gmailDiagnosticsIncludeStatusWithoutLoggingResponseBodies() {
        String diagnostico = CorreoRecuperacionService.diagnosticoSeguro(
                new GmailApiEmailSender.DeliveryException(
                        "sensitive provider response",
                        GmailApiEmailSender.Stage.OAUTH,
                        401,
                        new IllegalStateException("sensitive provider response")));

        assertEquals("Google OAuth respondió HTTP 401", diagnostico);
        assertFalse(diagnostico.contains("sensitive"));
    }

    private static Cliente cliente() {
        Cliente cliente = new Cliente();
        cliente.setIdCliente(42L);
        cliente.setCorreo("cliente@example.com");
        return cliente;
    }
}
