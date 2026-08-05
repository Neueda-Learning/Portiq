import { API_ENDPOINTS } from "../config/api";
import { apiClient } from "./apiClient";
import { getCached, setCached } from "../utils/cache";

const NEWS_TTL = 50_000;
const CACHE_KEY = "news:all";

export const newsService = {
  getNews: async () => {
    const cached = getCached(CACHE_KEY);
    if (cached) return cached;
    const data = await apiClient(API_ENDPOINTS.news);
    setCached(CACHE_KEY, data, NEWS_TTL);
    return data;
  },
};
