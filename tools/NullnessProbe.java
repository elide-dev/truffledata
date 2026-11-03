package dev.elide.truffledata.tools;

import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import org.jspecify.annotations.NullMarked;

/** Positive and negative compilation prove the configured checker actually executes. */
@NullMarked
public final class NullnessProbe {
  public static void main(String[] args) throws Exception {
    Path output =
        Files.createTempDirectory(
            Path.of(Objects.requireNonNull(System.getenv("TEST_TMPDIR"))), "nullaway-");
    check(output, "return value == null ? 0 : value.length();", true);
    check(output, "return value.length();", false);
  }

  private static void check(Path output, String body, boolean expected) {
    JavaCompiler compiler =
        new com.google.errorprone.ErrorProneJavaCompiler(
            com.google.errorprone.scanner.ScannerSupplier.fromBugCheckerClasses(
                com.uber.nullaway.NullAway.class,
                com.uber.nullaway.RequireExplicitNullMarking.class,
                com.uber.nullaway.JSpecifyUnrecognizedAnnotationLocation.class));
    StringWriter diagnostics = new StringWriter();
    String code =
        "import org.jspecify.annotations.*; @NullMarked class Probe {"
            + "static int length(@Nullable String value) {"
            + body
            + "}}";
    JavaFileObject source =
        new SimpleJavaFileObject(URI.create("string:///Probe.java"), JavaFileObject.Kind.SOURCE) {
          @Override
          public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
          }
        };
    List<String> options =
        List.of(
            "-d",
            output.toString(),
            "-classpath",
            System.getProperty("java.class.path"),
            "-XDcompilePolicy=simple",
            "--should-stop=ifError=FLOW",
            "-proc:none",
            "-XepDisableAllChecks",
            "-Xep:NullAway:ERROR",
            "-Xep:RequireExplicitNullMarking:ERROR",
            "-Xep:JSpecifyUnrecognizedAnnotationLocation:ERROR",
            "-XepOpt:NullAway:OnlyNullMarked=true",
            "-XepOpt:NullAway:JSpecifyMode=true");
    boolean passed =
        compiler.getTask(diagnostics, null, null, options, null, List.of(source)).call();
    if (passed != expected || (!expected && !diagnostics.toString().contains("[NullAway]"))) {
      throw new AssertionError("NullAway probe did not behave as expected: " + diagnostics);
    }
  }
}
