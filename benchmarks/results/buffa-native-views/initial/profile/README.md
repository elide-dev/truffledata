# Initial native-view profile

Source checkpoint: `6a9f4da` (same production sources as the initial timing experiment). Profile image build: https://app.buildbuddy.io/invocation/49443f86-8b90-4bae-8b77-980e293b2830.

Three serial CPU-2 runs on unclemax, 997 Hz hardware cycle sampling with frame pointers, no lost perf samples. JFR records after warmup; perf includes startup. See `run.sh` for exact flags. Binary and JFR hashes are in `profile-summary.json`; raw JFR/perf files remain in `/tmp/truffledata-native-views.G2ATYf/profiles` on unclemax.

Projected native-view ingestion: `DirectByteBuffer.getInt` 20.02% self cycles; native binary decoder 13.58%; Java view validator 12.42%; ordinal helper 6.88%; next helper 5.21%; duplicate cell charging 2.34%. These are sampled self shares, not percentages of the measured slowdown. Full consumption also pays uncached library dispatch and lazy index/cache creation.

This evidence motivates fusing the Java semantic checks and reusing native budget proofs. JFR allocation sample counts do not represent allocated bytes, and neither this profile nor Java allocation counters measures retained native index storage.
