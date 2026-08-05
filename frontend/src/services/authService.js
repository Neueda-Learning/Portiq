import { API_ENDPOINTS } from "../config/api";
import { apiClient } from "./apiClient";

export const authService = {
  login: (username, password) =>
    apiClient(API_ENDPOINTS.login, { method: "POST", body: JSON.stringify({ username, password }) }),
  me: () => apiClient(API_ENDPOINTS.me),
  getRegistrationOptions: () => apiClient(API_ENDPOINTS.webauthnRegistrationOptions, { method: "POST" }),
  verifyRegistration: (credential) =>
    apiClient(API_ENDPOINTS.webauthnRegistrationVerify, { method: "POST", body: JSON.stringify(credential) }),
  getLoginOptions: () => apiClient(API_ENDPOINTS.webauthnLoginOptions, { method: "POST" }),
  verifyLogin: (credential) =>
    apiClient(API_ENDPOINTS.webauthnLoginVerify, { method: "POST", body: JSON.stringify(credential) }),
};
