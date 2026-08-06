import { useCallback, useEffect, useMemo, useState } from "react";
import Button from "../../components/common/Button";
import Skeleton from "../../components/common/Skeleton";
import TrendChart from "../../components/charts/TrendChart";
import AllocationPieChart from "../../components/charts/AllocationPieChart";
import PerformanceCharts from "../../components/charts/PerformanceCharts";
import NewsList from "../../components/news/NewsList";
import { holdingsService } from "../../services/holdingsService";
import { newsService } from "../../services/newsService";
import { insightsService } from "../../services/insightsService";
import { useToast } from "../../context/ToastContext";
import { useInterval } from "../../utils/useInterval";
import { formatMoney, formatPercent, formatSignedMoney } from "../../utils/formatters";

const RANGES = [
  { key: "1d", label: "1D" },
  { key: "1w", label: "1W" },
  { key: "1m", label: "1M" },
  { key: "all", label: "All" },
];

const AUTO_REFRESH_MS = 60_000;

function MobileDashboardPage() {
  const toast = useToast();
  const [performance, setPerformance] = useState(null);
  const [history, setHistory] = useState([]);
  const [range, setRange] = useState("1m");
  const [news, setNews] = useState([]);
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
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loadPerformance, loadNews]);

  useEffect(() => {
    loadHistory(range).catch(() => setHistory([]));
  }, [range, loadHistory]);

  useInterval(() => {
    Promise.all([loadPerformance(), loadHistory(range), loadNews()]);
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

  const holdings = performance?.holdings || [];
  const gainUp = performance ? Number(performance.totalGainLoss) >= 0 : true;

  const topMovers = useMemo(() => {
    return [...holdings].sort((a, b) => Math.abs(Number(b.gainLossPercent)) - Math.abs(Number(a.gainLossPercent))).slice(0, 4);
  }, [holdings]);

  if (loading) {
    return (
      <div className="mobile-page">
        <div className="mobile-hero-card">
          <Skeleton width="120px" height="12px" />
          <div className="section-gap-sm">
            <Skeleton width="70%" height="32px" />
          </div>
        </div>
        <Skeleton height="200px" className="section-gap" />
      </div>
    );
  }

  return (
    <div className="mobile-page">
      <div className="mobile-hero-card">
        <p className="eyebrow">Portfolio Value</p>
        <h1 className="portfolio-value">{performance ? formatMoney(performance.totalCurrentValue) : "—"}</h1>
        {performance && (
          <span className={`stat-pill portfolio-hero-pill ${gainUp ? "pill-up" : "pill-down"}`}>
            <span className="pnl-arrow">{gainUp ? "▲" : "▼"}</span>
            {formatSignedMoney(performance.totalGainLoss)} ({formatPercent(performance.gainLossPercent)})
          </span>
        )}
        <div className="mobile-hero-meta">
          <span className="status-dot" aria-hidden="true" />
          <span>Live · refreshes every minute</span>
        </div>
        <Button className="full-width section-gap" onClick={handleSummary} loading={summaryLoading}>
          Generate Insight Summary
        </Button>
        {(summary || summaryError) && (
          <div className={`summary-banner ${summaryError ? "summary-error" : ""}`}>{summaryError || summary}</div>
        )}
      </div>

      <div className="mobile-section">
        <div className="mobile-section-heading">
          <h2>Performance</h2>
          <div className="filter-chips mobile-chip-scroll">
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
        <TrendChart points={history} range={range} />
      </div>

      {topMovers.length > 0 && (
        <div className="mobile-section">
          <div className="mobile-section-heading">
            <h2>Top Movers</h2>
          </div>
          <div className="mobile-mover-row">
            {topMovers.map((holding) => {
              const up = Number(holding.gainLoss) >= 0;
              return (
                <div key={holding.id} className="mobile-mover-card">
                  <span className="mobile-mover-ticker">{holding.ticker}</span>
                  <span className={up ? "pnl-up" : "pnl-down"}>
                    <span className="pnl-arrow">{up ? "▲" : "▼"}</span> {formatPercent(holding.gainLossPercent)}
                  </span>
                </div>
              );
            })}
          </div>
        </div>
      )}

      <div className="mobile-section">
        <AllocationPieChart holdings={holdings} />
      </div>

      <div className="mobile-section">
        <PerformanceCharts holdings={holdings} />
      </div>

      <div className="mobile-section">
        <div className="mobile-section-heading">
          <h2>Market News</h2>
        </div>
        <NewsList articles={news} />
      </div>
    </div>
  );
}

export default MobileDashboardPage;
