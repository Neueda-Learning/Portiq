import { useEffect, useState } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import Sidebar from "./components/common/Sidebar";
import MobileHeader from "./components/common/MobileHeader";
import MobileShell from "./components/mobile/MobileShell";
import ProtectedRoute from "./components/common/ProtectedRoute";
import DashboardPage from "./pages/DashboardPage";
import HoldingsPage from "./pages/HoldingsPage";
import LoginPage from "./pages/LoginPage";
import RecommendationsPage from "./pages/RecommendationsPage";
import RiskPage from "./pages/RiskPage";
import MobileDashboardPage from "./pages/mobile/MobileDashboardPage";
import MobileHoldingsPage from "./pages/mobile/MobileHoldingsPage";
import MobileAccountPage from "./pages/mobile/MobileAccountPage";
import MobileInsightsPage from "./pages/mobile/MobileInsightsPage";
import { useAuth } from "./context/AuthContext";
import { useIsMobile } from "./utils/useIsMobile";

const SIDEBAR_STORAGE_KEY = "portiq_sidebar_collapsed";

function App() {
  const { isAuthenticated } = useAuth();
  const isMobile = useIsMobile();
  const [collapsed, setCollapsed] = useState(() => localStorage.getItem(SIDEBAR_STORAGE_KEY) === "true");
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => {
    localStorage.setItem(SIDEBAR_STORAGE_KEY, String(collapsed));
  }, [collapsed]);

  if (isMobile) {
    const routes = (
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <MobileDashboardPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/holdings"
          element={
            <ProtectedRoute>
              <MobileHoldingsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/insights"
          element={
            <ProtectedRoute>
              <MobileInsightsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/account"
          element={
            <ProtectedRoute>
              <MobileAccountPage />
            </ProtectedRoute>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    );

    return (
      <div className="app-shell mobile-app-shell">
        {isAuthenticated ? <MobileShell>{routes}</MobileShell> : <div className="mobile-auth">{routes}</div>}
      </div>
    );
  }

  return (
    <div className={`app-shell ${isAuthenticated ? "with-sidebar" : ""}`}>
      {isAuthenticated && (
        <>
          <MobileHeader onOpenSidebar={() => setMobileOpen(true)} />
          <Sidebar
            collapsed={collapsed}
            onToggleCollapse={() => setCollapsed((prev) => !prev)}
            mobileOpen={mobileOpen}
            onCloseMobile={() => setMobileOpen(false)}
          />
        </>
      )}
      <main className={`app-content ${isAuthenticated && collapsed ? "sidebar-collapsed" : ""}`}>
        <div className="container">
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              path="/"
              element={
                <ProtectedRoute>
                  <DashboardPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/holdings"
              element={
                <ProtectedRoute>
                  <HoldingsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/recommendations"
              element={
                <ProtectedRoute>
                  <RecommendationsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/risk"
              element={
                <ProtectedRoute>
                  <RiskPage />
                </ProtectedRoute>
              }
            />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </div>
      </main>
    </div>
  );
}

export default App;
