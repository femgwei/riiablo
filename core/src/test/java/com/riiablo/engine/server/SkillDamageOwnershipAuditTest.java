package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.CharacterClass;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Cross-class completeness gate for the seven DMG-03B damage-owner audits. */
class SkillDamageOwnershipAuditTest extends RiiabloTest {
  private static final String HEADER = String.join("\t",
      "skill_id", "skill_name", "damage_role", "damage_owner", "owner_record",
      "native_path", "d2moo_reference", "riiablo_reference", "test_reference",
      "audit_status", "riiablo_status", "notes");
  private static final Set<String> ALLOWED_STATUSES = Set.of(
      "IMPLEMENTED_TESTED",
      "IMPLEMENTED_TEST_GAP",
      "OUT_OF_SCOPE_NO_DAMAGE",
      "RIIABLO_GAP");
  private static final Pattern CLASS_METHOD = Pattern.compile(
      "^([A-Za-z_][A-Za-z0-9_]*Test)\\.([A-Za-z_][A-Za-z0-9_]*)$");
  private static final Pattern CLASS_REFERENCE = Pattern.compile(
      "^([A-Za-z_][A-Za-z0-9_]*Test)(?:\\s.*)?$");
  private static final Pattern METHOD_REFERENCE = Pattern.compile(
      "^[A-Za-z_][A-Za-z0-9_]*$");
  private static final AuditSource[] SOURCES = {
      new AuditSource("amazon", CharacterClass.AMAZON),
      new AuditSource("sorceress", CharacterClass.SORCERESS),
      new AuditSource("necromancer", CharacterClass.NECROMANCER),
      new AuditSource("paladin", CharacterClass.PALADIN),
      new AuditSource("barbarian", CharacterClass.BARBARIAN),
      new AuditSource("druid", CharacterClass.DRUID),
      new AuditSource("assassin", CharacterClass.ASSASSIN),
  };

  @Test
  void sevenClassAuditsCoverExactly210SkillsWithOneCanonicalTaxonomy() throws IOException {
    Set<Integer> globalIds = new HashSet<>();
    Map<String, Integer> statusCounts = new HashMap<>();
    int noOutgoingDamage = 0;

    for (AuditSource source : SOURCES) {
      List<AuditRow> rows = read(source);
      assertEquals(30, rows.size(), source.slug + " skill count");
      Set<Integer> classIds = new HashSet<>();

      for (AuditRow row : rows) {
        int id = row.skillId();
        assertTrue(classIds.add(id), source.slug + " duplicate skill id " + id);
        assertTrue(globalIds.add(id), "cross-class duplicate skill id " + id);
        assertTrue(id >= source.characterClass.firstSpell && id < source.characterClass.lastSpell,
            source.slug + " contains out-of-range skill id " + id);

        Skills.Entry skill = Riiablo.files.skills.get(id);
        assertNotNull(skill, "missing 1.10f Skills.txt row " + id);
        assertEquals(skill.skill, row.value("skill_name"), "skill name " + id);
        assertEquals("OWNERSHIP_CONFIRMED", row.value("audit_status"),
            "owner decision " + id);
        for (String column : new String[] {
            "damage_role", "damage_owner", "owner_record", "native_path",
            "d2moo_reference", "riiablo_reference", "test_reference", "notes"}) {
          assertFalse(row.value(column).isEmpty(), "missing " + column + " for skill " + id);
        }
        assertTrue(row.value("d2moo_reference").contains(":"),
            "D2MOO reference must identify a source location for skill " + id);

        String status = row.value("riiablo_status");
        assertTrue(ALLOWED_STATUSES.contains(status), "unknown riiablo status " + status);
        statusCounts.merge(status, 1, Integer::sum);

        boolean hasOutgoingDamage = !"no_outgoing_damage".equals(row.value("damage_role"));
        if (hasOutgoingDamage) {
          assertFalse("n/a".equals(row.value("damage_owner")),
              "damage-affecting skill needs an owner " + id);
        } else {
          noOutgoingDamage++;
          assertEquals("n/a", row.value("damage_owner"), "non-damage owner " + id);
          assertTrue(row.value("notes").contains("N/A"), "non-damage reason " + id);
        }
        if ("OUT_OF_SCOPE_NO_DAMAGE".equals(status)) {
          assertFalse(hasOutgoingDamage, "out-of-scope row still owns outgoing damage " + id);
        } else if ("RIIABLO_GAP".equals(status)) {
          assertTrue(hasGapMarker(row.value("notes")), "missing implementation-gap reason " + id);
        } else if ("IMPLEMENTED_TEST_GAP".equals(status)) {
          assertTrue(hasTestMarker(row.value("notes")), "missing focused-test-gap reason " + id);
        }
      }

      for (int id = source.characterClass.firstSpell;
           id < source.characterClass.lastSpell; id++) {
        assertTrue(classIds.contains(id), source.slug + " missing skill id " + id);
      }
    }

    assertEquals(210, globalIds.size());
    assertEquals(40, noOutgoingDamage);
    assertEquals(121, count(statusCounts, "IMPLEMENTED_TESTED"));
    assertEquals(27, count(statusCounts, "IMPLEMENTED_TEST_GAP"));
    assertEquals(29, count(statusCounts, "OUT_OF_SCOPE_NO_DAMAGE"));
    assertEquals(33, count(statusCounts, "RIIABLO_GAP"));
  }

