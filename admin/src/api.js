import axios from "axios";
import { url } from "./assets/assets";

export const ADMIN_TOKEN_KEY = "adminToken";

// Shared axios client for the admin panel. Adds the admin's login token to
// every request; on 401/403 the token is dropped and the login screen shows.
const api = axios.create({
  baseURL: url,
  validateStatus: (status) => status < 500,
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(ADMIN_TOKEN_KEY);
  if (token) config.headers.token = token;
  return config;
});

api.interceptors.response.use((response) => {
  if (response.status === 401 || response.status === 403) {
    localStorage.removeItem(ADMIN_TOKEN_KEY);
    window.dispatchEvent(new Event("admin:logout"));
  }
  return response;
});

export default api;
