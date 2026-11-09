"""SDK bundles preserve validated artifact bytes and reject mismatched headers."""
import hashlib
import json
from pathlib import Path
import shutil
import tarfile
import tempfile
import unittest
from unittest.mock import patch

from native_bundle import bundle


class NativeBundleTest(unittest.TestCase):
    def test_bundle_integrity_and_reproducibility(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            version = '1.2.3'
            directory = root / 'repo/dev/elide/truffledata-csv-native' / version
            directory.mkdir(parents=True)
            prefix = directory / ('truffledata-csv-native-' + version)
            fingerprint = b'ab' * 32
            header = root / 'seam.h'
            header.write_bytes(b'#define ABI "' + fingerprint + b'"\n')
            for suffix in ('.a', '.so'):
                Path(str(prefix) + '-linux-x86_64' + suffix).write_bytes(b'native' + fingerprint + suffix.encode())

            # Exercise packaging itself without requiring host LLVM or zstd in unit tests.
            def command(args, **kwargs):
                return 'clang version 23.1.4\n' if args[0].endswith('clang') else 'GLIBC_2.2.5\nGLIBC_2.17\n'

            def compress(args, **kwargs):
                shutil.copyfile(args[5], args[7])

            with patch('native_bundle.subprocess.check_output', side_effect=command), patch('native_bundle.subprocess.run', side_effect=compress):
                results = [bundle(root / 'repo', version, 'csv-native', 'linux-x86_64', header, root / 'llvm', root / output, 'a' * 40, 1234) for output in ('one', 'two')]
                self.assertEqual(results[0].read_bytes(), results[1].read_bytes())
                with tarfile.open(results[0]) as archive:
                    manifest = json.load(archive.extractfile('manifest.json'))
                    self.assertEqual(manifest['minimumRuntime'], {'glibc': '2.17'})
                    self.assertEqual(manifest['targetTriple'], 'x86_64-unknown-linux-gnu')
                    for name, metadata in manifest['files'].items():
                        payload = archive.extractfile(name).read()
                        self.assertEqual(hashlib.sha256(payload).hexdigest(), metadata['sha256'])
                        self.assertEqual(len(payload), metadata['size'])
                        self.assertEqual(archive.getmember(name).mtime, 1234)
                        self.assertIn(metadata['sha256'].encode(), archive.extractfile(name + '.sha256').read())
                header.write_bytes(b'#define ABI "' + b'cd' * 32 + b'"\n')
                with self.assertRaisesRegex(ValueError, 'ABI mismatch'):
                    bundle(root / 'repo', version, 'csv-native', 'linux-x86_64', header, root / 'llvm', root / 'bad', 'a' * 40, 1234)

    def test_musl_bundle_is_static_only(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            version = '1.2.3'
            directory = root / 'repo/dev/elide/truffledata-csv-native' / version
            directory.mkdir(parents=True)
            prefix = directory / ('truffledata-csv-native-' + version)
            fingerprint = b'ab' * 32
            header = root / 'seam.h'
            header.write_bytes(b'#define ABI "' + fingerprint + b'"\n')
            Path(str(prefix) + '-linux-aarch64-musl.a').write_bytes(b'native' + fingerprint)

            def compress(args, **kwargs):
                shutil.copyfile(args[5], args[7])

            with patch('native_bundle.subprocess.check_output', return_value='clang version 23.1.4\n'), patch('native_bundle.subprocess.run', side_effect=compress):
                result = bundle(root / 'repo', version, 'csv-native', 'linux-aarch64-musl', header, root / 'llvm', root / 'out', 'a' * 40, 1234)
                with tarfile.open(result) as archive:
                    manifest = json.load(archive.extractfile('manifest.json'))
                    self.assertEqual(manifest['minimumRuntime'], {'musl': '1.2.5'})
                    self.assertEqual(manifest['targetTriple'], 'aarch64-unknown-linux-musl')
                    self.assertFalse([name for name in manifest['files'] if name.endswith('.so')])
                with self.assertRaisesRegex(ValueError, 'Unsupported native module'):
                    bundle(root / 'repo', version, 'json', 'linux-aarch64-musl', header, root / 'llvm', root / 'bad', 'a' * 40, 1234)


if __name__ == '__main__':
    unittest.main()
