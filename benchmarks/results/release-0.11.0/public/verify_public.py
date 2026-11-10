"""Compare every staged 0.11.0 Maven file with its anonymous public download; keep the downloads as a mirror."""
import hashlib, json, sys, urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
repo, mirror, version, out = Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3], Path(sys.argv[4])
public = 'https://maven.elide.dev/'
files = []
for module in sorted((repo / 'dev/elide').iterdir()):
    files += sorted(p for p in (module / version).iterdir() if p.is_file())
    files += sorted(p for p in module.iterdir() if p.is_file() and p.name.startswith('maven-metadata.xml'))
def check(path):
    rel = path.relative_to(repo).as_posix()
    request = urllib.request.Request(public + rel, headers={'User-Agent': 'release-verifier', 'Cache-Control': 'no-cache'})
    data = urllib.request.urlopen(request, timeout=300).read()
    target = mirror / rel
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    return rel, hashlib.sha256(data).hexdigest(), hashlib.sha256(path.read_bytes()).hexdigest(), len(data)
with ThreadPoolExecutor(12) as pool:
    rows = list(pool.map(check, files))
bad = [r[0] for r in rows if r[1] != r[2]]
kinds = {'signature': 0, 'checksum': 0, 'metadata': 0, 'payload': 0}
for rel, *_ in rows:
    name = rel.rsplit('/', 1)[1]
    kind = 'metadata' if name.startswith('maven-metadata.xml') else 'signature' if name.endswith('.asc') else 'checksum' if name.rsplit('.', 1)[1] in ('md5', 'sha1', 'sha256', 'sha512') else 'payload'
    kinds[kind] += 1
out.write_text(json.dumps({'version': version, 'public': public, 'files': len(rows), 'kinds': kinds, 'mismatches': bad,
                           'status': 'passed' if not bad else 'failed',
                           'sha256': {r[0]: r[1] for r in rows}}, indent=1, sort_keys=True) + '\n')
print(len(rows), kinds, 'mismatches:', bad)
sys.exit(1 if bad else 0)