  @Test
  void everyAutomatedTestReferenceResolvesToAnExistingClassOrMethod() throws IOException {
    Map<String, String> testSources = loadTestSources();

    for (AuditSource source : SOURCES) {
      for (AuditRow row : read(source)) {
        String currentClass = null;
        for (String rawReference : row.value("test_reference").split(";")) {
          String reference = rawReference.trim();
          if (reference.isEmpty() || reference.startsWith("headless")) continue;

          Matcher classMethod = CLASS_METHOD.matcher(reference);
          if (classMethod.matches()) {
            currentClass = classMethod.group(1);
            assertTestClass(testSources, currentClass, row.skillId());
            assertTestMethod(testSources.get(currentClass), currentClass, classMethod.group(2),
                row.skillId());
            continue;
          }

          Matcher classReference = CLASS_REFERENCE.matcher(reference);
          if (classReference.matches()) {
            currentClass = classReference.group(1);
            assertTestClass(testSources, currentClass, row.skillId());
            continue;
          }

          assertTrue(METHOD_REFERENCE.matcher(reference).matches(),
              "unrecognized test reference for skill " + row.skillId() + ": " + reference);
          assertNotNull(currentClass,
              "method-only test reference lacks a class for skill " + row.skillId());
          assertTestMethod(testSources.get(currentClass), currentClass, reference, row.skillId());
        }
      }
    }
  }

  private static List<AuditRow> read(AuditSource source) throws IOException {
    Path file = resolveDocs().resolve("skill-damage-" + source.slug + "-ownership.tsv");
    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    assertEquals(31, lines.size(), source.slug + " header plus 30 skills");
    assertEquals(HEADER, lines.get(0), source.slug + " canonical header");

    String[] header = lines.get(0).split("\\t", -1);
    Map<String, Integer> columns = new HashMap<>();
    for (int i = 0; i < header.length; i++) columns.put(header[i], i);
    List<AuditRow> rows = new ArrayList<>();
    for (int i = 1; i < lines.size(); i++) {
      String[] values = lines.get(i).split("\\t", -1);
      assertEquals(header.length, values.length,
          source.slug + " column count at TSV row " + (i + 1));
      rows.add(new AuditRow(columns, values));
    }
    return rows;
  }

  private static Map<String, String> loadTestSources() throws IOException {
    Path root = resolveTestRoot();
    List<Path> files;
    try (Stream<Path> paths = Files.walk(root)) {
      files = paths.filter(path -> path.toString().endsWith(".java"))
          .collect(Collectors.toList());
    }

    Map<String, String> sources = new HashMap<>();
    for (Path file : files) {
      String name = file.getFileName().toString();
      sources.put(name.substring(0, name.length() - ".java".length()),
          new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }
    return sources;
  }

  private static void assertTestClass(Map<String, String> sources, String className, int skillId) {
    assertTrue(sources.containsKey(className),
        "missing test class " + className + " for skill " + skillId);
  }

  private static void assertTestMethod(
      String source, String className, String methodName, int skillId) {
    Pattern method = Pattern.compile("\\b" + Pattern.quote(methodName) + "\\s*\\(");
    assertTrue(method.matcher(source).find(),
        "missing test method " + className + "." + methodName + " for skill " + skillId);
  }

  private static boolean hasGapMarker(String notes) {
    String normalized = notes.toLowerCase();
    return normalized.contains("gap")
        || normalized.contains("missing")
        || normalized.contains("omit")
        || normalized.contains("unsupported")
        || normalized.contains("incorrect")
        || normalized.contains("wrong")
        || normalized.contains("fallback")
        || normalized.contains("not ")
        || normalized.contains("缺口");
  }

  private static boolean hasTestMarker(String notes) {
    String normalized = notes.toLowerCase();
    return normalized.contains("test")
        || normalized.contains("assert")
        || normalized.contains("cover")
        || normalized.contains("测试");
  }

  private static int count(Map<String, Integer> counts, String status) {
    return counts.getOrDefault(status, 0);
  }

  private static Path resolveDocs() {
    Path cwd = Paths.get("").toAbsolutePath();
    Path direct = cwd.resolve("docs");
    if (Files.isDirectory(direct)) return direct;
    Path parent = cwd.resolve("..").resolve("docs").normalize();
    assertTrue(Files.isDirectory(parent), "missing ownership audit directory: " + direct);
    return parent;
  }

  private static Path resolveTestRoot() {
    Path cwd = Paths.get("").toAbsolutePath();
    Path repositoryRoot = cwd.resolve("core").resolve("src/test/java");
    if (Files.isDirectory(repositoryRoot)) return repositoryRoot;
    Path moduleRoot = cwd.resolve("src/test/java");
    assertTrue(Files.isDirectory(moduleRoot), "missing test source directory: " + repositoryRoot);
    return moduleRoot;
  }

  private static final class AuditSource {
    final String slug;
    final CharacterClass characterClass;

    AuditSource(String slug, CharacterClass characterClass) {
      this.slug = slug;
      this.characterClass = characterClass;
    }
  }

  private static final class AuditRow {
    final Map<String, Integer> columns;
    final String[] values;

    AuditRow(Map<String, Integer> columns, String[] values) {
      this.columns = columns;
      this.values = values;
    }

    int skillId() {
      return Integer.parseInt(value("skill_id"));
    }

    String value(String name) {
      return values[columns.get(name)].trim();
    }
  }
}
