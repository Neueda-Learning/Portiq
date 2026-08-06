import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach, vi } from "vitest";

// Unmount between tests. Without this, a component from a previous test stays in the document and
// queries like getByRole start matching the wrong element - which shows up as a confusing
// "found multiple elements" failure in whichever test happens to run second.
afterEach(() => {
  cleanup();
  localStorage.clear();
  vi.restoreAllMocks();
});

// jsdom implements neither of these, and both are called during a normal render:
// useIsMobile reads matchMedia, and the haptics helper calls navigator.vibrate.
if (!window.matchMedia) {
  window.matchMedia = (query) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  });
}

if (!navigator.vibrate) {
  navigator.vibrate = () => true;
}
