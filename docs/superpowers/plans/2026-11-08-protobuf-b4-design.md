# Protobuf B4 — interpreter read design (2026-11-08)

Status: Phase 1 measured and rejected on 2026-11-08. SVM generated interpreter reads improved
17.7%, but JVM generated interpreter reads regressed 20.2% and slab resident reads 40.7%, with
nonoverlapping observed fork ranges. Production was restored; representation/lifetime tests and
[complete paired evidence](../../../benchmarks/results/protobuf-b4-213a289b/README.md) remain.
B4 is still open. The design below records the rejected hypothesis; Phase 2 was not started.

## 0. Gate and evidence

- Gate: SVM full **interpret** generated-owned/stock ≤ 2.5× and slab/stock ≤ 2.5×, with peak
  unchanged or better (ledger `:271`). Measured at `4065dd6f`: 4.358× and 6.729×; generated
  peak/historical 1.090× SVM (ledger `:314`).
- The stock denominator is AOT host Java, not guest code. `StockRoot` calls `expected()` behind
  `@TruffleBoundary` (`benchmarks/comparison/ConsumerComparison.java:454-470`). The guest engines
  run `SharedRoot.score` in the Truffle interpreter with `engine.Compilation=false`
  (`ConsumerComparison.java:121,129`). B4 therefore compares interpreted node dispatch against
  compiled getters, so the budget for per-read overhead is a few nanoseconds.
- Profile (`benchmarks/results/protobuf-interpreter-4065dd6f/summary.json`), leaf samples out of
  2,999 generated-owned and 2,998 slab:

| leaf | generated-owned | slab |
|---|---|---|
| `Accessor.bound` | 561 (18.7%) | 596 (19.9%) |
| `ReadLongNode.accessed` | 260 | 277 |
| `Generated.readLong` / `read` / `readBoolean` | 253 / 192 / 124 | n/a |
| `Accessor.length` | 205 | n/a (Slot overrides) |
| `ReadLengthNode.accessed` / `ReadBooleanNode.accessed` | 177 / 126 | 208 / 137 |
| interop `readArrayElement` + `getArraySize` | 336 + 100 | 308 + 98 |
| `DataLibraryGen$CachedDispatch.read` | 143 | 174 |
| `SharedRoot.score` own frame | 298 | 290 |
| slab-only: `SlabRow.value`, `SlabFormat.getInt`, `SlabMessage.row`, `HashMap.*` | n/a | 182, 173, 146, 98 |

- AOT symbols show these as separate, non-inlined calls. `Accessor_bound` is 0x51 bytes and
  `GeneratedMessage$Generated_accepts` (0x7a), `SlabRow$Slot_accepts` (0x72),
  `Generated_message` (0x9f) and `SlabRow$Slot_row` (0x74) each exist as their own symbol
  (`symbols.txt.gz`). So `bound` makes a real vtable call to `accepts`. Leaf attribution may
  include skid onto the first frame that touches the accessor; treat percentages as upper bounds.

## 1. Where interpreter time goes

The workload per row (`ConsumerComparison.java:513-531`): root reads through cached
`ReadLengthNode` ×3 and `ReadLongNode` ×1; `DataLibrary.read` ×3; per element of `fields` an
interop `readArrayElement`, then `FieldAudit` (`:553-572`) runs five `@GenerateInline` readers
(`ReadLengthNode.InlineReader` on ordinal 3, `ReadBooleanNode.InlineReader` on 6, and three
`ReadLongNode.InlineReader`s when packed).

### 1a. Generated-owned row, one `ReadLongNode.InlineReader` read

1. `FieldAuditNodeGen.execute` loads its state and calls `FieldAudit.read`.
2. `ReadLongNodeGen$InlineReaderNodeGen$Inlined.execute`: reads the inlined state bits and the
   `accessedInline` cache-list reference from the parent through InlineSupport field handles.
   It walks up to 3 entries and tests `index == cachedIndex` (`core/.../ReadLongNode.java:81-83`).
3. Static call `Accessor.bound(accessor, row)` (`core/.../Accessor.java:141-143`): null check,
   then **virtual** `accepts`.
4. `Generated.accepts` (`protobuf-runtime/.../GeneratedMessage.java:133-135`):
   `row.getClass() == type`, checkcast, then `plan` load and compare.
