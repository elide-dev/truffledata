package dev.elide.truffledata.yaml;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.yaml.generated.YamlFFM;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Path;
import java.util.Objects;

/** JVM FFM entry point; each session owns its library until its last retained message closes. */
public final class YamlJvm {
  private YamlJvm() {}

  @TruffleBoundary
  public static YamlSession openSession(Path library) {
    return openSession(library, YamlOptions.DEFAULT);
  }

  @TruffleBoundary
  public static YamlSession openSession(Path library, YamlOptions options) {
    Objects.requireNonNull(options);
    Arena arena = Arena.ofConfined();
    try {
      SymbolLookup symbols = SymbolLookup.libraryLookup(library.toAbsolutePath(), arena);
      YamlFFM.LibraryBinding calls = new YamlFFM.LibraryBinding(symbols);
      MemorySegment fingerprint;
      try {
        fingerprint = calls.fingerprint();
      } catch (Throwable failure) {
        throw callFailure(failure);
      }
      if (!fingerprint.reinterpret(65).getString(0).equals(YamlFFM.ABI_FINGERPRINT))
        throw new IllegalStateException("Myna/YAML ABI fingerprint mismatch");
      return new YamlSession(options, new JvmSessionBackend(options, calls, arena));
    } catch (RuntimeException | Error failure) {
      arena.close();
      throw failure;
    }
  }

  static RuntimeException callFailure(Throwable failure) {
    if (failure instanceof Error error) throw error;
    if (failure instanceof RuntimeException runtime) return runtime;
    return new IllegalStateException("YAML native call failed", failure);
  }
}
