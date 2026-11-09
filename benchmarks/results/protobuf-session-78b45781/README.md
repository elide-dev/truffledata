# Session and compiled-producer qualification

Invocation `78b45781-b0ce-4712-a70b-026f3c9fbc0d` on Linux x86-64, GraalVM
25.5.5-elide.27. Buffa JVM/SVM, native-free fallback, 56,000-case producer parity,
runtime, 49 native tests and full consumer JVM/SVM smoke passed. Session read
allocation, stale access, pin, reset and lifetime checks passed. The full resident
consumer compiled within the unchanged 10,000-node graph budget on both runtimes.

A4's explicit compiled decoder passed byte/rejection parity and compiled both the
representative Type and 64-field layouts within 10,000 nodes on JVM. Its median
389.7 ns/op versus generated 156.6 ns/op (~2.49x) misses the <=1.2x adoption gate.
These short same-process samples are advisory; no production decoder switch was
made. The Native Image A4 build exposed two cold error paths reaching blocked JDK
methods; its separate retry remains required.

The invocation had three unrelated-to-session failures: A4 Native Image build,
core test dependency SimulationBenchmark nullability compilation, and the YAML
parser performance gate. They are not represented as a green whole-repository
run. Raw test output is retained here.
