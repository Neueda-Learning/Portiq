import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ErrorBoundary from "./ErrorBoundary";

function Explodes() {
  throw new Error("kaboom");
}

describe("ErrorBoundary", () => {
  beforeEach(() => {
    // React logs the caught error itself, which would bury the real test output in red noise.
    vi.spyOn(console, "error").mockImplementation(() => {});
  });

  it("renders its children when nothing throws", () => {
    render(
      <ErrorBoundary>
        <p>All fine</p>
      </ErrorBoundary>
    );

    expect(screen.getByText("All fine")).toBeInTheDocument();
  });

  it("shows a recovery message instead of a blank page when a child throws", () => {
    // Without a boundary React unmounts the whole tree, and on a portfolio screen a white page is
    // indistinguishable from "my data is gone".
    render(
      <ErrorBoundary>
        <Explodes />
      </ErrorBoundary>
    );

    expect(screen.getByRole("alert")).toBeInTheDocument();
    expect(screen.getByText(/something went wrong/i)).toBeInTheDocument();
    expect(screen.getByText(/your holdings are safe/i)).toBeInTheDocument();
  });

  it("offers a reload as the way out", async () => {
    render(
      <ErrorBoundary>
        <Explodes />
      </ErrorBoundary>
    );

    const reload = vi.fn();
    Object.defineProperty(window, "location", {
      configurable: true,
      value: { ...window.location, reload },
    });

    await userEvent.click(screen.getByRole("button", { name: /reload/i }));

    expect(reload).toHaveBeenCalled();
  });

  it("logs the error so the crash leaves a record", () => {
    render(
      <ErrorBoundary>
        <Explodes />
      </ErrorBoundary>
    );

    expect(console.error).toHaveBeenCalled();
  });
});
