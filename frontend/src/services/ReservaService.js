import { fetchAPI } from "./api";

export const ReservaService = {
  crear: (reservaData) =>
    fetchAPI("/reservas", {
      method: "POST",
      body: JSON.stringify(reservaData),
    }),
  cancelarSolicitud: (id, tokenCancelacion) =>
    fetchAPI(`/reservas/${id}/cancelar`, {
      method: "POST",
      body: JSON.stringify({ tokenCancelacion }),
    }),
  obtenerPorFecha: (fecha) => fetchAPI(`/reservas/fecha/${fecha}`),
  obtenerTodas: () => fetchAPI("/reservas"),
  actualizar: (id, reservaData) =>
    fetchAPI(`/reservas/${id}`, {
      method: "PUT",
      body: JSON.stringify(reservaData),
    }),
  eliminar: (id) => fetchAPI(`/reservas/${id}`, { method: "DELETE" }),
};