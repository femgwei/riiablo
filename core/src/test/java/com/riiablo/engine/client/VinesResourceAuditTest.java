package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.files.FileHandle;
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

/**
 * Read-only audit for the Druid Plague Poppy presentation resources.
 *
 * <p>Run with {@code -Dd2.110f.home=G:\\BaiduNetdiskDownload\\Diablo II}
 * or set {@code D2_110F_HOME}.  Missing optional layers are reported rather
 * than treated as a test failure; the client is expected to skip those layers
 * after the COF resource guard was added.</p>
 */
class VinesResourceAuditTest {
  private static final String EXCEL = "data\\global\\excel\\";
  private static final String[] COMPONENT_NAMES = {
      "HD", "TR", "LG", "RA", "LA", "RH", "LH", "SH",
      "S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8"
  };
  private static final String[] MONSTER_MODES = {
      "DT", "NU", "WL", "GH", "A1", "A2", "BL", "SC",
      "S1", "S2", "S3", "S4", "DD", "GH", "XX", "RN"
  };

  private static HeadlessApplication application;
  private static MPQFileHandleResolver resolver;
  private static MonStats monstats;
  private static MonStats2 monstats2;

  @BeforeAll
  static void setUp() {
    String home = System.getProperty("d2.110f.home");
    if (home == null || home.isEmpty()) home = System.getenv("D2_110F_HOME");
    if (home == null || home.isEmpty()) home = System.getenv("D2_HOME");
    Assumptions.assumeTrue(home != null && !home.isEmpty(),
        "Set -Dd2.110f.home or D2_110F_HOME to run the MPQ resource audit");
    Assumptions.assumeTrue(new File(home, "d2data.mpq").isFile(),
        "Not a Diablo II installation: " + home);

    application = new HeadlessApplication(new ApplicationAdapter() {});
    resolver = new MPQFileHandleResolver(new FileHandle(home));
    monstats = Excel.load(MonStats.class, resolver.resolve(EXCEL + "MonStats.txt"));
    monstats2 = Excel.load(MonStats2.class, resolver.resolve(EXCEL + "MonStats2.txt"));
  }

  @AfterAll
  static void tearDown() {
    if (application != null) application.exit();
  }

  @Test
  void reportsPlaguePoppyDccAndDc6Layers() {
    MonStats.Entry vine = find(monstats, "plaguepoppy");
    assertNotNull(vine, "MonStats row plaguepoppy is missing");
    MonStats2.Entry visual = monstats2.get(vine.MonStatsEx);
    assertNotNull(visual, "MonStats2 row is missing: " + vine.MonStatsEx);

    String token = vine.Code;
    int existing = 0;
    int missing = 0;
    for (int component = 0; component < COMPONENT_NAMES.length; component++) {
      String componentValue = visual.ComponentV[component];
      if (componentValue == null || componentValue.isEmpty()) continue;
      for (int mode = 0; mode < MONSTER_MODES.length; mode++) {
        if (!visual.mMode[mode]) continue;
        String stem = "data\\global\\monsters\\" + token + "\\"
            + COMPONENT_NAMES[component] + "\\" + token + COMPONENT_NAMES[component]
            + componentValue + MONSTER_MODES[mode] + "hth";
        boolean dcc = resolver.contains(stem + ".dcc");
        boolean dc6 = resolver.contains(stem + ".dc6");
        String status = dcc ? "DCC" : dc6 ? "DC6" : "MISSING";
        System.out.println("[VINES_ASSET] monster=" + vine.Id + " component="
            + COMPONENT_NAMES[component] + " mode=" + MONSTER_MODES[mode]
            + " path=" + stem + " status=" + status);
        if (dcc || dc6) existing++; else missing++;
      }
    }

    // This is the exact path seen in the pre-fix crash log. Keep it explicit
    // so a local run immediately answers whether the vanilla MPQ contains it.
    String crashPath = "data\\global\\monsters\\k9\\TR\\k9TRLITWLhth.dc6";
    boolean crashDcc = resolver.contains(crashPath.substring(0, crashPath.length() - 4) + ".dcc");
    boolean crashDc6 = resolver.contains(crashPath);
    System.out.println("[VINES_ASSET_CRASH_PATH] path=" + crashPath
        + " dcc=" + crashDcc + " dc6=" + crashDc6);

    assertTrue(existing > 0,
        "Plague Poppy has no readable DCC/DC6 layer in the configured MPQ");
    System.out.println("[VINES_ASSET_SUMMARY] token=" + token
        + " existing=" + existing + " missing=" + missing);
  }

  private static <T extends Excel.Entry> T find(Excel<T> table, String id) {
    T exact = table.get(id);
    if (exact != null) return exact;
    for (T entry : table) {
      if (entry != null && entry.toString() != null
          && entry.toString().toLowerCase(Locale.ROOT).equals(id)) return entry;
    }
    return null;
  }
}
