"""Classify rapidyaml failures: how many pass once `!!x` tags expand to `tag:yaml.org,2002:x`
(the secondary-handle default every YAML 1.2 processor applies; our own event handler would do it)."""
import importlib.util, os, re, subprocess, sys

S = sys.argv[1]
SUITE = os.path.join(S, "dl/yaml-test-suite/yaml-test-suite-data-2022-01-17")
spec = importlib.util.spec_from_file_location("conf", os.path.join(S, "probe/conformance_norm.py"))
conf = importlib.util.module_from_spec(spec)
spec.loader.exec_module(conf)
fails = [l.split("\t", 1) for l in open(os.path.join(S, "conformance/failures-" + (sys.argv[2] if len(sys.argv) > 2 else "rapidyaml-0.16.0") + ".tsv")).read().splitlines()
         if re.match(r"^[0-9A-Z]{4}(/[0-9]+)?\t", l)]
fixed, still = [], []
for rel, why in fails:
    if why != "events differ":
        still.append((rel, why))
        continue
    out = subprocess.run([os.path.join(S, sys.argv[3] if len(sys.argv) > 3 else "build/cprobe"), "events", os.path.join(SUITE, rel, "in.yaml")],
                         capture_output=True, text=True).stdout
    out = re.sub(r"<!!([^>]*)>", r"<tag:yaml.org,2002:\1>", out)
    exp = open(os.path.join(SUITE, rel, "test.event"), encoding="utf-8").read()
    if conf.normalize(out, False) == conf.normalize(exp, False):
        fixed.append(rel)
    else:
        still.append((rel, "events differ"))
print("tag-handle only:", len(fixed), " ".join(fixed))
print("remaining:", len(still))
for rel, why in still:
    name = open(os.path.join(SUITE, rel, "===")).read().strip()
    print(f"  {rel}\t{why[:60]}\t{name}")
