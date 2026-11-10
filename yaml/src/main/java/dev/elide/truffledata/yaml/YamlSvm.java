package dev.elide.truffledata.yaml;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.elide.truffledata.Schema;
import dev.elide.truffledata.tape.TypedLayout;
import dev.elide.truffledata.yaml.generated.Yaml;
import dev.elide.truffledata.yaml.generated.YamlAbi;
import java.util.Map;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.type.CTypeConversion;

/**
 * Static-image YAML entry point ({@code truffledata-yaml-native}): sessions and plans over the
 * static archive linked into the image. {@link YamlSession#open(YamlOptions)} selects this backend
 * through {@link SvmSessionProvider} whenever the artifact is on an image's class path; images
 * without the archive leave the artifact (and its bindings) out and use the Java kernel.
 */
public final class YamlSvm {
  private YamlSvm() {}

  private static volatile boolean verified;

  static void requireRuntime() {
    if (!ImageInfo.inImageRuntimeCode())
      throw new UnsupportedOperationException("Static YAML requires SVM");
    if (!verified) {
      // The image's ABI class (Platform.includedIn folds at image build time): one constant.
      String expected = YamlAbi.fingerprint(Platform.includedIn(Platform.LINUX_AARCH64.class));
      if (!CTypeConversion.toJavaString(Yaml.fingerprint()).equals(expected))
        throw new IllegalStateException("Myna/YAML ABI fingerprint mismatch");
      verified = true;
    }
  }

  /** A session over the static archive; throws {@link UnsupportedOperationException} off SVM. */
  @TruffleBoundary
  public static YamlSession open() {
    return openSession(YamlOptions.DEFAULT);
  }

  static YamlSession openSession(YamlOptions options) {
    requireRuntime();
    // The cells route is fixed at image build time (YamlFeature); SvmRoute folds, and fails
    // the image build when the flag and the Feature disagree.
    if (SvmRoute.ENABLED)
      return new YamlSession(options, SvmRoute.cells().session(options));
    return new YamlSession(options, new SvmSessionBackend(options));
  }

  /** A plan over the static archive with the default options (see {@link YamlPlan#compile}). */
  public static YamlPlan compile(Schema root, Map<String, Schema> recordTypes) {
    requireRuntime();
    TypedLayout layout = new TypedLayout(root, recordTypes, YamlPlan.FORMAT);
    if (SvmRoute.ENABLED) return SvmRoute.cells().plan(layout, YamlOptions.DEFAULT);
    return SvmPlanBackend.create(layout, YamlOptions.DEFAULT);
  }

  @TruffleBoundary
  public static YamlMessage decode(byte[] input) {
    return decode(input, YamlOptions.DEFAULT);
  }

  @TruffleBoundary
  public static YamlMessage decode(byte[] input, YamlOptions options) {
    try (YamlSession session = openSession(options)) {
      return session.decode(input);
    }
  }
}
