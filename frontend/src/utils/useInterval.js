import { useEffect, useRef } from "react";

/**
 * Runs `callback` every `delayMs` while the component is mounted, without resetting the
 * timer when `callback` changes identity between renders. Pass `null` to pause.
 */
export function useInterval(callback, delayMs) {
  const savedCallback = useRef(callback);

  useEffect(() => {
    savedCallback.current = callback;
  }, [callback]);

  useEffect(() => {
    if (delayMs == null) return undefined;
    const id = setInterval(() => savedCallback.current(), delayMs);
    return () => clearInterval(id);
  }, [delayMs]);
}
