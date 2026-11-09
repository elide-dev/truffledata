#!/usr/bin/env python3
"""Prove blocklist rejection and native compiled execution; retain JUnit and diagnostics."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET


def check_probe(returncode, output, never=False):
    if returncode == 0:
        raise ValueError('Unsafe Native Image unexpectedly built')
    expected = ('CompilerAsserts.neverPartOfCompilation reachable for runtime compilation', 'NeverCompileProbe$UnsafeRoot.execute') if never else (
        'Blocklisted methods are reachable for runtime compilation', 'java.math.BigDecimal.add(BigDecimal)', 'BlocklistProbe', 'OptimizedCallTarget.executeRootNode')
    if not all(text in output for text in expected):
        raise ValueError('Negative control failed without the expected blocklist call trace')


def matrix_cases(output, expected=None):
    matches = re.findall(r'^JIT_MATRIX (\S+) mode=(success|rejection) compiled=(true|false)$', output, re.M)
    counts = re.findall(r'^JIT_MATRIX cases=(\d+)$', output, re.M)
    if len(counts) != 1 or int(counts[0]) != len(matches) or len(matches) < 300:
        raise ValueError('Incomplete native operation matrix')
    if len({name for name, _, _ in matches}) != len(matches):
        raise ValueError('Duplicate matrix case')
    if sum(mode == 'success' for _, mode, _ in matches) < 250:
        raise ValueError('Insufficient successful compilation cases')
    if any(compiled != 'true' for _, mode, compiled in matches if mode == 'success'):
        raise ValueError('Matrix case never executed compiled code')
    if expected is not None and {name: mode for name, mode, _ in matches} != expected:
        raise ValueError('Native case inventory or expected outcome changed')
    return [name for name, _, _ in matches]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--probes-only', action='store_true')
    args = parser.parse_args()
    bazel = os.environ.get('BAZEL', 'bazel')
    reports = Path('build/reports/native-jit')
    reports.mkdir(parents=True, exist_ok=True)
    suite = ET.Element('testsuite', name='native-jit')
    failed = False
    # The negative controls must fail to build; this gate verifies the specific rejection. Their
    # BuildBuddy invocations must not post commit statuses, or an expected failure turns the commit red.
    quiet = '--build_metadata=DISABLE_COMMIT_STATUS_REPORTING=true'
    commands = [
        ('blocklist-negative-control', [bazel, 'build', quiet, '//tests/jit-negative:native'], 1200),
        ('never-compile-negative-control', [bazel, 'build', quiet, '//tests/jit-negative:never_compile'], 1200),
    ]
    if not args.probes_only:
        commands.append(('compiled-matrix', ['build/simulation/truffledata-simulation', '--jit-check'], 600))
    for name, command, timeout in commands:
        case = ET.SubElement(suite, 'testcase', classname='NativeJit', name=name)
        try:
            result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                    text=True, timeout=timeout)
            (reports / (name + '.log')).write_text(result.stdout)
            print(result.stdout)
            if name != 'compiled-matrix':
                check_probe(result.returncode, result.stdout, never=name.startswith('never-'))
                print('Verified expected rejection:', name, flush=True)
            else:
                if result.returncode:
                    raise ValueError('Native compiled matrix failed')
                for operation in matrix_cases(result.stdout, json.loads(Path('benchmarks/jit-cases.json').read_text())):
                    ET.SubElement(suite, 'testcase', classname='NativeJitMatrix', name=operation)
        except (ValueError, OSError, subprocess.TimeoutExpired) as error:
            failed = True
            ET.SubElement(case, 'failure', message=str(error)).text = str(error)
    suite.set('tests', str(len(suite.findall('testcase'))))
    suite.set('failures', str(len(suite.findall('.//failure'))))
    ET.ElementTree(suite).write(reports / 'junit.xml', encoding='utf-8', xml_declaration=True)
    if failed:
        sys.exit('Native JIT gate failed; see build/reports/native-jit/')


if __name__ == '__main__':
    main()
