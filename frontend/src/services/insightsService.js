import { API_ENDPOINTS } from "../config/api";
import { apiClient } from "./apiClient";

export const insightsService = {
  getSummary: () => apiClient(API_ENDPOINTS.insightsSummary),
};
