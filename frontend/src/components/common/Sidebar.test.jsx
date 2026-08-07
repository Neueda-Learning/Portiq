import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import Sidebar from "./Sidebar";

vi.mock("../../context/AuthContext", () => ({
  useAuth: () => ({
    username: "owner",
    biometricEnabled: false,
    logout: vi.fn(),
    setBiometricEnabled: vi.fn(),
  }),
}));

vi.mock("../../utils/webauthn", () => ({
  isWebAuthnSupported: () => true,
  registerBiometricCredential: vi.fn(),
}));

function renderSidebar(props = {}) {
  return render(
    <MemoryRouter>
      <Sidebar collapsed={false} onToggleCollapse={vi.fn()} mobileOpen={false} onCloseMobile={vi.fn()} {...props} />
    </MemoryRouter>
  );
}

describe("Sidebar", () => {
  it("links to every section", () => {
    renderSidebar();

    ["Dashboard", "Holdings", "Recommendations", "Risk"].forEach((label) => {
      expect(screen.getByRole("link", { name: new RegExp(label, "i") })).toBeInTheDocument();
    });
  });

  it("draws each nav item with an inline icon rather than a text glyph", () => {
    // The previous icons were Unicode shapes and one emoji, which rendered from whatever font
    // resolved and — in the emoji's case — ignored colour entirely.
    const { container } = renderSidebar();

    const icons = container.querySelectorAll(".nav-icon svg");
    expect(icons).toHaveLength(4);
  });

  it("gives every icon currentColor so it follows the active state", () => {
    const { container } = renderSidebar();

    container.querySelectorAll(".sidebar svg").forEach((svg) => {
      expect(svg).toHaveAttribute("stroke", "currentColor");
      expect(svg).toHaveAttribute("aria-hidden", "true");
    });
  });

  it("names the collapse control for what it will do", () => {
    // Previously a static "Collapse sidebar" even when it would expand.
    const { rerender } = renderSidebar({ collapsed: false });
    expect(screen.getByRole("button", { name: /collapse sidebar/i })).toBeInTheDocument();

    rerender(
      <MemoryRouter>
        <Sidebar collapsed onToggleCollapse={vi.fn()} mobileOpen={false} onCloseMobile={vi.fn()} />
      </MemoryRouter>
    );
    expect(screen.getByRole("button", { name: /expand sidebar/i })).toBeInTheDocument();
  });

  it("keeps text labels on the controls, so icons never carry meaning alone", () => {
    renderSidebar();

    expect(screen.getByText("Enable Biometrics")).toBeInTheDocument();
    expect(screen.getByText("Log out")).toBeInTheDocument();
  });
});
