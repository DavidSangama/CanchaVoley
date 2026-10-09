import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Footer } from '../components/Footer';
import { Navbar } from '../components/Navbar';
import { SelectEstilizado } from '../components/SelectEstilizado';
import { HorarioService } from '../services/HorarioService';
import { ReservaService } from '../services/ReservaService';

const ESTADOS_PAGO = {
  PENDIENTE_VERIFICACION: 'Pendiente de verificación',
  REALIZADO: 'Pago realizado',
  CANCELADO: 'Pago cancelado',
};

const obtenerFechaLocal = () => {
  const hoy = new Date();
  const pad2 = (valor) => String(valor).padStart(2, '0');
  return `${hoy.getFullYear()}-${pad2(hoy.getMonth() + 1)}-${pad2(hoy.getDate())}`;
};

export function GestionReservaPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const tokenGestion = location.hash.slice(1);
  const [reservas, setReservas] = useState([]);
  const [correoRecuperacion, setCorreoRecuperacion] = useState('');
  const [codigoRecuperacion, setCodigoRecuperacion] = useState('');
  const [codigoEnviado, setCodigoEnviado] = useState(false);
  const [solicitandoCodigo, setSolicitandoCodigo] = useState(false);
  const [verificandoCodigo, setVerificandoCodigo] = useState(false);
  const [reservaSeleccionada, setReservaSeleccionada] = useState(null);
  const [fechaSeleccionada, setFechaSeleccionada] = useState('');
  const [idHorarioSeleccionado, setIdHorarioSeleccionado] = useState('');
  const [horarios, setHorarios] = useState([]);
  const [horariosOcupados, setHorariosOcupados] = useState([]);
  const [cargando, setCargando] = useState(Boolean(tokenGestion));
  const [cargandoDisponibilidad, setCargandoDisponibilidad] = useState(false);
  const [guardando, setGuardando] = useState(false);
  const [cancelando, setCancelando] = useState(false);
  const [error, setError] = useState(() => (
    ''
  ));
  const [mensaje, setMensaje] = useState('');

  const solicitarCodigoCorreo = async (event) => {
    event.preventDefault();
    const correo = correoRecuperacion.trim().toLowerCase();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(correo) || correo.length > 254) {
      setError('Ingresa el correo electrónico asociado a tu cuenta.');
      return;
    }

    setSolicitandoCodigo(true);
    setError('');
    setMensaje('');
    try {
      const respuesta = await ReservaService.solicitarCodigoRecuperacionCorreo(correo);
      setCorreoRecuperacion(correo);
      setCodigoEnviado(true);
      setMensaje(respuesta.mensaje);
    } catch (err) {
      console.error('No se pudo solicitar el código de recuperación por correo:', err);
      setError(err.status === 503
        ? 'La recuperación por correo todavía no está configurada. Contacta al administrador.'
        : 'No se pudo solicitar el código. Intenta más tarde.');
    } finally {
      setSolicitandoCodigo(false);
    }
  };

  const verificarCodigoCorreo = async (event) => {
    event.preventDefault();
    const codigo = codigoRecuperacion.trim();
    if (!/^\d{6}$/.test(codigo)) {
      setError('Ingresa el código de seis dígitos que recibiste por correo.');
      return;
    }

    setVerificandoCodigo(true);
    setError('');
    try {
      const respuesta = await ReservaService.verificarCodigoRecuperacionCorreo(
        correoRecuperacion,
        codigo
      );
      if (typeof respuesta?.tokenGestion !== 'string' || !respuesta.tokenGestion) {
        throw new Error('La verificación no devolvió el acceso privado.');
      }
      navigate(`/mis-reservas#${respuesta.tokenGestion}`);
    } catch (err) {
      console.error('No se pudo verificar el código de recuperación por correo:', err);
      setError(err.status === 401
        ? 'El código es incorrecto o venció. Solicita uno nuevo.'
        : 'No se pudo verificar el código. Intenta más tarde.');
    } finally {
      setVerificandoCodigo(false);
    }
  };

  useEffect(() => {
    let cancelado = false;

    if (!tokenGestion) return undefined;

    Promise.all([
      ReservaService.obtenerReservasCliente(tokenGestion),
      HorarioService.obtenerTodos(),
    ])
      .then(([reservasCliente, listaHorarios]) => {
        if (cancelado) return;
        setReservas(reservasCliente);
        setHorarios(listaHorarios);
        setError('');
      })
      .catch((err) => {
        console.error('Error al cargar las reservas del cliente:', err);
        if (!cancelado) {
          setError(err.status === 404
            ? 'No se encontraron reservas para este enlace privado.'
            : 'No se pudieron cargar tus reservas. Intenta de nuevo más tarde.');
        }
      })
      .finally(() => {
        if (!cancelado) setCargando(false);
      });

    return () => {
      cancelado = true;
    };
  }, [tokenGestion]);

  useEffect(() => {
    if (!reservaSeleccionada || !fechaSeleccionada
      || reservaSeleccionada.estadoPago !== 'PENDIENTE_VERIFICACION') return undefined;

    let cancelado = false;
    ReservaService.obtenerPorFecha(fechaSeleccionada)
      .then((reservasDelDia) => {
        if (cancelado) return;
        setHorariosOcupados(
          reservasDelDia
            .filter((otraReserva) => (
              otraReserva.idCancha === reservaSeleccionada.idCancha
              && otraReserva.idReserva !== reservaSeleccionada.idReserva
            ))
            .map((otraReserva) => otraReserva.idHorario)
        );
        setError('');
      })
      .catch((err) => {
        console.error('Error al consultar disponibilidad para reprogramar:', err);
        if (!cancelado) setError('No se pudo consultar la disponibilidad. Intenta de nuevo.');
      })
      .finally(() => {
        if (!cancelado) setCargandoDisponibilidad(false);
      });

    return () => {
      cancelado = true;
    };
  }, [reservaSeleccionada, fechaSeleccionada]);

  const abrirGestion = (reserva) => {
    setReservaSeleccionada(reserva);
    setFechaSeleccionada(reserva.fecha);
    setIdHorarioSeleccionado(String(reserva.idHorario));
    setError('');
    setMensaje('');
    setCargandoDisponibilidad(reserva.estadoPago === 'PENDIENTE_VERIFICACION');
  };

  const cerrarGestion = () => {
    if (guardando || cancelando) return;
    setReservaSeleccionada(null);
    setError('');
    setMensaje('');
  };

  const reprogramar = async (event) => {
    event.preventDefault();
    if (!fechaSeleccionada || !idHorarioSeleccionado || !reservaSeleccionada) {
      setError('Selecciona una fecha y un horario disponibles.');
      return;
    }

    setGuardando(true);
    setError('');
    setMensaje('');
    try {
      const reservaActualizada = await ReservaService.reprogramarComoCliente(
        reservaSeleccionada.idReserva,
        tokenGestion,
        fechaSeleccionada,
        Number(idHorarioSeleccionado)
      );
      setReservas((actuales) => actuales.map((reserva) => (
        reserva.idReserva === reservaActualizada.idReserva ? reservaActualizada : reserva
      )));
      setReservaSeleccionada(reservaActualizada);
      setIdHorarioSeleccionado(String(reservaActualizada.idHorario));
      setMensaje('Tu reserva se reprogramó correctamente. El monto solicitado se actualizó al precio del nuevo horario.');
    } catch (err) {
      console.error('Error al reprogramar la reserva:', err);
      setError(err.status === 409
        ? 'No se pudo reprogramar: el horario está ocupado o el pago ya fue verificado. Actualiza la disponibilidad o contacta al administrador.'
        : 'No se pudo reprogramar la reserva. Intenta de nuevo.');
    } finally {
      setGuardando(false);
    }
  };

  const cancelar = async () => {
    if (!reservaSeleccionada) return;
    setCancelando(true);
    setError('');
    setMensaje('');
    try {
      await ReservaService.cancelarSolicitud(reservaSeleccionada.idReserva, tokenGestion);
      setReservas((actuales) => actuales.filter(
        (reserva) => reserva.idReserva !== reservaSeleccionada.idReserva
      ));
      setReservaSeleccionada(null);
      setMensaje('La reserva se canceló y el horario quedó disponible.');
    } catch (err) {
      console.error('Error al cancelar la reserva desde el enlace privado:', err);
      setError(err.status === 409
        ? 'El pago ya fue realizado; para cancelar, comunícate con el administrador.'
        : 'No se pudo cancelar la reserva. Intenta de nuevo o contacta al administrador.');
    } finally {
      setCancelando(false);
    }
  };

  const fechaMinima = obtenerFechaLocal();
  const pagoPendiente = reservaSeleccionada?.estadoPago === 'PENDIENTE_VERIFICACION';

  return (
    <div className="min-h-screen flex flex-col bg-slate-50">
      <Navbar />
      <main className="flex-1 w-full max-w-4xl mx-auto px-4 py-6 sm:px-5 sm:py-12">
        <section className="rounded-2xl bg-white p-5 sm:p-10 shadow-sm border border-slate-100">
          {cargando ? (
            <p className="text-center text-slate-600" role="status">Cargando tus reservas…</p>
          ) : !tokenGestion ? (
            <div className="mx-auto max-w-xl py-4 text-center">
              <p className="mb-2 text-sm font-semibold uppercase tracking-wide text-blue-700">Acceso privado</p>
              <h1 className="mb-3 text-3xl font-extrabold text-slate-900">Recuperar mis reservas</h1>
              {!codigoEnviado ? (
                <form className="space-y-4 text-left" onSubmit={solicitarCodigoCorreo}>
                  <p className="text-slate-600">
                    Ingresa el correo guardado en tu cuenta. Si corresponde a un cliente registrado, enviaremos un código de un solo uso.
                  </p>
                  <label className="block text-sm font-semibold text-slate-700" htmlFor="correo-recuperacion">
                    Correo electrónico
                  </label>
                  <input
                    id="correo-recuperacion"
                    className="w-full rounded-xl border border-slate-300 px-4 py-3 text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-200"
                    type="email"
                    autoComplete="email"
                    maxLength={254}
                    value={correoRecuperacion}
                    onChange={(event) => setCorreoRecuperacion(event.target.value)}
                    placeholder="tucorreo@gmail.com"
                    required
                  />
                  <button
                    className="w-full rounded-xl bg-blue-700 px-5 py-3 font-semibold text-white hover:bg-blue-800 disabled:opacity-60"
                    type="submit"
                    disabled={solicitandoCodigo}
                  >
                    {solicitandoCodigo ? 'Solicitando código…' : 'Enviar código al correo'}
                  </button>
                </form>
              ) : (
                <form className="space-y-4 text-left" onSubmit={verificarCodigoCorreo}>
                  <p className="text-sm text-slate-600" role="status">
                    {mensaje} Revisa también la carpeta de spam. El código vence en 10 minutos.
                  </p>
                  <p className="text-sm text-slate-500">
                    Puedes solicitar hasta 3 códigos cada 15 minutos; hay un máximo de 5 intentos por código.
                  </p>
                  <label className="block text-sm font-semibold text-slate-700" htmlFor="codigo-recuperacion-correo">
                    Código de seis dígitos
                  </label>
                  <input
                    id="codigo-recuperacion-correo"
                    className="w-full rounded-xl border border-slate-300 px-4 py-3 text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-200"
                    type="text"
                    inputMode="numeric"
                    autoComplete="one-time-code"
                    maxLength={6}
                    value={codigoRecuperacion}
                    onChange={(event) => setCodigoRecuperacion(event.target.value.replace(/\D/g, ''))}
                    placeholder="000000"
                    required
                  />
                  <button
                    className="w-full rounded-xl bg-blue-700 px-5 py-3 font-semibold text-white hover:bg-blue-800 disabled:opacity-60"
                    type="submit"
                    disabled={verificandoCodigo}
                  >
                    {verificandoCodigo ? 'Verificando…' : 'Verificar y abrir mis reservas'}
                  </button>
                  <button
                    className="w-full font-semibold text-blue-700 hover:underline"
                    type="button"
                    onClick={() => {
                      setCodigoEnviado(false);
                      setCodigoRecuperacion('');
                      setMensaje('');
                      setError('');
                    }}
                  >
                    Cambiar correo o solicitar otro código
                  </button>
                </form>
              )}
              {error && <p className="mt-4 text-sm text-red-700" role="alert">{error}</p>}
              <p className="mt-6 text-sm text-slate-500">
                Por seguridad, no mostramos reservas usando solo el DNI. Si tu ficha aún no tiene correo, solicita al administrador que lo agregue.
              </p>
              <Link to="/" className="mt-5 inline-block text-blue-700 font-semibold hover:underline">Volver al inicio</Link>
            </div>
          ) : error && reservas.length === 0 ? (
            <div className="text-center">
              <h1 className="text-2xl font-bold text-slate-900 mb-3">No pudimos abrir este enlace</h1>
              <p className="text-red-700 mb-6" role="alert">{error}</p>
              <Link to="/mis-reservas" className="mr-4 text-blue-700 font-semibold hover:underline">
                Recuperar acceso con mi correo
              </Link>
              <Link to="/" className="text-blue-700 font-semibold hover:underline">Volver al inicio</Link>
            </div>
          ) : (
            <>
              <p className="mb-2 text-sm font-semibold uppercase tracking-wide text-blue-700">Gestión privada</p>
              <h1 className="text-3xl font-extrabold text-slate-900 mb-3">Mis reservas</h1>
              <p className="mb-8 text-slate-600">Aquí puedes consultar todas tus reservas. Selecciona una para cambiarla o cancelarla.</p>

              {mensaje && <p className="mb-6 rounded-xl bg-emerald-50 p-4 text-sm text-emerald-800" role="status">{mensaje}</p>}
              {error && <p className="mb-6 text-sm text-red-700" role="alert">{error}</p>}

              {reservas.length === 0 ? (
                <div className="rounded-xl bg-slate-50 p-8 text-center">
                  <p className="mb-5 text-slate-600">No tienes reservas activas en este momento.</p>
                  <Link to="/reservar" className="inline-flex rounded-xl bg-blue-600 px-5 py-3 font-semibold text-white hover:bg-blue-700">
                    Hacer una reserva
                  </Link>
                </div>
              ) : (
                <div className="space-y-4">
                  {reservas.map((reserva) => (
                    <article key={reserva.idReserva} className="flex flex-col gap-5 rounded-xl border border-slate-200 p-5 sm:flex-row sm:items-center sm:justify-between">
                      <div>
                        <h2 className="text-lg font-bold text-slate-900">Cancha {reserva.numeroCancha}</h2>
                        <p className="mt-1 text-sm text-slate-600">{reserva.fecha} · {reserva.hora?.slice(0, 5)}</p>
                        <p className="mt-2 text-sm font-semibold text-blue-700">
                          {ESTADOS_PAGO[reserva.estadoPago] || 'Pago no registrado'} · S/ {Number(reserva.precio)}
                        </p>
                      </div>
                      <button
                        type="button"
                        onClick={() => abrirGestion(reserva)}
                        className="shrink-0 rounded-xl bg-blue-600 px-5 py-3 font-semibold text-white hover:bg-blue-700"
                      >
                        Gestionar reserva
                      </button>
                    </article>
                  ))}
                </div>
              )}
            </>
          )}
        </section>
      </main>
      <Footer />

      {reservaSeleccionada && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-950/60 p-4"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) cerrarGestion();
          }}
        >
          <section
            role="dialog"
            aria-modal="true"
            aria-labelledby="dialog-title"
            className="my-auto max-h-[calc(100dvh-2rem)] w-full max-w-xl overflow-y-auto rounded-2xl bg-white p-4 shadow-xl sm:p-8"
          >
            <div className="mb-6 flex items-start justify-between gap-4">
              <div>
                <p className="mb-1 text-sm font-semibold uppercase tracking-wide text-blue-700">Cancha {reservaSeleccionada.numeroCancha}</p>
                <h2 id="dialog-title" className="text-2xl font-extrabold text-slate-900">Configurar reserva</h2>
              </div>
              <button
                type="button"
                aria-label="Cerrar"
                onClick={cerrarGestion}
                className="rounded-lg px-3 py-1 text-2xl text-slate-500 hover:bg-slate-100"
              >
                ×
              </button>
            </div>

            <dl className="mb-6 grid grid-cols-2 gap-x-4 gap-y-3 rounded-xl bg-slate-50 p-4 text-sm">
              <dt className="text-slate-500">Fecha actual</dt>
              <dd className="text-right font-semibold text-slate-900">{reservaSeleccionada.fecha}</dd>
              <dt className="text-slate-500">Horario actual</dt>
              <dd className="text-right font-semibold text-slate-900">{reservaSeleccionada.hora?.slice(0, 5)}</dd>
              <dt className="text-slate-500">Estado del pago</dt>
              <dd className="text-right font-semibold text-slate-900">{ESTADOS_PAGO[reservaSeleccionada.estadoPago] || 'Pago no registrado'}</dd>
              <dt className="text-slate-500">Monto solicitado</dt>
              <dd className="text-right font-bold text-blue-700">S/ {Number(reservaSeleccionada.precio)}</dd>
            </dl>

            {pagoPendiente ? (
              <form className="space-y-4" onSubmit={reprogramar}>
                <h3 className="text-lg font-bold text-slate-900">Cambiar fecha y horario</h3>
                <label className="block text-sm font-semibold text-slate-700">
                  Nueva fecha
                  <input
                    type="date"
                    min={fechaMinima}
                    value={fechaSeleccionada}
                    onChange={(event) => {
                      setCargandoDisponibilidad(true);
                      setFechaSeleccionada(event.target.value);
                      setIdHorarioSeleccionado('');
                      setMensaje('');
                    }}
                    className="mt-2 block w-full rounded-xl border border-slate-300 px-4 py-3 font-normal"
                    required
                  />
                </label>

                <label className="block text-sm font-semibold text-slate-700">
                  Nuevo horario
                  <SelectEstilizado
                    ariaLabel="Nuevo horario"
                    value={idHorarioSeleccionado}
                    onChange={(value) => setIdHorarioSeleccionado(String(value))}
                    className="mt-2"
                    disabled={cargandoDisponibilidad}
                    placeholder={cargandoDisponibilidad ? 'Consultando disponibilidad…' : 'Selecciona un horario'}
                    options={horarios
                      .filter((horario) => !horariosOcupados.includes(horario.idHorario))
                      .map((horario) => ({
                        value: horario.idHorario,
                        label: `${horario.hora?.slice(0, 5)} · S/ ${Number(horario.precio)}`,
                      }))}
                  />
                </label>

                <button
                  type="submit"
                  disabled={guardando || cargandoDisponibilidad || !idHorarioSeleccionado}
                  className="w-full rounded-xl bg-blue-600 px-5 py-3 font-bold text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {guardando ? 'Guardando cambios…' : 'Guardar cambios'}
                </button>
              </form>
            ) : (
              <p className="rounded-xl bg-amber-50 p-4 text-sm text-amber-900">
                Los cambios de fecha u horario deben coordinarse con el administrador cuando el pago ya fue verificado.
              </p>
            )}

            {error && <p className="mt-4 text-sm text-red-700" role="alert">{error}</p>}
            {mensaje && <p className="mt-4 text-sm text-emerald-700" role="status">{mensaje}</p>}

            <div className="mt-6 flex flex-col-reverse justify-between gap-4 border-t border-slate-100 pt-5 sm:flex-row sm:items-center">
              <button
                type="button"
                onClick={cancelar}
                disabled={cancelando || guardando || reservaSeleccionada.estadoPago !== 'PENDIENTE_VERIFICACION'}
                className="font-semibold text-red-700 hover:underline disabled:cursor-not-allowed disabled:opacity-50"
              >
                {cancelando ? 'Cancelando reserva…' : 'Cancelar esta reserva'}
              </button>
              <button
                type="button"
                onClick={cerrarGestion}
                disabled={guardando || cancelando}
                className="rounded-xl border border-slate-300 px-5 py-3 font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50"
              >
                Cerrar
              </button>
            </div>
          </section>
        </div>
      )}
    </div>
  );
}
