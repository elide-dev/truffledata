#!/bin/bash
# cg_consumer.sh BINDIR OUTDIR LABEL ENGINE [MODE] [STAGE] [BINARY]
# Runs one consumer cell of a *-profile image under callgrind in the amd64 container. Collection
# toggles on SharedRoot_execute (the guest root's AOT interpreter body and everything it calls), so
# setup, fixture preparation and the harness loop outside the root are excluded. Shape/Ir only.
set -euo pipefail
bindir=$1 outdir=$2 label=$3 engine=$4 mode=${5:-full} stage=${6:-interpret}
binary=${7:-truffledata-consumer-profile}
mkdir -p "$outdir"
toggle='*SharedRoot_execute*'
if [ "$engine" = stock ]; then toggle='*StockRoot_execute*'; fi
docker run --rm --platform linux/amd64 -v "$bindir":/b:ro -v "$outdir":/out td-valgrind-trixie \
  valgrind --tool=callgrind "--toggle-collect=$toggle" \
  --compress-strings=no --compress-pos=no --dump-instr=yes \
  --callgrind-out-file="/out/cg.$label" "/b/$binary" \
  -Xmx1g "$engine" "$mode" "$stage" time "/out/$label.jfr" \
  > "$outdir/log.$label" 2>&1
rm -f "$outdir/$label.jfr"
gzip -f "$outdir/cg.$label"
