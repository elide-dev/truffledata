#!/usr/bin/env python3
"""Resolve the staged BOM with Maven, compile/run a consumer, and prove driver overrides."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET

from maven_repo import built_classifiers, MODULES, stage, validate_version

NS = 'http://maven.apache.org/POM/4.0.0'
ET.register_namespace('', NS)


def element(parent, name, text=None):
    child = ET.SubElement(parent, '{' + NS + '}' + name)
    child.text = text
    return child


def dependency(parent, artifact, version=None, group='dev.elide'):
    node = element(parent, 'dependency')
    element(node, 'groupId', group)
    element(node, 'artifactId', artifact)
    if version is not None:
        element(node, 'version', version)
    return node


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version', default='0.9.0-SNAPSHOT')
    args = parser.parse_args()
    validate_version(args.version)
    root = Path(__file__).resolve().parents[2]
    os.chdir(root)
    bazel = os.environ.get('BAZEL', 'bazel')
    sdk = {
        ('Darwin', 'arm64'): 'graalvm_macos_aarch64',
        ('Linux', 'x86_64'): 'graalvm_linux_amd64',
        ('Linux', 'aarch64'): 'graalvm_linux_aarch64',
    }.get((platform.system(), platform.machine()))
    if sdk is None:
        raise RuntimeError('Unsupported consumer host platform')
    subprocess.run([bazel, 'build', '//tools/release:publications', '@' + sdk + '//:jdk',
                    '--//tools/release:version=' + args.version, '--remote_download_toplevel'], check=True)
    output = Path(subprocess.check_output([bazel, 'info', 'bazel-bin'], text=True).strip())
    execution = Path(subprocess.check_output([bazel, 'info', 'execution_root'], text=True).strip())
    java_file = subprocess.check_output([bazel, 'cquery', '@' + sdk + '//:bin-java-filegroup',
                                       '--output=files'], text=True).strip()
    java = execution / java_file
    if not java.is_file():
        raise RuntimeError('Bazel SDK Java executable is missing')
    environment = dict(os.environ, JAVA_HOME=str(java.parent.parent))
    (root / 'build').mkdir(exist_ok=True)
    work = Path(tempfile.mkdtemp(prefix='maven-consumer.', dir=root / 'build'))
    # Keep failures for diagnosis; successful isolated repositories are removed below.
    print('Maven consumer workspace:', work, flush=True)
    stage(output / 'tools/release', work / 'repository', args.version,
          classifiers=built_classifiers(output / 'tools/release'))
    source = work / 'consumer/src/main/java'
    source.mkdir(parents=True)
    shutil.copyfile(root / 'tools/release/Consumer.java', source / 'Consumer.java')
    settings = work / 'settings.xml'
    settings.write_text('<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"/>')
    pom = ET.Element('{' + NS + '}project')
    for key, value in [('modelVersion', '4.0.0'), ('groupId', 'dev.elide.acceptance'),
                       ('artifactId', 'consumer'), ('version', '1')]:
        element(pom, key, value)
    element(element(pom, 'properties'), 'maven.compiler.release', '25')
    repositories = element(pom, 'repositories')
    for name, url in [('staged', (work / 'repository').as_uri()),
                      ('elide-graalvm', 'https://maven.elide.dev/gvm-elide/25.5.5-elide.27')]:
        repository = element(repositories, 'repository')
        element(repository, 'id', name)
        element(repository, 'url', url)
    managed = element(element(pom, 'dependencyManagement'), 'dependencies')
    bom = dependency(managed, 'truffledata-bom', args.version)
    element(bom, 'type', 'pom')
    element(bom, 'scope', 'import')
    deps = element(pom, 'dependencies')
    for module in MODULES:
        if module != 'bom':
            dependency(deps, 'truffledata-' + module)
    project = work / 'consumer/pom.xml'
    ET.ElementTree(pom).write(project, encoding='utf-8', xml_declaration=True)
    command = [os.environ.get('MVN', 'mvn'), '-B', '-q', '--settings', str(settings),
               '-Dmaven.repo.local=' + str(work / 'm2'), '-f', str(project)]
    subprocess.run(command + ['org.apache.maven.plugins:maven-compiler-plugin:3.14.1:compile',
                             'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath',
                             '-Dmdep.outputFile=classpath.txt'], env=environment, check=True)
    hashes = {}
    for module in MODULES:
        artifact = 'truffledata-' + module
        suffix = '.pom' if module == 'bom' else '.jar'
        path = Path('dev/elide') / artifact / args.version / (artifact + '-' + args.version + suffix)
        staged = (work / 'repository' / path).read_bytes()
        if staged != (work / 'm2' / path).read_bytes():
            raise RuntimeError('Maven did not consume the staged artifact: ' + artifact)
        hashes[artifact] = hashlib.sha256(staged).hexdigest()
    classpath = (work / 'consumer/classpath.txt').read_text().strip()
    subprocess.run([str(java), '--enable-native-access=ALL-UNNAMED', '-cp',
                    str(work / 'consumer/target/classes') + os.pathsep + classpath, 'Consumer'], check=True)
    # Resolution proof only: this override does not expand the real-server support matrix.
    dependency(managed, 'postgresql', '42.7.13', 'org.postgresql')
    ET.ElementTree(pom).write(project, encoding='utf-8', xml_declaration=True)
    subprocess.run(command + ['org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath',
                             '-Dmdep.outputFile=override-classpath.txt'], env=environment, check=True)
    jars = {Path(p).name for p in (work / 'consumer/override-classpath.txt').read_text().strip().split(os.pathsep)}
    if 'postgresql-42.7.13.jar' not in jars or 'postgresql-42.7.14.jar' in jars:
        raise RuntimeError('Explicit driver dependency management did not override the BOM')
    reports = root / 'build/reports'
    reports.mkdir(parents=True, exist_ok=True)
    (reports / 'maven-consumer.json').write_text(json.dumps({
        'version': args.version, 'artifacts_sha256': hashes,
        'consumer_executed': True, 'postgresql_resolution_override': '42.7.13',
    }, indent=2) + '\n')
    shutil.rmtree(work)
    print('Maven BOM consumer passed: staged artifact bytes, compilation, execution, driver override')


if __name__ == '__main__':
    main()
