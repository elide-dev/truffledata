# ABI 9 schema-compiled native views: before/after (83e8ebc vs f26678a)

`svm/` and `jvm/` hold one paired run: `baseline` is the release-head harness image
(`f26678a`), and `candidate` is `83e8ebc`. Both binaries ran in every shard on the
same BuildBuddy worker, with cells interleaved by seed. The raw per-shard samples are in `*-raw/`. Invocation:
<https://app.buildbuddy.io/invocation/e63387ed-e43d-4a60-aa45-dfb62f64e1bf>.
The method, caveats and reproduction are in [the cycle README](../protobuf-f26678a/README.md).

Native-view cells changed as follows on SVM (ns/row; × stock, both from the same shard):

| Cell | Before | After |
|---|---:|---:|
| Sparse resident | 1,056.5 (1.30×) | 816.9 (1.03×) |
| Full resident | 1,690.9 (1.99×) | 1,203.7 (1.42×) |
| Full heap input | 1,690.2 (2.00×) | 1,295.7 (1.53×) |
| Full decode only | 902.3 (1.14×) | 714.9 (0.92×) |
| Full first compiled access | 739.5 | 609.5 |
| Sparse first compiled access | 149.5 | 109.2 |
| Holdout resident | 3,496.9 (1.65×) | 2,889.4 (1.35×) |
| Large resident (~52 KB) | 441,907.4 (1.79×) | 372,213.9 (1.38×) |
| Full peak | 163.3 (1.54×) | 171.2 (1.63×) |
| Full interpreter | 417.6 (6.83×) | 400.2 (6.49×) |
| Retained native bytes/row, full | 1,512 | 1,165.5 |
| Java bytes/row, full resident | 1,307.25 | 1,131.5 |

Peak and interpreter reads are unchanged within this shared host's noise. The large decode-only
cell moved from 189,209 to 205,771 ns, but the next run measured the identical view code at
198,510 and 282,883 ns. Large-message cells on these workers vary by up to about 40% between
binaries, so they are advisory.
