import { MenuIcon } from "./icons";

function MobileHeader({ onOpenSidebar }) {
  return (
    <header className="mobile-header">
      <button className="hamburger" onClick={onOpenSidebar} aria-label="Open menu">
        <MenuIcon size={22} />
      </button>
      <div className="brand">Portiq</div>
    </header>
  );
}

export default MobileHeader;
