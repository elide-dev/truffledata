#!/bin/bash
S=/private/tmp/claude-501/-Users-sam-workspace-graalvm-truffledata/107505d2-50ff-4334-8569-b06acaf924d5/scratchpad
T=$S/dl/yaml-test-suite/yaml-test-suite-data-2022-01-17
export RYML_RESOLVE_TAGS=1
for c in 2AUY 5TYM 6CK3 6WLZ 9WXW C4HZ CC74 P76L QLJ7 U3C3 Z9M4; do
  echo "== $c"; diff <($S/build/cprobe-tabs events $T/$c/in.yaml 2>&1) $T/$c/test.event | head -6
done
