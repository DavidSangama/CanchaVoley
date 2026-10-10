export const TOKEN_GESTION_SESSION_KEY = 'reservation_management_token';
export const TOKENS_GESTION_RESERVAS_SESSION_KEY = 'reservation_management_tokens';
export const TOKEN_GESTION_UPDATED_EVENT = 'reservation-management-token-updated';

export const obtenerTokensGestionReservas = () => {
  try {
    const guardados = sessionStorage.getItem(TOKENS_GESTION_RESERVAS_SESSION_KEY);
    if (!guardados) return {};

    const tokens = JSON.parse(guardados);
    if (!tokens || typeof tokens !== 'object' || Array.isArray(tokens)) {
      throw new Error('El acceso guardado a las reservas no tiene un formato válido.');
    }

    return Object.fromEntries(
      Object.entries(tokens).filter(([id, token]) => /^\d+$/.test(id) && typeof token === 'string' && token),
    );
  } catch (error) {
    console.error('No se pudieron recuperar los accesos privados de reservas:', error);
    return {};
  }
};

export const obtenerTokenGestionGuardado = () => {
  try {
    if (Object.keys(obtenerTokensGestionReservas()).length > 1) return '';
    return sessionStorage.getItem(TOKEN_GESTION_SESSION_KEY) || '';
  } catch (error) {
    console.error('No se pudo recuperar el enlace privado de reservas:', error);
    return '';
  }
};

export const guardarTokensGestion = (reservas) => {
  try {
    const tokens = Object.fromEntries(
      reservas
        .filter((reserva) => reserva?.idReserva && typeof reserva.tokenGestion === 'string' && reserva.tokenGestion)
        .map((reserva) => [reserva.idReserva, reserva.tokenGestion]),
    );
    sessionStorage.setItem(TOKENS_GESTION_RESERVAS_SESSION_KEY, JSON.stringify(tokens));
    const ultimoToken = Object.values(tokens).at(-1);
    if (ultimoToken) {
      sessionStorage.setItem(TOKEN_GESTION_SESSION_KEY, ultimoToken);
    } else {
      sessionStorage.removeItem(TOKEN_GESTION_SESSION_KEY);
    }
    window.dispatchEvent(new Event(TOKEN_GESTION_UPDATED_EVENT));
    return true;
  } catch (error) {
    console.error('No se pudieron guardar los accesos privados de reservas:', error);
    return false;
  }
};

export const guardarTokenGestion = (idReserva, token) => guardarTokensGestion([
  ...Object.entries(obtenerTokensGestionReservas())
    .filter(([id]) => Number(id) !== idReserva)
    .map(([id, tokenGestion]) => ({ idReserva: Number(id), tokenGestion })),
  { idReserva, tokenGestion: token },
]);

export const eliminarTokenGestionReserva = (idReserva) => {
  const tokens = obtenerTokensGestionReservas();
  delete tokens[idReserva];
  return guardarTokensGestion(
    Object.entries(tokens).map(([id, tokenGestion]) => ({
      idReserva: Number(id),
      tokenGestion,
    })),
  );
};
