set -eu
cd /tmp/truffledata-native-views.G2ATYf
mkdir -p profiles
sha256sum truffledata-comparison-profile > profiles/binary.sha256
for cell in false:proto-mixed:buffa:ingest-generated true:proto-mixed:buffa:ingest-generated true:proto-mixed-full:buffa:ingest; do
  IFS=: read -r view workload engine stage <<EOF
$cell
EOF
  name="$view-$workload-$engine-$stage"
  taskset -c 2 perf record -F 997 --call-graph fp -o "profiles/$name.perf" -- ./truffledata-comparison-profile -Dtruffledata.buffa.views="$view" -Xmx1g "$workload" "$engine" "$stage" time "profiles/$name.jfr" > "profiles/$name.log" 2>&1
  perf report --stdio --no-children --percent-limit 1 -i "profiles/$name.perf" > "profiles/$name.flat.txt"
  perf report --stdio --children --percent-limit 1 -i "profiles/$name.perf" > "profiles/$name.calls.txt"
done
python3 json_profile_summary.py profiles dev.elide.truffledata.comparison.Comparison > profile-summary.json
