import { API_ENDPOINTS } from "../config/api";
import { apiClient } from "./apiClient";

export const authService = {
  login: (username, password) =>
    apiClient(API_ENDPOINTS.login, { method: "POST", body: JSON.stringify({ username, password }) }),
  /**
   * Tells the server to stop honouring this token. Deleting it locally is not enough on its own -
   * a signed token stays valid until it expires, so a copy captured beforehand would keep working
   * for the rest of the day.
   */
  logout: () => apiClient(API_ENDPOINTS.logout, { method: "POST" }),
  me: () => apiClient(API_ENDPOINTS.me),
  getRegistrationOptions: () => apiClient(API_ENDPOINTS.webauthnRegistrationOptions, { method: "POST" }),
  verifyRegistration: (credential) =>
    apiClient(API_ENDPOINTS.webauthnRegistrationVerify, { method: "POST", body: JSON.stringify(credential) }),
  getLoginOptions: () => apiClient(API_ENDPOINTS.webauthnLoginOptions, { method: "POST" }),
  verifyLogin: (credential) =>
    apiClient(API_ENDPOINTS.webauthnLoginVerify, { method: "POST", body: JSON.stringify(credential) }),
};
