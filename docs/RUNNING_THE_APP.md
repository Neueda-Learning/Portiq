# Running and Demonstrating Portiq

How to start the application — on your own machine and on a VM — and how to show it working.

`docs/DEPLOYMENT.md` covers the full production pipeline and Jenkins. This document is the
shorter path: get it running, prove it is running, walk someone through it.

---

## Quick reference

| | Local (from source) | VM (Docker) |
|---|---|---|
| Start | `mvn spring-boot:run` + `npm run dev` | `docker compose -f docker-compose.prod.yml up -d` |
| Open at | `http://localhost:5173` | `http://<vm-ip>:8090` |
| Database | H2, in memory | MySQL container, persisted |
| Data on restart | Reseeded | Kept |
| Login | `owner` / `ChangeMe123!` | `owner` / your `OWNER_PASSWORD` |

---

## Part 1 — Running on your own machine

### Prerequisites

- **JDK 17+** — `java -version`
- **Maven 3.9+** — `mvn -version`
- **Node 20+** — `node -v`

No database to install. The default profile uses H2 in memory, and sample holdings are seeded on
first start.

### Start the backend

```bash
cd backend
mvn spring-boot:run
```

Wait for `Started PortiqApplication`. It listens on **4001**.

### Start the frontend

In a second terminal:

```bash
cd frontend
npm install      # first time only
npm run dev
```

Open **http://localhost:5173** and log in with `owner` / `ChangeMe123!`.

> The console will warn that `JWT_SECRET` is not set and that it is signing with a development
> key. That is expected locally. The key is written to `backend/.dev-secrets/` so your session
> survives a restart, and production refuses to start without a real one.

### Turning the AI features on

Three features call a language model: **portfolio summaries**, **Excel / smart file import**, and
**statement screenshot import**. Without a key they report that they are unavailable, and
everything else works normally.

