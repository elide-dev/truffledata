import unittest
from coverage_contract import ACCESS_SOURCES, SVM_ONLY_METHODS, analyze, enforce


class CoverageContractTest(unittest.TestCase):
    def report(self):
        return ''.join(f'SF:{source}\n'
                       'FNDA:1,Example::read ()V\nFNDA:0,Example::<init> ()V\n'
                       'DA:1,1\nDA:2,0\nend_of_record\n' for source in ACCESS_SOURCES)

    def test_reports_gaps_and_excludes_only_constructors(self):
        summary = analyze(self.report())
        enforce(summary)
        entry = next(iter(summary.values()))
        self.assertEqual(entry['uncovered_lines'], [2])
        self.assertEqual(entry['methods_total'], 1)

    def test_abstract_declarations_do_not_count_as_executable_methods(self):
        text = self.report().replace('FNDA:1,Example::read ()V',
            'FN:0,Example::abstract ()V\nFNDA:0,Example::abstract ()V\nFNDA:1,Example::read ()V')
        enforce(analyze(text))

    def test_missing_or_uncovered_surface_fails(self):
        for text in ['', self.report().replace('FNDA:1,', 'FNDA:0,', 1)]:
            with self.assertRaises(ValueError):
                enforce(analyze(text))

    def test_platform_exception_does_not_exempt_native_array_access(self):
        source, methods = next(iter(SVM_ONLY_METHODS.items()))
        summary = analyze(self.report())
        summary[source]['uncovered_methods'] = list(methods)
        enforce(summary)
        summary[source]['uncovered_methods'].append(
            'dev/elide/truffledata/buffa/NativeRow$NativeArray::getArraySize ()J')
        with self.assertRaises(ValueError):
            enforce(summary)
