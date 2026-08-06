import { useState } from "react";
import { useNavigate } from "react-router-dom";
import Button from "../../components/common/Button";
import Card from "../../components/common/Card";
import { useAuth } from "../../context/AuthContext";
import { authService } from "../../services/authService";
import { isWebAuthnSupported, registerBiometricCredential } from "../../utils/webauthn";

function MobileAccountPage() {
  const { username, biometricEnabled, logout, setBiometricEnabled } = useAuth();
  const navigate = useNavigate();
  const [enrolling, setEnrolling] = useState(false);
  const [message, setMessage] = useState("");

  function handleLogout() {
    logout();
    navigate("/login");
  }

  async function handleEnrollBiometric() {
    setEnrolling(true);
    setMessage("");
    try {
      const options = await authService.getRegistrationOptions();
      const credential = await registerBiometricCredential(options);
      await authService.verifyRegistration(credential);
      setBiometricEnabled(true);
      setMessage("Biometric login enabled");
    } catch (error) {
      setMessage(error.message || "Could not enable biometric login");
    } finally {
      setEnrolling(false);
    }
  }

  return (
    <div className="mobile-page">
      <div className="mobile-section-heading">
        <h1>Account</h1>
      </div>

      <Card className="mobile-account-card">
        <div className="mobile-avatar" aria-hidden="true">
          {(username || "?").slice(0, 1).toUpperCase()}
        </div>
        <div>
          <div className="mobile-account-name">{username}</div>
          <div className="meta-line">Signed in to Portiq</div>
        </div>
      </Card>

      <div className="mobile-section">
        <div className="mobile-section-heading">
          <h2>Security</h2>
        </div>
        <Card>
          <p className="subtitle">
            {biometricEnabled
              ? "Biometric sign-in is enabled for this device."
              : "Enable biometric sign-in for faster, passwordless access."}
          </p>
          {isWebAuthnSupported() && !biometricEnabled && (
            <Button className="full-width section-gap" onClick={handleEnrollBiometric} loading={enrolling}>
              Enable Biometrics
            </Button>
          )}
          {message && <p className="meta-line section-gap-sm">{message}</p>}
        </Card>
      </div>

      <div className="mobile-section">
        <Button variant="ghost" className="full-width button-danger" onClick={handleLogout}>
          Log out
        </Button>
      </div>
    </div>
  );
}

export default MobileAccountPage;
