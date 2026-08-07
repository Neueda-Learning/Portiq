import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import * as icons from "./icons";

const NAMED = Object.entries(icons).filter(([name]) => name.endsWith("Icon"));

describe("icon set", () => {
  it("exports every icon the navigation uses", () => {
    const names = NAMED.map(([name]) => name);
    expect(names).toEqual(
      expect.arrayContaining([
        "DashboardIcon",
        "HoldingsIcon",
        "RecommendationsIcon",
        "RiskIcon",
        "FingerprintIcon",
        "LogOutIcon",
        "ChevronLeftIcon",
        "ChevronRightIcon",
        "CloseIcon",
        "MenuIcon",
        "AccountIcon",
      ])
    );
  });

  it.each(NAMED)("%s inherits colour and is hidden from assistive tech", (_name, IconComponent) => {
    // currentColor is what makes an icon follow its nav link's active state without any
    // icon-specific CSS - the previous emoji ignored colour entirely and stayed full-colour on a
    // selected row. aria-hidden because every icon here sits beside a visible text label.
    const { container } = render(<IconComponent />);
    const svg = container.querySelector("svg");

    expect(svg).toHaveAttribute("stroke", "currentColor");
    expect(svg).toHaveAttribute("aria-hidden", "true");
    expect(svg).toHaveAttribute("focusable", "false");
  });

  it.each(NAMED)("%s is drawn on the shared 24x24 grid", (_name, IconComponent) => {
    // One grid and one stroke weight is what makes a set look like a set rather than a collection.
    const { container } = render(<IconComponent />);
    const svg = container.querySelector("svg");

    expect(svg).toHaveAttribute("viewBox", "0 0 24 24");
    expect(svg).toHaveAttribute("stroke-width", "1.75");
    expect(svg).toHaveAttribute("fill", "none");
  });

  it("renders at the requested size", () => {
    const { container } = render(<icons.DashboardIcon size={32} />);
    const svg = container.querySelector("svg");

    expect(svg).toHaveAttribute("width", "32");
    expect(svg).toHaveAttribute("height", "32");
  });

  it("draws something in every icon", () => {
    // Guards against an icon that exports cleanly and renders an empty box.
    NAMED.forEach(([name, IconComponent]) => {
      const { container } = render(<IconComponent />);
      const shapes = container.querySelectorAll("path, circle, rect, line, polyline");
      expect(shapes.length, `${name} has no geometry`).toBeGreaterThan(0);
    });
  });
});
