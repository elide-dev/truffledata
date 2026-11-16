# Performance priorities

SVM / GraalVM Native Image is the production performance target. The JVM is a
correctness and test target. This is the user's explicit priority, reaffirmed
2026-11-08, and supersedes older plans that made JVM performance a release or
optimization-admission gate.

- Retain measured SVM improvements even when JVM throughput or latency worsens.
  Record the JVM measurements honestly; they do not veto an SVM improvement.
- Keep JVM correctness, API, ownership/lifetime, and coverage tests. Performance
  priority never authorizes changing semantics or weakening safety checks.
- Judge performance changes with paired SVM measurements and the existing SVM
  allocation, compiled-graph, and workload contracts. Distinguish native producer
  probes, instruction counts, and end-to-end SVM timings.
- A new priority does not turn an unmeasured candidate or a measured SVM loss into
  a win. Preserve existing no-PGO and prebuilt-native distribution constraints.

Current evidence and workflow checkpoints are in
`docs/superpowers/plans/2026-11-08-release-resume.md`. Follow its serialized Bazel
and stay-local rules; do not push, dispatch CI, or publish without user instruction.

# 0.10.0 release authorization

Sam subsequently authorized releasing the current work as `0.10.0` to unblock
TruffleCEL and Elide `1.6.3`. This authorizes the necessary branch push, PR/CI,
merge, signed tag, Maven/Central publication and GitHub release, followed by public
artifact verification. It supersedes the preceding stay-local/pause restriction
for this release. No additional permission round is needed for these steps.

Record measured performance gains and open gaps honestly. The remaining
performance goals are deferred from this unblock release; future approaches are
hypotheses, not committed outcomes. Standalone Buffa Cargo completion remains
follow-up work and must not be advertised as complete. Correctness, ownership,
safety, artifact integrity and existing graph/allocation contracts remain intact.

Keep bench charts current using the preserved measured data. Reuse the recent
qualification; do not repeat broad benchmarks or extensive validation without a
new change or failure that warrants it. Run focused checks for release fixes and
validate the staged/published artifacts. Resume hill-climbing after the release
as a separate tranche.

## Release completion

0.10.0 is released on Elide Maven and GitHub from signed tag `v0.10.0` at
`32a5f1946f4107831aa68d9594122de0a83c8d7a`. Normal candidate/main CI and actual
staged/public artifact checks passed. Sonatype validated the signed bundle, then
blocked publication on the organization's monthly release-size quota. Sam explicitly
selected “Finish release; defer Central.” Keep #28 open; do not retry Central or
start another hill-climbing tranche without a new instruction. #37/#38 and the
recorded performance investigations remain future work. Release receipts are in
`benchmarks/results/release-0.10.0/`.

# 0.11.0 release authorization

On 2026-11-09 Sam instructed: "i want to get truffledata out, let's settle and green
and merge and release". That authorizes settling the beat-stock-svm tranche (CSV wave
4 and WS12 merged; WS13 deferred), including the reviewed bot fixes #53–#56 and
#58–#61 (#57 was excluded: protobuf-java's generated code keeps the last map value,
and #57 deviates from that), PR/CI, merge, signed tag `v0.11.0`, Elide Maven and
GitHub publication, and public artifact verification. (Update 2026-11-09 16:14 PST: Sam
then merged #57 to main himself as `1158dc93`, so 0.11.0 includes it; the deviation from
protobuf-java generated code for invalid-then-valid entries is known and recorded.) Central stays deferred under
#28 until the quota resets (2026-12-01). The same evidence, safety and
honest-reporting rules as 0.10.0 apply.

## 0.11.0 landing and what still gates publication (2026-11-09)

Sam's later instructions on 2026-11-09, in order: "let's finish and then publish" (finish the
Myna Truffle cells adoption on all four seams first); "hold on final benchmarks, as myna is
releasing fixes in 0.16.0"; "get this landed". PR #67 therefore lands the cells routes for Buffa,
JSON, YAML and CSV with every `truffledata.<format>.cells` flag default off, on Myna 0.14.0.

Do not publish 0.11.0 until: Myna 0.16.0 is pinned and the cells routes are rebuilt on it; the
paired SVM measurements (flag on vs off) are recorded and each flag's default is ruled by Sam's
rule (default on only if cells beat the baseline on SVM); and the JSON, YAML and CSV cells
artifacts are wired into release packaging. `LEDGER.md` ("Current state") has the evidence, the
open items and the commands. After the release: re-run the benchmarks, cut new benchmark cards
for the README, and clean up the README.

## 0.11.0 release completion (2026-11-09)

Sam then instructed: "finish the release at `0.11.0`. we can benchmark it after. downstream libs
are waiting", and "keep going until it's published". 0.11.0 is released on Elide Maven and GitHub
from signed tag `v0.11.0` at `ae32c97df70795bf5e3f9ee3909a3724fd23ce1f`, on Myna 0.16.0 with all
four cells routes on `invoke` and the 29-library packaging. Every `truffledata.<format>.cells`
flag is default off: Buffa, YAML and CSV measured as ties
(`benchmarks/results/cells-flag-9570099/`); the JSON route is not timed (its probe is on branch
`bench/json-document-probe`, never built). Central validated the bundle and blocked publication on
the quota again; #28 stays open, do not retry before 2026-12-01 without instruction. Receipts are
in `benchmarks/results/release-0.11.0/`.

