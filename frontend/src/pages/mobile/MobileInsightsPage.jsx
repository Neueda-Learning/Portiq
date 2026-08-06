import { useCallback, useEffect, useState } from "react";
import Card from "../../components/common/Card";
import Skeleton from "../../components/common/Skeleton";
import RecommendationsPanel from "../../components/analysis/RecommendationsPanel";
import RiskPanel from "../../components/analysis/RiskPanel";
import { analysisService } from "../../services/analysisService";
import { useToast } from "../../context/ToastContext";

const TABS = [
  { key: "recommendations", label: "Recommendations" },
  { key: "risk", label: "Risk" },
];

/**
 * Both analysis features behind one bottom-nav tab. Five tabs would crowd the bar, so they share a
 * tab here and switch with the same chip control the dashboard uses for its date ranges.
 */
function MobileInsightsPage() {
  const toast = useToast();
  const [tab, setTab] = useState("recommendations");
  const [recommendations, setRecommendations] = useState(null);
  const [risk, setRisk] = useState(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    const [recommendationData, riskData] = await Promise.all([
      analysisService.getRecommendations(true),
      analysisService.getRisk(),
    ]);
    setRecommendations(recommendationData);
    setRisk(riskData);
  }, []);

  useEffect(() => {
    load()
      .catch((error) => toast.error(error.message))
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [load]);

  return (
    <div>
      <section className="hero">
        <div>
          <p className="eyebrow">Insights</p>
          <h1>Analysis</h1>
        </div>
      </section>

      <div className="filter-chips insights-tabs">
        {TABS.map((option) => (
          <button
            key={option.key}
            type="button"
            className={`chip ${tab === option.key ? "active" : ""}`}
            onClick={() => setTab(option.key)}
          >
            {option.label}
          </button>
        ))}
      </div>

      {loading ? (
        <div className="section-gap">
          <Card>
            <Skeleton height="220px" />
          </Card>
        </div>
      ) : tab === "recommendations" ? (
        <RecommendationsPanel data={recommendations} />
      ) : (
        <RiskPanel report={risk} compact />
      )}
    </div>
  );
}

export default MobileInsightsPage;
