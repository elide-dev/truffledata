#!/bin/bash
# cg_controls.sh BINDIR OUTDIR ENGINE... : callgrind each engine's full interpreter cell, then summarize.
set -uo pipefail
here=$(dirname "$0")
bindir=$1 outdir=$2
shift 2
for e in "$@"; do
  "$here/cg_consumer.sh" "$bindir" "$outdir" "$e" "$e"
  echo "$e rc=$?"
done
for e in "$@"; do
  echo "### $e"
  python3 "$here/cg_summary.py" "$outdir/cg.$e.gz" 32 10
  python3 "$here/cg_edges.py" "$outdir/cg.$e.gz" 32 0.3 | tail -1
done
