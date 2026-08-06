import { Suspense, lazy, useEffect, useState } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import Sidebar from "./components/common/Sidebar";
import MobileHeader from "./components/common/MobileHeader";
import MobileShell from "./components/mobile/MobileShell";
import ProtectedRoute from "./components/common/ProtectedRoute";
import ErrorBoundary from "./components/common/ErrorBoundary";
import Loading from "./components/common/Loading";
import LoginPage from "./pages/LoginPage";
import { useAuth } from "./context/AuthContext";
import { useIsMobile } from "./utils/useIsMobile";
import { useGlobalHapticFeedback } from "./utils/haptics";

/*
 * Routes are split so the first load only carries what the first screen needs.
 *
 * Everything used to be imported eagerly, which meant one bundle containing every page plus
 * Chart.js - so a visitor landing on the login screen downloaded the charting library, the risk
 * gauges and the recommendation cards before they could type a username. It also meant desktop
 * users downloaded all four mobile pages and vice versa, since only one branch ever renders.
 *
 * LoginPage stays eager: it is the one route an unauthenticated visitor always needs, and lazily
 * loading it would add a network round trip to the very first paint.
 */
const DashboardPage = lazy(() => import("./pages/DashboardPage"));
const HoldingsPage = lazy(() => import("./pages/HoldingsPage"));
const RecommendationsPage = lazy(() => import("./pages/RecommendationsPage"));
const RiskPage = lazy(() => import("./pages/RiskPage"));
const MobileDashboardPage = lazy(() => import("./pages/mobile/MobileDashboardPage"));
const MobileHoldingsPage = lazy(() => import("./pages/mobile/MobileHoldingsPage"));
const MobileAccountPage = lazy(() => import("./pages/mobile/MobileAccountPage"));
const MobileInsightsPage = lazy(() => import("./pages/mobile/MobileInsightsPage"));

const SIDEBAR_STORAGE_KEY = "portiq_sidebar_collapsed";

/**
 * Wraps the routed area in a boundary and a suspense fallback.
 *
 * The boundary is per-route rather than only at the root so a page that throws is recoverable by
 * navigating elsewhere - a root-only boundary would replace the whole shell, including the nav
 * the user needs to get out. A lazy chunk that fails to load (an offline reload after a deploy)
 * lands here too, which is why it is a boundary and not just Suspense.
 */
function RoutedArea({ children }) {
  return (
    <ErrorBoundary>
      <Suspense fallback={<Loading />}>{children}</Suspense>
    </ErrorBoundary>
  );
}

function App() {
  const { isAuthenticated } = useAuth();
  const isMobile = useIsMobile();
  const [collapsed, setCollapsed] = useState(() => localStorage.getItem(SIDEBAR_STORAGE_KEY) === "true");
  const [mobileOpen, setMobileOpen] = useState(false);

  // One document-level listener covers every interactive control in the app, so
  // individual components need no touch handlers. Gated on mobile because the
  // Vibration API is meaningless on desktop; it also no-ops on its own wherever
  // it is unsupported, so this is belt and braces.
  useGlobalHapticFeedback(isMobile);

  useEffect(() => {
    localStorage.setItem(SIDEBAR_STORAGE_KEY, String(collapsed));
  }, [collapsed]);

  if (isMobile) {
    const routes = (
      <RoutedArea>
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
      </RoutedArea>
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
          <RoutedArea>
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
          </RoutedArea>
        </div>
      </main>
    </div>
  );
}

export default App;
