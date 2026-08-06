import { API_ENDPOINTS } from "../config/api";
import { apiClient } from "./apiClient";
import { getCached, setCached } from "../utils/cache";

/**
 * Risk reports and recommendations both fan out to a year of price history per ticker, so they are
 * cached noticeably longer than holdings - the underlying signals move on a scale of days, not
 * seconds. Anything that changes the holdings clears this prefix (see holdingsService).
 */
const ANALYSIS_TTL = 5 * 60_000;

export const ANALYSIS_CACHE_PREFIX = "analysis:";

async function cached(key, fetcher) {
  const cacheKey = `${ANALYSIS_CACHE_PREFIX}${key}`;
  const hit = getCached(cacheKey);
  if (hit) return hit;

  const data = await fetcher();
  setCached(cacheKey, data, ANALYSIS_TTL);
  return data;
}

export const analysisService = {
  getRecommendations: (includeIdeas = true) =>
    cached(`recommendations:${includeIdeas}`, () => apiClient(API_ENDPOINTS.recommendations(includeIdeas))),

  getRecommendationFor: (ticker) =>
    cached(`recommendation:${ticker}`, () => apiClient(API_ENDPOINTS.recommendationByTicker(ticker))),

  getRisk: () => cached("risk", () => apiClient(API_ENDPOINTS.risk)),

  getRiskFor: (ticker) => cached(`risk:${ticker}`, () => apiClient(API_ENDPOINTS.riskByTicker(ticker))),
};
