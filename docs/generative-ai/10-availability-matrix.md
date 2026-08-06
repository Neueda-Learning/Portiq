# AI Feature Availability Matrix

A quick reference for what needs AI, what doesn't, and what happens when AI isn't configured.

| Feature | Needs an API key? | Needs a vision model? | Falls back gracefully? | Works without AI at all? |
|---|:---:|:---:|:---:|:---:|
| Portfolio performance tracking | No | No | — | Yes |
| Risk analysis (beta, Sharpe, VaR) | No | No | — | Yes |
| Buy/Hold/Sell recommendations | No | No | — | Yes |
| Portfolio Insights Summary | Yes | No | 503 with a message | No |
| Smart File Import (CSV/Excel) | Yes | No | 503 with a message | No |
| Statement Scanning (image) | Yes | Yes | 503 with a message | No |
| Recommendation Narration | Yes | No | Falls back to rule-generated text | Yes |
