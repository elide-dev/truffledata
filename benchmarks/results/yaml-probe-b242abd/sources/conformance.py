"""yaml-test-suite (data-2022-01-17) event conformance for each probe parser.

A case passes when:
  * it has an `error` file and the parser reports an error (non-zero exit / exception), or
  * it has no `error` file, the parser succeeds, and its events equal test.event after
    normalization: flow markers (`{}`/`[]`), explicit document markers (`---`/`...`) and anchor
    names (renumbered by first appearance) are dropped, as in saphyr's and yaml-rust2's own harnesses.
`strict` additionally requires the flow and document markers (anchor names stay renumbered).
"""
import json, os, re, subprocess, sys

S = sys.argv[1]
SUITE = os.path.join(S, "dl/yaml-test-suite/yaml-test-suite-data-2022-01-17")
OUT = sys.argv[2]
os.makedirs(OUT, exist_ok=True)

cases = []
for root, dirs, files in os.walk(SUITE):
    if "in.yaml" in files:
        rel = os.path.relpath(root, SUITE)
        if rel.startswith("name") or rel.startswith("tags"):
            continue
        cases.append((rel, root))
cases.sort()

NATIVE = {
    "rapidyaml-0.16.0": [os.path.join(S, "build/cprobe"), "events"],
    "rapidyaml-0.16.0-tabs": [os.path.join(S, "build/cprobe-tabs"), "events"],  # -DRYML_WITH_TAB_TOKENS
    # -DRYML_WITH_TAB_TOKENS plus ParserOptions::resolve_tags(true).resolve_tags_all(true)
    "rapidyaml-0.16.0-tabs-tags": ["env", "RYML_RESOLVE_TAGS=1", os.path.join(S, "build/cprobe-tabs"), "events"],
    "libyaml-0.2.5": [os.path.join(S, "build/libyaml/run-parser-test-suite")],
    "libfyaml-0.9.6": [os.path.join(S, "build/libfyaml/fy-tool"), "--testsuite"],
    "saphyr-parser-0.3.3": [os.path.join(S, "rprobe/target/release/rprobe"), "events", "saphyr"],
    "yaml-rust2-0.13.0": [os.path.join(S, "rprobe/target/release/rprobe"), "events", "yamlrust2"],
    "unsafe-libyaml-0.2.11": [os.path.join(S, "rprobe/target/release/ulyaml-events")],
}


def run_native(cmd, path):
    try:
        p = subprocess.run(cmd + [path], capture_output=True, timeout=20)
    except subprocess.TimeoutExpired:
        return None, "timeout"
    if p.returncode != 0:
        return None, f"exit {p.returncode}: {p.stderr.decode(errors='replace')[:200]}"
    return p.stdout.decode("utf-8", errors="replace"), None


def run_java():
    lst = os.path.join(OUT, "java-cases.tsv")
    with open(lst, "w") as f:
        for rel, root in cases:
            f.write(f"{rel}\t{os.path.join(root, 'in.yaml')}\n")
    jout = os.path.join(OUT, "java")
    subprocess.run(["bash", os.path.join(S, "tools/jprobe.sh"), "events-batch", lst, jout], check=True)
    res = {}
    for rel, _ in cases:
        n = rel.replace("/", "_")
        o, e = os.path.join(jout, n + ".out"), os.path.join(jout, n + ".err")
        if os.path.exists(o):
            res[rel] = (open(o, encoding="utf-8").read(), None)
        else:
            res[rel] = (None, open(e, encoding="utf-8").read()[:200])
    return res


def normalize(text, strict):
    lines = [l.rstrip() for l in text.strip("\n").split("\n") if l.strip()]
    anchors = {}
    out = []
    for l in lines:
        if not strict:
            l = re.sub(r"^(\+MAP|\+SEQ) (\{\}|\[\])", r"\1", l)
            l = re.sub(r"^\+DOC ---$", "+DOC", l)
            l = re.sub(r"^-DOC \.\.\.$", "-DOC", l)
        # renumber anchors and aliases by first appearance
        def anc(m):
            name = m.group(2)
            if m.group(1) == "&" or name not in anchors:
                anchors[name] = anchors.get(name) if m.group(1) == "*" else len(anchors) + 1
            return f"{m.group(1)}A{anchors[name]}"
        if l.startswith("=VAL") or l.startswith("+MAP") or l.startswith("+SEQ") or l.startswith("=ALI"):
            head, sep, val = (l.partition(" :") if " :" in l else (l, "", ""))
            # only rewrite anchors in the event head (before the scalar value)
            m = re.match(r"^(=VAL|\+MAP|\+SEQ|=ALI)((?: (?:\{\}|\[\]))?)((?: [&*][^ ]+)?)((?: <[^>]*>)?)(.*)$", l)
            if m:
                a = m.group(3)
                if a:
                    a = re.sub(r"([&*])(\S+)", anc, a)
                l = m.group(1) + m.group(2) + a + m.group(4) + m.group(5)
        out.append(l)
    return out


results = {}
for name, cmd in NATIVE.items():
    rows = {}
    for rel, root in cases:
        rows[rel] = run_native(cmd, os.path.join(root, "in.yaml"))
    results[name] = rows
results["snakeyaml-engine-3.2"] = run_java()

summary = {}
details = {}
for name, rows in results.items():
    ok = strict_ok = 0
    valid_ok = err_ok = 0
    fails = []
    for rel, root in cases:
        out, err = rows[rel]
        expect_error = os.path.exists(os.path.join(root, "error"))
        if expect_error:
            passed = err is not None
            sp = passed
            if passed:
                err_ok += 1
        else:
            exp = open(os.path.join(root, "test.event"), encoding="utf-8").read()
            passed = err is None and normalize(out, False) == normalize(exp, False)
            sp = err is None and normalize(out, True) == normalize(exp, True)
            if passed:
                valid_ok += 1
        ok += passed
        strict_ok += sp
        if not passed:
            fails.append(f"{rel}\t{'expected error' if expect_error else ('error: ' + ' '.join(err.split()) if err else 'events differ')}")
    n_err = sum(1 for _, r in cases if os.path.exists(os.path.join(r, "error")))
    summary[name] = dict(total=len(cases), passed=ok, passed_strict_markers=strict_ok,
                         valid=len(cases) - n_err, valid_passed=valid_ok, error_cases=n_err, error_detected=err_ok)
    details[name] = fails
    with open(os.path.join(OUT, f"failures-{name}.tsv"), "w") as f:
        f.write("\n".join(fails) + "\n")

with open(os.path.join(OUT, "summary.json"), "w") as f:
    json.dump(summary, f, indent=2)
print(f"{'parser':<24} {'pass':>9} {'valid ok':>12} {'errors caught':>14} {'strict markers':>15}")
for name, s in summary.items():
    print(f"{name:<24} {s['passed']:>4}/{s['total']:<4} {s['valid_passed']:>6}/{s['valid']:<5} "
          f"{s['error_detected']:>7}/{s['error_cases']:<6} {s['passed_strict_markers']:>8}/{s['total']}")
