# YAML parser probes at `b242abd` (2026-11-06)

Raw output of the temporary probes used to write `docs/superpowers/plans/2026-11-06-yaml.md`
(decision D1). The probes are not built by the repository: their sources are kept under
`sources/` for reproduction, and the temporary Bazel package (`yaml_probe/`, two `http_archive`s
and their BUILD overlays) was removed before commit.

**All timings are advisory.** Each figure is the best of 7 rounds of a tight loop on one machine,
run once, with no confidence intervals. Compare rows within one file, never absolute values across
hosts. These numbers support the parser choice only as ratios measured on the same host; the
plan's tasks re-measure through the comparison harness.

## Hosts

- **Local arm64:** Apple M5 Max (18 cores), macOS. C/C++: Homebrew clang 23.1.1, `-O3 -DNDEBUG`
  (libfyaml through its CMake `Release` config: `-O3 -DNDEBUG`). Rust: nightly 2026-10-07,
  `--release`, thin LTO, `codegen-units = 1`. Java: Oracle GraalVM 25.5.5 (JDK 25.0.4) JIT,
  `-Xmx2g`, three warm-up passes before the timed rounds.
- **RBE x86-64:** BuildBuddy Linux workers (`//bazel/platforms:rbe_linux_amd64`), the repository's
  Elide toolchain (LLVM, libc++, ThinLTO, `--compilation_mode=opt`), through
  `bazel test //yaml_probe:yaml_probe_test`. Invocations:
  - default rapidyaml configuration: <https://app.buildbuddy.io/invocation/8ea9e820-2703-4c8e-bb1f-8099267a593a>
  - selected configuration (`RYML_WITH_TAB_TOKENS`, `resolve_tags`): <https://app.buildbuddy.io/invocation/ebc195cc-e6f0-4eea-a053-ef55d6a53ecd>

## Versions

| Parser | Version | Source (SHA-256 of the archive) |
| --- | --- | --- |
| rapidyaml | 0.16.0 | `rapidyaml.v0.16.0.src.tgz` (`a0ce80a8f4211580c8703e57acb813a28ca6ec80908f6eb926be94be8e98d39d`) |
| libyaml | 0.2.5 | `yaml-0.2.5.tar.gz` (`c642ae9b75fee120b2d96c712538bd2cf283228d2337df2cf2988e3c02678ef4`) |
| libfyaml | 0.9.6 | GitHub tag archive (`2d016379a69f6cf6beaf06d12bcefe1ad1784bab28fbb41a6fa8d49d25f1bc0a`) |
| saphyr-parser / yaml-rust2 / unsafe-libyaml | 0.3.3 / 0.13.0 / 0.2.11 | crates.io |
| SnakeYAML Engine / SnakeYAML / Jackson YAML | 3.2 / 2.7 / 2.22.4 | Maven Central |
| yaml-test-suite | `data-2022-01-17` | (`ccae49c1d010d27c20d315969a97752f95b14642fc1e73755db4eb2df4bb0256`) |
| Myna (value builders) | 0.9.1 | the repository's pinned source |

## Files

- `native-arm64.txt`: `cprobe bench` (rapidyaml default config, libyaml, libfyaml), all fixtures.
  `ryml-ints(scan)` is rapidyaml's integer event handler (`src_extra`), the closest stock analogue
  of a custom handler; `ryml-ints->tape` walks those events into Myna's C++ `value_builder` in a
  monotonic arena; `ryml-tree` is the stock tree parser. libyaml and libfyaml "tape" variants
  build the same tape from their events (copying text; rapidyaml variants reference regions).
  The `records-flow-lines` section uses fresh libfyaml parsers: reusing one through
  `fy_parser_reset()` raised "flow sequence with invalid extra closing bracket" on that valid input
  (a fresh `fy-tool --testsuite` and a fresh parser accept it).
- `selected-arm64.txt`: the same rapidyaml variants built with `-DRYML_WITH_TAB_TOKENS` and run
  with `resolve_tags(true).resolve_tags_all(true)` (the configuration the plan selects).
- `longline-arm64.txt`: single-line flow documents of 5–222 KB, `NDEBUG` builds: linear.
- `longline-assertions-arm64.txt`: the same without `NDEBUG` (assertions on), transcribed from the
  first run's console: quadratic for rapidyaml (0.8 MB/s on 222 KB). This was a probe artefact
  that misled the first local table; every other file uses `NDEBUG` builds.
- `rust-arm64.txt`: saphyr-parser, yaml-rust2, unsafe-libyaml; "tape" uses Myna's Rust `Builder`
  over a reset `Bump` (text copied).