5. Call `accessedInline` → `ReadLongNode.accessed` (`ReadLongNode.java:84-89`, `:27-40`).
   This is a separate AOT frame with the try/catch and the `Accessor.STRICT` static load.
6. **Virtual** `accessor.readLong(row)` → `Generated.readLong` (`GeneratedMessage.java:148-154`).
7. `Generated.message(row)` (`:137-140`) runs **`accepts` again**, then `castExact`. That is a
   second class compare in the interpreter, about three class checks per read in total.
8. `field().kind()`: two dependent loads.
9. **Virtual** `message.getLong(ordinal())` into the generated class's ordinal switch.

Total: about 5 non-inlined calls, 3 of them virtual, and 3 to 4 layout checks for one `long`
field load. Strings take the same path through `ReadLengthNode.accessed` (`ReadLengthNode.java:31-38`),
then the inherited `Accessor.length` → virtual `read` → `message` (layout re-check) → `get` →
`lengthOf` → `stringLength` (`Accessor.java:81-103`). `Generated` does not override `length`.

### 1b. Slab row, one read

Steps 1 to 3 are the same. Step 4 is `SlabRow.Slot.accepts`: `instanceof SlabRow`, then `layout`
and `type` compares (`protobuf-runtime/.../SlabRow.java:206-208`). Then `accessed` runs and
**virtual** `Slot.readLong`. That calls `row()`, which repeats the full check (`:210-213`), then
`owner.requireValue` (a projection check, `SlabMessage.java:331-338`), `planField.repeated()`, the
presence bit `SlabFormat.present`, a kind switch and `getInt`/`getLong` (`SlabRow.java:225-237`).
`Slot.length` does the same with `utf16Length` (`:265-282`).

Slab-only costs that are not accessor costs:

- **Child row lookup.** It goes through a `synchronized @TruffleBoundary` `SlabMessage.row`
  (`SlabMessage.java:243-252`).
- **Array cache.** Arrays are cached in a boxed `HashMap<Integer, SlabArray>` (`:36,281-282`).
- **String cache.** Strings use a `synchronized` `text` (`:254+`).

Together these explain most of the extra gap from 4.4× to 6.7×.

### 1c. What is *not* the problem

- **DSL dispatch.** No `*NodeGen.execute` frame is a top leaf. Specialization order already puts
  `accessed` first, and one cache entry matches on the first probe.
- **Boxing.** The primitive paths do not box, and allocation samples are small (450 generated).
- **Strict audits.** `Accessor.STRICT` is false, which costs one static load per read
  (`Accessor.java:25`, `ReadLongNode.java:35`).
- **Exceptions.** `UnexpectedResultException` is thrown only on contract failure.

## 2. Candidates

All candidates keep these invariants:

- **I1.** A row of another layout, schema or representation is never read and still raises
  `ContractViolation` (`Accessor.java:112-115`).
- **I2.** Null, missing and wrong-type values still produce `UnexpectedResultException`, which the
  node turns into `ContractViolation`.
- **I3.** `BorrowedSlabRow` reads still call `owner.check(epoch)`
  (`protobuf-buffa/.../BorrowedSlabRow.java:193-199`). Slab projection still calls `requireValue`.
- **I4.** Public `Accessor.read*` and `length` stay checked.
- **I5.** Compiled reads stay at ≤ 0.05 B/read. This is the `AllocationTest` gate, with a measured
  value of about 4.4E-5. The 10,000-node graph budget and the 256-node AST ceiling also hold.

### (a) Exact-class plus schema guard, no virtual call ("exact layout" accessors)

Add an optional `exactRowClass` to `Accessor`. When it is set, the accessor promises that
`accepts(row)` is the same as `row.getClass() == exactRowClass && ((AbstractRow) row).descriptor == schema`.
`bound` then checks two final fields inline and makes no virtual call. Accessors that leave
`exactRowClass` unset keep the virtual `accepts`.

- **Generated qualifies.** One generated class can be built with several plans: `AccessorTest`
  builds `Access_…$Flat` over two plans (`AccessorTest.java:52-55`, `:225-240`). So `type` alone
  is not enough. However each `ProtoPlan` constructs its own `Schema` (`ProtoPlan.java:336`), and
  generated rows take `plan.schema()` as their descriptor (`GeneratedMessage.java:22`). So
  `descriptor == schema` is the same test as `plan == plan`.
