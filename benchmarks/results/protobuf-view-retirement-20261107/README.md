# Native protobuf view retirement

Seam ABI 11 removes the four ABI 9 view exports, the retired scalar fast-path export, and their View/Scalars result structs. TDS1 slabs and TDP1 projection bytes are unchanged. The typed index remains for text/ProtoJSON and as an independent binary validation oracle; retired format 3 is rejected. Myna 0.13.0 generates fingerprint `5bd597bd050e2bf8bc22a54529dbfa28be83491afb14ab03d3a9a2c2e2a01e52`, and the descriptor policy check passes. Symbol inspection confirms the five removed exports are absent while slab exports remain.

All 42 remaining native tests pass on macOS arm64 with AddressSanitizer and leak detection. Eight tests exclusively exercised the deleted view/scalar implementations and are removed with it. The 8,000-case slab differential is preserved by reading the independent typed decoder's preorder index directly, checking values, presence, repeated order, nesting, certification/merge status and rejection behavior; it no longer constructs view directories or owners. Goldens, projection corpus, budgets, allocation assertions and failure injection remain.

This local checkpoint does not replace final remote Java/native, multi-platform publication, sanitizer and ABI consumer qualification.
