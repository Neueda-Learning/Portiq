# Feature 4 — Recommendation Narration

## What it does

The recommendations engine gives a BUY, ACCUMULATE, HOLD, TRIM, or SELL call for every stock held, plus new position ideas from a configurable list of stocks not currently owned. All of these calls come from a deterministic quantitative model, no AI in the decision itself. The AI's only job is taking the rule-generated reasoning and rewriting it as plain English an ordinary investor could read and act on.

## Quant model first, AI last

This is probably the most deliberate AI use in the app, because the line between deciding and explaining is enforced strictly.

```
┌─────────────────────────────────────────────────────────────────┐
│              Recommendation Pipeline                            │
│                                                                 │
│  RecommendationService (pure Java, no AI)                      │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  For each holding and universe ticker:                    │  │
│  │  1. Fetch 1 year of daily price history (Yahoo Finance)  │  │
│  │  2. Compute signals:                                      │  │
│  │     - Momentum score (12-month return, capped at ±30)    │  │
│  │     - Trend bonus (price above 200-day MA)                │  │
│  │     - RSI signal (oversold <30 = bonus, overbought >70)  │  │
│  │     - Value signal (52-week low/high position)           │  │
│  │     - Concentration penalty (>25% portfolio weight)      │  │
│  │     - High-risk discount (risk score >70)                │  │
│  │  3. Sum signals → opportunity score (−100 to +100)       │  │
│  │  4. Map score to action band:                            │  │
│  │     ≥ +40 → BUY   │  +15 to +40 → ACCUMULATE           │  │
│  │     −15 to +15 → HOLD │ −40 to −15 → TRIM │ ≤−40 → SELL│  │
│  │  5. Generate rule-based reason text                      │  │
│  └───────────────────────────────────────────────────────────┘  │
│         │                                                       │
│         ▼  (only if AI is configured)                          │
│  RecommendationNarrator                                         │
│  Sends: action + score + reason list per ticker → model        │
│  Receives: rewritten reason text + 3–4 sentence narrative      │
│  Applies: rewrites reason fields in place                      │
│  Fallback: original rule-generated text if model fails         │
│         │                                                       │
│         ▼                                                       │
│  Response carries llmNarrated: true/false                      │
│  UI indicates which text source is displayed                   │
└─────────────────────────────────────────────────────────────────┘
```

## The narration prompt

```
You are a plain-spoken investment analyst. You will be given stock
recommendations that have ALREADY been decided by a quantitative model,
along with the signals behind each one. Do not change any recommendation,
price or number. Rewrite each one's justification as one or two clear
sentences an ordinary investor would understand. No markdown, no bullet
points, no headings. Respond with JSON only, in exactly this shape:
{
  "narrative": "3-4 sentences summarising the overall picture",
  "reasons": {"TICKER": "the rewritten justification"}
}
```

## Why the AI can't change the actual call

The prompt is explicit about not touching the recommendation, the price, or any number, only the wording. The model gets the decision as read-only context and its job stops at language. That keeps the investment decision fully reproducible from the quant signals alone, stops the AI from introducing bias or inventing performance numbers, and means that if anything goes wrong on the AI side (model down, bad JSON, a skipped ticker) the original rule-generated text is still sitting there, still correct.

## Transparency flag

Every response includes an `llmNarrated` boolean, which the frontend uses to show whether the text on screen was written by the AI or came straight from the rule-based logic. Nothing is hidden about where the wording came from.
