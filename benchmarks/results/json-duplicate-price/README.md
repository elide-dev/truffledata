# Duplicate-check price diagnostic

`//json/native:duplicate_probe` compares the production directoryless structural writer (S3n) to a test-only composition wrapper (S3x). The wrapper retains the same stage1, stage2 grammar, number/text validation, limits, structural writer and output. It removes key announcements, shape templates and duplicate rejection; the unchecked object close asserts distinctness only inside this executable. It adds no unchecked production option.

The200-document valid corpus must produce identical words/pool in both modes. Raw and escaped duplicate discriminators must reject only in S3n. The local ASan+UBSan run passes these202 comparisons. Timings alternate order for five rounds and warm each mode with at least100MB. Both modes require zero steady upstream index allocations.

Local median S3n/S3x ratios on the exact210/4,330/228,485/4,850,894-byte fixtures are1.029×/1.067×/1.086×/1.058×. Templates also accelerate repeated clean-key parsing, so the complete checked path can outperform the scanner with no templates. These are local diagnostic results; the required≥0.85× qualification awaits the pinned RBE executable on unclemax. All rounds, including the small0.946× outlier, are retained.
