package com.canchavoley.backend.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CodigoRecuperacionRepository {

    private static final String REGISTRAR_SOLICITUD = """
            INSERT INTO renta_cancha.recuperacion_correo
                (id_cliente, codigo_hash, expira_en, inicio_ventana, solicitudes, intentos)
            VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '10 minutes', CURRENT_TIMESTAMP, 1, 0)
            ON CONFLICT (id_cliente) DO UPDATE
            SET codigo_hash = EXCLUDED.codigo_hash,
                expira_en = EXCLUDED.expira_en,
                solicitudes = CASE
                    WHEN renta_cancha.recuperacion_correo.inicio_ventana
                         <= CURRENT_TIMESTAMP - INTERVAL '15 minutes' THEN 1
                    ELSE renta_cancha.recuperacion_correo.solicitudes + 1
                END,
                inicio_ventana = CASE
                    WHEN renta_cancha.recuperacion_correo.inicio_ventana
                         <= CURRENT_TIMESTAMP - INTERVAL '15 minutes' THEN CURRENT_TIMESTAMP
                    ELSE renta_cancha.recuperacion_correo.inicio_ventana
                END,
                intentos = 0
            WHERE renta_cancha.recuperacion_correo.inicio_ventana
                      <= CURRENT_TIMESTAMP - INTERVAL '15 minutes'
               OR renta_cancha.recuperacion_correo.solicitudes < 3
            RETURNING id_cliente
            """;

    private final JdbcTemplate jdbcTemplate;

    public CodigoRecuperacionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean registrarSolicitud(Long idCliente, String codigoHash) {
        List<Long> registrados = jdbcTemplate.query(
                REGISTRAR_SOLICITUD,
                (resultado, fila) -> resultado.getLong("id_cliente"),
                idCliente,
                codigoHash);
        return !registrados.isEmpty();
    }

    public Optional<CodigoPendiente> buscarParaActualizar(Long idCliente) {
        return jdbcTemplate.query(
                """
                        SELECT codigo_hash, expira_en > CURRENT_TIMESTAMP AS vigente, intentos
                        FROM renta_cancha.recuperacion_correo
                        WHERE id_cliente = ?
                        FOR UPDATE
                        """,
                (resultado, fila) -> new CodigoPendiente(
                        resultado.getString("codigo_hash"),
                        resultado.getBoolean("vigente"),
                        resultado.getInt("intentos")),
                idCliente).stream().findFirst();
    }

    public void registrarIntentoFallido(Long idCliente) {
        jdbcTemplate.update(
                "UPDATE renta_cancha.recuperacion_correo SET intentos = intentos + 1 WHERE id_cliente = ?",
                idCliente);
    }

    public void eliminar(Long idCliente) {
        jdbcTemplate.update("DELETE FROM renta_cancha.recuperacion_correo WHERE id_cliente = ?", idCliente);
    }

    public record CodigoPendiente(String codigoHash, boolean vigente, int intentos) {
    }
}
