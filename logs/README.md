# Logs

Everything Portiq writes lands here. The directory is tracked (via `.gitkeep`) but its contents are
gitignored — the structure is part of the project, the logs are not.

```
logs/
├── portiq.log                 application log, all levels
├── portiq-error.log           WARN and above only
├── portiq-security.log        the audit trail
├── archive/                   rolled and gzipped older files
├── build/
│   ├── backend-test-<timestamp>.log
│   ├── backend-test-latest.log
│   └── ...                    one pair per build task
└── run/
    ├── backend-run-latest.log
    └── frontend-run-latest.log
```

## Runtime logs

Written by the application itself, configured in
`backend/src/main/resources/logback-spring.xml`.

| File | Contains | Kept |
|---|---|---|
| `portiq.log` | everything the application logs | 14 days, 500 MB cap |
| `portiq-error.log` | WARN and ERROR only — the first place to look | 30 days, 200 MB cap |
| `portiq-security.log` | logins, lockouts, rate limiting, blocked outbound requests | 365 days, 1 GB cap |

All three roll daily and at 10 MB, and each has a total size cap, so logging can never be what
fills the disk.

The security log is kept far longer than the others on purpose. The question it answers — "when
did this account start being probed, and from where" — is usually asked long after the fact, and a
two-week window routinely predates the discovery of an incident.

It is also the only appender not buffered through an async writer. The rest are, so a slow disk
cannot stall a request; the security log is written synchronously because those lines are the
record of an attack in progress, and dropping them under load is exactly the wrong trade — load is
when they matter most.

Point the application somewhere else with `LOG_DIR`:

```bash
LOG_DIR=/var/log/portiq java -jar portiq.jar
```

In a container this must be a mounted volume, or the logs vanish with the container that wrote
them.

## Build and run logs

`mvn test` and `npm run build` print to a terminal and are gone the moment it closes. When a build
fails on someone else's machine, or a run dies overnight, the only useful artefact — the output —
is the one thing nobody kept. These scripts fix that:

```bash
# Linux / macOS / Git Bash
scripts/run-with-logs.sh test-backend
scripts/run-with-logs.sh all          # every build and test, in order
```

```powershell
# Windows
.\scripts\run-with-logs.ps1 test-backend
.\scripts\run-with-logs.ps1 all
```

Tasks: `build-backend`, `test-backend`, `build-frontend`, `test-frontend`, `run-backend`,
`run-frontend`, `all`.

Output is tee'd, so you still watch it live. Every log opens with a header recording the branch,
commit, host and start time, and closes with the exit code — which is what makes a log from three
weeks ago worth anything at all. Each task also writes a `-latest.log` copy, so docs and tooling
can reference one stable path.

The scripts exit with the underlying command's status, so CI can depend on them.

## Reading them

```bash
# Failed logins and lockouts
grep -E 'LOGIN_FAILURE|LOCKED_OUT' logs/portiq-security.log

# Everything from one IP
grep 'ip=203.0.113.5' logs/portiq-security.log

# Blocked outbound requests - an SSRF attempt looks like this
grep 'OUTBOUND_REQUEST_BLOCKED' logs/portiq-security.log

# Trace a 500 the user reported by its reference code
grep 'ref a1b2c3d4' logs/portiq-error.log

# Why the last backend build failed
tail -50 logs/build/backend-test-latest.log
```

Security lines are structured as `event=NAME key=value ...`, so they parse with `awk` or ship
straight into anything that speaks logfmt.

## What is never written here

No passwords, tokens, API keys, or cookie values — the audit logger takes only the fact that a
credential was presented and whether it was accepted. Every caller-supplied value is stripped of
control characters before it is written, so a username containing a newline cannot forge extra log
lines and bury its own trail.

Client IP addresses are personal data under GDPR. Set `AUDIT_LOG_CLIENT_IP=false` to record
`ip=redacted` instead.