- **Slab does not qualify.** One plan, and so one schema, is shared by several root layouts
  (`ProjectionTest.childAccessorsRemainBoundWhenTheSamePlanHasDifferentRootLayouts`,
  `ProjectionTest.java:50`). A `Slot` also accepts both `SlabMessage` and child `SlabRow`
  (`SlabRow.java:27,207`). `StaticLayout` is excluded for the same schema-sharing reason
  (`AccessorTest.java:206-213`).
- **Effect.** It removes the virtual call and the `accepts` frame from step 3/4 for generated rows.
  Expected cost reduction is up to the `bound` share, about 10 to 18%.
- **Compiled effect.** Neutral. `exactRowClass` and `schema` are final fields of a PE-constant
  accessor, so the guard folds to the same class compare plus one field compare as today.
- **Pinned by:**
  - `AccessorTest.accessor_rejects_foreign_layout` (`:202`)
  - `AccessorTest.flatRowsBindLayoutAccessorsForEveryOrdinal` (`:84`)
  - `AccessorTest.inlineReadersPreserveCachedContractsAcrossLayoutChurn` (`:102`)
  - `AccessorTest.accessor_is_pe_constant` (`:293`)
  - `CoreTest.picFallsBackCorrectlyBeyondBudget` (`CoreTest.java:493`)

### (b) Bound read hooks on `Accessor` (protected, guard-dominated)

Add protected, non-public hooks to `Accessor`:

- `readLongBound`, `readDoubleBound`, `readBooleanBound` and `lengthBound`, each taking
  `(Object row)`.
- Only the `accessed` specializations call them, directly after `bound(accessor, row)` succeeded.
- The default implementations delegate to the checked public method, so every existing accessor
  behaves as before.

Overrides skip only the layout re-check:

- **`Generated`** does `castExact(row, type)`, then a precomputed `int64`/`float64`/`bool` flag,
  then `getLong`/`getDouble`/`getBoolean`/`get`. It drops step 7's `accepts`.
- **`SlabRow.Slot`** casts to `SlabRow` and keeps `requireValue`, presence and kind handling.
- **`BorrowedSlabRow.Slot`** casts and **keeps `r.owner.check(r.epoch)`**.

Calls from outside are safe:

- **Same package.** `ReadLongNode` and the other read nodes live in `dev.elide.truffledata`, so
  they can call protected members.
- **Other packages.** Code outside the package can call a protected hook only through a reference
  of its own subclass type. The `Generated` and `Slot` classes are private or final, so external
  code cannot reach their unchecked paths.

Expected and measured effects:

- **Interpreter effect.** One virtual call and one to two class checks fewer per read. `Generated`
  string length loses a virtual `read` hop. Estimated at about a third to a half of the
  `accessed`/`readX`/`length` share, which is about 30% of samples.
- **Compiled effect.** Neutral or slightly smaller. PE already removed the duplicate check by
  conditional elimination.
- **Pinned by** the tests listed under (a), plus:
  - `AccessorTest.accessorsAgreeWithTheLibrary` (`:461`)
  - `AccessorTest.minimalAccessorInheritsLibraryDefaults` (`:549`), which pins the defaults
  - `SlabCodecTest.slabAccessorRejectsOtherLayoutsAndReadsStringLength` (`SlabCodecTest.java:285`)
  - Buffa `CompiledReads`, where closed and reset sessions must be rejected through cached reads
    (`protobuf-buffa/tests/CompiledReads.java:39,54,137,235`)
  - `AllocationTest.accessorReadsStayWithinAllocationBudget` (`AllocationTest.java:620`)

### (c) DSL tuning: limits, `@Idempotent`, ordering

- **Order.** `accessed` already runs first.
- **Lower `limit`.** This breaks the B2 ruling and its pinned instance counts (ledger `:31`;
  `AccessorTest.assertSites` asserts one instance per layout, `:262-271`).
- **`@Idempotent`.** It does not apply because `index` is dynamic per call.
- **Hand-unrolling the inline readers.** That would duplicate the generated code.

The profile shows no NodeGen frame as a hot leaf, so the expected gain is ≤ 3%. **Reject.**

### (d) Cache the bound accessor in node state, one reference compare per read

The node already caches the accessor. What varies per call is the row, so a single reference
compare needs a reference stored *in the row*, a per-layout key field on `AbstractRow`. That costs
about 4 to 8 bytes per row on every representation. B3 sparse heap already misses (ledger `:316`).
For generated rows, (a) gets the same one-compare guard for free from `descriptor` plus the hub.
**Defer**, and consider it only for slab rows if (a) and (b) leave slab as the last gap.

