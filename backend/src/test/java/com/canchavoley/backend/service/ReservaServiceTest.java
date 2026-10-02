package com.canchavoley.backend.service;

import com.canchavoley.backend.model.EstadoPago;
import com.canchavoley.backend.model.Pago;
import com.canchavoley.backend.model.Reserva;
import com.canchavoley.backend.repository.CanchaRepository;
import com.canchavoley.backend.repository.ClienteRepository;
import com.canchavoley.backend.repository.HorarioRepository;
import com.canchavoley.backend.repository.PagoRepository;
import com.canchavoley.backend.repository.ReservaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

    @Mock
    private ReservaRepository reservaRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private CanchaRepository canchaRepository;

    @Mock
    private HorarioRepository horarioRepository;

    @Mock
    private PagoRepository pagoRepository;

    @InjectMocks
    private ReservaService reservaService;

    @Test
    void createsReservationWithRandomCancellationTokenAndStoresOnlyItsHash() throws Exception {
        Reserva reserva = new Reserva();
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(invocation -> {
            Reserva saved = invocation.getArgument(0);
            saved.setIdReserva(42L);
            return saved;
        });

        var response = reservaService.crearConTokenCancelacion(reserva);

        assertEquals(42L, response.idReserva());
        assertTrue(response.tokenCancelacion().length() >= 40);
        assertNotEquals(response.tokenCancelacion(), reserva.getTokenCancelacionHash());
        assertEquals(hash(response.tokenCancelacion()), reserva.getTokenCancelacionHash());
    }

    @Test
    void cancellationDeletesPaymentAndReservationWhenTokenIsValidAndPaymentIsPending() throws Exception {
        String token = "customer-cancellation-token";
        Reserva reserva = reservaWithToken(token);
        Pago pago = new Pago();
        pago.setEstado(EstadoPago.PENDIENTE_VERIFICACION);
        when(reservaRepository.findByIdReservaAndTokenCancelacionHash(42L, hash(token)))
                .thenReturn(Optional.of(reserva));
        when(pagoRepository.findByReservaIdReserva(42L)).thenReturn(Optional.of(pago));

        reservaService.cancelarSolicitudCliente(42L, token);

        verify(pagoRepository).deleteByReservaIdReserva(42L);
        verify(reservaRepository).delete(reserva);
    }

    @Test
    void cancellationRejectsAnInvalidTokenWithoutAccessingPayments() {
        when(reservaRepository.findByIdReservaAndTokenCancelacionHash(eq(42L), any()))
                .thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> reservaService.cancelarSolicitudCliente(42L, "invalid-token"));

        verify(pagoRepository, never()).findByReservaIdReserva(any());
        verify(reservaRepository, never()).delete(any(Reserva.class));
    }

    @Test
    void cancellationRejectsCompletedPaymentsAndKeepsTheReservation() throws Exception {
        String token = "customer-cancellation-token";
        Reserva reserva = reservaWithToken(token);
        Pago pago = new Pago();
        pago.setEstado(EstadoPago.REALIZADO);
        when(reservaRepository.findByIdReservaAndTokenCancelacionHash(42L, hash(token)))
                .thenReturn(Optional.of(reserva));
        when(pagoRepository.findByReservaIdReserva(42L)).thenReturn(Optional.of(pago));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> reservaService.cancelarSolicitudCliente(42L, token));

        assertEquals(409, error.getStatusCode().value());
        verify(pagoRepository, never()).deleteByReservaIdReserva(any());
        verify(reservaRepository, never()).delete(any(Reserva.class));
    }

    private Reserva reservaWithToken(String token) throws Exception {
        Reserva reserva = new Reserva();
        reserva.setIdReserva(42L);
        reserva.setTokenCancelacionHash(hash(token));
        return reserva;
    }

    private String hash(String token) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(bytes);
    }
}
