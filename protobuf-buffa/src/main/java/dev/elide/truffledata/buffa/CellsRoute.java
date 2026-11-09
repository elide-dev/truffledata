package dev.elide.truffledata.buffa;

import java.nio.file.Path;

/**
 * The opt-in Myna Truffle cells route, {@code -Dtruffledata.buffa.cells=true} (docs/BUFFA.md): the
 * slab producer's native call goes through the cells of the separate {@code
 * truffledata-protobuf-buffa-cells} artifact. On HotSpot the flag is read once, when this holder
 * initializes (a {@code static final} the JIT folds), and selects the backend at {@link
 * BuffaWire#open}; nothing on the decode path tests it. In a Native Image the route is chosen when
 * the image is built ({@link BuffaFeature} registers the backend factory), so an image built
 * without the flag contains no cells code and this holder is unreachable there.
 */
final class CellsRoute {
  static final String PROPERTY = "truffledata.buffa.cells";
  static final boolean ENABLED = Boolean.getBoolean(PROPERTY);
  static final String SVM_FACTORY = "dev.elide.truffledata.buffa.CellsSvmBackend$Factory";
  static final String JVM_BACKEND = "dev.elide.truffledata.buffa.CellsFfmBackend";

  private CellsRoute() {}

  static BuffaWire.Backend jvm(Path library) {
    Class<?> type;
    try {
      type = Class.forName(JVM_BACKEND, true, CellsRoute.class.getClassLoader());
    } catch (ClassNotFoundException missing) {
      throw new IllegalStateException(
          "-D" + PROPERTY + "=true needs truffledata-protobuf-buffa-cells on the class path",
          missing);
    }
    try {
      return (BuffaWire.Backend) type.getDeclaredConstructor(Path.class).newInstance(library);
    } catch (java.lang.reflect.InvocationTargetException failure) {
      // The FFM backend's own failures keep their types (AUTO falls back on the same ones).
      if (failure.getCause() instanceof RuntimeException exception) throw exception;
      if (failure.getCause() instanceof Error error) throw error;
      throw new IllegalStateException("Buffa cells backend", failure.getCause());
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Buffa cells backend", failure);
    }
  }
}
