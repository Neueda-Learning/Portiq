import { Navigate } from "react-router-dom";
import Loading from "./Loading";
import { useAuth } from "../../context/AuthContext";

function ProtectedRoute({ children }) {
  const { isAuthenticated, loading } = useAuth();

  // Route-level gate: nothing of the app is on screen yet, so this is the
  // fullScreen case rather than a skeleton. Pages handle their own data loads
  // with Skeleton, which keeps the layout stable once the shell is up.
  if (loading) {
    return <Loading fullScreen label="Checking your session…" />;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return children;
}

export default ProtectedRoute;
