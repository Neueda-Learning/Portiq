import { formatMoney, formatPercent, formatSignedMoney } from "../../utils/formatters";

function HoldingCard({ holding, selected, onToggleSelect, onEdit, onDelete }) {
  const up = Number(holding.gainLoss) >= 0;

  return (
    <div className={`holding-card ${selected ? "selected" : ""}`}>
      <input
        type="checkbox"
        className="holding-card-check"
        checked={selected}
        onChange={onToggleSelect}
        aria-label={`Select ${holding.ticker}`}
      />
      <div className="holding-card-main">
        <div className="holding-card-top">
          <div>
            <div className="holding-card-ticker">{holding.ticker}</div>
            <div className="holding-card-name">{holding.name}</div>
          </div>
          <div className="holding-card-value">
            <div>{formatMoney(holding.currentValue)}</div>
            <span className={up ? "pnl-up" : "pnl-down"}>
              <span className="pnl-arrow">{up ? "▲" : "▼"}</span> {formatPercent(holding.gainLossPercent)}
            </span>
          </div>
        </div>
        <div className="holding-card-meta">
          <span>
            {holding.quantity} × {formatMoney(holding.purchasePrice)}
          </span>
          <span className="badge">{holding.type}</span>
        </div>
        <div className="holding-card-footer">
          <span className={up ? "pnl-up" : "pnl-down"}>{formatSignedMoney(holding.gainLoss)}</span>
          <div className="actions">
            <button className="icon-btn" title="Edit" onClick={onEdit}>
              ✎
            </button>
            <button className="icon-btn danger" title="Delete" onClick={onDelete}>
              ✕
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default HoldingCard;
