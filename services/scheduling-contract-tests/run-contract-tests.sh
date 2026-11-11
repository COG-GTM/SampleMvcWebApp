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

# -Dcontract.target=scheduling (or CONTRACT_TARGET=scheduling) also needs the scheduling-service jar (Java 21)
target="${CONTRACT_TARGET:-legacy}"
case " $* " in *" -Dcontract.target=scheduling "*) target=scheduling ;; esac
if [ "$target" = "scheduling" ] && { [ "${REBUILD_LEGACY:-0}" = "1" ] \
        || [ ! -f "$root/services/scheduling-service/target/scheduling-service.jar" ]; }; then
    echo "== building scheduling-service (Java 21)"
    (cd "$root/services/scheduling-service" && JAVA_HOME="$JAVA21_HOME" "$root/services/mvnw" -q -B -DskipTests package)
fi

echo "== running scheduling contract tests (Java 21, target=$target)"
cd "$here"
JAVA_HOME="$JAVA21_HOME" mvn -B test -Dlegacy.java.home="$JAVA8_HOME" -Dcontract.target="$target" "$@"
