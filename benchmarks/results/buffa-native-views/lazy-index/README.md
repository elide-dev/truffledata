# Lazy SVM index wrapper: initial experiment

Initial lazy-wrapper change on `7a2568f`, before deferring buffer selection past cache hits. SVM view decoding keeps the native index address and creates its Java ByteBuffer wrapper only if an audit or fallback consumer asks for bytes. Eager/JVM paths retain their buffer behavior. A detached buffer can first be requested after decoder shutdown.

JVM eager/views/strict views passed: https://app.buildbuddy.io/invocation/0fbad2a7-de96-401f-aaaf-158e817a0c7b. SVM strict eager/views/Java-only fallback passed: https://app.buildbuddy.io/invocation/70d80d74-bddb-43ad-a1a2-827b39f919fc.

Five fresh randomized forks per metric/cell on unclemax CPU 2, protocol `../full/run.py`; remote `/tmp/truffledata-native-views.G2ATYf/lazy-index`. Saves exactly 64 Java bytes per view decode: projected 421.5, full 1382.25 bytes/row. Native storage is excluded. Generated ingestion is 770.6 ns projected / 1406.8 ns full; stock controls are 635.3 / 679.6 ns. Retained interpreter/peak reads regress to 17.42 / 9.97 ns from approximately 16.9 / 9.4 ns.

The follow-up experiment defers buffer selection until an actual index load, keeping cache hits free of that branch. These initial numbers must not be presented as the follow-up's results.
