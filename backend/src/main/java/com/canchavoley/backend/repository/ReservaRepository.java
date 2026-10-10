package com.canchavoley.backend.repository;

import com.canchavoley.backend.model.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    // Listar reservas por fecha
    List<Reserva> findByFecha(LocalDate fecha);

    // Listar reservas por ID de cliente
    List<Reserva> findByClienteIdCliente(Long idCliente);

    List<Reserva> findByClienteIdClienteOrderByFechaDescIdReservaDesc(Long idCliente);

    @Query(value = """
            SELECT r.*
            FROM renta_cancha.reserva r
            LEFT JOIN renta_cancha.pago p ON p.id_reserva = r.id_reserva
            WHERE r.fecha = :fecha
              AND (p.id_pago IS NULL OR p.estado = 'PENDIENTE_VERIFICACION')
            """, nativeQuery = true)
    List<Reserva> findOcupadasByFecha(@Param("fecha") LocalDate fecha);

    // Listar reservas por ID de cancha
    List<Reserva> findByCanchaIdCancha(Long idCancha);

    Optional<Reserva> findByIdReservaAndTokenCancelacionHash(Long idReserva, String tokenCancelacionHash);

    Optional<Reserva> findByTokenCancelacionHash(String tokenCancelacionHash);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM renta_cancha.reserva r
                LEFT JOIN renta_cancha.pago p ON p.id_reserva = r.id_reserva
                WHERE r.fecha = :fecha
                  AND r.id_cancha = :idCancha
                  AND r.id_horario = :idHorario
                  AND (CAST(:idReservaExcluida AS BIGINT) IS NULL OR r.id_reserva <> :idReservaExcluida)
                  AND (p.id_pago IS NULL OR p.estado = 'PENDIENTE_VERIFICACION')
            )
            """, nativeQuery = true)
    boolean existsOcupada(
            @Param("fecha") LocalDate fecha,
            @Param("idCancha") Long idCancha,
            @Param("idHorario") Long idHorario,
            @Param("idReservaExcluida") Long idReservaExcluida);

    boolean existsByFechaAndCanchaIdCanchaAndHorarioIdHorarioAndIdReservaNot(
            LocalDate fecha,
            Long idCancha,
            Long idHorario,
            Long idReserva);

    // Eliminar todas las reservas de una fecha específica
    void deleteByFecha(LocalDate fecha);
}