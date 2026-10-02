package com.canchavoley.backend.dto;

import java.time.LocalDate;

public record ReprogramarReservaRequest(
        String tokenCancelacion,
        LocalDate fecha,
        Long idHorario) {
}
