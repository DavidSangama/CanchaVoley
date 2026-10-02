BEGIN;

LOCK TABLE renta_cancha.cliente,
           renta_cancha.reserva,
           renta_cancha.pago,
           renta_cancha.cliente_token_gestion
    IN ACCESS EXCLUSIVE MODE;

DO $$
DECLARE
    sufijo text := to_char(clock_timestamp(), 'YYYYMMDD_HH24MISS_MS');
BEGIN
    IF to_regclass('renta_cancha.cliente') IS NULL
       OR to_regclass('renta_cancha.reserva') IS NULL
       OR to_regclass('renta_cancha.pago') IS NULL
       OR to_regclass('renta_cancha.cliente_token_gestion') IS NULL THEN
        RAISE EXCEPTION 'No se encontraron todas las tablas requeridas en renta_cancha';
    END IF;

    EXECUTE format(
        'CREATE TABLE renta_cancha.%I AS TABLE renta_cancha.cliente',
        'backup_cliente_ids_' || sufijo);
    EXECUTE format(
        'CREATE TABLE renta_cancha.%I AS TABLE renta_cancha.reserva',
        'backup_reserva_ids_' || sufijo);
    EXECUTE format(
        'CREATE TABLE renta_cancha.%I AS TABLE renta_cancha.pago',
        'backup_pago_ids_' || sufijo);
    EXECUTE format(
        'CREATE TABLE renta_cancha.%I AS TABLE renta_cancha.cliente_token_gestion',
        'backup_token_gestion_ids_' || sufijo);

    RAISE NOTICE 'Se guardó una copia de seguridad de clientes, reservas, pagos y tokens con sufijo %', sufijo;
END $$;

CREATE TEMP TABLE _renumerar_cliente (
    id_anterior bigint PRIMARY KEY,
    id_nuevo bigint NOT NULL
) ON COMMIT DROP;

CREATE TEMP TABLE _renumerar_reserva (
    id_anterior bigint PRIMARY KEY,
    id_nuevo bigint NOT NULL
) ON COMMIT DROP;

CREATE TEMP TABLE _renumerar_pago (
    id_anterior bigint PRIMARY KEY,
    id_nuevo bigint NOT NULL
) ON COMMIT DROP;

INSERT INTO _renumerar_cliente (id_anterior, id_nuevo)
SELECT id_cliente, row_number() OVER (ORDER BY id_cliente)
FROM renta_cancha.cliente;

INSERT INTO _renumerar_reserva (id_anterior, id_nuevo)
SELECT id_reserva, row_number() OVER (ORDER BY id_reserva)
FROM renta_cancha.reserva;

INSERT INTO _renumerar_pago (id_anterior, id_nuevo)
SELECT id_pago, row_number() OVER (ORDER BY id_pago)
FROM renta_cancha.pago;

CREATE TEMP TABLE _renumerar_fk_constraints (
    esquema text NOT NULL,
    tabla text NOT NULL,
    nombre text NOT NULL,
    definicion text NOT NULL
) ON COMMIT DROP;

INSERT INTO _renumerar_fk_constraints (esquema, tabla, nombre, definicion)
SELECT origen_ns.nspname,
       origen.relname,
       restriccion.conname,
       pg_get_constraintdef(restriccion.oid)
FROM pg_constraint restriccion
JOIN pg_class origen ON origen.oid = restriccion.conrelid
JOIN pg_namespace origen_ns ON origen_ns.oid = origen.relnamespace
WHERE restriccion.contype = 'f'
  AND restriccion.confrelid IN (
      'renta_cancha.cliente'::regclass,
      'renta_cancha.reserva'::regclass
  );

CREATE TEMP TABLE _renumerar_fk_columnas (
    esquema_origen text NOT NULL,
    tabla_origen text NOT NULL,
    columna_origen text NOT NULL,
    tabla_referida text NOT NULL
) ON COMMIT DROP;

INSERT INTO _renumerar_fk_columnas (
    esquema_origen,
    tabla_origen,
    columna_origen,
    tabla_referida
)
SELECT origen_ns.nspname,
       origen.relname,
       columna_origen.attname,
       referida.relname
