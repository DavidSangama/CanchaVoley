package com.canchavoley.backend.service;

import com.canchavoley.backend.model.EstadoPago;
import com.canchavoley.backend.model.Cancha;
import com.canchavoley.backend.model.Horario;
import com.canchavoley.backend.model.Pago;
import com.canchavoley.backend.model.Reserva;
import com.canchavoley.backend.repository.PagoRepository;
import com.canchavoley.backend.repository.ReservaRepository;
import com.canchavoley.backend.repository.HorarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PagoServiceTest {

    @Mock
    private PagoRepository pagoRepository;

    @Mock
    private ReservaRepository reservaRepository;

    @Mock
    private HorarioRepository horarioRepository;

    @InjectMocks
    private PagoService pagoService;

    @Test
    void updatesAllPaymentStatusesAndReturnsTheNumberOfAffectedPayments() {
        List<Pago> pagos = List.of(pago(1L), pago(2L), pago(3L), pago(4L));
        when(pagoRepository.findAll()).thenReturn(pagos);
        when(horarioRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(new Horario(10L, null, null)));

        int actualizados = pagoService.actualizarEstadoDeTodos(EstadoPago.REALIZADO);

        assertEquals(4, actualizados);
        pagos.forEach(pago -> assertEquals(EstadoPago.REALIZADO, pago.getEstado()));
        verify(horarioRepository).findByIdForUpdate(10L);
        verify(pagoRepository).saveAll(pagos);
    }

    @Test
    void changesSinglePaymentStateAfterLockingItsSchedule() {
        Pago pago = pago(1L);
        when(pagoRepository.findById(1L)).thenReturn(Optional.of(pago));
        when(horarioRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(new Horario(10L, null, null)));
        when(pagoRepository.save(pago)).thenReturn(pago);

        Pago actualizado = pagoService.actualizarEstado(1L, EstadoPago.CANCELADO);

        assertEquals(EstadoPago.CANCELADO, actualizado.getEstado());
        verify(horarioRepository).findByIdForUpdate(10L);
        verify(pagoRepository).save(pago);
    }

    @Test
    void genericAdminUpdateLocksScheduleBeforeChangingPaymentStatus() {
        Pago existente = pago(1L);
        Reserva reserva = existente.getReserva();
        Pago detalles = pago(1L);
        detalles.setTotal(java.math.BigDecimal.TEN);
        detalles.setEstado(EstadoPago.CANCELADO);
        when(pagoRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reserva));
        when(horarioRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.of(new Horario(10L, null, null)));
        when(pagoRepository.save(existente)).thenReturn(existente);

        Pago actualizado = pagoService.actualizar(1L, detalles);

        assertEquals(EstadoPago.CANCELADO, actualizado.getEstado());
        verify(horarioRepository).findByIdForUpdate(10L);
    }

    @Test
    void rejectsAnEmptyPaymentStatusWithoutUpdatingPayments() {
        assertThrows(IllegalArgumentException.class, () -> pagoService.actualizarEstadoDeTodos(null));
    }

    private static Pago pago(Long idPago) {
        Horario horario = new Horario(10L, null, null);
        Reserva reserva = new Reserva();
        reserva.setIdReserva(idPago);
        reserva.setHorario(horario);
        Pago pago = new Pago();
        pago.setIdPago(idPago);
        pago.setReserva(reserva);
        pago.setEstado(EstadoPago.PENDIENTE_VERIFICACION);
        return pago;
    }
}
