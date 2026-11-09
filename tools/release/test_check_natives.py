"""The release gate's export checks for archives of static-only (musl) modules."""
import unittest
from unittest.mock import patch

from check_natives import Checker, CSV_EXPORTS, YAML_EXPORTS


def listing(symbols):
    return ''.join('0000000000000000 T _%s\n' % symbol for symbol in symbols)


class ExportsTest(unittest.TestCase):
    def check(self, module, symbols):
        checker = Checker('/nonexistent')
        with patch.object(Checker, 'tool', return_value=listing(symbols)):
            checker.exports('archive.a', module, 'archive.a', ['--extern-only'])
        return checker.errors

    def test_yaml_requires_the_seam(self):
        self.assertEqual(self.check('yaml', YAML_EXPORTS | {'_ZN4c4yml4TreeC1Ev'}), [])
        errors = self.check('yaml', YAML_EXPORTS - {'td_yaml_stream_next'})
        self.assertEqual(len(errors), 1)
        self.assertIn('td_yaml_stream_next', errors[0])
        self.assertTrue(any('td_yaml_fingerprint' in e for e in self.check('yaml', YAML_EXPORTS - {'td_yaml_fingerprint'})))

    def test_csv_exports_are_exact(self):
        self.assertEqual(self.check('csv-native', CSV_EXPORTS), [])
        self.assertTrue(self.check('csv-native', CSV_EXPORTS | {'simdutf_internal'}))

    def test_json_needs_only_its_fingerprint(self):
        self.assertEqual(self.check('json', {'td_json_fingerprint', 'anything'}), [])


if __name__ == '__main__':
    unittest.main()
