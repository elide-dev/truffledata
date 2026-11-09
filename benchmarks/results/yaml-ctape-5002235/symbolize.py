from pathlib import Path
from collections import Counter
import argparse,json,re,subprocess
p=argparse.ArgumentParser();p.add_argument('binary',type=Path);p.add_argument('profiles',type=Path);p.add_argument('log',type=Path);p.add_argument('output',type=Path);args=p.parse_args()
files=sorted(args.profiles.glob('profile-*.txt'))
addresses=sorted({a for f in files for line in f.read_text().splitlines() for a in line.split()})
if not addresses:raise SystemExit('No captured stacks')
result=subprocess.run(['/opt/homebrew/opt/llvm/bin/llvm-symbolizer','--obj='+str(args.binary),'--inlining','--output-style=JSON'],input=''.join('0x'+a+'\n' for a in addresses),text=True,capture_output=True,check=True)
symbols={int(x['Address'],16):x['Symbol'] for x in map(json.loads,result.stdout.splitlines())}
runs={name:int(n) for name,n in re.findall(r'profile (profile-\S+): (\d+) runs, \d+ samples',args.log.read_text())}
args.output.mkdir(parents=True,exist_ok=True)
(args.output/'symbols.json').write_text(json.dumps({hex(a):s for a,s in symbols.items()}))
summary=[]
for file in files:
 leaf=Counter();inclusive=Counter();lines=Counter();folded=Counter();stacks=[line.split() for line in file.read_text().splitlines()]
 for stack in stacks:
  entries=symbols.get(int(stack[0],16),[])
  if entries:
   leaf[entries[0]['FunctionName'] or '?']+=1
   lines[(entries[0]['FileName'],entries[0]['Line'],entries[0]['FunctionName'])]+=1
  else:leaf['?']+=1
  names=[s['FunctionName'] for a in stack for s in symbols.get(int(a,16),[]) if s['FunctionName']]
  inclusive.update(set(names));folded[';'.join(reversed(names))]+=1
 count=runs.get(file.name)
 summary.append(f'{file.name}: {len(stacks)} samples, {count} decodes')
 for label,counts in [('leaf',leaf),('inclusive',inclusive),('source',lines)]:
  summary.append(label)
  for name,n in counts.most_common(25):
   normalized=f'{n*1000/count:.3f} samples/1000decodes' if count else 'runs unavailable'
   summary.append(f'{n:5d} {n*100/len(stacks):6.2f}% {normalized} {name}')
 (args.output/(file.stem+'.folded')).write_text(''.join(f'{name} {n}\n' for name,n in folded.most_common()))
(args.output/'summary.txt').write_text('\n'.join(summary)+'\n');print('\n'.join(summary))
