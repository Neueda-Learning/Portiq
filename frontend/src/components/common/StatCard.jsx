import Card from "./Card";

function StatCard({ label, value, valueClassName = "" }) {
  return (
    <Card className="stat-card">
      <div className="stat-label">{label}</div>
      <div className={`stat-value ${valueClassName}`.trim()}>{value}</div>
    </Card>
  );
}

export default StatCard;
