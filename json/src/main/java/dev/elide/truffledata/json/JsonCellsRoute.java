package dev.elide.truffledata.json;

import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;

/**
 * The opt-in Myna Truffle cells route, {@code -Dtruffledata.json.cells=true}: the document decode's
 * native calls ({@code td_json_decode}, {@code td_json_decode_adopted}, {@code td_json_release})
 * go through the cells of the separate {@code truffledata-json-cells} artifact ({@code
 * //json/cells}). Default off.
 *
 * <p>On HotSpot the flag is read once, when this holder initializes (a {@code static final} the JIT
 * folds), and {@link JsonJvm} binds the cells when it opens. In a Native Image the route is chosen
 * when the image is built: {@code JsonCellsFeature} (in the cells artifact, registered with {@code
 * --features}) installs the {@link Svm} singleton when the flag is set, and this holder, initialized
 * at image build time with the rest of {@code dev.elide.truffledata}, folds {@link #ENABLED}, so an
 * image built without the flag compiles the baseline calls alone. A flag without the Feature (or
 * the Feature's route without the flag) fails the image build: there is no silent fallback from one
 * route to the other.
 */
final class JsonCellsRoute {
  static final String PROPERTY = "truffledata.json.cells";
  static final String JVM_BACKEND = "dev.elide.truffledata.json.CellsFfm";
  static final boolean ENABLED = enabled();

  private JsonCellsRoute() {}

  private static boolean enabled() {
    boolean requested = Boolean.getBoolean(PROPERTY);
    if (!ImageInfo.inImageCode()) return requested;
    boolean registered = ImageSingletons.contains(Svm.class);
    if (ImageInfo.inImageBuildtimeCode() && requested != registered)
      throw new IllegalStateException(
          "-D"
              + PROPERTY
              + "="
              + requested
              + " needs --features=dev.elide.truffledata.json.JsonCellsFeature and"
              + " truffledata-json-cells on the image class path");
    return registered;
  }

  /**
   * The cells route's native calls in a Native Image; arguments in seam order, addresses raw. Each
   * returns the native status exactly as the static binding does.
   */
  abstract static class Svm {
    abstract int decode(long data, long length, JsonLimits limits, long result);

    abstract int decodeAdopted(
        long data, long length, long capacity, JsonLimits limits, long result);

    abstract void release(long result);
  }

  /** The cells route's native calls on HotSpot (FFM-backed cells). */
  interface Jvm {
    int decodeAdopted(
        MemorySegment data, long length, long capacity, JsonLimits limits, MemorySegment result);

    void release(MemorySegment result);
  }

  /** The image's cells (only reachable when {@link #ENABLED} folds true). */
  static Svm svm() {
    return ImageSingletons.lookup(Svm.class);
  }

  /** Binds the HotSpot cells to {@code library}, whose production fingerprint was verified. */
  static Jvm jvm(Path library) {
    Class<?> type;
    try {
      type = Class.forName(JVM_BACKEND, true, JsonCellsRoute.class.getClassLoader());
    } catch (ClassNotFoundException missing) {
      throw new IllegalStateException(
          "-D" + PROPERTY + "=true needs truffledata-json-cells on the class path", missing);
    }
    try {
      return (Jvm) type.getDeclaredConstructor(Path.class).newInstance(library);
    } catch (java.lang.reflect.InvocationTargetException failure) {
      if (failure.getCause() instanceof RuntimeException exception) throw exception;
      if (failure.getCause() instanceof Error error) throw error;
      throw new IllegalStateException("JSON cells backend", failure.getCause());
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("JSON cells backend", failure);
    }
  }
}
