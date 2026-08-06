import { useMemo, useState } from "react";
import { Chart as ChartJS, ArcElement, Tooltip, Legend } from "chart.js";
import { Pie } from "react-chartjs-2";
import Card from "../common/Card";
import { formatMoney } from "../../utils/formatters";

ChartJS.register(ArcElement, Tooltip, Legend);

const COLORS = ["#00b386", "#5367ff", "#eb5b3c", "#00a3ff", "#ffb648", "#8b5cf6", "#ff6f91", "#364152"];

function AllocationPieChart({ holdings }) {
  const [mode, setMode] = useState("value");

  const data = useMemo(() => {
    const labels = holdings.map((holding) => holding.ticker);
    const values = holdings.map((holding) =>
      mode === "value" ? Number(holding.currentValue || 0) : Number(holding.quantity || 0)
    );
    return {
      labels,
      datasets: [
        {
          data: values,
          backgroundColor: labels.map((_, index) => COLORS[index % COLORS.length]),
          borderColor: "#ffffff",
          borderWidth: 2,
        },
      ],
    };
  }, [holdings, mode]);

  const options = {
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: "right",
        labels: { color: "#5a6472", font: { size: 11 }, boxWidth: 10, padding: 10 },
      },
      tooltip: {
        callbacks: {
          label: (context) =>
            mode === "value"
              ? `${context.label}: ${formatMoney(context.parsed)}`
              : `${context.label}: ${context.parsed}`,
        },
      },
    },
  };

  return (
    <Card>
      <div className="chart-header">
        <h3 className="card-title">Allocation</h3>
        <div className="filter-chips">
          <button type="button" className={`chip ${mode === "value" ? "active" : ""}`} onClick={() => setMode("value")}>
            By Value
          </button>
          <button
            type="button"
            className={`chip ${mode === "quantity" ? "active" : ""}`}
            onClick={() => setMode("quantity")}
          >
            By Quantity
          </button>
        </div>
      </div>
      {holdings.length === 0 ? (
        <p className="subtitle">No holdings yet</p>
      ) : (
        <div className="chart-canvas">
          <Pie data={data} options={options} />
        </div>
      )}
    </Card>
  );
}

export default AllocationPieChart;
