export const TOKEN_GESTION_SESSION_KEY = 'reservation_management_token';
export const TOKEN_GESTION_UPDATED_EVENT = 'reservation-management-token-updated';

export const obtenerTokenGestionGuardado = () => {
  try {
    return sessionStorage.getItem(TOKEN_GESTION_SESSION_KEY) || '';
  } catch (error) {
    console.error('No se pudo recuperar el enlace privado de reservas:', error);
    return '';
  }
};

export const guardarTokenGestion = (token) => {
  try {
    sessionStorage.setItem(TOKEN_GESTION_SESSION_KEY, token);
    window.dispatchEvent(new Event(TOKEN_GESTION_UPDATED_EVENT));
    return true;
  } catch (error) {
    console.error('No se pudo guardar el enlace privado de reservas:', error);
    return false;
  }
};
