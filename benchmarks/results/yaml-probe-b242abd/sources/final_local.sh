#!/bin/bash
# The selected configuration (RYML_WITH_TAB_TOKENS + resolve_tags): speed and ASan/UBSan.
S=/private/tmp/claude-501/-Users-sam-workspace-graalvm-truffledata/107505d2-50ff-4334-8569-b06acaf924d5/scratchpad
cd $S
export RYML_RESOLVE_TAGS=1
./build/cprobe-tabs bench 20 probe/fixtures/config.yaml probe/fixtures/records-block.yaml probe/fixtures/records-flow.yaml probe/fixtures/records-stream.yaml probe/fixtures/text-heavy.yaml probe/fixtures/records-flow-lines.yaml 2>&1 | grep ryml > results-cprobe-selected-arm64.txt
cat results-cprobe-selected-arm64.txt
SF="-fsanitize=address,undefined -fno-sanitize-recover=all -fno-omit-frame-pointer -g -O1 -DRYML_WITH_TAB_TOKENS"
ln -sfn libyaml-asan build/libyaml-asan-tabs
SAN=-asan-tabs SANFLAGS="$SF" bash tools/build_cprobe.sh > build-cprobe-asan-tabs.log 2>&1 || { echo "asan tabs build failed"; tail -5 build-cprobe-asan-tabs.log; }
T=$S/dl/yaml-test-suite/yaml-test-suite-data-2022-01-17
export ASAN_OPTIONS=detect_leaks=0 UBSAN_OPTIONS=print_stacktrace=1
n=0; bad=0
for f in $(find $T -name in.yaml | grep -v -E "/(name|tags)/") probe/fixtures/*.yaml; do
  n=$((n+1))
  if ./build/cprobe-asan-tabs events $f 2>&1 >/dev/null | grep -q -E "Sanitizer|runtime error"; then bad=$((bad+1)); echo "  SANITIZER: $f"; fi
done
echo "build/cprobe-asan-tabs events (RYML_WITH_TAB_TOKENS, resolve_tags): $n inputs, $bad with sanitizer reports" | tee -a results-sanitizers-arm64.txt
