# How the AI Layer Is Wired Up

## One client, four features

Insights, smart import, statement scanning, and recommendation narration all talk to the same underlying piece of code: `ChatCompletionClient`.

```
┌─────────────────────────────────────────────────────────────────┐
│                        Portiq Backend                           │
│                                                                 │
│   InsightsService      ─────────────────────────────────┐       │
│   SmartFileImportService ───────────────────────────────┤       │
│   StatementScanService  ────────────────────────────────┼──▶  ChatCompletionClient
│   RecommendationNarrator ───────────────────────────────┘       │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
                                                    │
                                                    ▼
                                    ┌───────────────────────────┐
                                    │  Any OpenAI-compatible    │
                                    │  API endpoint             │
                                    │                           │
                                    │  Groq · OpenAI · Mistral  │
                                    │  Together · Ollama        │
                                    │  (configured via env var) │
                                    └───────────────────────────┘
```

`ChatCompletionClient` isn't fancy on purpose. It's a small HTTP wrapper around a `/chat/completions` endpoint: it puts the API key in the `Authorization: Bearer` header, sends a `messages` array along with `model` and `temperature`, and hands back the text from the first choice in the response. No vendor SDK involved.

Whether the AI subsystem is on or off comes down to one check:

```java
// ChatCompletionClient.java
public boolean isConfigured() {
    return apiKey != null && !apiKey.isBlank()
        && apiUrl != null && !apiUrl.isBlank();
}
```

Every service checks `isConfigured()` before calling the model. If it's not set up, the feature either returns a `503 Service Unavailable` or quietly falls back to its rule-based output. Either way a missing AI config never crashes the app, it just means less polish.

## Two kinds of model

Portiq needs two different models depending on the job: a text model for reasoning and writing, and a vision model for reading images.

| Role | Env variable | Default | Used by |
|---|---|---|---|
| Text / reasoning | `INSIGHTS_MODEL` | `llama-3.3-70b-versatile` | Insights, Smart Import, Recommendation Narration |
| Vision | `INSIGHTS_VISION_MODEL` | `qwen/qwen3.6-27b` | Statement Scanning |

Both are set through environment variables, so swapping either one out is a config change, not a code change.
