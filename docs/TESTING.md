# Testing

How Portiq is tested, why the suite is shaped the way it is, and what to do when you add code.

**Current state:** 298 backend tests, 25 frontend tests, all passing.

---

## Running the tests

```bash
cd backend && mvn test          # 298 tests, ~45s
cd frontend && npm test         # 25 tests, ~8s
cd frontend && npm run test:watch   # re-runs on save

# Both, with output captured to logs/build/
scripts/run-with-logs.sh all
```

A single suite or method:

```bash
mvn -Dtest=OutboundUrlGuardTest test
mvn -Dtest=OutboundUrlGuardTest#refusesInternalAddressesEvenIfSomehowAllowlisted test
npx vitest run src/services/apiClient.test.js
```

Reports land in `backend/target/surefire-reports/` and are uploaded as CI artifacts with
`if: always()`, so a failing run's reports survive.

---

## What we test, and why

The suite is not aiming at a coverage percentage. It is aiming at the things that would actually
hurt if they broke, which is a different target and produces a different shape of test.

Three questions decide whether something gets a test:

1. **Would a bug here be silent?** Silent failures earn the most tests. A rate limiter registered
   in the wrong filter position does not throw — every request succeeds, which is exactly what a
   passing suite looks like. That is why `RateLimitChainIntegrationTest` exists at all.
2. **Is this logic or is it wiring?** Logic gets unit tests. Wiring gets one integration test that
   proves it is wired, rather than a mock-heavy test that proves the mock was called.
3. **Does it involve untrusted input?** Anything reading a file, a feed, or a model's output gets
   adversarial cases, not just happy paths.

### Where the tests are concentrated

| Area | Tests | Why here |
|---|---|---|
| Financial maths | 64 | `MetricsCalculator` (37), `RiskAnalysisService` (12), `RecommendationService` (15). Wrong numbers are the one failure a user cannot detect by looking. |
| Security controls | 119 | Every control has a test that fails if it regresses. Enumerated in `OWASP_TOP_10_COMPLIANCE.md`. |
| Untrusted input parsing | 69 | `NumericCellParser` (32), `LlmHoldingsParser` (17), `UploadValidator` (11), `RssFeedFetcherLink` (9). |
| Contracts and errors | 21 | Controllers and `GlobalExceptionHandler` — the shapes clients depend on. |

---

## Test types

### Unit tests — the default

Plain JUnit 5 with Mockito. No Spring context, so they run in milliseconds.

```java
@ExtendWith(MockitoExtension.class)
class HoldingServiceTest {
    @Mock private HoldingRepository holdingRepository;
    @InjectMocks private HoldingService holdingService;
}
```

Controllers are tested with **standalone MockMvc** rather than `@WebMvcTest`. It skips context
loading entirely, which keeps the whole controller layer under a second:

```java
mockMvc = MockMvcBuilders.standaloneSetup(holdingController)
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
```

### Integration tests — where wiring is the thing under test

`@SpringBootTest` with the real filter chain. There are only two, deliberately: they cost ~15
seconds of context startup each, so they are reserved for behaviour that *only* exists once
everything is assembled.

- **`SecurityHardeningIntegrationTest`** — headers on real responses, 401 vs 403, logout actually
  revoking a token, login enumeration resistance.
- **`RateLimitChainIntegrationTest`** — that the rate limiter is reachable in the real chain.

The second one is worth dwelling on, because it is the test that justifies the category. The rate
limiter had eight passing unit tests. Those proved it counts correctly; none of them could prove it
was *plugged in*. A filter registered in the wrong position fails open — every request succeeds.
Only a test driving the real chain catches that.

### Frontend tests

Vitest + Testing Library + jsdom. Queried by role and visible text, not by CSS class or test id, so
a styling change does not break a test and an accessibility regression does:

```javascript
expect(screen.getByRole("link", { name: "Markets rally" })).toHaveAttribute("href", "...");
```

