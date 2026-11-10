package dev.elide.truffledata.yaml;

import dev.elide.truffledata.tape.TypedLayout;

/**
 * The opt-in Myna Truffle cells route of the static backend ({@code -Dtruffledata.yaml.cells=true}
 * at image build time, with {@link YamlFeature}): an image singleton registered by the Feature
 * when, and only when, the image is built with the flag. {@link YamlSvm} tests for it with {@code
 * ImageSingletons.contains}, which Native Image folds at build time, so an image built without the
 * flag has no branch and no cells code.
 */
interface SvmCells {
  /** A session backend whose decode-path native calls go through cells. */
  YamlSession.Backend session(YamlOptions options);

  /** A plan whose typed decode goes through a cell. */
  YamlPlan plan(TypedLayout layout, YamlOptions options);
}
