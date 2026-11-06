# Maven publishing

The release publishes twelve thin libraries and a dependency-management BOM, with source jars and POMs. The protobuf runtime can be consumed without protobuf-java. Version 0.7.0 shipped the first nine libraries; the three JSON artifacts publish from the next release, which also adds them to the BOM.

| Coordinate | Contents |
| --- | --- |
| `dev.elide:truffledata-core:<version>` | Schema, batches, interop, dynamic/static records, specialized access |
| `dev.elide:truffledata-jdbc:<version>` | JDBC adapter; depends on core |
| `dev.elide:truffledata-jdbc-sqlite:<version>` | Xerial SQLite integration; depends on JDBC, core and sqlite-jdbc |
| `dev.elide:truffledata-jdbc-mysql:<version>` | Connector/J integration; depends on JDBC, core and mysql-connector-j |
| `dev.elide:truffledata-jdbc-postgresql:<version>` | pgJDBC integration; depends on JDBC, core and postgresql |
| `dev.elide:truffledata-protobuf-buffa:<version>` | Optional native protobuf decoder and typed views; includes Linux x86-64 native attachments |
| `dev.elide:truffledata-protobuf-buffa-bindings:<version>` | Generated Myna bindings for JVM FFM and static SVM |
| `dev.elide:truffledata-json:<version>` | Native simdjson JSON values, export, snapshots and typed plans, with the static SVM backend; includes Linux x86-64 native attachments |
| `dev.elide:truffledata-json-bindings:<version>` | Generated Myna JSON bindings and the Myna value ABI runtime they were generated against |
| `dev.elide:truffledata-json-jvm:<version>` | JVM FFM backend (`JsonJvm`); depends on truffledata-json. Keep it out of native-image classpaths |
| `dev.elide:truffledata-bom:<version>` | POM-only dependency management for the tested libraries, drivers and runtime |
| `dev.elide:truffledata-protobuf-runtime:<version>` | Stock-free plans, codecs, runtime descriptor compilation and generated-record support; core, Gson and Truffle dependencies |
| `dev.elide:truffledata-protobuf:<version>` | Optional Message/descriptor compatibility bridge and build-time contract generator; depends on protobuf-runtime, protobuf-java and protobuf-java-util |

All artifacts require Java 25 and currently target Elide GraalVM `25.5.5-elide.24`. They do not bundle GraalVM, guest languages, JDBC drivers, annotation processors, or test libraries. Select the runtime and guest languages in the embedding application. The driver integration artifacts declare their upstream drivers transitively; generic JDBC remains driver independent. JSpecify annotations and the Truffle API are compile dependencies. Release dependencies are checked against `maven_install.json` by `//tools/release:test`.

## Stage and inspect

```sh
# Build publication outputs only, with the normal remote execution/cache configuration:
bazel build //tools/release:publications --//tools/release:version=0.7.0

# Run all checks and stage jars, POMs, metadata and checksums without network writes:
tools/release/publish.sh 0.7.0
```

The staging command prints a unique directory under `build/release-repo.*`. Each library artifact includes its binary jar (including generated Truffle implementations), source jar, POM, and MD5/SHA-1/SHA-256/SHA-512 checksums. Source jars come from the library's JavaInfo, rather than an independently maintained source list. The default Bazel version is `0.7.0-SNAPSHOT` for checks; the release script requires an explicit immutable version and refuses snapshots.

## Upload

The publisher follows Komodo's deployment: `rclone` remote `elide:`, bucket `elide-maven`, public URL `https://maven.elide.dev`. Provision rclone credentials outside the repository. If your configured remote has a different name, set `MAVEN_BUCKET`, for example `MAVEN_BUCKET=r2:elide-maven tools/release/publish.sh 0.7.1 --publish`. The publishing host needs Bash, Python 3, curl, rclone and Bazelisk; compilation and tests retain the normal hermetic Bazel toolchains and RBE settings. The small repository-layout check runs with Python 3 from the pinned remote executor image.

```sh
# Run from a clean, committed checkout. This command writes to the public repository.
tools/release/publish.sh 0.7.1 --publish
```

The command runs the full test suite, the explicit native JIT smoke target, and both expected-failure native safety controls before staging artifacts. Native checks use the normal Linux RBE platform. It then merges existing metadata, uploads immutable payloads, verifies bucket contents and public payload bytes, and uploads metadata and checksums. Missing credentials, failed listings and malformed existing metadata fail the release; they are never interpreted as an empty repository. It neither tags nor pushes Git history. CI already exercises publication validation through `bazel test //...`; uploading is an explicit release operation, not a pull-request action.

**Serialize publishers**, including publishers on other machines. Rclone/R2 has no atomic multi-artifact transaction or cross-machine release lock. A failed upload can leave a partial version; the publisher refuses to overwrite or automatically resume that version. Inspect and repair the incomplete release before publishing again. Metadata's `latest` and `release` identify the most recently published version, so publish releases in intended discovery order. Explicit versions work independently of these fields. No signing key or Maven Central publishing is configured.

