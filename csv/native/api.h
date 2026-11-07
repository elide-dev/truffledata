// C ABI of the CSV structural indexer; declarations come from the Myna seam (csv.seam).
//
// td_csv_index / td_csv_index_leaf: index exactly `length` bytes (callers size calls with the
// index.h `indexable` rule; at most 2^29 - 1) into `out`, which must hold `length` entries.
// `format` packs delimiter | quote << 8 | quoting << 16, `kernel` selects an index.h Kernel (0
// for the best supported), and `carry` is the previous call's carry (0 at a stream start).
// Returns count << 8 | carry-out, or -1 (format), -2 (kernel), -3 (length), -4 (carry). The two
// symbols are identical; the leaf one is declared as a Myna native leaf (no thread transition)
// for small calls. td_csv_validate_utf8 returns -1 for strict UTF-8, else the offset of the
// first invalid or truncated sequence (simdutf).
#ifndef TRUFFLEDATA_CSV_API_H
#define TRUFFLEDATA_CSV_API_H

#include "csv/native/generated/seam.h"

#endif