## 3. Recommendation: (a) + (b) together, then slab cache work

**Estimate for generated-owned, as a hypothesis to measure.** 4.358 × (1 − ~0.15 − ~0.12) ≈ 3.2×
on the arithmetic. That is still above 2.5×, because ~25% of samples are harness interop
`readArrayElement`/`getArraySize`/`DataLibrary.read` and the `score` frame. Phase 2 below is
required. Slab additionally needs the slab cache work.

### Phase 1 patch plan (TDD order)

Tests first, all in `tests/src/test/java/dev/elide/truffledata/AccessorTest.java` unless noted:

1. **`exactLayoutGuardMatchesAccepts`.** For generated rows of plans L and R, and owned, slab and
   static rows, assert `Accessor.bound(a, x) == a.accepts(x)` for every accessor/row pair. Include
   a non-row `String`. Also assert that a generated accessor reports the exact class.
2. **`boundHooksOnlySkipTheLayoutCheck`.** For every representation and ordinal, run a node read
   and the public checked read. Assert equal values, or equal exception classes, for missing, null
   and wrong-type fields. Reuse `outcome(...)` and the `ReaderParityRoot`.
3. **`foreignLayoutStillRejectedThroughNodes`.** Warm an `accessed` site on L, call it with an R
   generated row and a schema-sharing slab or static row, and expect `ContractViolation` with the
   correct value after respecialization. `assertSites` covers part of this; extend it to the
   inline readers.
4. **Buffa lifetime.** In `protobuf-buffa/tests/CompiledReads.java`, add an **interpreter-tier**
   variant of the cached-owner rejection at `:39`. Use a context with `engine.Compilation=false`,
   and expect `IllegalStateException` after `close`/`reset` through `ReadLongNode`,
   `ReadLengthNode` and `ReadBooleanNode`.
5. **Strict mode.** Under `-Dtruffledata.strict=true`, each `*Bound` hook asserts
   `accepts(row)`, so a misuse fails loudly. Add a test that calls the hook on a foreign row from
   a same-package test helper and expects `AssertionError`.
6. **Coverage.** Exercise `ReadBooleanNode$InlineReader::accessedInline`, which is the known CI
   coverage gap (`benchmarks/results/protobuf-interpreter-4065dd6f/README.md:15`), and the new
   hooks.

Code:

- **`core/.../Accessor.java`**
  - Add the field `private final @Nullable Class<? extends AbstractRow> exactRowClass;`.
  - Add the constructor `protected Accessor(Schema schema, int ordinal, Class<? extends AbstractRow> exactRowClass)`,
    documented as "layout ≡ (exact class, schema identity)". The existing constructor sets the
    field to null.
  - Rewrite `bound`:

    ```java
    static boolean bound(@Nullable Accessor a, Object row) {
      if (a == null) return false;
      Class<?> exact = a.exactRowClass;
      if (exact != null) return row.getClass() == exact && ((AbstractRow) row).descriptor == a.schema;
      return a.accepts(row);
    }
    ```

  - In `bind` (`:134-139`), reject an exact accessor whose fast guard disagrees with `accepts(row)`
    by throwing `IllegalStateException`. That is a slow path and it keeps the guard honest.
  - Add the hooks with checked defaults, for example
    `protected long readLongBound(Object row) throws UnexpectedResultException { if (STRICT && !accepts(row)) throw new AssertionError(); return readLong(row); }`.
    Add the same for double, boolean and `lengthBound`.
- **`ReadLongNode`, `ReadDoubleNode`, `ReadBooleanNode`, `ReadLengthNode`.** In `accessed`, call
  `accessor.readLongBound(row)` and the other hooks instead of the public methods. Do not change
  the `InlineReader` delegates, `required`/`cached` or `generic`.
- **`GeneratedMessage.Generated`**
  - Call `super(plan.schema(), ordinal, type)`.
  - Implement `accepts` as `row.getClass() == type && ((GeneratedMessage) row).descriptor == schema()`.
    Keeping the plan compare is also fine because it is equivalent.
  - Precompute `kind` in a final field.
  - Override the four hooks without calling `message()`.
- **`SlabRow.Slot` and `BorrowedSlabRow.Slot`.** Override the hooks with a plain cast. Keep the
  rest of each body, which means shared private helpers taking an already-checked row. Borrowed
  rows keep `owner.check(epoch)`.
