function MobileHeader({ onOpenSidebar }) {
  return (
    <header className="mobile-header">
      <button className="hamburger" onClick={onOpenSidebar} aria-label="Open menu">
        &#9776;
      </button>
      <div className="brand">Portiq</div>
    </header>
  );
}

export default MobileHeader;
