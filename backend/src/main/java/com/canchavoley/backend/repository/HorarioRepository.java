package com.canchavoley.backend.repository;

import com.canchavoley.backend.model.Horario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface HorarioRepository extends JpaRepository<Horario, Long> {

    // Buscar por hora exacta
    Optional<Horario> findByHora(LocalTime hora);

    // Listar horarios con precio menor o igual a un monto
    List<Horario> findByPrecioLessThanEqual(BigDecimal precioMaximo);

    // Verificar si existe un horario por su hora
    boolean existsByHora(LocalTime hora);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM Horario h WHERE h.idHorario = :idHorario")
    Optional<Horario> findByIdForUpdate(@Param("idHorario") Long idHorario);

    // Eliminar por hora exacta
    void deleteByHora(LocalTime hora);
}