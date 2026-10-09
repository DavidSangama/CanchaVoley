package com.canchavoley.backend.repository;

import com.canchavoley.backend.model.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

    // Listar reservas por ID de cancha
    List<Reserva> findByCanchaIdCancha(Long idCancha);

    Optional<Reserva> findByIdReservaAndTokenCancelacionHash(Long idReserva, String tokenCancelacionHash);

    Optional<Reserva> findByTokenCancelacionHash(String tokenCancelacionHash);

    boolean existsByFechaAndCanchaIdCanchaAndHorarioIdHorarioAndIdReservaNot(
            LocalDate fecha,
            Long idCancha,
            Long idHorario,
            Long idReserva);

    @Modifying
    @Transactional
    @Query("UPDATE Reserva r SET r.tokenCancelacionHash = null WHERE r.cliente.idCliente = :idCliente")
    int revocarTokensDeCliente(@Param("idCliente") Long idCliente);

    // Eliminar todas las reservas de una fecha específica
    void deleteByFecha(LocalDate fecha);
}