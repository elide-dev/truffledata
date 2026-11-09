"""Measurement integrity checks; no benchmark timings are asserted."""
import copy
import unittest

import csv_pair


class CsvPairTest(unittest.TestCase):
    cell = ('strings', 'truffledata-batch', 'mixed', 1048576, 'ingest')

    def row(self):
        return dict(workload='strings', engine='truffledata-batch', dataset='mixed',
                    target=1048576, bytes=1048500, rows=1000, stage='ingest', runtime='svm',
                    checksum=789, sha256='a' * 64, ns_per_byte=[1, 2, 3, 4, 5],
                    allocated_bytes_per_row=[10, 10, 10])

    def test_pair_requires_identical_input_and_result(self):
        for field, value in [('checksum', 790), ('sha256', 'b' * 64), ('bytes', 1048501),
                             ('rows', 999)]:
            with self.subTest(field=field):
                checks = {}
                csv_pair.validate(self.row(), self.cell, checks)
                changed = self.row()
                changed[field] = value
                with self.assertRaises(ValueError):
                    csv_pair.validate(changed, self.cell, checks)

    def test_wrong_work_and_invalid_samples_rejected(self):
        for field, value in [('runtime', 'jvm'), ('engine', 'fastcsv'), ('target', 128),
                             ('ns_per_byte', [1, 2, float('nan'), 4, 5]),
                             ('allocated_bytes_per_row', [1, -1, 1]), ('ns_per_byte', [])]:
            with self.subTest(field=field, value=value):
                changed = self.row()
                changed[field] = value
                with self.assertRaises(ValueError):
                    csv_pair.validate(changed, self.cell, {})

    def test_typed_reads_require_correct_tier(self):
        row = dict(workload='typed-reads', engine='truffledata-plan', dataset='numeric',
                   stage='peak', runtime='svm', guest_compiled=True, rows=32, checksum=123,
                   ns_per_row=[1, 2, 3], allocated_bytes_per_row=[0, 0, 0])
        cell = ('typed-reads', 'truffledata-plan', 'numeric', 1048576, 'peak')
        csv_pair.validate(row, cell, {})
        row['guest_compiled'] = False
        with self.assertRaises(ValueError):
            csv_pair.validate(row, cell, {})

    def test_summary_keeps_binaries_separate_and_uses_fork_medians(self):
        rows = []
        for variant, samples in [('baseline', (2, 3, 100)), ('candidate', (1, 2, 3))]:
            for fork, value in enumerate(samples):
                row = copy.deepcopy(self.row())
                row.update(variant=variant, fork=fork, ns_per_byte=[value] * 5)
                rows.append(row)
        result, = csv_pair.summary(rows)
        self.assertEqual(result['baseline']['median'], 3)
        self.assertEqual(result['candidate']['median'], 2)
        self.assertEqual(result['baseline']['max'], 100)
        self.assertEqual(result['candidate']['forks'], 3)
        self.assertAlmostEqual(result['candidate_over_baseline'], 2 / 3)


if __name__ == '__main__':
    unittest.main()
