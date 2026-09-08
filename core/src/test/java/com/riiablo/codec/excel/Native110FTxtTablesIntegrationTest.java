package com.riiablo.codec.excel;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.files.FileHandle;
import com.riiablo.mpq.MPQFileHandleResolver;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Opt-in verification against an unmodified Diablo II 1.10f MPQ installation. */
class Native110FTxtTablesIntegrationTest {
  private static final String EXCEL = "data\\global\\excel\\";
  private static HeadlessApplication application;
  private static MPQFileHandleResolver resolver;

  @BeforeAll
  static void setUp() {
    String home = System.getProperty("d2.110f.home");
    if (home == null || home.isEmpty()) home = System.getenv("D2_110F_HOME");
    Assumptions.assumeTrue(home != null && !home.isEmpty(),
        "Set D2_110F_HOME or -Dd2.110f.home to run the 1.10f MPQ gate");
    Assumptions.assumeTrue(new File(home, "Patch_D2.mpq").isFile(),
        "The configured directory is not a complete 1.10f installation: " + home);
    application = new HeadlessApplication(new ApplicationAdapter() {});
    resolver = new MPQFileHandleResolver(new FileHandle(home));
  }

  @AfterAll
  static void tearDown() {
    if (application != null) application.exit();
  }

  @Test
  void manifestsAndRequiredHeadersMatchNative110f() throws Exception {
    Map<String, String[]> requiredHeaders = new LinkedHashMap<>();
    requiredHeaders.put("ItemStatCost.txt",
        new String[] {"Stat", "ID", "Send Bits", "Signed", "Op", "Op Param"});
    requiredHeaders.put("States.txt",
        new String[] {"state", "group", "plrstaydeath", "monstaydeath", "bossstaydeath",
            "noclear", "stat", "setfunc", "remfunc"});
    requiredHeaders.put("Skills.txt",
        new String[] {"skill", "Id", "srvstfunc", "srvdofunc", "aurastat1", "aurastatcalc1"});
    requiredHeaders.put("Missiles.txt",
        new String[] {"Missile", "Id", "pSrvDoFunc", "pSrvHitFunc", "Collision", "Pierce"});
    requiredHeaders.put("MonStats.txt",
        new String[] {"Id", "hcIdx", "MonType", "AI", "TreasureClass1"});

    Map<String, String> nativeManifests = new LinkedHashMap<>();
    nativeManifests.put("ItemStatCost.txt", "rows=359,headerColumns=53,valueColumns=53,"
        + "rawSha256=ee7fa82b48e0c4f27a566334b2da13cf1c83f5c4c0439552084a231cb5938375,"
        + "headerSha256=862d7da36a3a600e27cc0beb9219431f4fb816ba4ebf0771a723f2d07b77821d,"
        + "semanticSha256=1893803e442f816716f37a76ead3430fbe051cfa182b082e02d40286c195d7ba");
    nativeManifests.put("States.txt", "rows=184,headerColumns=72,valueColumns=72,"
        + "rawSha256=fd33b3f4d097c6ef89594384926e331fbfccb8f7d81a2f66c200b6bed94a7b5a,"
        + "headerSha256=9c67c0ca018215de6a1390486aa1ea067a06a6dd296fa198bcbf2db709cd9684,"
        + "semanticSha256=ae1fa5743dd99db48d181d57635a2de7147bdd1842b15f0158de9172c0fc78f0");
    nativeManifests.put("Skills.txt", "rows=357,headerColumns=256,valueColumns=256,"
        + "rawSha256=a82097efa82f585ee1189ba6366110c3dfd774154b329774e1cc831f4ec9d0c5,"
        + "headerSha256=08743ebd7cc484022527b2babc0f1745b705a742d3641317fbf9ea7d4ec9cc6f,"
        + "semanticSha256=2af6df651a6823b949f9ee1b77691384421a5ae5f0e7b4d7d978ef6264e18824");
    nativeManifests.put("Missiles.txt", "rows=684,headerColumns=171,valueColumns=171,"
        + "rawSha256=f903fd04f61aa676585d54bc2f98c3e5a42328188318ce15b9671e85609ec0ae,"
        + "headerSha256=1dfe5d1826150ae6cc8130665d7637a40409cfd2f7dcf461ffdad1dca0006e31,"
        + "semanticSha256=05491aedea8d6b954742d75159de909a4e562ea80d58f11e3a6dd3aa4351be4f");
    nativeManifests.put("MonStats.txt", "rows=705,headerColumns=255,valueColumns=255,"
        + "rawSha256=f905c19222ea93ea36815ca800f26f16219d9b3f8d85c5f7cab19a66ab982839,"
        + "headerSha256=993ac38a480003febf262ef291adbed452d02a766f826edd463d5be69083005b,"
        + "semanticSha256=52b168679f523804e689efab75cc68c5e7bd402a39fcfae7e5acb78c43f65a5d");

    Map<String, LosslessTxtTable> nativeTables = new LinkedHashMap<>();
    for (Map.Entry<String, String[]> expected : requiredHeaders.entrySet()) {
      FileHandle handle = resolver.resolve(EXCEL + expected.getKey());
      assertNotNull(handle, "Missing native table " + expected.getKey());
      byte[] bytes = handle.readBytes();
      LosslessTxtTable table = LosslessTxtTable.parse(bytes);
      nativeTables.put(expected.getKey(), table);
      for (String header : expected.getValue()) {
        assertTrue(table.columnIndex(header) >= 0,
            expected.getKey() + " is missing 1.10f column " + header);
      }
      TxtTableManifest manifest = TxtTableManifest.create(bytes);
      assertEquals(nativeManifests.get(expected.getKey()), manifest.toString(),
          expected.getKey() + " does not match the fixed native 1.10f payload");
    }

    Map<String, NativeTxtSchema> schemas = new LinkedHashMap<>();
    schemas.put("ItemStatCost.txt", NativeItemStatCost.SCHEMA);
    schemas.put("States.txt", States.SCHEMA);
    schemas.put("Skills.txt", NativeSkills.SCHEMA);
    schemas.put("Missiles.txt", NativeMissiles.SCHEMA);
    schemas.put("MonStats.txt", NativeMonStats.SCHEMA);
    for (NativeTxtProjectionReport.TableReport report
        : NativeTxtProjectionReport.createAll(schemas, nativeTables)) {
      assertTrue(report.isClean(), report::toString);
    }

    FileHandle statesHandle = resolver.resolve(EXCEL + "States.txt");
    States states = States.parse(statesHandle.readBytes());
    assertEquals(184, states.size());
    assertEquals("none", states.get(0).state);
    assertEquals("freeze", states.get(1).state);
    assertEquals("poison", states.get(2).state);
    assertEquals(1, states.get("FREEZE").id);
    assertEquals(-1, states.source().columnIndex("canstack"),
        "1.14d-only state columns must not enter the 1.10f schema");
    assertEquals(-1, states.source().columnIndex("sunder-res-reduce"));
    int playerStayDeath = 0;
    int monsterStayDeath = 0;
    int bossStayDeath = 0;
    int noClear = 0;
    for (States.Entry state : states) {
      if (state.playerStayDeath) playerStayDeath++;
      if (state.monsterStayDeath) monsterStayDeath++;
      if (state.bossStayDeath) bossStayDeath++;
      if (state.noClear) noClear++;
    }
    assertTrue(playerStayDeath > 0, "1.10f player stay-death mask must not be empty");
    assertTrue(monsterStayDeath > 0, "1.10f monster stay-death mask must not be empty");
    assertTrue(bossStayDeath > 0, "1.10f boss stay-death mask must not be empty");
    assertTrue(noClear > 0, "1.10f no-clear mask must not be empty");

    NativeItemStatCost itemStats = NativeItemStatCost.parse(
        resolver.resolve(EXCEL + "ItemStatCost.txt").readBytes());
    assertEquals(359, itemStats.size());
    boolean hasDamageRelated = false;
    for (int id = 0; id < itemStats.size(); id++) {
      assertEquals(id, itemStats.get(id).id);
      hasDamageRelated |= itemStats.get(id).damageRelated;
    }
    assertTrue(hasDamageRelated, "1.10f ItemStatCost must retain damageRelated bit fields");

    NativeSkills skills = NativeSkills.parse(resolver.resolve(EXCEL + "Skills.txt").readBytes());
    assertEquals(357, skills.size());
    assertTrue(skills.schemaIssues().isEmpty(), () -> "Skills.txt schema mismatch: "
        + skills.schemaIssues().subList(0, Math.min(10, skills.schemaIssues().size())));
    for (int id = 0; id < skills.size(); id++) assertEquals(id, skills.get(id).id);

    NativeMissiles missiles = NativeMissiles.parse(
        resolver.resolve(EXCEL + "Missiles.txt").readBytes());
    assertEquals(684, missiles.size());
    assertTrue(missiles.schemaIssues().isEmpty(), () -> "Missiles.txt schema mismatch: "
        + missiles.schemaIssues().subList(0, Math.min(10, missiles.schemaIssues().size())));
    for (int id = 0; id < missiles.size(); id++) assertEquals(id, missiles.get(id).id);

    NativeMonStats monsters = NativeMonStats.parse(
        resolver.resolve(EXCEL + "MonStats.txt").readBytes());
    assertEquals(705, monsters.source().rowCount());
    assertEquals(704, monsters.size(),
        "MonStats contains one Expansion control row which is not a native monster record");
    assertTrue(monsters.schemaIssues().isEmpty(), () -> "MonStats.txt schema mismatch: "
        + monsters.schemaIssues().subList(0, Math.min(10, monsters.schemaIssues().size())));
    for (int id = 0; id < monsters.size(); id++) assertEquals(id, monsters.get(id).id);
  }
}