- `java-arm64.txt`: SnakeYAML Engine events (scan) and `Load.loadAllFromString` (objects),
  SnakeYAML 2.7 `loadAll`, Jackson YAML `readValues` into `JsonNode`.
- `rbe-x86-64-default.txt`, `rbe-x86-64-selected.txt`: the Bazel probe (rapidyaml and libyaml
  only; libfyaml needs a hand-written `config.h` and was not ported) on RBE.
- `conformance/`: `summary.json` and one `failures-<parser>.tsv` per parser from `conformance.py`;
  `ryml-classification.txt` / `ryml-tabs-classification.txt` re-check rapidyaml failures with
  `!!x` expanded to `tag:yaml.org,2002:x`; `ryml-tab-tokens.txt` re-runs the 9 tab/indentation
  failures with `RYML_WITH_TAB_TOKENS`.
- `sanitizers-arm64.txt`: ASan+UBSan builds (`-O1 -g`, `-fno-sanitize-recover=all`, assertions on)
  of rapidyaml (both configurations, through the event renderer), libyaml (`run-parser-test-suite`)
  and libfyaml (`fy-tool --testsuite`) over 402 suite inputs plus 11 fixtures. LeakSanitizer is not
  available on macOS arm64 and was off. No fuzzing was done.
- `buildcost-arm64.txt`: serial `-O3 -DNDEBUG` compile time and archive sizes.
- `sizes-rbe-x86-64.txt`: statically linked minimal programs (`ryml_min`, `libyaml_min`,
  `empty_min`) built on RBE, to bound the per-image cost.
- `sources/`: `cprobe.cpp` (+ `fyprobe.c`), `rprobe/` (Cargo project; its `ulyaml-events` binary was
  unsafe-libyaml's own `src/bin/run-parser-test-suite.rs`, copied unchanged apart from one lint
  allow, and is not retained), `JProbe.java`, `fixtures.py`, `longline.py`, `conformance.py`,
  `ryml_tags.py`, the build and run scripts, and the temporary Bazel files under `bazel/`.

## Fixtures (`fixtures.py`)

Deterministic, mirroring `benchmarks/comparison/JsonFixture.document(i)` (nine members: I64, short
string, F64, boolean, string list, nested map, integer list, a 60-byte note that carries quote,
backslash, control, two-byte and supplementary escapes for odd documents, null; odd documents
reverse member order):

| File | Bytes | Shape |
| --- | ---: | --- |
| `config.yaml` | 3,788 | a CI workflow: comments, flow lists, literal and folded blocks, quoted strings |
| `records-block.yaml` | 242,028 | the 1,000 records as one block sequence |
| `records-flow.yaml` | 222,485 | the same data as one-line JSON (flow YAML) |
| `records-flow-lines.yaml` | 223,487 | the same JSON with one record per line |
| `records-stream.yaml` | 216,984 | 1,000 documents separated by `---` |
| `text-heavy.yaml` | 345,370 | 200 entries with ~1 KB literal blocks, folded blocks, quoted strings |
| `oneline-{25,50,100,200}.yaml` | 5–44 K | prefixes of the JSON array, one line each |

## Conformance method (`conformance.py`)

402 cases (308 valid, 94 must fail) from `data-2022-01-17`, excluding the `name/` and `tags/`
symlink trees. A valid case passes when the parser's yaml-test-suite event stream equals
`test.event` after normalizing flow markers (`{}`/`[]`), explicit document markers (`---`/`...`)
and anchor names (renumbered by first appearance), as saphyr's and yaml-rust2's own harnesses do;
the "strict markers" column keeps flow and document markers. An error case passes when the parser
reports an error. Emitters: rapidyaml through an integer-event → test-suite renderer
(`events_ints_to_testsuite`), libyaml's `run-parser-test-suite`, libfyaml's `fy-tool --testsuite`,
unsafe-libyaml's own runner, and hand-written emitters for saphyr, yaml-rust2 (from saphyr's
harness) and SnakeYAML Engine (`Event.toString()`, which prints the test-suite form).

## Caveats

- Probe "tape" variants use Myna's generic builders, which the JSON performance plan measured as
  the dominant decode cost; a direct handler with a presized writer is expected to be faster.
- Scalar resolution in the probes is a simplified core schema (no big-integer DECIMAL, `.inf`
  stored as text); the production decoder's resolver is specified in the plan.
- The Java figures include JIT warm-up of three passes only and are not JMH measurements.
- SnakeYAML 2.7 and Jackson YAML were measured for speed only, not for conformance.
- Only one run per configuration; RBE workers vary by up to ~1.7× between invocations
  (JSON perf probe README), so x86 rows from the two invocations are not directly comparable.
