import { API_BASE_URL, TOKEN_STORAGE_KEY } from "../config/api";

/**
 * Fallbacks for when the server sends no message of its own. These are what the user actually
 * reads in a toast, so they say what happened and what to do - "Request failed: 500" told them
 * neither.
 */
const STATUS_MESSAGES = {
  400: "That request was not valid. Check the values and try again.",
  403: "You do not have permission to do that.",
  404: "We could not find what you asked for.",
  409: "That conflicts with something already saved.",
  413: "That file is too large to upload.",
  429: "Too many requests in a row - wait a moment and try again.",
  500: "Something went wrong on the server. Try again in a moment.",
  502: "The server could not reach a service it depends on.",
  503: "That feature is not available on this server right now.",
  504: "The server took too long to respond. Try again in a moment.",
};

function messageForStatus(status) {
  if (STATUS_MESSAGES[status]) return STATUS_MESSAGES[status];
  if (status >= 500) return "The server had a problem handling that. Try again in a moment.";
  return "That request could not be completed.";
}

/**
 * Turns an error body into something worth reading. Validation responses carry a per-field map
 * rather than a sentence, so those are flattened into "field: reason" instead of being dumped as
 * raw JSON.
 */
function messageFromBody(body) {
  if (!body || typeof body !== "object") return null;

  const fieldErrors = body.errors && typeof body.errors === "object" ? Object.entries(body.errors) : [];
  if (fieldErrors.length > 0) {
    const details = fieldErrors.map(([field, reason]) => `${field}: ${reason}`).join(", ");
    return body.message ? `${body.message} (${details})` : details;
  }

  if (typeof body.message === "string" && body.message.trim()) {
    // The server attaches a reference code to unexpected 500s; keep it so a report is traceable.
    return body.message;
  }

  return null;
}

/**
 * Builds the error for a failed response. Exported so the multipart and download paths in
 * holdingsService, which cannot go through apiClient, still report failures the same way.
 */
export async function errorFromResponse(response, fallback) {
  let body = null;
  try {
    body = await response.json();
  } catch (_error) {
    // Non-JSON error body (an HTML error page, or nothing at all).
  }

  return new Error(
    messageFromBody(body) || (fallback ? `${fallback} ${messageForStatus(response.status)}` : messageForStatus(response.status))
  );
}

export async function apiClient(path, options = {}) {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY);
  const headers = {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(options.headers || {}),
  };

  let response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers });
  } catch (networkError) {
    // fetch only rejects when the request never completed - server down, DNS, CORS, offline.
    // Reporting that as a generic failure sends people hunting through application logs that
    // will not contain the request at all.
    throw new Error(
      navigator.onLine === false
        ? "You appear to be offline. Reconnect and try again."
        : `Could not reach the server at ${API_BASE_URL}. Check that the backend is running.`
    );
  }

  if (response.status === 401 && token) {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    window.location.href = "/login";
    throw new Error("Your session expired. Please log in again.");
  }

  if (!response.ok) {
    throw await errorFromResponse(response);
  }

  if (response.status === 204) {
    return null;
  }

  return response.json();
}
