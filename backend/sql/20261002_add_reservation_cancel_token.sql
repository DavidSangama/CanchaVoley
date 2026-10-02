ALTER TABLE renta_cancha.reserva
    ADD COLUMN token_cancelacion_hash VARCHAR(64);

CREATE UNIQUE INDEX reserva_token_cancelacion_hash_unico
    ON renta_cancha.reserva (token_cancelacion_hash)
    WHERE token_cancelacion_hash IS NOT NULL;
