#!/bin/bash
# cg_sim.sh BINDIR OUTDIR LABEL CASE... : callgrind of the simulation image, one case per run,
# collection toggled on __codspeed_root_frame__databench, in the amd64 trixie container
# (Valgrind 3.24 reads the image's DWARF; 3.19 does not).
set -euo pipefail
bindir=$1 outdir=$2 label=$3; shift 3
mkdir -p "$outdir"
for case in "$@"; do
  docker run --rm --platform linux/amd64 -v "$bindir":/b:ro -v "$outdir":/out td-valgrind-trixie \
    valgrind --tool=callgrind --toggle-collect=__codspeed_root_frame__databench \
    --compress-strings=no --compress-pos=no --dump-instr=yes \
    --callgrind-out-file="/out/cg.$label.$case" /b/truffledata-simulation "$case" \
    > "$outdir/log.$label.$case" 2>&1 || echo "FAILED $case" >> "$outdir/failures"
  gzip -f "$outdir"/cg.$label.$case*
done
echo done > "$outdir/done.$label"
