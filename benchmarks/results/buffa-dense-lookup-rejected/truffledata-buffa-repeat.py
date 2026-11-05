import hashlib,itertools,json,os,random,statistics,subprocess
from pathlib import Path
import compare
root=Path.cwd()
binaries={'baseline':root/'baseline','candidate':root/'candidate'}
cells=[('proto-mixed','buffa','ingest'),('proto-mixed','buffa','ingest-generated'),('proto-mixed-full','buffa','ingest'),('proto-scalar','buffa','ingest-generated'),('proto-mixed','generated-owned','ingest'),('proto-mixed','stock','ingest')]
jobs=list(itertools.product(binaries,cells,range(5),('time','memory')))
random.Random(int(os.environ.get("BENCH_SEED", "20261105"))).shuffle(jobs)
rows=[]
for variant,cell,fork,mode in jobs:
 p=subprocess.run([str(binaries[variant]),*cell,mode],capture_output=True,text=True,check=True,timeout=90)
 records=[json.loads(line) for line in p.stdout.splitlines() if line.startswith('{')]
 assert len(records)==1
 row=records[0]
 metric='ns_per_row' if mode=='time' else 'allocated_bytes_per_row'
 compare.validate_row(row,cell,metric)
 row.update(variant=variant,fork=fork)
 rows.append(row)
 print(variant,*cell,fork,mode,flush=True)
report={'binary_sha256':{v:hashlib.sha256(p.read_bytes()).hexdigest() for v,p in binaries.items()},'cpu_affinity':sorted(os.sched_getaffinity(0)),'measurements':rows}
Path('paired.json').write_text(json.dumps(report,indent=2)+'\n')
lines=['# Paired dense field lookup experiment (rejected)','','Five fresh forks per variant/metric; median of fork medians; ranges are fork medians. Java heap excludes native memory.','','| Workload | Engine | Stage | Variant | ns/row (range) | Java bytes/row |','|---|---|---|---|---:|---:|']
for cell in cells:
 for variant in binaries:
  selected=[r for r in rows if (r['workload'],r['engine'],r['stage'])==cell and r['variant']==variant]
  times=[statistics.median(r['ns_per_row']) for r in selected if 'ns_per_row' in r]
  allocations=[statistics.median(r['allocated_bytes_per_row']) for r in selected if 'allocated_bytes_per_row' in r]
  lines.append('| '+' | '.join((*cell,variant,f'{statistics.median(times):.3f} ({min(times):.3f}–{max(times):.3f})',f'{statistics.median(allocations):.3f}'))+' |')
Path('paired.md').write_text('\n'.join(lines)+'\n')
