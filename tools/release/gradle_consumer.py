#!/usr/bin/env python3
"""Consume a staged BOM/core using Gradle metadata only, including documentation variants."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import tempfile

from maven_repo import validate_version

BUILD = '''
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.gradle.api.attributes.Usage
plugins { id 'application' }
def coordinates = new groovy.json.JsonSlurper().parse(file('coordinates.json'))
repositories {
    exclusiveContent {
        forRepository {
            maven { url = uri(coordinates.repository); metadataSources { gradleMetadata() } }
        }
        filter { includeGroup 'dev.elide' }
    }
    maven { url = 'https://maven.elide.dev/gvm-elide/25.5.5-elide.28' }
    mavenCentral()
}
dependencies {
    implementation platform('dev.elide:truffledata-bom:' + coordinates.version)
    implementation 'dev.elide:truffledata-core'
}
application { mainClass = 'Consumer' }
['sources', 'javadoc'].each { kind ->
    configurations.create(kind) {
        canBeConsumed = false
        canBeResolved = true
        attributes {
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.DOCUMENTATION))
            attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, kind))
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
        }
    }
    dependencies.add(kind, 'dev.elide:truffledata-core:' + coordinates.version)
}
tasks.register('verifyMetadata') {
    dependsOn 'run'
    doLast {
        def paths = [configurations.compileClasspath, configurations.sources, configurations.javadoc]
            .collectMany { it.files }.findAll { it.name.startsWith('truffledata-core-') }
        file('resolved.json').text = groovy.json.JsonOutput.toJson(paths.collect { it.absolutePath })
    }
}
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repository', type=Path, required=True)
    parser.add_argument('--version', required=True)
    parser.add_argument('--gradle', default=os.environ.get('GRADLE', 'gradle'))
    args = parser.parse_args()
    validate_version(args.version)
    repository = args.repository.resolve()
    with tempfile.TemporaryDirectory(prefix='truffledata-gradle-') as directory:
        work = Path(directory)
        (work / 'settings.gradle').write_text("rootProject.name = 'truffledata-metadata-consumer'\n")
        (work / 'build.gradle').write_text(BUILD)
        (work / 'coordinates.json').write_text(json.dumps({'repository': repository.as_uri(), 'version': args.version}))
        source = work / 'src/main/java/Consumer.java'
        source.parent.mkdir(parents=True)
        source.write_text('''import dev.elide.truffledata.Field;
import dev.elide.truffledata.Schema;
public class Consumer {
  public static void main(String[] args) {
    Schema schema = Schema.of(Field.requiredLong("first"), Field.requiredLong("second"));
    if (schema.indexOf("second") != 1 || schema.indexOf("absent") != -1) throw new AssertionError();
    System.out.println("GRADLE_METADATA_OK");
  }
}
''')
        subprocess.run([args.gradle, '--no-daemon', '--console=plain', '--project-dir', str(work),
                        '--gradle-user-home', str(work / 'gradle-home'), 'verifyMetadata'], check=True)
        paths = [Path(p) for p in json.loads((work / 'resolved.json').read_text())]
        expected = {'truffledata-core-' + args.version + suffix for suffix in ('.jar', '-sources.jar', '-javadoc.jar')}
        if {p.name for p in paths} != expected:
            raise RuntimeError('Gradle did not select the binary/source/Javadoc variants')
        for path in paths:
            staged = repository / 'dev/elide/truffledata-core' / args.version / path.name
            if path.read_bytes() != staged.read_bytes():
                raise RuntimeError('Gradle did not consume staged bytes: ' + path.name)
        print('Gradle consumed staged metadata, BOM constraints, binary, sources and Javadocs')


if __name__ == '__main__':
    main()