You only need an API key. Get a free one at
[console.groq.com/keys](https://console.groq.com/keys), then:

```bash
# macOS / Linux
cd backend
INSIGHTS_API_KEY=gsk_your_key_here mvn spring-boot:run
```

```powershell
# Windows PowerShell
cd backend
$env:INSIGHTS_API_KEY = "gsk_your_key_here"
mvn spring-boot:run
```

```cmd
:: Windows cmd
cd backend
set INSIGHTS_API_KEY=gsk_your_key_here
mvn spring-boot:run
```

Confirm it took effect — the backend says so in its first few lines of output:

```
AI features enabled - summaries, smart import and statement scanning are available
```

If it instead says:

```
AI features disabled - INSIGHTS_API_KEY not set. ...
```

then the variable did not reach the process. See
[If the AI features say "not configured"](#if-the-ai-features-say-not-configured).

> **`.env` does not work here.** Docker Compose reads `.env`; `mvn spring-boot:run` does not.
> On a local run the variable has to be in the shell that starts Maven.

Other optional settings, all with working defaults:

| Variable | Default | Purpose |
|---|---|---|
| `INSIGHTS_API_KEY` | *(none)* | **Required** to enable the AI features |
| `INSIGHTS_API_URL` | Groq's OpenAI-compatible endpoint | Point at any OpenAI-compatible provider |
| `INSIGHTS_MODEL` | `llama-3.3-70b-versatile` | Text model |
| `INSIGHTS_VISION_MODEL` | `qwen/qwen3.6-27b` | Model used to read statement screenshots |

---

## Part 2 — Running on a VM

This is the setup to use for a demonstration: one command, real database, survives a reboot.

### Prerequisites on the VM

- Docker Engine and the Compose plugin
- Ports **8090** (app) reachable from wherever you are demoing

```bash
# Amazon Linux 2023
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker $USER && newgrp docker

# Ubuntu
sudo apt update && sudo apt install -y docker.io docker-compose-plugin git
sudo systemctl enable --now docker
sudo usermod -aG docker $USER && newgrp docker
```

### Open the port

The application will start happily and be unreachable if this step is skipped — it is the single
most common reason a VM demo fails.

- **AWS EC2** — Security Group → Inbound rules → Add rule → Custom TCP, port `8090`, source
  `My IP` (prefer this over `0.0.0.0/0`)
- **Azure** — Network Security Group → Inbound security rule, port `8090`
- **GCP** — VPC firewall rule allowing `tcp:8090`
- **A VM with its own firewall** — `sudo ufw allow 8090/tcp`, or
  `sudo firewall-cmd --add-port=8090/tcp --permanent && sudo firewall-cmd --reload`

### Get the code and configure it

```bash
git clone https://github.com/Neueda-Learning/114_Portiq.git portiq
cd portiq
cp .env.prod.example .env.prod
```

Generate the two secrets and put them in `.env.prod`:

```bash
openssl rand -base64 48     # -> JWT_SECRET
openssl rand -base64 32     # -> DB_ENCRYPTION_KEY
```

Fill in at minimum:

```bash
DB_PASSWORD=<pick something>
DB_ROOT_PASSWORD=<pick something else>
JWT_SECRET=<the 48-byte value>
DB_ENCRYPTION_KEY=<the 32-byte value>
OWNER_PASSWORD=<at least 12 characters, not the default>
WEBAUTHN_ORIGIN=http://<vm-ip>:8090
INSIGHTS_API_KEY=<optional, for the AI features>
```

> The application **will refuse to start** under the `prod` profile if `JWT_SECRET`,
> `DB_ENCRYPTION_KEY` or `OWNER_PASSWORD` are missing or left at their defaults, and it lists
> every problem at once. That is deliberate: a deployment that boots with development defaults
> looks healthy and is discovered by whoever finds it first.
>
> Keep `DB_ENCRYPTION_KEY` stable once you have real data. Rotating it makes every encrypted
> column unreadable.

### Start it

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

First build takes a few minutes. Then:

```bash
docker compose -f docker-compose.prod.yml ps
```

All services should read `running`, with MySQL `(healthy)`.

### Prove it is up before you show anyone

Two checks, because they fail for different reasons and telling them apart saves a lot of time.

```bash
# 1. Is the web tier up? nginx answers this itself, so it stays up even when the backend is down.
curl -s http://localhost:8090/healthz
# ok

# 2. Is the backend up? This one goes through the proxy to Spring.
curl -s http://localhost:8090/actuator/health
# {"status":"UP"}
```

Then the check that actually matters — the same call **from your own machine**, which is the only
one that proves the port is open:

```bash
curl -s http://<vm-ip>:8090/actuator/health
```

Reading the result:

| `/healthz` | `/actuator/health` | Meaning |
|---|---|---|
| ok | `{"status":"UP"}` | Everything is running |
| ok | fails or hangs | Web tier fine, backend down — check `logs backend` |
| fails from your laptop, works on the VM | — | The application is fine and **the port is closed** |

For the last case, go back to [Open the port](#open-the-port).

Then open **http://\<vm-ip\>:8090** and log in as `owner` with your `OWNER_PASSWORD`.

### Watching it run

```bash
docker compose -f docker-compose.prod.yml logs -f backend     # live application log
docker compose -f docker-compose.prod.yml logs --tail 100     # everything, recent
```

Inside the backend container the structured logs are also on disk:

| File | Contains |
|---|---|
| `logs/portiq.log` | everything |
| `logs/portiq-error.log` | warnings and errors only |
| `logs/portiq-security.log` | logins, lockouts, rate limiting, blocked requests |

The security log is worth having open during a demo — every login shows up in it live.

### Stopping

```bash
docker compose -f docker-compose.prod.yml down          # stop, keep the data
docker compose -f docker-compose.prod.yml down -v       # stop and delete the database
```

---

## Part 3 — Demonstrating it

A route through the application that shows the substance rather than clicking around. Roughly
ten minutes.

### Before you start

- Have `docker compose logs -f backend` open in a second window — showing the security log
  reacting live is more convincing than describing it
- Log out, so you start at the login screen
- If you want to show the AI features, confirm `INSIGHTS_API_KEY` is set **before** the demo,
  not during it

### 1. Sign in (1 min)

Log in as `owner`. Then, in the log window, point out the audit line:

```
event=LOGIN_SUCCESS user=owner ip=...
```

Worth saying: five wrong passwords locks the account for fifteen minutes, and a wrong password
and an unknown username are indistinguishable — same message, same response time.

### 2. Dashboard (2 min)

- **Amount Invested / Gain-Loss / Return** — computed from live prices, falling back to purchase
  price when the market feed is unreachable
- **Value over time** — switch between 1D / 1W / 1M / All
- **Allocation** — the pie chart by holding
- **Market news** — pulled from free RSS feeds, no API key involved

### 3. Holdings (2 min)

- Add a holding and show it merging into an existing position at weighted-average cost rather
  than creating a duplicate row
- Try an invalid ticker like `TCS NS` — validation rejects it, and the reason it matters is that
  the ticker ends up in an outbound URL
- Export to CSV and to PDF

### 4. Risk (2 min)

Open **Risk**. Everything here is computed in Java from a year of daily closes — no model
involved, so it is the same numbers every time:

- Volatility, beta against the NIFTY 50, max drawdown, Sharpe, 95% VaR
- Per-holding risk scores and the portfolio rollup
- Concentration and diversification, with warnings when a position dominates

### 5. Recommendations (2 min)

Open **Recommendations**. The action — ACCUMULATE / HOLD / TRIM / SELL — comes from
deterministic rules with a confidence score and a suggested target weight. Every response
carries an educational disclaimer.

If a key is configured, the *wording* is polished by a model; the numbers and the action never
are.

### 6. AI features (1 min, optional)

- **Insights** — a plain-language summary of the portfolio
- **Import a statement screenshot** — upload a photo of a broker statement and watch it become
  holdings

### 7. Security, if the audience cares (2 min)

- `curl -i http://<vm-ip>:8090/api/holdings` → `401` with a JSON body, no session
  (contrast with `curl -s http://<vm-ip>:8090/actuator/health`, which is deliberately public)
- Response headers: `Content-Security-Policy`, `X-Frame-Options: DENY`, `Strict-Transport-Security`
- Rapid repeated logins → `429` with `Retry-After`, and a `RATE_LIMITED` line in the security log
- Everything sensitive in the database is encrypted with AES-256-GCM per field

### 8. Mobile (1 min)

Open the same URL on a phone, or use browser device emulation — a distinct mobile layout with
bottom navigation, not a squeezed desktop page. It installs as a PWA.

---

## Troubleshooting

### If the AI features say "not configured"

This is the most common local stumble. The message now names the variable it wants:

```
Summaries are not configured on this server. Set INSIGHTS_API_KEY and restart the backend.
```

Work through these in order:

1. **Is the variable set in the shell that started Maven?**
   ```bash
   echo $INSIGHTS_API_KEY          # bash
   echo $env:INSIGHTS_API_KEY      # PowerShell
   ```
   Setting it in a different terminal from the one running the backend is the usual cause.

2. **Did you restart the backend after setting it?** It is read once at startup.

3. **Check the startup line.** The backend states its own status in the first few lines:
   ```
   AI features enabled  - ...
   AI features disabled - INSIGHTS_API_KEY not set. ...
   ```

4. **Using `.env`?** That only works for Docker Compose. `mvn spring-boot:run` does not read it.

5. **On Docker?** The variable has to be in `.env.prod` *and* the stack recreated:
   ```bash
   docker compose --env-file .env.prod -f docker-compose.prod.yml up -d
   ```

6. **Getting a 502 instead?** Then the key *is* configured and the provider rejected the call —
   usually an invalid or expired key, or no remaining quota. The exact reason is in the backend
   log; it is deliberately not returned to the browser, because provider errors can quote your
   endpoint and account details.

> Historical note: before this was fixed, `INSIGHTS_API_URL` had no default outside Docker, so a
> local run reported "not configured" even with a valid key set. Setting the key alone is now
> enough everywhere.

### Port 4001 or 8090 already in use

```bash
# Linux / macOS
lsof -i :4001
```
```powershell
# Windows
Get-NetTCPConnection -LocalPort 4001 -State Listen
```

A previous run left behind is the usual culprit. For Docker, change `APP_PORT` in `.env.prod`.

### The page loads but every request fails

**On the Docker stack, this is not CORS.** nginx serves the SPA and proxies the API on the same
origin, and it strips the `Origin` header before the backend sees it — so the image works on
`localhost`, a LAN address or a public IP with no rebuild and no origin configuration. Check the
backend instead:

```bash
docker compose -f docker-compose.prod.yml logs --tail 50 backend
curl -s http://localhost:8090/actuator/health
```

**CORS only applies when the frontend is served from a different origin** — the Vite dev server on
`:5173` talking to a backend elsewhere, or a separately hosted frontend. In that case set
`CORS_ALLOWED_ORIGINS` to the exact address in the browser's address bar, including the port:

```bash
CORS_ALLOWED_ORIGINS=http://localhost:5173,https://your-frontend.example
```

A wildcard is rejected at startup on purpose — these responses carry portfolio data.

### Biometric login will not enable

WebAuthn requires a secure context. `http://localhost` counts; a plain-HTTP LAN or public address
does not. Over HTTP on a VM the rest of the app works and biometrics will not — that is the
browser's rule, not a bug. For a real demo of it, put the VM behind HTTPS and set
`WEBAUTHN_ORIGIN` to match the address bar exactly.

### The backend refuses to start on the VM

Read the message — it lists every missing setting at once:

```
Refusing to start with an insecure configuration:
  - JWT_SECRET is not set ...
  - OWNER_PASSWORD is still the default value committed to this repository
```

Fix those in `.env.prod` and bring the stack up again.

### Prices show as the purchase price

The market data feed is unreachable, or the ticker is delisted or misspelled. The application
degrades to purchase price on purpose rather than failing the page. The backend log says which
ticker and why.

### Container will not start / database errors

```bash
docker compose -f docker-compose.prod.yml logs backend | tail -50
docker compose -f docker-compose.prod.yml ps
```

If MySQL never reaches `(healthy)`, its volume may be from a run with a different password. For
a demo VM, the quickest fix is to reset it — this deletes the data:

```bash
docker compose -f docker-compose.prod.yml down -v
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

---

## Related

- `docs/DEPLOYMENT.md` — the full pipeline, Jenkins, and EC2 setup
- `docs/TESTING.md` — running the test suites
- `SECURITY.md` — every configuration flag and what it protects
- `logs/README.md` — reading the logs and the audit trail
- `docs/delivery-board.html` — every capability in the system, in one board
