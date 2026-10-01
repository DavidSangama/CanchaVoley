// Agrega esta línea si no está:
const BASE_URL = import.meta.env.VITE_API_URL || "http://localhost:6767/api";

export const fetchAPI = async (endpoint, options = {}) => {
  try {
    const response = await fetch(`${BASE_URL}${endpoint}`, {
      headers: {
        "Content-Type": "application/json",
        ...options.headers,
      },
      ...options,
    });

    if (!response.ok) {
      throw new Error(`Error ${response.status}: ${response.statusText}`);
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