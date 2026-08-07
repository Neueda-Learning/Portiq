/**
 * The application's icon set.
 *
 * Drawn inline rather than pulled from an icon package: there are a dozen of them, they never
 * change, and a dependency would add weight to a bundle that was deliberately cut from 417 kB to
 * 187 kB. It also keeps every icon on one grid with one stroke weight, which is what actually
 * makes a set look like a set.
 *
 * Conventions, matching what BottomNav already used:
 *   - 24×24 viewBox, so paths are interchangeable between sizes
 *   - stroke, never fill, at width 1.75 with round caps and joins
 *   - stroke="currentColor", so an icon inherits the nav link's colour and its active state
 *     without any icon-specific CSS
 *
 * These replace a set of Unicode glyphs (▦ ▤ ◎ ▲) and one emoji (🔒). Glyphs were the wrong tool
 * twice over: they render from whatever font happens to resolve, so weight and alignment drifted
 * between platforms, and the emoji ignored `color` entirely - it stayed full-colour while the rest
 * of the row turned teal on selection.
 *
 * Every icon is decorative here: each sits beside a visible text label, so they are marked
 * aria-hidden and the label does the announcing. An icon used *without* a label needs an
 * aria-label on the control that holds it.
 */
function Icon({ children, size = 18, className = "" }) {
  return (
    <svg
      viewBox="0 0 24 24"
      width={size}
      height={size}
      fill="none"
      stroke="currentColor"
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
      focusable="false"
    >
      {children}
    </svg>
  );
}

/** Dashboard — a home, matching the same route's icon in the mobile bottom nav. */
export function DashboardIcon(props) {
  return (
    <Icon {...props}>
      <path d="M3 11.5 12 4l9 7.5" />
      <path d="M5.5 10v9a1 1 0 0 0 1 1H10v-6h4v6h3.5a1 1 0 0 0 1-1v-9" />
    </Icon>
  );
}

/** Holdings — columns, for a list of positions with sizes. */
export function HoldingsIcon(props) {
  return (
    <Icon {...props}>
      <path d="M4 19V10M10 19V5M16 19v-7" />
      <path d="M20 19H4" />
    </Icon>
  );
}

/** Recommendations — a lamp, the usual shorthand for a suggestion. */
export function RecommendationsIcon(props) {
  return (
    <Icon {...props}>
      <path d="M12 3a6 6 0 0 0-3.5 10.9V16a1 1 0 0 0 1 1h5a1 1 0 0 0 1-1v-2.1A6 6 0 0 0 12 3Z" />
      <path d="M10 20h4" />
    </Icon>
  );
}

/**
 * Risk — a dial with a needle.
 *
 * Chosen over the obvious warning triangle because risk here is a *measurement*, not an error:
 * the page's centrepiece is literally a RiskScoreGauge. A triangle would say "something is wrong"
 * about a screen that is usually reporting that nothing is.
 */
export function RiskIcon(props) {
  return (
    <Icon {...props}>
      <path d="M4 17.5a8 8 0 1 1 16 0" />
      <path d="M12 17.5 16 13" />
      <circle cx="12" cy="17.5" r="1.15" fill="currentColor" stroke="none" />
    </Icon>
  );
}

/** Biometrics — a fingerprint, replacing a padlock emoji that meant "locked", not "your finger". */
export function FingerprintIcon(props) {
  return (
    <Icon {...props}>
      <path d="M12 4.5A7.5 7.5 0 0 0 4.5 12v1.5" />
      <path d="M19.5 12A7.5 7.5 0 0 0 15.2 5.2" />
      <path d="M8.5 12a3.5 3.5 0 0 1 7 0v3.2" />
      <path d="M12 12v5.6" />
      <path d="M6.9 17A9 9 0 0 0 7.6 13.4" />
      <path d="M15.2 19.2c.4-1 .6-2 .6-3" />
    </Icon>
  );
}

/** Log out — leaving through a door. */
export function LogOutIcon(props) {
  return (
    <Icon {...props}>
      <path d="M11 4H6.5A1.5 1.5 0 0 0 5 5.5v13A1.5 1.5 0 0 0 6.5 20H11" />
      <path d="M12.5 12H19" />
      <path d="M16 9l3 3-3 3" />
    </Icon>
  );
}

/** Collapse / expand the sidebar. */
export function ChevronLeftIcon(props) {
  return (
    <Icon {...props}>
      <path d="M14.5 7 9.5 12l5 5" />
    </Icon>
  );
}

export function ChevronRightIcon(props) {
  return (
    <Icon {...props}>
      <path d="M9.5 7l5 5-5 5" />
    </Icon>
  );
}

/** Close a panel or dialog. */
export function CloseIcon(props) {
  return (
    <Icon {...props}>
      <path d="M6.5 6.5l11 11M17.5 6.5l-11 11" />
    </Icon>
  );
}

/** Open the mobile navigation. */
export function MenuIcon(props) {
  return (
    <Icon {...props}>
      <path d="M4 7h16M4 12h16M4 17h16" />
    </Icon>
  );
}

/** Account — a person, matching the mobile bottom nav. */
export function AccountIcon(props) {
  return (
    <Icon {...props}>
      <circle cx="12" cy="8" r="3.5" />
      <path d="M5 20c1.3-3.4 4-5 7-5s5.7 1.6 7 5" />
    </Icon>
  );
}

export default Icon;
