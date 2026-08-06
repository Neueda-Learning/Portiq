# Model Configuration & Provider Flexibility

Portiq doesn't lock anyone into a single AI vendor. `ChatCompletionClient` has no vendor SDK baked in, it just speaks the OpenAI chat completions format, which most inference providers support at this point anyway.

## Providers we've tried

Groq is the default (`llama-3.3-70b-versatile` for text, `qwen/qwen3.6-27b` for vision). OpenAI works too, with `gpt-4o`, `gpt-4-turbo`, or `gpt-4o-mini`. Mistral AI works with `mistral-large` or `mistral-small`, Together AI with their open-model catalogue, and Ollama if you want to run something fully on-prem. Really, any OpenAI-compatible endpoint works, just point `INSIGHTS_API_URL` at it.

## Config

```properties
# No code changes required to change the provider
INSIGHTS_API_URL=https://api.groq.com/openai/v1/chat/completions
INSIGHTS_API_KEY=<your-api-key>
INSIGHTS_MODEL=llama-3.3-70b-versatile
INSIGHTS_VISION_MODEL=qwen/qwen3.6-27b
```

## Off by default

All four AI features stay off until both `INSIGHTS_API_KEY` and `INSIGHTS_API_URL` are set. A base deployment of Portiq has zero external AI dependency out of the box, and AI gets turned on by adding config, not by ripping anything out.
