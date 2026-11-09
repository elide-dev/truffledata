#!/bin/bash
# cg_mode.sh BINDIR OUTDIR MODE ENGINE... : callgrind one mode's interpreter cell per engine, then summarize.
set -uo pipefail
here=$(dirname "$0")
bindir=$1 outdir=$2 mode=$3
shift 3
for e in "$@"; do
  "$here/cg_consumer.sh" "$bindir" "$outdir" "$mode-$e" "$e" "$mode" interpret
  echo "$e rc=$?"
done
for e in "$@"; do
  echo "### $mode $e"
  python3 "$here/cg_summary.py" "$outdir/cg.$mode-$e.gz" 32 6
  python3 "$here/cg_edges.py" "$outdir/cg.$mode-$e.gz" 32 0.3 | tail -1
done
