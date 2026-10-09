package com.canchavoley.backend.service;

import com.canchavoley.backend.model.Cliente;
import com.canchavoley.backend.model.TokenGestionCliente;
import com.canchavoley.backend.repository.CodigoRecuperacionRepository;
import com.canchavoley.backend.repository.ClienteRepository;
import com.canchavoley.backend.repository.ReservaRepository;
import com.canchavoley.backend.repository.TokenGestionClienteRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorreoRecuperacionServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private CodigoRecuperacionRepository codigoRepository;

    @Mock
    private ReservaRepository reservaRepository;

    @Mock
    private TokenGestionClienteRepository tokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JavaMailSender mailSender;

    private CorreoRecuperacionService correoRecuperacionService;

    @BeforeEach
    void setUp() {
        correoRecuperacionService = new CorreoRecuperacionService(
                clienteRepository,
                codigoRepository,
                reservaRepository,
                tokenRepository,
                passwordEncoder,
                mailSender,
                "sender@example.com",
                "app-password");
    }

    @Test
    void sendsAHashedTemporaryCodeToTheRegisteredAddress() throws Exception {
        Cliente cliente = cliente();
        when(clienteRepository.findByCorreoIgnoreCase("cliente@example.com")).thenReturn(Optional.of(cliente));
        when(passwordEncoder.encode(anyString())).thenReturn("bcrypt-code-hash");
        when(codigoRepository.registrarSolicitud(42L, "bcrypt-code-hash")).thenReturn(true);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        var response = correoRecuperacionService.solicitarCodigo(" Cliente@Example.com ");

        assertEquals(
                "Si el correo corresponde a una cuenta, recibirás un código para recuperar tu acceso.",
                response.mensaje());
        ArgumentCaptor<MimeMessage> mensaje = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(mensaje.capture());
        assertTrue(((String) mensaje.getValue().getContent()).matches("(?s).*\\b\\d{6}\\b.*"));
        verify(codigoRepository).registrarSolicitud(42L, "bcrypt-code-hash");
    }

    @Test
    void unknownEmailReceivesTheSameResponseWithoutSendingMail() {
        when(clienteRepository.findByCorreoIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());

        var response = correoRecuperacionService.solicitarCodigo("unknown@example.com");

        assertEquals(
                "Si el correo corresponde a una cuenta, recibirás un código para recuperar tu acceso.",
                response.mensaje());
        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(codigoRepository, never()).registrarSolicitud(any(), anyString());
    }

    @Test
    void rateLimitedRequestDoesNotSendAnotherEmail() {
        when(clienteRepository.findByCorreoIgnoreCase("cliente@example.com"))
                .thenReturn(Optional.of(cliente()));
        when(passwordEncoder.encode(anyString())).thenReturn("bcrypt-code-hash");
        when(codigoRepository.registrarSolicitud(42L, "bcrypt-code-hash")).thenReturn(false);

        correoRecuperacionService.solicitarCodigo("cliente@example.com");

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void approvedCodeRevokesOldAccessAndReturnsANewToken() {
        Cliente cliente = cliente();
        when(clienteRepository.findByCorreoIgnoreCase("cliente@example.com")).thenReturn(Optional.of(cliente));
        when(codigoRepository.buscarParaActualizar(42L))
                .thenReturn(Optional.of(new CodigoRecuperacionRepository.CodigoPendiente("bcrypt-code-hash", true, 0)));
        when(passwordEncoder.matches("123456", "bcrypt-code-hash")).thenReturn(true);

        var response = correoRecuperacionService.verificarCodigo("cliente@example.com", "123456");

        assertEquals(43, response.tokenGestion().length());
        verify(codigoRepository).eliminar(42L);
        verify(reservaRepository).revocarTokensDeCliente(42L);
        verify(tokenRepository).deleteAllByClienteIdCliente(42L);
        ArgumentCaptor<TokenGestionCliente> token = ArgumentCaptor.forClass(TokenGestionCliente.class);
        verify(tokenRepository).save(token.capture());
        assertEquals(TokenGestionUtils.hashToken(response.tokenGestion()), token.getValue().getTokenHash());
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
        verify(reservaRepository, never()).revocarTokensDeCliente(any());
        verify(tokenRepository, never()).save(any());
    }

    private static Cliente cliente() {
        Cliente cliente = new Cliente();
        cliente.setIdCliente(42L);
        cliente.setCorreo("cliente@example.com");
        return cliente;
    }
}
