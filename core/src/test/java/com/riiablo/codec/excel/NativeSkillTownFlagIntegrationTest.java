package com.riiablo.codec.excel;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.files.FileHandle;
import com.riiablo.mpq.MPQFileHandleResolver;
import java.io.File;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Verifies native 1.10f town restrictions used by the client/server skill gate. */
class NativeSkillTownFlagIntegrationTest {
  private static HeadlessApplication application;
  private static NativeSkills skills;

  @BeforeAll
  static void setUp() {
    String home = System.getProperty("d2.110f.home");
    if (home == null || home.isEmpty()) home = System.getenv("D2_110F_HOME");
    Assumptions.assumeTrue(home != null && new File(home, "d2data.mpq").isFile());
    application = new HeadlessApplication(new ApplicationAdapter() {});
    MPQFileHandleResolver resolver = new MPQFileHandleResolver(new FileHandle(home));
    skills = NativeSkills.load(resolver.resolve("data\\global\\excel\\Skills.txt"));
  }

  @AfterAll
  static void tearDown() { if (application != null) application.exit(); }

  @Test
  void nativeWeaponAndAttackSkillsAreDisabledInTown() {
    for (String name : new String[] {"Attack", "Throw", "Left Hand Throw", "Fire Bolt",
        "Scroll of Townportal", "Book of Townportal", "Teleport"}) {
      NativeSkills.Entry entry = skills.get(name);
      assertNotNull(entry, "Missing native skill row " + name);
      assertFalse(entry.bool("InTown"), name + " must be disabled in town in 1.10f");
    }
  }
}
