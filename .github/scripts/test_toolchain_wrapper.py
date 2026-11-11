"""Exercise the actual patched compiler wrapper without downloading a toolchain."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


PATCH = Path(__file__).resolve().parents[2] / 'bazel/patches/elide-toolchain-cc.patch'


class CompilerWrapperPaths(unittest.TestCase):
    def setUp(self):
        self.scratch = tempfile.TemporaryDirectory(prefix='cc-wrapper-')
        self.addCleanup(self.scratch.cleanup)
        self.root = Path(self.scratch.name)
        self.execroot = self.root / 'execroot'
        self.execroot.mkdir()
        self.commands = self.root / 'commands'
        self.commands.mkdir()
        uname = self.commands / 'uname'
        uname.write_text('#!/bin/sh\ncase "$1" in -s) echo Linux;; -m) echo x86_64;; *) exit 1;; esac\n')
        uname.chmod(0o755)
        self.env = dict(os.environ, PATH=f'{self.commands}{os.pathsep}{os.defpath}')

    def toolchain(self, location):
        location.mkdir(parents=True, exist_ok=True)
        subprocess.run(['patch', '-p1', '--input', str(PATCH)], cwd=location,
                       check=True, capture_output=True, text=True)
        compiler = location / 'cc'
        compiler.chmod(0o755)
        binaries = location / 'bin'
        binaries.mkdir()
        (binaries / 'x86_64-unknown-linux-gnu.cfg').write_text(
            '--target=x86_64-unknown-linux-gnu\n--sysroot=<CFGDIR>/../sysroot\n')
        clang = binaries / 'clang'
        clang.write_text('#!/bin/sh\nprintf "%s\\n" "$@"\n')
        clang.chmod(0o755)
        return compiler

    def arguments(self, compiler):
        return subprocess.check_output(
            [str(compiler), '-no-canonical-prefixes', '-DVALUE=with space', '-c', 'probe.c'],
            cwd=self.execroot, env=self.env, text=True).splitlines()

    def test_absolute_invocation_matches_relative_toolchain_paths(self):
        compiler = self.toolchain(self.execroot / 'external/toolchain')
        relative = self.arguments(compiler.relative_to(self.execroot))
        absolute = self.arguments(compiler)
        self.assertEqual(absolute, relative)
        self.assertIn('--sysroot=external/toolchain/bin/../sysroot', absolute)
        self.assertEqual(absolute[-4:], ['-no-canonical-prefixes', '-DVALUE=with space', '-c', 'probe.c'])

    def test_toolchain_at_execroot_uses_dot(self):
        compiler = self.toolchain(self.execroot)
        self.assertIn('--sysroot=./bin/../sysroot', self.arguments(compiler))

    def test_outside_toolchain_keeps_absolute_path(self):
        # A textual prefix without a path boundary must not count as the execroot.
        compiler = self.toolchain(self.root / 'execroot-other/toolchain')
        self.assertIn(f'--sysroot={compiler.parent}/bin/../sysroot', self.arguments(compiler))


if __name__ == '__main__':
    unittest.main()
