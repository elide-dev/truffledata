# IndexScan framing loop, local Native Image (macOS arm64, @graalvm_macos_aarch64, -march=native, -O2)

objdump -d of the method symbol (-H:-DeleteLocalSymbols). Directional evidence of code shape; timings are the RBE probes.

| Method | Instructions | Stack frame | Stack loads | Stack stores | Calls (bl) |
|---|---:|---:|---:|---:|---:|
| HEAD d73e093 IndexScan.frame | 7652 | 368 B | 670 | 382 | 292 |
| split, no cutoff: framePlain | 1108 | 224 B | 231 | 98 | 20 |
| split, no cutoff: frameFused | 56740 | 480 B | 5952 | 1998 | 2590 |
| fix 24ac7fe: framePlain | 1108 | 224 B | 231 | 98 | 20 |
| fix 24ac7fe: frameFused | 7832 | 352 B | 780 | 483 | 295 |
