package dev.elide.truffledata.comparison;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.jspecify.annotations.NullMarked;

/** Exact benchmark getter metadata needed by the stock protobuf-java host-interop cell. */
@NullMarked
public final class StockInteropFeature implements Feature {
  @Override
  public void beforeAnalysis(BeforeAnalysisAccess access) {
    register(
        com.google.protobuf.Type.class,
        "getName",
        "getFieldsCount",
        "getOneofsCount",
        "hasSourceContext",
        "getSourceContext",
        "getSyntaxValue");
    try {
      RuntimeReflection.register(com.google.protobuf.Type.class.getMethod("getFields", int.class));
      RuntimeReflection.register(com.google.protobuf.Type.class.getMethod("getOneofs", int.class));
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException(failure);
    }
    register(
        com.google.protobuf.Field.class,
        "getName",
        "getNumber",
        "getKindValue",
        "getCardinalityValue",
        "getPacked");
    register(com.google.protobuf.SourceContext.class, "getFileName");
  }

  private static void register(Class<?> type, String... methods) {
    RuntimeReflection.register(type);
    try {
      for (String name : methods) RuntimeReflection.register(type.getMethod(name));
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException(failure);
    }
  }
}
