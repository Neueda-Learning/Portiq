# Portiq

A personal portfolio tracker: Spring Boot REST API backend + React (Vite) frontend, installable as a PWA.

```text
Frontend (http://localhost:5173, PWA)
    |
    | fetch /api requests, JWT bearer token
    v
Backend (http://localhost:4001)
    |
    +--> H2 (dev, in-memory) / MySQL (prod, via docker-compose) / Supabase Postgres (supabase)
```

## Features

- Single-user login with username/password, plus optional biometric sign-in (WebAuthn - Windows Hello, Touch ID, Android biometrics) once enabled from the top bar.
- Dashboard: stat tiles, portfolio value trend chart (1D/1W/1M/All), allocation pie chart (by value or quantity), investment-vs-current-value chart, live market news, and an on-demand plain-text portfolio summary.
- Holdings report: full table with purchase/current value and P&L, inline edit and delete, add manually, import from CSV (with a downloadable sample template), or import by uploading a statement image. Importing a ticker you already hold merges into the existing row (weighted-average price, summed quantity) instead of duplicating it.
- Stock recommendations: a BUY / ACCUMULATE / HOLD / TRIM / SELL call on every holding, plus BUY ideas from a configurable universe of tickers you do not own. Each carries the current price, a plain-English reason, the signals behind it (momentum, trend against the 50/200-day averages, RSI, position in the 52-week range) and a suggested target weight.
- Risk analysis: a 0-100 risk score per stock built from annualised volatility, beta against the index, maximum drawdown, 95% value at risk and position size, plus a portfolio roll-up with true portfolio volatility, beta, diversification score and concentration warnings.
- Export the holdings report as CSV or PDF.
- Sensitive fields (portfolio names, holdings, quantities, prices, dates) are encrypted at rest with AES-256-GCM before being written to the database.
- Installable as a PWA with offline app-shell caching.

## Folder Structure

```text
portiq/
  docker-compose.yml        # local MySQL
  backend/
    pom.xml
    .env.example
    src/main/java/com/portiq/
      config/                # security, CORS/security filter chain, data seeding
      controller/            # REST APIs
      dto/
      exception/
      model/
      repository/
      security/              # JWT, field encryption, WebAuthn
      service/
    src/main/resources/
      application.properties
      application-prod.properties       # MySQL
      application-supabase.properties   # Supabase Postgres
      sample-holdings.csv
  frontend/
    .env.example
    index.html
    vite.config.js
    src/
      App.jsx, main.jsx, styles.css
      config/api.js
      context/AuthContext.jsx
      services/               # API calls
      components/             # reusable UI, charts, news
      pages/                  # LoginPage, DashboardPage, HoldingsPage
      utils/webauthn.js       # browser WebAuthn helpers
```

## Quick Start (H2, no setup)

```bash
cd backend
mvn spring-boot:run
```

```bash
cd frontend
npm install
npm start
```

Open `http://localhost:5173`. Log in with the seeded account (`owner` / `ChangeMe123!` by default - change these via `OWNER_USERNAME`/`OWNER_PASSWORD` env vars before first run). Sample holdings are seeded automatically on a fresh database.

## Running Against MySQL

```bash
cp .env.example .env      # adjust DB_NAME / DB_USERNAME / DB_PASSWORD if needed
docker compose up -d
```

Then start the backend with the `prod` profile and the same DB credentials exported as environment variables (see `backend/.env.example` for the full list, including `JWT_SECRET` and `DB_ENCRYPTION_KEY`):

```bash
cd backend
$env:DB_HOST="localhost"; $env:DB_NAME="portiq_db"; $env:DB_USERNAME="portiq"; $env:DB_PASSWORD="portiq"
$env:JWT_SECRET="<generate one>"; $env:DB_ENCRYPTION_KEY="<generate one>"
mvn spring-boot:run "-Dspring-boot.run.profiles=prod"
```

Generate secrets with `openssl rand -base64 48` (JWT) and `openssl rand -base64 32` (encryption key). Keep `DB_ENCRYPTION_KEY` stable once you have real data - rotating it makes previously stored data unreadable.

