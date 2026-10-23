package com.riiablo.codec.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TxtDataPackManifestTest {
  private static TxtTableSource source(String name, String value) throws Exception {
    return TxtTableSource.fromBytes(name, TxtTableSource.Version.DIABLO_II_110F,
        ("Id\tName\n1\t" + value + "\n").getBytes(StandardCharsets.ISO_8859_1));
  }

  @Test void digestIsStableAcrossInsertionOrderAndPaths() throws Exception {
    TxtTableSource skills = source("Skills.txt", "skill");
    TxtTableSource states = source("States.txt", "state");
    TxtDataPackManifest first = TxtDataPackManifest.builder("d2-110f",
        TxtTableSource.Version.DIABLO_II_110F)
        .require("Skills.txt").require("States.txt")
        .table("Skills.txt", skills).table("States.txt", states).build();
    TxtDataPackManifest second = TxtDataPackManifest.builder("d2-110f",
        TxtTableSource.Version.DIABLO_II_110F)
        .require("States.txt").require("Skills.txt")
        .table("States.txt", source("other\\States.txt", "state"))
        .table("Skills.txt", source("other\\Skills.txt", "skill")).build();
    assertEquals(first.sha256, second.sha256);
  }

  @Test void digestChangesWhenOneTableChanges() throws Exception {
    TxtDataPackManifest first = TxtDataPackManifest.builder("d2-110f",
        TxtTableSource.Version.DIABLO_II_110F)
        .table("Skills.txt", source("Skills.txt", "skill")).build();
    TxtDataPackManifest changed = TxtDataPackManifest.builder("d2-110f",
        TxtTableSource.Version.DIABLO_II_110F)
        .table("Skills.txt", source("Skills.txt", "changed")).build();
    assertNotEquals(first.sha256, changed.sha256);
  }

  @Test void requiredTablesAndVersionsAreRejectedBeforeRulesRun() throws Exception {
    assertThrows(MissingTxtDataException.class, () -> TxtDataPackManifest.builder("d2-110f",
        TxtTableSource.Version.DIABLO_II_110F).require("Skills.txt").build());
    TxtTableSource unspecified = TxtTableSource.fromBytes("Skills.txt",
        TxtTableSource.Version.UNSPECIFIED, "Id\n1\n".getBytes(StandardCharsets.ISO_8859_1));
    assertThrows(IllegalArgumentException.class, () -> TxtDataPackManifest.builder("d2-110f",
        TxtTableSource.Version.DIABLO_II_110F).table("Skills.txt", unspecified));
  }

  @Test void strictProjectionRejectsMissingTableAndSchemaColumn() throws Exception {
    assertThrows(MissingTxtDataException.class, () -> NativeTxtProjectionReport.createAllStrict(
        java.util.Collections.singletonMap("Skills.txt", NativeSkills.SCHEMA),
        java.util.Collections.<String, LosslessTxtTable>emptyMap()));
    LosslessTxtTable incomplete = LosslessTxtTable.parse("skill\nFire Bolt\n"
        .getBytes(StandardCharsets.ISO_8859_1));
    assertThrows(IllegalArgumentException.class, () -> NativeTxtProjectionReport.createAllStrict(
        java.util.Collections.singletonMap("Skills.txt", NativeSkills.SCHEMA),
        java.util.Collections.singletonMap("Skills.txt", incomplete)));
  }
}
