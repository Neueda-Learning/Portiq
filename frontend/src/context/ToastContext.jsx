import { createContext, useCallback, useContext, useRef, useState } from "react";
import { triggerHaptic } from "../utils/haptics";
import { CloseIcon } from "../components/common/icons";

const ToastContext = createContext(null);
let idCounter = 0;

// The global touch listener only ever fires a light tap pulse. Outcomes deserve
// a distinct one, and every success and failure in the app already surfaces as a
// toast - so hooking in here covers all of them without touching call sites.
const TOAST_HAPTICS = {
  success: "success",
  error: "error",
  info: "light",
};

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const timers = useRef({});

  const dismiss = useCallback((id) => {
    setToasts((prev) => prev.filter((toast) => toast.id !== id));
    clearTimeout(timers.current[id]);
    delete timers.current[id];
  }, []);

  const showToast = useCallback(
    (message, type = "info", duration = 4000) => {
      const id = ++idCounter;
      setToasts((prev) => [...prev, { id, message, type }]);
      triggerHaptic(TOAST_HAPTICS[type] || "light");
      timers.current[id] = setTimeout(() => dismiss(id), duration);
      return id;
    },
    [dismiss]
  );

  const value = {
    showToast,
    success: (message, duration) => showToast(message, "success", duration),
    error: (message, duration) => showToast(message, "error", duration),
    info: (message, duration) => showToast(message, "info", duration),
    dismiss,
  };

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toast-stack" aria-live="polite">
        {toasts.map((toast) => (
          <div key={toast.id} className={`toast toast-${toast.type}`} role="status">
            <span>{toast.message}</span>
            <button className="toast-close" onClick={() => dismiss(toast.id)} aria-label="Dismiss notification">
              <CloseIcon size={14} />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    throw new Error("useToast must be used within a ToastProvider");
  }
  return ctx;
}
