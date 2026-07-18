#!/usr/bin/env sh
# Composition concurrency stress tests.
#   ./scripts/stress.sh            # tier 1: in-memory H2 (no infra)
#   ./scripts/stress.sh mariadb    # tier 2: real MariaDB via docker compose
# Extra knobs: STRESS_ROUNDS, STRESS_THREADS env vars.
set -e
cd "$(dirname "$0")/.."

ARGS="-Pstress -Dtest=CompositionStressTest -DfailIfNoTests=false"
[ -n "$STRESS_ROUNDS" ] && ARGS="$ARGS -Dstress.rounds=$STRESS_ROUNDS"
[ -n "$STRESS_THREADS" ] && ARGS="$ARGS -Dstress.threads=$STRESS_THREADS"

if [ "$1" = "mariadb" ]; then
    docker compose up -d mariadb
    echo "waiting for mariadb to accept connections..."
    for i in $(seq 1 30); do
        if docker compose exec -T mariadb healthcheck.sh --connect >/dev/null 2>&1 \
           || docker compose exec -T mariadb mariadb -uingest -pingest -e 'select 1' ingest >/dev/null 2>&1; then
            break
        fi
        sleep 2
        [ "$i" = 30 ] && { echo "mariadb did not come up" >&2; exit 1; }
    done
    # OS env beats src/test/resources/application.yml in Spring's precedence order.
    export SPRING_DATASOURCE_URL="jdbc:mariadb://localhost:3306/ingest"
    export SPRING_DATASOURCE_USERNAME=ingest
    export SPRING_DATASOURCE_PASSWORD=ingest
    export SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.mariadb.jdbc.Driver
    echo "NOTE: running against docker mariadb; tables are Flyway-managed in the 'ingest' db."
fi

# shellcheck disable=SC2086
./scripts/verify.sh $ARGS
