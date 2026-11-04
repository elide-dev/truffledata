package dev.elide.bazel.junit;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.Filter;
import org.junit.platform.engine.discovery.ClassNameFilter;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TagFilter;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.reporting.legacy.xml.LegacyXmlReportGeneratingListener;

/**
 * JUnit Platform test runner for Bazel's java_test (Gradle's useJUnitPlatform in a test task).
 *
 * <p>Arguments, in order: {@code --package <name>} selects every test class of a package
 * (repeatable), {@code --class <name>} selects one class explicitly, {@code --include-tag}/{@code
 * --exclude-tag <tag>}, {@code --include-class}/{@code --exclude-class <regex>} (fully qualified
 * names; Gradle's filter patterns), and {@code -D<key>=<value>} system properties set before
 * discovery, where {@code ${NAME}} reads the environment (TEST_UNDECLARED_OUTPUTS_DIR, for
 * reports). Later properties win, so {@code --test_arg=-D...} overrides a target's defaults
 * (Gradle's -P properties).
 *
 * <p>Bazel sharding distributes whole test classes (Gradle's forks): each shard runs the classes
 * whose sorted position modulo TEST_TOTAL_SHARDS is TEST_SHARD_INDEX. A test that shards its own
 * work (the feature and corpus ratchets) asks for {@code --shard-properties <index property>,<count
 * property>} instead: every shard runs every selected class, with TEST_SHARD_INDEX and
 * TEST_TOTAL_SHARDS in those system properties. Results are written as JUnit XML to
 * XML_OUTPUT_FILE.
 */
public final class BazelJUnitRunner {
  private static final Pattern VARIABLE = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");

  private BazelJUnitRunner() {}

  public static void main(String[] args) throws IOException {
    var packages = new ArrayList<String>();
    var explicitClasses = new ArrayList<String>();
    var filters = new ArrayList<Filter<?>>();
    String[] shardProperties = null;
    for (int i = 0; i < args.length; i++) {
      var arg = args[i];
      if (arg.startsWith("-D")) {
        int eq = arg.indexOf('=');
        if (eq < 0) throw new IllegalArgumentException("Expected -Dkey=value: " + arg);
        System.setProperty(arg.substring(2, eq), expand(arg.substring(eq + 1)));
        continue;
      }
      if (i + 1 >= args.length) throw new IllegalArgumentException("Missing value for " + arg);
      var value = args[++i];
      switch (arg) {
        case "--package" -> packages.add(value);
        case "--class" -> explicitClasses.add(value);
        case "--include-tag" -> filters.add(TagFilter.includeTags(value));
        case "--exclude-tag" -> filters.add(TagFilter.excludeTags(value));
        case "--include-class" -> filters.add(ClassNameFilter.includeClassNamePatterns(value));
        case "--exclude-class" -> filters.add(ClassNameFilter.excludeClassNamePatterns(value));
        case "--shard-properties" -> {
          shardProperties = value.split(",");
          if (shardProperties.length != 2)
            throw new IllegalArgumentException("Expected <index>,<count>: " + value);
        }
        default -> throw new IllegalArgumentException("Unknown argument: " + arg);
      }
    }
    if (packages.isEmpty() && explicitClasses.isEmpty())
      throw new IllegalArgumentException("No --package or --class selected");
    var launcher = LauncherFactory.create();
    var selectors = new ArrayList<DiscoverySelector>();
    for (var name : packages) selectors.add(selectPackage(name));
    for (var name : explicitClasses) selectors.add(selectClass(name));
    var request = request(selectors, filters);
    var shards = System.getenv("TEST_TOTAL_SHARDS");
    if (shards != null && shardProperties != null) {
      // The test shards its own work: every shard runs it, told which shard it is.
      var status = System.getenv("TEST_SHARD_STATUS_FILE");
      if (status != null) Files.write(Path.of(status), new byte[0]);
      System.setProperty(shardProperties[0], System.getenv("TEST_SHARD_INDEX"));
      System.setProperty(shardProperties[1], shards);
    } else if (shards != null) {
      int count = Integer.parseInt(shards);
      int index = Integer.parseInt(System.getenv("TEST_SHARD_INDEX"));
      var status = System.getenv("TEST_SHARD_STATUS_FILE");
      if (status != null) Files.write(Path.of(status), new byte[0]);
      var classes = new TreeSet<String>();
      var plan = launcher.discover(request);
      for (var root : plan.getRoots())
        for (var test : plan.getDescendants(root)) className(test).ifPresent(classes::add);
      var mine = new ArrayList<DiscoverySelector>();
      int position = 0;
      for (var name : classes) if (position++ % count == index) mine.add(selectClass(name));
      if (mine.isEmpty()) {
        System.out.println("No test classes in shard " + index + " of " + count);
        return;
      }
      request = request(mine, filters);
    }
    var summary = new SummaryGeneratingListener();
    var xml = System.getenv("XML_OUTPUT_FILE");
    Path reports = xml == null ? null : Files.createTempDirectory("junit-reports");
    var listeners =
        new ArrayList<org.junit.platform.launcher.TestExecutionListener>(List.of(summary));
    if (reports != null)
      listeners.add(new LegacyXmlReportGeneratingListener(reports, new PrintWriter(System.err)));
    launcher.execute(
        request, listeners.toArray(org.junit.platform.launcher.TestExecutionListener[]::new));
    if (reports != null) {
      try (var files = Files.list(reports)) {
        var reportFiles = files.filter(p -> p.toString().endsWith(".xml")).sorted().toList();
        if (!reportFiles.isEmpty())
          Files.copy(
              reportFiles.getFirst(),
              Path.of(xml),
              java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
    }
    var result = summary.getSummary();
    result.printTo(new PrintWriter(System.out, true));
    result.printFailuresTo(new PrintWriter(System.out, true), 50);
    if (result.getTestsFoundCount() == 0 && (shards == null || shardProperties != null)) {
      System.out.println("No tests found");
      System.exit(1);
    }
    System.exit(result.getTotalFailureCount() == 0 ? 0 : 1);
  }

  private static LauncherDiscoveryRequest request(
      List<DiscoverySelector> selectors, List<Filter<?>> filters) {
    return LauncherDiscoveryRequestBuilder.request()
        .selectors(selectors)
        .filters(filters.toArray(Filter[]::new))
        .build();
  }

  private static java.util.Optional<String> className(TestIdentifier test) {
    return test.getSource()
        .filter(ClassSource.class::isInstance)
        .map(s -> ((ClassSource) s).getClassName());
  }

  private static String expand(String value) {
    Matcher matcher = VARIABLE.matcher(value);
    var out = new StringBuilder();
    while (matcher.find()) {
      var env = System.getenv(matcher.group(1));
      if (env == null)
        throw new IllegalArgumentException(
            "Unset environment variable in property value: " + value);
      matcher.appendReplacement(out, Matcher.quoteReplacement(env));
    }
    return matcher.appendTail(out).toString();
  }
}
