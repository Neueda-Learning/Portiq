# Branching Strategy

How work moves from an idea to `main` in this repository.

**Model:** trunk-based development with short-lived feature branches and pull requests.
**Trunk:** `main`.

---

## The model in one paragraph

`main` is the single source of truth and is always releasable. Work happens on a short-lived branch
cut from `main`, opens a pull request back into `main`, and is deleted once merged. There is no
long-running `develop`, no release branch, and no per-environment branch — those exist to solve
problems a two-week release cycle creates, and this project does not have that cycle. Deployment is
driven by Jenkins from `main`, so a branch that lives for three weeks is three weeks of merge risk
bought for nothing.

```
main ─────●───────●─────────────●────────●──────────>  always releasable, always deployable
           \     /             /        /
            ●───●             /        /               feature/haptics            → PR #1
                 \           /        /
                  ●─────────●        /                 feature/security-...       → PR #4
                             \      /
                              ●────●                   fix/test-logging-isolation → PR #6
```

---

## Branch types

| Prefix | For | Example | Lifetime |
|---|---|---|---|
| `feature/` | New capability or enhancement | `feature/security-owasp-hardening` | Days |
| `fix/` | Bug fix on `main` | `fix/test-logging-isolation` | Hours to a day |
| `hotfix/` | Production is broken **now** | `hotfix/login-500` | Under an hour |
| `chore/` | Tooling, dependencies, config | `chore/bump-spring-boot` | Hours |
| `docs/` | Documentation only | `docs/api-reference` | Hours |

### Naming

```
<type>/<short-kebab-case-description>

feature/risk-analysis-engine        ✅ says what it does
fix/csv-import-negative-quantity    ✅ says what is wrong
feature/aaditya-work                ❌ ownership is in git, not the branch name
feature/fixes                       ❌ tells the next person nothing
feature/PORTIQ-123                  ❌ ticket number only — needs a lookup to read
```

If a ticket exists, put the number in the PR title, not the branch name. Branch names are read in
`git branch` listings where a bare number forces everyone to go look it up.

---

## The workflow

### 1. Start from current `main`

```bash
git checkout main
git pull origin main
git checkout -b feature/portfolio-alerts
```

Cutting from a stale `main` is the single most common cause of a painful merge. Pull first, every
time.

### 2. Commit in logical units

One commit should be one reviewable idea. Not one commit per file, and not one commit for a week
of work.

**Commit messages** — a short imperative subject, then *why*, in prose:

```
Import holdings in one batch and report row errors usefully

mergeOrCreate was called once per row by the importers, and every call re-read
the entire holdings table. A 500-row broker export therefore performed 500 full
table loads - and because every field is encrypted, each load decrypts every
column of every row.

mergeAll now reads the table once into a ticker-keyed map and writes with a
single saveAll.
```

The diff already shows what changed. The message exists to explain why, and to say what a future
reader would otherwise have to reconstruct. Six months from now that paragraph is the difference
between understanding a decision and reverting it by accident.

### 3. Keep the branch current

If `main` moves while you work, bring it in **before** opening the PR:

```bash
git fetch origin
git merge origin/main        # or: git rebase origin/main, if the branch is unpushed
```

Rebase freely on a branch nobody else has pulled. Once it is shared, merge — rewriting history
under a colleague is how you lose their work.

### 4. Push and open a PR

```bash
git push -u origin feature/portfolio-alerts
gh pr create --base main --title "..." --body "..."
```

### 5. Merge, then delete the branch

Squash for a messy branch; merge commit when the individual commits are worth keeping — as with
the three-way split of the security work, where merge commits preserved each author's contributions
separately.

---

## Pull requests

### Before you open one

- [ ] `cd backend && mvn test` passes
- [ ] `cd frontend && npm test` passes
- [ ] `cd frontend && npm run build` succeeds
- [ ] No secrets, tokens, `.env` files or log files in the diff
- [ ] Branch is up to date with `main`

### What a good PR description contains

The template is: **what changed, why, what it means for a reviewer.** Not a restatement of the
diff — the diff is right there.

