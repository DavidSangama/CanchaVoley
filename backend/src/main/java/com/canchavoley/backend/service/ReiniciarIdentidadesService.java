package com.canchavoley.backend.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ReiniciarIdentidadesService {

    private final JdbcTemplate jdbcTemplate;

    public ReiniciarIdentidadesService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void reiniciarPagos() {
        reiniciarDesdeUno("pago", "id_pago");
    }

    public void reiniciarReservas() {
        reiniciarDesdeUno("reserva", "id_reserva");
    }

    public void reiniciarClientes() {
        reiniciarDesdeUno("cliente", "id_cliente");
    }

    public void reiniciarTokensGestion() {
        reiniciarDesdeUno("cliente_token_gestion", "id_token_gestion");
    }

    private void reiniciarDesdeUno(String tabla, String columna) {
        String nombreTabla = "renta_cancha." + tabla;
        String secuencia = jdbcTemplate.queryForObject(
                "SELECT pg_get_serial_sequence(?, ?)",
                String.class,
                nombreTabla,
                columna);
        if (secuencia == null) {
            throw new IllegalStateException("No se encontró la secuencia de " + nombreTabla + "." + columna);
        }
        jdbcTemplate.execute("ALTER SEQUENCE " + secuencia + " MINVALUE 1 RESTART WITH 1");
    }
}
