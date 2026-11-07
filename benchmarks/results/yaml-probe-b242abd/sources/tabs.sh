#!/bin/bash
# rapidyaml built with RYML_WITH_TAB_TOKENS: conformance delta on the 19 remaining failures, and speed.
S=/private/tmp/claude-501/-Users-sam-workspace-graalvm-truffledata/107505d2-50ff-4334-8569-b06acaf924d5/scratchpad
cd $S
ln -sfn libyaml build/libyaml-tabs
SAN=-tabs SANFLAGS="-O3 -DNDEBUG -DRYML_WITH_TAB_TOKENS" bash tools/build_cprobe.sh > /dev/null 2>&1 || echo BUILD FAILED
T=$S/dl/yaml-test-suite/yaml-test-suite-data-2022-01-17
for c in 6BCT DC7X J3BT K54U Y79Y/004 Y79Y/006 Y79Y/008 Y79Y/010 A2M4; do
  exp=$T/$c/test.event
  if [ -f $T/$c/error ]; then
    if ./build/cprobe-tabs events $T/$c/in.yaml > /dev/null 2>&1; then echo "$c: still accepts invalid"; else echo "$c: now rejects (pass)"; fi
  else
    out=$(./build/cprobe-tabs events $T/$c/in.yaml 2>/dev/null)
    if [ "$out" == "$(cat $exp)" ]; then echo "$c: pass"; else echo "$c: still fails"; fi
  fi
done
./build/cprobe-tabs bench 20 probe/fixtures/records-block.yaml 2>&1 | grep ryml
