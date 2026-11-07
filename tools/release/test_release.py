"""Checks the real Bazel publication outputs, dependencies and metadata merging."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile

from maven_repo import artifact_suffixes, built_classifiers, CLASSIFIERS, MODULES, NAMESPACE, stage, validate_version


class PublicationTest(unittest.TestCase):
    def test_publications(self):
        base = Path('tools/release')
        pom = next((base / 'core/dev/elide/truffledata-core').glob('*/*.pom'))
        version = pom.parent.name
        lock = json.loads(Path('maven_install.json').read_text())
        # One Bazel invocation builds one platform's attachments; publish.sh stages the others beside them.
        classifiers = built_classifiers(base)
        self.assertEqual(len(classifiers), 1)
        classifier = classifiers[0]
        extension, bitcode = CLASSIFIERS[classifier]
        with tempfile.TemporaryDirectory() as temporary:
            repo = Path(temporary)
            with self.assertRaisesRegex(ValueError, 'Missing artifact'):
                stage(base, Path(temporary) / 'all-platforms', version)
            stage(base, repo, version, classifiers=classifiers)
            for module in MODULES:
                artifact = 'truffledata-' + module
                directory = repo / 'dev/elide' / artifact
                prefix = directory / version / (artifact + '-' + version)
                root = ET.parse(str(prefix) + '.pom').getroot()
                expected = {'org.graalvm.truffle:truffle-api', 'org.jspecify:jspecify'}
                if module != 'core':
                    expected.add('dev.elide:truffledata-core')
                if module == 'protobuf':
                    expected.update(('com.google.protobuf:protobuf-java', 'com.google.protobuf:protobuf-java-util', 'dev.elide:truffledata-protobuf-runtime'))
                if module == 'protobuf-runtime':
                    expected.add('com.google.code.gson:gson')
                if module in ('protobuf-buffa', 'protobuf-buffa-bindings'):
                    expected.update(('org.graalvm.sdk:nativeimage', 'org.graalvm.sdk:word'))
                if module == 'protobuf-buffa':
                    expected.update(('dev.elide:truffledata-protobuf-runtime', 'dev.elide:truffledata-protobuf-buffa-bindings'))
                if module in ('tape', 'json', 'json-bindings'):
                    expected.update(('org.graalvm.sdk:nativeimage', 'org.graalvm.sdk:word'))
                if module == 'tape-jvm':
                    expected.add('dev.elide:truffledata-tape')
                if module == 'json':
                    expected.update(('dev.elide:truffledata-json-bindings', 'dev.elide:truffledata-tape'))
                if module == 'json-jvm':
                    expected.update(('dev.elide:truffledata-json', 'dev.elide:truffledata-json-bindings', 'dev.elide:truffledata-tape-jvm'))
                if module in ('csv-bindings', 'csv-native'):
                    expected.update(('org.graalvm.sdk:nativeimage', 'org.graalvm.sdk:word'))
                if module in ('csv-native', 'csv-jvm'):
                    expected.update(('dev.elide:truffledata-csv', 'dev.elide:truffledata-csv-bindings'))
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
                for suffix in artifact_suffixes(module, classifiers):
                    self.assertTrue(Path(str(prefix) + suffix).is_file())
                if module == 'protobuf-buffa-bindings':
                    with zipfile.ZipFile(str(prefix) + '.jar') as jar:
                        self.assertIn('dev/elide/truffledata/buffa/generated/Buffa.class', jar.namelist())
                        self.assertIn('dev/elide/truffledata/buffa/generated/BuffaFFM.class', jar.namelist())
                if module.startswith('json') or module.startswith('tape'):
                    with zipfile.ZipFile(str(prefix) + '.jar') as jar:
                        names = {n for n in jar.namelist() if not n.endswith('/')}
                    myna = {n for n in names if n.startswith('dev/elide/myna/')}
                    generated = {n for n in names if n.startswith('dev/elide/truffledata/json/generated/')}
                    backend = {n for n in names if n.startswith('dev/elide/truffledata/json/') and n not in generated}
                    shared = {n for n in names if n.startswith('dev/elide/truffledata/tape/')}
                    jvm = {'dev/elide/truffledata/json/JsonJvm.class', 'dev/elide/truffledata/json/JvmTypedOwner.class'}
                    segment = 'dev/elide/truffledata/tape/SegmentValueTape.class'
                    if module == 'tape':
                        # The shared tape travels with the exact Myna value ABI its tapes extend; static
                        # images take it, so the FFM tape reader stays out.
                        self.assertIn('dev/elide/truffledata/tape/TapeValue.class', names)
                        self.assertIn('dev/elide/truffledata/tape/TapeValueGen.class', names)
                        self.assertIn('dev/elide/truffledata/tape/SvmValueTape.class', names)
                        self.assertIn('dev/elide/myna/values/ValueTape.class', names)
                        self.assertNotIn(segment, names)
                        self.assertFalse(backend | generated)
                    else:
                        self.assertFalse(myna)
                    if module == 'tape-jvm':
                        self.assertEqual(shared, {segment})
                        self.assertFalse(backend | generated)
                    if module.startswith('json'):
                        self.assertFalse(shared)
                    if module == 'json-bindings':
                        # Generated bindings; the Myna value ABI they were generated against ships in truffledata-tape.
                        self.assertIn('dev/elide/truffledata/json/generated/Json.class', names)
                        self.assertIn('dev/elide/truffledata/json/generated/JsonFFM.class', names)
                        self.assertIn('META-INF/native-image/dev.elide.myna/dev.elide.truffledata.json.generated.Json/native-image.properties', names)
                        self.assertFalse(backend)
                    elif module.startswith('json'):
                        self.assertFalse(generated)
                    if module == 'json':
                        # Static images take this jar: the FFM backend must stay out of it.
                        self.assertIn('dev/elide/truffledata/json/JsonSvm.class', names)
                        self.assertIn('dev/elide/truffledata/json/JsonSession.class', names)
                        self.assertFalse(jvm & names)
                    if module == 'json-jvm':
                        self.assertTrue(jvm <= names)
                        self.assertNotIn('dev/elide/truffledata/json/JsonSvm.class', names)
                if module.startswith('csv'):
                    with zipfile.ZipFile(str(prefix) + '.jar') as jar:
                        names = {n for n in jar.namelist() if not n.endswith('/')}
                    classes = {n for n in names if n.endswith('.class')}
                    generated = {n for n in classes if n.startswith('dev/elide/truffledata/csv/kernel/generated/')}
                    service = 'META-INF/services/dev.elide.truffledata.csv.CsvIndexing$NativeBackend'
                    svm = 'dev/elide/truffledata/csv/kernel/svm/SvmIndexer.class'
                    ffm = 'dev/elide/truffledata/csv/kernel/jvm/FfmIndexer.class'
                    self.assertTrue(all(n.startswith('dev/elide/truffledata/csv/') for n in classes))
                    if module == 'csv':
                        # Pure Java: one jar for the JVM and SVM, no native code, bindings or attachments.
                        self.assertIn('dev/elide/truffledata/csv/CsvBatch.class', classes)
                        self.assertIn('dev/elide/truffledata/csv/CsvRowGen.class', classes)
                        self.assertIn('dev/elide/truffledata/csv/CsvIndexing$NativeBackend.class', classes)
                        self.assertFalse(generated or svm in classes or ffm in classes or service in names)
                        self.assertEqual(artifact_suffixes(module), ('.pom', '.jar', '-sources.jar'))
                        self.assertFalse([p for p in (base / module).rglob('*') if p.suffix in ('.a', '.so', '.h')])
                    if module == 'csv-bindings':
                        self.assertIn('dev/elide/truffledata/csv/kernel/generated/CsvNative.class', classes)
                        self.assertIn('dev/elide/truffledata/csv/kernel/generated/CsvNativeFFM.class', classes)
                        self.assertIn('META-INF/native-image/dev.elide.myna/dev.elide.truffledata.csv.kernel.generated.CsvNative/native-image.properties', names)
                        self.assertEqual(classes, generated)
                    if module == 'csv-native':
                        # Static images take this jar: the FFM backend must stay out of it.
                        self.assertIn(svm, classes)
                        self.assertIn('dev/elide/truffledata/csv/kernel/svm/SvmIndexer$Backend', {n[:-6] for n in classes})
                        self.assertFalse(generated or ffm in classes)
                        with zipfile.ZipFile(str(prefix) + '.jar') as jar:
                            self.assertEqual(jar.read(service).decode().split(), ['dev.elide.truffledata.csv.kernel.svm.SvmIndexer$Backend'])
                        self.assertTrue(Path(str(prefix) + '-%s.a' % classifier).read_bytes().startswith(b'!<arch>\n'))
                        self.assertTrue(Path(str(prefix) + '-%s%s' % (classifier, extension)).is_file())
                    if module == 'csv-jvm':
                        self.assertIn(ffm, classes)
                        self.assertFalse(generated or svm in classes)
                        with zipfile.ZipFile(str(prefix) + '.jar') as jar:
                            self.assertEqual(jar.read(service).decode().split(), ['dev.elide.truffledata.csv.kernel.jvm.FfmIndexer$Backend'])
                if module == 'json':
                    self.assertTrue(Path(str(prefix) + '-%s.a' % classifier).read_bytes().startswith(b'!<arch>\n'))
                    self.assertEqual(Path(str(prefix) + '-%s-bitcode.a' % classifier).is_file(), bitcode)
                for path in (directory / version).iterdir():
                    if path.suffix in ('.pom', '.jar'):
                        self.assertEqual(Path(str(path) + '.sha256').read_text().strip(), hashlib.sha256(path.read_bytes()).hexdigest())
                metadata = directory / 'maven-metadata.xml'
                tree = ET.parse(metadata)
                ET.SubElement(tree.getroot().find('versioning/versions'), 'version').text = '0.0.1'
                tree.write(metadata)
            stage(base, repo, version, classifiers=classifiers)
            for module in MODULES:
                versions = ET.parse(repo / 'dev/elide' / ('truffledata-' + module) / 'maven-metadata.xml').findall('versioning/versions/version')
                self.assertEqual([v.text for v in versions], [version, '0.0.1'])

    def test_unsafe_versions(self):
        for version in ('../escape', '1.0.0/<tag>', '', 'latest'):
            with self.assertRaises(ValueError):
                validate_version(version)


if __name__ == '__main__':
    unittest.main()
