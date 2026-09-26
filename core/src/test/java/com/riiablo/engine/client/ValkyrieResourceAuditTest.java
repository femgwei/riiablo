package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.files.FileHandle;
import com.riiablo.codec.COF;
import com.riiablo.codec.excel.Excel;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.mpq.MPQFileHandleResolver;
import java.io.File;
import java.util.Locale;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Read-only audit for the native Valkyrie COF and DCC presentation chain. */
class ValkyrieResourceAuditTest {
  private static HeadlessApplication application;
  private static MPQFileHandleResolver resolver;
  private static MonStats monstats;
  private static MonStats2 monstats2;

  @BeforeAll
  static void setUp() {
    String home = System.getProperty("d2.110f.home");
    if (home == null || home.isEmpty()) home = System.getenv("D2_110F_HOME");
    if (home == null || home.isEmpty()) home = System.getenv("D2_HOME");
    Assumptions.assumeTrue(home != null && !home.isEmpty());
    Assumptions.assumeTrue(new File(home, "d2data.mpq").isFile());
    application = new HeadlessApplication(new ApplicationAdapter() {});
    resolver = new MPQFileHandleResolver(new FileHandle(home));
    monstats = Excel.load(MonStats.class,
        resolver.resolve("data\\global\\excel\\MonStats.txt"));
    monstats2 = Excel.load(MonStats2.class,
        resolver.resolve("data\\global\\excel\\MonStats2.txt"));
  }

  @AfterAll
  static void tearDown() {
    if (application != null) application.exit();
  }

  @Test
  void reportsValkyrieCofAndLayerResources() {
    for (String summonId : new String[] {"valkyrie", "dopplezon", "plaguepoppy", "spiritwolf",
        "direwolf", "grizzly", "claygolem", "bloodgolem", "firegolem", "irongolem"}) {
      MonStats.Entry row = monstats.get(summonId);
      if (row == null) continue;
      String path = "data\\global\\monsters\\" + row.Code + "\\cof\\"
          + row.Code + "NUHTH.cof";
      if (!resolver.contains(path)) continue;
      COF cof = COF.loadFromFile(resolver.resolve(path));
      StringBuilder layers = new StringBuilder();
      for (int i = 0; i < cof.getNumLayers(); i++) {
        if (i != 0) layers.append(',');
        layers.append(cof.getLayer(i).component);
      }
      System.out.println("[SUMMON_DEFAULT_AUDIT] id=" + row.Id + " code=" + row.Code
          + " componentV=" + java.util.Arrays.toString(monstats2.get(row.MonStatsEx).ComponentV)
          + " cofLayers=" + layers);
    }
    MonStats.Entry valkyrie = monstats.get("valkyrie");
    if (valkyrie == null) {
      for (MonStats.Entry entry : monstats) {
        if (entry != null && entry.Id != null
            && entry.Id.toLowerCase(Locale.ROOT).equals("valkyrie")) {
          valkyrie = entry;
          break;
        }
      }
    }
    assertNotNull(valkyrie);
    MonStats2.Entry visual = monstats2.get(valkyrie.MonStatsEx);
    assertNotNull(visual);
    boolean hasComponentValue = false;
    for (String value : visual.ComponentV) hasComponentValue |= value != null && !value.isEmpty();
    assertTrue(!hasComponentValue, "Valkyrie audit assumes the native empty ComponentV row");
    System.out.println("[VALKYRIE_STATS] id=" + valkyrie.Id + " code=" + valkyrie.Code
        + " row=" + valkyrie.MonStatsEx + " modes=" + java.util.Arrays.toString(visual.mMode)
        + " components=" + java.util.Arrays.toString(visual.ComponentV));

    String[] modes = {"NU", "WL", "A1", "DD", "GH", "RN"};
    int cofCount = 0;
    for (String mode : modes) {
      String path = "data\\global\\monsters\\VK\\cof\\VK" + mode + "HTH.cof";
      boolean present = resolver.contains(path);
      System.out.println("[VALKYRIE_COF] mode=" + mode + " path=" + path
          + " present=" + present);
      if (present) {
        COF cof = COF.loadFromFile(resolver.resolve(path));
        System.out.println("[VALKYRIE_COF] mode=" + mode + " frames="
            + cof.getNumFramesPerDir() + " layers=" + cof.getNumLayers());
        for (int i = 0; i < cof.getNumLayers(); i++) {
          COF.Layer layer = cof.getLayer(i);
          String composite = new String[] {"HD", "TR", "LG", "RA", "LA", "RH", "LH", "SH",
              "S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8"}[layer.component];
          String stem = "data\\global\\monsters\\VK\\" + composite + "\\VK" + composite;
          String suffix = layer.weaponClass.toLowerCase(Locale.ROOT);
          StringBuilder candidates = new StringBuilder();
          for (String candidateMode : new String[] {mode, "NU", "WL"}) {
            String candidate = stem + "LIT" + candidateMode + suffix + ".dcc";
            if (resolver.contains(candidate)) {
              if (candidates.length() != 0) candidates.append(',');
              candidates.append(candidateMode);
            }
          }
          System.out.println("[VALKYRIE_LAYER] mode=" + mode + " component=" + composite
              + " weaponClass=" + layer.weaponClass + " dccModes=" + candidates);
        }
        if ("NU".equals(mode)) {
          assertEquals(com.riiablo.codec.COF.Component.TR, cof.getLayer(0).component,
              "Valkyrie body is the standalone TR layer");
          assertTrue(resolver.contains(
              "data\\global\\monsters\\VK\\TR\\VKTRLITNUhth.dcc"),
              "the static NU body DCC must be available for mode fallback");
        }
        cofCount++;
      }
    }
    assertTrue(cofCount > 0, "Valkyrie COF resources are missing from the configured MPQ");
  }
}