- **`PolicyTest`.** Boundary budgets stay unchanged. Add no new `@TruffleBoundary`.

### Phase 2, only if Phase 1 lands above 2.5×

- **Harness-visible interop.** Profile `OwnedArray`/`SlabArray` `readArrayElement` and
  `DataLibrary.read` dispatch, which together are about 20%.
- **Slab caches.**
  - Replace `HashMap<Integer, SlabArray>` with the handle-indexed `Object[]` cache that `row`
    and `text` already use.
  - Make cache *hits* lock-free: a racy read of an immutable, safely published flyweight, falling
    back to `synchronized` only on a miss.
  - Each slab cache change needs the existing durable and concurrency slab tests.

### Measurement procedure

1. Build the candidate and keep the `4065dd6f` image as baseline. Run the B4 group pair on one
   quiet Linux host, as in `benchmarks/comparison/CONSUMER.md:52-60`:

   ```sh
   taskset -c 2 python3 tools/bench/consumer.py --runtime svm --pin \
     --binary baseline=/abs/baseline --binary candidate=/abs/candidate \
     --engines baseline=stock,generated-owned --engines candidate=stock,generated-owned,slab \
     --groups full:interpret,full:peak,sparse:interpret,sparse:peak \
     --source '<commit>' --output /abs/b4-<sha>
   ```

   The formal verdict is the paired G1 shards:
   `bazel test //benchmarks/comparison:consumer_measure` (SVM) and `:consumer_measure_jvm`.
   Override the baseline with `consumer_svm_baseline`.
2. Re-profile with `bazel test //benchmarks/comparison:consumer_interpreter_profile`. Retain the
   result as `benchmarks/results/protobuf-interpreter-<inv8>/` and summarize with
   `tools/bench/json_profile_summary.py`.
3. Run the gates:
   - `bazel test //tests:test //protobuf-runtime:test //protobuf-buffa/tests:acceptance`
   - `//benchmarks/comparison:consumer_smoke` and `:consumer_native_smoke` (132 cells, AST and
     graph budgets)
   - the coverage lane
4. Add a ledger entry in the house format, appended after `:321`:

   ```text
   ### B4 exact-layout guard + bound hooks — `<inv8>` (YYYY-MM-DD)
   - Change: <commit>. Invariants pinned by <tests>.
   - SVM full interpret generated-owned/stock X.XXX× (was 4.358×), slab/stock Y.YYY× (was 6.729×); sparse …; fork ranges.
   - Peak: generated-owned full/historical … SVM / … JVM (was 1.090× / 0.924×). Compiled reads … B/read (≤0.05).
   - Profile: `Accessor.bound` leaf …% (was 18.7%). Verdict: B4 pass | open (remaining owner: …).
   ```

## 4. Risks and rejection criteria

- **Wrong equivalence.** If any code path builds a `GeneratedMessage` whose descriptor is not its
  plan's schema, or ever shares a `Schema` between two plans, (a) would accept a foreign layout.
  The `bind`-time cross-check and test 1 guard this. **Reject (a)** if a schema-sharing plan
  constructor exists or appears.
- **Hook misuse.** A future call to a `*Bound` hook that is not dominated by `bound` would read
  without a layout check. Mitigations: the hooks stay protected, are called only from the four
  `accessed` bodies, and assert in strict mode. Consider a `PolicyTest` grep that allows `Bound(`
  call sites only in those four files.
- **Lifetime regression.** If any borrowed or projection test fails in either tier, **reject.**
  Never move `owner.check` or `requireValue` behind the layout check.
- **Compiled regression.** **Reject** if any of these changes:
  - peak generated-owned/historical worsens by more than the fork range, against 1.090× SVM
  - `AllocationTest` B/read rises above 0.05 or measurably above the ~4.4E-5 baseline
  - `JitMatrixTest`, `GeneratedGraphTest` or the 10,000-node graph budget changes
- **No win.** If paired interpret ratios improve by less than 5% with overlapping fork ranges,
  revert, keep the tests, and record the result as a measured negative in the ledger. The
  sampled `bound` share would then have been skid, and the next owner is Phase 2.
- **API surface.** The new protected constructor and hooks are a public-ABI addition to an exported
  abstract class. Note them in release notes. They are not a break, because the defaults preserve
  behavior.
