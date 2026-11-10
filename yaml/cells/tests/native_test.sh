#!/usr/bin/env bash
# Runs the cells image over the YAML corpus: testdata, the session fuzz corpus, the yaml-test-suite.
set -euo pipefail
cd "${TEST_SRCDIR}/${TEST_WORKSPACE}"
exec "$1" yaml/native/testdata yaml/native/corpus/session "${TEST_SRCDIR}/+http_archive+yaml_test_suite"
