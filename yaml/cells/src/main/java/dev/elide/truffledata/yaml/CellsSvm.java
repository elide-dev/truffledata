package dev.elide.truffledata.yaml;

import dev.elide.truffledata.tape.TypedLayout;

/**
 * The cells route of the static backend, registered by {@link YamlFeature} under the flag: the
 * factory of the backends that call the cells. It holds nothing: the backends call each cell's
 * static {@code invoke} entry ({@link CellsSvmSessionBackend}), so no cell instance exists.
 */
final class CellsSvm implements SvmCells {
  CellsSvm() {}

  @Override
  public YamlSession.Backend session(YamlOptions options) {
    return new CellsSvmSessionBackend(options);
  }

  @Override
  public YamlPlan plan(TypedLayout layout, YamlOptions options) {
    return CellsSvmPlanBackend.create(layout, options);
  }
}
