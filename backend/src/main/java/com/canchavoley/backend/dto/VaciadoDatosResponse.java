package com.canchavoley.backend.dto;

public record VaciadoDatosResponse(
        long clientesEliminados,
        long reservasEliminadas,
        long pagosEliminados) {
}
