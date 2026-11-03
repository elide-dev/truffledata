# Maven publishing

Bazel produces six thin libraries and a dependency-management BOM, with source jars and POMs:

| Coordinate | Contents |
| --- | --- |
| `dev.elide:truffledata-core:<version>` | Schema, batches, interop, dynamic/static records, specialized access |
| `dev.elide:truffledata-jdbc:<version>` | JDBC adapter; depends on core |
| `dev.elide:truffledata-jdbc-sqlite:<version>` | Xerial SQLite integration; depends on JDBC, core and sqlite-jdbc |
| `dev.elide:truffledata-jdbc-mysql:<version>` | Connector/J integration; depends on JDBC, core and mysql-connector-j |
| `dev.elide:truffledata-jdbc-postgresql:<version>` | pgJDBC integration; depends on JDBC, core and postgresql |
| `dev.elide:truffledata-bom:<version>` | POM-only dependency management for the tested libraries, drivers and runtime |
| `dev.elide:truffledata-protobuf:<version>` | Protobuf adapter; depends on core and protobuf-java |

All artifacts require Java 25 and currently target Elide GraalVM `25.5.5-elide.5`. They do not bundle GraalVM, guest languages, JDBC drivers, annotation processors, or test libraries. Select the runtime and guest languages in the embedding application. The driver integration artifacts declare their upstream drivers transitively; generic JDBC remains driver independent. JSpecify annotations and the Truffle API are compile dependencies. Release dependencies are checked against `maven_install.json` by `//tools/release:test`.

## Stage and inspect

```sh
# Build publication outputs only, with the normal remote execution/cache configuration:
bazel build //tools/release:publications --//tools/release:version=0.1.0-alpha.3

# Run all checks and stage jars, POMs, metadata and checksums without network writes:
tools/release/publish.sh 0.1.0-alpha.3
```

The staging command prints a unique directory under `build/release-repo.*`. Each library artifact includes its binary jar (including generated Truffle implementations), source jar, POM, and MD5/SHA-1/SHA-256/SHA-512 checksums. Source jars come from the library's JavaInfo, rather than an independently maintained source list. The default Bazel version is `0.1.0-SNAPSHOT` for checks; the release script requires an explicit immutable version and refuses snapshots.

## Upload

The publisher follows Komodo's deployment: `rclone` remote `elide:`, bucket `elide-maven`, public URL `https://maven.elide.dev`. Provision rclone credentials outside the repository. If your configured remote has a different name, set `MAVEN_BUCKET`, for example `MAVEN_BUCKET=r2:elide-maven tools/release/publish.sh 0.1.0-alpha.3 --publish`. The publishing host needs Bash, Python 3, curl, rclone and Bazelisk; compilation and tests retain the normal hermetic Bazel toolchains and RBE settings. The small repository-layout check runs with Python 3 from the pinned remote executor image.

```sh
# Run from a clean, committed checkout. This command writes to the public repository.
tools/release/publish.sh 0.1.0-alpha.3 --publish
```

The command runs the full test suite, stages artifacts, merges existing metadata, uploads immutable payloads, verifies bucket contents and public payload bytes, then uploads metadata and checksums. Missing credentials, failed listings and malformed existing metadata fail the release; they are never interpreted as an empty repository. It neither tags nor pushes Git history. CI already exercises publication validation through `bazel test //...`; uploading is an explicit release operation, not a pull-request action.

**Serialize publishers**, including publishers on other machines. Rclone/R2 has no atomic multi-artifact transaction or cross-machine release lock. A failed upload can leave a partial version; the publisher refuses to overwrite or automatically resume that version. Inspect and repair the incomplete release before publishing again. Metadata's `latest` and `release` identify the most recently published version, so publish releases in intended discovery order. Explicit versions work independently of these fields. No signing key or Maven Central publishing is configured.

## Consume

After publishing the selected version, Gradle consumers can use:

```kotlin
repositories {
    mavenCentral()
    maven("https://maven.elide.dev")
    maven("https://maven.elide.dev/gvm-elide/25.5.5-elide.5")
}
dependencies {
    implementation("dev.elide:truffledata-jdbc:0.1.0-alpha.3")
    implementation("dev.elide:truffledata-protobuf:0.1.0-alpha.3")
}
```

For Maven, declare `https://maven.elide.dev` in `<repositories>` and the same coordinates in `<dependencies>`. Published POMs also declare the fork repository; Gradle and Bazel consumers must explicitly configure it. For Bazel, add the two repositories and desired coordinates to `maven.install`, then repin. Use core alone if neither adapter is needed. The staging examples use the proposed next prerelease `0.1.0-alpha.3`; it has not been uploaded. Core/JDBC/Protobuf `0.1.0-alpha.2` are already published. The three driver integrations and BOM are new and await the next release.


## BOM and consumer acceptance

Import `dev.elide:truffledata-bom:<version>` as a Maven `pom` dependency with
`import` scope in `dependencyManagement`, or use Gradle's
`implementation(platform("dev.elide:truffledata-bom:<version>"))`. Library and driver
dependencies can then omit versions. The BOM supplies defaults, so applications can
override upstream driver versions independently; overrides require their own
compatibility testing. It does not add drivers or guest runtimes unless selected.

`python3 tools/release/consumer.py` builds the publications with Bazel, stages an
isolated repository, imports the BOM in a real Maven project, verifies consumed
artifact bytes, compiles and runs `Consumer.java` using the Bazel-provided Java 25
SDK, and verifies an explicit PostgreSQL version override. Maven and network access
are required for this consumer acceptance check; the main build remains Bazel.
The reusable driver CI job runs it alongside real database acceptance. Pass
`--version <version>` to validate a particular proposed publication version.
