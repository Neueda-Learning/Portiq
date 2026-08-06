#!/usr/bin/env bash
#
# Runs a build or start command and keeps a durable record of it under logs/.
#
# The problem this solves: `mvn test` and `npm run build` print to a terminal and are gone the
# moment it closes. When a build fails on someone else's machine, or a run dies overnight, the
# only useful artefact - the output - is the thing that was never kept. Runtime logging is handled
# by logback-spring.xml; this covers everything that happens *around* the application.
#
# Output is tee'd, so you still watch it live. Each log carries a header recording the commit,
# branch and start time, which is what makes an old log worth anything.
#
# Usage:
#   scripts/run-with-logs.sh build-backend
#   scripts/run-with-logs.sh build-frontend
#   scripts/run-with-logs.sh test-backend
#   scripts/run-with-logs.sh test-frontend
#   scripts/run-with-logs.sh run-backend
#   scripts/run-with-logs.sh run-frontend
#   scripts/run-with-logs.sh all          # every build and test, in order
#
# Exit status is the command's own, so CI can rely on it.

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="${LOG_DIR:-$REPO_ROOT/logs}"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"

mkdir -p "$LOG_DIR/build" "$LOG_DIR/run"

git_describe() {
    git -C "$REPO_ROOT" rev-parse --short HEAD 2>/dev/null || echo "unknown"
}

git_branch() {
    git -C "$REPO_ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown"
}

# Runs a command, tee'ing to both a timestamped log and a stable "latest" symlink-style copy.
# The latest copy matters more than it looks: it means tooling and docs can reference one path
# without knowing when the run happened.
capture() {
    local name="$1" category="$2" workdir="$3"
    shift 3

    local log_file="$LOG_DIR/$category/${name}-${TIMESTAMP}.log"
    local latest_file="$LOG_DIR/$category/${name}-latest.log"

    {
        echo "=============================================================="
        echo " Portiq $category log"
        echo " task      : $name"
        echo " command   : $*"
        echo " directory : $workdir"
        echo " branch    : $(git_branch)"
        echo " commit    : $(git_describe)"
        echo " started   : $(date -Is)"
        echo " host      : $(hostname)"
        echo "=============================================================="
        echo
    } | tee "$log_file" > "$latest_file"

    local status=0
    ( cd "$workdir" && "$@" ) 2>&1 | tee -a "$log_file" "$latest_file"
    status=${PIPESTATUS[0]}

    {
        echo
        echo "--------------------------------------------------------------"
        echo " finished  : $(date -Is)"
        echo " exit code : $status"
        echo "--------------------------------------------------------------"
    } | tee -a "$log_file" > /dev/null
    tail -4 "$log_file" >> "$latest_file"

    if [ "$status" -eq 0 ]; then
        echo ">> $name succeeded. Log: $log_file"
    else
        echo ">> $name FAILED (exit $status). Log: $log_file" >&2
    fi
    return "$status"
}

case "${1:-}" in
    build-backend)
        capture "backend-build" build "$REPO_ROOT/backend" mvn -B clean package -DskipTests
        ;;
    test-backend)
        capture "backend-test" build "$REPO_ROOT/backend" mvn -B test
        ;;
    build-frontend)
        capture "frontend-build" build "$REPO_ROOT/frontend" npm run build
        ;;
    test-frontend)
        capture "frontend-test" build "$REPO_ROOT/frontend" npm test
        ;;
    run-backend)
        # Runtime logging also goes to logs/portiq*.log via logback; this captures startup output
        # and anything written straight to stdout/stderr, which logback never sees.
        capture "backend-run" run "$REPO_ROOT/backend" mvn -B spring-boot:run
        ;;
    run-frontend)
        capture "frontend-run" run "$REPO_ROOT/frontend" npm run dev
        ;;
    all)
        overall=0
        capture "backend-test"   build "$REPO_ROOT/backend"  mvn -B test           || overall=1
        capture "backend-build"  build "$REPO_ROOT/backend"  mvn -B clean package -DskipTests || overall=1
        capture "frontend-test"  build "$REPO_ROOT/frontend" npm test              || overall=1
        capture "frontend-build" build "$REPO_ROOT/frontend" npm run build         || overall=1
        echo
        echo "All logs are under $LOG_DIR/build/"
        exit "$overall"
        ;;
    *)
        sed -n '2,25p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
        exit 2
        ;;
esac
