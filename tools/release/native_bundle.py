#!/usr/bin/env python3
"""Package checked Maven natives as deterministic per-triple SDK bundles."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import tarfile

from maven_repo import MUSL_MODULES, TRIPLES as PLATFORMS

LIBRARIES = {'csv-native': 'csv'}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def glibc_floor(readobj, shared):
    info = subprocess.check_output([str(readobj), '--version-info', str(shared)], text=True)
    versions = {tuple(map(int, v.split('.'))) for v in re.findall(r'\bGLIBC_(\d+(?:\.\d+)+)\b', info)}
    if not versions:
        raise ValueError('No versioned glibc dependency in ' + str(shared))
    return '.'.join(map(str, max(versions)))


def bundle(repository, version, module, classifier, header, llvm, output, revision, epoch, dirty=False):
    """Read already-gated artifacts; retain their exact bytes, with checksums and link metadata."""
    if module not in LIBRARIES or classifier not in PLATFORMS:
        raise ValueError('Unsupported native module/platform')
    if not re.fullmatch(r'[0-9a-f]{40}', revision):
        raise ValueError('A full source commit is required')
    musl = classifier.endswith('-musl')
    if musl and module not in MUSL_MODULES:
        raise ValueError('No musl archive for ' + module)
    artifact = 'truffledata-' + module
    name = LIBRARIES[module]
    prefix = repository / 'dev/elide' / artifact / version / (artifact + '-' + version)
    mac = classifier.startswith('macos-')
    shared = None if musl else Path(str(prefix) + '-' + classifier + ('.dylib' if mac else '.so'))
    static = Path(str(prefix) + '-' + classifier + '.a')
    header_bytes = header.read_bytes()
    fingerprints = set(re.findall(rb'\b[0-9a-f]{64}\b', header_bytes))
    if len(fingerprints) != 1:
        raise ValueError('Header must contain exactly one ABI fingerprint: ' + str(header))
    fingerprint = fingerprints.pop()
    files = {
        'lib/libtruffledata_' + name + '.a': static.read_bytes(),
        'include/truffledata_' + name + '.h': header_bytes,
    }
    if shared:
        files['lib/libtruffledata_' + name + ('.dylib' if mac else '.so')] = shared.read_bytes()
    for license_name in ('LICENSE', 'LICENSE-MIT', 'LICENSE-APACHE'):
        files[license_name] = (Path(__file__).resolve().parents[2] / license_name).read_bytes()
    if module == 'csv-native':
        for notice in sorted((Path(__file__).resolve().parent / 'licenses').iterdir()):
            files['licenses/' + notice.name] = notice.read_bytes()
    if mac:
        files['lib/libtruffledata_' + name + '-bitcode.a'] = Path(str(prefix) + '-' + classifier + '-bitcode.a').read_bytes()
    for payload in (static, shared) if shared else (static,):
        if fingerprint not in payload.read_bytes():
            raise ValueError('Header/native ABI mismatch: ' + str(payload))
    compiler = subprocess.check_output([str(llvm / 'clang'), '--version'], text=True).splitlines()[0]
    metadata = {
        'schemaVersion': 1, 'module': artifact, 'version': version,
        'classifier': classifier, 'targetTriple': PLATFORMS[classifier],
        'abiFingerprint': fingerprint.decode(), 'compiler': compiler, 'license': 'MIT OR Apache-2.0',
        'linkLibraries': ['c++'] if mac else ['c++', 'c++abi', 'dl', 'pthread', 'm'],
        'minimumRuntime': ({'macos': '12.0'} if mac else {'musl': '1.2.5'} if musl else {'glibc': glibc_floor(llvm / 'llvm-readobj', shared)}),
        'minimumRuntimeBasis': 'deployment target' if mac else 'tested static-link baseline from elide-toolchain 2026.11.5' if musl else 'highest versioned dependency of the matching shared library',
        'staticFormat': 'machine-code with separate LLVM 23 ThinLTO archive' if mac else 'ELF FatLTO (LLVM 23)',
        'ltoLinker': 'ld64.lld' if mac else 'ld.lld',
        'dependencies': [{'name': 'simdutf', 'version': '7.7.1', 'sourcePackage': 'simdutf-0.7.0', 'source': 'https://github.com/simdutf/simdutf/tree/v7.7.1'}] if module == 'csv-native' else [],
        'source': {'repository': 'https://github.com/elide-dev/truffledata', 'commit': revision, 'dirty': dirty},
        'files': {path: {'size': len(data), 'sha256': digest(data)} for path, data in sorted(files.items())},
    }
    # The Maven sidecar uses Maven filenames; the bundle keeps conventional lib/include paths.
    maven_files = [static] + ([shared] if shared else [])
    if mac:
        maven_files.append(Path(str(prefix) + '-' + classifier + '-bitcode.a'))
    maven_files.append(header)
    maven_metadata = dict(metadata, files={p.name: {'size': p.stat().st_size, 'sha256': digest(p.read_bytes())} for p in maven_files})
    sidecar = Path(str(prefix) + '-' + classifier + '.json')
    sidecar.write_text(json.dumps(maven_metadata, indent=2, sort_keys=True) + '\n')
    from maven_repo import checksums
    checksums(sidecar)
    files['manifest.json'] = (json.dumps(metadata, indent=2, sort_keys=True) + '\n').encode()
    for path, data in list(files.items()):
        files[path + '.sha256'] = (digest(data) + '  ' + Path(path).name + '\n').encode()
    output.mkdir(parents=True, exist_ok=True)
    destination = output / (artifact + '-' + version + '-' + PLATFORMS[classifier] + '.tar.zst')
    temporary = destination.with_suffix('')
    try:
        with tarfile.open(temporary, 'w', format=tarfile.PAX_FORMAT) as archive:
            for path, data in sorted(files.items()):
                entry = tarfile.TarInfo(path)
                entry.size = len(data)
                entry.mode = 0o644
                entry.mtime = epoch
                archive.addfile(entry, io.BytesIO(data))
        subprocess.run(['zstd', '-q', '-19', '-T1', '-f', str(temporary), '-o', str(destination)], check=True)
    finally:
        temporary.unlink(missing_ok=True)
    Path(str(destination) + '.sha256').write_text(digest(destination.read_bytes()) + '  ' + destination.name + '\n')
    return destination


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repository', type=Path, required=True)
    parser.add_argument('--version', required=True)
    parser.add_argument('--module', choices=LIBRARIES, required=True)
    parser.add_argument('--header', type=Path, required=True)
    parser.add_argument('--llvm-bin', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--revision', required=True)
    parser.add_argument('--source-date-epoch', type=int, required=True)
    parser.add_argument('--dirty', action='store_true', help='mark preview artifacts built from uncommitted sources')
    args = parser.parse_args()
    from maven_repo import validate_version
    validate_version(args.version)
    for classifier in PLATFORMS:
        if classifier.endswith('-musl') and args.module not in MUSL_MODULES:
            continue
        print(bundle(args.repository, args.version, args.module, classifier, args.header,
                     args.llvm_bin, args.output, args.revision, args.source_date_epoch, args.dirty))


if __name__ == '__main__':
    main()
