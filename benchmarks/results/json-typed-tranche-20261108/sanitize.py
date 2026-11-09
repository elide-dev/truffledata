"""Reproduce the local Arm64 ASan+UBSan qualification with every C++ dependency instrumented."""
from pathlib import Path
import hashlib
import json
import os
import subprocess

root = Path(__file__).resolve().parents[3]
os.chdir(root)
out = root / 'build/json-typed-tranche-sanitize'
out.mkdir(parents=True, exist_ok=True)
vendor = root / 'json/native/crate/vendor'
compiler = '/usr/bin/clang++'
flags = ['-std=c++17', '-O1', '-g', '-fsanitize=address,undefined', '-fno-sanitize-recover=all',
         '-fno-omit-frame-pointer', '-Wno-enum-compare', '-I.', '-I' + str(vendor),
         '-I' + str(vendor / 'myna/include'), '-I' + str(vendor / 'simdjson'), '-I' + str(vendor / 'simdutf')]
metadata = {'compiler': subprocess.check_output([compiler, '--version'], text=True), 'flags': flags,
            'source_sha256': {}}
objects = []
for name, source in [('simdjson', vendor / 'simdjson/simdjson.cpp'),
                     ('simdutf', vendor / 'simdutf/simdutf.cpp'), ('api', root / 'json/native/api.cc')]:
    obj = out / (name + '.o')
    subprocess.run([compiler, *flags, '-c', str(source), '-o', str(obj)], check=True)
    objects.append(str(obj))
    metadata['source_sha256'][str(source.relative_to(root))] = hashlib.sha256(source.read_bytes()).hexdigest()
for test in ['typed_stage2_test', 'typed_test', 'api_test', 'stage2_test', 'shape_fuzz_test']:
    source = root / 'json/native' / (test + '.cc')
    binary = out / test
    subprocess.run([compiler, *flags, str(source), *objects, '-o', str(binary)], check=True)
    print('RUN', test, flush=True)
    subprocess.run([str(binary)], check=True)
    metadata['source_sha256'][str(source.relative_to(root))] = hashlib.sha256(source.read_bytes()).hexdigest()
for file in ['json/native/typed.h', 'json/native/decoder.h', 'json/native/stage2.h', 'tape/native/ctape.h']:
    metadata['source_sha256'][file] = hashlib.sha256((root / file).read_bytes()).hexdigest()
Path(__file__).with_name('sanitizer-metadata.json').write_text(json.dumps(metadata, indent=2) + '\n')
print('All five Arm64 ASan+UBSan tests passed', flush=True)
