# Maven publishing

The release publishes eighteen thin libraries and a dependency-management BOM, with source jars and POMs. The protobuf runtime can be consumed without protobuf-java. Version 0.7.0 shipped the first nine libraries; 0.8.0 adds the two shared tape artifacts, the three JSON artifacts and the four CSV artifacts, all in the BOM.

| Coordinate | Contents |
| --- | --- |
| `dev.elide:truffledata-core:<version>` | Schema, batches, interop, dynamic/static records, specialized access |
| `dev.elide:truffledata-jdbc:<version>` | JDBC adapter; depends on core |
| `dev.elide:truffledata-jdbc-sqlite:<version>` | Xerial SQLite integration; depends on JDBC, core and sqlite-jdbc |
| `dev.elide:truffledata-jdbc-mysql:<version>` | Connector/J integration; depends on JDBC, core and mysql-connector-j |
| `dev.elide:truffledata-jdbc-postgresql:<version>` | pgJDBC integration; depends on JDBC, core and postgresql |
| `dev.elide:truffledata-protobuf-buffa:<version>` | Optional native protobuf decoder and typed views; includes [prebuilt natives](#prebuilt-natives) |
| `dev.elide:truffledata-protobuf-buffa-bindings:<version>` | Generated Myna bindings for JVM FFM and static SVM |
| `dev.elide:truffledata-tape:<version>` | Format-neutral native tape layer shared by JSON and YAML: owners, values, sinks, export, snapshots, sessions, streams and typed records, plus the Myna value ABI runtime they extend; depends on core |
| `dev.elide:truffledata-tape-jvm:<version>` | JVM FFM tape reader (`SegmentValueTape`); depends on truffledata-tape. Keep it out of native-image classpaths |
| `dev.elide:truffledata-json:<version>` | Native simdjson JSON entry points (`JsonSession`, `JsonMessage`, `JsonStream`, typed plans), with the static SVM backend; depends on truffledata-tape; includes [prebuilt natives](#prebuilt-natives) |
| `dev.elide:truffledata-json-bindings:<version>` | Generated Myna JSON bindings |
| `dev.elide:truffledata-json-jvm:<version>` | JVM FFM backend (`JsonJvm`); depends on truffledata-json and truffledata-tape-jvm. Keep it out of native-image classpaths |
| `dev.elide:truffledata-csv:<version>` | Strict streaming CSV batches, typed plans and export; pure Java for JVM and SVM, no native attachments; depends on core |
| `dev.elide:truffledata-csv-bindings:<version>` | Generated Myna bindings of the native CSV structural-index kernel (static SVM and JVM FFM) |
| `dev.elide:truffledata-csv-native:<version>` | Optional native CSV kernel: the static SVM backend; includes [prebuilt natives](#prebuilt-natives); depends on truffledata-csv and its bindings |
| `dev.elide:truffledata-csv-jvm:<version>` | JVM FFM backend of the native CSV kernel; depends on truffledata-csv and its bindings. Keep it out of native-image classpaths |
| `dev.elide:truffledata-bom:<version>` | POM-only dependency management for the tested libraries, drivers and runtime |
| `dev.elide:truffledata-protobuf-runtime:<version>` | Stock-free plans, codecs, runtime descriptor compilation and generated-record support; core, Gson and Truffle dependencies |
| `dev.elide:truffledata-protobuf:<version>` | Optional Message/descriptor compatibility bridge and build-time contract generator; depends on protobuf-runtime, protobuf-java and protobuf-java-util |

All artifacts require Java 25 and currently target Elide GraalVM `25.5.5-elide.24`. They do not bundle GraalVM, guest languages, JDBC drivers, annotation processors, or test libraries. Select the runtime and guest languages in the embedding application. The driver integration artifacts declare their upstream drivers transitively; generic JDBC remains driver independent. JSpecify annotations and the Truffle API are compile dependencies. Release dependencies are checked against `maven_install.json` by `//tools/release:test`.

## Stage and inspect

```sh
# Build publication outputs only, with the normal remote execution/cache configuration:
bazel build //tools/release:publications --//tools/release:version=0.8.0

# Run all checks and stage jars, POMs, metadata and checksums without network writes:
tools/release/publish.sh 0.8.0
```

The staging command builds the prebuilt natives of all three platforms (Linux amd64 and arm64 on BuildBuddy RBE, macOS locally, so run it on an Apple silicon Mac; see [Prebuilt natives](#prebuilt-natives)), gates them with `tools/release/check_natives.py`, and prints a unique directory under `build/release-repo.*`; the collected per-platform build outputs and member manifests stay beside it in `build/release-repo.*.natives`. Each library artifact includes its binary jar (including generated Truffle implementations), source jar, POM, and MD5/SHA-1/SHA-256/SHA-512 checksums. Source jars come from the library's JavaInfo, rather than an independently maintained source list. The default Bazel version is `0.8.0-SNAPSHOT` for checks; the release script requires an explicit immutable version and refuses snapshots.

## Upload

The publisher follows Komodo's deployment: `rclone` remote `elide:`, bucket `elide-maven`, public URL `https://maven.elide.dev`. Provision rclone credentials outside the repository. If your configured remote has a different name, set `MAVEN_BUCKET`, for example `MAVEN_BUCKET=r2:elide-maven tools/release/publish.sh 0.7.1 --publish`. The publishing host needs Bash, Python 3, curl, rclone and Bazelisk; compilation and tests retain the normal hermetic Bazel toolchains and RBE settings. The small repository-layout check runs with Python 3 from the pinned remote executor image.

```sh
# Run from a clean, committed checkout. This command writes to the public repository.
tools/release/publish.sh 0.7.1 --publish
```

The command runs the full test suite, the explicit native JIT smoke target, and both expected-failure native safety controls before staging artifacts. Native checks use the normal Linux RBE platform; the per-platform native gates and the native release check are described under [Prebuilt natives](#prebuilt-natives). It then merges existing metadata, uploads immutable payloads, verifies bucket contents and public payload bytes, and uploads metadata and checksums. Missing credentials, failed listings and malformed existing metadata fail the release; they are never interpreted as an empty repository. It neither tags nor pushes Git history. CI already exercises publication validation through `bazel test //...`; uploading is an explicit release operation, not a pull-request action.

**Serialize publishers**, including publishers on other machines. Rclone/R2 has no atomic multi-artifact transaction or cross-machine release lock. A failed upload can leave a partial version; the publisher refuses to overwrite or automatically resume that version. Inspect and repair the incomplete release before publishing again. Metadata's `latest` and `release` identify the most recently published version, so publish releases in intended discovery order. Explicit versions work independently of these fields. No signing key or Maven Central publishing is configured.

## Consume

For version `0.8.0`, Gradle consumers can use:

```kotlin
repositories {
    mavenCentral()
    maven("https://maven.elide.dev")
    maven("https://maven.elide.dev/gvm-elide/25.5.5-elide.24")
}
dependencies {
    implementation(platform("dev.elide:truffledata-bom:0.8.0"))
    implementation("dev.elide:truffledata-jdbc-sqlite")
    implementation("dev.elide:truffledata-protobuf")
    // Select truffledata-jdbc-mysql or truffledata-jdbc-postgresql as needed.
}
```

For Maven, declare `https://maven.elide.dev` in `<repositories>` and the same coordinates in `<dependencies>`. Published POMs also declare the fork repository; Gradle and Bazel consumers must explicitly configure it. For Bazel, add the two repositories and desired coordinates to `maven.install`, then repin. Use core alone if neither adapter is needed. The release contains eighteen libraries and the BOM. Publishing verifies public artifact bytes and metadata; isolated consumers check Maven resolution and JVM/static SVM execution. The staging examples can reproduce that version locally. Choose a new version for the next upload: published versions are immutable.


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
loading a native library. Prebuilt attachments ship for Linux x86-64, Linux AArch64 and macOS
AArch64 ([Prebuilt natives](#prebuilt-natives)); other platforms must build their matching
libraries from the Bazel source targets.

The Buffa artifact publishes these additional files, each with the normal checksums:

| Classifier | Extension | Use |
|---|---|---|
| `<platform>` | `so` / `dylib` | Explicit JVM FFM loading through `BuffaWire.open` |
| `<platform>` | `a` | Static linking into an SVM executable |
| `macos-aarch64-bitcode` | `a` | ThinLTO links on macOS (ld64.lld, LLVM 23 or newer) |
| `native` | `h` | Generated C ABI declarations |

Resolve an attachment with Maven's dependency plugin using
`dev.elide:truffledata-protobuf-buffa:0.8.0:a:linux-x86_64` (or `so`, or another platform), or use the
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

The JSON backend publishes as three artifacts on top of the two shared tape artifacts, all
managed by the BOM:

- `truffledata-tape`: the format-neutral classes JSON and YAML share (`TapeValue`,
  `TapeSink`, `JavaSink`, `TruffleSink`, `TapeSnapshot`, `TapeSession`, `TapeStream`,
  `TypedRecord`, `TypedAccess`, ...; see [Shared tape types](JSON.md#shared-tape-types)) and
  Myna's value ABI classes (`dev.elide.myna.values`), which its tapes extend. Myna is not
  published separately; the classes ship once here, pinned to the same Myna source as every
  native library of the release, so the format artifacts never carry two copies.
- `truffledata-tape-jvm`: the JVM FFM tape reader. Like `truffledata-json-jvm`, static images
  must not carry it.
- `truffledata-json`: the JSON API (`JsonMessage`, `JsonSession`, `JsonStream`, `JsonPlan`,
  `JsonLimits`) and the static SVM backend `JsonSvm`.
- `truffledata-json-bindings`: the generated Myna bindings, valid only against the Myna value
  ABI that `truffledata-tape` of the same release carries.
- `truffledata-json-jvm`: the JVM FFM backend `JsonJvm`. It is a separate artifact because
  static images link the parser directly and must not carry the FFM backend; this mirrors
  the source split between `//json` and `//json:jvm`. JVM applications add it explicitly.

`truffledata-json` carries the native attachments, each with the normal checksums:

| Classifier | Extension | Use |
|---|---|---|
| `<platform>` | `so` / `dylib` | Explicit JVM FFM loading through `JsonJvm.open(Path)` |
| `macos-aarch64-bitcode` | `a` | ThinLTO links on macOS (ld64.lld, LLVM 23 or newer) |
| `<platform>` | `a` | Static linking into an SVM executable (decoder, simdjson, simdutf, C ABI and Myna's `myna-values` Rust static library, whose `myna_utf8_to_utf16_v2` the SVM tape calls for long strings, in one archive) |

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

## CSV distribution

`dev.elide:truffledata-csv` is a single pure-Java artifact (jar, sources jar and POM)
managed by the BOM. It has no native attachments, no classifier variants and no
native-image feature: the same jar serves the JVM and static SVM images, with the
built-in `java-swar` structural-index kernel. Its POM depends only on `truffledata-core`,
`truffle-api` and `jspecify`. See the [CSV backend](CSV.md).

The native AVX-512/AVX2/NEON kernel is optional and ships as three more artifacts, all
managed by the BOM, mirroring the JSON split:

- `truffledata-csv-bindings`: the generated Myna bindings (`CsvNative` for static images,
  `CsvNativeFFM` for the JVM) and their native-image properties.
- `truffledata-csv-native`: the static-image backend (`SvmIndexer`), registered as a
  `CsvIndexing.NativeBackend` service, plus the native attachments below.
- `truffledata-csv-jvm`: the JVM FFM backend (`FfmIndexer`), also a service. Static images
  link the kernel directly and must not carry it.

`truffledata-csv-native` carries the native attachments, each with the normal checksums:

| Classifier | Extension | Use |
|---|---|---|
| `<platform>` | `so` / `dylib` | JVM FFM loading through `CsvIndexing.loadNative(Path)` |
| `macos-aarch64-bitcode` | `a` | ThinLTO links on macOS (ld64.lld, LLVM 23 or newer) |
| `<platform>` | `a` | Static linking into an SVM executable (kernels, C ABI and simdutf in one archive) |

Opting in is explicit; nothing is extracted, loaded or installed automatically:

```java
// Native image: add truffledata-csv-native and link the archive.
CsvIndexing.install(CsvIndexing.loadNative());
// JVM: add truffledata-csv-jvm and run with --enable-native-access=ALL-UNNAMED.
CsvIndexing.install(CsvIndexing.loadNative(Path.of("truffledata-csv-native-<version>-linux-x86_64.so")));
```

For a standalone native-image build, stage the archive as `libtruffledata_csv.a`, add its
directory to `-H:CLibraryPath`, and link libc++ (`-lc++ -lc++abi`). No native-image
feature is required. Keep the Java jars and native attachments from the same release:
both backends verify the generated ABI fingerprint before the first index call.
`CsvIndexing.loadNative` throws `IllegalStateException` when no backend for the runtime is
on the class path or the library cannot be loaded.

`//tools/release:csv_consumer` parses, streams, compiles a typed plan, reads typed values
and exports using only the publication jars on the JVM. The explicit
`//tools/release:csv_native_test` builds the same consumer into a static image (Linux
x86-64, through RBE) with the Truffle JIT checks and runs it.
`//tools/release:csv_kernel_consumer` loads the published shared library through
`truffledata-csv-jvm`, and the explicit `//tools/release:csv_kernel_native_test` links the
published static archive into an image through `truffledata-csv-native` (without
`truffledata-csv-jvm`); both check that nothing native loads before `loadNative`, then
compare generic, typed and streamed parses and malformed-input diagnostics with the
built-in kernel's. Release publishing and native CI require both static checks.

The publisher includes all eighteen Java libraries and the BOM, plus Buffa, JSON and CSV native attachments.

## Prebuilt natives

`truffledata-protobuf-buffa`, `truffledata-json` and `truffledata-csv-native` ship their native
library prebuilt for three platforms, each as a self-contained static archive and a shared
library (`<platform>` in the tables above):

| Platform | Static archive | LTO input | Shared library | Built on |
|---|---|---|---|---|
| `linux-x86_64` | `-linux-x86_64.a` (fat objects) | the same archive | `-linux-x86_64.so` | BuildBuddy RBE, `--config=remote` |
| `linux-aarch64` | `-linux-aarch64.a` (fat objects) | the same archive | `-linux-aarch64.so` | BuildBuddy arm64 RBE, `--config=remote_arm64` |
| `macos-aarch64` | `-macos-aarch64.a` (machine code) | `-macos-aarch64-bitcode.a` | `-macos-aarch64.dylib` | an Apple silicon Mac, `--config=local` |

**Linux archives hold fat objects.** Every member carries machine code and, in an
`SHF_EXCLUDE` `.llvm.lto` section, the same code as LLVM 23 ThinLTO bitcode (clang's
`-flto=thin -ffat-lto-objects`, Rust included). Link it like any static archive and the
linker uses the machine code; link with clang/ld.lld and `-flto=thin` and lld reads the
bitcode, so our code inlines into yours across the C/C++ and Rust boundary. Other linkers
(GNU ld, gold, Native Image) see ordinary objects. The only members without bitcode are
Rust's `compiler_builtins` and the compiler-rt objects it bundles, which rustc never LTOs.

**macOS ships two archives.** Mach-O has no fat object format (LLVM embeds LTO bitcode
only in ELF and COFF objects, and neither ld64 nor ld64.lld reads `__LLVM,__bitcode` for
LTO), so `-macos-aarch64.a` is machine code for any linker, including Apple's, and
`-macos-aarch64-bitcode.a` holds the same members as ThinLTO bitcode. LTO on macOS
therefore needs **lld**: link the bitcode archive with clang `-flto=thin -fuse-ld=lld`
(ld64.lld) from LLVM 23 or newer. Apple's linker and Apple clang reject LLVM 23 bitcode
(summary version 14). The `compiler_builtins` members are machine code in both archives.
LLVM inlines our functions into yours only when their CPU tuning is no wider than your
code's: the bitcode is tuned as clang 23 tunes by default (`apple-m5` on macOS, generic on
Linux), so build with that LLVM's default or a matching `-mtune`.

Shared libraries need only the platform's C runtime: on Linux libc, libm, libpthread,
libdl, librt, the dynamic loader and libgcc_s (libc++ is linked in); on macOS libSystem and
the OS's libc++ (`/usr/lib/libc++.1.dylib`).
Each `.dylib` has the install name `@rpath/libtruffledata_<name>.dylib`: stage it under
that name in a directory on your executable's `LC_RPATH`, or load it by path (as the JVM
backends do). Each static archive needs libc++ (`-lc++ -lc++abi` on Linux, `-lc++` on
macOS) from the consumer's link, plus `-ldl -lpthread -lm` on Linux.

**Release gate.** `tools/release/publish.sh` builds `//tools/release:natives` once per
platform (`--config=remote`, `--config=remote_arm64`, `--config=local`), copies each
result out, and stages all of them with the publications (`maven_repo.py --natives`);
outside an Apple silicon Mac, set `MACOS_NATIVES` to a macOS natives build output. Then
`tools/release/check_natives.py`, with the toolchain's `llvm-readobj`, `llvm-objdump`,
`llvm-nm`, `llvm-ar` and `llvm-bcanalyzer`, checks every attachment of the staged
repository against the member manifests that `release_archive` writes, and fails the
release if an attachment is missing, a Linux archive member is not fat (machine code and a
ThinLTO-summarized `.llvm.lto`, except the exemptions above), a macOS machine-code member
lacks machine code or still carries bitcode, a macOS bitcode member is not ThinLTO
bitcode, a shared library needs a library or binds an undefined symbol outside the
platform allowlist above (Rust std's weak probes for newer glibc functions excepted), or a dylib's install name is not `@rpath/...`, or any platform's library lacks the ABI fingerprint
that the published bindings jar verifies. (Myna currently folds the target triple into that
fingerprint and the bindings are generated once, for linux-x86_64, so this check fails the
linux-aarch64 and macos-aarch64 natives until that is resolved.)

**Consumers per platform.** `//tools/release:native_consumers` links each release static
archive into a C program with LTO off and with ThinLTO (on macOS, the bitcode archive with
ld64.lld), checks from the disassembly that ThinLTO inlined the archive's ABI fingerprint
function into `main` and that the plain link calls the archive's machine code, runs both,
and loads the shared library with `dlopen`. `publish.sh` runs them, with the JVM consumers
of the shared libraries (`buffa_consumer`, `json_consumer`, `csv_kernel_consumer`), on
linux-aarch64 RBE and locally on macOS; linux-x86_64 runs them in `bazel test //...`.
