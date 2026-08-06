/**
 * Shared loading indicator. Use `inline` for small in-flow spots (e.g. next to a heading),
 * the default block form for a section that's fetching, or `fullScreen` for a route-level
 * loading state (auth check, initial app boot).
 */
function Loading({ label = "Loading…", size = "md", fullScreen = false, inline = false, className = "" }) {
  const content = (
    <div
      className={`loading ${inline ? "loading-inline" : ""} loading-${size} ${className}`.trim()}
      role="status"
      aria-live="polite"
    >
      <span className="loading-spinner" aria-hidden="true" />
      {label && <span className="loading-label">{label}</span>}
    </div>
  );

  if (fullScreen) {
    return <div className="loading-fullscreen">{content}</div>;
  }

  return content;
}

export default Loading;
