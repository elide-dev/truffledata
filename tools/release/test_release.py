"""Checks the real Bazel publication outputs, dependencies and metadata merging."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile

from maven_repo import MODULES, NAMESPACE, stage, validate_version


class PublicationTest(unittest.TestCase):
    def test_publications(self):
        base = Path('tools/release')
        pom = next((base / 'core/dev/elide/truffledata-core').glob('*/*.pom'))
        version = pom.parent.name
        lock = json.loads(Path('maven_install.json').read_text())
        with tempfile.TemporaryDirectory() as temporary:
            repo = Path(temporary)
            stage(base, repo, version)
            for module in MODULES:
                artifact = 'truffledata-' + module
                directory = repo / 'dev/elide' / artifact
                prefix = directory / version / (artifact + '-' + version)
                root = ET.parse(str(prefix) + '.pom').getroot()
                expected = {'org.graalvm.truffle:truffle-api', 'org.jspecify:jspecify'}
                if module != 'core':
                    expected.add('dev.elide:truffledata-core')
                if module == 'protobuf':
                    expected.update(('com.google.protobuf:protobuf-java', 'com.google.protobuf:protobuf-java-util', 'com.google.code.gson:gson'))
                if module == 'jdbc-sqlite':
                    expected.update(('dev.elide:truffledata-jdbc', 'org.xerial:sqlite-jdbc'))
                if module in ('jdbc-mysql', 'jdbc-postgresql'):
                    expected.add('dev.elide:truffledata-jdbc')
                    expected.add('com.mysql:mysql-connector-j' if module == 'jdbc-mysql' else 'org.postgresql:postgresql')
                if module == 'bom':
                    expected = {'dev.elide:truffledata-' + name for name in MODULES if name != 'bom'} | {
                        'org.graalvm.truffle:truffle-api', 'org.jspecify:jspecify',
                        'org.graalvm.truffle:truffle-runtime', 'org.graalvm.polyglot:polyglot',
                        'org.graalvm.js:js-language', 'org.graalvm.python:python-language',
                        'org.graalvm.python:python-resources', 'com.mysql:mysql-connector-j',
                        'org.postgresql:postgresql', 'org.xerial:sqlite-jdbc', 'com.google.protobuf:protobuf-java', 'com.google.protobuf:protobuf-java-util'}
                    self.assertEqual(root.findtext('m:packaging', namespaces=NAMESPACE), 'pom')
                    self.assertIsNone(root.find('m:dependencies', NAMESPACE))
                    self.assertFalse(Path(str(prefix) + '.jar').exists())
                actual = set()
                dep_path = 'm:dependencyManagement/m:dependencies/m:dependency' if module == 'bom' else 'm:dependencies/m:dependency'
                for dep in root.findall(dep_path, NAMESPACE):
                    get = lambda key: dep.findtext('m:' + key, namespaces=NAMESPACE)
                    coordinate = get('groupId') + ':' + get('artifactId')
                    actual.add(coordinate)
                    self.assertEqual(get('scope'), 'compile')
                    self.assertEqual(get('version'), version if coordinate.startswith('dev.elide:') else lock['artifacts'][coordinate]['version'])
                self.assertEqual(actual, expected)
                if module != 'bom':
                    with zipfile.ZipFile(str(prefix) + '.jar') as jar:
                        names = jar.namelist()
                        self.assertTrue(any(n.endswith('.class') for n in names))
                        if module == 'core':
                            self.assertTrue(any(n.endswith('ReadLongNodeGen.class') for n in names))
                for path in (directory / version).iterdir():
                    if path.suffix in ('.pom', '.jar'):
                        self.assertEqual(Path(str(path) + '.sha256').read_text().strip(), hashlib.sha256(path.read_bytes()).hexdigest())
                metadata = directory / 'maven-metadata.xml'
                tree = ET.parse(metadata)
                ET.SubElement(tree.getroot().find('versioning/versions'), 'version').text = '0.0.1'
                tree.write(metadata)
            stage(base, repo, version)
            for module in MODULES:
                versions = ET.parse(repo / 'dev/elide' / ('truffledata-' + module) / 'maven-metadata.xml').findall('versioning/versions/version')
                self.assertEqual([v.text for v in versions], [version, '0.0.1'])

    def test_unsafe_versions(self):
        for version in ('../escape', '1.0.0/<tag>', '', 'latest'):
            with self.assertRaises(ValueError):
                validate_version(version)


if __name__ == '__main__':
    unittest.main()
