import axios from "axios";

export const API_URL = import.meta.env.VITE_API_URL || "http://localhost:4000";

const api = axios.create({
  baseURL: API_URL,
  validateStatus: (status) => status < 500,
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) config.headers.token = token;
  return config;
});

api.interceptors.response.use((response) => {
  if (response.status === 401 && localStorage.getItem("token")) {
    localStorage.removeItem("token");
    window.dispatchEvent(new Event("auth:logout"));
  }
  return response;
});

export default api;
