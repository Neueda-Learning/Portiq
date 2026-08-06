import DataTable from "../common/DataTable";
import { levelClass, levelLabel } from "./RiskScoreGauge";

function number(value, digits = 2, suffix = "") {
  if (value === null || value === undefined) return <span className="muted-cell">—</span>;
  return `${Number(value).toFixed(digits)}${suffix}`;
}

const COLUMNS = [
  {
    key: "ticker",
    title: "Ticker",
    render: (row) => (
      <div className="risk-table-identity">
        <span className="risk-table-ticker">{row.ticker}</span>
        {row.name && row.name !== row.ticker && <span className="risk-table-name">{row.name}</span>}
      </div>
    ),
  },
  {
    key: "riskScore",
    title: "Risk",
    render: (row) =>
      row.riskScore == null ? (
        <span className="muted-cell">No data</span>
      ) : (
        <span className={`risk-badge ${levelClass(row.riskLevel)}`}>
          {Number(row.riskScore).toFixed(0)} · {levelLabel(row.riskLevel)}
        </span>
      ),
  },
  { key: "weightPercent", title: "Weight", render: (row) => number(row.weightPercent, 1, "%") },
  {
    key: "volatility",
    title: "Volatility",
    render: (row) => number(row.metrics?.annualisedVolatilityPercent, 1, "%"),
  },
  { key: "beta", title: "Beta", render: (row) => number(row.metrics?.beta, 2) },
  {
    key: "drawdown",
    title: "Max drawdown",
    render: (row) => number(row.metrics?.maxDrawdownPercent, 1, "%"),
  },
  {
    key: "var",
    title: "1-day VaR 95%",
    render: (row) => number(row.metrics?.valueAtRisk95Percent, 2, "%"),
  },
  {
    key: "drivers",
    title: "What drives it",
    render: (row) => (
      <ul className="risk-driver-list">
        {(row.drivers || []).map((driver) => (
          <li key={driver}>{driver}</li>
        ))}
      </ul>
    ),
  },
];

function RiskHoldingsTable({ holdings }) {
  return (
    <DataTable
      columns={COLUMNS}
      rows={holdings || []}
      emptyText="No holdings to analyse yet"
    />
  );
}

export default RiskHoldingsTable;
