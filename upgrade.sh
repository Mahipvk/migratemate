#!/bin/bash
# Usage: ./upgrade.sh [path-to-maven-project] [full|recipes|ai]
# Defaults: the bundled sample app, mode "full" (OpenRewrite + AI).
set -e
cd "$(dirname "$0")"

# Prefer Java 21 if it is installed (macOS)
if [ -x /usr/libexec/java_home ] && /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
  export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
fi

if [ ! -f target/migratemate.jar ] || [ -n "$(find src pom.xml -newer target/migratemate.jar 2>/dev/null | head -1)" ]; then
  echo "Building MigrateMate..."
  mvn -q -B package
fi

java -jar target/migratemate.jar --project="${1:-samples/legacy-bank-app}" --mode="${2:-full}"
