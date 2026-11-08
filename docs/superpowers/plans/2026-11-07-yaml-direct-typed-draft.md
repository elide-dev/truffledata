# Direct typed YAML handler — follow-up draft

Status: local review draft. No upstream issue has been filed and no direct handler is implemented. This is the deferred trigger from YAML plan Task 6, separate from the current release's mandatory correctness, graph and compact-throughput gates.

The pinned measurement in invocation `4bb84dea-d72c-4dda-84c5-43f6c45f1910` found typed decode at 2,050,214 ns versus generic retain at 1,513,763 ns (1.354×). The isolated compact-tape-to-slots walk took 508,605 ns, or 24.8% of typed decode, above the plan's 15% trigger. The typed ≤1.2×generic target is a hypothesis, not a replacement for existing acceptance requirements. Re-measure against the final ABI 5 source before designing a production fast path; the recorded comparison predates later producer improvements.

Current `yaml/native/typed.h::typed_document` owns a fresh session, decodes with pristine-source retention, and then runs the shared `tape_typed` walker into independent slots and record entries. Generic benchmark sessions are reused. The first experiment must separate parser/session construction, source preservation, generic tape production, slot traversal and destruction costs. Removing the second walk cannot be credited with savings from unrelated lifetime or workload changes.

Proposed experiment: a schema-aware rapidyaml event consumer writes the existing shared scalar slots, record entries and presence/null masks directly. Keep the current tape-to-slots implementation as a correctness oracle and fallback while evaluating the strict common case. Reuse its contract resolution helpers rather than introducing another numeric/string coercion policy. No Java typed layout or result-lifetime change is required.

Required behavior remains:

- D5 field resolution and constraints, including plain `1.0` into a string field, `0x1f` into an integer field, nonfinite floating fields, quoted scalars requiring string fields, widths and string-length units.
- Unknown, duplicate and missing required fields fail. Typed plans reject duplicates even if generic options select last-wins. Nested/repeated records, nullability, directives, tags, aliases and enabled merge keys keep their current semantics.
- Strict encoding, recursive/undefined-alias rejection, expansion and storage budgets, bounded status/error publication, and all injected allocation failures remain enforced. Inputs unsupported by an experimental direct path must use a checked fallback, never skip validation.
- Results remain independent of plan/session closure; snapshots and compiled TypedAccess reads retain the current behavior and allocation/graph budgets.

A fallback design must preserve the original caller bytes: rapidyaml filters its session copy in place, so that filtered buffer cannot be reparsed as if it were the source. Account for replay/copy costs and failed-attempt storage explicitly. A partial handler that consumes aliases or merges without preserving their semantics is not eligible for promotion.

Measure the final baseline and candidate on identical wrapped native fixtures, plus the config, flow and stream-relevant supported workloads, with interleaved repeated samples and complete input/checksum/lifetime accounting. Preserve raw samples and native memory observations. Run slot/value/error differential tests against the current walker, the existing YAML corpus, ASan/UBSan and allocation failures, JVM/SVM typed tests, and unchanged JSON shared-layout tests.

Stop rules: reject a candidate without a repeatable end-to-end improvement or with any required regression. Record a failed ≤1.2× hypothesis without weakening correctness or graph gates. Do not broaden the production parser merely to remove the measured second walk; session reuse or a smaller contract-aware optimization should be evaluated separately if it explains more of the cost. Any promotion requires a separate review of the measured result and complete semantic coverage.
