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

/**
 * Multipart upload with progress reporting.
 *
 * <p>Uses XMLHttpRequest rather than fetch, and that is the whole reason this is not a one-line
 * call: fetch exposes no upload progress at all. There is no event, no callback and no way to
 * observe the request body being sent — so a progress bar over fetch could only ever be a fake
 * one. XHR's `upload.progress` reports real bytes.
 *
 * <p>`onProgress` is called with a phase as well as a percentage, because these two endpoints
 * spend most of their time *after* the bytes arrive: the smart importer and the statement scanner
 * both call a language model, which can take tens of seconds. A single bar would race to 100% and
 * then sit there, which reads as a hung request. Splitting it lets the caller show a real
 * percentage while uploading and an honest "still working" state while the server thinks.
 *
 *   onProgress({ phase: "uploading",  percent })   // real, from the network
 *   onProgress({ phase: "processing", percent: 100 })
 *
 * @param onProgress optional; called with {phase, percent}
 */
function uploadFile(endpoint, file, onProgress) {
  const formData = new FormData();
  formData.append("file", file);

  const report = typeof onProgress === "function" ? onProgress : () => {};

  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("POST", `${API_BASE_URL}${endpoint}`);

    // Content-Type is deliberately not set: the browser has to add it itself so it can append the
    // multipart boundary. Setting it by hand produces a body the server cannot parse.
    Object.entries(authHeaders()).forEach(([key, val]) => xhr.setRequestHeader(key, val));

    xhr.upload.addEventListener("progress", (event) => {
      if (!event.lengthComputable) return;
      const percent = (event.loaded / event.total) * 100;
      report({ phase: percent >= 100 ? "processing" : "uploading", percent });
    });

    // Fires when the last byte is away, whether or not the browser gave us progress events —
    // some do not for small bodies, which would otherwise leave the bar stuck at zero.
    xhr.upload.addEventListener("load", () => report({ phase: "processing", percent: 100 }));

    xhr.addEventListener("load", async () => {
      // Wrapping the raw response in a Response lets this reuse apiClient's error handling
      // verbatim, so an upload failure reads exactly like any other failed request.
      const response = new Response(xhr.responseText || "", { status: xhr.status });

      if (xhr.status === 401) {
        // Matches apiClient: an expired session sends the user back to log in rather than
        // reporting a confusing "Import failed" for a file that was perfectly fine.
        localStorage.removeItem(TOKEN_STORAGE_KEY);
        window.location.href = "/login";
        reject(new Error("Your session expired. Please log in again."));
        return;
      }

      if (!response.ok) {
        reject(await errorFromResponse(response, "Import failed."));
        return;
      }

      try {
        resolve(JSON.parse(xhr.responseText));
      } catch (_parseError) {
        reject(new Error("The server returned a response that could not be read."));
      }
    });

    xhr.addEventListener("error", () => {
      reject(new Error(`Could not reach the server at ${API_BASE_URL}. Check that the backend is running.`));
    });

    xhr.addEventListener("abort", () => reject(new Error("The upload was cancelled.")));

    xhr.addEventListener("timeout", () => {
      reject(new Error("The upload timed out. Try again, or use a smaller file."));
    });

    // Generous because the server side may be waiting on a model reading a statement image.
    xhr.timeout = 120_000;

    report({ phase: "uploading", percent: 0 });
    xhr.send(formData);
  });
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

  importCsv: async (file, onProgress) => {
    const result = await uploadFile(API_ENDPOINTS.importCsv, file, onProgress);
    invalidateHoldingsAndAnalysis();
    return result;
  },

  importImage: async (file, onProgress) => {
    const result = await uploadFile(API_ENDPOINTS.importImage, file, onProgress);
    invalidateHoldingsAndAnalysis();
    return result;
  },

  downloadSampleCsv: () => downloadFile(`${API_BASE_URL}${API_ENDPOINTS.sampleCsv}`, "sample-holdings.csv"),
  exportCsv: () => downloadFile(`${API_BASE_URL}${API_ENDPOINTS.exportCsv}`, "portiq-holdings.csv"),
  exportPdf: () => downloadFile(`${API_BASE_URL}${API_ENDPOINTS.exportPdf}`, "portiq-holdings.pdf"),
};
