import { NavLink } from "react-router-dom";
import {
  AccountIcon,
  DashboardIcon,
  HoldingsIcon,
  RecommendationsIcon,
} from "../common/icons";

/**
 * Icons come from the shared set so a route looks the same on both surfaces - Holdings should not
 * be one glyph on a phone and another on a desktop. Sized a little larger than the sidebar because
 * they sit above their labels rather than beside them, and carry more of the tap target.
 */
const TABS = [
  { to: "/", end: true, label: "Dashboard", Icon: DashboardIcon },
  { to: "/holdings", end: false, label: "Holdings", Icon: HoldingsIcon },
  { to: "/insights", end: false, label: "Insights", Icon: RecommendationsIcon },
  { to: "/account", end: false, label: "Account", Icon: AccountIcon },
];

function BottomNav() {
  return (
    <nav className="bottom-nav">
      {TABS.map(({ to, end, label, Icon }) => (
        <NavLink
          key={to}
          to={to}
          end={end}
          className={({ isActive }) => `bottom-nav-item ${isActive ? "active" : ""}`}
        >
          <span className="bottom-nav-icon">
            <Icon size={21} />
          </span>
          <span className="bottom-nav-label">{label}</span>
        </NavLink>
      ))}
    </nav>
  );
}

export default BottomNav;
