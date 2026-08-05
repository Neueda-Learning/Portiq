import { API_BASE_URL, API_ENDPOINTS, TOKEN_STORAGE_KEY } from "../config/api";
import { apiClient } from "./apiClient";
import { getCached, invalidateCache, setCached } from "../utils/cache";

const HOLDINGS_TTL = 30_000;
const HISTORY_TTL = 50_000;
const CACHE_PREFIX = "holdings:";

function authHeaders() {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY);
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function handleJson(response) {
  if (!response.ok) {
    let message = `Request failed: ${response.status}`;
    try {
      const body = await response.json();
      message = body.message || message;
    } catch (_error) {
      // Ignore non-JSON bodies.
    }
    throw new Error(message);
  }
  return response.json();
}

async function downloadFile(url, filename) {
  const response = await fetch(url, { headers: authHeaders() });
  if (!response.ok) {
    throw new Error(`Export failed: ${response.status}`);
  }
  const blob = await response.blob();
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(link.href);
}

export const holdingsService = {
  getAll: async () => {
    const cacheKey = `${CACHE_PREFIX}all`;
    const cached = getCached(cacheKey);
    if (cached) return cached;
    const data = await apiClient(API_ENDPOINTS.allHoldings);
    setCached(cacheKey, data, HOLDINGS_TTL);
    return data;
  },

  add: async (payload) => {
    const result = await apiClient(API_ENDPOINTS.allHoldings, { method: "POST", body: JSON.stringify(payload) });
    invalidateCache(CACHE_PREFIX);
    return result;
  },

  update: async (id, payload) => {
    const result = await apiClient(API_ENDPOINTS.holdingByIdFlat(id), {
      method: "PUT",
      body: JSON.stringify(payload),
    });
    invalidateCache(CACHE_PREFIX);
    return result;
  },

  remove: async (id) => {
    const result = await apiClient(API_ENDPOINTS.holdingByIdFlat(id), { method: "DELETE" });
    invalidateCache(CACHE_PREFIX);
    return result;
  },

  removeMany: async (ids) => {
    const result = await apiClient(API_ENDPOINTS.bulkDeleteHoldings, {
      method: "POST",
      body: JSON.stringify({ ids }),
    });
    invalidateCache(CACHE_PREFIX);
    return result;
  },

  getHistory: async (range) => {
    const cacheKey = `${CACHE_PREFIX}history:${range}`;
    const cached = getCached(cacheKey);
    if (cached) return cached;
    const data = await apiClient(API_ENDPOINTS.holdingsHistory(range));
    setCached(cacheKey, data, HISTORY_TTL);
    return data;
  },

  importCsv: async (file) => {
    const formData = new FormData();
    formData.append("file", file);
    const response = await fetch(`${API_BASE_URL}${API_ENDPOINTS.importCsv}`, {
      method: "POST",
      headers: authHeaders(),
      body: formData,
    });
    const result = await handleJson(response);
    invalidateCache(CACHE_PREFIX);
    return result;
  },

  importImage: async (file) => {
    const formData = new FormData();
    formData.append("file", file);
    const response = await fetch(`${API_BASE_URL}${API_ENDPOINTS.importImage}`, {
      method: "POST",
      headers: authHeaders(),
      body: formData,
    });
    const result = await handleJson(response);
    invalidateCache(CACHE_PREFIX);
    return result;
  },

  downloadSampleCsv: () => downloadFile(`${API_BASE_URL}${API_ENDPOINTS.sampleCsv}`, "sample-holdings.csv"),
  exportCsv: () => downloadFile(`${API_BASE_URL}${API_ENDPOINTS.exportCsv}`, "portiq-holdings.csv"),
  exportPdf: () => downloadFile(`${API_BASE_URL}${API_ENDPOINTS.exportPdf}`, "portiq-holdings.pdf"),
};
