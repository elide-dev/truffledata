package dev.elide.truffledata.yaml;

import java.nio.file.Path;

/**
 * The opt-in Myna Truffle cells route on HotSpot, {@code -Dtruffledata.yaml.cells=true}: the
 * decode-path native calls of {@link YamlJvm} sessions go through the FFM cells of the separate
 * {@code truffledata-yaml-cells} artifact. The flag is read once, when this holder initializes (a
 * {@code static final} the JIT folds), and selects the backend in {@link YamlJvm#openSession}; no
 * decode path tests it. Native images choose the route at build time instead ({@link YamlFeature}).
 */
final class CellsRoute {
  static final String PROPERTY = "truffledata.yaml.cells";
  static final boolean ENABLED = Boolean.getBoolean(PROPERTY);
  static final String JVM_BACKEND = "dev.elide.truffledata.yaml.CellsJvmSessionBackend";

  private CellsRoute() {}

  /**
   * Wraps {@code backend} in the cells backend. On failure the backend is released (which lets the
   * library go) and the failure keeps its type.
   */
  static YamlSession.Backend jvm(JvmSessionBackend backend, Path library) {
    try {
      Class<?> type;
      try {
        type = Class.forName(JVM_BACKEND, true, CellsRoute.class.getClassLoader());
      } catch (ClassNotFoundException missing) {
        throw new IllegalStateException(
            "-D" + PROPERTY + "=true needs truffledata-yaml-cells on the class path", missing);
      }
      try {
        return (YamlSession.Backend)
            type.getDeclaredConstructor(JvmSessionBackend.class, Path.class)
                .newInstance(backend, library);
      } catch (java.lang.reflect.InvocationTargetException failure) {
        if (failure.getCause() instanceof RuntimeException exception) throw exception;
        if (failure.getCause() instanceof Error error) throw error;
        throw new IllegalStateException("YAML cells backend", failure.getCause());
      } catch (ReflectiveOperationException failure) {
        throw new IllegalStateException("YAML cells backend", failure);
      }
    } catch (RuntimeException | Error failure) {
      backend.release();
      throw failure;
    }
  }
}
