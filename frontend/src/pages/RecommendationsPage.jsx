import { useCallback, useEffect, useState } from "react";
import Button from "../components/common/Button";
import Card from "../components/common/Card";
import Skeleton from "../components/common/Skeleton";
import RecommendationsPanel from "../components/analysis/RecommendationsPanel";
import { analysisService } from "../services/analysisService";
import { useToast } from "../context/ToastContext";

function RecommendationsPage() {
  const toast = useToast();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    const response = await analysisService.getRecommendations(true);
    setData(response);
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
            <Skeleton width="160px" height="13px" />
            <div className="section-gap-sm">
              <Skeleton width="280px" height="36px" />
            </div>
          </div>
        </section>
        <div className="grid reco-grid section-gap">
          {[0, 1, 2, 3].map((key) => (
            <Card key={key}>
              <Skeleton height="180px" />
            </Card>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div>
      <section className="hero">
        <div>
          <p className="eyebrow">Recommendations</p>
          <h1>What to do next</h1>
          <p className="meta-line">
            Buy, hold and sell calls scored from a year of price history - momentum, trend, RSI and
            where the price sits in its 52-week range.
          </p>
        </div>
        <Button onClick={handleRefresh} loading={refreshing}>
          Refresh
        </Button>
      </section>

      {error && <div className="summary-banner summary-error">{error}</div>}

      <RecommendationsPanel data={data} />
    </div>
  );
}

export default RecommendationsPage;
