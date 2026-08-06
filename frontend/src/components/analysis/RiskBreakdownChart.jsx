import { Bar } from "react-chartjs-2";
import {
  BarElement,
  CategoryScale,
  Chart as ChartJS,
  Legend,
  LinearScale,
  Tooltip,
} from "chart.js";
import Card from "../common/Card";

ChartJS.register(CategoryScale, LinearScale, BarElement, Tooltip, Legend);

// Risk bands, coldest to hottest. Deliberately not the --series-* ramp: this axis is ordered, so
// the colour has to carry magnitude rather than just tell categories apart.
const BAND_COLORS = {
  LOW: "#00b386",
  MODERATE: "#e0a92a",
  HIGH: "#eb7a3c",
  VERY_HIGH: "#eb5b3c",
};

function RiskBreakdownChart({ holdings }) {
  const scored = (holdings || []).filter((holding) => holding.riskScore !== null && holding.riskScore !== undefined);

  if (scored.length === 0) {
    return (
      <Card>
        <h3 className="card-title">Risk by Holding</h3>
        <p className="meta-line">No holdings with enough price history to score.</p>
      </Card>
    );
  }

  const sorted = [...scored].sort((a, b) => Number(b.riskScore) - Number(a.riskScore));

  const data = {
    labels: sorted.map((holding) => holding.ticker),
    datasets: [
      {
        label: "Risk score",
        data: sorted.map((holding) => Number(holding.riskScore)),
        backgroundColor: sorted.map((holding) => BAND_COLORS[holding.riskLevel] || BAND_COLORS.MODERATE),
        borderRadius: 4,
      },
    ],
  };

  const options = {
    indexAxis: "y",
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        callbacks: {
          label: (context) => {
            const holding = sorted[context.dataIndex];
            const level = (holding.riskLevel || "").replace("_", " ").toLowerCase();
            return `${context.parsed.x.toFixed(1)} / 100 (${level})`;
          },
          afterLabel: (context) => {
            const holding = sorted[context.dataIndex];
            return holding.weightPercent != null ? `${Number(holding.weightPercent).toFixed(1)}% of portfolio` : "";
          },
        },
      },
    },
    scales: {
      x: { min: 0, max: 100, title: { display: true, text: "Risk score" } },
      y: { grid: { display: false } },
    },
  };

  return (
    <Card>
      <h3 className="card-title">Risk by Holding</h3>
      <div className="risk-chart-wrap" style={{ height: `${Math.max(160, sorted.length * 34 + 60)}px` }}>
        <Bar data={data} options={options} />
      </div>
    </Card>
  );
}

export default RiskBreakdownChart;