## Consume

For version `0.7.0`, Gradle consumers can use:

```kotlin
repositories {
    mavenCentral()
    maven("https://maven.elide.dev")
    maven("https://maven.elide.dev/gvm-elide/25.5.5-elide.24")
}
dependencies {
    implementation(platform("dev.elide:truffledata-bom:0.7.0"))
    implementation("dev.elide:truffledata-jdbc-sqlite")
    implementation("dev.elide:truffledata-protobuf")
    // Select truffledata-jdbc-mysql or truffledata-jdbc-postgresql as needed.
}
```

For Maven, declare `https://maven.elide.dev` in `<repositories>` and the same coordinates in `<dependencies>`. Published POMs also declare the fork repository; Gradle and Bazel consumers must explicitly configure it. For Bazel, add the two repositories and desired coordinates to `maven.install`, then repin. Use core alone if neither adapter is needed. The release contains twelve libraries and the BOM. Publishing verifies public artifact bytes and metadata; isolated consumers check Maven resolution and JVM/static SVM execution. The staging examples can reproduce that version locally. Choose a new version for the next upload: published versions are immutable.


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

## Buffa native distribution

Since version `0.2.0`, the distribution includes `dev.elide:truffledata-protobuf-buffa` and its transitive
`truffledata-protobuf-buffa-bindings` artifact to the BOM. Java-only fallback works without
loading a native library. Native prebuilt attachments target **Linux x86-64**; other native
platforms must build their matching bindings and libraries from the Bazel source targets.

The Buffa artifact publishes these additional files, each with the normal checksums:

| Classifier | Extension | Use |
|---|---|---|
| `linux-x86_64` | `so` | Explicit JVM FFM loading through `BuffaWire.open` |
| `linux-x86_64` | `a` | Static linking into an SVM executable |
| `native` | `h` | Generated C ABI declarations |

Resolve an attachment with Maven's dependency plugin using
`dev.elide:truffledata-protobuf-buffa:0.7.0:a:linux-x86_64` (or `so`), or use the
matching classifier/type in your build. For a standalone native-image build, stage the
archive as `libtruffledata_buffa.a`, add its directory to `-H:CLibraryPath`, and enable
`--features=dev.elide.truffledata.buffa.BuffaFeature`. Keep the release's Java jars and
native attachment together: the backend verifies the generated ABI fingerprint.
No native library is automatically extracted or loaded. JVM native use requires
`--enable-native-access=ALL-UNNAMED`.

`//tools/release:buffa_consumer` tests both native and fallback binary/text/JSON ingestion
using publication jars and the published shared-library attachment. The explicit
`//tools/release:buffa_native_test` links the publication's static archive into SVM and
runs the same consumer. Both release publishing and native CI require this static check.
## JSON native distribution

The JSON backend publishes as three artifacts, all managed by the BOM:

- `truffledata-json`: the shared API (`JsonMessage`, `JsonValue`, `JavaSink`,
  snapshots, typed plans) and the static SVM backend `JsonSvm`.
- `truffledata-json-bindings`: the generated Myna bindings and Myna's value ABI classes
  (`dev.elide.myna.values`), pinned to the same Myna source as the native library. Myna is
  not published separately, and the bindings are only valid against that exact value ABI.
- `truffledata-json-jvm`: the JVM FFM backend `JsonJvm`. It is a separate artifact because
  static images link the parser directly and must not carry the FFM backend; this mirrors
  the source split between `//json` and `//json:jvm`. JVM applications add it explicitly.

`truffledata-json` carries the native attachments, each with the normal checksums:

| Classifier | Extension | Use |
|---|---|---|
| `linux-x86_64` | `so` | Explicit JVM FFM loading through `JsonJvm.open(Path)` |
| `linux-x86_64` | `a` | Static linking into an SVM executable (decoder, simdjson and C ABI in one archive) |

For a standalone native-image build, stage the archive as `libtruffledata_json.a`, add
its directory to `-H:CLibraryPath`, and link libc++ (`-lc++ -lc++abi`). No native-image
feature is required. Keep the Java jars and native attachments from the same release: both
backends verify the generated ABI fingerprint before the first decode. Nothing is extracted
or loaded automatically; JVM use requires `--enable-native-access=ALL-UNNAMED`.

`//tools/release:json_consumer` decodes through `JsonJvm` using only publication jars and
the published shared library, then reads members and exports through `JavaSink`. The
explicit `//tools/release:json_native_test` links the publication's static archive into SVM
and runs the same consumer through `JsonSvm`, without `truffledata-json-jvm` on the image
classpath.

The publisher includes all twelve Java libraries and the BOM, plus Buffa and JSON native attachments.
