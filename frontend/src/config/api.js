const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL;

// An explicitly empty value means "same origin", which is how the container
// image is built - nginx proxies /api through to the backend. Only fall back to
// the local dev server when the variable was not supplied at all, otherwise an
// empty string would silently point a deployed build at localhost:4001.
export const API_BASE_URL =
  configuredBaseUrl === undefined ? "http://localhost:4001" : configuredBaseUrl;

export const TOKEN_STORAGE_KEY = "portiq_token";

export const API_ENDPOINTS = {
  portfolios: "/api/portfolios",
  portfolioById: (portfolioId) => `/api/portfolios/${portfolioId}`,
  portfolioPerformance: (portfolioId) => `/api/portfolios/${portfolioId}/performance`,
  holdings: (portfolioId) => `/api/portfolios/${portfolioId}/holdings`,
  holdingById: (portfolioId, holdingId) => `/api/portfolios/${portfolioId}/holdings/${holdingId}`,

  login: "/api/auth/login",
  me: "/api/auth/me",
  webauthnRegistrationOptions: "/api/auth/webauthn/registration/options",
  webauthnRegistrationVerify: "/api/auth/webauthn/registration/verify",
  webauthnLoginOptions: "/api/auth/webauthn/login/options",
  webauthnLoginVerify: "/api/auth/webauthn/login/verify",

  allHoldings: "/api/holdings",
  holdingByIdFlat: (id) => `/api/holdings/${id}`,
  bulkDeleteHoldings: "/api/holdings/bulk-delete",
  holdingsHistory: (range) => `/api/holdings/history?range=${encodeURIComponent(range)}`,
  importCsv: "/api/holdings/import/csv",
  importImage: "/api/holdings/import/image",
  sampleCsv: "/api/holdings/import/csv/sample",
  exportCsv: "/api/holdings/export/csv",
  exportPdf: "/api/holdings/export/pdf",

  news: "/api/news",
  insightsSummary: "/api/insights/summary",

  recommendations: (includeIdeas) => `/api/recommendations?includeIdeas=${includeIdeas ? "true" : "false"}`,
  recommendationByTicker: (ticker) => `/api/recommendations/${encodeURIComponent(ticker)}`,
  risk: "/api/risk",
  riskByTicker: (ticker) => `/api/risk/${encodeURIComponent(ticker)}`,
};
