# TruffleData 0.15.0

Upgrade the TruffleData BOM, generated bindings and native attachments together. Myna 0.20.0 generates new ABI fingerprints for both supported LP64 classes. The Gradle and Bazel repository URL for the GraalVM dependencies is `https://maven.elide.dev/gvm-elide/25.5.5-elide.31`.

- The JVM and Native Image builds use Elide GraalVM 25.5.5-elide.31. The native libraries and Linux ASan checks use Elide toolchain 2026.11.7.
- Buffa and the experimental Bebop validator use Myna’s separate Rust records and implementation checks. The generated checks verify the records’ ABI fingerprint at compile time. The JSON and CSV Cargo headers match the new bindings on both ABI classes.
- The optional cells runtime inherits Myna’s support for host `BigInteger` arguments to integer coercions. All four cells routes remain opt-in.
- A rejected accessor layout or generated protobuf input transfers to the interpreter without explicitly invalidating the caller’s compiled code. Callers receive the same contract exceptions. TruffleCheck 0.1.1 continues to run in the Java build.
- The native YAML decoder handles an empty block scalar followed by a dedented line with a different line ending. It now restores the block header’s position using the header’s own line ending.

The README benchmark cards show the 0.12.0 capture. No new performance measurements were taken for this release.
