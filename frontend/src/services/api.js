const BASE_URL = (import.meta.env.VITE_API_URL ?? "").replace(/\/+$/, "");
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
    if (!BASE_URL) {
      throw new Error("Falta configurar VITE_API_URL para conectar con el backend.");
    }

    const authorization = sessionStorage.getItem(ADMIN_AUTHORIZATION_KEY);
    const response = await fetch(`${BASE_URL}${endpoint}`, {
      ...options,
      headers: {
        "Content-Type": "application/json",
        ...(authorization ? { Authorization: authorization } : {}),
        ...options.headers,
      },
    });

    const texto = await response.text();
    let respuesta = null;
    if (texto) {
      try {
        respuesta = JSON.parse(texto);
      } catch {
        respuesta = texto;
      }
    }

    if (!response.ok) {
      if (response.status === 401) {
        sessionStorage.removeItem(ADMIN_AUTHORIZATION_KEY);
      }
      const detalle = typeof respuesta === "string"
        ? respuesta
        : respuesta?.detail ?? respuesta?.message ?? respuesta?.error;
      const error = new Error(
        `Error ${response.status}${detalle ? `: ${detalle}` : `: ${response.statusText}`}`,
      );
      error.status = response.status;
      error.body = respuesta;
      throw error;
    }

    if (response.status === 204) {
      return null;
    }

    return texto ? JSON.parse(texto) : null;
  } catch (error) {
    console.error("Error en la petición API:", error);
    throw error;
  }
};