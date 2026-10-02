import React, { useState, useEffect } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { HorarioService } from '../services/HorarioService';
import { ReservaService } from '../services/ReservaService';
import { ClienteService } from '../services/ClienteService';
import { PagoService } from '../services/PagoService';
import './ReservaPage.css';

export function ReservaPage() {
  const [searchParams] = useSearchParams();
  const canchaParam = searchParams.get('cancha');

  const [pasoActual, setPasoActual] = useState(1);
  const [fechaSeleccionada, setFechaSeleccionada] = useState(null);

  const hoy = new Date();
  hoy.setHours(0, 0, 0, 0);

  const [mesVisible, setMesVisible] = useState(hoy.getMonth());
  const [anioVisible, setAnioVisible] = useState(hoy.getFullYear());

  const [canchaSeleccionada, setCanchaSeleccionada] = useState(
    canchaParam ? parseInt(canchaParam, 10) : null
  );

  useEffect(() => {
    setCanchaSeleccionada(canchaParam ? parseInt(canchaParam, 10) : null);
  }, [canchaParam]);

  const canchas = [
    { id: 1, nombre: 'Cancha 1' },
    { id: 2, nombre: 'Cancha 2' },
    { id: 3, nombre: 'Cancha 3' },
    { id: 4, nombre: 'Cancha 4' },
    { id: 5, nombre: 'Cancha 5' },
  ];

  const NOMBRES_MES = [
    'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
    'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre',
  ];
  const NOMBRES_MES_ABREV = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
  const NOMBRES_DIA = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];
  const NOMBRES_DIA_ABREV = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];

  const esMismaFecha = (a, b) =>
    a && b && a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();

  const diasDelMes = (() => {
    const primerDia = new Date(anioVisible, mesVisible, 1);
    const totalDias = new Date(anioVisible, mesVisible + 1, 0).getDate();
    const offsetInicio = (primerDia.getDay() + 6) % 7; // 0 = lunes

    const dias = [];
    for (let d = 1; d <= totalDias; d++) {
      const fecha = new Date(anioVisible, mesVisible, d);
      dias.push({ dia: d, fecha, deshabilitado: fecha < hoy });
    }
    return { offsetInicio, dias };
  })();

  const irMesAnterior = () => {
    if (anioVisible === hoy.getFullYear() && mesVisible === hoy.getMonth()) return;
    const nuevaFecha = new Date(anioVisible, mesVisible - 1, 1);
    setAnioVisible(nuevaFecha.getFullYear());
    setMesVisible(nuevaFecha.getMonth());
  };

  const irMesSiguiente = () => {
    const nuevaFecha = new Date(anioVisible, mesVisible + 1, 1);
    setAnioVisible(nuevaFecha.getFullYear());
    setMesVisible(nuevaFecha.getMonth());
  };

  const esMesActual = anioVisible === hoy.getFullYear() && mesVisible === hoy.getMonth();

  // ---- PASO 2: HORARIOS ----
  const pad2 = (n) => String(n).padStart(2, '0');
  const fechaISO = fechaSeleccionada
    ? `${fechaSeleccionada.getFullYear()}-${pad2(fechaSeleccionada.getMonth() + 1)}-${pad2(fechaSeleccionada.getDate())}`
    : null;

  const [horarios, setHorarios] = useState([]);
  const [horariosOcupados, setHorariosOcupados] = useState([]);
  const [horarioSeleccionado, setHorarioSeleccionado] = useState(null);
  const [cargandoHorarios, setCargandoHorarios] = useState(false);
  const [errorHorarios, setErrorHorarios] = useState(null);

  useEffect(() => {
    if (pasoActual !== 2 || !canchaSeleccionada || !fechaISO) return;

    let cancelado = false;
    setCargandoHorarios(true);
    setErrorHorarios(null);
    setHorarioSeleccionado(null);

    Promise.all([
      HorarioService.obtenerTodos(),
      ReservaService.obtenerPorFecha(fechaISO),
    ])
      .then(([listaHorarios, reservasDelDia]) => {
        if (cancelado) return;
        setHorarios(listaHorarios);
        const ocupados = reservasDelDia
          .filter((r) => r.idCancha === canchaSeleccionada)
          .map((r) => r.idHorario);
        setHorariosOcupados(ocupados);
      })
      .catch((err) => {
        console.error('Error al cargar horarios:', err);
        if (!cancelado) setErrorHorarios('No se pudieron cargar los horarios. Intenta de nuevo.');
      })
      .finally(() => {
        if (!cancelado) setCargandoHorarios(false);
      });

    return () => {
      cancelado = true;
    };
  }, [pasoActual, canchaSeleccionada, fechaISO]);

  const fechaTexto = fechaSeleccionada
    ? `${NOMBRES_DIA[fechaSeleccionada.getDay()]} ${fechaSeleccionada.getDate()} de ${NOMBRES_MES[fechaSeleccionada.getMonth()]}`
    : '';
  const fechaLarga = fechaSeleccionada
    ? `${NOMBRES_DIA[fechaSeleccionada.getDay()].charAt(0).toUpperCase() + NOMBRES_DIA[fechaSeleccionada.getDay()].slice(1)} ${fechaSeleccionada.getDate()} de ${NOMBRES_MES[fechaSeleccionada.getMonth()]} de ${fechaSeleccionada.getFullYear()}`
    : '';
  const fechaCorta = fechaSeleccionada
    ? `${NOMBRES_DIA_ABREV[fechaSeleccionada.getDay()]} ${fechaSeleccionada.getDate()} ${NOMBRES_MES_ABREV[fechaSeleccionada.getMonth()]} ${fechaSeleccionada.getFullYear()}`
    : '';

  // ---- PASO 3: TUS DATOS ----
  const [datosCliente, setDatosCliente] = useState({
    nombre: '',
    apellido: '',
    dni: '',
    telefono: '',
  });
  const [erroresDatos, setErroresDatos] = useState({});

  const actualizarCampo = (campo, valor) => {
    setDatosCliente((prev) => ({ ...prev, [campo]: valor }));
  };

  const validarDatos = () => {
    const errores = {};
    if (!datosCliente.nombre.trim()) errores.nombre = 'Ingresa tu nombre.';
    if (!datosCliente.apellido.trim()) errores.apellido = 'Ingresa tu apellido.';
    if (!/^\d{8}$/.test(datosCliente.dni.trim())) errores.dni = 'El DNI debe tener 8 dígitos.';
    if (!/^\d{9}$/.test(datosCliente.telefono.trim())) errores.telefono = 'El teléfono debe tener 9 dígitos.';
    setErroresDatos(errores);
    return Object.keys(errores).length === 0;
  };

  // ---- PASO 4: RESUMEN Y PAGO ----
  const [enviandoReserva, setEnviandoReserva] = useState(false);
  const [errorEnvio, setErrorEnvio] = useState(null);
  const [reservaConfirmada, setReservaConfirmada] = useState(false);
  const [datosConfirmacion, setDatosConfirmacion] = useState(null);
  const [cancelandoSolicitud, setCancelandoSolicitud] = useState(false);
  const [errorCancelacion, setErrorCancelacion] = useState(null);
  const [solicitudCancelada, setSolicitudCancelada] = useState(false);

  const confirmarReserva = async () => {
    setEnviandoReserva(true);
    setErrorEnvio(null);

    try {
      // 1. Buscar si el cliente ya existe por DNI; si no, crearlo
      let idClienteFinal;
      try {
        const clienteExistente = await ClienteService.buscarPorDni(datosCliente.dni.trim());
        idClienteFinal = clienteExistente.idCliente;
      } catch {
        const nuevoCliente = await ClienteService.registrar({
          nombre: datosCliente.nombre.trim(),
          apellido: datosCliente.apellido.trim(),
          dni: datosCliente.dni.trim(),
          telefono: datosCliente.telefono.trim(),
        });
        idClienteFinal = nuevoCliente.idCliente;
      }

      // 2. Crear la reserva
      const nuevaReserva = await ReservaService.crear({
        cliente: { idCliente: idClienteFinal },
        cancha: { idCancha: canchaSeleccionada },
        horario: { idHorario: horarioSeleccionado.idHorario },
        fecha: fechaISO,
      });

      // 3. Registrar el pago
      await PagoService.procesar({
        reserva: { idReserva: nuevaReserva.idReserva },
        total: horarioSeleccionado.precio,
      });

      setDatosConfirmacion({
        idReserva: nuevaReserva.idReserva,
        tokenCancelacion: nuevaReserva.tokenCancelacion,
        cancha: canchaSeleccionada,
        fecha: fechaLarga,
        horario: horarioSeleccionado.hora?.slice(0, 5),
        total: Number(horarioSeleccionado.precio),
      });
      setReservaConfirmada(true);
    } catch (err) {
      console.error('Error al confirmar la reserva:', err);
      setErrorEnvio('No se pudo confirmar la reserva. Es posible que ese horario ya haya sido tomado por otra persona. Vuelve al paso 2 y elige otro horario.');
    } finally {
      setEnviandoReserva(false);
    }
  };

  const hacerOtraReserva = () => {
    setPasoActual(1);
    setFechaSeleccionada(28);
    setCanchaSeleccionada(null);
    setHorarioSeleccionado(null);
    setDatosCliente({ nombre: '', apellido: '', dni: '', telefono: '' });
    setErroresDatos({});
    setErrorEnvio(null);
    setReservaConfirmada(false);
    setDatosConfirmacion(null);
    setErrorCancelacion(null);
    setSolicitudCancelada(false);
  };

  const cancelarSolicitud = async () => {
    if (!datosConfirmacion?.tokenCancelacion || !datosConfirmacion?.idReserva) {
      setErrorCancelacion('No se encontraron los datos necesarios para cancelar la solicitud. Contacta al administrador.');
      return;
    }

    if (!window.confirm('¿Quieres cancelar la solicitud de pago y liberar este horario?')) return;

    setCancelandoSolicitud(true);
    setErrorCancelacion(null);
    try {
      await ReservaService.cancelarSolicitud(
        datosConfirmacion.idReserva,
        datosConfirmacion.tokenCancelacion
      );
      setSolicitudCancelada(true);
    } catch (error) {
      console.error('Error al cancelar la solicitud de pago:', error);
      if (error.status === 409) {
        setErrorCancelacion('Este pago ya fue verificado o la solicitud ya no está pendiente. Contacta al administrador para cancelar la reserva.');
      } else if (error.status === 404) {
        setErrorCancelacion('No se pudo validar la solicitud. Contacta al administrador para recibir ayuda.');
      } else {
        setErrorCancelacion('No se pudo cancelar la solicitud. Inténtalo de nuevo.');
      }
    } finally {
      setCancelandoSolicitud(false);
    }
  };

  // ===== PANTALLA DE ÉXITO =====
  if (reservaConfirmada && datosConfirmacion) {
    return (
      <div className="reserva-container">
        <header className="header con-sombra" id="header">
          <div className="container header-in">
            <Link to="/" className="marca">
              <span className="logo">V</span>
              <span className="marca-nombre">CanchaVóley</span>
            </Link>
            <nav className="menu" aria-label="Principal">
              <Link className="enlace" to="/">Inicio</Link>
              <Link className="enlace" to="/#canchas">Canchas</Link>
              <Link className="enlace" to="/#horarios">Horarios</Link>
              <Link className="enlace" to="/">Contacto</Link>
            </nav>
            <Link className="btn btn-primario header-cta" to="/reservar">Reservar</Link>
          </div>
        </header>

        <main className="container reserva-main">
          <div className="confirmacion-card">
            <div className={`confirmacion-icono ${solicitudCancelada ? '' : 'pendiente'}`}>✓</div>
            <h1>
              {solicitudCancelada
                ? 'Solicitud cancelada'
                : 'Pago pendiente de verificación'}
            </h1>
            <p>
              {solicitudCancelada
                ? 'Tu solicitud fue cancelada y el horario quedó liberado.'
                : 'Recibimos tu solicitud. El administrador verificará el pago antes de confirmar la reserva.'}
            </p>

            {!solicitudCancelada && (
              <>
                <div className="confirmacion-estado" role="status">
                  Pendiente de verificación
                </div>
                <div className="confirmacion-detalle">
                  <div className="resumen-fila">
                    <span>Cancha</span>
                    <strong>Cancha {datosConfirmacion.cancha}</strong>
                  </div>
                  <div className="resumen-fila">
                    <span>Fecha</span>
                    <strong>{datosConfirmacion.fecha}</strong>
                  </div>
                  <div className="resumen-fila">
                    <span>Horario</span>
                    <strong>{datosConfirmacion.horario}</strong>
                  </div>
                  <div className="resumen-fila">
                    <span>Monto solicitado</span>
                    <strong className="total-destacado">S/ {datosConfirmacion.total}</strong>
                  </div>
                </div>
              </>
            )}

            {errorCancelacion && <p className="error-cancelacion" role="alert">{errorCancelacion}</p>}

            <div className="confirmacion-acciones">
              {solicitudCancelada ? (
                <button type="button" className="btn btn-secundario" onClick={hacerOtraReserva}>
                  Hacer otra reserva
                </button>
              ) : (
                <>
                  <button type="button" className="btn btn-cancelar" onClick={cancelarSolicitud} disabled={cancelandoSolicitud}>
                    {cancelandoSolicitud ? 'Cancelando...' : 'Cancelar solicitud'}
                  </button>
                  <button type="button" className="btn btn-secundario" onClick={hacerOtraReserva}>
                    Hacer otra reserva
                  </button>
                </>
              )}
              <Link className="btn btn-primario" to="/">
                Volver al inicio
              </Link>
            </div>
          </div>
        </main>
      </div>
    );
  }

  return (
    <div className="reserva-container">
      <header className="header con-sombra" id="header">
        <div className="container header-in">
          <Link to="/" className="marca">
            <span className="logo">V</span>
            <span className="marca-nombre">CanchaVóley</span>
          </Link>

          <nav className="menu" aria-label="Principal">
            <Link className="enlace" to="/">Inicio</Link>
            <Link className="enlace" to="/#canchas">Canchas</Link>
            <Link className="enlace" to="/#horarios">Horarios</Link>
            <Link className="enlace" to="/">Contacto</Link>
          </nav>

          <Link className="btn btn-primario header-cta" to="/reservar">
            Reservar
          </Link>
        </div>
      </header>

      <main className="container reserva-main">
        <div className="reserva-header">
          <h1>Reserva tu cancha</h1>
          <p>Completa los 4 pasos para confirmar tu reserva.</p>
        </div>

        <div className="stepper">
          <div className={`step-item ${pasoActual >= 1 ? 'activo' : ''}`}>
            <span className="step-num">1</span>
            <span className="step-text">Fecha y cancha</span>
          </div>
          <div className="step-linea"></div>

          <div className={`step-item ${pasoActual >= 2 ? 'activo' : ''}`}>
            <span className="step-num">2</span>
            <span className="step-text">Horario</span>
          </div>
          <div className="step-linea"></div>

          <div className={`step-item ${pasoActual >= 3 ? 'activo' : ''}`}>
            <span className="step-num">3</span>
            <span className="step-text">Tus datos</span>
          </div>
          <div className="step-linea"></div>

          <div className={`step-item ${pasoActual >= 4 ? 'activo' : ''}`}>
            <span className="step-num">4</span>
            <span className="step-text">Resumen y pago</span>
          </div>
        </div>

        <div className="reserva-grid">
          <div className="reserva-card-paso">
            {/* ===== PASO 1: FECHA Y CANCHA ===== */}
            {pasoActual === 1 && (
              <>
                <section className="bloque-seleccion">
                  <h2>Elige la fecha</h2>
                  <p className="subtexto-bloque">Los días anteriores a hoy no están disponibles.</p>

                  <div className="calendario-box">
                    <div className="calendario-header">
                      <button
                        type="button"
                        className="cal-btn-nav"
                        onClick={irMesAnterior}
                        disabled={esMesActual}
                      >
                        ‹
                      </button>
                      <span className="cal-mes">
                        {NOMBRES_MES[mesVisible].charAt(0).toUpperCase() + NOMBRES_MES[mesVisible].slice(1)} {anioVisible}
                      </span>
                      <button type="button" className="cal-btn-nav" onClick={irMesSiguiente}>
                        ›
                      </button>
                    </div>

                    <div className="calendario-dias-semana">
                      <span>Lu</span><span>Ma</span><span>Mi</span><span>Ju</span>
                      <span>Vi</span><span>Sá</span><span>Do</span>
                    </div>

                    <div className="calendario-grid">
                      {Array.from({ length: diasDelMes.offsetInicio }).map((_, i) => (
                        <div key={`vacio-${i}`} className="cal-dia vacio"></div>
                      ))}

                      {diasDelMes.dias.map(({ dia, fecha, deshabilitado }) => (
                        <button
                          key={dia}
                          type="button"
                          className={`cal-dia ${deshabilitado ? 'deshabilitado' : 'habilitado'} ${
                            esMismaFecha(fecha, fechaSeleccionada) ? 'seleccionado' : ''
                          }`}
                          disabled={deshabilitado}
                          onClick={() => setFechaSeleccionada(fecha)}
                        >
                          {dia}
                        </button>
                      ))}
                    </div>
                  </div>
                </section>

                <section className="bloque-seleccion">
                  <h2>Elige la cancha</h2>
                  <div className="canchas-grid-selector">
                    {canchas.map((c) => (
                      <button
                        key={c.id}
                        type="button"
                        className={`cancha-opcion ${canchaSeleccionada === c.id ? 'seleccionada' : ''}`}
                        onClick={() => setCanchaSeleccionada(c.id)}
                      >
                        <span>Cancha</span>
                        <strong>{c.id}</strong>
                      </button>
                    ))}
                  </div>
                </section>

                <div className="reserva-acciones">
                  <button
                    type="button"
                    className="btn btn-primario btn-continuar"
                    disabled={!canchaSeleccionada || !fechaSeleccionada}
                    onClick={() => setPasoActual(2)}
                  >
                    Continuar
                  </button>
                </div>
              </>
            )}

            {/* ===== PASO 2: HORARIO ===== */}
            {pasoActual === 2 && (
              <>
                <section className="bloque-seleccion">
                  <h2>Elige tu horario</h2>
                  <p className="subtexto-bloque">
                    Los horarios en gris ya están reservados en la cancha {canchaSeleccionada} el {fechaTexto}.
                  </p>

                  <div className="horario-leyenda">
                    <span className="leyenda-item">
                      <span className="leyenda-punto disponible"></span> Disponible
                    </span>
                    <span className="leyenda-item">
                      <span className="leyenda-punto ocupado"></span> Ocupado
                    </span>
                    <span className="leyenda-item">
                      <span className="leyenda-punto seleccionado"></span> Seleccionado
                    </span>
                  </div>

                  {cargandoHorarios && <p className="estado-carga">Cargando horarios…</p>}
                  {errorHorarios && <p className="estado-error">{errorHorarios}</p>}

                  {!cargandoHorarios && !errorHorarios && (
                    <div className="horarios-grid-selector">
                      {horarios.map((h) => {
                        const ocupado = horariosOcupados.includes(h.idHorario);
                        const seleccionado = horarioSeleccionado?.idHorario === h.idHorario;
                        return (
                          <button
                            key={h.idHorario}
                            type="button"
                            className={`horario-slot ${ocupado ? 'ocupado' : seleccionado ? 'seleccionado' : 'disponible'}`}
                            disabled={ocupado}
                            onClick={() => setHorarioSeleccionado(h)}
                          >
                            <strong>{h.hora?.slice(0, 5)}</strong>
                            <span>S/ {Number(h.precio)}</span>
                          </button>
                        );
                      })}
                    </div>
                  )}
                </section>

                <div className="reserva-acciones acciones-dos-botones">
                  <button type="button" className="btn btn-secundario" onClick={() => setPasoActual(1)}>
                    Atrás
                  </button>
                  <button
                    type="button"
                    className="btn btn-primario btn-continuar"
                    disabled={!horarioSeleccionado}
                    onClick={() => setPasoActual(3)}
                  >
                    Continuar
                  </button>
                </div>
              </>
            )}

            {/* ===== PASO 3: TUS DATOS ===== */}
            {pasoActual === 3 && (
              <>
                <section className="bloque-seleccion">
                  <h2>Tus datos</h2>
                  <p className="subtexto-bloque">Los usaremos para registrar tu reserva.</p>

                  <div className="form-datos-grid">
                    <div className="campo-grupo">
                      <label className="campo-label" htmlFor="campo-nombre">Nombre</label>
                      <input
                        id="campo-nombre"
                        type="text"
                        className={`campo-input ${erroresDatos.nombre ? 'con-error' : ''}`}
                        placeholder="Escribe aquí"
                        value={datosCliente.nombre}
                        onChange={(e) => actualizarCampo('nombre', e.target.value)}
                      />
                      {erroresDatos.nombre && <span className="campo-ayuda-error">{erroresDatos.nombre}</span>}
                    </div>

                    <div className="campo-grupo">
                      <label className="campo-label" htmlFor="campo-apellido">Apellido</label>
                      <input
                        id="campo-apellido"
                        type="text"
                        className={`campo-input ${erroresDatos.apellido ? 'con-error' : ''}`}
                        placeholder="Escribe aquí"
                        value={datosCliente.apellido}
                        onChange={(e) => actualizarCampo('apellido', e.target.value)}
                      />
                      {erroresDatos.apellido && <span className="campo-ayuda-error">{erroresDatos.apellido}</span>}
                    </div>

                    <div className="campo-grupo">
                      <label className="campo-label" htmlFor="campo-dni">DNI</label>
                      <input
                        id="campo-dni"
                        type="text"
                        inputMode="numeric"
                        maxLength={8}
                        className={`campo-input ${erroresDatos.dni ? 'con-error' : ''}`}
                        placeholder="Escribe aquí"
                        value={datosCliente.dni}
                        onChange={(e) => actualizarCampo('dni', e.target.value.replace(/\D/g, ''))}
                      />
                      {erroresDatos.dni && <span className="campo-ayuda-error">{erroresDatos.dni}</span>}
                    </div>

                    <div className="campo-grupo">
                      <label className="campo-label" htmlFor="campo-telefono">Teléfono</label>
                      <input
                        id="campo-telefono"
                        type="text"
                        inputMode="numeric"
                        maxLength={9}
                        className={`campo-input ${erroresDatos.telefono ? 'con-error' : ''}`}
                        placeholder="Escribe aquí"
                        value={datosCliente.telefono}
                        onChange={(e) => actualizarCampo('telefono', e.target.value.replace(/\D/g, ''))}
                      />
                      {erroresDatos.telefono && <span className="campo-ayuda-error">{erroresDatos.telefono}</span>}
                    </div>
                  </div>
                </section>

                <div className="reserva-acciones acciones-dos-botones">
                  <button type="button" className="btn btn-secundario" onClick={() => setPasoActual(2)}>
                    Atrás
                  </button>
                  <button
                    type="button"
                    className="btn btn-primario btn-continuar"
                    onClick={() => {
                      if (validarDatos()) setPasoActual(4);
                    }}
                  >
                    Continuar
                  </button>
                </div>
              </>
            )}

            {/* ===== PASO 4: RESUMEN Y PAGO ===== */}
            {pasoActual === 4 && (
              <>
                <section className="bloque-seleccion">
                  <h2>Confirma tu reserva</h2>
                  <p className="subtexto-bloque">Revisa los datos antes de confirmar.</p>

                  <div className="confirma-filas">
                    <div className="confirma-fila">
                      <span>Cliente</span>
                      <strong>{datosCliente.nombre} {datosCliente.apellido}</strong>
                    </div>
                    <div className="confirma-fila">
                      <span>DNI</span>
                      <strong>{datosCliente.dni}</strong>
                    </div>
                    <div className="confirma-fila">
                      <span>Teléfono</span>
                      <strong>{datosCliente.telefono}</strong>
                    </div>
                    <div className="confirma-fila">
                      <span>Cancha</span>
                      <strong>Cancha {canchaSeleccionada}</strong>
                    </div>
                    <div className="confirma-fila">
                      <span>Fecha</span>
                      <strong>{fechaLarga}</strong>
                    </div>
                    <div className="confirma-fila">
                      <span>Horario</span>
                      <strong>{horarioSeleccionado?.hora?.slice(0, 5)}</strong>
                    </div>
                    <div className="confirma-fila">
                      <span>Precio por hora</span>
                      <strong>S/ {Number(horarioSeleccionado?.precio)}</strong>
                    </div>
                  </div>

                  <div className="total-a-pagar">
                    <span>Total a pagar</span>
                    <strong>S/ {Number(horarioSeleccionado?.precio)}</strong>
                  </div>

                  {errorEnvio && <p className="estado-error">{errorEnvio}</p>}
                </section>

                <div className="reserva-acciones acciones-dos-botones">
                  <button
                    type="button"
                    className="btn btn-secundario"
                    onClick={() => setPasoActual(3)}
                    disabled={enviandoReserva}
                  >
                    Atrás
                  </button>
                  <button
                    type="button"
                    className="btn btn-primario btn-continuar"
                    onClick={confirmarReserva}
                    disabled={enviandoReserva}
                  >
                    {enviandoReserva ? 'Confirmando…' : 'Confirmar reserva'}
                  </button>
                </div>
              </>
            )}
          </div>

          <aside className="reserva-resumen-card">
            <h3>Tu reserva</h3>

            <div className="resumen-filas">
              <div className="resumen-fila">
                <span>Fecha</span>
                <strong>{fechaCorta || '—'}</strong>
              </div>

              <div className="resumen-fila">
                <span>Cancha</span>
                <strong>{canchaSeleccionada ? `Cancha ${canchaSeleccionada}` : '—'}</strong>
              </div>

              <div className="resumen-fila">
                <span>Horario</span>
                <strong>{horarioSeleccionado ? horarioSeleccionado.hora?.slice(0, 5) : '—'}</strong>
              </div>

              <hr className="resumen-divider" />

              <div className="resumen-fila total">
                <span>Total</span>
                <strong>{horarioSeleccionado ? `S/ ${Number(horarioSeleccionado.precio)}` : '—'}</strong>
              </div>
            </div>
          </aside>
        </div>
      </main>
    </div>
  );
}

export default ReservaPage;