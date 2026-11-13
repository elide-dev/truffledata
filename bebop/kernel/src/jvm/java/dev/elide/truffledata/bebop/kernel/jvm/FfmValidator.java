package dev.elide.truffledata.bebop.kernel.jvm;

import dev.elide.truffledata.TargetAbi;
import dev.elide.truffledata.bebop.BebopCodec;
import dev.elide.truffledata.bebop.BebopValidator;
import dev.elide.truffledata.bebop.kernel.generated.BebopNativeAbi;
import dev.elide.truffledata.bebop.kernel.generated.BebopNativeFFM;
import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;

/**
 * The native validator on the JVM. Inputs of at most {@link #CRITICAL_BYTES} are checked in place
 * through a critical downcall with heap access (no copy; the collector waits for the call). Larger
 * inputs are copied to native memory for an ordinary downcall. Thread-safe; the library stays
 * loaded for the life of the process.
 */
public final class FfmValidator extends BebopValidator {
  public static final int CRITICAL_BYTES = 1 << 20;

  private final MethodHandle critical;
  private final MethodHandle plain;

  private FfmValidator(MethodHandle critical, MethodHandle plain) {
    this.critical = critical;
    this.plain = plain;
  }

  /** Loads the shared library and verifies its ABI fingerprint for this host. */
  public static FfmValidator load(Path library) {
    try {
      SymbolLookup symbols = SymbolLookup.libraryLookup(library.toAbsolutePath(), Arena.global());
      MemorySegment fingerprint = (MemorySegment) BebopNativeFFM.fingerprint(symbols).invokeExact();
      String expected = BebopNativeAbi.fingerprint(TargetAbi.HOST.charUnsigned());
      // The library's fingerprint is 64 hex characters without a terminator.
      byte[] actual = fingerprint.reinterpret(expected.length()).toArray(ValueLayout.JAVA_BYTE);
      if (!new String(actual, StandardCharsets.US_ASCII).equals(expected))
        throw new IllegalStateException(
            "Myna/Bebop ABI fingerprint mismatch (expected the " + TargetAbi.HOST + " class)");
      MethodHandle critical =
          Linker.nativeLinker()
              .downcallHandle(
                  symbols.find("td_bebop_validate_leaf").orElseThrow(),
                  BebopNativeFFM.validate_leaf_DESCRIPTOR,
                  Linker.Option.critical(true));
      return new FfmValidator(critical, BebopNativeFFM.validate(symbols));
    } catch (RuntimeException | Error failure) {
      throw failure;
    } catch (Throwable failure) {
      throw new IllegalStateException(failure);
    }
  }

  @Override
  public String name() {
    return "native-ffm";
  }

  @Override
  public void validate(BebopCodec codec, byte[] bytes, int offset, int length) {
    Objects.checkFromIndexSize(offset, length, bytes.length);
    byte[] program = program(codec);
    long status;
    try {
      if (length <= CRITICAL_BYTES) {
        status =
            (long)
                critical.invokeExact(
                    MemorySegment.ofArray(program),
                    (long) program.length,
                    MemorySegment.ofArray(bytes).asSlice(offset, length),
                    (long) length);
      } else {
        try (Arena arena = Arena.ofConfined()) {
          MemorySegment schema = arena.allocate(program.length);
          MemorySegment.copy(program, 0, schema, ValueLayout.JAVA_BYTE, 0, program.length);
          MemorySegment data = arena.allocate(length);
          MemorySegment.copy(bytes, offset, data, ValueLayout.JAVA_BYTE, 0, length);
          status = (long) plain.invokeExact(schema, (long) program.length, data, (long) length);
        }
      }
    } catch (RuntimeException | Error failure) {
      throw failure;
    } catch (Throwable failure) {
      throw new IllegalStateException(failure);
    }
    report(status);
  }
}
