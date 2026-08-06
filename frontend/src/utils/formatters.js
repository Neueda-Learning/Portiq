export function formatMoney(value) {
  const numericValue = Number(value || 0);
  return `Rs ${numericValue.toLocaleString("en-IN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
}

export function formatPercent(value) {
  const numericValue = Number(value || 0);
  return `${numericValue >= 0 ? "+" : ""}${numericValue.toFixed(2)}%`;
}

export function formatSignedMoney(value) {
  const numericValue = Number(value || 0);
  return `${numericValue >= 0 ? "+" : ""}${formatMoney(numericValue)}`;
}
