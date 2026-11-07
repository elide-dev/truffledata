# Directed default decode: before/after (a58a36d vs 83e8ebc)

`svm/` and `jvm/` hold one paired run: `baseline` is `83e8ebc` (ABI 9 views) and `candidate`
is `a58a36d` (default `decode()` builds owned rows from view directories). Both binaries ran in
every shard on the same BuildBuddy worker. Raw per-shard samples are in `*-raw/`. Invocation:
<https://app.buildbuddy.io/invocation/eac664cf-dbb0-4d0e-a5e2-329b6b11c9c3>. Method and caveats
are in [the cycle README](../protobuf-f26678a/README.md). The `candidate` columns are the final state
of this cycle.

Default eager decode (`native-eager`) on SVM (ns/row; × stock in the same shard):

| Cell | Before | After |
|---|---:|---:|
| Full decode only | 1,697.0 (2.19×) | 1,439.0 (1.86×) |
| Sparse decode only | 1,710.3 (2.22×) | 1,454.9 (1.90×) |
| Full resident | 1,986.1 (2.32×) | 1,721.7 (2.03×) |
| Sparse resident | 1,827.6 (2.36×) | 1,563.7 (1.99×) |
| Holdout resident | 4,534.1 (2.14×) | 3,989.9 (1.87×) |
| Large decode only | 541,430.8 (2.42×) | 460,382.1 (2.04×) |
| Java bytes/row, full resident | 1,737 | 1,424 |

The view code is identical in both binaries. Its cells give the noise floor of this worker set:
full peak measured 171.9 and 222.8 ns, and large decode measured 198,510 and 282,883 ns. Native
eager reads are unchanged. Text decoding, strict audits, required fields and merges still use
the State path.
