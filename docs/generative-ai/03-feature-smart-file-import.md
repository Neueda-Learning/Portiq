# Feature 2 — Smart File Import

## What it does

This solves one of the most annoying parts of tracking a portfolio: getting holdings out of whatever export a broker gives you. Every broker's CSV or Excel export looks a bit different. Column names and order vary, some show total trade value instead of a per-share price, some list every buy and sell separately instead of a net position, and some use tickers without the exchange suffix.

Smart File Import handles all of that without making the user map columns to a template.

## How it works

The pipeline is split into two stages, one for the AI and one for plain Java, because each is good at a different part of the job.

```
┌─────────────────────────────────────────────────────────────────┐
│                     Smart Import Pipeline                        │
│                                                                 │
│  Upload (CSV / XLSX / XLS)                                      │
│         │                                                       │
│         ▼                                                       │
│  SpreadsheetTextExtractor                                       │
│  Apache POI (Excel) or plain reader (CSV)                       │
│  Converts to comma-separated plain text                         │
│         │                                                       │
│         ▼                                                       │
│  ChatCompletionClient  ◀── Text model (INSIGHTS_MODEL)         │
│  Prompt: normalise every row 1:1 into:                         │
│    ticker, name, type, side (BUY/SELL), quantity, price, date  │
│  Model returns JSON array — one object per INPUT row           │
│         │                                                       │
│         ▼                                                       │
│  SmartFileImportService.netByTicker()   ← Deterministic Java   │
│  Weighted-average-cost accounting:                              │
│  BUY rows blend into running average cost                       │
│  SELL rows reduce quantity only                                 │
│  Zero or negative net positions are dropped                     │
│         │                                                       │
│         ▼                                                       │
│  HoldingImportService.importRequests()                          │
│  Merge or create holdings in the portfolio                      │
└─────────────────────────────────────────────────────────────────┘
```

## Why the model only handles one row at a time

This came out of hitting a wall during testing. Ask a language model to take a year's worth of trades and net them down to final positions in one pass, and it makes subtle arithmetic mistakes. A 21-row order history came back with wrong totals and a dropped holding when the model tried to do the netting itself.

So the model's job is kept narrow now: normalise each row on its own, with no aggregation or arithmetic across rows.

```java
// The model is explicitly told:
// "Output one JSON object per INPUT row — do not combine, merge,
//  or summarize rows yourself, just normalize each row exactly
//  as it stands on its own."
```

The actual netting, weighted-average cost, combining buys and sells into a final position, happens afterward in plain, testable Java (`SmartFileImportService.netByTicker()`).

## What it can handle

CSV files are read as plain text, and `.xlsx`/`.xls` go through Apache POI's `WorkbookFactory`. Column order doesn't matter since the AI normalises it. If a file lists total trade value instead of per-share price, the AI works out the per-share price for each row. NSE/BSE tickers without an exchange suffix get `.NS` or `.BO` appended automatically. And when a file mixes BUY and SELL rows, Java handles the weighted-average netting on the back end.

## API

```
POST /api/holdings/import/smart
Content-Type: multipart/form-data
```
