import Card from "../common/Card";
import RiskScoreGauge, { levelClass, levelLabel } from "./RiskScoreGauge";
import RiskMetricGrid from "./RiskMetricGrid";
import RiskHoldingsTable from "./RiskHoldingsTable";
import RiskBreakdownChart from "./RiskBreakdownChart";
import Disclaimer from "./Disclaimer";

/**
 * Shared body for the desktop and mobile risk views. The per-holding table is dropped on mobile
 * (`compact`) because ten numeric columns cannot be read on a phone - the chart carries the
 * comparison there instead.
 */
function RiskPanel({ report, compact = false }) {
  if (!report) return null;

  const holdings = report.holdings || [];
  const warnings = report.warnings || [];

  return (
    <>
      <div className="grid risk-hero-grid">
        <Card className="risk-hero-card">
          <RiskScoreGauge
            score={report.overallRiskScore}
            level={report.riskLevel}
            caption={`Value-weighted across ${report.holdingsCount} holdings`}
          />
        </Card>

        <Card className="risk-warnings-card">
          <h3 className="card-title">What to watch</h3>
          {warnings.length === 0 ? (
            <p className="meta-line">Nothing stands out as a concentration or volatility concern.</p>
          ) : (
            <ul className="risk-warning-list">
              {warnings.map((warning) => (
                <li key={warning}>{warning}</li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      <section className="section-gap-lg">
        <div className="section-heading">
          <h2>Portfolio Metrics</h2>
        </div>
        <RiskMetricGrid report={report} />
      </section>

      {report.highestRiskHoldings?.length > 0 && (
        <section className="section-gap-lg">
          <div className="section-heading">
            <h2>Highest Risk Holdings</h2>
          </div>
          <div className="grid risk-top-grid">
            {report.highestRiskHoldings.map((holding) => (
              <Card key={holding.ticker} className="risk-top-card">
                <div className="risk-top-header">
                  <span className="reco-ticker">{holding.ticker}</span>
                  <span className={`risk-badge ${levelClass(holding.riskLevel)}`}>
                    {Number(holding.riskScore).toFixed(0)} · {levelLabel(holding.riskLevel)}
                  </span>
                </div>
                <ul className="risk-driver-list">
                  {(holding.drivers || []).map((driver) => (
                    <li key={driver}>{driver}</li>
                  ))}
                </ul>
              </Card>
            ))}
          </div>
        </section>
      )}

      <section className="section-gap-lg">
        <div className="section-heading">
          <h2>Breakdown</h2>
        </div>
        <RiskBreakdownChart holdings={holdings} />
        {!compact && (
          <div className="section-gap">
            <RiskHoldingsTable holdings={holdings} />
          </div>
        )}
      </section>

      <Disclaimer text={report.disclaimer} />
    </>
  );
}

export default RiskPanel;
