#!/bin/bash
# Local advisory arm64 run (Apple M5 Max): Java kernels on the JVM, then the C++ probe (NEON).
set -euo pipefail
W=/Users/sam/workspace/graalvm/truffledata/.claude/worktrees/agent-a543fd922eeecd1b2
L=/private/tmp/claude-501/-Users-sam-workspace-graalvm-truffledata/107505d2-50ff-4334-8569-b06acaf924d5/scratchpad/local
M=/Users/sam/Library/Caches/komodo-bazel-volume/root/92d3b3424470196a97480bc33393afaf/external/rules_jvm_external++maven+unpinned_maven/v1/https/maven.elide.dev/gvm-elide/25.5.5-elide.24/org/graalvm
V=25.5.5-elide.24
CP="$M/truffle/truffle-api/$V/truffle-api-$V.jar:$M/polyglot/polyglot/$V/polyglot-$V.jar:$M/sdk/collections/$V/collections-$V.jar:$M/sdk/word/$V/word-$V.jar:$M/sdk/nativeimage/$V/nativeimage-$V.jar:/Users/sam/.m2/repository/org/jspecify/jspecify/1.0.0/jspecify-1.0.0.jar"
cd "$W"
mkdir -p "$L/classes" "$L/data"
if [ "${1:-}" != "--skip-java" ]; then
javac -nowarn -d "$L/classes" --add-modules jdk.incubator.vector -cp "$CP" \
  -processorpath "$M/truffle/truffle-dsl-processor/$V/truffle-dsl-processor-$V.jar" \
  $(find core/src/main/java csv/src/main/java csv/kernel/src/main csv/kernel/src/vector -name "*.java") \
  benchmarks/comparison/CsvFixture.java benchmarks/comparison/csv/CsvBench.java "$L/LocalBench.java" 2>&1 | grep -v "^Note" || true
java --add-modules jdk.incubator.vector -Xmx8g -cp "$L/classes:$CP" LocalBench "$L/data" | tee "$L/jvm-arm64.jsonl"
fi
S=/Users/sam/Library/Caches/komodo-bazel-volume/root/47b326af2da7d9467eaa615f95541b7f/external/rules_rust++crate+crates__simdutf-0.7.0/cpp
clang++ -std=c++17 -O3 -march=armv8.2-a+crypto -I. -I"$S" benchmarks/comparison/csv_probe.cc csv/native/index_swar.cc \
  csv/native/index_neon.cc csv/native/dispatch.cc csv/native/index_avx2.cc csv/native/index_avx512.cc "$S/simdutf.cpp" -o "$L/csv_probe"
: > "$L/native-arm64.jsonl"
for d in numeric mixed quoted escaped; do "$L/csv_probe" "$d" "$L/data/$d.csv" | tee -a "$L/native-arm64.jsonl"; done
