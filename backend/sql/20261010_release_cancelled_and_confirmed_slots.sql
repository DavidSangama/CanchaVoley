ALTER TABLE renta_cancha.reserva
    ADD COLUMN IF NOT EXISTS precio NUMERIC(10, 2);

UPDATE renta_cancha.reserva r
SET precio = COALESCE(
    (SELECT p.total FROM renta_cancha.pago p WHERE p.id_reserva = r.id_reserva),
    (SELECT h.precio FROM renta_cancha.horario h WHERE h.id_horario = r.id_horario)
)
WHERE r.precio IS NULL;

ALTER TABLE renta_cancha.reserva
    ALTER COLUMN precio SET NOT NULL;

ALTER TABLE renta_cancha.reserva
    DROP CONSTRAINT IF EXISTS reserva_unica;
