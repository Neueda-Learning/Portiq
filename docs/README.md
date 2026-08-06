# Documentation

Everything written about Portiq lives here. `README.md` stays at the repository root because
GitHub renders it as the project's front page.

## Start here

| Document | Read it when |
|---|---|
| [RUNNING_THE_APP.md](RUNNING_THE_APP.md) | You want it running — locally or on a VM — or you are about to demo it |
| [DEPLOYMENT.md](DEPLOYMENT.md) | You are deploying for real, with Jenkins and EC2 |
| [TESTING.md](TESTING.md) | You are writing a test, or wondering what is covered |
| [BRANCHING_STRATEGY.md](BRANCHING_STRATEGY.md) | You are about to open a branch or a pull request |

## Security

| Document | Contents |
|---|---|
| [SECURITY.md](SECURITY.md) | Control design, configuration reference, and known limitations |
| [OWASP_TOP_10_COMPLIANCE.md](OWASP_TOP_10_COMPLIANCE.md) | Each Top 10 category, the code enforcing it, and the tests proving it |
| [LOGGING.md](LOGGING.md) | Log locations, retention, and how to read the security audit trail |

## Product and design

| Document | Contents |
|---|---|
| [user_stories.md](user_stories.md) | Epics and user stories |
| [user_stories_alternate.md](user_stories_alternate.md) | A second revision — see the note below |
| [risk_and_recommnedation.md](risk_and_recommnedation.md) | How the risk and recommendation engines are designed |
| [api_docs.md](api_docs.md) | Endpoint reference |
| [API_DOCUMENTATION_REQUIREMENTS.md](API_DOCUMENTATION_REQUIREMENTS.md) | What the API documentation has to cover |
| [generative-ai/](generative-ai/) | Twelve notes on the AI features: architecture, prompts, failure handling, model configuration |

## Delivery

[delivery-board.html](delivery-board.html) — an interactive board covering every capability in the
system: 216 cards across 18 areas, each naming the endpoint, class or file behind it. Open the file
in a browser; there is no build step and no server.

---

> **Two versions of the user stories exist.** `user_stories.md` and `user_stories_alternate.md`
> disagree on whether secure login is mandatory or optional — the alternate says the product may
> run without it in trusted or demo environments. Both were in the repository already; they are
> kept side by side rather than one being chosen silently. Decide which is current and delete the
> other.
