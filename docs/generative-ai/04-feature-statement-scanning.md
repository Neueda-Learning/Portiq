# Feature 3 — Brokerage Statement Scanning (Vision)

## What it does

This extends the import feature to images. Got a printed brokerage statement, a screenshot of a portfolio on your phone, or a scanned PDF page? Photograph or upload it and Portiq pulls the holdings out using a vision-capable model.

It mostly matters for older brokers that don't offer a digital export, or when someone just wants a portfolio into the app quickly from a paper statement.

## How it works

```
┌─────────────────────────────────────────────────────────────────┐
│                  Statement Scan Pipeline                        │
│                                                                 │
│  Upload (JPG / PNG / PDF screenshot)                            │
│         │                                                       │
│         ▼                                                       │
│  StatementScanService.extractHoldings()                        │
│  File bytes → Base64 encoded                                    │
│  Wrapped as a data URL: data:<mime>;base64,<content>           │
│         │                                                       │
│         ▼                                                       │
│  ChatCompletionClient  ◀── Vision model (INSIGHTS_VISION_MODEL)│
│  Multi-modal message:                                           │
│    [text instruction] + [image_url (data URL)]                 │
│  Instruction: extract ticker, name, type, quantity,            │
│    purchasePrice, purchaseDate as JSON array                   │
│         │                                                       │
│         ▼                                                       │
│  LlmHoldingsParser.parse()                                      │
│  Extracts JSON array from raw model output                      │
│  Maps each object → HoldingRequest                             │
│         │                                                       │
│         ▼                                                       │
│  HoldingImportService.importRequests()                          │
│  Merge or create holdings in the portfolio                      │
└─────────────────────────────────────────────────────────────────┘
```

## Sending text and an image together

The request to the vision model uses the standard OpenAI multi-modal message shape — a text instruction plus the base64-encoded image, both in the same user message:

```java
List<Map<String, Object>> content = List.of(
    Map.of("type", "text", "text", instructions),
    Map.of("type", "image_url", "image_url",
        Map.of("url", dataUrl))  // data:<contentType>;base64,<bytes>
);
```

## What we ask the model to do

```
Look at this brokerage or portfolio statement image. Return ONLY a JSON
array (no prose, no markdown fences) of objects with keys: ticker, name,
type (STOCK, BOND, or CASH), quantity, purchasePrice, purchaseDate
(YYYY-MM-DD, use today's date if not visible). If a field cannot be read
exactly, make a reasonable estimate rather than skipping the row.
```

## Shared parsing logic

Both this feature and Smart File Import lean on the same `LlmHoldingsParser`. It strips markdown code fences if the model adds them anyway despite being told not to, finds the first `[` and last `]` to pull the JSON array out of any surrounding text, and converts each row into a `HoldingRequest`, handling type coercion and date-parsing fallbacks along the way.

## API

```
POST /api/holdings/import/image
Content-Type: multipart/form-data
```
