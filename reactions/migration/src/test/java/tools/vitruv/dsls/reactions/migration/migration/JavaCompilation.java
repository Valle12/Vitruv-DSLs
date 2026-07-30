package tools.vitruv.dsls.reactions.migration.migration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

final class JavaCompilation {
  private JavaCompilation() {}

  static List<String> errorsIn(Path vsumFolder, Path classOutput) {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("compiling the derived Java needs a JDK, not a JRE");
    }

    List<Path> sources;
    try {
      sources = ModelStructure.modelFiles(vsumFolder, ".java");
      Files.createDirectories(classOutput);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read the derived Java of " + vsumFolder, e);
    }

    if (sources.isEmpty()) {
      return List.of();
    }

    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    Locale machine = Locale.getDefault();
    Locale.setDefault(Locale.ENGLISH);
    try (StandardJavaFileManager files =
        compiler.getStandardFileManager(diagnostics, Locale.ENGLISH, null)) {
      compiler
          .getTask(
              null,
              files,
              diagnostics,
              List.of("-d", classOutput.toString(), "-proc:none"),
              null,
              files.getJavaFileObjectsFromPaths(sources))
          .call();
    } catch (IOException e) {
      throw new UncheckedIOException("Could not compile the derived Java of " + vsumFolder, e);
    } finally {
      Locale.setDefault(machine);
    }

    return diagnostics.getDiagnostics().stream()
        .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
        .map(JavaCompilation::describe)
        .toList();
  }

  private static String describe(Diagnostic<? extends JavaFileObject> diagnostic) {
    JavaFileObject source = diagnostic.getSource();
    String name = source == null ? "<none>" : Path.of(source.getName()).getFileName().toString();
    return name + ":" + diagnostic.getLineNumber() + " " + diagnostic.getMessage(Locale.ENGLISH);
  }
}
