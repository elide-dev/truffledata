package dev.elide.truffledata.comparison;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.lowlevel.Parse;
import org.snakeyaml.engine.v2.schema.CoreSchema;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** JVM-only competitors: no reflective tree constructors are linked into our static image. */
@NullMarked
final class YamlCompetitors implements YamlComparison.Engine {
  private final String name;
  private final @Nullable Load loader;
  private final @Nullable Parse parser;
  private final @Nullable Yaml legacy;
  private final @Nullable ObjectMapper jackson;

  YamlCompetitors(String name) {
    if (!YamlComparison.FOREIGN.contains(name)) throw new IllegalArgumentException(name);
    this.name = name;
    var settings =
        LoadSettings.builder()
            .setSchema(new CoreSchema())
            .setAllowDuplicateKeys(false)
            .setMaxAliasesForCollections(10000)
            .build();
    loader = name.equals("snakeyaml-engine-load") ? new Load(settings) : null;
    parser = name.equals("snakeyaml-engine-events") ? new Parse(settings) : null;
    var options = new LoaderOptions();
    options.setAllowDuplicateKeys(false);
    options.setMaxAliasesForCollections(10000);
    legacy = name.equals("snakeyaml-1.1-load") ? new Yaml(new SafeConstructor(options)) : null;
    jackson = name.equals("jackson-yaml-tree") ? new ObjectMapper(new YAMLFactory()) : null;
  }

  public long ingest(YamlFixture.Input input) throws Exception {
    var stream = new ByteArrayInputStream(input.bytes());
    if (parser != null) {
      long events = 0;
      for (var event : parser.parseInputStream(stream)) {
        YamlComparison.retain(event);
        events++;
      }
      return events;
    }
    boolean documents = input.name().equals("yaml-stream");
    long checksum = 0;
    if (jackson != null) {
      try (var parser = jackson.createParser(stream)) {
        while (parser.nextToken() != null) {
          JsonNode root = jackson.readTree(parser);
          YamlComparison.retain(root);
          if (documents) checksum += jacksonRead(root);
          else for (var row : root.get("rows")) checksum += jacksonRead(row);
        }
      }
      return checksum;
    }
    Iterable<?> roots =
        loader != null
            ? loader.loadAllFromInputStream(stream)
            : java.util.Objects.requireNonNull(legacy).loadAll(stream);
    for (Object root : roots) {
      YamlComparison.retain(root);
      if (documents) checksum += read(root);
      else
        for (Object row :
            (List<?>) java.util.Objects.requireNonNull(((Map<?, ?>) root).get("rows")))
          checksum += read(row);
    }
    return checksum;
  }

  private static long read(Object tree) {
    var row = (Map<?, ?>) tree;
    var dims = (Map<?, ?>) java.util.Objects.requireNonNull(row.get("dims"));
    return ((Number) java.util.Objects.requireNonNull(row.get("id"))).longValue()
        + ((Number) java.util.Objects.requireNonNull(row.get("price"))).longValue()
        + ((Boolean) java.util.Objects.requireNonNull(row.get("active")) ? 1 : 0)
        + ((Number) java.util.Objects.requireNonNull(dims.get("w"))).longValue()
        + ((Number) java.util.Objects.requireNonNull(dims.get("h"))).longValue()
        + ((List<?>) java.util.Objects.requireNonNull(row.get("tags"))).size()
        + ((List<?>) java.util.Objects.requireNonNull(row.get("scores"))).size()
        + (row.get("note") instanceof String ? 1 : 0)
        + (row.containsKey("parent") && row.get("parent") == null ? 1 : 0);
  }

  private static long jacksonRead(JsonNode row) {
    var dims = row.get("dims");
    return java.util.Objects.requireNonNull(row.get("id")).longValue()
        + java.util.Objects.requireNonNull(row.get("price")).longValue()
        + (java.util.Objects.requireNonNull(row.get("active")).booleanValue() ? 1 : 0)
        + java.util.Objects.requireNonNull(dims.get("w")).longValue()
        + java.util.Objects.requireNonNull(dims.get("h")).longValue()
        + java.util.Objects.requireNonNull(row.get("tags")).size()
        + java.util.Objects.requireNonNull(row.get("scores")).size()
        + (row.get("note").isTextual() ? 1 : 0)
        + (row.get("parent").isNull() ? 1 : 0);
  }
}
