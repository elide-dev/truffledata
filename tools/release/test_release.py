"""Checks the real Bazel publication outputs, dependencies and metadata merging."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile

from maven_repo import artifact_suffixes, built_classifiers, CLASSIFIERS, MODULES, MUSL_MODULES, NAMESPACE, NATIVE_MODULES, stage, validate_version, TRIPLES
from check_natives import abi_fingerprints, abi_source, BINDINGS

# The bindings jars and their generated ABI class constants (check_natives.BINDINGS, by bindings module).
ABI_CLASSES = {bindings: entry for bindings, entry in BINDINGS.values()}

# The opt-in Myna Truffle cells routes: module -> (route classes, generated cells package, SVM cells class, the production
# generated bindings package the route must not repackage, the route's own extra resources).
CELLS_ROUTES = {
    'protobuf-buffa-cells': (
        ('dev/elide/truffledata/buffa/CellsSvmBackend.class', 'dev/elide/truffledata/buffa/CellsFfmBackend.class'),
        'dev/elide/truffledata/buffa/cells/generated/', 'BuffaCells', 'dev/elide/truffledata/buffa/generated/', ()),
    'json-cells': (
        ('dev/elide/truffledata/json/CellsSvm.class', 'dev/elide/truffledata/json/CellsFfm.class',
         'dev/elide/truffledata/json/JsonCellsFeature.class'),
        'dev/elide/truffledata/json/cells/generated/', 'JsonCells', 'dev/elide/truffledata/json/generated/', ()),
    'yaml-cells': (
        ('dev/elide/truffledata/yaml/CellsSvm.class', 'dev/elide/truffledata/yaml/CellsSvmSessionBackend.class',
         'dev/elide/truffledata/yaml/CellsSvmPlanBackend.class', 'dev/elide/truffledata/yaml/CellsJvmSessionBackend.class',
         'dev/elide/truffledata/yaml/CellsJvmPlanBackend.class'),
        'dev/elide/truffledata/yaml/cells/generated/', 'YamlCells', 'dev/elide/truffledata/yaml/generated/', ()),
    'csv-cells': (
        ('dev/elide/truffledata/csv/cells/CsvCellsRoute.class', 'dev/elide/truffledata/csv/cells/CellsSvmIndexer.class',
         'dev/elide/truffledata/csv/cells/CellsFfmIndexer.class', 'dev/elide/truffledata/csv/cells/CsvCellsFeature.class'),
        'dev/elide/truffledata/csv/kernel/cells/generated/', 'CsvCells', 'dev/elide/truffledata/csv/kernel/generated/',
        ('META-INF/native-image/dev.elide.truffledata/truffledata-csv-cells/native-image.properties',)),
}
# Myna's cells runtime, published once in truffledata-cells.
MYNA_TRUFFLE = 'dev/elide/myna/truffle/'
MYNA_TRUFFLE_METADATA = 'META-INF/native-image/dev.elide.myna/myna-truffle/'
# Default artifacts that must carry neither cells nor the cells runtime.
CELLS_FREE = ('protobuf-buffa', 'protobuf-buffa-bindings', 'json', 'json-bindings', 'json-jvm', 'yaml', 'yaml-bindings',
              'yaml-native', 'yaml-jvm', 'yaml-java', 'csv', 'csv-bindings', 'csv-native', 'csv-jvm')


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
        jars = {}
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
                if module not in ('core', 'cells'):
                    expected.add('dev.elide:truffledata-core')
                if module == 'cells':
                    # Myna's runtime alone: no TruffleData dependency.
                    expected.update(('org.graalvm.sdk:nativeimage', 'org.graalvm.sdk:word'))
                if module in CELLS_ROUTES:
                    expected.update(('dev.elide:truffledata-cells', 'org.graalvm.sdk:nativeimage', 'org.graalvm.sdk:word'))
                if module == 'protobuf':
                    expected.update(('com.google.protobuf:protobuf-java', 'com.google.protobuf:protobuf-java-util', 'dev.elide:truffledata-protobuf-runtime'))
                if module == 'protobuf-runtime':
                    expected.add('com.google.code.gson:gson')
                if module in ('protobuf-buffa', 'protobuf-buffa-bindings', 'protobuf-buffa-cells'):
                    expected.update(('org.graalvm.sdk:nativeimage', 'org.graalvm.sdk:word'))
                if module == 'protobuf-buffa':
                    expected.update(('dev.elide:truffledata-protobuf-runtime', 'dev.elide:truffledata-protobuf-buffa-bindings'))
                if module == 'protobuf-buffa-cells':
                    expected.update(('dev.elide:truffledata-protobuf-runtime', 'dev.elide:truffledata-protobuf-buffa',
                                     'dev.elide:truffledata-protobuf-buffa-bindings'))
                if module == 'json-cells':
                    expected.update(('dev.elide:truffledata-json', 'dev.elide:truffledata-json-bindings'))
                if module == 'yaml-cells':
                    expected.update(('dev.elide:truffledata-tape', 'dev.elide:truffledata-tape-jvm', 'dev.elide:truffledata-yaml',
                                     'dev.elide:truffledata-yaml-bindings', 'dev.elide:truffledata-yaml-jvm',
                                     'dev.elide:truffledata-yaml-native'))
                if module == 'csv-cells':
                    expected.update(('dev.elide:truffledata-csv', 'dev.elide:truffledata-csv-bindings', 'dev.elide:truffledata-csv-native'))
                if module in ('tape', 'json', 'json-bindings', 'yaml', 'yaml-bindings', 'yaml-native'):
                    expected.update(('org.graalvm.sdk:nativeimage', 'org.graalvm.sdk:word'))
                if module == 'tape-jvm':
                    expected.add('dev.elide:truffledata-tape')
                if module == 'json':
                    expected.update(('dev.elide:truffledata-json-bindings', 'dev.elide:truffledata-tape'))
                if module == 'json-jvm':
                    expected.update(('dev.elide:truffledata-json', 'dev.elide:truffledata-json-bindings', 'dev.elide:truffledata-tape-jvm'))
                if module == 'yaml':
                    expected.add('dev.elide:truffledata-tape')
                if module == 'yaml-native':
                    expected.update(('dev.elide:truffledata-yaml', 'dev.elide:truffledata-yaml-bindings'))
                if module == 'yaml-jvm':
                    expected.update(('dev.elide:truffledata-yaml', 'dev.elide:truffledata-yaml-bindings', 'dev.elide:truffledata-tape-jvm'))
                if module == 'yaml-java':
                    expected.update(('dev.elide:truffledata-yaml', 'dev.elide:truffledata-tape'))
                if module == 'yaml-snakeyaml':
                    expected.update(('dev.elide:truffledata-yaml-java', 'org.snakeyaml:snakeyaml-engine'))
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
                        'org.postgresql:postgresql', 'org.xerial:sqlite-jdbc', 'com.google.protobuf:protobuf-java', 'com.google.protobuf:protobuf-java-util',
                        'org.snakeyaml:snakeyaml-engine'}
                    self.assertEqual(root.findtext('m:packaging', namespaces=NAMESPACE), 'pom')
                    self.assertIsNone(root.find('m:dependencies', NAMESPACE))
                    self.assertFalse(Path(str(prefix) + '.jar').exists())
                metadata = json.loads(Path(str(prefix) + '.module').read_text())
                self.assertEqual(metadata['component']['version'], version)
                dependency_key = 'dependencyConstraints' if module == 'bom' else 'dependencies'
                for variant in metadata['variants'][:2]:
                    self.assertEqual({d['group'] + ':' + d['module'] for d in variant[dependency_key]}, expected)
                    self.assertEqual(variant['attributes']['org.gradle.category'], 'platform' if module == 'bom' else 'library')
                    for file in variant.get('files', []):
                        self.assertTrue((prefix.parent / file['url']).is_file())
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
                if module != 'bom':
                    with zipfile.ZipFile(str(prefix) + '.jar') as jar:
                        jars[module] = {n for n in jar.namelist() if not n.endswith('/')}
                if module in CELLS_ROUTES:
                    # Each opt-in cells route ships its route, both generated cell sets (Native Image cells over the
                    # static bindings, HotSpot cells over FFM) and the Native Image metadata of its SVM cells, but
                    # neither Myna's runtime (truffledata-cells) nor the production bindings.
                    routes, package, cells, bindings, resources = CELLS_ROUTES[module]
                    names = jars[module]
                    for name in routes + tuple(package + cells + suffix + '.class' for suffix in ('', 'Truffle', 'JvmFFM', 'JvmTruffle')) + (
                            'META-INF/native-image/dev.elide.myna/%s/native-image.properties' % (package.replace('/', '.') + cells),) + resources:
                        self.assertIn(name, names)
                    self.assertFalse({n for n in names if n.startswith(('dev/elide/myna/', bindings, MYNA_TRUFFLE_METADATA))})
                    self.assertFalse({n for n in names if n.startswith('META-INF/native-image/')}
                                     - {'META-INF/native-image/dev.elide.myna/%s/native-image.properties' % (package.replace('/', '.') + cells)}
                                     - set(resources))
                if module == 'cells':
                    # Myna's cells runtime and its metadata, and nothing else.
                    names = jars[module]
                    for name in (MYNA_TRUFFLE + 'NativeCell.class', MYNA_TRUFFLE + 'NativeCellRootNode.class',
                                 MYNA_TRUFFLE + 'NativePointer.class', MYNA_TRUFFLE_METADATA + 'native-image.properties'):
                        self.assertIn(name, names)
                    self.assertFalse({n for n in names if n.endswith('.class') and not n.startswith(MYNA_TRUFFLE)})
                    self.assertFalse({n for n in names if n.startswith('META-INF/native-image/') and not n.startswith(MYNA_TRUFFLE_METADATA)})
                if module == 'protobuf-buffa-bindings':
                    with zipfile.ZipFile(str(prefix) + '.jar') as jar:
                        self.assertIn('dev/elide/truffledata/buffa/generated/Buffa.class', jar.namelist())
                        self.assertIn('dev/elide/truffledata/buffa/generated/BuffaFFM.class', jar.namelist())
                if module in ABI_CLASSES:
                    # One bindings jar for every platform: the generated <Class>Abi carries the fingerprint of each Myna
                    # 0.14.0 target ABI class (bazel/myna.bzl), and its sources name them for the release gate.
                    with zipfile.ZipFile(str(prefix) + '.jar') as jar, zipfile.ZipFile(str(prefix) + '-sources.jar') as sources:
                        self.assertIn(ABI_CLASSES[module] + '.class', jar.namelist())
                        text = abi_source(sources, ABI_CLASSES[module])
                        self.assertIsNotNone(text, module)
                        self.assertIsNotNone(abi_fingerprints(text, jar.read(ABI_CLASSES[module] + '.class')), module)
                if module.startswith('json') or module.startswith('tape'):
                    with zipfile.ZipFile(str(prefix) + '.jar') as jar:
                        names = {n for n in jar.namelist() if not n.endswith('/')}
                    myna = {n for n in names if n.startswith('dev/elide/myna/')}
                    generated = {n for n in names if n.startswith('dev/elide/truffledata/json/generated/')}
                    backend = {n for n in names if n.startswith('dev/elide/truffledata/json/') and n not in generated}
                    shared = {n for n in names if n.startswith('dev/elide/truffledata/tape/')}
                    jvm = {'dev/elide/truffledata/json/JsonJvm.class', 'dev/elide/truffledata/json/JvmTypedOwner.class'}
                    segment = 'dev/elide/truffledata/tape/SegmentTape.class'
                    if module == 'tape':
                        # The compact reader ships with Myna interchange support; static images
                        # take it, while the FFM reader stays in the JVM artifact.
                        self.assertIn('dev/elide/truffledata/tape/TapeValue.class', names)
                        self.assertIn('dev/elide/truffledata/tape/TapeValueGen.class', names)
                        self.assertIn('dev/elide/truffledata/tape/SvmTape.class', names)
                        self.assertIn('dev/elide/myna/values/ValueTape.class', names)
                        self.assertNotIn(segment, names)
                        self.assertFalse(backend | generated)
                    else:
                        self.assertFalse(myna)
                    if module == 'tape-jvm':
                        self.assertEqual(shared, {segment, 'dev/elide/truffledata/tape/SegmentTape.class'})
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
                        self.assertEqual(artifact_suffixes(module), ('.pom', '.jar', '-sources.jar', '-javadoc.jar', '.module'))
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
                        self.assertEqual(Path(str(prefix) + '-%s.a' % classifier).read_bytes(), Path(str(prefix) + '-%s.a' % TRIPLES[classifier]).read_bytes())
                        self.assertTrue(Path(str(prefix) + '-%s%s' % (classifier, extension)).is_file())
                    if module == 'csv-jvm':
                        self.assertIn(ffm, classes)
                        self.assertFalse(generated or svm in classes)
                        with zipfile.ZipFile(str(prefix) + '.jar') as jar:
                            self.assertEqual(jar.read(service).decode().split(), ['dev.elide.truffledata.csv.kernel.jvm.FfmIndexer$Backend'])
                # Musl modules also carry target-triple copies of every attachment; other modules do not.
                for suffix in artifact_suffixes(module, classifiers):
                    if classifier not in suffix:
                        continue
                    alias = Path(str(prefix) + suffix.replace('-' + classifier, '-' + TRIPLES[classifier], 1))
                    if module in MUSL_MODULES:
                        self.assertEqual(alias.read_bytes(), Path(str(prefix) + suffix).read_bytes())
                        self.assertEqual(Path(str(alias) + '.sha256').read_text().strip(), hashlib.sha256(alias.read_bytes()).hexdigest())
                    else:
                        self.assertFalse(alias.exists())
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
            # Myna's cells runtime ships in truffledata-cells alone; no default artifact carries a route's classes,
            # its generated cells or the runtime.
            self.assertEqual({m for m, names in jars.items() if any(n.startswith(MYNA_TRUFFLE) for n in names)}, {'cells'})
            route_classes = set().union(*(jars[m] for m in CELLS_ROUTES)) - {n for n in jars['cells']}
            route_classes = {n for n in route_classes if n.endswith('.class')}
            for module in CELLS_FREE:
                self.assertFalse(jars[module] & route_classes, module)
                self.assertFalse({n for n in jars[module] if '/cells/' in n or n.startswith((MYNA_TRUFFLE, MYNA_TRUFFLE_METADATA))}, module)
            for module in CELLS_ROUTES:
                for other in set(CELLS_ROUTES) - {module}:
                    self.assertFalse({n for n in jars[module] & jars[other] if n.endswith('.class')}, (module, other))
            stage(base, repo, version, classifiers=classifiers)
            for module in MODULES:
                versions = ET.parse(repo / 'dev/elide' / ('truffledata-' + module) / 'maven-metadata.xml').findall('versioning/versions/version')
                self.assertEqual([v.text for v in versions], [version, '0.0.1'])

    def test_musl_suffixes(self):
        # CSV and YAML ship static-only musl archives (no shared library, no bitcode twin); JSON and Buffa do not.
        self.assertEqual(set(MUSL_MODULES), {'csv-native', 'yaml'})
        self.assertTrue(set(MUSL_MODULES) <= set(NATIVE_MODULES))
        for module in NATIVE_MODULES:
            suffixes = artifact_suffixes(module)
            musl = {s for s in suffixes if '-musl' in s}
            if module in MUSL_MODULES:
                self.assertEqual(musl, {'-linux-x86_64-musl.a', '-linux-aarch64-musl.a'})
            else:
                self.assertEqual(musl, set())
            self.assertEqual('-native.h' in suffixes, module in ('protobuf-buffa', 'csv-native'))
        self.assertEqual(artifact_suffixes('yaml', ('linux-aarch64-musl',)), ('.pom', '.jar', '-sources.jar', '-javadoc.jar', '.module', '-linux-aarch64-musl.a'))
        self.assertEqual(artifact_suffixes('json', ('linux-aarch64-musl',)), ('.pom', '.jar', '-sources.jar', '-javadoc.jar', '.module'))

    def test_unsafe_versions(self):
        for version in ('../escape', '1.0.0/<tag>', '', 'latest'):
            with self.assertRaises(ValueError):
                validate_version(version)


if __name__ == '__main__':
    unittest.main()
