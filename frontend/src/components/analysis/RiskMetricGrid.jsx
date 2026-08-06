import StatCard from "../common/StatCard";

function fixed(value, digits = 2, suffix = "") {
  if (value === null || value === undefined) return "—";
  return `${Number(value).toFixed(digits)}${suffix}`;
}

/**
 * The portfolio-level numbers behind the headline score. Beta and volatility are the two most
 * likely to be misread, so both carry a plain-language hint rather than standing alone.
 */
function RiskMetricGrid({ report }) {
  const metrics = [
    {
      label: "Volatility (annualised)",
      value: fixed(report.portfolioVolatilityPercent, 1, "%"),
      hint: "How much the whole portfolio swings in a year",
    },
    {
      label: `Beta vs ${report.benchmark || "market"}`,
      value: fixed(report.portfolioBeta, 2),
      hint:
        report.portfolioBeta == null
          ? null
          : Number(report.portfolioBeta) > 1
          ? "Moves more than the market"
          : "Moves less than the market",
    },
    {
      label: "Max drawdown",
      value: fixed(report.portfolioMaxDrawdownPercent, 1, "%"),
      hint: "Worst peak-to-trough fall in the last year",
    },
    {
      label: "Sharpe ratio",
      value: fixed(report.sharpeRatio, 2),
      hint: "Return earned per unit of risk taken",
    },
    {
      label: "Diversification",
      value: fixed(report.diversificationScore, 0, " / 100"),
      hint: "Higher means more evenly spread",
    },
    {
      label: "Largest position",
      value: report.topHoldingTicker
        ? `${report.topHoldingTicker} · ${fixed(report.topHoldingWeightPercent, 1, "%")}`
        : "—",
      hint: `${report.holdingsCount} holdings in total`,
    },
  ];

  return (
    <div className="grid risk-metric-grid">
      {metrics.map((metric) => (
        <StatCard
          key={metric.label}
          label={metric.label}
          value={
            <>
              <span>{metric.value}</span>
              {metric.hint && <span className="risk-metric-hint">{metric.hint}</span>}
            </>
          }
        />
      ))}
    </div>
  );
}

export default RiskMetricGrid;
