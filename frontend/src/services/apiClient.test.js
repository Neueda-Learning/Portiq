import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiClient } from "./apiClient";
import { TOKEN_STORAGE_KEY } from "../config/api";

function jsonResponse(body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  };
}

describe("apiClient", () => {
  beforeEach(() => {
    global.fetch = vi.fn();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("attaches the bearer token when one is stored", async () => {
    localStorage.setItem(TOKEN_STORAGE_KEY, "a-token");
    global.fetch.mockResolvedValue(jsonResponse({ ok: true }));

    await apiClient("/api/holdings");

    const [, options] = global.fetch.mock.calls[0];
    expect(options.headers.Authorization).toBe("Bearer a-token");
  });

  it("sends no Authorization header when there is no token", async () => {
    global.fetch.mockResolvedValue(jsonResponse({ ok: true }));

    await apiClient("/api/holdings");

    const [, options] = global.fetch.mock.calls[0];
    expect(options.headers.Authorization).toBeUndefined();
  });

  it("clears an expired token on 401 and redirects to log in", async () => {
    // The backend answers 401 (not 403) precisely so this path fires; without it an expired
    // session leaves the UI stuck showing permission errors.
    //
    // window.location is replaced because jsdom cannot navigate and logs a noisy
    // "Not implemented: navigation" stack when the real one is assigned to.
    const originalLocation = window.location;
    delete window.location;
    window.location = { href: "" };

    localStorage.setItem(TOKEN_STORAGE_KEY, "expired-token");
    global.fetch.mockResolvedValue(jsonResponse({ message: "Authentication is required." }, 401));

    await expect(apiClient("/api/holdings")).rejects.toThrow(/session expired/i);

    expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull();
    expect(window.location.href).toBe("/login");

    window.location = originalLocation;
  });

  it("surfaces the server's own message when it sends one", async () => {
    global.fetch.mockResolvedValue(
      jsonResponse({ message: "Too many requests. Try again in 42 seconds." }, 429)
    );

    await expect(apiClient("/api/auth/login")).rejects.toThrow(/try again in 42 seconds/i);
  });

  it("flattens field validation errors into something readable", async () => {
    global.fetch.mockResolvedValue(
      jsonResponse(
        { message: "Some fields need fixing", errors: { ticker: "Ticker is required" } },
        400
      )
    );

    await expect(apiClient("/api/holdings", { method: "POST" })).rejects.toThrow(
      /ticker: Ticker is required/
    );
  });

  it("falls back to a status-specific message when the body carries none", async () => {
    global.fetch.mockResolvedValue({
      ok: false,
      status: 503,
      json: async () => {
        throw new Error("not json");
      },
    });

    await expect(apiClient("/api/insights/summary")).rejects.toThrow(/not available/i);
  });

  it("explains a network failure rather than reporting a generic error", async () => {
    global.fetch.mockRejectedValue(new TypeError("Failed to fetch"));

    await expect(apiClient("/api/holdings")).rejects.toThrow(/could not reach the server/i);
  });

  it("says so when the browser reports being offline", async () => {
    vi.spyOn(navigator, "onLine", "get").mockReturnValue(false);
    global.fetch.mockRejectedValue(new TypeError("Failed to fetch"));

    await expect(apiClient("/api/holdings")).rejects.toThrow(/offline/i);
  });

  it("returns null for 204 rather than trying to parse an empty body", async () => {
    global.fetch.mockResolvedValue({ ok: true, status: 204, json: async () => undefined });

    await expect(apiClient("/api/holdings/1", { method: "DELETE" })).resolves.toBeNull();
  });
});
