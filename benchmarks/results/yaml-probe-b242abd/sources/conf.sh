#!/bin/bash
S=/private/tmp/claude-501/-Users-sam-workspace-graalvm-truffledata/107505d2-50ff-4334-8569-b06acaf924d5/scratchpad
cd $S
rm -rf conformance-new
python3 -I probe/conformance.py $S $S/conformance-new || exit 1
python3 -I probe/ryml_tags.py $S rapidyaml-0.16.0 build/cprobe > conformance-new/ryml-classification.txt 2>&1
python3 -I probe/ryml_tags.py $S rapidyaml-0.16.0-tabs build/cprobe-tabs > conformance-new/ryml-tabs-classification.txt 2>&1
cp conformance/ryml-tab-tokens.txt conformance-new/ 2>/dev/null
rm -rf conformance && mv conformance-new conformance
echo "--- tabs+tags failures:"; cat conformance/failures-rapidyaml-0.16.0-tabs-tags.tsv