## Portfolio Summary and Statement Image Import (optional)

Both features call an OpenAI-compatible chat completions endpoint you configure yourself. Until configured, the Summary button and image import show a clear "not configured" message instead of failing.

Set these environment variables on the backend (see `backend/.env.example`):

```
INSIGHTS_API_KEY=...
INSIGHTS_API_URL=...        # chat completions endpoint
INSIGHTS_MODEL=...          # text model
INSIGHTS_VISION_MODEL=...   # vision-capable model, for statement image import
```

## Biometric Login

Biometric login uses the browser's built-in WebAuthn API - no third-party service involved. After logging in with a password once, click **Enable Biometrics** in the top bar and follow your OS prompt (Windows Hello, Touch ID, etc). It requires a secure context, so `http://localhost` works but a plain-HTTP LAN address will not; deploy behind HTTPS for anything beyond local use, and update `WEBAUTHN_RP_ID` / `WEBAUTHN_ORIGIN` to match your real domain.

## Backend Tests

```bash
cd backend
mvn test
```

## API Overview

- `POST /api/auth/login`, `GET /api/auth/me`
- `POST /api/auth/webauthn/registration/options`, `/registration/verify`
- `POST /api/auth/webauthn/login/options`, `/login/verify`
- `GET/POST /api/holdings`, `PUT/DELETE /api/holdings/{id}`
- `POST /api/holdings/import/csv`, `POST /api/holdings/import/image`
- `GET /api/holdings/import/csv/sample`
- `GET /api/holdings/export/csv`, `GET /api/holdings/export/pdf`
- `GET /api/holdings/history?range=1d|1w|1m|all`
- `GET /api/news`
- `GET /api/insights/summary`
- `GET /api/recommendations?includeIdeas=true&narrate=true`, `GET /api/recommendations/{ticker}`
- `GET /api/risk`, `GET /api/risk/{ticker}`
- `GET /api/portfolios/**`, `GET /api/portfolios/{id}/holdings/**` (original per-portfolio API, still available)
- `http://localhost:4001/swagger-ui.html`, `http://localhost:4001/h2-console` (dev only)

## Notes

- All endpoints under `/api/**` except `/api/auth/**` require a `Authorization: Bearer <token>` header.
- Market prices and the portfolio value chart use Yahoo Finance's public quote/chart endpoints (no API key). News uses free Yahoo Finance and Google News RSS feeds. Both fail gracefully (falling back to purchase price, or an empty list) if unreachable.
- Recommendations and risk scores also need no API key - they are computed in Java from a year of daily closes. Setting `INSIGHTS_API_KEY` only rewrites the recommendation wording into better prose; the calls themselves are always rule-based, and the endpoints stay on HTTP 200 whether or not a model is configured. Every response carries a `disclaimer` field: this is educational analysis, not investment advice.
- A ticker the price feed cannot resolve (delisted, renamed) is reported with `dataQuality: UNAVAILABLE` and excluded from the portfolio aggregates rather than silently scored as zero.

### Analytics configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `ANALYTICS_BENCHMARK` | `^NSEI` | Index that beta is measured against. Match it to your holdings' market. |
| `ANALYTICS_RISK_FREE_RATE` | `6.5` | Annual risk-free rate, in percent, used by the Sharpe ratio. |
| `RECOMMENDATION_UNIVERSE` | 20 NSE large caps | Comma-separated tickers to draw new ideas from. Held tickers are filtered out automatically. |
| `RECOMMENDATION_MAX_IDEAS` | `5` | How many ideas to return. |
- The backend caches quotes (60s), price history (5 min), and news feeds (10 min) in memory to avoid re-hitting external endpoints on every request. The frontend also caches holdings/history/news responses briefly in memory, cleared automatically whenever holdings are added, edited, deleted, or imported.
- The sidebar collapses/expands on desktop (state persisted locally) and becomes an off-canvas drawer with a hamburger toggle below 900px width.
- This is a single-user app - there is no registration flow, only the one seeded account.
