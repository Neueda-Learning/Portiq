import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import NewsList from "./NewsList";

/**
 * Headline links come from third-party RSS feeds. React escapes text but not URLs, so this is the
 * last place a hostile href can be stopped before the browser sees it.
 */
describe("NewsList", () => {
  it("links headlines that carry an ordinary http(s) URL", () => {
    render(
      <NewsList
        articles={[
          { title: "Markets rally", link: "https://finance.yahoo.com/story", source: "Yahoo" },
        ]}
      />
    );

    const link = screen.getByRole("link", { name: "Markets rally" });
    expect(link).toHaveAttribute("href", "https://finance.yahoo.com/story");
    expect(link).toHaveAttribute("rel", expect.stringContaining("noopener"));
  });

  it("renders a javascript: headline as plain text with no href", () => {
    render(
      <NewsList
        articles={[
          { title: "Click me", link: "javascript:alert(document.cookie)", source: "Feed" },
        ]}
      />
    );

    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(screen.getByText("Click me")).toBeInTheDocument();
  });

  it("refuses data: URLs too", () => {
    render(
      <NewsList
        articles={[
          { title: "Story", link: "data:text/html;base64,PHNjcmlwdD4x", source: "Feed" },
        ]}
      />
    );

    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  it("survives a missing or non-string link", () => {
    render(
      <NewsList
        articles={[
          { title: "No link", link: null, source: "Feed" },
          { title: "Odd link", link: 42, source: "Feed" },
        ]}
      />
    );

    expect(screen.getByText("No link")).toBeInTheDocument();
    expect(screen.getByText("Odd link")).toBeInTheDocument();
  });

  it("shows an empty state rather than a bare list", () => {
    render(<NewsList articles={[]} />);

    expect(screen.getByText(/no news available/i)).toBeInTheDocument();
  });

  it("shows the related ticker badge when one is present", () => {
    render(
      <NewsList
        articles={[
          { title: "TCS results", link: "https://x.com/a", source: "Yahoo", relatedTicker: "TCS.NS" },
        ]}
      />
    );

    expect(screen.getByText("TCS.NS")).toBeInTheDocument();
  });
});
