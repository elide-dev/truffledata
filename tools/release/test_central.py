"""Reject unsigned/tampered publication inputs and unsafe deployment transitions."""
import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import central


class CentralTest(unittest.TestCase):
    def test_checksums_and_required_metadata(self):
        with tempfile.TemporaryDirectory() as temporary:
            repo = Path(temporary)
            directory = repo / 'dev/elide/truffledata-core/1.2.3'
            directory.mkdir(parents=True)
            pom = directory / 'truffledata-core-1.2.3.pom'
            pom.write_text('''<project xmlns="http://maven.apache.org/POM/4.0.0">
              <groupId>dev.elide</groupId><artifactId>truffledata-core</artifactId><version>1.2.3</version>
              <name>test</name><description>test</description><url>https://example.com</url>
              <licenses><license><name>MIT OR Apache-2.0</name></license></licenses>
              <developers><developer><name>Authors</name></developer></developers>
              <scm><connection>scm:git:https://example.com/repo</connection><url>https://example.com/repo</url></scm>
            </project>''')
            for algorithm in central.HASHES:
                Path(str(pom) + '.' + algorithm).write_text(hashlib.new(algorithm, pom.read_bytes()).hexdigest())
            with patch.object(central, 'MODULES', ('core',)), patch.object(central, 'artifact_suffixes', return_value=('.pom',)):
                self.assertEqual(central.checked_payloads(repo, '1.2.3'), [pom])
                with self.assertRaisesRegex(ValueError, 'Missing signed attachment'):
                    central.make_bundle(repo, '1.2.3', repo / 'central.zip')
                self.assertFalse((repo / 'central.zip').exists())
                pom.write_text(pom.read_text().replace('<name>test</name>', '<name>tampered</name>'))
                with self.assertRaisesRegex(ValueError, 'Checksum mismatch'):
                    central.checked_payloads(repo, '1.2.3')

    def test_does_not_publish_failed_deployment(self):
        identifier = '12345678-1234-1234-1234-123456789012'
        with tempfile.TemporaryDirectory() as temporary:
            state = Path(temporary) / 'deployment.json'
            state.write_text(json.dumps({'deploymentId': identifier}))
            with patch('sys.argv', ['central.py', 'publish', '--state', str(state)]), patch.object(central, 'status', return_value='FAILED'), patch.object(central, 'request') as request:
                with self.assertRaisesRegex(ValueError, 'VALIDATED'):
                    central.main()
                request.assert_not_called()

    def test_maven_settings_fragment_and_ambiguity(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'settings.xml'
            path.write_text('<server><id>central</id><username>example</username><password>example-token</password></server>')
            self.assertEqual(central.read_credentials(path), {'username': 'example', 'token': 'example-token'})
            path.write_text('<settings><servers>' + path.read_text() * 2 + '</servers></settings>')
            with self.assertRaisesRegex(ValueError, 'CENTRAL_SERVER_ID'):
                central.read_credentials(path)

    def test_credentials_required_and_redirect_rejected(self):
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaisesRegex(ValueError, 'SONATYPE_USERNAME'):
                central.request('status?id=12345678-1234-1234-1234-123456789012')
        with self.assertRaisesRegex(RuntimeError, 'redirected'):
            central.NoRedirect().redirect_request(None, None, 302, None, None, 'https://example.com')


if __name__ == '__main__':
    unittest.main()
