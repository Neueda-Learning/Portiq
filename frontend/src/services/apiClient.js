import { API_BASE_URL, TOKEN_STORAGE_KEY } from "../config/api";

export async function apiClient(path, options = {}) {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY);
  const headers = {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(options.headers || {}),
  };

  const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers });

  if (response.status === 401 && token) {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    window.location.href = "/login";
    throw new Error("Session expired, please log in again");
  }

  if (!response.ok) {
    let message = `Request failed: ${response.status}`;

    try {
      const body = await response.json();
      message = body.message || JSON.stringify(body.errors) || message;
    } catch (_error) {
      // Ignore non-JSON bodies.
    }

    throw new Error(message);
  }

  if (response.status === 204) {
    return null;
  }

  return response.json();
}
