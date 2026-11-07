# Tape-GB/s probe (plan `docs/superpowers/plans/2026-11-07-tape-gbps.md`), measured at `7f55043`

What the tape costs between simdjson's ceiling and the shipped ABI v4 decoder, and what leaner
representations and tapeless paths cost, on the comparison fixture's documents (one record,
20, 1,000 and 20,000 records in an array: 210 B, 4.3 KB, 228 KB, 4.85 MB).

**Host and caveats.** Every row ran on a shared BuildBuddy RBE Linux amd64 executor (Firecracker
microVM, simdjson active implementation `icelake`), as a temporary `cc_test`
(`//json/native:gbps_probe`, source retained here as `gbps_probe.cc`, removed before the commit)
built with the repository's hermetic toolchain at `-O3` and ThinLTO. One process per round, best of
9 rounds of N repetitions per row, rows of one document measured back to back. **Timings are
advisory**: workers differ between rounds (compare rows within a round), and they are slower than
unclemax, the host behind `benchmarks/results/json-v4-86a691a`: the shipped `V4` row measures
451–466 ns / 9.0–9.3 ms here against 340 ns / 7.05 ms there (small / 4.85 MB), i.e. unclemax runs
this code about 1.35–1.4× faster. Ratios between rows are what the plan uses.

| File | What it is |
|---|---|
| `round1-native.txt` | Rows `I0`..`V4` (invocation `c675e84e`); the small-document rows before `W3v` were not captured |
| `round2-native.txt` | Every row again plus the duplicate-check alternatives `K1`..`K3`, the unchecked writer `T8x` and the raw DOM-tape walk `D0t` (invocation of the final round-2 run) |
| `round3-native.txt` | Every row again plus the honest consumers `W2c`/`W3c`, stage 1 through `dom_parser_implementation` (`S0`), the structural-index walk floor (`S1`) and the first `S2` attempt (which failed on escaped quotes: `S2 FAILED` lines) |
| `round3b-native.txt` | The corrected `S2` (own stage 2 over simdjson's structural index) rows only |
| `gbps_probe.cc` | The probe source (all rounds) |

## Rows

- `I0`/`S0` simdjson stage 1 only (structural index + UTF-8 validation).
- `D0` simdjson DOM parse: the ceiling a tuned stage 2 reaches (tape, unescaped strings, typed numbers, no duplicate check, no exact numbers).
- `D0d`/`D0t`/`D0e` DOM plus a strict duplicate-key pass (through the public `dom::object` iterator / over the raw tape words) and plus a v4-style directory per object.
- `W1` the plain On-Demand walk (`unescaped_key`, `get_string`, `get_double`).
- `W2`/`W2s` the shipped visitor's walk (raw tokens, body scan scalar / SWAR, `get_number` first) with a **null writer**: the compiler drops the key and value-string handling, so these are floors, not ceilings.
- `W2c`/`W3c` the same walk into an inline checksum consumer (every value kept alive), without / with the shipped `key_table` duplicate check: the honest validate-only ceiling of the On-Demand walk.
- `W3` null writer plus `key_table`; `K1` a 64-bit filter in front of the compares; `K2` YAML-style per-depth key templates; `K3` templates plus a one-multiply hash for keys of at most 8 bytes; `W3g` numbers validated by grammar only (no `get_number`).
- `W3v` the walk driving a virtual sink (one indirect call per event): what a native direct-sink consumer costs.
- `T8` a simdjson-style compact tape (one 8-byte word per value, two for numbers and containers); `T8k` with `K3`; `T8x` with unchecked presized pushes (the worst-case reservation of one word per input byte).
- `T16`/`T16d` fixed 16-byte nodes with embedded spans, without / with eager directories.
- `T24` Myna 24-byte nodes through the production `tape_writer` (ABI v3) with no side tables.
- `V3`/`V4` the shipped `tape_decoder` (24-byte nodes + lexical spans + key meta, + v4 directories).
- `S1` stage 1 plus a classification of every structural index (the floor of a hand-written stage 2); `S2` a first-cut recursive-descent stage 2 over the index writing the compact tape with duplicate checks (strings scanned by SWAR to the closing quote, integers typed, doubles left as lazy DECIMAL spans).

## Readings (big document, round 3 unless stated)

| Row | MB/s | Reading |
|---|---:|---|
| D0 | 1,721 | tuned stage 2 ceiling |
| W2c | 998 | On-Demand walk consuming every value |
| W3c | 783 | + strict duplicate check: 5 ns per key, 22 % |
| W3v | 803 | virtual sink ≈ the compact tape: a direct sink is not cheaper than a tape |
| T8 / T8x | 749 / 814 | compact tape; unchecked pushes gain 4 % (medium/big) and lose 13 % on 4.85 MB |
| T24 | 694 (round 2) | 24-byte nodes alone cost little more than 8-byte words |
| V3 / V4 | 516 / 495 (round 2) | lexical spans + key meta cost 25 %; directories 4 % |
| D0e | 801 (round 2) | DOM + duplicates + directories ties T8x |
| S2 | 760 (round 3b) | a first-cut own stage 2 ties T8 |
| K1–K3 | ≈ W3 | filters, templates and a SWAR hash do not reduce the duplicate-check cost |

Memory: the compact tape is 1.27–1.49 × the input; the shipped v4 tape with its side tables is
5.0–5.8 ×.
