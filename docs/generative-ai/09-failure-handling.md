# Failure Handling & Graceful Degradation

Every AI integration in Portiq is built so that an AI failure never takes a user-facing feature down completely.

Portfolio Insights returns a `503 Service Unavailable` with a clear message if the model fails, and the UI shows a friendly notice instead of an error. Smart File Import and Statement Scanning behave the same way: a `503` if AI isn't configured, and an informative error message if the model call itself fails. Recommendation Narration is the most forgiving of the four. It keeps all the original rule-generated text, sets `llmNarrated: false`, and the request still succeeds either way.

Recommendation Narration catches every kind of failure (bad JSON, network timeout, empty response) and just logs a warning instead of letting the error bubble up. The recommendations stay fully usable with their original, rule-generated explanations.

```java
// RecommendationNarrator.java
} catch (JsonProcessingException e) {
    log.warn("Narration skipped - model did not return usable JSON: {}", ...);
    response.setLlmNarrated(false);
} catch (Exception e) {
    log.warn("Narration skipped - service could not be reached: {}", ...);
    response.setLlmNarrated(false);
}
// The response is returned intact — the caller never sees this failure
```
