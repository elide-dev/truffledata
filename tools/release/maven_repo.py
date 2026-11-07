#!/usr/bin/env python3
"""Stage and validate thin Bazel artifacts; merge existing Maven metadata."""
import argparse
import datetime
import hashlib
from pathlib import Path
import re
import shutil
import xml.etree.ElementTree as ET
import zipfile

MODULES = ('core', 'jdbc', 'jdbc-sqlite', 'jdbc-mysql', 'jdbc-postgresql', 'protobuf', 'protobuf-runtime', 'protobuf-buffa', 'protobuf-buffa-bindings', 'tape', 'tape-jvm', 'json', 'json-bindings', 'json-jvm', 'csv', 'csv-bindings', 'csv-native', 'csv-jvm', 'bom')
NAMESPACE = {'m': 'http://maven.apache.org/POM/4.0.0'}


# Native attachment classifiers: (shared library extension, ThinLTO bitcode twin -<classifier>-bitcode.a). Mach-O has
# no fat objects, so macOS ships the static archive's members twice: machine code and bitcode. Keep in sync with
# CLASSIFIERS in bazel/maven.bzl.
CLASSIFIERS = {
    'linux-x86_64': ('.so', False),
    'linux-aarch64': ('.so', False),
    'macos-aarch64': ('.dylib', True),
}
NATIVE_MODULES = ('protobuf-buffa', 'json', 'csv-native')
# ELF e_machine per Linux classifier.
ELF_MACHINES = {'linux-x86_64': 0x3e, 'linux-aarch64': 0xb7}


def native_suffixes(classifiers=tuple(CLASSIFIERS)):
    suffixes = ()
    for classifier in classifiers:
        extension, bitcode = CLASSIFIERS[classifier]
        suffixes += ('-%s.a' % classifier,) + (('-%s-bitcode.a' % classifier,) if bitcode else ()) + ('-%s%s' % (classifier, extension),)
    return suffixes


def artifact_suffixes(module, classifiers=tuple(CLASSIFIERS)):
    if module == 'bom':
        return ('.pom',)
    common = ('.pom', '.jar', '-sources.jar')
    if module == 'protobuf-buffa':
        return common + native_suffixes(classifiers) + ('-native.h',)
    if module in NATIVE_MODULES:
        return common + native_suffixes(classifiers)
    return common


def built_classifiers(publications):
    """The classifiers whose attachments a single-platform Bazel publication build produced."""
    return tuple(c for c in CLASSIFIERS if any((publications / 'json').rglob('*-%s.a' % c)))


def validate_native(path, suffix):
    data = path.read_bytes()
    classifier = next((c for c in CLASSIFIERS if suffix in ('-%s.a' % c, '-%s-bitcode.a' % c, '-%s%s' % (c, CLASSIFIERS[c][0]))), None)
    if classifier is None:
        raise ValueError('Unknown native attachment: ' + str(path))
    if suffix.endswith('.a'):
        if not data.startswith(b'!<arch>\n'):
            raise ValueError('Invalid static archive: ' + str(path))
    elif suffix.endswith('.so'):
        if data[:6] != b'\x7fELF\x02\x01' or int.from_bytes(data[16:18], 'little') != 3 or int.from_bytes(data[18:20], 'little') != ELF_MACHINES[classifier]:
            raise ValueError('Expected a %s ELF shared library: %s' % (classifier, path))
    elif suffix.endswith('.dylib'):
        # MH_MAGIC_64, CPU_TYPE_ARM64, MH_DYLIB.
        if data[:4] != b'\xcf\xfa\xed\xfe' or int.from_bytes(data[4:8], 'little') != 0x0100000c or int.from_bytes(data[12:16], 'little') != 6:
            raise ValueError('Expected a %s Mach-O dylib: %s' % (classifier, path))


def validate_version(version):
    if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+(?:[.-][A-Za-z0-9.-]+)?', version):
        raise ValueError('Invalid version: ' + version)


def checksums(path):
    for algorithm in ('md5', 'sha1', 'sha256', 'sha512'):
        Path(str(path) + '.' + algorithm).write_text(
            hashlib.new(algorithm, path.read_bytes()).hexdigest() + '\n')


