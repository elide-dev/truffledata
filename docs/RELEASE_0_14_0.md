# TruffleData 0.14.0

The bindings move to [Myna 0.19.0](https://github.com/elide-dev/myna/releases/tag/v0.19.0), the build
gains [trufflecheck](https://github.com/elide-dev/trufflecheck)'s Truffle and Native Image rules, and
seven bugs are fixed, one of them a use-after-free on the JVM. No benchmark capture was taken and no
performance claim is made: the README cards are unchanged.

## Myna 0.19.0 (#121)

- **One source for each seam's integer protocol.** Status codes, decode and option flags, limits and
  the code tables that were only comments (typed-plan field kinds, length units, float widths, field
  flags, CSV format words, Bebop reason codes) are `const`/`enum`/`flags` declarations in the
  production seams. C++ aliases the generated `seam.h` names, Rust uses the generated types, and Java
  uses the generated `<Class>Constants`. Pure-Java modules that cannot depend on the bindings
  (`protobuf-runtime`, `csv`, the shared tape layer) keep their own copies, and tests assert that
  those copies equal the seam.
- **Typed pointers and counted views.** Sessions, streams and layouts are opaque types; results are
  typed struct pointers; result tables declare their element counts. Native Image code uses
  `@CStruct` records; JVM table reads are bounds-checked by the generated FFM views.
- **Ownership in the cells routes.** JSON results have three constructors and Buffa results two. With
  `truffledata.json.cells` on, the `decode` and `session_retain` cells return owners. The cells
  routes stay off by default.
- **Breaking for direct users of the `*-bindings` artifacts**: Native Image records are `@CStruct`
  interfaces, struct and handle pointers are typed, and every class gains generated `<Class>Constants`
  and `<Class>Directives` (the `@CContext` that checks the embedded `seam.h` at image build). ABI
  fingerprints change, as in every Myna release. The libraries' own APIs are unchanged.

## trufflecheck (#122)

trufflecheck 0.1.1 is a submodule (`third_party/trufflecheck`); its `truffle-language` agent skill is
linked into `.claude/skills` and `.agents/skills`, and its Error Prone rules run in every library under
`-Werror`, with the policy in `bazel/trufflecheck/`. `TruffleStackfulException` and
`TruffleSkippedException` are off until they are scoped to compiled code
([elide-dev/trufflecheck#9](https://github.com/elide-dev/trufflecheck/issues/9)). The findings it
raised are fixed: fatal error paths, including the protobuf generator's required-field checks, no
longer invalidate compiled code before throwing; the tape exporter builds its guard messages outside
the hot loop; locale-independent escapes. Deliberate patterns carry explained suppressions.

## Fixes

- **Buffa: borrowed payloads check the adopted region** (#112). A projected slab that borrowed its
  input read borrowed bytes on the JVM through a cached raw address, so closing the adopted region's
  arena behind a live borrow read freed memory. Reads now go through the adopted segment and fail with
  `IllegalStateException`, as `BuffaJvm.adopt` documents. Native Image is unchanged.
- **Tape: a refused close releases nothing** (#113, #119). Closing a session while an export walk
  held a streamed document threw, but still released the native session beneath the walk.
- **Tape: mapping keys keep their tags and anchors on export** (#110, #117), and an alias of an
  anchored key reuses the key instead of expanding it.
- **Tape: number interop on scalars without source text** (#109, #116). Quoted YAML numbers decoded
  without retained source made every number message throw; they now answer from the decoded value.
- **YAML: empty verbatim tags (`!<>`) are rejected at decode** (#108, #115), as the Java kernel and
  the YAML 1.2 grammar require, instead of failing later on emit.
- **Protobuf: lax projections hold for repeated singular messages** (#111, #118). A message field that
  appeared twice on the wire was validated strictly the second time.
- **Core: `DynamicRecord` rejects schemas with duplicate labels** (#114, #120), which it would have
  aliased onto one property. The check costs one field read per construction.

## Documentation

Bebop's publication status and the library count in PUBLISHING.md (#104).

## Publication

Published on Elide Maven and GitHub. Maven Central remains deferred under
[#28](https://github.com/elide-dev/truffledata/issues/28) until the quota resets (2026-12-01).
