#!/usr/bin/env bash
# collect_measure.sh DEST INVOCATION : copy consumer_measure shards out of bazel-testlogs and merge.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
dest=$1 invocation=$2
mkdir -p "$dest/shards"
for s in bazel-testlogs/benchmarks/comparison/consumer_measure/shard_*; do
  n=$(basename "$s")
  mkdir -p "$dest/shards/$n"
  cp "$s/test.outputs/consumer/results.json" "$dest/shards/$n/results.json"
done
python3 -I tools/bench/consumer.py merge --output "$dest/merged" "$dest"/shards/*/results.json | tail -3
echo "$invocation" > "$dest/invocation.txt"
ls "$dest/merged"
