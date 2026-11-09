import copy
import os
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock
from consumer import CELLS, ENGINES, GROUPS, command, markdown, merge, select_engines, select_modes, select_groups, supported, projection_gates, shard, throughput, validate


class ConsumerValidationTest(unittest.TestCase):
    VALID = dict(engine='native-view', mode='full', stage='peak', runtime='svm', rows=32,
                 guest_compiled=True, shared_access=True, stock_boundary=False,
                 native_index_retained=True, checksum=123,
                 ns_per_row=[1, 2, 3], input_bytes_per_row=100, copied_input_bytes_per_row=0,
                 decoded_native_index_capacity_bytes_per_row=128,
                 decoded_native_result_header_bytes_per_row=24)

    def test_rejects_misleading_measurements(self):
        cell = ('native-view', 'full', 'peak')
        checksums = {}
        validate(self.VALID, cell, 'time', checksums)
        for change in ({'guest_compiled': False}, {'checksum': 124}, {'runtime': 'jvm'},
                       {'input_bytes_per_row': 101},
                       {'ns_per_row': [1, float('nan'), 3]}, {'ns_per_row': [1, -1, 3]},
                       {'ns_per_row': [1, 2]}, {'stock_boundary': True}, {'shared_access': False},
                       {'native_index_retained': False},
                       {'decoded_native_index_capacity_bytes_per_row': -1}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                record = copy.deepcopy(self.VALID)
                record.update(change)
                validate(record, cell, 'time', checksums)

    def test_decode_cells_run_no_guest_and_eager_retains_no_index(self):
        record = copy.deepcopy(self.VALID)
        record.update(engine='native-eager', stage='decode', guest_compiled=False,
                      shared_access=False, native_index_retained=False)
        validate(record, ('native-eager', 'full', 'decode'), 'time', {})
        record.update(guest_compiled=True)
        with self.assertRaises(ValueError):
            validate(record, ('native-eager', 'full', 'decode'), 'time', {})
        jvm = copy.deepcopy(self.VALID)
        jvm.update(runtime='jvm')
        validate(jvm, ('native-view', 'full', 'peak'), 'time', {}, 'jvm')

    def test_shards_keep_each_group_whole_and_cover_every_cell(self):
        seen = []
        for index in range(7):
            with mock.patch.dict(os.environ, TEST_TOTAL_SHARDS='7', TEST_SHARD_INDEX=str(index)):
                seen += shard(GROUPS)
        self.assertEqual(sorted(seen), sorted(GROUPS))
        self.assertNotIn('native-view', {e for e, _, _ in CELLS})
        self.assertIn(('slab-projected', 'holdout-sparse', 'resident'), CELLS)
        self.assertNotIn(('slab-projected', 'holdout', 'resident'), CELLS)
        self.assertTrue(all(s in ('peak', 'interpret') for e, _, s in CELLS if e == 'stock-interop'))
        self.assertEqual({stage for engine, mode, stage in CELLS if engine == 'slab-session'},
                         {'decode', 'resident'})

    def test_decode_probes_stay_out_of_the_default_matrix(self):
        from consumer import PROBE_ENGINES, PROBE_GROUPS
        self.assertFalse({e for e, _, _ in CELLS} & set(PROBE_ENGINES))
        self.assertTrue(all(supported(p, 'decode', 'holdout') for p in PROBE_ENGINES))
        self.assertFalse(supported('probe-native', 'resident', 'full'))
        self.assertFalse(supported('slab', 'decode', 'holdout'))
        self.assertEqual(select_groups('holdout:decode'), [('holdout', 'decode')])
        self.assertNotIn(('holdout', 'decode'), GROUPS)
        self.assertEqual(PROBE_GROUPS, [('full', 'decode'), ('holdout', 'decode')])
        self.assertEqual(select_engines(['candidate'], ['candidate=slab,probe-native']),
                         {'candidate': ('slab', 'probe-native')})

    def test_slab_storage_requires_full_capacity_accounting(self):
        record = copy.deepcopy(self.VALID)
        record.update(engine='slab', native_index_retained=False, slab_bytes_per_row=100,
                      retained_slab_capacity_bytes_per_row=128)
        validate(record, ('slab', 'full', 'peak'), 'time', {})
        for change in ({'slab_bytes_per_row': 0}, {'retained_slab_capacity_bytes_per_row': 99},
                       {'retained_slab_capacity_bytes_per_row': float('nan')}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                validate(dict(record, **change), ('slab', 'full', 'peak'), 'time', {})

    def test_explicit_native_producer_cannot_report_java_or_hide_input_route(self):
        record = dict(self.VALID, engine='slab-native', runtime='jvm',
                      native_index_retained=False, slab_bytes_per_row=100,
                      retained_slab_capacity_bytes_per_row=128, producer_backend='JVM_FFM',
                      native_input='native')
        cell = ('slab-native', 'full', 'peak')
        validate(record, cell, 'time', {}, 'jvm')
        for change in ({'producer_backend': 'JAVA'}, {'native_input': 'heap'}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                validate(dict(record, **change), cell, 'time', {}, 'jvm')
        record.update(stage='heap', native_input='heap')
        validate(record, ('slab-native', 'full', 'heap'), 'time', {}, 'jvm')
        record.update(runtime='svm', producer_backend='SVM_STATIC')
        validate(record, ('slab-native', 'full', 'heap'), 'time', {})

    def test_session_storage_is_native_and_charges_arena_capacity(self):
        record = copy.deepcopy(self.VALID)
        record.update(engine='slab-session', stage='resident', native_index_retained=False,
                      slab_bytes_per_row=100, retained_slab_capacity_bytes_per_row=0,
                      session_arena_capacity_bytes_per_row=128)
        validate(record, ('slab-session', 'full', 'resident'), 'time', {})
        for change in ({'session_arena_capacity_bytes_per_row': 99},
                       {'retained_slab_capacity_bytes_per_row': 128}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                validate(dict(record, **change), ('slab-session', 'full', 'resident'), 'time', {})

    def test_new_engines_do_not_run_against_historical_baselines(self):
        selected = select_engines(['candidate', 'baseline'],
                                  ['baseline=stock,generated-owned,native-view,native-eager'])
        self.assertIn('slab', selected['candidate'])
        self.assertIn('slab-native', selected['candidate'])
        self.assertNotIn('slab-native', selected['baseline'])
        self.assertNotIn('slab', selected['baseline'])
        for specification in ('missing=stock', 'candidate=absent', 'candidate=stock,stock',
                              'candidate='):
            with self.subTest(specification=specification), self.assertRaises(ValueError):
                select_engines(['candidate'], [specification])

    def test_historical_modes_and_subset_groups_are_explicit(self):
        selected = select_modes(['candidate', 'baseline'], ['baseline=sparse,full,holdout,large'])
        self.assertIn('holdout-sparse', selected['candidate'])
        self.assertNotIn('holdout-sparse', selected['baseline'])
        self.assertEqual(select_groups('holdout-sparse:resident'), [('holdout-sparse', 'resident')])
        for specification in ('candidate=unknown', 'missing=full', 'candidate=full,full'):
            with self.subTest(specification=specification), self.assertRaises(ValueError):
                select_modes(['candidate'], [specification])
        with self.assertRaises(ValueError):
            select_groups('holdout-sparse:decode')

    def test_projection_and_host_metadata_are_required(self):
        record = dict(self.VALID, engine='slab-lazy', native_index_retained=False,
                      slab_bytes_per_row=128, retained_slab_capacity_bytes_per_row=256,
                      retained_input_bytes_per_row=512)
        validate(record, ('slab-lazy', 'full', 'peak'), 'time', {})
        with self.assertRaises(ValueError):
            validate(dict(record, retained_input_bytes_per_row=-1),
                     ('slab-lazy', 'full', 'peak'), 'time', {})
        record.update(engine='stock-interop', host_interop=True, host_interop_boundary=True)
        validate(record, ('stock-interop', 'full', 'peak'), 'time', {})
        with self.assertRaises(ValueError):
            validate(dict(record, host_interop=False), ('stock-interop', 'full', 'peak'), 'time', {})

    def test_projection_gate_requires_complete_pairs_and_keeps_misses(self):
        rows = [dict(binary='candidate', engine=engine, mode=mode, stage='resident', ns_per_row=ns)
                for engine, mode, ns in (
                    ('slab', 'sparse', 100), ('slab-projected', 'sparse', 80),
                    ('slab', 'holdout', 200), ('slab-projected', 'holdout-sparse', 90),
                    ('slab-lazy', 'holdout-sparse', 110), ('slab-session', 'holdout', 200),
                    ('slab-session-projected', 'holdout-sparse', 100))]
        gates = projection_gates(rows)
        self.assertEqual([g['passed'] for g in gates], [True, True, False, True])
        self.assertEqual(gates[2]['ceiling'], 0.5)
        with self.assertRaises(ValueError):
            projection_gates(rows[:-1])

    def test_commands_and_throughput(self):
        self.assertEqual(command('/b', 'svm', ('stock', 'full', 'peak'), 'time'),
                         ['/b', '-Xmx1g', 'stock', 'full', 'peak', 'time'])
        jvm = command('/b', 'jvm', ('stock', 'full', 'peak'), 'memory')
        self.assertIn('--jvm_flag=-XX:+UseSerialGC', jvm)
        self.assertEqual(jvm[-4:], ['stock', 'full', 'peak', 'memory'])
        self.assertAlmostEqual(throughput(1000, 1000), 1000.0)

    def test_merge_rejects_mixed_builds_and_preserves_provenance(self):
        reports = []
        for index, mode in enumerate(('full', 'sparse')):
            rows = [dict(self.VALID, binary='candidate', mode=mode, metric=metric,
                         java_bytes_per_row=[8, 8, 8]) for metric in ('time', 'memory')]
            reports.append(dict(schema=4, runtime='svm', source='commit+patch', seed=20261105,
                                forks=3, binary_sha256={'candidate': 'original-hash'},
                                engines={'candidate': ['native-view']},
                                modes={'candidate': ['full', 'sparse']},
                                groups=[[mode, 'peak']], shard=dict(index=index, total=2),
                                measurements=rows))
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            inputs = [root / 'first.json', root / 'second.json']
            for path, report in zip(inputs, reports):
                path.write_text(json.dumps(report))
            output = root / 'merged'
            merge(['--output', str(output), *map(str, inputs)])
            result = json.loads((output / 'results.json').read_text())
            self.assertFalse(result['complete'])
            for key in ('schema', 'runtime', 'source', 'seed', 'forks', 'binary_sha256',
                        'engines', 'modes'):
                self.assertEqual(result[key], reports[0][key])
            for key, value in (
                    ('schema', 3), ('runtime', 'jvm'), ('source', 'other-commit'),
                    ('seed', 1), ('forks', 5), ('binary_sha256', {'candidate': 'other-hash'}),
                    ('engines', {'candidate': ['stock']}), ('modes', {'candidate': ['full']})):
                with self.subTest(key=key), self.assertRaisesRegex(ValueError, 'different builds'):
                    changed = dict(reports[1], **{key: value})
                    inputs[1].write_text(json.dumps(changed))
                    merge(['--output', str(root / ('reject-' + key)), *map(str, inputs)])

    def test_markdown_reports_stock_ratio_and_native_bytes(self):
        rows = []
        for engine, ns in (('stock', 100.0), ('native-view', 150.0)):
            for metric in ('time', 'memory'):
                record = copy.deepcopy(self.VALID)
                record.update(engine=engine, binary='b', metric=metric,
                              java_bytes_per_row=[8, 8, 8], ns_per_row=[ns, ns, ns])
                rows.append(record)
        text = markdown(rows, ['b'])
        self.assertIn('| b | full | peak | native-view | 150.000 (150.000–150.000) | 1.50 | 666.7 '
                      '| 8.00 | 152.0 |', text)


if __name__ == '__main__':
    unittest.main()
