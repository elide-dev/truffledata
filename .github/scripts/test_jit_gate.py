import unittest
from jit_gate import check_probe, matrix_cases


class JitGateTest(unittest.TestCase):
    def test_probe_requires_specific_rejection(self):
        expected = 'Blocklisted methods are reachable for runtime compilation java.math.BigDecimal.add(BigDecimal) BlocklistProbe OptimizedCallTarget.executeRootNode'
        check_probe(1, expected)
        for code, output in [(0, expected), (1, 'network failure'), (1, expected.replace('BlocklistProbe', 'Other'))]:
            with self.assertRaises(ValueError):
                check_probe(code, output)

    def test_never_compile_probe_requires_specific_rejection(self):
        output = 'CompilerAsserts.neverPartOfCompilation reachable for runtime compilation NeverCompileProbe$UnsafeRoot.execute'
        check_probe(1, output, never=True)
        for code, text in [(0, output), (1, 'missing header'), (1, output.replace('NeverCompileProbe', 'Other'))]:
            with self.assertRaises(ValueError):
                check_probe(code, text, never=True)

    def test_matrix_requires_complete_unique_compiled_cases(self):
        output = ''.join(f'JIT_MATRIX case-{i} mode=success compiled=true\n' for i in range(300)) + 'JIT_MATRIX cases=300\n'
        self.assertEqual(len(matrix_cases(output)), 300)
        expected = {f'case-{i}': 'success' for i in range(300)}
        self.assertEqual(len(matrix_cases(output, expected)), 300)
        expected['case-1'] = 'rejection'
        with self.assertRaises(ValueError):
            matrix_cases(output, expected)
        for broken in [output.replace('case-1 ', 'case-0 '), output.replace('compiled=true', 'compiled=false', 1), output.replace('cases=300', 'cases=301'), '']:
            with self.assertRaises(ValueError):
                matrix_cases(broken)
