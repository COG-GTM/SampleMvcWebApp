#!/usr/bin/env bash
# Builds the legacy Java 8 jars the contract tests drive (if missing), then runs the Java 21 contract suite.
# Needs: Docker, JDK 8 and JDK 21. Optional: mono + mcs (runs the real portal RetakeRules.cs; otherwise skipped).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
JAVA8_HOME="${JAVA8_HOME:-/usr/lib/jvm/java-8-openjdk-amd64}"
JAVA21_HOME="${JAVA21_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export MVNW_REPOURL="${MVNW_REPOURL:-https://maven-central.storage-download.googleapis.com/maven2}"

if [ "${REBUILD_LEGACY:-0}" = "1" ] || [ ! -f "$root/services/booking-api/target/booking-api.jar" ] \
        || [ ! -f "$root/services/results-service/target/results-service.jar" ]; then
    echo "== building legacy services (Java 8)"
    (cd "$root/services" && JAVA_HOME="$JAVA8_HOME" ./mvnw -q -B -DskipTests package)
fi

# Target: CONTRACT_TARGET or -Dcontract.target=legacy|scheduling. Defaults to scheduling when this tree has
# services/scheduling-service (Phase 1+), since its booking-api/results-service are rewired to call it.
if [ -f "$root/services/scheduling-service/pom.xml" ]; then default_target=scheduling; else default_target=legacy; fi
target="${CONTRACT_TARGET:-$default_target}"
case " $* " in
    *" -Dcontract.target=scheduling "*) target=scheduling ;;
    *" -Dcontract.target=legacy "*) target=legacy ;;
esac
if [ "$target" = "legacy" ] && [ -f "$root/services/scheduling-service/pom.xml" ]; then
    cat >&2 <<'WARN'
== WARNING: legacy mode is only valid on the pre-Phase-2 baseline (migration/phase0 / estate/legacy-baseline).
   This tree's booking-api and results-service call scheduling-service, which legacy mode does not start, so
   ResultsAttend, BookingApi and Retake will fail here. Use CONTRACT_TARGET=scheduling (the default on this tree).
WARN
fi
if [ "$target" = "scheduling" ] && { [ "${REBUILD_LEGACY:-0}" = "1" ] \
        || [ ! -f "$root/services/scheduling-service/target/scheduling-service.jar" ]; }; then
    echo "== building scheduling-service (Java 21)"
    (cd "$root/services/scheduling-service" && JAVA_HOME="$JAVA21_HOME" "$root/services/mvnw" -q -B -DskipTests package)
fi

echo "== running scheduling contract tests (Java 21, target=$target)"
cd "$here"
JAVA_HOME="$JAVA21_HOME" mvn -B test -Dlegacy.java.home="$JAVA8_HOME" -Dcontract.target="$target" "$@"
