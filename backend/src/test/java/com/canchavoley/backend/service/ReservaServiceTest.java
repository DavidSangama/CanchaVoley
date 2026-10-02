package com.canchavoley.backend.service;

import com.canchavoley.backend.model.Cancha;
import com.canchavoley.backend.model.Cliente;
import com.canchavoley.backend.model.EstadoPago;
import com.canchavoley.backend.model.Horario;
import com.canchavoley.backend.model.Pago;
import com.canchavoley.backend.model.Reserva;
import com.canchavoley.backend.model.TokenGestionCliente;
import com.canchavoley.backend.repository.CanchaRepository;
import com.canchavoley.backend.repository.ClienteRepository;
import com.canchavoley.backend.repository.HorarioRepository;
import com.canchavoley.backend.repository.PagoRepository;
import com.canchavoley.backend.repository.ReservaRepository;
import com.canchavoley.backend.repository.TokenGestionClienteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

    @Mock
    private TokenGestionClienteRepository tokenGestionClienteRepository;

    @InjectMocks
    private ReservaService reservaService;

    @Test
    void createsReservationWithRandomManagementTokenAndStoresOnlyItsHash() throws Exception {
        Reserva reserva = new Reserva();
        Cliente cliente = cliente(9L);
        reserva.setCliente(cliente);
        when(clienteRepository.findById(9L)).thenReturn(Optional.of(cliente));
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(invocation -> {
            Reserva saved = invocation.getArgument(0);
            saved.setIdReserva(42L);
            return saved;
        });

        var response = reservaService.crearConTokenCancelacion(reserva);

        assertEquals(42L, response.idReserva());
        assertTrue(response.tokenGestion().length() >= 40);
        assertNotEquals(response.tokenGestion(), reserva.getTokenCancelacionHash());
        assertEquals(hash(response.tokenGestion()), reserva.getTokenCancelacionHash());
        ArgumentCaptor<TokenGestionCliente> tokenCaptor = ArgumentCaptor.forClass(TokenGestionCliente.class);
        verify(tokenGestionClienteRepository).save(tokenCaptor.capture());
        assertEquals(9L, tokenCaptor.getValue().getCliente().getIdCliente());
        assertEquals(hash(response.tokenGestion()), tokenCaptor.getValue().getTokenHash());
    }

    @Test
    void cancellationDeletesPaymentAndReservationWhenTokenIsValidAndPaymentIsPending() throws Exception {
        String token = "customer-cancellation-token";
        Reserva reserva = reservaWithToken(token);
        Pago pago = new Pago();
        pago.setEstado(EstadoPago.PENDIENTE_VERIFICACION);
        when(reservaRepository.findByTokenCancelacionHash(hash(token))).thenReturn(Optional.of(reserva));
        when(reservaRepository.findById(42L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.findByReservaIdReserva(42L)).thenReturn(Optional.of(pago));

        reservaService.cancelarSolicitudCliente(42L, token);

        verify(pagoRepository).deleteByReservaIdReserva(42L);
        verify(reservaRepository).delete(reserva);
    }

    @Test
    void cancellationRejectsAnInvalidTokenWithoutAccessingPayments() {
        when(reservaRepository.findByTokenCancelacionHash(any())).thenReturn(Optional.empty());

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
        when(reservaRepository.findByTokenCancelacionHash(hash(token))).thenReturn(Optional.of(reserva));
        when(reservaRepository.findById(42L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.findByReservaIdReserva(42L)).thenReturn(Optional.of(pago));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> reservaService.cancelarSolicitudCliente(42L, token));

        assertEquals(409, error.getStatusCode().value());
        verify(pagoRepository, never()).deleteByReservaIdReserva(any());
        verify(reservaRepository, never()).delete(any(Reserva.class));
    }

    @Test
    void returnsReservationDetailsOnlyAfterValidatingManagementToken() throws Exception {
        String token = "customer-management-token";
        Reserva reserva = reservationWithSchedule(token);
        Pago pago = new Pago();
        pago.setEstado(EstadoPago.PENDIENTE_VERIFICACION);
        when(reservaRepository.findByIdReservaAndTokenCancelacionHash(42L, hash(token)))
                .thenReturn(Optional.of(reserva));
        when(pagoRepository.findByReservaIdReserva(42L)).thenReturn(Optional.of(pago));

        var response = reservaService.obtenerGestionCliente(42L, token);

        assertEquals(42L, response.idReserva());
        assertEquals(7L, response.idCancha());
        assertEquals(3L, response.idHorario());
        assertEquals("10:30", response.hora());
        assertEquals(EstadoPago.PENDIENTE_VERIFICACION, response.estadoPago());
    }

    @Test
    void returnsAllReservationsForTheCustomerAuthenticatedByTheDashboardToken() throws Exception {
        String token = "customer-dashboard-token";
        Cliente cliente = cliente(9L);
        Reserva primera = reservationWithSchedule(token);
        Reserva segunda = reservationWithSchedule(token);
        segunda.setIdReserva(43L);
        segunda.setFecha(LocalDate.now().plusDays(3));
        Pago pagoPrimera = new Pago();
        pagoPrimera.setEstado(EstadoPago.PENDIENTE_VERIFICACION);
        Pago pagoSegunda = new Pago();
        pagoSegunda.setEstado(EstadoPago.REALIZADO);
        when(tokenGestionClienteRepository.findByTokenHash(hash(token)))
                .thenReturn(Optional.of(new TokenGestionCliente(cliente, hash(token))));
        when(reservaRepository.findByClienteIdClienteOrderByFechaDescIdReservaDesc(9L))
                .thenReturn(java.util.List.of(segunda, primera));
        when(pagoRepository.findByReservaIdReserva(43L)).thenReturn(Optional.of(pagoSegunda));
        when(pagoRepository.findByReservaIdReserva(42L)).thenReturn(Optional.of(pagoPrimera));

        var response = reservaService.obtenerReservasCliente(token);

        assertEquals(2, response.size());
        assertEquals(43L, response.get(0).idReserva());
        assertEquals(EstadoPago.REALIZADO, response.get(0).estadoPago());
        assertEquals(42L, response.get(1).idReserva());
        assertEquals(EstadoPago.PENDIENTE_VERIFICACION, response.get(1).estadoPago());
    }

    @Test
    void existingReservationTokenCanOpenTheCustomerDashboard() throws Exception {
        String token = "legacy-reservation-token";
        Reserva reserva = reservationWithSchedule(token);
        when(reservaRepository.findByTokenCancelacionHash(hash(token))).thenReturn(Optional.of(reserva));
        when(reservaRepository.findByClienteIdClienteOrderByFechaDescIdReservaDesc(9L))
                .thenReturn(java.util.List.of(reserva));
        when(pagoRepository.findByReservaIdReserva(42L)).thenReturn(Optional.empty());

        var response = reservaService.obtenerReservasCliente(token);

        assertEquals(1, response.size());
        assertEquals(42L, response.get(0).idReserva());
        assertNull(response.get(0).estadoPago());
    }

    @Test
    void doesNotAllowDashboardTokenToManageAnotherCustomersReservation() throws Exception {
        String token = "customer-dashboard-token";
        Reserva tokenOwner = reservationWithSchedule(token);
        Reserva otherCustomerReservation = reservationWithSchedule("other-token");
        otherCustomerReservation.setIdReserva(43L);
        otherCustomerReservation.setCliente(cliente(10L));
        when(reservaRepository.findByTokenCancelacionHash(hash(token))).thenReturn(Optional.of(tokenOwner));
        when(reservaRepository.findById(43L)).thenReturn(Optional.of(otherCustomerReservation));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> reservaService.cancelarSolicitudCliente(43L, token));

        assertEquals(404, error.getStatusCode().value());
        verify(pagoRepository, never()).findByReservaIdReserva(43L);
        verify(reservaRepository, never()).delete(otherCustomerReservation);
    }

    @Test
    void reprogramsPendingReservationAndUpdatesRequestedAmount() throws Exception {
        String token = "customer-management-token";
        Reserva reserva = reservationWithSchedule(token);
        Pago pago = new Pago();
        pago.setEstado(EstadoPago.PENDIENTE_VERIFICACION);
        pago.setTotal(BigDecimal.valueOf(20));
        Horario nuevoHorario = new Horario(4L, LocalTime.of(11, 30), BigDecimal.valueOf(35));
        LocalDate nuevaFecha = LocalDate.now().plusDays(2);

        when(reservaRepository.findByTokenCancelacionHash(hash(token))).thenReturn(Optional.of(reserva));
        when(reservaRepository.findById(42L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.findByReservaIdReserva(42L)).thenReturn(Optional.of(pago));
        when(horarioRepository.findById(4L)).thenReturn(Optional.of(nuevoHorario));
        when(reservaRepository.existsByFechaAndCanchaIdCanchaAndHorarioIdHorarioAndIdReservaNot(
                nuevaFecha, 7L, 4L, 42L)).thenReturn(false);
        when(reservaRepository.save(reserva)).thenReturn(reserva);

        var response = reservaService.reprogramarComoCliente(42L, token, nuevaFecha, 4L);

        assertEquals(nuevaFecha, response.fecha());
        assertEquals(4L, response.idHorario());
        assertEquals(BigDecimal.valueOf(35), response.precio());
        assertEquals(BigDecimal.valueOf(35), pago.getTotal());
        verify(pagoRepository).save(pago);
        verify(reservaRepository).save(reserva);
    }

    @Test
    void doesNotReprogramWhenNewTimeIsAlreadyBooked() throws Exception {
        String token = "customer-management-token";
        Reserva reserva = reservationWithSchedule(token);
        Pago pago = new Pago();
        pago.setEstado(EstadoPago.PENDIENTE_VERIFICACION);
        LocalDate nuevaFecha = LocalDate.now().plusDays(2);
        when(reservaRepository.findByTokenCancelacionHash(hash(token))).thenReturn(Optional.of(reserva));
        when(reservaRepository.findById(42L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.findByReservaIdReserva(42L)).thenReturn(Optional.of(pago));
        when(reservaRepository.existsByFechaAndCanchaIdCanchaAndHorarioIdHorarioAndIdReservaNot(
                nuevaFecha, 7L, 4L, 42L)).thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> reservaService.reprogramarComoCliente(42L, token, nuevaFecha, 4L));

        assertEquals(409, error.getStatusCode().value());
        verify(reservaRepository, never()).save(any(Reserva.class));
        verify(pagoRepository, never()).save(any(Pago.class));
    }

    private Reserva reservationWithSchedule(String token) throws Exception {
        Cancha cancha = new Cancha();
        cancha.setIdCancha(7L);
        cancha.setNumeroCancha(2);
        Horario horario = new Horario(3L, LocalTime.of(10, 30), BigDecimal.valueOf(20));
        Reserva reserva = reservaWithToken(token);
        reserva.setCancha(cancha);
        reserva.setHorario(horario);
        reserva.setFecha(LocalDate.now().plusDays(1));
        reserva.setCliente(cliente(9L));
        return reserva;
    }

    private Reserva reservaWithToken(String token) throws Exception {
        Reserva reserva = new Reserva();
        reserva.setIdReserva(42L);
        reserva.setTokenCancelacionHash(hash(token));
        reserva.setCliente(cliente(9L));
        return reserva;
    }

    private Cliente cliente(Long idCliente) {
        Cliente cliente = new Cliente();
        cliente.setIdCliente(idCliente);
        return cliente;
    }

    private String hash(String token) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(bytes);
    }
}
