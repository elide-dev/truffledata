package dev.elide.truffledata.yaml;

import dev.elide.truffledata.tape.TypedLayout;
import dev.elide.truffledata.yaml.cells.generated.YamlCellsTruffle;

/**
 * The cells route of the static backend, registered by {@link YamlFeature} under the flag: the
 * image's cells, and the factory of the backends that call them.
 *
 * <p>The cells are created and adopted once, when {@link YamlFeature} constructs this singleton in
 * the image builder: they live in the image heap and hold no address. Every session and plan, on
 * every thread, shares them, like the nodes of any multi-threaded Truffle AST. {@link YamlSvm#decode}
 * opens a session per call, so cells owned by each backend would add ten cells, their coercion
 * nodes and ten root nodes to every one-shot decode. Sharing is safe because the backends pass
 * only {@code NativePointer}, {@code Integer} and {@code Long} arguments: every coercion takes
 * those on a path that reads final state and never inserts an interop library, so a cell call
 * mutates nothing. The argument arrays stay with each backend, which is confined to its owner's
 * thread ({@link Cells}).
 */
final class CellsSvm implements SvmCells {
  final YamlCellsTruffle.session_decode_Cell decode =
      Cells.adopt(new YamlCellsTruffle.session_decode_Cell());
  final YamlCellsTruffle.session_write_text_Cell writeText =
      Cells.adopt(new YamlCellsTruffle.session_write_text_Cell());
  final YamlCellsTruffle.session_retain_Cell retain =
      Cells.adopt(new YamlCellsTruffle.session_retain_Cell());
  final YamlCellsTruffle.stream_feed_Cell feed =
      Cells.adopt(new YamlCellsTruffle.stream_feed_Cell());
  final YamlCellsTruffle.stream_next_Cell next =
      Cells.adopt(new YamlCellsTruffle.stream_next_Cell());
  final YamlCellsTruffle.session_reset_Cell reset =
      Cells.adopt(new YamlCellsTruffle.session_reset_Cell());
  final YamlCellsTruffle.stream_create_Cell streamCreate =
      Cells.adopt(new YamlCellsTruffle.stream_create_Cell());
  final YamlCellsTruffle.stream_finish_Cell streamFinish =
      Cells.adopt(new YamlCellsTruffle.stream_finish_Cell());
  final YamlCellsTruffle.stream_release_Cell streamRelease =
      Cells.adopt(new YamlCellsTruffle.stream_release_Cell());
  final YamlCellsTruffle.typed_decode_Cell typedDecode =
      Cells.adopt(new YamlCellsTruffle.typed_decode_Cell());

  CellsSvm() {}

  /** The image's route: the singleton {@link YamlFeature} registered when built with the flag. */
  static CellsSvm image() {
    return (CellsSvm) SvmRoute.cells();
  }

  @Override
  public YamlSession.Backend session(YamlOptions options) {
    return new CellsSvmSessionBackend(this, options);
  }

  @Override
  public YamlPlan plan(TypedLayout layout, YamlOptions options) {
    return CellsSvmPlanBackend.create(this, layout, options);
  }
}