Open after the release: a YAML fuzz finding in rapidyaml on main (see the receipts README; not
fixed in 0.11.0); the JSON cells timing; then, as Sam listed, re-run the benchmarks, new
benchmark cards for the README, and the README cleanup. Do not start these without instruction.

# 0.12.0 release authorization (2026-11-11)

Sam instructed: "adopt latest myna plz and make main green", then "adopt the latest myna features
and push that as a pr", then "OK, let's get it green on main, re-bench, update charts preserving
style in readme, and release". That authorizes: merging #96 (Myna 0.18.0 cells ownership) after
green CI, a fresh single-host capture on unclemax of the four comparison suites at the release code
(`c378d6d8`), new README cards in the 0.11.0 card style (`tools/bench/plot_cards.py`), the release
PR/CI/merge, signed tag `v0.12.0`, Elide Maven and GitHub publication, and public artifact
verification. Central stays deferred under #28 until the quota resets (2026-12-01); do not retry
it without instruction. The same evidence, safety and honest-reporting rules apply. Every
`truffledata.<format>.cells` flag stays default off (no new paired flag measurement was taken).

## 0.12.0 release completion (2026-11-12)

0.12.0 is released on Elide Maven and GitHub from signed tag `v0.12.0` at
`c378d6d837a248f19fcc12fcff8799415695f244` (Sam: tag the green commit; notes and cards may follow on
main). At Sam's instruction the publish skipped the gates main CI had already run and kept only the
per-platform consumer gates. Central was not attempted (#28, quota resets 2026-12-01). README cards
are a fresh unclemax capture at the tag. Receipts: `benchmarks/results/release-0.12.0/`. Open: the
rapidyaml fuzz finding; adopting typed `execute*` with lent segments on the HotSpot routes (proposed,
not authorized).

# 0.13.0 release authorization (2026-11-12)

Sam instructed: "let's get both merged and a release issued. call Bebop experimental in the readme
with a pretty admonishment. Clean up the artifacts table". That authorizes: merging #101 (sanitizer
toolchain pin) and #100 (experimental Bebop) after green CI, the release PR/CI/merge, signed tag
`v0.13.0`, Elide Maven and GitHub publication, and public artifact verification. Central stays
deferred under #28 until the quota resets (2026-12-01); do not retry it without instruction. The
same evidence, safety and honest-reporting rules apply.

0.13.0 publishes `truffledata-bebop` (pure Java, experimental) as the thirtieth library. The
native Bebop validator (`//bebop/native`, `//bebop/kernel`) is not published and stays opt-in in
the repository: no paired SVM measurement exists, so there is no performance claim and no default
change. No benchmark capture was taken for this release.

Sam also instructed (2026-11-12): never add `Co-Authored-By` trailers for Claude to commits.

## 0.13.0 release completion (2026-11-12)

0.13.0 is released on Elide Maven and GitHub from signed tag `v0.13.0` at
`6b6c78d09812f54e04a9e8afa571a393390720b8`. As for 0.12.0, the publish skipped the gates main CI
had already run and kept the per-platform consumer gates. Central was not attempted (#28, quota
resets 2026-12-01). The public artifacts were verified against the staged bytes, and the public
`truffledata-bebop` jar passed a consumer check. Receipts: `benchmarks/results/release-0.13.0/`.
Open: the rapidyaml fuzz finding; for Bebop, a `.bop` parser, a set convention for `.bop` sources,
a strict rule for `@flags` enums, registration in the root source-policy and JIT-matrix tests, and
any SVM measurement (none exists, so the native validator stays unpublished and opt-in).

# 0.13.1 release authorization (2026-11-13)

Sam instructed: "let's crunch through these on an integration branch, and get it merged and
released." That authorizes: the integration branch `integration/0.14.0` (named before the version
was chosen) carrying the open fix PRs #79–#83, the doc PRs #85–#87, #99 and #103, the 0.12.0 bench
cards #98 and the rapidyaml fix for #72/#73; the release PR/CI/merge, signed tag `v0.13.1` (a
fixes-only release, so a patch version), Elide Maven and GitHub publication, and public artifact
verification. Central stays deferred under #28 until the quota resets (2026-12-01); do not retry it
without instruction. The same evidence, safety and honest-reporting rules apply. No benchmark
capture is taken for this release. Never add `Co-Authored-By` trailers for Claude.

## 0.13.1 release completion (2026-11-13)

0.13.1 is released on Elide Maven and GitHub from signed tag `v0.13.1` at
`1f3845bd60b8c658bf833f300e09caad49923c21`. Main CI and Untrusted input passed on that commit; the
publish skipped the gates they had run and kept the per-platform consumer gates. Central was not
attempted (#28, quota resets 2026-12-01). The public artifacts were verified against the staged
bytes. Receipts: `benchmarks/results/release-0.13.1/`. Open: reporting the rapidyaml finding
upstream; #89; the Bebop and performance follow-ups listed under 0.13.0.
