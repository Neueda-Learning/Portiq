import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { holdingsService } from "./holdingsService";
import { TOKEN_STORAGE_KEY } from "../config/api";

/**
 * A minimal XMLHttpRequest stand-in. jsdom ships one, but it cannot be driven — these tests need
 * to fire upload progress events on demand, which is the whole behaviour under test.
 */
class FakeXhr {
  static last = null;

  constructor() {
    this.upload = { listeners: {}, addEventListener: (t, fn) => (this.upload.listeners[t] = fn) };
    this.listeners = {};
    this.headers = {};
    this.status = 200;
    this.responseText = "{}";
    FakeXhr.last = this;
  }

  open(method, url) {
    this.method = method;
    this.url = url;
  }

  setRequestHeader(key, value) {
    this.headers[key] = value;
  }

  addEventListener(type, fn) {
    this.listeners[type] = fn;
  }

  send(body) {
    this.body = body;
  }

  // -- drivers used by the tests --
  emitUpload(loaded, total, lengthComputable = true) {
    this.upload.listeners.progress?.({ loaded, total, lengthComputable });
  }

  finishUpload() {
    this.upload.listeners.load?.();
  }

  respond(status, text) {
    this.status = status;
    this.responseText = text;
    this.listeners.load?.();
  }

  fail() {
    this.listeners.error?.();
  }
}

describe("holdingsService upload progress", () => {
  beforeEach(() => {
    vi.stubGlobal("XMLHttpRequest", FakeXhr);
    localStorage.setItem(TOKEN_STORAGE_KEY, "a-token");
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    FakeXhr.last = null;
  });

  it("reports real upload percentages as bytes go out", async () => {
    const seen = [];
    const promise = holdingsService.importCsv(
      new File(["ticker,name"], "holdings.csv"),
      (p) => seen.push(p)
    );

    const xhr = FakeXhr.last;
    xhr.emitUpload(25, 100);
    xhr.emitUpload(50, 100);
    xhr.respond(200, JSON.stringify({ imported: 3, errors: [] }));
    await promise;

    expect(seen[0]).toEqual({ phase: "uploading", percent: 0 });
    expect(seen).toContainEqual({ phase: "uploading", percent: 25 });
    expect(seen).toContainEqual({ phase: "uploading", percent: 50 });
  });

  it("switches to the processing phase once the bytes are up", async () => {
    // This is the point of splitting the phases: the file lands quickly and the server then
    // spends seconds on it, so the bar has to stop claiming to measure something.
    const seen = [];
    const promise = holdingsService.importImage(
      new File(["x"], "statement.png"),
      (p) => seen.push(p)
    );

    const xhr = FakeXhr.last;
    xhr.emitUpload(100, 100);
    xhr.respond(200, JSON.stringify({ imported: 2, errors: [] }));
    await promise;

    expect(seen).toContainEqual({ phase: "processing", percent: 100 });
  });

  it("still reaches the processing phase when no progress events fire", async () => {
    // Browsers may skip progress events entirely for a small body, which would otherwise leave
    // the bar frozen at zero for the whole request.
    const seen = [];
    const promise = holdingsService.importCsv(new File(["a"], "tiny.csv"), (p) => seen.push(p));

    const xhr = FakeXhr.last;
    xhr.finishUpload();
    xhr.respond(200, JSON.stringify({ imported: 1, errors: [] }));
    await promise;

    expect(seen.at(-1)).toEqual({ phase: "processing", percent: 100 });
  });

  it("ignores progress events with no known total", async () => {
    const seen = [];
    const promise = holdingsService.importCsv(new File(["a"], "x.csv"), (p) => seen.push(p));

    const xhr = FakeXhr.last;
    xhr.emitUpload(10, 0, false);
    xhr.respond(200, JSON.stringify({ imported: 0, errors: [] }));
    await promise;

    expect(seen.filter((p) => p.percent > 0 && p.phase === "uploading")).toHaveLength(0);
  });

  it("sends the auth token and lets the browser set the content type", async () => {
    const promise = holdingsService.importCsv(new File(["a"], "x.csv"));
    const xhr = FakeXhr.last;
    xhr.respond(200, "{}");
    await promise;

    expect(xhr.headers.Authorization).toBe("Bearer a-token");
    // Setting it by hand would omit the multipart boundary and the server could not parse it.
    expect(xhr.headers["Content-Type"]).toBeUndefined();
  });

  it("surfaces the server's error message", async () => {
    const promise = holdingsService.importCsv(new File(["a"], "x.csv"));
    FakeXhr.last.respond(400, JSON.stringify({ message: "That file is not a readable image." }));

    await expect(promise).rejects.toThrow("That file is not a readable image.");
  });

  it("explains a network failure rather than reporting a generic error", async () => {
    const promise = holdingsService.importCsv(new File(["a"], "x.csv"));
    FakeXhr.last.fail();

    await expect(promise).rejects.toThrow(/could not reach the server/i);
  });

  it("clears an expired session on 401, like every other request", async () => {
    const originalLocation = window.location;
    delete window.location;
    window.location = { href: "" };

    const promise = holdingsService.importCsv(new File(["a"], "x.csv"));
    FakeXhr.last.respond(401, JSON.stringify({ message: "Authentication is required." }));

    await expect(promise).rejects.toThrow(/session expired/i);
    expect(localStorage.getItem(TOKEN_STORAGE_KEY)).toBeNull();
    expect(window.location.href).toBe("/login");

    window.location = originalLocation;
  });

  it("works when no progress callback is supplied", async () => {
    const promise = holdingsService.importCsv(new File(["a"], "x.csv"));
    FakeXhr.last.respond(200, JSON.stringify({ imported: 1, errors: [] }));

    await expect(promise).resolves.toEqual({ imported: 1, errors: [] });
  });
});
