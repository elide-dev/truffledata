#!/usr/bin/env python3
"""Release gate for the prebuilt natives of a staged Maven repository (docs/PUBLISHING.md, Prebuilt natives).

For every published native module and every platform classifier it requires each attachment and checks it with the
toolchain's LLVM tools against the member manifest its release_archive action wrote:

  linux-*  -<c>.a          every member a fat object: machine code plus a ThinLTO `.llvm.lto` section; only Rust's
                           compiler_builtins and the compiler-rt objects it bundles stay machine code (R1 ruling)
           -<c>.so         ELF of the classifier's machine; needs only libc, libm, libpthread, libdl, librt,
                           ld-linux and libgcc_s, and every undefined symbol is versioned by them
  macos-*  -<c>.a          every member Mach-O machine code without bitcode
           -<c>-bitcode.a  every member ThinLTO bitcode (same exemptions, as machine code)
           -<c>.dylib      arm64 MH_DYLIB, install name @rpath/..., links only libSystem and the OS's libc++
                           and binds every undefined symbol to them
Members that compiled to no functions (an AVX kernel on AArch64, empty std crates) count as empty on every platform.

usage: check_natives.py --llvm-bin DIR --version VERSION --repository REPO --manifests DIR [DIR...]
"""
import argparse
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import zipfile

from maven_repo import CLASSIFIERS, NATIVE_MODULES

# Rust's compiler_builtins is never LTO'd by rustc and carries no bitcode; the compiler-rt C objects it bundles are
# named <crate hash>-<source>.o and define only compiler-rt's __-prefixed builtins and C11 atomic_* functions.
COMPILER_BUILTINS = re.compile(r'compiler_builtins')
BUNDLED = re.compile(r'^[0-9a-f]{16}-[^/]+\.o$')

LINUX_NEEDED = re.compile(r'^(libc\.so\.6|libm\.so\.6|libpthread\.so\.0|libdl\.so\.2|librt\.so\.1|'
                          r'ld-linux-(x86-64|aarch64)\.so\.[12]|libgcc_s\.so\.1)$')
LINUX_VERSIONS = re.compile(r'@(GLIBC|GCC)_[0-9.]+$')
# Weak references to libc that are tested at run time and resolve to null when absent: the C runtime's and unwinder's,
# and Rust std's probes for functions newer than its glibc baseline (its weak! macro).
LINUX_WEAK = {'__gmon_start__', '_ITM_registerTMCloneTable', '_ITM_deregisterTMCloneTable',
              '__cxa_finalize', '__deregister_frame_info', '__register_frame_info', '_Jv_RegisterClasses',
              '__cxa_thread_atexit_impl', 'gettid', 'statx', 'getrandom', 'copy_file_range', 'memfd_create',
              'pidfd_getpid', 'pidfd_spawnp', 'posix_spawn_file_actions_addchdir_np', 'renameat2', 'splice'}
# libSystem and the OS's libc++ (the macOS toolchain links C++ against the SDK's libc++, which every macOS ships).
MACOS_DYLIBS = re.compile(r'^/usr/lib/(libSystem\.B|libc\+\+\.1)\.dylib$')
MACOS_ORIGINS = {'libSystem', 'libc++'}
MACHINES = {'linux-x86_64': 'EM_X86_64', 'linux-aarch64': 'EM_AARCH64'}
FINGERPRINT = {'protobuf-buffa': 'td_buffa_fingerprint', 'json': 'td_json_fingerprint', 'csv-native': 'td_csv_fingerprint'}
# The generated bindings (published once, from the linux-x86_64 build) and the ABI fingerprint they verify at run time.
BINDINGS = {
    'protobuf-buffa': ('protobuf-buffa-bindings', 'dev/elide/truffledata/buffa/generated/Buffa.class'),
    'json': ('json-bindings', 'dev/elide/truffledata/json/generated/Json.class'),
    'csv-native': ('csv-bindings', 'dev/elide/truffledata/csv/kernel/generated/CsvNative.class'),
}
HEX64 = re.compile(rb'(?<![0-9a-f])[0-9a-f]{64}(?![0-9a-f])')