def stage(publications, repository, version, natives=(), classifiers=tuple(CLASSIFIERS)):
    """Stage every module from `publications` (one Bazel publication build) plus native attachments collected from
    other platforms' //tools/release:natives builds (`natives`: directories holding <module>_natives/dev/elide/...).
    Every attachment of `classifiers` is required; one found in several places must be byte-identical."""
    validate_version(version)
    for module in MODULES:
        artifact = 'truffledata-' + module
        relative = Path('dev/elide') / artifact
        source = publications / module / relative / version
        target = repository / relative / version
        target.mkdir(parents=True, exist_ok=True)
        for suffix in artifact_suffixes(module, classifiers):
            name = artifact + '-' + version + suffix
            candidates = [p for p in [source / name] + [Path(d) / (module + '_natives') / relative / version / name for d in natives] if p.is_file()]
            if not candidates:
                raise ValueError('Missing artifact: %s (looked in %s and %d natives directories)' % (name, source, len(natives)))
            path = candidates[0]
            for other in candidates[1:]:
                if other.read_bytes() != path.read_bytes():
                    raise ValueError('Conflicting copies of %s: %s and %s' % (name, path, other))
            if suffix == '.pom':
                root = ET.parse(path).getroot()
                for key, expected in [('groupId', 'dev.elide'), ('artifactId', artifact), ('version', version)]:
                    if root.findtext('m:' + key, namespaces=NAMESPACE) != expected:
                        raise ValueError('Incorrect POM coordinates: ' + str(path))
            elif suffix in ('.jar', '-sources.jar'):
                with zipfile.ZipFile(path) as jar:
                    names = jar.namelist()
                    if not any(n.endswith('.java' if suffix == '-sources.jar' else '.class') for n in names):
                        raise ValueError('Empty artifact: ' + str(path))
                    if any(n.startswith(('org/', 'com/')) for n in names):
                        raise ValueError('Bundled third-party classes: ' + str(path))
            elif suffix.endswith(('.a', '.so', '.dylib')):
                validate_native(path, suffix)
            elif not path.read_bytes():
                raise ValueError('Empty native header')
            shutil.copyfile(path, target / name)
            checksums(target / name)
        metadata = target.parent / 'maven-metadata.xml'
        if metadata.exists():
            root = ET.parse(metadata).getroot()
            if root.findtext('groupId') != 'dev.elide' or root.findtext('artifactId') != artifact:
                raise ValueError('Mismatched existing metadata')
        else:
            root = ET.Element('metadata')
            ET.SubElement(root, 'groupId').text = 'dev.elide'
            ET.SubElement(root, 'artifactId').text = artifact
        versioning = root.find('versioning')
        if versioning is None:
            versioning = ET.SubElement(root, 'versioning')
        versions = versioning.find('versions')
        if versions is None:
            versions = ET.SubElement(versioning, 'versions')
        if version not in [v.text for v in versions]:
            ET.SubElement(versions, 'version').text = version
        # Releases are serialized and latest/release identify the most recently published release.
        for key, value in [('latest', version), ('release', version), ('lastUpdated', datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d%H%M%S'))]:
            element = versioning.find(key)
            if element is None:
                element = ET.SubElement(versioning, key)
            element.text = value
        ET.indent(root)
        ET.ElementTree(root).write(metadata, encoding='utf-8', xml_declaration=True)
        checksums(metadata)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Stage the Bazel publications and every platform\'s native attachments.')
    parser.add_argument('publications', type=Path, help='Bazel publication directory (bazel-bin/tools/release)')
    parser.add_argument('repository', type=Path)
    parser.add_argument('version')
    parser.add_argument('--natives', type=Path, nargs='*', default=[], help='collected //tools/release:natives trees')
    parser.add_argument('--classifiers', nargs='*', default=list(CLASSIFIERS), choices=list(CLASSIFIERS))
    args = parser.parse_args()
    stage(args.publications, args.repository, args.version, args.natives, tuple(args.classifiers))
