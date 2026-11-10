# Cells flag: paired Native Image measurements (tree `95700998`, Myna 0.16.0)

Flag-on image over flag-off image (`-Dtruffledata.<format>.cells=true` against the default),
three forks per cell, from `//benchmarks/comparison:{consumer_measure,yaml_pair,json_typed_pair,csv_pair}`.
Ratios below 1 mean the cells route was faster. "Controls" are cells whose code is the same in
both images; they show the noise floor of the pairing.

| Format | Routed cells | Median ratio | Min | Max | Disjoint fork ranges (faster / slower) | Controls median | Allocation |
|---|---|---|---|---|---|---|---|
| Buffa | 83 | 1.004 | 0.647 | 3.311 | 3 / 10 | 1.009 (49 cells) | equal in all 83 |
| YAML | 19 | 1.011 | 0.872 | 1.172 | 0 / 1 | none in this harness | equal in all 19 |
| CSV (1 MiB and 128 MiB) | 40 | 1.011 | 0.754 | 1.194 | 1 / 1 | 0.996 (78 cells) | equal in all 40 |
| JSON | none | not measured | | | | 1.004 (55 cells) | not measured |

Reading: the routed medians sit inside the control noise for Buffa, YAML and CSV. That is a tie,
not a win, so by the rule "default on only if cells beat the baseline on SVM" every
`truffledata.<format>.cells` flag stays **default off** in 0.11.0.

Not measured: the JSON route. `json_typed_pair` times `JsonSession`, which the cells route does
not serve, so it has no routed cell. A one-shot document decode probe exists on branch
`bench/json-document-probe` (`e3a52764`); it has never been built or run and is not part of the
release. JSON's flag is default off without a timing.

The wide Buffa extremes (0.647, 3.311) are single cells whose fork ranges overlap; see
`summary.txt` for the per-cell outliers and `pairs/` for every cell. `summarize_pairs.py`
regenerates `summary.json` and `pairs/` from the Bazel test outputs.
