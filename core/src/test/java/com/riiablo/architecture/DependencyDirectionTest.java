package com.riiablo.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  @Test
  void legacyGlobalAccessDoesNotGrowInAnyMechanismFile() throws IOException {
    Path sourceRoot = sourceRoot();
    Path baseline = sourceRoot.resolve("../../../../docs/architecture-global-access.tsv").normalize();
    Map<String, Integer> budgets = new HashMap<>();
    for (String line : Files.readAllLines(baseline, StandardCharsets.UTF_8)) {
      if (line.isEmpty() || line.startsWith("#")) continue;
      String[] parts = line.split("\\t");
      budgets.put(parts[0], Integer.parseInt(parts[1]));
    }
    Pattern globals = Pattern.compile(
        "\\bRiiablo\\s*\\.\\s*(?:files|mpqs|gameSeed)\\b|\\bMathUtils\\s*\\.\\s*random\\b|\\bINSTANCE\\b");
    Pattern commentsAndStrings = Pattern.compile(
        "/\\*.*?\\*/|//[^\\n]*|\"(?:\\\\.|[^\"\\\\])*\"", Pattern.DOTALL);
    List<String> violations = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(sourceRoot)) {
      for (Path file : (Iterable<Path>) paths.filter(path -> path.toString().endsWith(".java"))::iterator) {
        String relative = sourceRoot.relativize(file).toString().replace('\\', '/');
        if (!isMechanismPackage(relative)) continue;
        String text = commentsAndStrings.matcher(new String(
            Files.readAllBytes(file), StandardCharsets.UTF_8)).replaceAll(" ");
        Matcher matcher = globals.matcher(text);
        int count = 0;
        while (matcher.find()) count++;
        int budget = budgets.getOrDefault(relative, 0);
        if (count > budget) violations.add(relative + ": " + count + " > " + budget);
      }
    }
    assertTrue(violations.isEmpty(), "legacy global access grew:\n" + String.join("\n", violations));
  }

  @Test
  void targetedGlobalAccessDoesNotGrow() throws IOException {
    Path sourceRoot = sourceRoot();
    Map<String, Integer> budgets = new HashMap<>();
    budgets.put("com/riiablo/engine/server/MissileCollisionSystem.java", 1);
    budgets.put("com/riiablo/attributes/Stat.java", 0);
    budgets.put("com/riiablo/item/Type.java", 1);
    budgets.put("com/riiablo/item/ItemRequirements.java", 0);
    budgets.put("com/riiablo/item/ItemWriter.java", 0);
    budgets.put("com/riiablo/item/VendorPricing.java", 0);
    budgets.put("com/riiablo/map/d2moo/NativeShrineDistributionAudit.java", 0);
    budgets.put("com/riiablo/engine/server/quest/Act5QuestMessageValidator.java", 1);
    budgets.put("com/riiablo/engine/server/skill/DruidSkills.java", 0);

    List<String> violations = new ArrayList<>();
    for (Map.Entry<String, Integer> budget : budgets.entrySet()) {
      Path file = sourceRoot.resolve(budget.getKey());
      int count = countMatches(file, Pattern.compile(
          "Riiablo\\.files|Riiablo\\.mpqs|MathUtils\\.random|\\bINSTANCE\\b"));
      if (count > budget.getValue()) {
        violations.add(budget.getKey() + " has " + count
            + " targeted global accesses (budget " + budget.getValue() + ")");
      }
    }

    Path combatRoot = sourceRoot.resolve("com/riiablo/engine/server/combat");
    try (Stream<Path> paths = Files.walk(combatRoot)) {
      paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
        try {
          int count = countMatches(path, Pattern.compile("\\bINSTANCE\\b"));
          if (count > 0) {
            violations.add(sourceRoot.relativize(path).toString().replace('\\', '/')
                + " has " + count + " combat singleton references");
          }
        } catch (IOException e) {
          throw new AssertionError("cannot inspect " + path, e);
        }
      });
    }

    assertTrue(violations.isEmpty(),
        "targeted global access grew:\n  " + String.join("\n  ", violations));
  }

  private static int countMatches(Path file, Pattern pattern) throws IOException {
    int count = 0;
    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
      Matcher matcher = pattern.matcher(line);
      while (matcher.find()) count++;
    }
    return count;
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
