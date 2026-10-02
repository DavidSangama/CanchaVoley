package com.canchavoley.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReiniciarIdentidadesServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private ReiniciarIdentidadesService reiniciarIdentidadesService;

    @Test
    void restartsIdentitySequenceAtZero() {
        when(jdbcTemplate.queryForObject(
                "SELECT pg_get_serial_sequence(?, ?)",
                String.class,
                "renta_cancha.pago",
                "id_pago"))
                .thenReturn("renta_cancha.pago_id_pago_seq");

        reiniciarIdentidadesService.reiniciarPagos();

        verify(jdbcTemplate).execute(
                "ALTER SEQUENCE renta_cancha.pago_id_pago_seq MINVALUE 1 RESTART WITH 1");
    }

    @Test
    void reportsMissingIdentitySequence() {
        when(jdbcTemplate.queryForObject(
                "SELECT pg_get_serial_sequence(?, ?)",
                String.class,
                "renta_cancha.cliente",
                "id_cliente"))
                .thenReturn(null);

        assertThrows(IllegalStateException.class, reiniciarIdentidadesService::reiniciarClientes);
    }
}
