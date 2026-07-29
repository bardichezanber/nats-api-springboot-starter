#!/usr/bin/env sh
# Single verification command for humans and agents: ./scripts/verify.sh
# Locates a JDK 25 if JAVA_HOME is not set, then runs the full build + tests.
set -e

if [ -z "$JAVA_HOME" ]; then
    # A JDK 25 is preferred over whatever is on PATH: the build targets
    # release 25, so an older PATH java would fail at compile time anyway.
    if [ -x /usr/libexec/java_home ] && /usr/libexec/java_home -v 25 >/dev/null 2>&1; then
        JAVA_HOME=$(/usr/libexec/java_home -v 25)
        export JAVA_HOME
    elif [ -d /opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ]; then
        JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
        export JAVA_HOME
    # Note: macOS ships a /usr/bin/java stub that exists but cannot run,
    # so actually execute it instead of just checking it is on PATH.
    elif java -version >/dev/null 2>&1; then
        : # some other working java on PATH; mvnw will report it if too old
    else
        echo "ERROR: no JDK found. Install JDK 25 or set JAVA_HOME." >&2
        exit 1
    fi
fi

cd "$(dirname "$0")/.."
exec ./mvnw -B verify "$@"
