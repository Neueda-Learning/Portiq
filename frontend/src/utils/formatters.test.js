import { describe, expect, it } from "vitest";
import { formatMoney, formatPercent, formatSignedMoney } from "./formatters";

describe("formatters", () => {
  it("formats money to two decimal places", () => {
    expect(formatMoney(1234.5)).toContain("1,234.50");
    expect(formatMoney(1234.5)).toMatch(/^Rs /);
  });

  it("groups in the Indian numbering system", () => {
    // en-IN groups as 10,00,000 rather than 1,000,000. Getting this wrong is the kind of bug
    // nobody notices until a portfolio crosses a lakh.
    expect(formatMoney(1000000)).toContain("10,00,000.00");
  });

  it("treats a missing value as zero rather than rendering NaN", () => {
    expect(formatMoney(null)).toContain("0.00");
    expect(formatMoney(undefined)).toContain("0.00");
    expect(formatMoney("")).toContain("0.00");
  });

  it("signs percentages so a gain is unambiguous", () => {
    expect(formatPercent(12.5)).toBe("+12.50%");
    expect(formatPercent(-3.456)).toBe("-3.46%");
    expect(formatPercent(0)).toBe("+0.00%");
  });

  it("signs money the same way", () => {
    expect(formatSignedMoney(500)).toMatch(/^\+Rs /);
    expect(formatSignedMoney(-500)).toContain("-");
  });

  it("accepts numeric strings, which is what the API sends for BigDecimal fields", () => {
    expect(formatMoney("2500.75")).toContain("2,500.75");
    expect(formatPercent("7.5")).toBe("+7.50%");
  });
});
