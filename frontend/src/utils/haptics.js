import { useEffect } from "react";

// Short, distinct vibration patterns (ms) for different kinds of feedback.
const HAPTIC_PATTERNS = {
  light: 8,
  medium: 16,
  success: [10, 30, 12],
  warning: [14, 40, 14],
  error: [18, 40, 18, 40, 18],
};

/**
 * Fires a vibration pulse on devices/browsers that support the Vibration API. Silently no-ops
 * everywhere else (desktop browsers, iOS Safari, denied permissions, etc.) so it is always safe
 * to call.
 */
export function triggerHaptic(pattern = "light") {
  if (typeof window === "undefined" || typeof window.navigator?.vibrate !== "function") {
    return;
  }
  const value = typeof pattern === "string" ? HAPTIC_PATTERNS[pattern] || HAPTIC_PATTERNS.light : pattern;
  try {
    window.navigator.vibrate(value);
  } catch {
    // Vibration can throw in some embedded webviews - ignore, it's a non-essential enhancement.
  }
}

// Elements that count as "a touch" worth acknowledging with haptic feedback. Kept broad so every
// interactive control across the mobile app is covered without having to wire each one up by hand.
const INTERACTIVE_SELECTOR = [
  "button",
  "a[href]",
  "input[type='checkbox']",
  "input[type='radio']",
  "select",
  "[role='button']",
  "[role='tab']",
  ".chip",
  ".bottom-nav-item",
  ".holding-card",
  ".mobile-mover-card",
  ".icon-btn",
  ".hamburger",
  ".sidebar-toggle",
  ".sidebar-close",
  "[data-haptic]",
].join(", ");

/**
 * Attaches a single document-level listener that gives every tap on an interactive element a
 * light haptic pulse while `enabled` (i.e. on mobile). Centralising this avoids sprinkling
 * `triggerHaptic()` calls through every component's touch handlers.
 */
export function useGlobalHapticFeedback(enabled = true) {
  useEffect(() => {
    if (!enabled || typeof window === "undefined" || typeof window.navigator?.vibrate !== "function") {
      return undefined;
    }

    function handleTouchStart(event) {
      const target = event.target instanceof Element ? event.target.closest(INTERACTIVE_SELECTOR) : null;
      if (!target || target.disabled || target.getAttribute("aria-disabled") === "true") {
        return;
      }
      triggerHaptic(target.dataset.haptic || "light");
    }

    document.addEventListener("touchstart", handleTouchStart, { passive: true });
    return () => document.removeEventListener("touchstart", handleTouchStart);
  }, [enabled]);
}
