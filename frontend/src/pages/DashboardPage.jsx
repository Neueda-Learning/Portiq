import { useCallback, useEffect, useMemo, useState } from "react";
import Button from "../components/common/Button";
import Card from "../components/common/Card";
import StatCard from "../components/common/StatCard";
import Skeleton from "../components/common/Skeleton";
import PerformanceCharts from "../components/charts/PerformanceCharts";
import TrendChart from "../components/charts/TrendChart";
import AllocationPieChart from "../components/charts/AllocationPieChart";
import NewsList from "../components/news/NewsList";
import { holdingsService } from "../services/holdingsService";
import { newsService } from "../services/newsService";
import { insightsService } from "../services/insightsService";
import { useToast } from "../context/ToastContext";
import { useInterval } from "../utils/useInterval";
import { formatMoney, formatPercent, formatSignedMoney } from "../utils/formatters";

const RANGES = [
  { key: "1d", label: "1D" },
  { key: "1w", label: "1W" },
  { key: "1m", label: "1M" },
  { key: "all", label: "All" },
];

const AUTO_REFRESH_MS = 60_000;

function DashboardPage() {
  const toast = useToast();
  const [performance, setPerformance] = useState(null);
  const [history, setHistory] = useState([]);
  const [range, setRange] = useState("1m");
  const [news, setNews] = useState([]);
  const [refreshedAt, setRefreshedAt] = useState(() => new Date());
  const [summary, setSummary] = useState("");
  const [summaryError, setSummaryError] = useState("");
  const [summaryLoading, setSummaryLoading] = useState(false);
  const [loading, setLoading] = useState(true);

  const loadPerformance = useCallback(async () => {
    const data = await holdingsService.getAll();
    setPerformance(data);
  }, []);

  const loadHistory = useCallback(async (selectedRange) => {
    const points = await holdingsService.getHistory(selectedRange);
    setHistory(points);
  }, []);

  const loadNews = useCallback(async () => {
    const articles = await newsService.getNews();
    setNews(articles);
  }, []);

  useEffect(() => {
    Promise.all([loadPerformance(), loadNews()])
      .catch((error) => toast.error(error.message))
      .finally(() => {
        setRefreshedAt(new Date());
        setLoading(false);
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loadPerformance, loadNews]);

  useEffect(() => {
    loadHistory(range).catch(() => setHistory([]));
  }, [range, loadHistory]);

  useInterval(() => {
    Promise.all([loadPerformance(), loadHistory(range), loadNews()]).finally(() => {
      setRefreshedAt(new Date());
    });
  }, AUTO_REFRESH_MS);

  async function handleSummary() {
    setSummaryLoading(true);
    setSummaryError("");
    setSummary("");
    try {
      const data = await insightsService.getSummary();
      setSummary(data.summary);
    } catch (error) {
      setSummaryError(error.message);
    } finally {
      setSummaryLoading(false);
    }
  }

  const stats = useMemo(() => {
    if (!performance) return null;
    const returnUp = Number(performance.gainLossPercent) >= 0;
    return [
      { label: "Amount Invested", value: formatMoney(performance.totalCostBasis) },
      {
        label: "Gain / Loss",
        value: (
          <span className={`stat-pill ${returnUp ? "pill-up" : "pill-down"}`}>
            <span className="pnl-arrow">{returnUp ? "▲" : "▼"}</span>
            {formatSignedMoney(performance.totalGainLoss)}
          </span>
        ),
      },
      {
        label: "Return",
        value: (
          <span className={`stat-pill ${returnUp ? "pill-up" : "pill-down"}`}>
            <span className="pnl-arrow">{returnUp ? "▲" : "▼"}</span>
            {formatPercent(performance.gainLossPercent)}
          </span>
        ),
      },
    ];
  }, [performance]);

  if (loading) {
    return (
      <div>
        <section className="hero">
          <div>
            <Skeleton width="140px" height="13px" />
            <div className="section-gap-sm">
              <Skeleton width="240px" height="36px" />
            </div>
          </div>
        </section>
        <div className="grid stat-grid">
          {[0, 1, 2].map((key) => (
            <Card key={key} className="stat-card">
              <Skeleton width="60%" height="11px" />
              <div className="section-gap-sm">
                <Skeleton width="80%" height="24px" />
              </div>
            </Card>
          ))}
        </div>
        <div className="grid chart-grid section-gap">
          <Card>
            <Skeleton height="240px" />
          </Card>
          <Card>
            <Skeleton height="240px" />
          </Card>
        </div>
      </div>
    );
  }

  const holdings = performance?.holdings || [];
  const gainUp = performance ? Number(performance.totalGainLoss) >= 0 : true;

  return (
    <div>
      <section className="hero portfolio-hero">
        <div>
          <p className="eyebrow">Portfolio Value</p>
          <h1 className="portfolio-value">{performance ? formatMoney(performance.totalCurrentValue) : "—"}</h1>
          {performance && (
            <span className={`stat-pill portfolio-hero-pill ${gainUp ? "pill-up" : "pill-down"}`}>
              <span className="pnl-arrow">{gainUp ? "▲" : "▼"}</span>
              {formatSignedMoney(performance.totalGainLoss)} ({formatPercent(performance.gainLossPercent)})
            </span>
          )}
          <div className="hero-status-row">
            <span className="status-dot" aria-hidden="true" />
            {/* <span className="status-text">Live tracking enabled</span> */}
            <span className="meta-line">Last updated {refreshedAt.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</span>
          </div>
        </div>
        <Button onClick={handleSummary} loading={summaryLoading}>
          Generate Insight Summary
        </Button>
      </section>

      {(summary || summaryError) && (
        <div className={`summary-banner ${summaryError ? "summary-error" : ""}`}>{summaryError || summary}</div>
      )}

      {stats && (
        <div className="grid stat-grid">
          {stats.map((stat) => (
            <StatCard key={stat.label} label={stat.label} value={stat.value} />
          ))}
        </div>
      )}

      <section className="section-gap-lg">
        <div className="section-heading">
          <h2>Performance</h2>
          <div className="filter-chips">
            {RANGES.map((option) => (
              <button
                key={option.key}
                type="button"
                className={`chip ${range === option.key ? "active" : ""}`}
                onClick={() => setRange(option.key)}
              >
                {option.label}
              </button>
            ))}
          </div>
        </div>

        <div className="grid chart-grid">
          <TrendChart points={history} range={range} />
          <AllocationPieChart holdings={holdings} />
        </div>

        <div className="section-gap">
          <PerformanceCharts holdings={holdings} />
        </div>
      </section>

      <section className="section-gap-lg">
        <div className="section-heading">
          <h2>Market News</h2>
        </div>
        <NewsList articles={news} />
      </section>
    </div>
  );
}

export default DashboardPage;
