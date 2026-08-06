import Card from "../common/Card";
import { formatMoney, formatPercent } from "../../utils/formatters";
import { levelLabel } from "./RiskScoreGauge";

// Buy-side actions read green, sell-side red, hold neutral - matching the gain/loss pills the rest
// of the app already uses, so the colour language stays consistent.
const ACTION_STYLES = {
  BUY: { className: "pill-up", label: "Buy" },
  ACCUMULATE: { className: "pill-up", label: "Accumulate" },
  HOLD: { className: "pill-neutral", label: "Hold" },
  TRIM: { className: "pill-warn", label: "Trim" },
  SELL: { className: "pill-down", label: "Sell" },
  AVOID: { className: "pill-down", label: "Avoid" },
};

function Metric({ label, value }) {
  if (value === null || value === undefined) return null;
  return (
    <div className="reco-metric">
      <span className="reco-metric-label">{label}</span>
      <span className="reco-metric-value">{value}</span>
    </div>
  );
}

function RecommendationCard({ recommendation }) {
  const action = ACTION_STYLES[recommendation.action] || ACTION_STYLES.HOLD;
  const confidence = Number(recommendation.confidence || 0);

  return (
    <Card className="reco-card">
      <div className="reco-header">
        <div className="reco-identity">
          <div className="reco-ticker">{recommendation.ticker}</div>
          {recommendation.name && recommendation.name !== recommendation.ticker && (
            <div className="reco-name">{recommendation.name}</div>
          )}
        </div>
        <div className="reco-header-right">
          <span className={`stat-pill ${action.className}`}>{action.label}</span>
          <div className="reco-price">{formatMoney(recommendation.currentPrice)}</div>
        </div>
      </div>

      <div className="reco-confidence" title={`Confidence ${confidence.toFixed(0)} out of 100`}>
        <div className="reco-confidence-bar">
          <div className={`reco-confidence-fill ${action.className}`} style={{ width: `${confidence}%` }} />
        </div>
        <span className="meta-line">{confidence.toFixed(0)}% confidence</span>
      </div>

      <p className="reco-reason">{recommendation.reason}</p>

      {recommendation.signals?.length > 0 && (
        <div className="filter-chips reco-signals">
          {recommendation.signals.map((signal) => (
            <span key={signal} className="chip reco-signal">
              {signal}
            </span>
          ))}
        </div>
      )}

      <div className="reco-metrics">
        <Metric label="Trend" value={recommendation.trend?.toLowerCase()} />
        <Metric
          label="90d"
          value={recommendation.momentum90dPercent != null ? formatPercent(recommendation.momentum90dPercent) : null}
        />
        <Metric label="RSI" value={recommendation.rsi14 != null ? Number(recommendation.rsi14).toFixed(0) : null} />
        <Metric
          label="Risk"
          value={
            recommendation.riskScore != null
              ? `${Number(recommendation.riskScore).toFixed(0)} · ${levelLabel(recommendation.riskLevel)}`
              : null
          }
        />
        {recommendation.held && (
          <>
            <Metric
              label="Your P&L"
              value={
                recommendation.gainLossPercent != null ? formatPercent(recommendation.gainLossPercent) : null
              }
            />
            <Metric
              label="Weight"
              value={
                recommendation.weightPercent != null
                  ? `${Number(recommendation.weightPercent).toFixed(1)}% → ${Number(
                      recommendation.suggestedWeightPercent || 0
                    ).toFixed(1)}%`
                  : null
              }
            />
          </>
        )}
      </div>
    </Card>
  );
}

export default RecommendationCard;
