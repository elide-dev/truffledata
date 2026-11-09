import unittest
from json_pair_summary import cells, markdown


def row(engine, stage, role, metric, samples, workload='json-generic'):
    return {'engine': engine, 'stage': stage, 'role': role, 'workload': workload, metric: samples}


def report(shard, group, stages, rows):
    return {'complete': True, 'shard': shard, 'group': group, 'stages': stages,
            'binary_sha256': {'candidate': 'c', 'baseline': 'b'}, 'measurements': rows}


def paired_rows(engine, stage, before, after):
    rows = []
    for role, values in (('baseline', before), ('candidate', after)):
        for value in values:
            rows.append(row(engine, stage, role, 'ns_per_row', [value, value, value]))
            rows.append(row(engine, stage, role, 'allocated_bytes_per_row', [1.0, 1.0, 1.0]))
    return rows


class JsonPairSummaryTest(unittest.TestCase):
    def test_skips_stages_a_group_does_not_measure_and_flags_disjoint_ranges(self):
        rows = paired_rows('simdjson-snapshot', 'ingest', [2000.0, 2010.0, 2020.0], [1500.0, 1510.0, 1520.0])
        rows += paired_rows('simdjson-stream-interop', 'ingest', [700.0, 705.0, 710.0], [708.0, 712.0, 720.0])
        paired = cells([report(6, ['simdjson-snapshot', 'simdjson-stream-interop'],
                               ['ingest', 'read-aot', 'interpret', 'peak'], rows)])
        self.assertEqual([(c['engine'], c['stage']) for c in paired],
                         [('simdjson-snapshot', 'ingest'), ('simdjson-stream-interop', 'ingest')])
        self.assertAlmostEqual(paired[0]['baseline_over_candidate'], 2010.0 / 1510.0)
        self.assertTrue(paired[0]['disjoint'])
        self.assertFalse(paired[1]['disjoint'])
        self.assertIn('| 6 | json-generic | simdjson-snapshot | ingest | 2010.000 (2000.000–2020.000) |',
                      markdown(paired))

    def test_candidate_only_engines_are_reported_unpaired(self):
        rows = [row('simdjson-generic-decode', 'ingest', 'candidate', metric, [v, v, v])
                for v in (500.0, 505.0, 510.0) for metric in ('ns_per_row', 'allocated_bytes_per_row')]
        shard = report(6, ['simdjson-generic-decode'], ['ingest'], rows)
        shard['candidate_only'] = ['simdjson-generic-decode']
        paired = cells([shard])
        self.assertNotIn('baseline', paired[0])
        self.assertAlmostEqual(paired[0]['candidate']['median'], 505.0)
        self.assertIn('| (candidate only) | 505.000 (500.000–510.000) | — | — | — |', markdown(paired))

    def test_rejects_incomplete_or_mismatched_reports(self):
        rows = paired_rows('simdjson-snapshot', 'peak', [1.0, 1.0, 1.0], [1.0, 1.0, 1.0])
        incomplete = report(6, ['simdjson-snapshot'], ['peak'], rows)
        incomplete['complete'] = False
        with self.assertRaises(ValueError):
            cells([incomplete])
        other = report(7, ['simdjson-snapshot'], ['peak'], rows)
        other['binary_sha256'] = {'candidate': 'x', 'baseline': 'b'}
        with self.assertRaises(ValueError):
            cells([report(6, ['simdjson-snapshot'], ['peak'], rows), other])
        missing = report(6, ['simdjson-snapshot'], ['peak'], rows[:2])
        with self.assertRaises(ValueError):
            cells([missing])


if __name__ == '__main__':
    unittest.main()
