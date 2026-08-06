# OWASP Top 10 — Compliance Report

**Application:** Portiq — Portfolio Management System
**Standard:** OWASP Top 10 (2021)
**Status:** All ten categories addressed
**Verification:** 240 backend tests, 25 frontend tests — all passing

---

## Summary

Portiq implements controls against all ten OWASP Top 10 categories. Every control is enforced in
code, configured with a secure default, and covered by an automated test.

| # | Category | Status | Key controls |
|---|---|---|---|
| A01 | Broken Access Control | ✅ Covered | Deny-by-default authorisation, explicit CORS allowlist, JSON 401 handling |
| A02 | Cryptographic Failures | ✅ Covered | AES-GCM field encryption, BCrypt cost 12, TLS to database, HSTS |
| A03 | Injection | ✅ Covered | Parameterised JPA, CSV formula neutralisation, URL scheme allowlist, XXE-hardened parsing |
| A04 | Insecure Design | ✅ Covered | Rate limiting, account lockout, bounded inputs and batch sizes |
| A05 | Security Misconfiguration | ✅ Covered | Full security header set, startup configuration validation, suppressed error detail |
| A06 | Vulnerable Components | ✅ Covered | OWASP Dependency-Check in the build, failing on CVSS ≥ 7 |
| A07 | Authentication Failures | ✅ Covered | Lockout, enumeration-resistant login, server-side token revocation |
| A08 | Data Integrity Failures | ✅ Covered | Magic-byte upload validation, constraint checks on all import paths |
| A09 | Logging and Monitoring | ✅ Covered | Dedicated security audit log, 365-day retention, injection-safe values |
| A10 | Server-Side Request Forgery | ✅ Covered | Host allowlist, internal-address blocking, redirect suppression, timeouts |

---

## A01 — Broken Access Control

**Implemented controls**

- **Deny by default.** `anyRequest().authenticated()` means any endpoint added in future is
  protected automatically, without anyone having to remember to protect it.
- **Minimal public surface.** Only `/api/auth/login`, the WebAuthn login endpoints, and the
  health/info actuator endpoints are reachable without a session.
- **Explicit CORS allowlist.** Origins are configured per deployment and validated at startup; a
  wildcard is rejected outright, since these responses carry portfolio data.
- **Correct authentication signalling.** Unauthenticated requests receive a JSON `401`, which the
  frontend uses to clear a stale token and return the user to the login screen.
- **Environment-gated developer tooling.** The H2 console route requires both its property and a
  non-production profile before it is registered at all; Swagger requires a session in production.

**Evidence:** `SecurityConfig.java`, `JsonAuthenticationEntryPoint.java` ·
Tests: `SecurityHardeningIntegrationTest`

---

## A02 — Cryptographic Failures

**Implemented controls**

- **Field-level encryption at rest.** Tickers, names, quantities, prices and purchase dates are
  encrypted with AES-256-GCM. A fresh random IV per value means identical plaintexts never produce
  identical ciphertext, so the stored data reveals nothing through pattern matching.
- **Authenticated encryption.** GCM provides integrity as well as confidentiality: tampering with a
  stored value is detected on read rather than silently accepted.
- **Strong password hashing.** BCrypt at cost factor 12 — four times the work per guess compared to
  the library default, while still only milliseconds on a genuine login.
- **Encrypted transport to the database.** TLS is the default for both MySQL and PostgreSQL
  profiles, protecting credentials and decrypted rows on the wire.
- **HSTS.** One year, including subdomains, instructing browsers to refuse plaintext connections.
- **Enforced key management.** Production will not start without a real JWT signing secret and a
  well-formed AES key.

**Evidence:** `EncryptedStringConverter.java`, `EncryptionKeyHolder.java`, `SecurityConfig.java`,
`application-prod.properties` · Tests: `StartupSecurityValidatorTest`

---

## A03 — Injection

**Implemented controls**

- **Parameterised data access throughout.** All persistence goes through Spring Data JPA. There is
  no string-concatenated SQL and no native query anywhere in the codebase.
- **Strict input patterns.** Ticker symbols are validated against a strict character set before
  storage, and lengths are bounded on every text field.
- **CSV formula injection neutralised.** Text cells in exports that begin with a spreadsheet
  formula trigger (`=`, `+`, `-`, `@`, tab, carriage return) are prefixed so the spreadsheet treats
  them as text. Numeric columns are left untouched so exported figures remain calculable.
- **URL scheme allowlisting.** Links from third-party news feeds are restricted to `http` and
  `https` on the server, and again in the React component before they reach an `href`.
- **XXE-hardened XML parsing.** Feed parsing disables DOCTYPE declarations, external general and
  parameter entities, external DTDs and XInclude, and enables secure processing to cap entity
  expansion.
- **Log injection prevented.** Every caller-supplied value is stripped of control characters before
  it is written to a log.

