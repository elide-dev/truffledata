# Compact-tape JSON decoder probe (tape-gbps plan Task 2, Step 3), measured on top of `8447a53`

The `benchmarks/results/tape-gbps-probe-7f55043/gbps_probe.cc` probe re-run with two rows for the
Task 2 decoder, every row of a document measured back to back in one process:

- `V5` the shipped compact-tape decoder: `json::tape_decoder<tape::ctape_writer>` (embedded spans,
  ASCII/KEY/OWNED flags, a directory on every object of at least two members, duplicates detected
  in the directory sort), constructed per document over a reset block arena with the production
  hint, as the `V4` row does for the 0.8.0 decoder (now `json::legacy`).
- `V5v` the validate-only walk: `json::tape_decoder<json::null_writer>` (the same limits and the
  same sorted-run duplicate check over a scratch key stack, no tape).

**Host and caveats.** As in `tape-gbps-probe-7f55043`: a shared BuildBuddy RBE Linux amd64
executor (Firecracker microVM, simdjson `icelake`), a temporary `cc_test`
(`//json/native:gbps_probe`, `-O3`, ThinLTO; source retained here as `gbps_probe.cc`, removed
before the commit), best of 9 rounds per row. Workers differ between rounds, so only ratios within
one round are compared. Unclemax runs this code ≈ 1.35–1.4× faster (plan §1).

| File | What it is |
|---|---|
| `round1-native.txt` | every probe row plus `V5`/`V5v`, validate-only hashing keys with FNV-1a (invocation `4e5122da-0325-4eac-95f6-b69ad96bb630`) |
| `round2-native.txt` | the same after `null_writer` switched to `scratch_hash` (eight bytes per multiply; it builds no directory, so it needs no FNV-1a), the shipped state (invocation `5aa5a5f4-d8f2-4cbc-9a47-e506aec7dd6c`) |
| `gbps_probe.cc` | the probe with the `V5`/`V5v` rows and `V3`/`V4` pointed at `json::legacy` |

## Hypotheses (plan Task 2 Step 3) against round 2 (round 1 in parentheses)

| Hypothesis | small 210 B | medium 4.3 KB | big 228 KB | huge 4.85 MB | Met |
|---|---:|---:|---:|---:|---|
| `V5 ≥ 0.92 × T8` (big, huge) | 0.948 | 0.866 | **0.927** (0.910) | 0.880 (0.875) | big only |
| `V5 ≥ 1.5 × V4` (big, huge) | 1.321 | 1.353 | 1.428 (1.375) | 1.401 (1.392) | no |
| `V5 ≥ 1.35 × V4` (small) | 1.321 (1.334) | | | | no (−2 %) |
| `V5v ≥ 0.95 × W2c` (big, huge) | 0.854 | 0.761 | 0.819 (0.779) | 0.817 (0.784) | no |

Absolute (round 2): `V5` 549 / 602 / 669 / 712 MB/s, `V5v` 649 / 715 / 759 / 805 MB/s against `V4`
415 / 445 / 469 / 508 MB/s on this worker; × 1.38 for unclemax gives `V5` ≈ 0.92–0.98 GB/s on
big/huge and `V5v` ≈ 1.05–1.11 GB/s.

## Readings

- **The compact tape removes 29–30 % of the decode** (`V5/V4` 1.40–1.43 on big/huge, 1.32–1.35
  on small/medium): the side tables and 24-byte nodes are gone. It falls short of the 1.5 the plan
  derived from `T8/V4` because `V5` carries what `T8` does not: a second word for every escaped
  string (raw span), a directory run per object (240,000 entries on huge, 1.9 MB), value and text
  limits, and span bounds checks. On huge the tape is 1.91× the input (words 6.9 MB, directory
  1.9 MB, pool 0.44 MB) against `T8`'s 1.27×, and that document is memory-bound: `V5/T8` is 0.88
  there and 0.93 on big.
- **Directory-fused duplicates cost what the plan expected on a retained tape**: `V5` is 7 %
  below `T8` on big, where `T8` has the `key_table` check but neither directories nor spans.
- **Validate-only is not ≈ the walk.** `V5v` is 0.82 × `W2c` on big/huge: the duplicate check costs
  ≈ 4.5 ns per key even sorted per object with a cheap hash, because nothing else builds the run it
  sorts (on a retained tape the directory pays for it). `V5v` is 5 % faster than the `key_table`
  check (`W3c`) and ≈ 13 % faster than `V5`; FNV-1a → `scratch_hash` gained 4 points of `W2c`
  (round 1 → 2). The `W2c` row computes no key hash at all, so `0.95 × W2c` would need the
  duplicate check, hash included, below 1 ns per key.
- Small documents: `V5/V4` 1.32 is within 2 % of the 1.35 hypothesis; stage 1, parser setup and
  the arena reset dominate a 210-byte decode.
