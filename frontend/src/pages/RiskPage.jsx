import { useCallback, useEffect, useState } from "react";
import Button from "../components/common/Button";
import Card from "../components/common/Card";
import Skeleton from "../components/common/Skeleton";
import RiskPanel from "../components/analysis/RiskPanel";
import { analysisService } from "../services/analysisService";
import { useToast } from "../context/ToastContext";

function RiskPage() {
  const toast = useToast();
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    const response = await analysisService.getRisk();
    setReport(response);
  }, []);

  useEffect(() => {
    load()
      .catch((loadError) => {
        setError(loadError.message);
        toast.error(loadError.message);
      })
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [load]);

  async function handleRefresh() {
    setRefreshing(true);
    setError("");
    try {
      await load();
    } catch (refreshError) {
      setError(refreshError.message);
      toast.error(refreshError.message);
    } finally {
      setRefreshing(false);
    }
  }

  if (loading) {
    return (
      <div>
        <section className="hero">
          <div>
            <Skeleton width="120px" height="13px" />
            <div className="section-gap-sm">
              <Skeleton width="260px" height="36px" />
            </div>
          </div>
        </section>
        <div className="grid risk-hero-grid section-gap">
          <Card>
            <Skeleton height="200px" />
          </Card>
          <Card>
            <Skeleton height="200px" />
          </Card>
        </div>
      </div>
    );
  }

  return (
    <div>
      <section className="hero">
        <div>
          <p className="eyebrow">Risk Analysis</p>
          <h1>How much risk you carry</h1>
          <p className="meta-line">
            Scored 0-100 from volatility, beta, drawdown, value at risk and position size
            {report?.benchmark ? `, measured against ${report.benchmark}` : ""}.
          </p>
        </div>
        <Button onClick={handleRefresh} loading={refreshing}>
          Refresh
        </Button>
      </section>

      {error && <div className="summary-banner summary-error">{error}</div>}

      <RiskPanel report={report} />
    </div>
  );
}

export default RiskPage;
