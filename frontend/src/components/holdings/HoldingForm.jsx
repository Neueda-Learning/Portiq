import Button from "../common/Button";

const HOLDING_TYPES = ["STOCK", "BOND", "CASH"];

function HoldingForm({ formData, onChange, onSubmit, onCancel, submitLabel = "Save", submitting = false }) {
  return (
    <form onSubmit={onSubmit}>
      <div className="form-grid">
        <div>
          <label htmlFor="ticker">Ticker</label>
          <input
            id="ticker"
            value={formData.ticker}
            onChange={(event) => onChange("ticker", event.target.value.toUpperCase())}
            required
          />
        </div>
        <div>
          <label htmlFor="holdingType">Type</label>
          <select
            id="holdingType"
            value={formData.type}
            onChange={(event) => onChange("type", event.target.value)}
            required
          >
            {HOLDING_TYPES.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="holdingName">Name</label>
          <input
            id="holdingName"
            value={formData.name}
            onChange={(event) => onChange("name", event.target.value)}
            required
          />
        </div>
        <div>
          <label htmlFor="holdingQty">Quantity</label>
          <input
            id="holdingQty"
            type="number"
            min="0.0001"
            step="0.0001"
            value={formData.quantity}
            onChange={(event) => onChange("quantity", event.target.value)}
            required
          />
        </div>
        <div>
          <label htmlFor="holdingPrice">Purchase Price</label>
          <input
            id="holdingPrice"
            type="number"
            min="0"
            step="0.01"
            value={formData.purchasePrice}
            onChange={(event) => onChange("purchasePrice", event.target.value)}
            required
          />
        </div>
        <div>
          <label htmlFor="holdingDate">Purchase Date</label>
          <input
            id="holdingDate"
            type="date"
            value={formData.purchaseDate}
            onChange={(event) => onChange("purchaseDate", event.target.value)}
          />
        </div>
      </div>
      <div className="actions form-actions">
        <Button variant="ghost" type="button" onClick={onCancel} disabled={submitting}>
          Cancel
        </Button>
        <Button type="submit" loading={submitting}>
          {submitLabel}
        </Button>
      </div>
    </form>
  );
}

export default HoldingForm;
