# Feature 1 — Portfolio Insights Summary

## What it does

This writes a short, 3 to 5 sentence plain-English summary of how a portfolio is doing. It mentions the overall gain or loss and calls out the best and worst performing holdings by percentage. No jargon, no markdown, just something you could read out loud.

## How it works

The AI never touches raw numbers directly. `InsightsService` calculates everything first, the normal way: cost basis, current value, gain/loss per holding, percentage changes, using the same deterministic logic the rest of the app relies on. Only once that's done does it hand a summary of those numbers to the model to turn into a sentence or two of prose.

```
Backend flow:
  HoldingService.getAggregatePerformance()
       │
       ▼
  InsightsService.buildPrompt(summary)
       │  Builds a plain text block:
       │  total cost basis, current value, gain/loss,
       │  and per holding: ticker, name, quantity, value, gain/loss %
       ▼
  ChatCompletionClient.complete(model, messages)
       │  System prompt: "plain-spoken portfolio assistant, 3–5 sentences,
       │  no markdown, mention best and worst performers"
       ▼
  Plain-text narrative returned to the frontend
```

## The system prompt

```
You are a plain-spoken portfolio assistant. Summarize the investor's
portfolio performance in 3 to 5 short sentences. No markdown, no bullet
points, no headings. Mention the overall gain or loss, and call out the
best and worst performing holdings by percent.
```

## API

```
GET /api/insights/summary
```

The frontend shows this as a short paragraph in the Insights panel. If no AI model is configured, the endpoint returns a `503` and the panel shows a friendly note instead of breaking.
