import unittest
from aot_symbols import analyze_disassembly, check_budgets, markdown, parse_nm, select, strip

NM = """\
0000000000969a00 0000000000000067 t SlabFormat_getInt%%D_Zv1UzqRwPm5jU9FD534gHA
0000000000969a80 00000000000000dc t SlabFormat_getInt_YTcLUlNkmF4nsS2B9x6nN7
0000000000971240 0000000000000072 t SlabRow$Slot_accepts_lR7MVNbUhOKPwx8XS6ZS92
00000000008cd700 00000000000000a0 t BorrowedSlabRow$Slot_accepts%%D_q0mqEvivw5Glkwou5ZC418
0000000000400000 0000000000000010 d not_code_2LMnEa7JK37pRAUSfw0RK1
"""

X86 = """\
0000000000969a80 <SlabFormat_getInt_YTcLUlNkmF4nsS2B9x6nN7>:
  969a80:      \tleaq\t-0x28(%rsp), %rbx
  969a85:      \tcmpq\t0x8(%r15), %rbx
  969a89:      \tjbe\t0x2a5b40 <StackOverflowCheckImpl_throwNewStackOverflowError_CiGyUiZvwzBgNjjkTo2w7K>
  969ad9:      \tmovl\t%esi, 0x1c(%rsp)
  969add:      \tmovq\t0x10(%rsp), %rdi
  969b08:      \tcallq\t0x359620 <ExceptionUnwind_unwindExceptionWithoutCalleeSavedRegisters_D2uDbIIG0NJynRdXlWueWI>
  969b46:      \tcallq\t0x26e5e0 <GenScavengeAllocationSupport_slowNewInstance_cVwSGnaka62OV0G4bo2NyI>
  969b34:      \tretq
"""

ARM = """\
0000000100001000 <IndexScan_framePlain_abc>:
100001000: \tsub\tsp, sp, #0xe0
100001004: \tstp\tx29, x30, [sp, #0xd0]
100001008: \tldr\tx8, [sp, #0x10]
10000100c: \tbl\t0x100002000 <Other_call_x>
100001010: \tret
"""


class AotSymbolsTest(unittest.TestCase):
    def test_strip_removes_hash_and_marks_deopt_twins(self):
        self.assertEqual(strip('SlabFormat_getInt_YTcLUlNkmF4nsS2B9x6nN7'), ('SlabFormat_getInt', False))
        self.assertEqual(strip('SlabFormat_getInt%%D_Zv1UzqRwPm5jU9FD534gHA'), ('SlabFormat_getInt', True))

    def test_select_matches_stripped_names_and_skips_deopt_by_default(self):
        symbols = parse_nm(NM)
        self.assertEqual(len(symbols), 4)  # data symbols are not code
        chosen = select(symbols, [r'^SlabFormat_getInt$', r'Slot_accepts$'])
        self.assertEqual([e['name'] for e in chosen[r'^SlabFormat_getInt$']], ['SlabFormat_getInt'])
        self.assertEqual([e['name'] for e in chosen[r'Slot_accepts$']], ['SlabRow$Slot_accepts'])
        with_deopt = select(symbols, [r'Slot_accepts$'], deopt=True)
        self.assertEqual(len(with_deopt[r'Slot_accepts$']), 2)

    def test_x86_shape_counts(self):
        shape = analyze_disassembly(X86)
        self.assertEqual(shape['instructions'], 8)
        self.assertEqual(shape['calls'], 2)
        self.assertEqual(shape['frame'], 0x28)
        self.assertEqual(shape['stack_stores'], 1)
        self.assertEqual(shape['stack_loads'], 1)
        self.assertEqual(shape['call_targets'], [
            'ExceptionUnwind_unwindExceptionWithoutCalleeSavedRegisters',
            'GenScavengeAllocationSupport_slowNewInstance'])

    def test_arm_shape_counts(self):
        shape = analyze_disassembly(ARM)
        self.assertEqual(shape['instructions'], 5)
        self.assertEqual(shape['calls'], 1)
        self.assertEqual(shape['frame'], 0xe0)
        self.assertEqual(shape['stack_stores'], 1)
        self.assertEqual(shape['stack_loads'], 1)

    def test_budgets_fail_on_excess_and_on_missing_symbols(self):
        result = {'symbols': [
            {'pattern': 'a', 'name': 'A_m', 'instructions': 10, 'calls': 1, 'frame': 8, 'stack_loads': 0, 'stack_stores': 0},
            {'pattern': 'b', 'name': 'B_m', 'instructions': 10, 'calls': 1, 'frame': None, 'stack_loads': 0, 'stack_stores': 0},
        ], 'unmatched': ['c']}
        failures = check_budgets(result, {
            'a': {'instructions': 10, 'calls': 0},
            'b': {'frame': 8},
            'c': {'instructions': 1},
        })
        self.assertEqual(failures, [
            'a A_m: calls 1 > budget 0',
            'b B_m: frame not measured',
            'c: no symbol matched (renamed or dead; re-pin the budget)',
        ])
        table = markdown(result)
        self.assertIn('| `A_m` | 10 | 1 | 8 |', table)
        self.assertIn('| `B_m` | 10 | 1 | ? |', table)
        self.assertIn('`c` | (no match)', table)


if __name__ == '__main__':
    unittest.main()
