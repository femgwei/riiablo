package com.riiablo.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Ratchets the dependency direction of the Java core.
 *
 * <p>The intended direction is mechanism -> data/services -> host composition.
 * Server and mechanism packages must not acquire new dependencies on client,
 * screen, or widget packages. A small explicit baseline records the legacy
 * bridges that still need migration; removing a bridge removes its baseline
 * entry, while adding another bridge fails this test.</p>
 */
class DependencyDirectionTest {
  private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([^;]+);\\s*$");
  private static final String[] OUTWARD = {
      "com.riiablo.engine.client",
      "com.riiablo.screen",
      "com.riiablo.widget"
  };

  @Test
  void mechanismPackagesDoNotGainNewOutwardImports() throws IOException {
    Path sourceRoot = sourceRoot();
    List<String> violations = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(sourceRoot)) {
      paths.filter(path -> path.toString().endsWith(".java"))
          .forEach(path -> inspect(path, sourceRoot, violations));
    }

    assertTrue(violations.isEmpty(),
        "new outward dependency edges detected:\n  " + String.join("\n  ", violations));
  }

  private static void inspect(Path path, Path sourceRoot, List<String> violations) {
    String relative = sourceRoot.relativize(path).toString().replace('\\', '/');
    if (!isMechanismPackage(relative)) return;

    try {
      for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
        Matcher matcher = IMPORT.matcher(line);
        if (!matcher.matches()) continue;
        String imported = matcher.group(1).trim();
        for (String outward : OUTWARD) {
          if (imported.startsWith(outward) && !knownLegacyBridge(relative, imported)) {
            violations.add(relative + " -> " + imported);
          }
        }
      }
    } catch (IOException e) {
      throw new AssertionError("cannot inspect " + path, e);
    }
  }

  private static boolean isMechanismPackage(String relative) {
    return relative.startsWith("com/riiablo/engine/server/")
        || relative.startsWith("com/riiablo/attributes/")
        || relative.startsWith("com/riiablo/item/")
        || relative.startsWith("com/riiablo/map/");
  }

  /** Existing bridges are debt, not permission for a new reverse dependency. */
  private static boolean knownLegacyBridge(String relative, String imported) {
    if (relative.equals("com/riiablo/engine/server/CofManager.java")) {
      return imported.startsWith("com.riiablo.engine.client.component.");
    }
    if (relative.equals("com/riiablo/engine/server/VelocityModeChanger.java")) {
      return imported.startsWith("com.riiablo.engine.client.component.");
    }
    if (relative.equals("com/riiablo/engine/server/quest/NativeMercenaryRewardSystem.java")) {
      return imported.startsWith("com.riiablo.engine.client.component.");
    }
    if (relative.equals("com/riiablo/engine/server/component/MenuWrapper.java")) {
      return imported.startsWith("com.riiablo.widget.");
    }
    if (relative.equals("com/riiablo/engine/server/ai/Npc.java")) {
      return imported.startsWith("com.riiablo.engine.client.")
          || imported.startsWith("com.riiablo.screen.")
          || imported.startsWith("com.riiablo.widget.");
    }
    if (relative.equals("com/riiablo/item/ItemLabeler.java")) {
      return imported.startsWith("com.riiablo.widget.");
    }
    if (relative.equals("com/riiablo/map/RenderSystem.java")) {
      return imported.startsWith("com.riiablo.engine.client.");
    }
    return false;
  }

  private static Path sourceRoot() {
    Path working = Paths.get("").toAbsolutePath().normalize();
    Path moduleSource = working.resolve("src/main/java");
    if (Files.isDirectory(moduleSource)) return moduleSource;
    Path repositorySource = working.resolve("core/src/main/java");
    if (Files.isDirectory(repositorySource)) return repositorySource;
    throw new AssertionError("cannot locate core/src/main/java from " + working);
  }
}
