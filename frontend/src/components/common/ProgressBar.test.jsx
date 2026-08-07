import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import ProgressBar from "./ProgressBar";

describe("ProgressBar", () => {
  it("reports the percentage to assistive technology", () => {
    render(<ProgressBar value={42} label="Uploading" />);

    const bar = screen.getByRole("progressbar");
    expect(bar).toHaveAttribute("aria-valuenow", "42");
    expect(bar).toHaveAttribute("aria-valuemin", "0");
    expect(bar).toHaveAttribute("aria-valuemax", "100");
    expect(screen.getByText("42%")).toBeInTheDocument();
  });

  it("omits aria-valuenow when indeterminate", () => {
    // The ARIA signal for "in progress, amount unknown" is the absence of a value. Supplying one
    // would mean announcing a number we invented.
    render(<ProgressBar indeterminate label="Processing" />);

    const bar = screen.getByRole("progressbar");
    expect(bar).not.toHaveAttribute("aria-valuenow");
    expect(bar.className).toContain("is-indeterminate");
  });

  it("shows no percentage while indeterminate", () => {
    render(<ProgressBar value={100} indeterminate label="Processing" />);

    expect(screen.queryByText("100%")).not.toBeInTheDocument();
  });

  it("clamps values outside 0-100", () => {
    // A browser reporting loaded > total is rare but real; a 214%-wide bar is not acceptable.
    const { rerender } = render(<ProgressBar value={150} label="Uploading" />);
    expect(screen.getByRole("progressbar")).toHaveAttribute("aria-valuenow", "100");

    rerender(<ProgressBar value={-20} label="Uploading" />);
    expect(screen.getByRole("progressbar")).toHaveAttribute("aria-valuenow", "0");
  });

  it("falls back to a sensible accessible name", () => {
    render(<ProgressBar value={10} />);
    expect(screen.getByRole("progressbar")).toHaveAccessibleName("Upload progress");
  });

  it("shows the detail line when one is given", () => {
    render(
      <ProgressBar
        indeterminate
        label="Reading your statement"
        detail="This can take up to a minute."
      />
    );

    expect(screen.getByText("This can take up to a minute.")).toBeInTheDocument();
  });
});
