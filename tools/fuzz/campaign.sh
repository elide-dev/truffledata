#!/usr/bin/env bash
set -uo pipefail
binary="$1"
shift
out="${TEST_UNDECLARED_OUTPUTS_DIR:?}"
mkdir -p "$out/corpus"
extra=()
for seed in "$@"; do
  case "$seed" in
    --*) extra+=("$seed") ;;
    *) cp "$seed" "$out/corpus/" || exit 1 ;;
  esac
done
# Preserve discoveries and crash/timeout/leak reproducers as Bazel test outputs.
"$binary" "${extra[@]}" "$out/corpus" -max_total_time="${TD_FUZZ_SECONDS:-30}" \
  -timeout=5 -rss_limit_mb=1024 -max_len=65536 -print_final_stats=1 \
  -artifact_prefix="$out/"
status=$?
exit "$status"
