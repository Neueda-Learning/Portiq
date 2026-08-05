import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Tooltip,
  Legend,
  Filler,
} from "chart.js";
import { Line } from "react-chartjs-2";
import Card from "../common/Card";
import { formatMoney } from "../../utils/formatters";

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Legend, Filler);

function formatLabel(timestampSeconds, range) {
  const date = new Date(timestampSeconds * 1000);
  if (range === "1d") {
    return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  }
  return date.toLocaleDateString([], { day: "2-digit", month: "short" });
}

function TrendChart({ points, range }) {
  const data = {
    labels: points.map((point) => formatLabel(point.timestamp, range)),
    datasets: [
      {
        label: "Portfolio Value",
        data: points.map((point) => Number(point.value)),
        borderColor: "#00b386",
        backgroundColor: "rgba(0, 179, 134, 0.1)",
        borderWidth: 2,
        fill: true,
        tension: 0.3,
        pointRadius: 0,
      },
    ],
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        callbacks: {
          label: (context) => formatMoney(context.parsed.y),
        },
      },
    },
    scales: {
      x: {
        grid: { display: false },
        ticks: { color: "#8b95a1", font: { size: 11 } },
      },
      y: {
        grid: { color: "#e6eaef" },
        ticks: { color: "#8b95a1", font: { size: 11 }, callback: (value) => formatMoney(value) },
      },
    },
  };

  return (
    <Card>
      <h3 className="card-title">Portfolio Value</h3>
      {points.length === 0 ? (
        <p className="subtitle">Not enough price history yet</p>
      ) : (
        <div className="chart-canvas">
          <Line data={data} options={options} />
        </div>
      )}
    </Card>
  );
}

export default TrendChart;
