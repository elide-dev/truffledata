"""Deterministic YAML probe fixtures, mirroring benchmarks/comparison/JsonFixture.document(i)."""
import json, os, sys

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)


def doc(i):
    tags = [f"t{k}" for k in range(1 + i % 4)]
    scores = [i + k for k in range(3 + (i & 1))]
    note = (f'line "{i}" with \\ escapes\n\té and \U0001F600 tail' if i & 1
            else f"plain note for item {i} padded beyond the inline text limit")
    members = [("id", 1000 + 7 * i), ("name", f"item-{i}"), ("price", i + 0.25), ("active", i % 3 == 0),
               ("tags", tags), ("dims", {"w": 3 * i, "h": i + 0.5, "unit": "cm"}), ("scores", scores),
               ("note", note), ("parent", None)]
    if i & 1:
        members.reverse()
    return members


def scalar(v):
    if v is None:
        return "null"
    if v is True:
        return "true"
    if v is False:
        return "false"
    if isinstance(v, (int, float)):
        return repr(v)
    if isinstance(v, str):
        if any(c in v for c in '"\\\n\t') or not v.isascii():
            return json.dumps(v, ensure_ascii=False)
        return v
    raise TypeError(v)


def block(members, indent):
    pad = " " * indent
    out = []
    for k, v in members:
        if isinstance(v, dict):
            out.append(f"{pad}{k}:")
            for kk, vv in v.items():
                out.append(f"{pad}  {kk}: {scalar(vv)}")
        elif isinstance(v, list):
            if k == "tags":
                out.append(f"{pad}{k}:")
                for t in v:
                    out.append(f"{pad}  - {scalar(t)}")
            else:
                out.append(f"{pad}{k}: [{', '.join(scalar(x) for x in v)}]")
        else:
            out.append(f"{pad}{k}: {scalar(v)}")
    return out


def to_obj(members):
    return {k: v for k, v in members}


N = 1000
# 1. Block sequence of 1,000 records (the JSON fixture's data in idiomatic block YAML).
lines = ["# probe fixture: 1,000 records, block style"]
for i in range(N):
    b = block(doc(i), 2)
    lines.append("- " + b[0][2:])
    lines.extend(b[1:])
open(os.path.join(OUT, "records-block.yaml"), "w").write("\n".join(lines) + "\n")

# 2. The same data as one JSON array (JSON is a YAML 1.2 subset): flow style throughout.
arr = [to_obj(doc(i)) for i in range(N)]
open(os.path.join(OUT, "records-flow.yaml"), "w").write(json.dumps(arr, ensure_ascii=False, separators=(",", ":")))

# 3. Multi-document stream: 1,000 documents separated by '---'.
lines = []
for i in range(N):
    lines.append("---")
    lines.extend(block(doc(i), 0))
open(os.path.join(OUT, "records-stream.yaml"), "w").write("\n".join(lines) + "\n")

# 4. A realistic config document (CI workflow + deployment), comments and literal blocks, ~4 KB.
cfg = []
cfg.append("# Build and deploy configuration\nname: ci\non:\n  push:\n    branches: [main, 'release/**']\n  pull_request: {}\n")
cfg.append("env:\n  RUST_LOG: info  # verbosity\n  TIMEOUT: 30\n  RATIO: 0.75\n  ENABLED: true\n  EMPTY: ~\n")
cfg.append("jobs:\n")
for j in range(8):
    cfg.append(f"  job-{j}:\n    runs-on: ubuntu-24.04\n    timeout-minutes: {10 + j}\n    strategy:\n      matrix:\n"
               f"        os: [linux, macos]\n        version: [21, 25]\n    steps:\n"
               f"      - uses: actions/checkout@v4\n      - name: Build step {j}\n        run: |\n"
               f"          bazel build //... --config=ci\n          echo \"done {j}\"\n"
               f"      - name: Test\n        run: >-\n          bazel test //... --test_output=errors\n          --jobs=8\n"
               f"        env:\n          SHARD: '{j}'\n          RETRIES: 3\n")
open(os.path.join(OUT, "config.yaml"), "w").write("".join(cfg))

# 5. Text heavy: 200 entries with ~1 KB literal blocks, folded blocks and quoted strings.
para = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore."
lines = []
for i in range(200):
    lines.append(f"entry-{i}:")
    lines.append("  body: |")
    for k in range(10):
        lines.append(f"    {para} {i}.{k}")
    lines.append("  summary: >")
    for k in range(3):
        lines.append(f"    {para} folded {k}")
    lines.append(f"  quoted: \"{para} \\u00e9\\n{i}\"")
    lines.append(f"  single: '{para} it''s {i}'")
open(os.path.join(OUT, "text-heavy.yaml"), "w").write("\n".join(lines) + "\n")

# Config repeated as a stream for throughput (and as a single small document for per-doc cost).
c = open(os.path.join(OUT, "config.yaml")).read()
open(os.path.join(OUT, "config-stream.yaml"), "w").write(("---\n" + c) * 64)

for f in sorted(os.listdir(OUT)):
    print(f, os.path.getsize(os.path.join(OUT, f)))
