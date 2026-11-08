# Alias-safe shape-key replacement

Validate-only accepted a duplicate after replacing a cached key sequence: validate `{"aa":1,"bb":2,"cc":3}`, then `{"aa":1,"bb":2,"dd":3}`, then `{"\u0000\u0000":1,"\u0000\u0000":2,"dd":3}`. The null writer materializes unchanged prefix keys as views into the old template. `key.set` cleared its destination before copying from that aliased source, changing the prefix bytes to NUL while retaining their original hash. A later escaped-NUL sequence could therefore match the corrupted template and skip duplicate rejection.

The setter now moves decoded bytes before clearing only unused tail bytes. Same-address and overlapping-source tests protect both cases. The exact three-document regression runs in indexed, directoryless and validate-only modes. The million-sequence sorted-key oracle now includes decoded NUL keys and escapes raw controls consistently.

The before/after reproduction demonstrates the original validate-only acceptance and fixed rejection; retained mode rejected in both versions. ASan+UBSan passes16 compact-writer groups,1,000,000 key sequences in all three modes (3,000,000 decodes), and the independent stage2 corpus200/malformed60/grammar/numeric-token cases.

Audit: the shared setter has two callers. Retained compact templates copy from tape input/pool, so their source does not alias the template. Null-writer prefix materialization does alias and is fixed. YAML's independent short-key templates assign loaded `lo`/`hi` scalar values and stop recording on a followed-template mismatch; there is no zero-before-copy path there. No performance experiment is included in this change.
