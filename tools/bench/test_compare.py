import copy
import itertools
import unittest
import compare


class ComparisonTest(unittest.TestCase):
    def dataset(self):
        rows = []
        for cell, fork in itertools.product(compare.cells(), range(3)):
            workload, engine, stage = cell
            rows.append(dict(workload=workload, engine=engine, stage=stage, fork=fork,
                             runtime='svm', backend='SVM_STATIC' if engine == 'buffa' else 'none',
                             guest_compiled=stage == 'peak', rows=32, checksum=compare.CHECKSUMS[workload],
                             ns_per_row=[1., 2., 3.], allocated_bytes_per_row=[0., 0., 0.]))
        return dict(schema=1, forks=3, results=rows)

    def test_complete(self):
        self.assertEqual(len(list(compare.cells())), 29)
        compare.validate(self.dataset())

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
