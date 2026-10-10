CREATE UNIQUE INDEX IF NOT EXISTS reserva_token_gestion_hash_unico
    ON renta_cancha.reserva (token_cancelacion_hash)
    WHERE token_cancelacion_hash IS NOT NULL;
