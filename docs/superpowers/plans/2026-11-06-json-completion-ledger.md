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