class Checker:
    def __init__(self, bin_dir):
        self.bin = Path(bin_dir)
        self.errors = []
        self.summary = []

    def tool(self, name, *args, binary=False):
        result = subprocess.run([str(self.bin / name), *map(str, args)], capture_output=True, check=False)
        if result.returncode != 0:
            raise RuntimeError('%s %s: %s' % (name, ' '.join(map(str, args)), result.stderr.decode(errors='replace')))
        return result.stdout if binary else result.stdout.decode(errors='replace')

    def fail(self, where, message):
        self.errors.append('%s: %s' % (where, message))

    def sections(self, path):
        """[(name, size, executable)] from llvm-readobj (ELF and Mach-O)."""
        out = self.tool('llvm-readobj', '--sections', path)
        sections = []
        for block in re.split(r'\n\s*Section \{', out)[1:]:
            name = re.search(r'Name: (\S+)', block)
            size = re.search(r'Size: (0x[0-9A-Fa-f]+|\d+)', block)
            executable = 'SHF_EXECINSTR' in block or 'PureInstructions' in block or 'SomeInstructions' in block
            sections.append((name.group(1) if name else '', int(size.group(1), 0) if size else 0, executable))
        return sections

    def thinlto(self, bitcode):
        """True when the bitcode file is a module with a ThinLTO summary."""
        return 'GLOBALVAL_SUMMARY_BLOCK' in self.tool('llvm-bcanalyzer', bitcode)

    def exempt(self, name, path):
        if COMPILER_BUILTINS.search(name):
            return True
        if BUNDLED.match(name):
            defined = [line.split()[-1] for line in self.tool('llvm-nm', '--defined-only', '--extern-only', path).splitlines()
                       if line.strip()]
            macho = path.read_bytes()[:4] == b'\xcf\xfa\xed\xfe'
            names = [symbol[1:] if macho else symbol for symbol in defined]
            # Mach-O's leading underscore stripped: compiler-rt's names start with _ (__addsf3, __aarch64_ldset8_rel)
            # or are C11 atomic_*.
            return bool(names) and all(name.startswith(('_' if macho else '__', 'atomic_')) for name in names)
        return False

    def functions(self, path):
        """Defined external function symbols."""
        return [line.split()[-1] for line in self.tool('llvm-nm', '--defined-only', '--extern-only', path).splitlines()
                if len(line.split()) >= 3 and line.split()[-2] in ('T', 't')]

    def kind(self, path, work):
        """(kind, detail): fat, machine-code, bitcode, empty (a translation unit that compiled to no functions, such
        as an x86 kernel on AArch64), or a description of what is wrong."""
        data = path.read_bytes()
        if data[:4] in (b'BC\xc0\xde', b'\xde\xc0\x17\x0b'):
            return ('bitcode', 'ThinLTO' if self.thinlto(path) else 'no ThinLTO summary')
        sections = self.sections(path)
        code = any(executable and size > 0 for _, size, executable in sections)
        names = {name for name, _, _ in sections}
        if data[:4] == b'\x7fELF':
            if '.llvm.lto' in names:
                bc = work / (path.name + '.lto.bc')
                self.tool('llvm-objcopy', '--dump-section=.llvm.lto=%s' % bc, path, work / (path.name + '.copy'))
                summary = self.thinlto(bc)
                if code and summary:
                    return ('fat', 'ThinLTO')
                if not code and summary and not self.functions(path):
                    return ('empty', 'no functions')
                return ('broken', '.llvm.lto without %s' % ('machine code' if not code else 'a ThinLTO summary'))
        elif data[:4] == b'\xcf\xfa\xed\xfe':
            if '__bitcode' in names:
                return ('broken', 'Mach-O member still carries __LLVM,__bitcode')
        else:
            return ('unknown', 'neither ELF, Mach-O nor bitcode')
        if code:
            return ('machine-code', '')
        if not self.functions(path):
            return ('empty', 'no functions')
        return ('broken', 'functions without machine code')

    def archive(self, path, manifest, expected, where):
        """Check every member of the archive against its manifest; `expected` is the required kind of non-exempt members."""
        listed = [line.split('\t') for line in manifest.read_text().splitlines() if line]
        members = [line for line in self.tool('llvm-ar', 't', path).splitlines() if line]
        if [name for name, _ in listed] != members:
            self.fail(where, 'archive members differ from manifest %s' % manifest.name)
            return
        counts = {}
        with tempfile.TemporaryDirectory(prefix='check-natives.') as temporary:
            work = Path(temporary)
            subprocess.run([str(self.bin / 'llvm-ar'), 'x', str(path.resolve())], cwd=work, check=True)
            scratch = work / '.scratch'
            scratch.mkdir()
            for name, claimed in listed:
                member = work / name
                actual, detail = self.kind(member, scratch)
                if actual == 'empty':
                    # Nothing to link either way; the release_archive manifest records how it was built.
                    counts['empty'] = counts.get('empty', 0) + 1
                    continue
                if actual != claimed:
                    self.fail(where, '%s is %s%s, manifest says %s' % (name, actual, ' (%s)' % detail if detail else '', claimed))
                    continue
                if actual == expected:
                    if actual in ('fat', 'bitcode') and detail != 'ThinLTO':
                        self.fail(where, '%s: %s' % (name, detail))
                        continue
                    counts[actual] = counts.get(actual, 0) + 1
                elif actual == 'machine-code' and self.exempt(name, member):
                    counts['exempt machine-code'] = counts.get('exempt machine-code', 0) + 1
                else:
                    self.fail(where, '%s is %s; release members must be %s' % (name, actual, expected))
        self.summary.append('%s: %d members, %s' % (where, len(members), ', '.join('%d %s' % (n, k) for k, n in sorted(counts.items()))))

    def linux_shared(self, path, classifier, module, where):
        headers = self.tool('llvm-readobj', '--file-headers', path)
        if 'Type: SharedObject' not in headers or MACHINES[classifier] not in headers:
            self.fail(where, 'not a %s shared object' % MACHINES[classifier])
            return
        needed = re.findall(r'^\s*(\S+\.so[.\d]*)\s*$', self.tool('llvm-readobj', '--needed-libs', path), re.M)
        for library in needed:
            if not LINUX_NEEDED.match(library):
                self.fail(where, 'needs %s, outside the platform allowlist' % library)
        bad = []
        for line in self.tool('llvm-nm', '-D', '--undefined-only', path).splitlines():
            fields = line.split()
            if not fields:
                continue
            kind, symbol = fields[-2], fields[-1]
            if LINUX_VERSIONS.search(symbol):
                continue
            if kind in ('w', 'v') and symbol.split('@')[0] in LINUX_WEAK:
                continue
            bad.append(symbol)
        if bad:
            self.fail(where, '%d undefined symbols outside libc/libm/libpthread/libdl/librt/libgcc_s: %s' % (len(bad), ', '.join(bad[:20])))
        self.exports(path, module, where, ['-D'])
        self.summary.append('%s: needs %s; %s' % (where, ' '.join(needed), 'undefined symbols all glibc/libgcc_s-versioned' if not bad else 'FAILED'))

    def macos_shared(self, path, module, where):
        headers = self.tool('llvm-readobj', '--file-headers', path)
        if 'FileType: DynamicLibrary' not in headers or 'CpuType: Arm64' not in headers:
            self.fail(where, 'not an arm64 MH_DYLIB')
            return
        install = self.tool('llvm-objdump', '--macho', '--dylib-id', '--no-leading-headers', path).split()
        install = install[-1] if install else ''
        if not install.startswith('@rpath/'):
            self.fail(where, 'install name %r is not @rpath/...' % install)
        loads = [library for library in re.findall(r'^\s*(\S+) \(compatibility version', self.tool('llvm-objdump', '--macho', '--dylibs-used', '--no-leading-headers', path), re.M)
                 if library != install]
        for library in loads:
            if not MACOS_DYLIBS.match(library):
                self.fail(where, 'links %s, outside the platform allowlist (libSystem, libc++)' % library)
        bad = []
        for line in self.tool('llvm-nm', '-m', '--undefined-only', path).splitlines():
            if not line.strip():
                continue
            origin = re.search(r'\(from ([^)]+)\)', line)
            if origin is None or origin.group(1) not in MACOS_ORIGINS:
                bad.append(line.strip())
        if bad:
            self.fail(where, '%d undefined symbols not bound to libSystem or libc++: %s' % (len(bad), '; '.join(bad[:20])))
        self.exports(path, module, where, ['--extern-only'])
        self.summary.append('%s: install name %s, links %s; %s' % (where, install, ' '.join(loads), 'undefined symbols all from libSystem/libc++' if not bad else 'FAILED'))

    def exports(self, path, module, where, flags):
        defined = {line.split()[-1].lstrip('_') for line in self.tool('llvm-nm', *flags, '--defined-only', path).splitlines() if line.strip()}
        if FINGERPRINT[module].lstrip('_') not in defined:
            self.fail(where, 'does not export %s' % FINGERPRINT[module])


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--llvm-bin', required=True)
    parser.add_argument('--version', required=True)
    parser.add_argument('--repository', required=True, type=Path)
    parser.add_argument('--manifests', required=True, nargs='+', type=Path)
    parser.add_argument('--classifiers', nargs='+', default=list(CLASSIFIERS), choices=list(CLASSIFIERS),
                        help='platforms to require (default: all; the release gate requires all)')
    args = parser.parse_args()
    checker = Checker(args.llvm_bin)
    manifests = {}
    for root in args.manifests:
        for path in root.rglob('*.members.txt'):
            manifests.setdefault(path.name, path)
    for module in NATIVE_MODULES:
        artifact = 'truffledata-' + module
        prefix = args.repository / 'dev/elide' / artifact / args.version / ('%s-%s' % (artifact, args.version))
        # Every platform's library must report the ABI fingerprint the published bindings check before first use.
        bindings, entry = BINDINGS[module]
        jar = args.repository / 'dev/elide' / ('truffledata-' + bindings) / args.version / ('truffledata-%s-%s.jar' % (bindings, args.version))
        expected = None
        if jar.is_file():
            with zipfile.ZipFile(jar) as archive:
                found = set(HEX64.findall(archive.read(entry)))
            if len(found) == 1:
                expected = found.pop()
            else:
                checker.fail(jar.name, '%s holds %d ABI fingerprints' % (entry, len(found)))
        else:
            checker.fail(jar.name, 'missing bindings jar')
        for classifier in args.classifiers:
            extension, bitcode = CLASSIFIERS[classifier]
            def attachment(suffix):
                path = Path(str(prefix) + suffix)
                where = path.name
                if not path.is_file():
                    checker.fail(where, 'missing platform attachment')
                    return None, where
                return path, where

            def manifest(suffix):
                path = manifests.get('%s-%s%s' % (artifact, args.version, suffix))
                if path is None:
                    checker.fail('%s-%s%s' % (artifact, args.version, suffix), 'missing member manifest')
                return path

            linux = classifier.startswith('linux-')
            static, where = attachment('-%s.a' % classifier)
            listing = manifest('-%s.members.txt' % classifier)
            if static and listing:
                checker.archive(static, listing, 'fat' if linux else 'machine-code', where)
            if bitcode:
                twin, where = attachment('-%s-bitcode.a' % classifier)
                listing = manifest('-%s-bitcode.members.txt' % classifier)
                if twin and listing:
                    checker.archive(twin, listing, 'bitcode', where)
            shared, where = attachment('-%s%s' % (classifier, extension))
            for path, name in ((static, 'static archive'), (shared, 'shared library')):
                if path and expected and expected not in path.read_bytes():
                    reported = sorted(set(HEX64.findall(path.read_bytes())) - {expected})
                    checker.fail(path.name, '%s lacks the bindings\' ABI fingerprint %s (64-hex strings it holds: %s)'
                                 % (name, expected.decode(), ', '.join(f.decode()[:12] + '...' for f in reported[:3]) or 'none'))
            if shared:
                if linux:
                    checker.linux_shared(shared, classifier, module, where)
                else:
                    checker.macos_shared(shared, module, where)
    for line in checker.summary:
        print(line)
    if checker.errors:
        print('\nRelease native check FAILED (%d):' % len(checker.errors), file=sys.stderr)
        for error in checker.errors:
            print('  ' + error, file=sys.stderr)
        sys.exit(1)
    print('Release native check passed: %d modules x %d platforms' % (len(NATIVE_MODULES), len(args.classifiers)))


if __name__ == '__main__':
    main()
