import unittest
from profile_summary import summarize

INGEST = 'dev.elide.truffledata.comparison.Comparison$Fixture.ingest'


def event(kind, names, **values):
    frames = []
    for name in names:
        owner, method = name.rsplit('.', 1)
        frames.append({'method': {'type': {'name': owner}, 'name': method}})
    return {'type': kind, 'values': {'stackTrace': {'frames': frames}, **values}}


class ProfileSummaryTest(unittest.TestCase):
    def test_setup_excluded_and_recursive_frames_count_once(self):
        result = summarize([
            event('jdk.ExecutionSample', ['example.Parser.parse', 'example.Setup.run']),
            event('jdk.ExecutionSample', ['example.Parser.parse', 'example.Parser.parse', INGEST]),
            event('jdk.ObjectAllocationSample', ['example.Parser.parse', INGEST],
                  objectClass={'name': '[B'}),
        ])
        self.assertEqual(result['counts']['jdk.ExecutionSample total'], 2)
        self.assertEqual(result['counts']['jdk.ExecutionSample ingestion'], 1)
        self.assertEqual(dict(result['inclusive'])['example.Parser.parse'], 1)
        self.assertEqual(result['allocation_samples'], [('[B', 1)])
        self.assertFalse(result['safepoint_dominated'])

    def test_flags_callback_sampler_without_claiming_zero_allocation(self):
        result = summarize([event('jdk.ExecutionSample', [
            'com.oracle.svm.core.thread.RecurringCallbackSupport.invoke', INGEST])])
        self.assertTrue(result['safepoint_dominated'])
        self.assertEqual(result['allocation_samples'], [])
        self.assertNotIn('allocated_bytes', result)


if __name__ == '__main__':
    unittest.main()