| File | Covers |
|---|---|
| `apiClient.test.js` | Token attachment, 401 clearing the session, error-body flattening, offline detection |
| `ErrorBoundary.test.jsx` | Recovery UI instead of a blank page, reload path, error logged |
| `NewsList.test.jsx` | `javascript:` and `data:` links never reaching an `href` |
| `formatters.test.js` | en-IN grouping (`10,00,000`, not `1,000,000`), sign handling, null safety |

---

## Conventions

### Name the behaviour, not the method

```java
// Yes — reads as a sentence, and a failure report tells you what broke
void refusesAHostThatMerelyStartsWithAnAllowedName()
void clearsAnExpiredTokenOn401SoTheUserIsSentBackToLogIn()

// No — tells you nothing when it fails
void testIsAllowed()
void testCase2()
```

### Comment the *why*, never the *what*

The assertion already says what. A comment earns its place by explaining why the case matters —
usually the attack it prevents or the bug it caught:

```java
@Test
void refusesInternalAddressesEvenIfSomehowAllowlisted(String url) {
    // 169.254.169.254 is the cloud metadata endpoint; its response is a set of
    // instance credentials.
```

### Assert the consequence, not the mechanism

```java
// Yes — survives a refactor, fails on a real regression
assertThat(csv).contains("'=HYPERLINK");

// No — passes while the behaviour is broken
verify(sanitizer).sanitize(anyString());
```

### Parameterise families of cases

Nine near-identical tests hide which case failed. One parameterised test names it:

```java
@ParameterizedTest
@ValueSource(strings = {"javascript:alert(1)", "data:text/html;base64,...", "vbscript:msgbox(1)"})
void dropsLinksThatWouldExecuteOnClick(String link) {
    assertThat(RssFeedFetcher.safeLink(link))
            .as("'%s' must never reach an href", link)   // names the case in the failure
            .isNull();
}
```

### Test isolation is not optional

Frontend tests unmount between cases and clear `localStorage` (`src/test/setup.js`). Without it a
component from a previous test stays mounted and `getByRole` starts matching the wrong element — a
"found multiple elements" failure in whichever test happens to run second, which is a miserable
thing to debug.

Backend tests log to console only, via `src/test/resources/logback-test.xml`. This was a real bug:
a `<springProfile name="test">` block never fired, because Spring Boot does not activate a `test`
profile on its own, so every `mvn test` wrote real log files recording deliberately-provoked
security failures.

---

## Writing a test for new code

**A new endpoint** — a controller test for the success shape and each error shape; add it to
`SecurityHardeningIntegrationTest` if it should require authentication.

**A new calculation** — unit tests for the normal case, the boundaries, and the degenerate inputs.
Empty series and single-element series have caught more real bugs here than anything else.

**Anything reading untrusted input** — a happy path, then the adversarial cases. `UploadValidatorTest`
and `LlmHoldingsParserTest` are the models to copy.

**A new security control** — the test must fail if the control is removed. Verify that by actually
removing it and watching the test go red. A security test that passes with the control deleted is
worse than no test, because it reports safety that is not there.

---

## Deliberate gaps

Stated plainly, because a testing document that implies total coverage is not useful.

- **No end-to-end browser tests.** Playwright or Cypress would cover the flows integration tests
  cannot — login through to a rendered dashboard. The gap is known; the value did not yet justify
  the maintenance.
- **Frontend pages are untested.** Coverage is on the components and services where logic lives.
  The page components are mostly composition, which is where component tests earn least.
- **No load or performance tests.** The import batching change was reasoned about and measured by
  hand, not covered by a regression test that would catch a future N+1.
- **External APIs are mocked, never contracted.** If Yahoo changes its response shape, the tests
  still pass; only the typed binding limits the damage at runtime.

---

## Related

- `OWASP_TOP_10_COMPLIANCE.md` — which suite proves which security control
- `docs/BRANCHING_STRATEGY.md` — when tests must pass in the workflow
- `logs/README.md` — where test output is captured
