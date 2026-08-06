import BottomNav from "./BottomNav";

function MobileShell({ children }) {
  return (
    <div className="mobile-shell">
      <header className="mobile-topbar">
        <span className="brand">Portiq</span>
      </header>
      <main className="mobile-content">{children}</main>
      <BottomNav />
    </div>
  );
}

export default MobileShell;
