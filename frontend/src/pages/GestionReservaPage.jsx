import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Footer } from '../components/Footer';
import { Navbar } from '../components/Navbar';
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
  const { id } = useParams();
  const tokenGestion = window.location.hash.slice(1);
  const [reserva, setReserva] = useState(null);
  const [fechaSeleccionada, setFechaSeleccionada] = useState('');
  const [idHorarioSeleccionado, setIdHorarioSeleccionado] = useState('');
  const [horarios, setHorarios] = useState([]);
  const [horariosOcupados, setHorariosOcupados] = useState([]);
  const [cargando, setCargando] = useState(Boolean(tokenGestion));
  const [cargandoDisponibilidad, setCargandoDisponibilidad] = useState(false);
  const [guardando, setGuardando] = useState(false);
  const [cancelando, setCancelando] = useState(false);
  const [error, setError] = useState(() => (
    tokenGestion ? '' : 'Este enlace no contiene la clave privada. Usa el enlace completo que guardaste al reservar.'
  ));
  const [mensaje, setMensaje] = useState('');
  const [cancelada, setCancelada] = useState(false);

  useEffect(() => {
    let cancelado = false;

    if (!tokenGestion) {
      return undefined;
    }

    Promise.all([
      ReservaService.obtenerGestion(id, tokenGestion),
      HorarioService.obtenerTodos(),
    ])
      .then(([datosReserva, listaHorarios]) => {
        if (cancelado) return;
        setReserva(datosReserva);
        setFechaSeleccionada(datosReserva.fecha);
        setIdHorarioSeleccionado(String(datosReserva.idHorario));
        setHorarios(listaHorarios);
        setCargandoDisponibilidad(true);
        setError('');
      })
      .catch((err) => {
        console.error('Error al cargar la reserva para gestionarla:', err);
        if (!cancelado) {
          setError(err.status === 404
            ? 'No se encontró esta reserva o el enlace privado no es válido.'
            : 'No se pudo cargar la reserva. Intenta de nuevo más tarde.');
        }
      })
      .finally(() => {
        if (!cancelado) setCargando(false);
      });

    return () => {
      cancelado = true;
    };
  }, [id, tokenGestion]);

  useEffect(() => {
    if (!reserva || !fechaSeleccionada) return undefined;

    let cancelado = false;
    ReservaService.obtenerPorFecha(fechaSeleccionada)
      .then((reservasDelDia) => {
        if (cancelado) return;
        setHorariosOcupados(
          reservasDelDia
            .filter((otraReserva) => (
              otraReserva.idCancha === reserva.idCancha
              && otraReserva.idReserva !== reserva.idReserva
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
  }, [reserva, fechaSeleccionada]);

  const reprogramar = async (event) => {
    event.preventDefault();
    if (!fechaSeleccionada || !idHorarioSeleccionado) {
      setError('Selecciona una fecha y un horario disponibles.');
      return;
    }

    setGuardando(true);
    setError('');
    setMensaje('');
    try {
      const reservaActualizada = await ReservaService.reprogramarComoCliente(
        id,
        tokenGestion,
        fechaSeleccionada,
        Number(idHorarioSeleccionado)
      );
      setReserva(reservaActualizada);
      setIdHorarioSeleccionado(String(reservaActualizada.idHorario));
      setMensaje('Tu reserva se reprogramó correctamente. El monto solicitado se actualizó al precio del nuevo horario.');
    } catch (err) {
      console.error('Error al reprogramar la reserva:', err);
      if (err.status === 409) {
        setError('No se pudo reprogramar: el horario está ocupado o el pago ya fue verificado. Actualiza la disponibilidad o contacta al administrador.');
      } else {
        setError('No se pudo reprogramar la reserva. Intenta de nuevo.');
      }
    } finally {
      setGuardando(false);
    }
  };

  const cancelar = async () => {
    setCancelando(true);
    setError('');
    setMensaje('');
    try {
      await ReservaService.cancelarSolicitud(id, tokenGestion);
      setCancelada(true);
    } catch (err) {
      console.error('Error al cancelar la reserva desde su enlace privado:', err);
      setError(err.status === 409
        ? 'El pago ya fue realizado; para cancelar, comunícate con el administrador.'
        : 'No se pudo cancelar la reserva. Intenta de nuevo o contacta al administrador.');
    } finally {
      setCancelando(false);
    }
  };

  const fechaMinima = obtenerFechaLocal();
  const pagoPendiente = reserva?.estadoPago === 'PENDIENTE_VERIFICACION';

  return (
    <div className="min-h-screen flex flex-col bg-slate-50">
      <Navbar />
      <main className="flex-1 w-full max-w-2xl mx-auto px-5 py-12">
        <section className="rounded-2xl bg-white p-6 sm:p-10 shadow-sm border border-slate-100">
          {cargando ? (
            <p className="text-center text-slate-600" role="status">Cargando tu reserva…</p>
          ) : error && !reserva ? (
            <div className="text-center">
              <h1 className="text-2xl font-bold text-slate-900 mb-3">No pudimos abrir este enlace</h1>
              <p className="text-red-700 mb-6" role="alert">{error}</p>
              <Link to="/" className="text-blue-700 font-semibold hover:underline">Volver al inicio</Link>
            </div>
          ) : cancelada ? (
            <div className="text-center">
              <div className="mx-auto mb-5 flex h-16 w-16 items-center justify-center rounded-full bg-emerald-100 text-emerald-700 text-3xl">✓</div>
              <h1 className="text-2xl font-extrabold text-slate-900 mb-3">Reserva cancelada</h1>
              <p className="text-slate-600 mb-6">Se canceló la solicitud y el horario quedó disponible.</p>
              <Link to="/reservar" className="inline-flex rounded-xl bg-blue-600 px-5 py-3 font-semibold text-white hover:bg-blue-700">
                Hacer otra reserva
              </Link>
            </div>
          ) : reserva && (
            <>
              <p className="mb-2 text-sm font-semibold uppercase tracking-wide text-blue-700">Gestión privada</p>
              <h1 className="text-3xl font-extrabold text-slate-900 mb-3">Tu reserva</h1>
              <p className="mb-6 text-slate-600">Este enlace privado permite consultar, cambiar o cancelar esta reserva.</p>

              <div className="mb-8 rounded-xl bg-slate-50 p-5">
                <dl className="grid grid-cols-2 gap-x-4 gap-y-4 text-sm">
                  <dt className="text-slate-500">Estado del pago</dt>
                  <dd className="text-right font-semibold text-slate-900">{ESTADOS_PAGO[reserva.estadoPago] || 'Estado desconocido'}</dd>
                  <dt className="text-slate-500">Cancha</dt>
                  <dd className="text-right font-semibold text-slate-900">Cancha {reserva.numeroCancha}</dd>
                  <dt className="text-slate-500">Fecha</dt>
                  <dd className="text-right font-semibold text-slate-900">{reserva.fecha}</dd>
                  <dt className="text-slate-500">Horario</dt>
                  <dd className="text-right font-semibold text-slate-900">{reserva.hora?.slice(0, 5)}</dd>
                  <dt className="text-slate-500">Monto solicitado</dt>
                  <dd className="text-right font-bold text-blue-700">S/ {Number(reserva.precio)}</dd>
                </dl>
              </div>

              {pagoPendiente ? (
                <form className="space-y-5" onSubmit={reprogramar}>
                  <h2 className="text-xl font-bold text-slate-900">Reprogramar reserva</h2>
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
                    Nuevo horario · Cancha {reserva.numeroCancha}
                    <select
                      value={idHorarioSeleccionado}
                      onChange={(event) => setIdHorarioSeleccionado(event.target.value)}
                      className="mt-2 block w-full rounded-xl border border-slate-300 px-4 py-3 font-normal"
                      required
                      disabled={cargandoDisponibilidad}
                    >
                      <option value="">
                        {cargandoDisponibilidad ? 'Consultando disponibilidad…' : 'Selecciona un horario'}
                      </option>
                      {horarios
                        .filter((horario) => !horariosOcupados.includes(horario.idHorario))
                        .map((horario) => (
                          <option key={horario.idHorario} value={horario.idHorario}>
                            {horario.hora?.slice(0, 5)} · S/ {Number(horario.precio)}
                          </option>
                        ))}
                    </select>
                  </label>

                  <button
                    type="submit"
                    disabled={guardando || cargandoDisponibilidad || !idHorarioSeleccionado}
                    className="w-full rounded-xl bg-blue-600 px-5 py-3 font-bold text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    {guardando ? 'Guardando cambios…' : 'Guardar nueva fecha y horario'}
                  </button>
                </form>
              ) : (
                <p className="rounded-xl bg-amber-50 p-4 text-sm text-amber-900">
                  Cuando el pago ya fue verificado, los cambios de fecha u horario deben coordinarse con el administrador.
                </p>
              )}

              {error && <p className="mt-5 text-sm text-red-700" role="alert">{error}</p>}
              {mensaje && <p className="mt-5 text-sm text-emerald-700" role="status">{mensaje}</p>}

              <div className="mt-8 border-t border-slate-100 pt-6">
                <button
                  type="button"
                  onClick={cancelar}
                  disabled={cancelando || reserva.estadoPago === 'REALIZADO'}
                  className="font-semibold text-red-700 hover:underline disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {cancelando ? 'Cancelando reserva…' : 'Cancelar esta reserva'}
                </button>
                {reserva.estadoPago === 'REALIZADO' && (
                  <p className="mt-2 text-sm text-slate-500">Para cancelar un pago realizado, contacta al administrador.</p>
                )}
              </div>
            </>
          )}
        </section>
      </main>
      <Footer />
    </div>
  );
}