**Evidence:** `HoldingRequest.java`, `ExportService.java`, `RssFeedFetcher.java`,
`SecurityAuditLogger.java` · Tests: `ExportServiceTest`, `RssFeedFetcherLinkTest`,
`HoldingRequestValidationTest`, `NewsList.test.jsx`

---

## A04 — Insecure Design

**Implemented controls**

- **Cost-aware rate limiting.** Requests are throttled per caller in buckets sized to what each
  endpoint costs: the tightest budget for login, then the AI-backed import and insights endpoints
  (each spends an upstream model call), then analytics, writes and ordinary reads.
- **Account lockout as a second, independent layer.** Failures are counted per account as well as
  per address, so a slow distributed attempt that stays under every volume limit is still caught.
- **Time-boxed lockout.** The lock expires automatically, so the mechanism cannot be turned into a
  denial of service against a legitimate account holder.
- **Trustworthy client identification.** `X-Forwarded-For` is honoured only where a proxy is
  declared, and the nearest hop is used, so a caller cannot forge a new identity per request.
- **Bounded resource consumption.** Import row counts, upload sizes, bulk-delete list lengths,
  numeric magnitudes, request header sizes and form post sizes all have explicit ceilings.

**Evidence:** `RateLimitFilter.java`, `RateLimiter.java`, `LoginAttemptService.java`,
`ClientIpResolver.java` · Tests: `RateLimiterTest`, `RateLimitFilterTest`,
`LoginAttemptServiceTest`, `RateLimitChainIntegrationTest`, `ClientIpResolverTest`

---

## A05 — Security Misconfiguration

**Implemented controls**

- **Complete security header set** on every response, including error responses:
  - `Content-Security-Policy` — `default-src 'none'` for API responses, with a separate policy
    scoped to the documentation UI
  - `X-Frame-Options: DENY` and `frame-ancestors 'none'`
  - `X-Content-Type-Options: nosniff`
  - `Referrer-Policy: no-referrer`
  - `Permissions-Policy` denying camera, microphone, geolocation, payment and USB
  - `Strict-Transport-Security`
  - `Cache-Control: no-store`
- **Startup configuration validation.** Production refuses to start on any development default and
  reports every problem at once, so misconfiguration is caught by the person deploying rather than
  discovered later.
- **No information disclosure in errors.** Stack traces, exception messages and binding details are
  suppressed; unexpected failures return a generic message with a reference code that ties the
  response to the full trace in the log.
- **Server fingerprint removed.** No `Server` header is sent.
- **Explicit framework settings.** `spring.jpa.open-in-view` is set deliberately rather than left
  to an implicit default.

**Evidence:** `SecurityConfig.java`, `StartupSecurityValidator.java`, `GlobalExceptionHandler.java`,
`application.properties` · Tests: `SecurityHardeningIntegrationTest`, `StartupSecurityValidatorTest`

---

## A06 — Vulnerable and Outdated Components

**Implemented controls**

- **Automated CVE scanning** via OWASP Dependency-Check, integrated into the Maven build:

  ```bash
  cd backend && mvn -Psecurity verify
  ```

- **Build-failing threshold.** Any dependency with a CVSS score of 7.0 or higher fails the build.
- **Maintained framework baseline.** Spring Boot 3.3.x with managed dependency versions, so
  security patches arrive through a single coordinated upgrade.
- **Reports for review.** HTML and JSON output is produced for triage and record-keeping.

**Evidence:** `backend/pom.xml` (`security` profile)

---

## A07 — Identification and Authentication Failures

**Implemented controls**

- **Brute-force resistance.** Login is rate limited per address and locked out per account after
  repeated failures.
- **Username enumeration prevented.** A wrong password and an unknown username return an identical
  message *and* take an identical amount of time — an unknown username is verified against a dummy
  hash so both paths perform the same cryptographic work.
- **Verified token claims.** Session tokens carry an issuer and a unique identifier, and signature,
  issuer and expiry are all checked on every request.
- **Genuine logout.** `POST /api/auth/logout` revokes the token server-side, so a captured token
  stops working immediately rather than remaining valid until it expires.
- **Phishing-resistant second factor.** WebAuthn biometric login, with origin binding, challenge
  verification and replay detection via the authenticator sign counter.
- **Minimal failure detail.** Biometric verification failures return a generic message; the specific
  check that failed is recorded only in the security log.

**Evidence:** `AuthController.java`, `UserService.java`, `JwtService.java`, `TokenDenylist.java`,
`WebAuthnService.java` · Tests: `JwtServiceTest`, `TokenDenylistTest`,
`SecurityHardeningIntegrationTest`, `WebAuthnServiceTest`

---

## A08 — Software and Data Integrity Failures

**Implemented controls**

