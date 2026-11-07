import copy
import os
import unittest
from unittest import mock
from consumer import CELLS, ENGINES, GROUPS, command, markdown, shard, throughput, validate


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
        self.assertEqual(len(CELLS), len(GROUPS) * len(ENGINES))

    def test_commands_and_throughput(self):
        self.assertEqual(command('/b', 'svm', ('stock', 'full', 'peak'), 'time'),
                         ['/b', '-Xmx1g', 'stock', 'full', 'peak', 'time'])
        jvm = command('/b', 'jvm', ('stock', 'full', 'peak'), 'memory')
        self.assertIn('--jvm_flag=-XX:+UseSerialGC', jvm)
        self.assertEqual(jvm[-4:], ['stock', 'full', 'peak', 'memory'])
        self.assertAlmostEqual(throughput(1000, 1000), 1000.0)

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
