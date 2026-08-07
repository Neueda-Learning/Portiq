import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import Modal from "./Modal";

/**
 * These exist because a browser caught something the suite did not: Modal renders an icon, and
 * nothing rendered Modal, so a missing import would have shipped as a runtime ReferenceError on
 * the first click. Rendering each component that uses an icon at least once is what closes that.
 */
describe("Modal", () => {
  it("renders its title and content when open", () => {
    render(
      <Modal isOpen title="Add Holding" onClose={() => {}}>
        <p>Form goes here</p>
      </Modal>
    );

    expect(screen.getByText("Add Holding")).toBeInTheDocument();
    expect(screen.getByText("Form goes here")).toBeInTheDocument();
  });

  it("is hidden when closed", () => {
    // The modal keeps its markup and hides via `display: none` on the wrapper, which is the right
    // way round for accessibility - display:none removes it from the tab order and the
    // accessibility tree. The class is what the test asserts rather than visibility, because the
    // stylesheet is not loaded in jsdom so computed styles would prove nothing here.
    const { container } = render(
      <Modal isOpen={false} title="Add Holding" onClose={() => {}}>
        <p>Form goes here</p>
      </Modal>
    );

    expect(container.querySelector(".modal")).not.toHaveClass("show");
  });

  it("is shown when open", () => {
    const { container } = render(
      <Modal isOpen title="Add Holding" onClose={() => {}}>
        <p>Form goes here</p>
      </Modal>
    );

    expect(container.querySelector(".modal")).toHaveClass("show");
  });

  it("renders the close control with an icon and an accessible name", () => {
    const { container } = render(
      <Modal isOpen title="Add Holding" onClose={() => {}}>
        <p>x</p>
      </Modal>
    );

    const close = screen.getByRole("button", { name: /close dialog/i });
    expect(close).toBeInTheDocument();
    // The icon is decorative; the button's aria-label carries the meaning.
    expect(container.querySelector(".modal-close svg")).toBeInTheDocument();
  });

  it("closes on the close control", async () => {
    const onClose = vi.fn();
    render(
      <Modal isOpen title="Add Holding" onClose={onClose}>
        <p>x</p>
      </Modal>
    );

    await userEvent.click(screen.getByRole("button", { name: /close dialog/i }));
    expect(onClose).toHaveBeenCalled();
  });

  it("closes on Escape", async () => {
    const onClose = vi.fn();
    render(
      <Modal isOpen title="Add Holding" onClose={onClose}>
        <p>x</p>
      </Modal>
    );

    await userEvent.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalled();
  });
});
