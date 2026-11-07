"""Long-line scaling fixtures: the same flow JSON as one line, one record per line, and truncated sizes."""
import json, os, sys
out = sys.argv[1]
arr = json.load(open(os.path.join(out, "records-flow.yaml")))
lines = "[\n" + ",\n".join(json.dumps(r, ensure_ascii=False, separators=(",", ":")) for r in arr) + "\n]\n"
open(os.path.join(out, "records-flow-lines.yaml"), "w").write(lines)
for n in (25, 50, 100, 200):
    open(os.path.join(out, f"oneline-{n}.yaml"), "w").write(json.dumps(arr[:n], ensure_ascii=False, separators=(",", ":")))
