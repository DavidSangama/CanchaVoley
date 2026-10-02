package com.canchavoley.backend.service;

import com.canchavoley.backend.repository.ClienteRepository;
import com.canchavoley.backend.repository.PagoRepository;
import com.canchavoley.backend.repository.ReservaRepository;
import com.canchavoley.backend.repository.TokenGestionClienteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VaciadoDatosServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private ReservaRepository reservaRepository;

    @Mock
    private PagoRepository pagoRepository;

    @Mock
    private TokenGestionClienteRepository tokenGestionClienteRepository;

    @Mock
    private ReiniciarIdentidadesService reiniciarIdentidadesService;

    @InjectMocks
    private ClienteService clienteService;

    @InjectMocks
    private ReservaService reservaService;

    @InjectMocks
    private PagoService pagoService;

    @Test
    void vaciarPagosOnlyDeletesPayments() {
        when(pagoRepository.count()).thenReturn(5L);

        var resultado = pagoService.vaciarPagos();

        assertEquals(0, resultado.clientesEliminados());
        assertEquals(0, resultado.reservasEliminadas());
        assertEquals(5, resultado.pagosEliminados());
        verify(pagoRepository).deleteAllInBatch();
        verify(reiniciarIdentidadesService).reiniciarPagos();
    }

    @Test
    void vaciarReservasDeletesPaymentsBeforeReservations() {
        when(reservaRepository.count()).thenReturn(3L);
        when(pagoRepository.count()).thenReturn(2L);

        var resultado = reservaService.vaciarReservas();

        assertEquals(0, resultado.clientesEliminados());
        assertEquals(3, resultado.reservasEliminadas());
        assertEquals(2, resultado.pagosEliminados());
        InOrder orden = inOrder(pagoRepository, reservaRepository);
        orden.verify(pagoRepository).deleteAllInBatch();
        orden.verify(reservaRepository).deleteAllInBatch();
        verify(reiniciarIdentidadesService).reiniciarPagos();
        verify(reiniciarIdentidadesService).reiniciarReservas();
    }

    @Test
    void vaciarClientesDeletesDependentsBeforeCustomersAndPrivateTokens() {
        when(clienteRepository.count()).thenReturn(4L);
        when(reservaRepository.count()).thenReturn(3L);
        when(pagoRepository.count()).thenReturn(2L);

        var resultado = clienteService.vaciarClientes();

        assertEquals(4, resultado.clientesEliminados());
        assertEquals(3, resultado.reservasEliminadas());
        assertEquals(2, resultado.pagosEliminados());
        InOrder orden = inOrder(
                tokenGestionClienteRepository,
                pagoRepository,
                reservaRepository,
                clienteRepository);
        orden.verify(tokenGestionClienteRepository).deleteAllInBatch();
        orden.verify(pagoRepository).deleteAllInBatch();
        orden.verify(reservaRepository).deleteAllInBatch();
        orden.verify(clienteRepository).deleteAllInBatch();
        verify(reiniciarIdentidadesService).reiniciarTokensGestion();
        verify(reiniciarIdentidadesService).reiniciarPagos();
        verify(reiniciarIdentidadesService).reiniciarReservas();
        verify(reiniciarIdentidadesService).reiniciarClientes();
    }
}
