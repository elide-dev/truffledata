#!/usr/bin/env bash
set -euo pipefail
binary="$1"
out="${TEST_UNDECLARED_OUTPUTS_DIR:?}"
for kind in address undefined; do
  if "$binary" "$kind" >"$out/$kind.log" 2>&1; then
    echo "Sanitizer canary unexpectedly succeeded: $kind" >&2
    exit 1
  fi
done
grep -q 'AddressSanitizer: heap-buffer-overflow' "$out/address.log"
grep -q 'runtime error: signed integer overflow' "$out/undefined.log"
