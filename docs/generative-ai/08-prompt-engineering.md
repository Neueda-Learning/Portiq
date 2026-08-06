# Prompt Engineering Notes

A few habits show up across all the prompts in Portiq, most of them learned by trial and error rather than decided up front.

## Always specify the exact output shape

Every prompt expecting structured output tells the model exactly what to return and says explicitly not to wrap it in prose or markdown fences:

```
Return ONLY a JSON array (no prose, no markdown fences).
```

Even so, models sometimes add formatting anyway, so `LlmHoldingsParser` and `RecommendationNarrator` both do defensive extraction, stripping code fences and pulling the actual JSON out of whatever surrounds it.

## Low temperature, on purpose

Every request uses `temperature: 0.3`, which keeps output fairly consistent and predictable. For parsing financial data or rewriting a recommendation's reasoning, creative variation isn't what you want. You want the same answer given the same input.

```java
// ChatCompletionClient.java
body.put("temperature", 0.3);
```

## Break the task into small pieces

The Smart File Import prompt is a good example of this: the model is told not to sum, net, or average across rows, just normalise one row at a time. All the cross-row logic (netting, averaging, aggregating) happens afterward in Java. Keeping the model's job narrow keeps it inside the range where it's actually reliable.

## Set the persona before the data

System-role messages establish who the model is supposed to be and what constraints it's working under before any actual financial data shows up in the conversation. Insights, Recommendation Narration, and Statement Scanning all use this to ground the model's behaviour up front.
