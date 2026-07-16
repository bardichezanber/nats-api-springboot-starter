#!/usr/bin/env sh
# Single verification command for humans and agents: ./scripts/verify.sh
# Locates a JDK 21 if JAVA_HOME is not set, then runs the full build + tests.
set -e

if [ -z "$JAVA_HOME" ]; then
    # Note: macOS ships a /usr/bin/java stub that exists but cannot run,
    # so actually execute it instead of just checking it is on PATH.
    if java -version >/dev/null 2>&1; then
        : # a working java on PATH is enough for mvnw
    elif [ -x /usr/libexec/java_home ] && /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
        JAVA_HOME=$(/usr/libexec/java_home -v 21)
        export JAVA_HOME
    elif [ -d /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ]; then
        JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
        export JAVA_HOME
    else
        echo "ERROR: no JDK found. Install JDK 21 or set JAVA_HOME." >&2
        exit 1
    fi
fi

cd "$(dirname "$0")/.."
exec ./mvnw -B verify "$@"
