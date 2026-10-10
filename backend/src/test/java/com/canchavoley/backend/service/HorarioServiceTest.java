package com.canchavoley.backend.service;

import com.canchavoley.backend.model.EstadoPago;
import com.canchavoley.backend.model.Horario;
import com.canchavoley.backend.model.Pago;
import com.canchavoley.backend.model.Reserva;
import com.canchavoley.backend.repository.HorarioRepository;
import com.canchavoley.backend.repository.PagoRepository;
import com.canchavoley.backend.repository.ReservaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HorarioServiceTest {

    @Mock
    private HorarioRepository horarioRepository;

    @Mock
    private PagoRepository pagoRepository;

    @Mock
    private ReservaRepository reservaRepository;

    @InjectMocks
    private HorarioService horarioService;

    @Test
    void changingSchedulePriceUpdatesOnlyReservationsWithPendingPayments() {
        BigDecimal precio = BigDecimal.valueOf(45);
        Horario horario = new Horario(3L, LocalTime.of(10, 30), BigDecimal.valueOf(20));
        Reserva reserva = new Reserva();
        reserva.setIdReserva(42L);
        reserva.setHorario(horario);
        reserva.setPrecio(BigDecimal.valueOf(20));
        Pago pagoPendiente = new Pago();
        pagoPendiente.setReserva(reserva);
        pagoPendiente.setTotal(BigDecimal.valueOf(20));
        pagoPendiente.setEstado(EstadoPago.PENDIENTE_VERIFICACION);

        when(horarioRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(horario));
        when(pagoRepository.findByReserva_Horario_IdHorarioAndEstado(
                3L, EstadoPago.PENDIENTE_VERIFICACION)).thenReturn(List.of(pagoPendiente));

        horarioService.actualizarPrecio(3L, precio);

        assertEquals(precio, horario.getPrecio());
        assertEquals(precio, reserva.getPrecio());
        assertEquals(precio, pagoPendiente.getTotal());
        verify(reservaRepository).saveAll(List.of(reserva));
        verify(pagoRepository).saveAll(List.of(pagoPendiente));
    }
}
