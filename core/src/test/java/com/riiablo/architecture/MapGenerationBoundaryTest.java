package com.riiablo.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Generation dependencies have a zero budget; adapter debt cannot enlarge it. */
class MapGenerationBoundaryTest {
  private static final Set<String> ADAPTERS = new HashSet<>(Arrays.asList(
      "map/MapManager.java", "map/Box2DPhysics.java", "map/RenderSystem.java"));
  private static final Pattern COMMENTS_AND_STRINGS = Pattern.compile(
      "/\\*.*?\\*/|//[^\\n]*|\"(?:\\\\.|[^\"\\\\])*+\"", Pattern.DOTALL);
  private static final Pattern RUNTIME = Pattern.compile(
      "com\\.artemis\\.|com\\.badlogic\\.gdx\\.net\\.Socket"
          + "|com\\.riiablo\\.engine\\.server\\.component\\."
          + "|\\b\\w*EntityFactory\\b|\\bNativeDataTables\\b"
          + "|\\bfactory\\s*\\.\\s*create(?:Monster|Warp|Object|StaticObjectByClassId)\\s*\\(");

  @Test void generationAndDataAccessHaveNoEcsFactoryOrTransportDependencies() throws IOException {
    Path root = sourceRoot().resolve("com/riiablo");
    List<String> violations = new ArrayList<>();
    for (String directory : Arrays.asList("map", "drlg")) {
      try (Stream<Path> paths = Files.walk(root.resolve(directory))) {
        for (Path file : (Iterable<Path>) paths.filter(p -> p.toString().endsWith(".java"))::iterator) {
          String name = root.relativize(file).toString().replace('\\', '/');
          if (ADAPTERS.contains(name)) continue;
          String code = COMMENTS_AND_STRINGS.matcher(new String(
              Files.readAllBytes(file), StandardCharsets.UTF_8)).replaceAll(" ");
          if (RUNTIME.matcher(code).find()) violations.add(name);
        }
      }
    }
    String data = new String(Files.readAllBytes(root.resolve("codec/excel/NativeTableValues.java")),
        StandardCharsets.UTF_8);
    if (data.contains("com.riiablo.engine")) violations.add("codec/excel/NativeTableValues.java");
    assertTrue(violations.isEmpty(), "generation must stay independent of runtime: " + violations);
  }

  private static Path sourceRoot() {
    Path working = Paths.get("").toAbsolutePath().normalize();
    Path source = working.resolve("src/main/java");
    if (Files.isDirectory(source)) return source;
    return working.resolve("core/src/main/java");
  }
}
