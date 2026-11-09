package dev.elide.truffledata.comparison;

import com.google.protobuf.SourceContext;
import com.google.protobuf.Type;
import java.util.HashSet;
import java.util.Set;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.jspecify.annotations.NullMarked;

/** Probe-only reflection metadata for the generated protobuf-java host fixtures. */
@NullMarked
public final class SourceIngestionFeature implements Feature {
  @Override
  public void beforeAnalysis(BeforeAnalysisAccess access) {
    var visited = new HashSet<Class<?>>();
    register(Type.class, visited);
    register(com.google.protobuf.Field.class, visited);
    register(SourceContext.class, visited);
    register(com.google.protobuf.Syntax.class, visited);
    register(com.google.protobuf.Option.class, visited);
    register(com.google.protobuf.Any.class, visited);
  }

  private static void register(Class<?> type, Set<Class<?>> visited) {
    if (!visited.add(type)) return;
    RuntimeReflection.register(type);
    RuntimeReflection.register(type.getDeclaredMethods());
    RuntimeReflection.register(type.getDeclaredFields());
    for (Class<?> nested : type.getDeclaredClasses()) register(nested, visited);
  }
}
