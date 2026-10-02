// Agrega esta línea si no está:
const BASE_URL = import.meta.env.VITE_API_URL;
export const ADMIN_AUTHORIZATION_KEY = "admin_authorization";

const crearAutorizacionBasica = (usuario, clave) => {
  const bytes = new TextEncoder().encode(`${usuario}:${clave}`);
  const binario = Array.from(bytes, (byte) => String.fromCharCode(byte)).join("");
  return `Basic ${btoa(binario)}`;
};

export const iniciarSesionAdmin = async (usuario, clave) => {
  const authorization = crearAutorizacionBasica(usuario, clave);
  await fetchAPI("/admin/authenticate", {
    headers: { Authorization: authorization },
  });
  sessionStorage.setItem(ADMIN_AUTHORIZATION_KEY, authorization);
};

export const fetchAPI = async (endpoint, options = {}) => {
  try {
    const response = await fetch(`${BASE_URL}${endpoint}`, {
      headers: {
        "Content-Type": "application/json",
        ...(sessionStorage.getItem(ADMIN_AUTHORIZATION_KEY)
          ? { Authorization: sessionStorage.getItem(ADMIN_AUTHORIZATION_KEY) }
          : {}),
        ...options.headers,
      },
      ...options,
    });

    if (!response.ok) {
      if (response.status === 401) {
        sessionStorage.removeItem(ADMIN_AUTHORIZATION_KEY);
      }
      const error = new Error(`Error ${response.status}: ${response.statusText}`);
      error.status = response.status;
      throw error;
    }

    if (response.status === 204) {
      return null;
    }

    const texto = await response.text();
    return texto ? JSON.parse(texto) : null;
  } catch (error) {
    console.error("Error en la petición API:", error);
    throw error;
  }
};