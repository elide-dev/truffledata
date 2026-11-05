set -eu
cd /tmp/truffledata-buffa-sparse-profile.kwdveA
mkdir -p profiles
sha256sum truffledata-comparison-profile > profiles/binary.sha256
for cell in proto-mixed:buffa:ingest proto-mixed:buffa:ingest-generated proto-mixed-full:buffa:ingest proto-mixed:stock:ingest; do
  IFS=: read -r workload engine stage <<EOF
$cell
EOF
  name="$workload-$engine-$stage"
  taskset -c 2 perf record -F 997 --call-graph fp -o "profiles/$name.perf" -- ./truffledata-comparison-profile -Xmx1g "$workload" "$engine" "$stage" time "profiles/$name.jfr" > "profiles/$name.log" 2>&1
  perf report --stdio --no-children --percent-limit 1 -i "profiles/$name.perf" > "profiles/$name.flat.txt"
  perf report --stdio --children --percent-limit 1 -i "profiles/$name.perf" > "profiles/$name.calls.txt"
done
python3 json_profile_summary.py profiles dev.elide.truffledata.comparison.Comparison > profile-summary.json
