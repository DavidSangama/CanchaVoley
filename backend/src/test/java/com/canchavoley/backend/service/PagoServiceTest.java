package com.canchavoley.backend.service;

import com.canchavoley.backend.model.EstadoPago;
import com.canchavoley.backend.repository.PagoRepository;
import com.canchavoley.backend.repository.ReservaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks
    private PagoService pagoService;

    @Test
    void updatesAllPaymentStatusesAndReturnsTheNumberOfAffectedPayments() {
        when(pagoRepository.actualizarEstadoDeTodos(EstadoPago.REALIZADO)).thenReturn(4);

        int actualizados = pagoService.actualizarEstadoDeTodos(EstadoPago.REALIZADO);

        assertEquals(4, actualizados);
        verify(pagoRepository).actualizarEstadoDeTodos(EstadoPago.REALIZADO);
    }

    @Test
    void rejectsAnEmptyPaymentStatusWithoutUpdatingPayments() {
        assertThrows(IllegalArgumentException.class, () -> pagoService.actualizarEstadoDeTodos(null));
    }
}