FROM pg_constraint restriccion
JOIN pg_class origen ON origen.oid = restriccion.conrelid
JOIN pg_namespace origen_ns ON origen_ns.oid = origen.relnamespace
JOIN pg_class referida ON referida.oid = restriccion.confrelid
CROSS JOIN LATERAL unnest(restriccion.conkey, restriccion.confkey)
    AS columnas(attnum_origen, attnum_referido)
JOIN pg_attribute columna_origen
    ON columna_origen.attrelid = origen.oid
   AND columna_origen.attnum = columnas.attnum_origen
JOIN pg_attribute columna_referida
    ON columna_referida.attrelid = referida.oid
   AND columna_referida.attnum = columnas.attnum_referido
WHERE restriccion.contype = 'f'
  AND restriccion.confrelid IN (
      'renta_cancha.cliente'::regclass,
      'renta_cancha.reserva'::regclass
  )
  AND (
      (referida.relname = 'cliente' AND columna_referida.attname = 'id_cliente')
      OR
      (referida.relname = 'reserva' AND columna_referida.attname = 'id_reserva')
  );

DO $$
BEGIN
    IF (SELECT count(*) FROM _renumerar_fk_columnas) <> 3
       OR EXISTS (
           SELECT 1
           FROM _renumerar_fk_columnas
           WHERE NOT (
               (esquema_origen = 'renta_cancha'
                AND tabla_origen = 'reserva'
                AND columna_origen = 'id_cliente'
                AND tabla_referida = 'cliente')
               OR
               (esquema_origen = 'renta_cancha'
                AND tabla_origen = 'cliente_token_gestion'
                AND columna_origen = 'id_cliente'
                AND tabla_referida = 'cliente')
               OR
               (esquema_origen = 'renta_cancha'
                AND tabla_origen = 'pago'
                AND columna_origen = 'id_reserva'
                AND tabla_referida = 'reserva')
           )
       ) THEN
        RAISE EXCEPTION
            'Las relaciones de cliente/reserva difieren de las revisadas; no se modificaron los IDs';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE contype = 'f'
          AND confrelid = 'renta_cancha.pago'::regclass
    ) THEN
        RAISE EXCEPTION
            'Se encontró una tabla que referencia pagos; revisa sus relaciones antes de renumerar';
    END IF;
END $$;

DO $$
DECLARE
    restriccion record;
BEGIN
    FOR restriccion IN SELECT * FROM _renumerar_fk_constraints LOOP
        EXECUTE format(
            'ALTER TABLE %I.%I DROP CONSTRAINT %I',
            restriccion.esquema,
            restriccion.tabla,
            restriccion.nombre);
    END LOOP;
END $$;

DO $$
DECLARE
    desplazamiento bigint;
    referencia record;
    restriccion record;
