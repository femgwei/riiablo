package com.riiablo.codec.excel;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.files.FileHandle;
import com.riiablo.mpq.MPQFileHandleResolver;
import java.io.File;
import java.util.Locale;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Read-only 1.10f MPQ probe for Barbarian war-cry sound indirection.
 * Run with -Dd2.110f.home=... or D2_110F_HOME=... .
 */
class NativeBarbarianSoundProbeTest {
  private static HeadlessApplication application;
  private static MPQFileHandleResolver resolver;

  @BeforeAll
  static void setUp() {
    String home = System.getProperty("d2.110f.home");
    if (home == null || home.isEmpty()) home = System.getenv("D2_110F_HOME");
    Assumptions.assumeTrue(home != null && !home.isEmpty());
    Assumptions.assumeTrue(new File(home, "d2data.mpq").isFile());
    application = new HeadlessApplication(new ApplicationAdapter() {});
    resolver = new MPQFileHandleResolver(new FileHandle(home));
  }

  @AfterAll
  static void tearDown() {
    if (application != null) application.exit();
  }

  @Test
  void printNativeWarCrySoundRows() {
    Skills skills = Excel.load(Skills.class,
        resolver.resolve("data\\global\\excel\\Skills.txt"));
    Sounds sounds = Excel.load(Sounds.class,
        resolver.resolve("data\\global\\excel\\Sounds.txt"));
    assertNotNull(skills);
    assertNotNull(sounds);
    for (Skills.Entry skill : skills) {
      if (skill.skill == null) continue;
      String name = skill.skill.toLowerCase(Locale.ROOT);
      if (!(name.equals("howl") || name.equals("taunt") || name.equals("shout")
          || name.equals("battle cry") || name.equals("battle orders")
          || name.equals("war cry") || name.equals("battle command"))) continue;
      System.out.println("[NATIVE_BARBARIAN_SOUND] skill=" + skill.skill
          + " id=" + skill.Id + " stsound=" + skill.stsound
          + " dosound=" + skill.dosound + " stsoundclass=" + skill.stsoundclass
          + " cltstfunc=" + skill.cltstfunc + " cltdofunc=" + skill.cltdofunc);
      printSound(sounds, skill.stsound);
      printSound(sounds, skill.dosound);
    }
  }

  private static void printSound(Sounds sounds, String key) {
    if (key == null || key.isEmpty()) return;
    Sounds.Entry sound = sounds.get(key);
    System.out.println("[NATIVE_SOUND_ROW] key=" + key + " found=" + (sound != null)
        + (sound == null ? "" : " index=" + sound.Index + " file=" + sound.FileName));
  }
}
