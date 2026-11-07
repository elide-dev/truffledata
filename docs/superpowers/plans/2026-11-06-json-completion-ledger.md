# SDD ledger — plan: docs/superpowers/plans/2026-11-06-json-completion.md
Setup: Ruling: working in place on branch feat/json-completion (no worktree) — user is not available to consent and the checkout was clean; a worktree would force a cold Bazel analysis — cost if wrong: none to main, only branch hygiene.
Pre-flight: Task 4 produces JsonOwner permits JsonMessage only; Task 6 extends permits — consistent.
Pre-flight: Task 5 JsonDecoder.adopt(MemorySegment,long,Runnable) consumed by Task 6/Task 15 — consistent after plan fix.
Pre-flight: Task 2 renames lexical_tokens()→lexical_spans(); Task 4 consumes JsonResult.lexical — consistent.
Pre-flight: Task 12 renames JsonScalarPlan/Record/Access before Task 13–15 use JsonPlan/JsonRecord/JsonAccess — consistent; Tasks 4–11 keep old names.
Pre-flight: Task 13 JsonScalarField grows to 88 bytes; Task 14 Java writer must use regenerated constants — consistent.
Task 1: complete (commits ee78001..d4a087e, tests: bazel test //json/native:test //json/native:api_test → INFO: Streaming build results to: https://app.buildbuddy.io/invocation/a87130c9-0fa8-4d86-8334-277bd47f3e42)
Task 2: Ruling: simdjson classifies -0 as signed integer 0; decoder emits F64 -0.0 when the integer is zero and the token starts with '-' — JSON.parse semantics need negative zero — cost if wrong: one branch in the number path.
Task 2: complete (commits d4a087e..ef737e5, tests: bazel test //json/native:test //json/native:api_test //json/tests:jvm → INFO: Streaming build results to: https://app.buildbuddy.io/invocation/aa2fa4af-834c-4975-8836-019264e3f96e)
Task 3: complete (commits ef737e5..74b7ed8, tests: bazel test //json/native:api_test //json/native:test → INFO: Streaming build results to: https://app.buildbuddy.io/invocation/72519c75-873c-4f63-9434-ee85c0920cfa)
Task 4: complete (commits 74b7ed8..82d47bb, tests: bazel test //json/tests:native_test → INFO: Streaming build results to: https://app.buildbuddy.io/invocation/73bff80f-b6ba-428c-a246-b94461f7bdec)
Task 5: Ruling: JsonLimits is its own public record file rather than nested in JsonDecoder.java — public API types need their own compilation units for javadoc/sources jars — cost if wrong: one import.
Task 5: complete (commits 82d47bb..e6ae353, tests: bazel test //json/tests:jvm → INFO: Streaming build results to: https://app.buildbuddy.io/invocation/cfae840c-371c-45f3-8721-5b189df6d5c8)
Task 6: Ruling: keyAt/memberAt (planned for Task 7) landed here because the snapshot test needs ordered key access — cost if wrong: none, Task 7 keeps the array index work.
Task 6: complete (commits e6ae353..e496f75, tests: bazel test //json/tests:jvm → INFO: Streaming build results to: https://app.buildbuddy.io/invocation/0bf55ac4-a636-4b13-ac8c-bfa27e0405ea)
Task 7: complete (commits e496f75..f279a3d, tests: bazel test //json/tests:jvm → INFO: Streaming build results to: https://app.buildbuddy.io/invocation/d63b415b-2cfc-49f1-b7cf-c350e32066ce)
Task 8: Ruling: JsonValue primitive reads keep an inlined owner.check() (final fields on JsonOwner, no virtual dispatch) instead of a check-free variant — a cached CheckOpenNode cannot replace per-read checks without an unsafe API — cost if wrong: one field load and compare per read.
Task 8: Ruling: array-index cache is a ConcurrentHashMap (security review found the HashMap racy on shared snapshots) — cost if wrong: none; computeIfAbsent publishes complete arrays.
Task 8: complete (commits f279a3d..eb79330, tests: bazel test //json/tests:jvm → INFO: Streaming build results to: https://app.buildbuddy.io/invocation/d1d99851-cb69-4cd1-8653-91426bfcbfb2)

Task 9: Ruling: JsonOwner.tag reads the raw storage tag through TapeSource.nodeInt instead of ValueTape.tag — SVM runtime compilation could not resolve the deopt target of the trivially inlined ValueTape.tag one-liner (ValueTape.tag%%D not seen during parsing); masking with KIND_MASK equals tag() for ABI v2 and v3 — cost if wrong: one bounds check duplicated from ValueTape.
Task 9: Ruling: interop number exports keep the I64/F64 fast paths inline and route every other kind through static (owner, node) boundaries; member reads cache Utf8Key per name identity (limit 3) — instance boundaries let fresh views escape and getBytes allocated per read (compiled interop gate measured 96 then 48 B/read, now 3.2E-5) — cost if wrong: none.
Task 9: Ruling: member names with unpaired surrogates match nothing (previously getBytes encoded them lossily as "?", matching a real "?" key) — cost if wrong: none; strict JSON keys are valid UTF-8.
Task 9: Fixed WIP test bugs: InteropReads checksum is 2002000000 (b is false), and the SvmAcceptance asLong-on-object check now fails if no exception is thrown.
Task 9: complete (tests: bazel test //json/tests:jvm //json/tests:native_test → INFO: Streaming build results to: https://app.buildbuddy.io/invocation/ — see commit)
Task 10: Ruling: JsonExportNode.execute takes (JsonValue, JsonSink) instead of (JsonOwner, long, JsonSink) — owner and node are package-private, so the planned signature was unusable outside the package — cost if wrong: one overload.
Task 10: Ruling: an export pins its owner and close() throws while pinned — a sink is arbitrary embedder code and closing mid-walk would free the native tape under the loop — cost if wrong: an embedder must defer close to after export.
Task 10: Ruling: the outermost export on a confined owner reuses an owner-held container stack (129 slots: root plus the decoder's 128 levels); nested and snapshot exports allocate one — the compiled export gate requires zero walk allocation (measured 3.2E-5 B/export) — cost if wrong: ~1 KiB retained per exported message.
Task 10: complete (tests: bazel test //json/tests:jvm (27 tests) //json/tests:native_test)
Execution: Tasks 11–19 run as parallel Opus workers in isolated worktrees (max 3), merged onto feat/json-completion by the orchestrator; workers report rulings and the orchestrator records them here.
Task 11: Ruling: docs follow the code where it differs from the plan (export signature/stack size, inline owner.check, no public UTF-8 copy outside asTruffleString, snapshots keep regions at original offsets, ConcurrentHashMap array index, adopt() uses JsonLimits.DEFAULT, typed path uses fixed 64 MiB caps) — cost if wrong: doc edits.
Task 11: complete (commit 6a94025, tests: bazel test //tests:test → https://app.buildbuddy.io/invocation/9c76000e-e14d-4c1d-a248-1d865664a135)
Task 11 follow-up: Ruling: integral DECIMAL fits BigInteger only up to 4096 integer digits, decided after stripTrailingZeros, and -0 fits no integer type — the docs review found fitsInLong on 1E999999999 would expand 10^999999999 (a hang or OOM driven by guest input), and Truffle's double contract excludes -0 from integer types — cost if wrong: integral DECIMAL values beyond 4096 digits must be read through numberText (commit 6dcf286).
Task 12: Ruling: PERFORMANCE_REVIEW.md links were renamed, but benchmarks/results/** evidence and the plan keep the old names — retained raw evidence must match what was measured — cost if wrong: text-only follow-up.
Task 12: complete (commit c6e7abc, tests: bazel test //json/tests:jvm //json/tests:native_test //benchmarks/comparison:json_native_smoke → https://app.buildbuddy.io/invocation/0e998e98-b055-4d15-a003-9ef6bb98ca1a on the integrated branch)
Task 13: Ruling: flat plans publish the root record entry from result control storage — keeps exactly one index allocation for flat results, so existing accounting assertions hold — cost if wrong: one branch in records()/record_count().
Task 13: Ruling: JsonRecordPlan entries must partition the field table in order; plans are capped at 65536 records and 64 fields per record; depth ≤ 32 counts the root as 1 and is checked from every record — a bounded, simple creation check — cost if wrong: loosening later is easy.
Task 13: Ruling: repeated arrays are pre-counted with count_elements() so element entries and slots are contiguous and reserved once — cost if wrong: one extra scan per array.
Task 13: Ruling: no seam ABI number bump (abi=4 as planned); the seam-hash fingerprint already rejects stale bindings. bazel/myna_contract.py's expected set gains decode_adopted (missing since Task 3) — cost if wrong: none.
Task 13: complete (commit c6bd959, tests: native typed/api/test + ASan/UBSan → https://app.buildbuddy.io/invocation/51dfba37-8490-41d1-9f88-d29016efbbb4; integrated lanes → https://app.buildbuddy.io/invocation/38dda529-9cdd-4f21-83e7-23bb0957a94b)
Task 18: Ruling: //json:jvm publishes as a separate truffledata-json-jvm — keeps the FFM backend out of static images, and the publication rule packages exactly one library — cost if wrong: one extra BOM-managed coordinate for JVM users.
Task 18: Ruling: Myna value classes ship inside truffledata-json-bindings — Myna is not on any Maven repository, and the bindings are only valid for that exact value ABI — cost if wrong: duplicate classes if Myna is published later; drop them and add a POM dependency then.
Task 18: Ruling: truffledata-json attaches a -linux-x86_64.a static archive as well as the .so — JsonSvm binds with requireStatic — cost if wrong: one extra file per release. The docs say the JSON artifacts publish from the next release (0.7.0 is already out).
Task 18: complete (commit fb55da3, tests: //tools/release:test buffa_native_test json_native_test json_consumer → https://app.buildbuddy.io/invocation/af0ad54f-e75b-4ef0-be1a-5a1d4c5732e7)
Task 17: Ruling: JavaSink export budget is 2,400 B/doc (measured 2,357 at peak); the interop read loop and export walk are held to ≤ 1.0 B/doc (harness per-call cost) — cost if wrong: the budget is re-measured.
Task 17: Ruling: the trace "no call" column counts only unresolved calls, not @TruffleBoundary calls (this also applies to the scalar gate); the export root has two loops (document loop + tape walk); four JSON simulation cases were added because CodSpeed runs every cases.json entry by name; results live in json-generic-c6e7abc with the measured source kept as harness.patch — cost if wrong: gate/text follow-ups.
Task 17: Finding: the generic path is slower than Gson on the 32-doc fixture (peak interop ~2.7 µs/doc vs Gson 0.5 µs), and ingest is not faster, with far less Java heap allocation; snapshot reads allocated 22.7 KB/doc and interop array reads went through an instance boundary.
Task 17: complete (commit c1de21c, tests: json_native_smoke → https://app.buildbuddy.io/invocation/882ab314-bd5c-4135-8be2-6528ae0a0761, json_measure → https://app.buildbuddy.io/invocation/9e5ec883-f49d-4db9-9365-c27cc2fe4dec)
Task 17 follow-up: Ruling: HeapValueTape stores nodes as three long words instead of byte[]+VarHandle, readArrayElement uses a static element boundary, and owners keep a one-entry most-recent array index — snapshot peak reads went from 22,749 to 1.0 B/doc; new CompiledArrayReads gate (message + snapshot, ≤ 0.05 B/read) — cost if wrong: none (commit after c1de21c).
Task 14: Ruling: record-type schemas must have typeName equal to their recordTypes key (core Values.validate checks it); ARRAY length limits and nullable/optional elements are rejected at compile time (the ABI has none); arrays of arrays are rejected; close() on any record closes the shared owner; snapshots use generous Limits since native caps already bound size; compiled reads check an inlined open field — cost if wrong: API loosening later.
Task 14: complete (commit 7427b0c, tests: integrated lanes → https://app.buildbuddy.io/invocation/b8670a0d-4c75-486a-aa99-682952c2983e)
Task 15: Ruling: backends split through an abstract JsonTypedOwner (SvmTypedOwner in //json, JvmTypedOwner in //json:jvm) and a shared JsonPlan.Layout table writer — keeps FFM out of //json and devirtualizes on SVM (gates unchanged at 3.2E-5 B/read) — cost if wrong: one virtual dispatch per slot read where it does not devirtualize.
Task 15: Ruling: JVM typed decode copies input into a temporary confined arena (FFM downcalls cannot take heap arrays); a JVM plan rejects decode after its decoder closes while records already returned stay valid; static JsonPlan.compile stays SVM-only and throws on JVM — cost if wrong: one extra input copy per JVM decode.
Task 15: complete (commit 3e2367b, tests: integrated lanes → https://app.buildbuddy.io/invocation/4ec281d8-ffa2-4bd6-929b-9e2b3da87e66; the release test now also checks JvmTypedOwner stays out of truffledata-json)
Task 16: Ruling: a separate JVM_UNREACHABLE_METHODS set (JsonRecord.walk, JvmTypedOwner.readOther*) and DECLARATION_ONLY_SOURCES (JsonSink, TapeSource) rather than mislabelling them SVM-only; JsonOwner.ArrayIndex became a final class and the heap/segment tapes route region 0 through readByte so tests reach them — cost if wrong: revert and exempt.
Task 16: complete (commit a326d83). Follow-up 68f1454: JsonRecord.walk now runs on SVM via a dynamic-accessor nested root (3.2E-5 B/read); Gazelle's benchmark entry gains the JSON deps from Task 17.
Myna: upgraded to the 0.9.0 release source (82bc9a9); value layer byte-identical, seams regenerated, no ThinLTO issue on any lane.
Task 19: complete. bazel test //... → 30/30 (https://app.buildbuddy.io/invocation/9dc14948-45ce-4cca-95ba-2b83c6de586a); native lanes → https://app.buildbuddy.io/invocation/7a87d8f9-980b-4f1e-8200-4097eb33d530; ASan/UBSan → https://app.buildbuddy.io/invocation/1acff9c2-e094-4339-9427-9d397337efa7; coverage (real Bazel 9.2.0; the aspect CLI has no coverage verb) → https://app.buildbuddy.io/invocation/d3978464-7ec4-4563-af98-0ec679ec28a0, reports.py and coverage_contract.py pass; Gazelle clean. .bazelignore excludes .claude (local worktrees contain nested Bazel output symlinks that break //...).
Review (whole-branch): fixed elements() iterators reading the tape without a liveness/thread check (high); nested JsonAccess hops now verify each child entry's plan schema, so an accessor built from a different recordTypes map cannot index another layout's slots (medium); fitsInDouble/fitsInFloat now use an exact round trip for I64/U64 (2^60, U64 2^63 fit; 2^63-1, 2^64-1 do not); DECIMAL tokens longer than 8192 bytes fit nothing and are never parsed by interop; the parser pool frees parsers whose capacity grew past 1 MiB instead of pinning their buffers.
Review ruling: F64 nodes expose binary64 semantics through interop (1E-999999999 is 0.0 and fits long; 9007199254740993.0 reads as ...992), as JSON.parse does; the exact token stays available through numberText — cost if wrong: integer fits for F64 would need a token-exactness check (BigDecimal per call).
Myna: upgraded to 0.9.1 (d325396), all 39 lanes pass on toolchain 2026.11.1.
RELEASE BLOCKER: toolchain 2026.11.2 breaks Bazel distributed ThinLTO (fat-archive LTO default puts absolute libc++.a member paths in .imports; filed elide-dev/toolchain#18). The fix ships in toolchain 2026.11.4 (2026.11.3 was never released). The next release (JSON + CSV artifacts) must not be cut until the toolchain pin is moved to 2026.11.4 (Myna 0.9.1 pins 2026.11.2+) and every lane, including sanitizers and native images, passes on it.
## Hand-off state (written 2026-11-06 11:05 PST, branch feat/json-completion)

Tasks 1–8 are complete and committed (see lines above; `git log main..feat/json-completion`).
Task 9 has since completed (see the Task 9 lines above); the WIP notes below are historical:
- JVM lane passes: `bazel test //json/tests:jvm` (JsonInteropTest, JvmScopedTest, FfmAcceptanceTest all green).
- SVM lane FAILS TO BUILD: `bazel test //json/tests:native_test` → native-image error
  "The following method is reachable during compilation, but was not seen during Bytecode parsing:
  dev.elide.myna.values.ValueTape.tag%%D(long)" (a deopt-target variant requested at compile time).
- Diagnosis so far (from `-H:+PrintRuntimeCompilationCallTree`): `ValueTape.tag` is trivially inlined
  before analysis into `JsonValue.tag()` (only `ValueTape.storageTag%%R` is registered), reached from
  `JsonValueGen$InteropLibraryExports$Cached.isNull` ← `CompiledReads$InteropReads`. Non-trivial
  ValueTape methods (`signedValue`, `booleanValue`, `length`, `doubleValue`) register fine.
  `first%%D` disappeared once traversal methods (`element`, `keyAt`, `memberAt`, `elements`, `member`,
  `size`) got `@TruffleBoundary`; only `tag%%D` remains.
- Already ruled out: JVM backend in the image (split into `//json:jvm`, still fails); boundaries on
  all number-conversion messages (still fails); adding a compiled InteropLibrary root (still fails).
- Next experiments, in order: (1) replace the private `JsonValue.tag()` helper with a call that is not
  a trivial one-liner chain (e.g. read `owner.tape.tag(node)` inside `kind()` and switch on Kind in the
  exports), (2) build once with `-H:-InlineBeforeAnalysis` to confirm the trivial-inlining theory,
  (3) as a last resort put `@TruffleBoundary` on the `tag()`-only messages (isNull/isBoolean/isString/
  isNumber/hasMembers/hasArrayElements/asBoolean/getArraySize/isArrayElementReadable) to unblock,
  and file the trivial-inline deopt-target issue upstream (Myna/GraalVM).
- The InteropReads root in json/tests/CompiledReads.java (checksum 2003000000 over 1e6 iterations on
  the `second` message) and the uncached interop assertions in SvmAcceptance.scoped() are part of
  Task 9's SVM coverage and should stay once the image builds.
Remaining tasks after 9: 10 (JsonSink export), 11 (docs), 12 (rename to JsonPlan/JsonRecord/JsonAccess),
13–15 (nested/repeated typed plans, Java API, JVM typed), 16 (coverage/policy inventories),
17 (benchmark cells), 18 (release artifacts incl. new //json:jvm), 19 (final verification), then the
final whole-branch review per superpowers:executing-plans.
Note: the automated commit security review for commit 74b7ed8 did not complete (HTTP 429); the
HashMap race it later flagged in JsonOwner was fixed in eb79330.
