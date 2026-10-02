package com.canchavoley.backend.repository;

import com.canchavoley.backend.model.EstadoPago;
import com.canchavoley.backend.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface PagoRepository extends JpaRepository<Pago, Long> {

    // Buscar pago asociado a una reserva específica
    Optional<Pago> findByReservaIdReserva(Long idReserva);

    @Query("SELECT SUM(p.total) FROM Pago p WHERE p.estado = :estado")
    BigDecimal sumarTotalPorEstado(@Param("estado") EstadoPago estado);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Pago p SET p.estado = :estado")
    int actualizarEstadoDeTodos(@Param("estado") EstadoPago estado);

    // Eliminar pago asociado a una reserva específica
    void deleteByReservaIdReserva(Long idReserva);
}