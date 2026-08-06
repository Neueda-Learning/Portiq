import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { authService } from "../services/authService";
import { TOKEN_STORAGE_KEY } from "../config/api";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_STORAGE_KEY));
  const [username, setUsername] = useState(null);
  const [biometricEnabled, setBiometricEnabled] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!token) {
      setLoading(false);
      return;
    }
    authService
      .me()
      .then((data) => {
        setUsername(data.username);
        setBiometricEnabled(data.biometricEnabled);
      })
      .catch(() => {
        localStorage.removeItem(TOKEN_STORAGE_KEY);
        setToken(null);
      })
      .finally(() => setLoading(false));
  }, [token]);

  const applySession = useCallback((data) => {
    localStorage.setItem(TOKEN_STORAGE_KEY, data.token);
    setToken(data.token);
    setUsername(data.username);
    setBiometricEnabled(data.biometricEnabled);
  }, []);

  const logout = useCallback(() => {
    // Revoke server-side first, so the token is dead even if a copy was captured. The local
    // clear-down runs regardless of the outcome: if the call fails - offline, or the token had
    // already expired - the user still expects to be logged out of this browser.
    authService.logout().catch(() => {});

    localStorage.removeItem(TOKEN_STORAGE_KEY);
    setToken(null);
    setUsername(null);
    setBiometricEnabled(false);
  }, []);

  const value = {
    token,
    username,
    biometricEnabled,
    loading,
    isAuthenticated: !!token,
    applySession,
    logout,
    setBiometricEnabled,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return ctx;
}
