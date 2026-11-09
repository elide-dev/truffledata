# Compact-tape upstream request audit and drafts

Audited 2026-11-07 against the pinned Myna0.13.0 source archive and all20 issues returned by `gh issue list --repo elide-dev/myna --state all --limit 100`. These are drafts only; none has been filed. The coordinator reserved the filing decision. They are not release blockers: TruffleData supplies explicit Myna ABI4 conversion and its own compact text export.

The older YAML request labels M6/M7 refer to different features. [Myna#52](https://github.com/elide-dev/myna/issues/52) covers nonfinite F64 and [#53](https://github.com/elide-dev/myna/issues/53) covers REF/metadata; both are closed and the pinned headers contain those features. [#45](https://github.com/elide-dev/myna/issues/45) supplies ABI4 directories, and [#48](https://github.com/elide-dev/myna/issues/48) supplies whole-tape text export. Neither covers the compact layout proposed below. Myna0.13.0 still declares ABI3/4 views, `enable_v4(threshold, metadata)` and `myna_tape_text_v1(const myna_value_view*, ...)`; its builder sorts directory entries without a duplicate-rejection option.

## Draft M6: compact value ABI and shared Rust/Java readers

**Title:** Values: evaluate a compact word-tape ABI with embedded lexical spans

TruffleData's JSON and YAML producers now use a shared compact representation and convert explicitly to Myna ABI4 for interchange. Please evaluate that representation as a future Myna value ABI, with Rust and Java readers, so consumers could read it directly without conversion.

The implemented proposal uses64-bit words: one word for null, boolean, clean string/decimal and REF; two for typed numbers, containers and owned text. The tag/flags share the upper byte, a24-bit field stores length/count, and a32-bit field stores an offset or subtree successor. Numeric second words preserve exact bits; owned-text second words preserve raw source spans. Keys/ASCII/ownership are explicit flags. Container counts saturate and readers walk when necessary. Optional object-relative directories retain Myna's FNV32/offset ordering. A REF names an earlier closed node, never an expanded copy.

The88-byte descriptor names immutable word, pool, input, directory and optional metadata arrays. Input/pool/source lifetime remains explicit. JSON and YAML share the layout; descriptor flags distinguish nonfinite numbers, metadata, omitted directories and YAML plain-token spans. Explicit converters preserve existing ABI4 consumers. This is a proposal for review, not a request to silently change ABI4.

Suggested acceptance: independently validated extents/word boundaries/REF ordering; cross-language reader parity; unchanged exact numeric tokens and decoded strings; directoryless traversal; format-specific lexical spans; malformed/truncated descriptor rejection. The implementation and differential corpus are in TruffleData's `tape/native/ctape.h`, compact Java readers and `tape/native/ctape_test.cc`. The layout reduces memory substantially, but does not promise a universal1.5× input bound: the fixed fixture's unavoidable words+pool range is1.510–1.871×.

Related prior work: [#45](https://github.com/elide-dev/myna/issues/45), [#53](https://github.com/elide-dev/myna/issues/53). TruffleData continues to use explicit ABI4 conversion until an upstream format is agreed.

## Draft M7: duplicate rejection while building directories

**Title:** C++ value_builder: optional decoded-key duplicate rejection during directory construction

Myna0.13.0's ABI4 builder already computes key hashes and sorts each directory by hash/offset. Please add an opt-in rejection policy for equal decoded keys, such as an additional source-compatible `reject_duplicates` argument to `enable_v4`, while preserving the existing default behavior.

Equal hashes must trigger exact decoded-byte comparison, including inline strings and every region-backed string. A hash collision alone must remain valid. Duplicate detection should apply even when an object's directory is omitted by the threshold, and should complete before a result can be published. Failure should poison the builder until `clear()`, consistently with its other failed operations.

Suggested regressions: escaped/raw-equivalent caller-provided decoded names, adjacent and separated duplicates, forced equal-hash distinct keys, duplicates late in an equal-hash run, directory thresholds0/1/2/large, and allocation failure during scratch/directory growth. This would let producers that remain on `value_builder` share the duplicate check without a second key set. TruffleData already performs it in its compact writer, so this is not a downstream blocker.

## Draft M8: compact-tape batch text export after the compact ABI exists

**Title:** Values: extend whole-tape text export to the proposed compact ABI

After a compact ABI is agreed, please provide a versioned whole-tape text exporter for that descriptor. Myna0.13.0's existing `myna_tape_text_v1` accepts the ABI3/4 view and cannot consume compact words directly. A distinct versioned entry point avoids changing that contract.

Preserve the current caller-owned buffer/table model, checked sizing, explicit encoding per text entry, no allocation, and failure atomicity. Compact entries are indexed by word offsets, with empty entries for numeric payload/raw-span words. Export every key/string and decimal token; REF aliases must stay references, without unbounded expansion. Plain source spans and owned decoded pool text must be treated according to their descriptor flags.

Suggested acceptance: compact-to-ABI4 conversion plus existing exporter produces identical text; Latin-1/UTF-16, escaped text, supplementary characters, empty strings, invalid UTF-8, exact capacity and one-byte-short capacity all agree; alias-heavy tapes stay linear. TruffleData's current `tape/native/text_arena.h` remains the implementation until this exists. This request depends on M6 above and need not precede that format decision.
