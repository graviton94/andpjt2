#!/usr/bin/env bash
set -euo pipefail

sim_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
if [[ -z ${ANDPJT2_TOOLS:-} ]]; then source /workspace/.andpjt2-android/activate.sh; fi
engine="$sim_dir/../app/src/main/java/com/graviton94/notificationcleaner/GameEngine.kt"
economy="$(dirname "$engine")/Economy.kt"
cache="$ANDPJT2_TOOLS/simulator-cache"
mkdir -p "$cache"

dependency() {
    local group=$1 artifact=$2 version=$3 jar
    jar=$(find "$GRADLE_USER_HOME/caches/modules-2/files-2.1/$group/$artifact/$version" \
        -type f -name "$artifact-$version.jar" -print -quit 2>/dev/null) || true
    if [[ -z "$jar" ]]; then
        printf 'Missing %s:%s:%s. Run :app:compileDebugKotlin in the configured checkout first.\n' \
            "$group" "$artifact" "$version" >&2
        return 1
    fi
    printf '%s\n' "$jar"
}

stdlib=$(dependency org.jetbrains.kotlin kotlin-stdlib 2.2.21)
fingerprint=$(sha256sum "$engine" "$economy" "$sim_dir/BalanceBacktest.kt" "$sim_dir/balance-backtest.sh" | sha256sum)
output="$cache/${fingerprint%% *}.jar"
if [[ ! -f "$output" ]]; then
    compiler=$(dependency org.jetbrains.kotlin kotlin-compiler-embeddable 2.2.21)
    script=$(dependency org.jetbrains.kotlin kotlin-script-runtime 2.2.21)
    reflect=$(dependency org.jetbrains.kotlin kotlin-reflect 1.6.10)
    daemon=$(dependency org.jetbrains.kotlin kotlin-daemon-embeddable 2.2.21)
    coroutines=$(dependency org.jetbrains.kotlinx kotlinx-coroutines-core-jvm 1.8.0)
    annotations=$(dependency org.jetbrains annotations 13.0)
    stage=$(mktemp -d "$cache/compile.XXXXXX")
    trap 'rm -rf -- "$stage"' EXIT
    "$JAVA_HOME/bin/java" -Xmx1g -cp "$compiler:$stdlib:$script:$reflect:$daemon:$coroutines:$annotations" \
        org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 \
        -classpath "$stdlib" -d "$stage/simulator.jar" "$engine" "$economy" "$sim_dir/BalanceBacktest.kt"
    mv -f -- "$stage/simulator.jar" "$output"
    rmdir "$stage"
    trap - EXIT
fi

exec "$JAVA_HOME/bin/java" -Xmx1536m -cp "$output:$stdlib" notificationcleaner.report.BalanceBacktestKt "$@"
