import copy
import itertools
import unittest
import compare


class ComparisonTest(unittest.TestCase):
    def dataset(self, schema=7):
        rows = []
        for cell, fork in itertools.product(compare.cells(schema), range(3)):
            workload, engine, stage = cell
            rows.append(dict(workload=workload, engine=engine, stage=stage, fork=fork,
                             runtime='svm', backend='SVM_STATIC' if engine == 'buffa' else 'none',
                             guest_compiled=stage.startswith('peak'), rows=32, checksum=compare.CHECKSUMS[workload],
                             ns_per_row=[1., 2., 3.], allocated_bytes_per_row=[0., 0., 0.]))
        return dict(schema=schema, forks=3, results=rows)

    def test_complete(self):
        self.assertEqual(len(list(compare.cells())), 96)
        self.assertEqual(len(list(compare.cells(1))), 31)
        self.assertEqual(len(list(compare.cells(2))), 37)
        self.assertEqual(len(list(compare.cells(3))), 47)
        self.assertEqual(len(list(compare.cells(4))), 59)
        compare.validate(self.dataset(5))
        compare.validate(self.dataset(4))
        compare.validate(self.dataset(3))
        compare.validate(self.dataset(2))
        compare.validate(self.dataset(1))
        compare.validate(self.dataset())

    def test_stock_guest_tiers_keep_direct_baselines(self):
        self.assertTrue(set(compare.cells(3)).issubset(set(compare.cells())))
        stock_peak = [c for c in compare.cells() if c[1].startswith('stock') and c[2].startswith('peak')]
        self.assertEqual(len(stock_peak), 6)
        for stage, wrong in [('peak', False), ('interpret', True),
                             ('peak-boundary', False), ('interpret-boundary', True)]:
            data = self.dataset()
            row = next(r for r in data['results'] if r['engine'] == 'stock' and r['stage'] == stage)
            row['guest_compiled'] = wrong
            with self.assertRaises(ValueError):
                compare.validate(data)

    def test_rejects_incomplete_duplicate_and_wrong_identity(self):
        original = self.dataset()
        changes = [lambda d: d['results'].pop(),
                   lambda d: d['results'].append(d['results'][0]),
                   lambda d: d['results'][0].update(runtime='jvm'),
                   lambda d: d['results'][0].update(guest_compiled=True),
                   lambda d: d['results'][0].update(checksum=0),
                   lambda d: d['results'][0].update(backend='JAVA'),
                   lambda d: d['results'][0].update(ns_per_row=[float('nan')] * 3),
                   lambda d: d['results'][0].update(allocated_bytes_per_row=[-1] * 3)]
        for change in changes:
            with self.subTest(change=change):
                data = copy.deepcopy(original)
                change(data)
                with self.assertRaises(ValueError):
                    compare.validate(data)


if __name__ == '__main__':
    unittest.main()
