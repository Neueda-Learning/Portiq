# Generative AI in Portiq

Project: Portiq
Doc type: Architecture & feature notes
Date: 2026-08-06
Scope: everything AI-related, backend and frontend

## What this covers

These are notes on how Portiq uses Generative AI. There are four places in the app where a model gets involved: writing a plain-English summary of your portfolio, making sense of messy CSV/Excel files from your broker, reading holdings straight out of a photo of a statement, and rewriting the reasoning behind buy/hold/sell calls so it reads naturally.

None of these are required for Portiq to actually work. Portfolio tracking, performance numbers, risk scoring, and the buy/hold/sell recommendations are all plain deterministic code, no model involved anywhere. AI sits on top to make things more readable and save you from typing data in by hand. If you never configure an AI key, the app runs fine, just with plainer output in those four spots.

All four features go through one shared piece of code, `ChatCompletionClient`, which talks to whatever OpenAI-compatible endpoint you point it at. Swapping providers or models is just a matter of changing two environment variables, no code changes needed.

The rest of the folder breaks this down file by file:

- [01-architecture.md](./01-architecture.md) — the shared AI client and how it's wired up
- [02-feature-insights-summary.md](./02-feature-insights-summary.md)
- [03-feature-smart-file-import.md](./03-feature-smart-file-import.md)
- [04-feature-statement-scanning.md](./04-feature-statement-scanning.md)
- [05-feature-recommendation-narration.md](./05-feature-recommendation-narration.md)
- [06-design-philosophy.md](./06-design-philosophy.md)
- [07-model-configuration.md](./07-model-configuration.md)
- [08-prompt-engineering.md](./08-prompt-engineering.md)
- [09-failure-handling.md](./09-failure-handling.md)
- [10-availability-matrix.md](./10-availability-matrix.md)
- [11-summary.md](./11-summary.md)