BEGIN
    SELECT COALESCE(max(id_anterior), 0) + count(*) + 1
    INTO desplazamiento
    FROM _renumerar_cliente;

    FOR referencia IN
        SELECT * FROM _renumerar_fk_columnas WHERE tabla_referida = 'cliente'
    LOOP
        EXECUTE format(
            'UPDATE %I.%I SET %I = %I + $1 WHERE %I IS NOT NULL',
            referencia.esquema_origen,
            referencia.tabla_origen,
            referencia.columna_origen,
            referencia.columna_origen,
            referencia.columna_origen)
        USING desplazamiento;
    END LOOP;

    UPDATE renta_cancha.cliente
    SET id_cliente = id_cliente + desplazamiento;

    UPDATE renta_cancha.cliente cliente
    SET id_cliente = mapa.id_nuevo
    FROM _renumerar_cliente mapa
    WHERE cliente.id_cliente = mapa.id_anterior + desplazamiento;

    FOR referencia IN
        SELECT * FROM _renumerar_fk_columnas WHERE tabla_referida = 'cliente'
    LOOP
        EXECUTE format(
            'UPDATE %I.%I origen SET %I = mapa.id_nuevo FROM _renumerar_cliente mapa WHERE origen.%I = mapa.id_anterior + $1',
            referencia.esquema_origen,
            referencia.tabla_origen,
            referencia.columna_origen,
            referencia.columna_origen)
        USING desplazamiento;
    END LOOP;

    SELECT COALESCE(max(id_anterior), 0) + count(*) + 1
    INTO desplazamiento
    FROM _renumerar_reserva;

    FOR referencia IN
        SELECT * FROM _renumerar_fk_columnas WHERE tabla_referida = 'reserva'
    LOOP
        EXECUTE format(
            'UPDATE %I.%I SET %I = %I + $1 WHERE %I IS NOT NULL',
            referencia.esquema_origen,
            referencia.tabla_origen,
            referencia.columna_origen,
            referencia.columna_origen,
            referencia.columna_origen)
        USING desplazamiento;
    END LOOP;

    UPDATE renta_cancha.reserva
    SET id_reserva = id_reserva + desplazamiento;

    UPDATE renta_cancha.reserva reserva
    SET id_reserva = mapa.id_nuevo
    FROM _renumerar_reserva mapa
    WHERE reserva.id_reserva = mapa.id_anterior + desplazamiento;

    FOR referencia IN
        SELECT * FROM _renumerar_fk_columnas WHERE tabla_referida = 'reserva'
    LOOP
        EXECUTE format(
            'UPDATE %I.%I origen SET %I = mapa.id_nuevo FROM _renumerar_reserva mapa WHERE origen.%I = mapa.id_anterior + $1',
            referencia.esquema_origen,
            referencia.tabla_origen,
            referencia.columna_origen,
            referencia.columna_origen)
        USING desplazamiento;
    END LOOP;

    SELECT COALESCE(max(id_anterior), 0) + count(*) + 1
    INTO desplazamiento
    FROM _renumerar_pago;

    UPDATE renta_cancha.pago
    SET id_pago = id_pago + desplazamiento;

    UPDATE renta_cancha.pago pago
    SET id_pago = mapa.id_nuevo
    FROM _renumerar_pago mapa
    WHERE pago.id_pago = mapa.id_anterior + desplazamiento;

    FOR restriccion IN SELECT * FROM _renumerar_fk_constraints LOOP
        EXECUTE format(
            'ALTER TABLE %I.%I ADD CONSTRAINT %I %s',
            restriccion.esquema,
            restriccion.tabla,
            restriccion.nombre,
            restriccion.definicion);
    END LOOP;
END $$;

DO $$
DECLARE
    identidad record;
    secuencia text;
    maximo bigint;
BEGIN
    FOR identidad IN
        SELECT *
        FROM (VALUES
            ('cliente', 'id_cliente'),
            ('reserva', 'id_reserva'),
            ('pago', 'id_pago'),
            ('cliente_token_gestion', 'id_token_gestion')
        ) AS identidades(tabla, columna)
    LOOP
        secuencia := pg_get_serial_sequence(
            'renta_cancha.' || identidad.tabla,
            identidad.columna);
        IF secuencia IS NULL THEN
            RAISE EXCEPTION
                'No se encontró la identidad para renta_cancha.%.%',
                identidad.tabla,
                identidad.columna;
        END IF;

        EXECUTE format(
            'ALTER SEQUENCE %s MINVALUE 1 RESTART WITH 1',
            secuencia::regclass);

        EXECUTE format(
            'SELECT max(%I) FROM renta_cancha.%I',
            identidad.columna,
            identidad.tabla)
        INTO maximo;

        PERFORM setval(secuencia::regclass, COALESCE(maximo, 1), maximo IS NOT NULL);
    END LOOP;
END $$;

SELECT 'clientes' AS entidad, min(id_cliente) AS id_minimo, max(id_cliente) AS id_maximo, count(*) AS cantidad
FROM renta_cancha.cliente
UNION ALL
SELECT 'reservas', min(id_reserva), max(id_reserva), count(*)
FROM renta_cancha.reserva
UNION ALL
SELECT 'pagos', min(id_pago), max(id_pago), count(*)
FROM renta_cancha.pago;

COMMIT;