- **Three-layer upload validation.** Every uploaded file is checked on its extension, its declared
  content type, and its actual leading bytes. The magic-byte check is what prevents a renamed file
  reaching a document parser under a trusted-looking name.
- **Format-specific signatures.** ZIP for `.xlsx`, OLE2 for `.xls`, and PNG/JPEG/GIF/RIFF for
  images; plain text is verified to contain no NUL bytes.
- **Uniform validation across all entry points.** Holdings imported from a CSV or extracted from an
  uploaded statement pass through the same bean constraints as one typed into the form, so no input
  path bypasses validation.
- **Filename sanitisation.** Client-supplied filenames are reduced to their final path segment and
  stripped of control characters.
- **Size and count ceilings** on uploads and import batches.

**Evidence:** `UploadValidator.java`, `HoldingImportService.java`, `HoldingsController.java` ·
Tests: `UploadValidatorTest`

---

## A09 — Security Logging and Monitoring Failures

**Implemented controls**

- **Dedicated audit trail.** All security events are written through a single logger to
  `logs/portiq-security.log`, separate from application output so it can be shipped or alerted on
  independently.
- **Comprehensive event coverage:** login success and failure, account lockout, logout, rate-limit
  refusal, invalid and revoked tokens, authentication required, access denied, rejected uploads,
  blocked outbound requests, and WebAuthn registration events.
- **Structured, machine-readable format** — `event=NAME key=value` — parsable by standard log
  tooling.
- **Extended retention.** The security log is kept for 365 days, well beyond the application log,
  because incidents are usually investigated long after they begin.
- **Guaranteed delivery.** The security appender writes synchronously rather than through a
  buffered queue, so events are not dropped under the load conditions where they matter most.
- **No secrets recorded.** Passwords, tokens, keys and cookies are never logged — only the fact
  that a credential was presented and whether it was accepted.
- **Privacy option.** Client IP logging can be disabled where data-protection rules require it.
- **Durable build and run records.** Build and run output is captured to `logs/build/` and
  `logs/run/` with commit, branch and exit-code metadata.

**Evidence:** `SecurityAuditLogger.java`, `logback-spring.xml`, `scripts/run-with-logs.sh` ·
Documentation: `logs/README.md`

---

## A10 — Server-Side Request Forgery

**Implemented controls**

- **Outbound host allowlist.** The server may only make requests to explicitly named hosts, matched
  on exact name or dot-suffix so a lookalike domain cannot pass.
- **Internal address blocking.** Loopback, private (RFC 1918), link-local, carrier-grade NAT and
  "this host" ranges are refused — including `169.254.169.254`, the cloud metadata endpoint that
  returns instance credentials.
- **Scheme restriction.** Only `http` and `https` are permitted; `file`, `gopher` and `jar` are
  refused.
- **Input pinned to its place in the URL.** Ticker symbols are URL-encoded into their path segment,
  so no value can rewrite the query string or traverse to a different API path.
- **Redirects not followed.** A redirect response from an allowed host cannot be used to reach an
  unapproved destination.
- **Timeouts on every outbound call.** Connect and read timeouts prevent a slow upstream from
  holding request threads.
- **Every blocked attempt logged** as an `OUTBOUND_REQUEST_BLOCKED` security event.

**Evidence:** `OutboundUrlGuard.java`, `HttpClientConfig.java`, `MarketDataFetcher.java`,
`PriceLookupService.java`, `PriceSeriesFetcher.java` · Tests: `OutboundUrlGuardTest`

---

## Verification

```bash
# Backend — 240 tests, 119 covering security controls
cd backend && mvn test

# Frontend — 25 tests
cd frontend && npm test

# Dependency CVE scan
cd backend && mvn -Psecurity verify

# Everything, with output captured to logs/build/
scripts/run-with-logs.sh all
```

**Security-focused test suites**

| Suite | Covers |
|---|---|
| `SecurityHardeningIntegrationTest` | Headers, 401 handling, logout revocation, enumeration resistance |
| `RateLimitChainIntegrationTest` | Rate limiting active in the real filter chain |
| `OutboundUrlGuardTest` | SSRF allowlist and internal-address blocking |
| `UploadValidatorTest` | Magic-byte, extension and content-type validation |
| `HoldingRequestValidationTest` | Input constraints against injection payloads |
| `ExportServiceTest` | CSV formula injection neutralisation |
| `RssFeedFetcherLinkTest` | URL scheme allowlisting |
| `JwtServiceTest` / `TokenDenylistTest` | Token verification and revocation |
| `LoginAttemptServiceTest` / `RateLimiterTest` | Lockout and throttling |
| `StartupSecurityValidatorTest` | Production configuration enforcement |

---

## Related documentation

- `SECURITY.md` — control design, configuration reference and operational guidance
- `logs/README.md` — log locations, retention and how to read the audit trail
- `DEPLOYMENT.md` — deployment procedure and required environment variables
