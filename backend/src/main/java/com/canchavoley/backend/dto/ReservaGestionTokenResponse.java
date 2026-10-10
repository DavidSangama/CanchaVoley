package com.canchavoley.backend.dto;

import com.canchavoley.backend.model.EstadoPago;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReservaGestionTokenResponse(
        Long idReserva,
        LocalDate fecha,
        Long idCancha,
        Integer numeroCancha,
        Long idHorario,
        String hora,
        BigDecimal precio,
        EstadoPago estadoPago,
        String tokenGestion) {
}
