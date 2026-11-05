import copy
import unittest
from consumer import validate


class ConsumerValidationTest(unittest.TestCase):
    def test_rejects_misleading_measurements(self):
        cell = ('native-view', 'full', 'peak')
        valid = dict(engine='native-view', mode='full', stage='peak', runtime='svm', rows=32,
                     guest_compiled=True, shared_access=True, stock_boundary=False, checksum=123,
                     ns_per_row=[1, 2, 3], input_bytes_per_row=100, copied_input_bytes_per_row=0,
                     decoded_native_index_capacity_bytes_per_row=128,
                     decoded_native_result_header_bytes_per_row=24)
        checksums = {}
        validate(valid, cell, 'time', checksums)
        for change in ({'guest_compiled': False}, {'checksum': 124}, {'runtime': 'jvm'},
                       {'ns_per_row': [1, float('nan'), 3]}, {'ns_per_row': [1, -1, 3]},
                       {'ns_per_row': [1, 2]}, {'stock_boundary': True}, {'shared_access': False},
                       {'decoded_native_index_capacity_bytes_per_row': -1}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                record = copy.deepcopy(valid)
                record.update(change)
                validate(record, cell, 'time', checksums)
