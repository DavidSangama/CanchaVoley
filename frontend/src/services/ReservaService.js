import { fetchAPI } from "./api";

export const ReservaService = {
  crear: (reservaData) =>
    fetchAPI("/reservas", {
      method: "POST",
      body: JSON.stringify(reservaData),
    }),
  solicitarCodigoRecuperacionCorreo: (correo) =>
    fetchAPI("/reservas/recuperacion-correo", {
      method: "POST",
      body: JSON.stringify({ correo }),
    }),
  verificarCodigoRecuperacionCorreo: (correo, codigo) =>
    fetchAPI("/reservas/recuperacion-correo/verificar", {
      method: "POST",
      body: JSON.stringify({ correo, codigo }),
    }),
  cancelarSolicitud: (id, tokenGestion) =>
    fetchAPI(`/reservas/${id}/cancelar`, {
      method: "POST",
      body: JSON.stringify({ tokenCancelacion: tokenGestion }),
    }),
  obtenerGestion: (id, tokenGestion) =>
    fetchAPI(`/reservas/${id}/gestion`, {
      headers: { "X-Reservation-Token": tokenGestion },
    }),
  obtenerReservasCliente: (tokenGestion) =>
    fetchAPI("/reservas/gestion/cliente", {
      headers: { "X-Reservation-Token": tokenGestion },
    }),
  reprogramarComoCliente: (id, tokenGestion, fecha, idHorario) =>
    fetchAPI(`/reservas/${id}/gestion`, {
      method: "PATCH",
      body: JSON.stringify({ tokenCancelacion: tokenGestion, fecha, idHorario }),
    }),
  obtenerPorFecha: (fecha) => fetchAPI(`/reservas/fecha/${fecha}`),
  obtenerTodas: () => fetchAPI("/reservas"),
  actualizar: (id, reservaData) =>
    fetchAPI(`/reservas/${id}`, {
      method: "PUT",
      body: JSON.stringify(reservaData),
    }),
  eliminar: (id) => fetchAPI(`/reservas/${id}`, { method: "DELETE" }),
  vaciarTodas: () => fetchAPI("/reservas/vaciar", { method: "DELETE" }),
};