```markdown
## What this changes
Two-sentence summary.

## Why
The problem being solved. If it fixes a bug, describe the failure.

## Verification
What you ran and what you observed. "Tests pass" is weaker than
"240 tests pass, and I confirmed no log directory is created."

## Notes for review
Anything you want a second opinion on, and anything you deliberately did NOT do.
```

That last section carries the most weight. Saying *"I left the `localStorage` token as-is because
moving to a cookie means reinstating CSRF protection"* is worth more than any amount of "LGTM",
because it puts the decision where it can be argued with.

### Review

One approval from someone who did not write it. A reviewer is asking:

- Does it do what the description says?
- Would this break silently? (Silent failures are the ones review is best at catching.)
- Is the test proving the behaviour, or just exercising the code?
- Would someone new understand *why* in six months?

Review the code, not the person. `"this catch swallows the cause"` — not `"you swallowed the cause"`.

---

## Stacked branches

When work naturally splits into parts that build on each other, stack them rather than shipping one
enormous PR. This repository has done exactly that:

```bash
git checkout -b feature/security-owasp-hardening main
# ... commits ...
git checkout -b feature/code-quality-hardening        # cut from the branch above
# ... commits ...
git checkout -b feature/observability-and-docs        # cut from that one
```

All three target `main`, and **merge in stack order**. Until the one below lands, each PR's diff
includes its parent's commits; once the parent merges, the diff collapses to just its own.

Worth doing when parts are genuinely sequential — the observability work documents the security
work, so reviewing it first would mean reviewing documentation of code that did not exist yet.
Not worth doing for independent work: two parallel branches off `main` are simpler and merge in
either order.

---

## Hotfixes

Production is broken. The process is shorter, not absent:

```bash
git checkout main && git pull origin main
git checkout -b hotfix/login-500
# smallest change that fixes it, plus a test that fails without it
git push -u origin hotfix/login-500
gh pr create --base main --title "Hotfix: ..." --label hotfix
```

Still a PR, still reviewed, still tested — an unreviewed change to a broken production system is
how one outage becomes two. What changes is scope: fix the failure, ship it, and do the tidying in
a follow-up.

---

## What CI enforces

`.github/workflows/` runs on every PR into `main`:

| Check | Runs |
|---|---|
| Backend tests (298) | On any `backend/**` change |
| Dependency CVE scan | On any `backend/**` change, plus weekly — fails on CVSS ≥ 7 |
| Frontend tests (25) | On any `frontend/**` change |
| Frontend build | On any `frontend/**` change |
| Docker images build | After tests pass |

The CVE scan also runs on a schedule, because a vulnerability is published against a dependency you
already have, not against a change you just made. A scan that only runs on push reports it whenever
someone next happens to touch that directory — which could be weeks.

Jenkins deploys from `main` after merge. See `DEPLOYMENT.md`.

---

## Repository state

### Active

| Branch | Role |
|---|---|
| `main` | Trunk. Default branch, protected, deployed from. |

### Legacy — do not branch from these

`backend`, `frontend`, `frontend-ci-setup`, `doc` predate the monorepo consolidation. They are
kept for history and are **31+ commits divergent** from `main`.

This is a live trap, not a hypothetical one: `origin/HEAD` still points at `backend`, so tooling
that asks git for "the main branch" gets the wrong answer, and a PR targeting it would try to merge
the entire frontend codebase as a change. **Recommended:** repoint `origin/HEAD` at `main` and
archive or delete these.

```bash
git remote set-head origin main     # fix locally
```

---

## Rules

1. **Never commit directly to `main`.** Everything arrives through a reviewed PR.
2. **Never force-push a shared branch.** `--force-with-lease` on your own unshared branch is fine.
3. **Never commit secrets.** `.env`, tokens, keys, `*.log` — `.gitignore` covers the known cases,
   but it does not cover carelessness.
4. **Never merge red CI.** A skipped test is a broken test with better manners.
5. **Delete merged branches.** A stale branch list makes the live ones hard to find.

---

## Related

- `TESTING.md` — what must pass before a PR
- `DEPLOYMENT.md` — how `main` reaches production
- `SECURITY.md` — controls a change must not regress
