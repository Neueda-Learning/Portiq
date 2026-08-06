import { API_BASE_URL, API_ENDPOINTS, TOKEN_STORAGE_KEY } from "../config/api";
import { apiClient, errorFromResponse } from "./apiClient";
import { getCached, invalidateCache, setCached } from "../utils/cache";
import { ANALYSIS_CACHE_PREFIX } from "./analysisService";

const HOLDINGS_TTL = 30_000;
const HISTORY_TTL = 50_000;
const CACHE_PREFIX = "holdings:";

/**
 * Risk scores and recommendations are derived from the holdings, so editing a holding invalidates
 * them too - otherwise a freshly added position would be missing from the risk report for another
 * five minutes.
 */
function invalidateHoldingsAndAnalysis() {
  invalidateCache(CACHE_PREFIX);
  invalidateCache(ANALYSIS_CACHE_PREFIX);
}

function authHeaders() {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY);
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function handleJson(response) {
  if (!response.ok) {
    throw await errorFromResponse(response, "Import failed.");
  }
  return response.json();
}

/**
 * Multipart upload. Cannot go through apiClient because that sets a JSON content type, so it
 * repeats the network-failure handling rather than inheriting it.
 */
async function uploadFile(endpoint, file) {
  const formData = new FormData();
  formData.append("file", file);

  let response;
  try {
    response = await fetch(`${API_BASE_URL}${endpoint}`, {
      method: "POST",
      headers: authHeaders(),
      body: formData,
    });
  } catch (_networkError) {
    throw new Error(`Could not reach the server at ${API_BASE_URL}. Check that the backend is running.`);
  }

  return handleJson(response);
}

async function downloadFile(url, filename) {
  let response;
  try {
    response = await fetch(url, { headers: authHeaders() });
  } catch (_networkError) {
    throw new Error(`Could not reach the server at ${API_BASE_URL}. Check that the backend is running.`);
  }

  if (!response.ok) {
    throw await errorFromResponse(response, `Could not export ${filename}.`);
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
    invalidateHoldingsAndAnalysis();
    return result;
  },

  update: async (id, payload) => {
    const result = await apiClient(API_ENDPOINTS.holdingByIdFlat(id), {
      method: "PUT",
      body: JSON.stringify(payload),
    });
    invalidateHoldingsAndAnalysis();
    return result;
  },

  remove: async (id) => {
    const result = await apiClient(API_ENDPOINTS.holdingByIdFlat(id), { method: "DELETE" });
    invalidateHoldingsAndAnalysis();
    return result;
  },

  removeMany: async (ids) => {
    const result = await apiClient(API_ENDPOINTS.bulkDeleteHoldings, {
      method: "POST",
      body: JSON.stringify({ ids }),
    });
    invalidateHoldingsAndAnalysis();
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
    const result = await uploadFile(API_ENDPOINTS.importCsv, file);
    invalidateHoldingsAndAnalysis();
    return result;
  },

  importImage: async (file) => {
    const result = await uploadFile(API_ENDPOINTS.importImage, file);
    invalidateHoldingsAndAnalysis();
    return result;
  },

  downloadSampleCsv: () => downloadFile(`${API_BASE_URL}${API_ENDPOINTS.sampleCsv}`, "sample-holdings.csv"),
  exportCsv: () => downloadFile(`${API_BASE_URL}${API_ENDPOINTS.exportCsv}`, "portiq-holdings.csv"),
  exportPdf: () => downloadFile(`${API_BASE_URL}${API_ENDPOINTS.exportPdf}`, "portiq-holdings.pdf"),
};
