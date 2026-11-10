"""Card logic that decides what a reader sees: chips, pooling, fork checks and ticks.

Rendering itself needs fontTools; these tests cover the decisions, not the drawing.
"""
import unittest

from cards import Row, _nice, fmt
from plot_cards import Data, chip, forks, stat


class ChipTest(unittest.TestCase):
    def rows(self, ours, *others):
        return [Row('TruffleData', ours, ours, ours, True)] + [
            Row(name, value, value, value) for name, value in others]

    def test_compares_with_the_best_other_implementation_not_a_chosen_one(self):
        text, tone = chip(self.rows(50, ('Jackson', 200), ('fastjson2', 100)), 'lower')
        self.assertEqual((text, tone), ('2.00× faster than fastjson2', 'good'))

    def test_reports_a_loss_as_a_loss(self):
        text, tone = chip(self.rows(150, ('fastjson2', 100), ('Gson', 300)), 'lower')
        self.assertEqual((text, tone), ('1.50× slower than fastjson2', 'bad'))

    def test_higher_is_better_uses_the_fastest_other(self):
        text, tone = chip(self.rows(0.5, ('FastCSV', 0.25), ('Jackson CSV', 0.2)), 'higher')
        self.assertEqual((text, tone), ('2.00× faster than FastCSV', 'good'))

    def test_heap_wording(self):
        text, _ = chip(self.rows(400, ('Jackson', 1600)), 'lower', 'heap')
        self.assertEqual(text, '4.00× less heap than Jackson')

    def test_names_which_truffledata_api_it_compares(self):
        rows = [Row('protobuf-java', 600, 600, 600), Row('TruffleData generated', 660, 660, 660, True),
                Row('TruffleData slab', 300, 300, 300, True)]
        self.assertEqual(chip(rows, 'lower'), ('Slab: 2.00× faster than protobuf-java', 'good'))

    def test_no_chip_without_another_implementation(self):
        self.assertIsNone(chip([Row('A', 1, 1, 1, True), Row('B', 2, 2, 2, True)], 'lower'))


class DataTest(unittest.TestCase):
    def test_pools_only_one_image(self):
        data = Data(None)
        data.image('json', {'candidate': 'a', 'baseline': 'a'})
        with self.assertRaisesRegex(ValueError, 'differ'):
            data.image('csv', {'candidate': 'a', 'baseline': 'b'})
        with self.assertRaisesRegex(ValueError, 'different images'):
            data.image('json', {'candidate': 'c'})

    def test_duplicate_fork_is_rejected(self):
        rows = [dict(role='candidate', fork=0, x=[1]), dict(role='candidate', fork=0, x=[2])]
        with self.assertRaisesRegex(ValueError, 'duplicate fork'):
            forks(rows, 'x')

    def test_roles_pool_into_six_forks(self):
        rows = [dict(role=role, fork=f, x=[f + 1, 100, f + 1]) for role in ('candidate', 'baseline') for f in range(3)]
        self.assertEqual(sorted(forks(rows, 'x')), [1, 1, 2, 2, 3, 3])

    def test_stat_keeps_every_fork(self):
        self.assertEqual(stat([3, 1, 2, 10]), dict(median=2.5, min=1, max=10, forks=[1, 2, 3, 10], n=4))
        with self.assertRaises(ValueError):
            stat([1, float('nan')])


class FormatTest(unittest.TestCase):
    def test_nearest_clean_step(self):
        self.assertEqual(_nice(59), 50)
        self.assertEqual(_nice(0.12), 0.1)
        self.assertEqual(_nice(2300), 2500)

    def test_significant_figures(self):
        self.assertEqual([fmt(v) for v in (13404, 885.3, 28.79, 4.38, 0.4774)],
                         ['13,404', '885', '28.8', '4.38', '0.477'])


if __name__ == '__main__':
    unittest.main()
