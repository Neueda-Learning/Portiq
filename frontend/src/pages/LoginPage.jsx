import { useState } from "react";
import { Navigate } from "react-router-dom";
import Button from "../components/common/Button";
import Card from "../components/common/Card";
import { useAuth } from "../context/AuthContext";
import { authService } from "../services/authService";
import { getBiometricAssertion, isWebAuthnSupported } from "../utils/webauthn";

function LoginPage() {
  const { isAuthenticated, applySession } = useAuth();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [biometricSubmitting, setBiometricSubmitting] = useState(false);

  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  async function handlePasswordLogin(event) {
    event.preventDefault();
    setError("");
    setSubmitting(true);
    try {
      const session = await authService.login(username.trim(), password);
      applySession(session);
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleBiometricLogin() {
    setError("");
    setBiometricSubmitting(true);
    try {
      const options = await authService.getLoginOptions();
      const credential = await getBiometricAssertion(options);
      const session = await authService.verifyLogin(credential);
      applySession(session);
    } catch (err) {
      setError(err.message || "Biometric sign-in was not completed");
    } finally {
      setBiometricSubmitting(false);
    }
  }

  return (
    <div className="login-screen">
      <div className="login-layout">
        <section className="login-showcase" aria-hidden="true">
          <p className="login-kicker">Portfolio intelligence</p>
          <h1>Decisions at market speed</h1>
          <p>
            Track holdings, compare allocation, and monitor gain and loss with a workspace designed for confident
            investing.
          </p>
          <div className="login-showcase-grid">
            <article>
              <span>Live data</span>
              <strong>60s refresh cadence</strong>
            </article>
            <article>
              <span>Smart import</span>
              <strong>CSV, Excel, image parsing</strong>
            </article>
            <article>
              <span>Secure access</span>
              <strong>Password and biometrics</strong>
            </article>
          </div>
        </section>

        <Card className="login-card">
          <div className="brand login-brand">Portiq</div>
          <p className="subtitle">Sign in to access your portfolio workspace</p>

          <form onSubmit={handlePasswordLogin}>
            <div className="form-group">
              <label htmlFor="username">Username</label>
              <input
                id="username"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                autoComplete="username"
                required
              />
            </div>
            <div className="form-group">
              <label htmlFor="password">Password</label>
              <div className="input-with-action">
                <input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  autoComplete="current-password"
                  required
                />
                <button
                  type="button"
                  className="input-action-btn"
                  onClick={() => setShowPassword((prev) => !prev)}
                  aria-label={showPassword ? "Hide password" : "Show password"}
                  aria-pressed={showPassword}
                  tabIndex={-1}
                >
                  {showPassword ? "\u{1F648}" : "\u{1F441}"}
                </button>
              </div>
            </div>

            {error && <p className="login-error">{error}</p>}

            <div className="actions form-actions">
              <Button type="submit" loading={submitting} className="full-width">
                Sign in
              </Button>
            </div>
          </form>

          {isWebAuthnSupported() && (
            <>
              <div className="login-divider">or</div>
              <Button
                variant="ghost"
                className="full-width"
                onClick={handleBiometricLogin}
                loading={biometricSubmitting}
              >
                Sign in with biometrics
              </Button>
            </>
          )}
        </Card>
      </div>
    </div>
  );
}

export default LoginPage;
