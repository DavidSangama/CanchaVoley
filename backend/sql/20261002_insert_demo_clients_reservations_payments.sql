BEGIN;

LOCK TABLE renta_cancha.cliente,
           renta_cancha.reserva,
           renta_cancha.pago,
           renta_cancha.cancha,
           renta_cancha.horario
    IN SHARE ROW EXCLUSIVE MODE;

CREATE TEMP TABLE _cv_clientes_prueba (
    orden integer PRIMARY KEY,
    dni varchar(8) NOT NULL UNIQUE,
    nombre varchar(100) NOT NULL,
    apellido varchar(100) NOT NULL,
    telefono varchar(20) NOT NULL,
    id_cliente bigint
) ON COMMIT DROP;

INSERT INTO _cv_clientes_prueba (orden, dni, nombre, apellido, telefono)
VALUES
    (1, '00000001', 'Cliente Prueba 1', 'Datos Sinteticos', '000000001'),
    (2, '00000002', 'Cliente Prueba 2', 'Datos Sinteticos', '000000002'),
    (3, '00000003', 'Cliente Prueba 3', 'Datos Sinteticos', '000000003'),
    (4, '00000004', 'Cliente Prueba 4', 'Datos Sinteticos', '000000004');

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM renta_cancha.cliente existente
        JOIN _cv_clientes_prueba prueba USING (dni)
    ) THEN
        RAISE EXCEPTION
            'Ya existe uno de los DNI sintéticos 00000001-00000004; se canceló la carga para evitar duplicados';
    END IF;
END $$;

INSERT INTO renta_cancha.cliente (nombre, apellido, dni, telefono)
SELECT nombre, apellido, dni, telefono
FROM _cv_clientes_prueba
ORDER BY orden;

UPDATE _cv_clientes_prueba prueba
SET id_cliente = cliente.id_cliente
FROM renta_cancha.cliente cliente
WHERE cliente.dni = prueba.dni;

CREATE TEMP TABLE _cv_horarios_prueba (
    orden integer PRIMARY KEY,
    id_cancha bigint NOT NULL,
    id_horario bigint NOT NULL,
    fecha date NOT NULL,
    precio numeric(10, 2) NOT NULL
) ON COMMIT DROP;

INSERT INTO _cv_horarios_prueba (orden, id_cancha, id_horario, fecha, precio)
WITH candidatos AS (
    SELECT fecha::date AS fecha,
           cancha.id_cancha,
           horario.id_horario,
           horario.precio,
           cancha.numero_cancha,
           horario.hora
    FROM generate_series(
        (timezone('America/Lima', current_timestamp)::date + 1)::timestamp,
        (timezone('America/Lima', current_timestamp)::date + 365)::timestamp,
        interval '1 day'
    ) AS fechas(fecha)
    CROSS JOIN renta_cancha.cancha cancha
    CROSS JOIN renta_cancha.horario horario
    WHERE NOT EXISTS (
        SELECT 1
        FROM renta_cancha.reserva reserva
        WHERE reserva.fecha = fechas.fecha::date
          AND reserva.id_cancha = cancha.id_cancha
          AND reserva.id_horario = horario.id_horario
    )
),
disponibles AS (
    SELECT row_number() OVER (
               ORDER BY fecha, hora, numero_cancha
           ) AS orden,
           id_cancha,
           id_horario,
           fecha,
           precio
    FROM candidatos
)
SELECT orden::integer, id_cancha, id_horario, fecha, precio
FROM disponibles
WHERE orden <= 8;

DO $$
BEGIN
    IF (SELECT count(*) FROM _cv_horarios_prueba) < 8 THEN
        RAISE EXCEPTION
            'No hay ocho horarios disponibles en los próximos 365 días; se canceló toda la carga de prueba';
    END IF;
END $$;

INSERT INTO renta_cancha.reserva (id_cliente, id_cancha, id_horario, fecha)
SELECT cliente.id_cliente,
       horario.id_cancha,
       horario.id_horario,
       horario.fecha
FROM _cv_horarios_prueba horario
JOIN _cv_clientes_prueba cliente
  ON cliente.orden = ((horario.orden + 1) / 2);

CREATE TEMP TABLE _cv_reservas_prueba (
    orden integer PRIMARY KEY,
    id_reserva bigint NOT NULL,
    id_cliente bigint NOT NULL,
    precio numeric(10, 2) NOT NULL
) ON COMMIT DROP;

INSERT INTO _cv_reservas_prueba (orden, id_reserva, id_cliente, precio)
SELECT horario.orden,
       reserva.id_reserva,
       cliente.id_cliente,
       horario.precio
FROM _cv_horarios_prueba horario
JOIN _cv_clientes_prueba cliente
  ON cliente.orden = ((horario.orden + 1) / 2)
JOIN renta_cancha.reserva reserva
  ON reserva.id_cliente = cliente.id_cliente
 AND reserva.id_cancha = horario.id_cancha
 AND reserva.id_horario = horario.id_horario
 AND reserva.fecha = horario.fecha;

DO $$
BEGIN
    IF (SELECT count(*) FROM _cv_clientes_prueba WHERE id_cliente IS NOT NULL) <> 4
       OR (SELECT count(*) FROM _cv_reservas_prueba) <> 8 THEN
        RAISE EXCEPTION
            'No se pudieron relacionar los cuatro clientes con sus ocho reservas; se canceló toda la carga';
    END IF;
END $$;

INSERT INTO renta_cancha.pago (id_reserva, total, estado)
SELECT id_reserva, precio, 'PENDIENTE_VERIFICACION'
FROM _cv_reservas_prueba
ORDER BY orden;

SELECT 'Cliente' AS tipo,
       cliente.id_cliente::text AS id,
       cliente.nombre || ' ' || cliente.apellido AS detalle,
       NULL::date AS fecha,
       NULL::numeric AS monto,
       NULL::text AS estado
FROM _cv_clientes_prueba cliente
UNION ALL
SELECT 'Reserva',
       reserva.id_reserva::text,
       'Cliente ' || cliente.id_cliente || ' · Cancha ' || horario.id_cancha
           || ' · Horario ' || horario.id_horario,
       horario.fecha,
       horario.precio,
       NULL::text
FROM _cv_reservas_prueba reserva
JOIN _cv_horarios_prueba horario USING (orden)
JOIN _cv_clientes_prueba cliente USING (id_cliente)
UNION ALL
SELECT 'Pago',
       pago.id_pago::text,
       'Reserva ' || pago.id_reserva,
       reserva.fecha,
       pago.total,
       pago.estado
FROM renta_cancha.pago pago
JOIN _cv_reservas_prueba reserva USING (id_reserva)
ORDER BY tipo, id;

COMMIT;
