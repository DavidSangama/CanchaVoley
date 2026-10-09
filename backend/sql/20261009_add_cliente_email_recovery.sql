ALTER TABLE renta_cancha.cliente
    ADD COLUMN IF NOT EXISTS correo VARCHAR(254);

CREATE UNIQUE INDEX IF NOT EXISTS cliente_correo_normalizado_unico
    ON renta_cancha.cliente (LOWER(correo))
    WHERE correo IS NOT NULL;

CREATE TABLE IF NOT EXISTS renta_cancha.recuperacion_correo (
    id_cliente BIGINT PRIMARY KEY REFERENCES renta_cancha.cliente(id_cliente) ON DELETE CASCADE,
    codigo_hash VARCHAR(60) NOT NULL,
    expira_en TIMESTAMPTZ NOT NULL,
    inicio_ventana TIMESTAMPTZ NOT NULL,
    solicitudes INTEGER NOT NULL CHECK (solicitudes > 0),
    intentos INTEGER NOT NULL CHECK (intentos >= 0)
);
