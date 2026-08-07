/**
 * A progress bar with two modes, because an upload has two phases that need telling apart.
 *
 * `determinate` — a real percentage, used while bytes are going up and we genuinely know how far
 * along we are.
 *
 * `indeterminate` — a moving stripe with no number, used once the bytes have landed and the
 * server is working. Showing a percentage there would mean inventing one, and a bar that sits
 * frozen at 100% reads as a hung request rather than a working one.
 */
function ProgressBar({ value = 0, label, detail, indeterminate = false, className = "" }) {
  const percent = Math.max(0, Math.min(100, Math.round(value)));

  return (
    <div className={`progress ${className}`.trim()}>
      {(label || (!indeterminate && percent > 0)) && (
        <div className="progress-header">
          {label && <span className="progress-label">{label}</span>}
          {!indeterminate && <span className="progress-percent">{percent}%</span>}
        </div>
      )}

      <div
        className={`progress-track ${indeterminate ? "is-indeterminate" : ""}`.trim()}
        role="progressbar"
        aria-label={label || "Upload progress"}
        // An indeterminate bar deliberately carries no aria-valuenow: that is the ARIA signal for
        // "in progress, amount unknown", and screen readers announce it as such instead of
        // repeating a number that would be a guess.
        {...(indeterminate
          ? {}
          : { "aria-valuenow": percent, "aria-valuemin": 0, "aria-valuemax": 100 })}
      >
        <div
          className="progress-fill"
          style={indeterminate ? undefined : { width: `${percent}%` }}
        />
      </div>

      {detail && <p className="progress-detail">{detail}</p>}
    </div>
  );
}

export default ProgressBar;
