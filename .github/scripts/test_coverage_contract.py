from pathlib import Path
import unittest
from coverage_contract import (ACCESS_SOURCES, DECLARATION_ONLY_SOURCES, JVM_UNREACHABLE_METHODS,
                               SVM_ONLY_METHODS, analyze, enforce)


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

    def test_exemptions_name_inventoried_sources_and_cover_only_their_methods(self):
        for exemptions in (SVM_ONLY_METHODS, JVM_UNREACHABLE_METHODS):
            self.assertLessEqual(set(exemptions), ACCESS_SOURCES)
            for source, methods in exemptions.items():
                summary = analyze(self.report())
                summary[source]['uncovered_methods'] = list(methods)
                enforce(summary)
                summary[source]['uncovered_methods'].append('Example::other ()V')
                with self.assertRaises(ValueError):
                    enforce(summary)

    def test_json_and_csv_sources_are_inventoried(self):
        repo = Path(__file__).resolve().parents[2]
        for module in ('json', 'tape', 'yaml', 'csv'):
            root = repo / module / 'src/main/java'
            sources = {str(path.relative_to(repo)) for path in root.rglob('*.java')
                       if path.name != 'package-info.java'}
            self.assertTrue(sources)
            self.assertEqual(sources, {s for s in ACCESS_SOURCES if s.startswith(module + '/')})

    def test_only_declaration_sources_may_have_no_methods(self):
        summary = analyze(self.report())
        for source in DECLARATION_ONLY_SOURCES:
            summary[source].update(methods_total=0, uncovered_methods=[])
        enforce(summary)
        other = next(iter(ACCESS_SOURCES - DECLARATION_ONLY_SOURCES))
        summary[other].update(methods_total=0, uncovered_methods=[])
        with self.assertRaises(ValueError):
            enforce(summary)
