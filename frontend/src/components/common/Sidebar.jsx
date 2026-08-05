import { useState } from "react";
import { NavLink, useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { authService } from "../../services/authService";
import { isWebAuthnSupported, registerBiometricCredential } from "../../utils/webauthn";

function Sidebar({ collapsed, onToggleCollapse, mobileOpen, onCloseMobile }) {
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
    <>
      {mobileOpen && <div className="sidebar-backdrop" onClick={onCloseMobile} />}
      <aside className={`sidebar ${collapsed ? "collapsed" : ""} ${mobileOpen ? "mobile-open" : ""}`}>
        <div className="sidebar-header">
          <div className="brand">{collapsed ? "P" : "Portiq"}</div>
          <button className="sidebar-toggle" onClick={onToggleCollapse} title="Collapse sidebar" aria-label="Collapse sidebar">
            {collapsed ? "»" : "«"}
          </button>
          <button className="sidebar-close" onClick={onCloseMobile} aria-label="Close menu">
            &#10005;
          </button>
        </div>

        <nav className="sidebar-nav">
          <NavLink to="/" end onClick={onCloseMobile} title="Dashboard">
            <span className="nav-icon">&#9638;</span>
            <span className="nav-label">Dashboard</span>
          </NavLink>
          <NavLink to="/holdings" onClick={onCloseMobile} title="Holdings">
            <span className="nav-icon">&#9636;</span>
            <span className="nav-label">Holdings</span>
          </NavLink>
        </nav>

        <div className="sidebar-footer">
          {isWebAuthnSupported() && !biometricEnabled && (
            <button className="link-btn full-width" onClick={handleEnrollBiometric} disabled={enrolling}>
              <span className="nav-label">{enrolling ? "Enabling..." : "Enable Biometrics"}</span>
              <span className="nav-icon-only">&#128272;</span>
            </button>
          )}
          {message && <p className="meta-line sidebar-message">{message}</p>}
          <div className="sidebar-user" title={username || ""}>
            <span className="nav-label">{username}</span>
          </div>
          <button className="link-btn full-width" onClick={handleLogout} title="Log out">
            <span className="nav-label">Log out</span>
            <span className="nav-icon-only">&#9099;</span>
          </button>
        </div>
      </aside>
    </>
  );
}

export default Sidebar;
