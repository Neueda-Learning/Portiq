const SIZE = 180;
const STROKE = 14;
const RADIUS = (SIZE - STROKE) / 2;

// A 240-degree arc reads as a gauge without needing a needle. Angles are clock angles: 0 is 12
// o'clock and they increase clockwise. Starting at 240 (lower left) and sweeping 240 degrees ends
// at 480 (= 120, lower right), which centres the 120-degree gap at the bottom.
const SWEEP = 240;
const START_ANGLE = 240;

function polarToCartesian(angleDegrees) {
  const radians = ((angleDegrees - 90) * Math.PI) / 180;
  return {
    x: SIZE / 2 + RADIUS * Math.cos(radians),
    y: SIZE / 2 + RADIUS * Math.sin(radians),
  };
}

function arcPath(fromAngle, toAngle) {
  const start = polarToCartesian(fromAngle);
  const end = polarToCartesian(toAngle);
  const largeArc = toAngle - fromAngle > 180 ? 1 : 0;
  return `M ${start.x} ${start.y} A ${RADIUS} ${RADIUS} 0 ${largeArc} 1 ${end.x} ${end.y}`;
}

function levelClass(level) {
  return `risk-${(level || "unknown").toLowerCase().replace("_", "-")}`;
}

function levelLabel(level) {
  if (!level) return "Not enough data";
  return level.replace("_", " ").toLowerCase().replace(/\b\w/g, (char) => char.toUpperCase());
}

function RiskScoreGauge({ score, level, caption }) {
  const hasScore = score !== null && score !== undefined;
  const numericScore = hasScore ? Math.max(0, Math.min(100, Number(score))) : 0;
  const endAngle = START_ANGLE + (SWEEP * numericScore) / 100;

  return (
    <div className="risk-gauge">
      <svg viewBox={`0 0 ${SIZE} ${SIZE}`} width={SIZE} height={SIZE} role="img"
           aria-label={hasScore ? `Risk score ${numericScore} out of 100, ${levelLabel(level)}` : "Risk score unavailable"}>
        <path
          d={arcPath(START_ANGLE, START_ANGLE + SWEEP)}
          className="risk-gauge-track"
          strokeWidth={STROKE}
          strokeLinecap="round"
          fill="none"
        />
        {hasScore && numericScore > 0 && (
          <path
            d={arcPath(START_ANGLE, endAngle)}
            className={`risk-gauge-value ${levelClass(level)}`}
            strokeWidth={STROKE}
            strokeLinecap="round"
            fill="none"
          />
        )}
      </svg>
      <div className="risk-gauge-readout">
        <div className={`risk-gauge-score ${levelClass(level)}`}>{hasScore ? numericScore.toFixed(0) : "—"}</div>
        <div className="risk-gauge-level">{levelLabel(level)}</div>
        {caption && <div className="meta-line">{caption}</div>}
      </div>
    </div>
  );
}

export { levelClass, levelLabel };
export default RiskScoreGauge;
