import { Bar } from "react-chartjs-2";
import { Chart as ChartJS, CategoryScale, LinearScale, BarElement, Tooltip, Legend } from "chart.js";
import Card from "../common/Card";
import { formatMoney } from "../../utils/formatters";

ChartJS.register(CategoryScale, LinearScale, BarElement, Tooltip, Legend);

function PerformanceCharts({ holdings }) {
  if (!holdings || holdings.length === 0) {
    return null;
  }

  const data = {
    labels: holdings.map((holding) => holding.ticker),
    datasets: [
      {
        label: "Amount Invested",
        data: holdings.map((holding) => Number(holding.costBasis || 0)),
        backgroundColor: "#c7d0da",
        borderRadius: 4,
        maxBarThickness: 28,
      },
      {
        label: "Current Value",
        data: holdings.map((holding) => Number(holding.currentValue || 0)),
        backgroundColor: "#00b386",
        borderRadius: 4,
        maxBarThickness: 28,
      },
    ],
  };

  const options = {
    maintainAspectRatio: false,
    plugins: {
      legend: {
        labels: { color: "#5a6472", font: { size: 12 } },
      },
      tooltip: {
        callbacks: {
          label: (context) => `${context.dataset.label}: ${formatMoney(context.parsed.y)}`,
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
      <h3 className="card-title">Investment vs Current Value</h3>
      <div className="chart-canvas chart-canvas-wide">
        <Bar data={data} options={options} />
      </div>
    </Card>
  );
}

export default PerformanceCharts;
