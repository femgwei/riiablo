package com.riiablo.map.d2moo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.d2moo.common.drlg.D2DrlgCoord;
import com.d2moo.common.drlg.D2DrlgLevel;
import com.d2moo.common.drlg.D2DrlgGridStrc;
import com.d2moo.common.drlg.D2DrlgOutdoorInfoStrc;
import com.d2moo.common.drlg.D2DrlgOutdoorPackedGrid2InfoStrc;
import com.d2moo.common.drlg.D2DrlgOutdoorRoomStrc;
import com.d2moo.common.drlg.D2DrlgPresetRoomStrc;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.d2moo.common.drlg.D2DrlgStrc;
import com.d2moo.common.drlg.D2DrlgVertexStrc;
import com.d2moo.common.drlg.D2LevelIds;
import com.d2moo.common.drlg.D2LvlPrestIds;
import com.d2moo.common.drlg.D2PresetUnit;
import com.d2moo.common.drlg.D2UnitTypes;
import com.d2moo.common.drlg.DrlgDrlg;
import com.d2moo.common.drlg.DrlgExport;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.COF;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.Objects;
import com.riiablo.drlg.TileGrid;
import com.riiablo.map.Act1MapBuilderD2MOD;
import com.riiablo.map.Map;

/** Fixed-seed DRLG smoke test and diagnostic report for the Act1 bridge. */
public class Act1D2MOOLayoutBridgeTest extends RiiabloTest {
  private static final int DEFAULT_DIFFICULTY = 0;
  /** Reproduction seed from the town-exit black-map game log. */
  private static final String DEFAULT_SEED = "0x171A6100";

  @Test
  public void rogueEncampmentWaypointDefinitionIsRenderable() {
    Objects.Entry waypoint = Riiablo.files.objects.get(119);
    assertNotNull(waypoint, "Rogue Encampment waypoint object 119 is missing");
    assertTrue(waypoint.Draw, "Rogue Encampment waypoint is marked non-drawable");
    assertTrue(waypoint.Mode[com.riiablo.engine.Engine.Object.MODE_NU],
        "Rogue Encampment waypoint NU mode is disabled");
    assertTrue(waypoint.Mode[com.riiablo.engine.Engine.Object.MODE_ON],
        "Rogue Encampment waypoint ON mode is disabled");
    assertTrue(waypoint.FrameCnt[com.riiablo.engine.Engine.Object.MODE_NU] > 0,
        "Rogue Encampment waypoint NU mode has no frames");
    assertTrue(waypoint.FrameCnt[com.riiablo.engine.Engine.Object.MODE_ON] > 0,
        "Rogue Encampment waypoint ON mode has no frames");
    for (String mode : new String[] {"NU", "ON"}) {
      String cofPath = "data\\global\\objects\\wp\\cof\\wp" + mode + "HTH.cof";
      assertTrue(Riiablo.mpqs.contains(cofPath),
          "Rogue Encampment waypoint " + mode + " COF is missing");
      COF cof = COF.loadFromFile(Riiablo.mpqs.resolve(cofPath));
      for (int i = 0; i < cof.getNumLayers(); i++) {
        COF.Layer layer = cof.getLayer(i);
        String component = com.riiablo.engine.Engine.getComposite(layer.component);
        String dccPath = "data\\global\\objects\\wp\\" + component + "\\wp"
            + component + "LIT" + mode + layer.weaponClass + ".dcc";
        assertTrue(Riiablo.mpqs.contains(dccPath),
            "Rogue Encampment waypoint component is missing: " + dccPath);
      }
    }
    System.out.println("[ACT1-DIAG] town waypoint object=119 token=" + waypoint.Token
        + " draw=" + waypoint.Draw + " modeNU=" + waypoint.Mode[0]
        + " modeON=" + waypoint.Mode[com.riiablo.engine.Engine.Object.MODE_ON]
        + " frameNU=" + waypoint.FrameCnt[com.riiablo.engine.Engine.Object.MODE_NU]
        + " frameON=" + waypoint.FrameCnt[com.riiablo.engine.Engine.Object.MODE_ON]
        + " components="
        + java.util.Arrays.toString(waypoint.Components));
  }

  @Test
  public void fixedSeedAct1LayoutIsStableAndExportable() {
    int seed = Integer.decode(System.getProperty("d2.seed", DEFAULT_SEED));
    int difficulty = Integer.getInteger("d2.difficulty", DEFAULT_DIFFICULTY);
    int burialId = findLevelId("Burial Grounds");
    assertTrue(burialId > 0, "Burial Grounds is missing from Levels.txt");
    assertNativeUndergroundVisRoutes();

    Act1D2MOOLayoutBridge.LayoutAndDrlg first =
        Act1D2MOOLayoutBridge.getLayoutAndDrlg(seed, difficulty, burialId);
    assertNotNull(first, "D2MOO Act1 layout failed; inspect ACT1_D2MOO logs");
    assertDiscoveredNativeSublevels(first);
    assertNativeMonasteryMainline(first.drlg, first.result.levelIds);
    assertNativeJailChain(first.drlg, first.result.levelIds);
    assertNativeCathedralChain(first.drlg, first.result.levelIds);
    assertNativePortalLevels(first.drlg, first.result.levelIds);
    assertFixedSeedOutdoorCoverage(first.drlg);
    String firstSummary = summarize(first.drlg, first.result.levelIds);
    System.out.println("[ACT1-DIAG] seed=" + seed + " diff=" + difficulty + " " + firstSummary);
    exportAndReport(first.drlg);
    assertPresetUnitListsAreAcyclic(first.drlg);
    DrlgDrlg.freeDrlg(first.drlg);
    Act1D2MOOLayoutBridge.releaseDataTables();

    Act1D2MOOLayoutBridge.LayoutAndDrlg second =
        Act1D2MOOLayoutBridge.getLayoutAndDrlg(seed, difficulty, burialId);
    assertNotNull(second, "Second fixed-seed generation failed");
    assertDiscoveredNativeSublevels(second);
    assertNativeMonasteryMainline(second.drlg, second.result.levelIds);
    assertNativeJailChain(second.drlg, second.result.levelIds);
    assertNativeCathedralChain(second.drlg, second.result.levelIds);
    assertNativePortalLevels(second.drlg, second.result.levelIds);
    assertFixedSeedOutdoorCoverage(second.drlg);
    assertEquals(firstSummary, summarize(second.drlg, second.result.levelIds),
        "same seed must produce the same Act1 layout summary");
    DrlgDrlg.freeDrlg(second.drlg);
    Act1D2MOOLayoutBridge.releaseDataTables();
  }

  @Test
  public void mapBuilderCreatesDiscoveredAct1SublevelZones() {
    int seed = Integer.decode(System.getProperty("d2.seed", DEFAULT_SEED));
    Map map = new Map(seed, DEFAULT_DIFFICULTY);
    Act1MapBuilderD2MOD.INSTANCE.generate(map, seed, DEFAULT_DIFFICULTY);
    try {
      int[][] routes = nativeSublevelRoutes();
      for (int[] route : routes) {
        Map.Zone source = map.findZone(Riiablo.files.Levels.get(route[1]));
        Map.Zone target = map.findZone(Riiablo.files.Levels.get(route[0]));
        assertNotNull(source, "source zone is missing " + route[1]);
        assertNotNull(target, "auto-discovered zone is missing " + route[0]);
        assertTrue(Act1MapBuilderD2MOD.INSTANCE.hasD2MooExport(route[0]),
            "native export was rejected " + route[0]);
        assertTrue(target.width() > 0 && target.height() > 0,
            "discovered zone has invalid bounds " + route[0]);
        assertTrue(!overlaps(source, target),
            "linked sublevel must occupy a detached world-coordinate region " + route[0]);
      }
    } finally {
      map.dispose();
    }
  }

  @Test
  public void mapBuilderCreatesContinuousMonasteryZones() {
    int seed = Integer.decode(System.getProperty("d2.seed", DEFAULT_SEED));
    Map map = new Map(seed, DEFAULT_DIFFICULTY);
    Act1MapBuilderD2MOD.INSTANCE.generate(map, seed, DEFAULT_DIFFICULTY);
    try {
      Map.Zone tamoe = zone(map, D2LevelIds.LEVEL_TAMOEHIGHLAND);
      Map.Zone gate = zone(map, D2LevelIds.LEVEL_MONASTERYGATE);
      Map.Zone cloister = zone(map, D2LevelIds.LEVEL_OUTERCLOISTER);
      Map.Zone barracks = zone(map, D2LevelIds.LEVEL_BARRACKS);
      Map.Zone innerCloister = zone(map, D2LevelIds.LEVEL_INNERCLOISTER);
      Map.Zone cathedral = zone(map, D2LevelIds.LEVEL_CATHEDRAL);
      assertTrue(touches(tamoe, gate), "Tamoe Highland must touch Monastery Gate");
      assertTrue(touches(gate, cloister), "Monastery Gate must touch Outer Cloister");
      assertTrue(touches(cloister, barracks), "Outer Cloister must touch Barracks");
      assertTrue(touches(innerCloister, cathedral),
          "Inner Cloister must touch Cathedral");
      assertEquals(60 * com.riiablo.map.DT1.Tile.SUBTILE_SIZE, barracks.width(),
          "Barracks Zone must retain the exact native width");
      assertEquals(42 * com.riiablo.map.DT1.Tile.SUBTILE_SIZE, barracks.height(),
          "Barracks Zone must retain the exact native height");
      for (int levelId : new int[] {
          D2LevelIds.LEVEL_MONASTERYGATE,
          D2LevelIds.LEVEL_OUTERCLOISTER,
          D2LevelIds.LEVEL_BARRACKS,
          D2LevelIds.LEVEL_INNERCLOISTER,
          D2LevelIds.LEVEL_CATHEDRAL }) {
        assertTrue(Act1MapBuilderD2MOD.INSTANCE.hasD2MooExport(levelId),
            "native monastery export was rejected " + levelId);
      }
    } finally {
      map.dispose();
    }
  }

  @Test
  public void mapBuilderCreatesDynamicPortalDestinations() {
    int seed = Integer.decode(System.getProperty("d2.seed", DEFAULT_SEED));
    Map map = new Map(seed, DEFAULT_DIFFICULTY);
    Act1MapBuilderD2MOD.INSTANCE.generate(map, seed, DEFAULT_DIFFICULTY);
    try {
      for (int levelId : new int[] {
          D2LevelIds.LEVEL_TRISTRAM,
          D2LevelIds.LEVEL_MOOMOOFARM }) {
        Map.Zone destination = zone(map, levelId);
        assertTrue(destination.width() > 0 && destination.height() > 0,
            "dynamic portal destination has invalid bounds " + levelId);
        assertTrue(Act1MapBuilderD2MOD.INSTANCE.hasD2MooExport(levelId),
            "dynamic portal destination was not exported " + levelId);
      }
    } finally {
      map.dispose();
    }
  }

  private static Map.Zone zone(Map map, int levelId) {
    Map.Zone zone = map.findZone(Riiablo.files.Levels.get(levelId));
    assertNotNull(zone, "zone is missing " + levelId);
    return zone;
  }

  private static boolean touches(Map.Zone a, Map.Zone b) {
    boolean vertical = (a.x() + a.width() == b.x() || b.x() + b.width() == a.x())
        && a.y() < b.y() + b.height() && b.y() < a.y() + a.height();
    boolean horizontal = (a.y() + a.height() == b.y() || b.y() + b.height() == a.y())
        && a.x() < b.x() + b.width() && b.x() < a.x() + a.width();
    return vertical || horizontal;
  }

  private static boolean overlaps(Map.Zone a, Map.Zone b) {
    return a.x() < b.x() + b.width() && b.x() < a.x() + a.width()
        && a.y() < b.y() + b.height() && b.y() < a.y() + a.height();
  }

  private static void assertNativeMonasteryMainline(D2DrlgStrc drlg, int[] levelIds) {
    for (int levelId : new int[] {
        D2LevelIds.LEVEL_MONASTERYGATE,
        D2LevelIds.LEVEL_OUTERCLOISTER,
        D2LevelIds.LEVEL_BARRACKS }) {
      assertTrue(contains(levelIds, levelId), "native monastery level is missing " + levelId);
      assertNativeLevelExport(drlg, levelId);
    }
    assertTouches(
        DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_TAMOEHIGHLAND),
        DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_MONASTERYGATE));
    assertTouches(
        DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_MONASTERYGATE),
        DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_OUTERCLOISTER));
    assertTouches(
        DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_OUTERCLOISTER),
        DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_BARRACKS));
  }

  private static void assertNativeJailChain(D2DrlgStrc drlg, int[] levelIds) {
    for (int levelId : new int[] {
        D2LevelIds.LEVEL_JAILLVL1,
        D2LevelIds.LEVEL_JAILLVL2,
        D2LevelIds.LEVEL_JAILLVL3,
        D2LevelIds.LEVEL_INNERCLOISTER }) {
      assertTrue(contains(levelIds, levelId), "native jail level is missing " + levelId);
      assertNativeLevelExport(drlg, levelId);
    }

    assertPresetInRange(drlg, D2LevelIds.LEVEL_JAILLVL1,
        D2LvlPrestIds.LVLPREST_ACT1_JAIL_WAYPOINT_W,
        D2LvlPrestIds.LVLPREST_ACT1_JAIL_WAYPOINT_N);
    assertPresetInRange(drlg, D2LevelIds.LEVEL_JAILLVL2,
        D2LvlPrestIds.LVLPREST_ACT1_JAIL_PITSPAWN_W,
        D2LvlPrestIds.LVLPREST_ACT1_JAIL_PITSPAWN_N);
    assertPresetInRange(drlg, D2LevelIds.LEVEL_JAILLVL3,
        D2LvlPrestIds.LVLPREST_ACT1_JAIL_CATH_W,
        D2LvlPrestIds.LVLPREST_ACT1_JAIL_CATH_N);
  }

  private static void assertNativeCathedralChain(D2DrlgStrc drlg, int[] levelIds) {
    for (int levelId : new int[] {
        D2LevelIds.LEVEL_CATHEDRAL,
        D2LevelIds.LEVEL_CATACOMBSLVL1,
        D2LevelIds.LEVEL_CATACOMBSLVL2,
        D2LevelIds.LEVEL_CATACOMBSLVL3,
        D2LevelIds.LEVEL_CATACOMBSLVL4 }) {
      assertTrue(contains(levelIds, levelId),
          "native cathedral/catacombs level is missing " + levelId);
      assertNativeLevelExport(drlg, levelId);
    }

    assertTouches(
        DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_INNERCLOISTER),
        DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_CATHEDRAL));
    assertPresetInRange(drlg, D2LevelIds.LEVEL_CATACOMBSLVL1,
        D2LvlPrestIds.LVLPREST_ACT1_CATACOMBS_NEXT_W,
        D2LvlPrestIds.LVLPREST_ACT1_CATACOMBS_NEXT_N);
    assertPresetInRange(drlg, D2LevelIds.LEVEL_CATACOMBSLVL2,
        D2LvlPrestIds.LVLPREST_ACT1_CATACOMBS_WAYPOINT_W,
        D2LvlPrestIds.LVLPREST_ACT1_CATACOMBS_WAYPOINT_N);
    assertPresetInRange(drlg, D2LevelIds.LEVEL_CATACOMBSLVL3,
        D2LvlPrestIds.LVLPREST_ACT1_CATACOMBS_NEXT_W,
        D2LvlPrestIds.LVLPREST_ACT1_CATACOMBS_NEXT_N);
  }

  private static void assertNativePortalLevels(D2DrlgStrc drlg, int[] levelIds) {
    for (int levelId : new int[] {
        D2LevelIds.LEVEL_TRISTRAM,
        D2LevelIds.LEVEL_MOOMOOFARM }) {
      assertTrue(contains(levelIds, levelId),
          "native dynamic portal level is missing " + levelId);
      assertNativeLevelExport(drlg, levelId);
    }
  }

  private static void assertPresetInRange(
      D2DrlgStrc drlg, int levelId, int firstPreset, int lastPreset) {
    D2DrlgLevel level = DrlgDrlg.getLevel(drlg, levelId);
    for (D2DrlgRoom room = level.getFirstRoomEx(); room != null;
        room = room.getDrlgRoomNext()) {
      if (!(room.getMazeOrOutdoor() instanceof D2DrlgPresetRoomStrc)) continue;
      int preset = ((D2DrlgPresetRoomStrc) room.getMazeOrOutdoor()).getNLevelPrest();
      if (preset >= firstPreset && preset <= lastPreset) return;
    }
    throw new AssertionError("missing native special preset " + firstPreset + ".."
        + lastPreset + " in level " + levelId);
  }

  private static void assertNativeLevelExport(D2DrlgStrc drlg, int levelId) {
    D2DrlgLevel level = DrlgDrlg.getLevel(drlg, levelId);
    assertNotNull(level, "missing D2MOO level " + levelId);
    assertTrue(level.getRooms() > 0, "D2MOO level has no rooms " + levelId);
    TileGrid grid = new TileGrid(
        level.getLevelCoords().getNWidth(), level.getLevelCoords().getNHeight());
    D2MooTileApplier applier = new D2MooTileApplier();
    applier.putGrid(levelId, grid);
    assertTrue(DrlgExport.exportLevelTiles(drlg, levelId, applier) > 0,
        "D2MOO level exported no floors " + levelId);
    assertTrue(applier.getExportedWallCount() > 0,
        "D2MOO level exported no walls " + levelId);
    assertEquals(0, applier.getInvalidTileCount());
    assertEquals(0, applier.getOutOfBoundsCount());
  }

  private static void assertTouches(D2DrlgLevel a, D2DrlgLevel b) {
    assertNotNull(a);
    assertNotNull(b);
    D2DrlgCoord ac = a.getLevelCoords();
    D2DrlgCoord bc = b.getLevelCoords();
    boolean vertical = (ac.getNPosX() + ac.getNWidth() == bc.getNPosX()
        || bc.getNPosX() + bc.getNWidth() == ac.getNPosX())
        && ac.getNPosY() < bc.getNPosY() + bc.getNHeight()
        && bc.getNPosY() < ac.getNPosY() + ac.getNHeight();
    boolean horizontal = (ac.getNPosY() + ac.getNHeight() == bc.getNPosY()
        || bc.getNPosY() + bc.getNHeight() == ac.getNPosY())
        && ac.getNPosX() < bc.getNPosX() + bc.getNWidth()
        && bc.getNPosX() < ac.getNPosX() + ac.getNWidth();
    assertTrue(vertical || horizontal,
        "native levels do not share a boundary " + a.getLevelId() + " -> " + b.getLevelId());
  }

  private static void assertDiscoveredNativeSublevels(
      Act1D2MOOLayoutBridge.LayoutAndDrlg layout) {
    for (int[] route : nativeSublevelRoutes()) {
      assertTrue(contains(layout.result.levelIds, route[0]),
          "native linked level was not discovered: " + route[0]);
      assertNativeLinkedSublevelWarp(layout.drlg, route[0], route[1]);
    }
  }

  private static int[][] nativeSublevelRoutes() {
    return new int[][] {
        {D2LevelIds.LEVEL_DENOFEVIL, D2LevelIds.LEVEL_BLOODMOOR},
        {D2LevelIds.LEVEL_CAVEOFLEVEL1, D2LevelIds.LEVEL_COLDPLAINS},
        {D2LevelIds.LEVEL_HOLELVL1, D2LevelIds.LEVEL_BLACKMARSH},
        {D2LevelIds.LEVEL_PITLVL1, D2LevelIds.LEVEL_TAMOEHIGHLAND},
        {D2LevelIds.LEVEL_CAVEOFLEVEL2, D2LevelIds.LEVEL_CAVEOFLEVEL1},
        {D2LevelIds.LEVEL_UNDERGROUNDPASSAGELVL2,
            D2LevelIds.LEVEL_UNDERGROUNDPASSAGELVL1},
        {D2LevelIds.LEVEL_HOLELVL2, D2LevelIds.LEVEL_HOLELVL1},
        {D2LevelIds.LEVEL_PITLVL2, D2LevelIds.LEVEL_PITLVL1},
        {D2LevelIds.LEVEL_CRYPT, D2LevelIds.LEVEL_BURIALGROUNDS},
        {D2LevelIds.LEVEL_MAUSOLEUM, D2LevelIds.LEVEL_BURIALGROUNDS},
        {D2LevelIds.LEVEL_FORGOTTENTOWER, D2LevelIds.LEVEL_BLACKMARSH},
        {D2LevelIds.LEVEL_TOWERCELLARLVL1, D2LevelIds.LEVEL_FORGOTTENTOWER},
        {D2LevelIds.LEVEL_TOWERCELLARLVL2, D2LevelIds.LEVEL_TOWERCELLARLVL1},
        {D2LevelIds.LEVEL_TOWERCELLARLVL3, D2LevelIds.LEVEL_TOWERCELLARLVL2},
        {D2LevelIds.LEVEL_TOWERCELLARLVL4, D2LevelIds.LEVEL_TOWERCELLARLVL3},
        {D2LevelIds.LEVEL_TOWERCELLARLVL5, D2LevelIds.LEVEL_TOWERCELLARLVL4},
        {D2LevelIds.LEVEL_JAILLVL1, D2LevelIds.LEVEL_BARRACKS},
        {D2LevelIds.LEVEL_JAILLVL2, D2LevelIds.LEVEL_JAILLVL1},
        {D2LevelIds.LEVEL_JAILLVL3, D2LevelIds.LEVEL_JAILLVL2},
        {D2LevelIds.LEVEL_INNERCLOISTER, D2LevelIds.LEVEL_JAILLVL3},
        {D2LevelIds.LEVEL_CATACOMBSLVL1, D2LevelIds.LEVEL_CATHEDRAL},
        {D2LevelIds.LEVEL_CATACOMBSLVL2, D2LevelIds.LEVEL_CATACOMBSLVL1},
        {D2LevelIds.LEVEL_CATACOMBSLVL3, D2LevelIds.LEVEL_CATACOMBSLVL2},
        {D2LevelIds.LEVEL_CATACOMBSLVL4, D2LevelIds.LEVEL_CATACOMBSLVL3},
    };
  }

  private static boolean contains(int[] values, int expected) {
    for (int value : values) if (value == expected) return true;
    return false;
  }

  private static void assertNativeLinkedSublevelWarp(
      D2DrlgStrc drlg, int levelId, int sourceLevelId) {
    D2DrlgLevel level = DrlgDrlg.getLevel(drlg, levelId);
    assertNotNull(level, "missing linked D2MOO level " + levelId);
    assertTrue(level.getRooms() > 0, "linked level has no rooms " + levelId);
    TileGrid grid = new TileGrid(
        level.getLevelCoords().getNWidth(), level.getLevelCoords().getNHeight());
    D2MooTileApplier applier = new D2MooTileApplier();
    applier.putGrid(levelId, grid);
    int floors = DrlgExport.exportLevelTiles(drlg, levelId, applier);
    assertTrue(floors > 0, "linked level exported no floors " + levelId);
    assertTrue(applier.getExportedWallCount() > 0,
        "linked level exported no walls " + levelId);
    assertEquals(0, applier.getInvalidTileCount());
    assertEquals(0, applier.getOutOfBoundsCount());

    Levels.Entry levelEntry = Riiablo.files.Levels.get(levelId);
    boolean reverseWarp = false;
    for (int layer = 0; layer < TileGrid.MAX_WALL_LAYERS && !reverseWarp; layer++) {
      for (int y = 0; y < grid.height && !reverseWarp; y++) {
        for (int x = 0; x < grid.width; x++) {
          int id = grid.wallIds[layer][y][x];
          if (id == -1 || !com.riiablo.map.Orientation.isSpecial(
              com.riiablo.map.DT1.Tile.Index.orientation(id))) continue;
          int mainIndex = com.riiablo.map.DT1.Tile.Index.mainIndex(id);
          int subIndex = com.riiablo.map.DT1.Tile.Index.subIndex(id);
          if (subIndex != 1 && mainIndex < levelEntry.Vis.length
              && levelEntry.Vis[mainIndex] == sourceLevelId) {
            reverseWarp = true;
            break;
          }
        }
      }
    }
    assertTrue(reverseWarp, "linked level has no reverse warp to " + sourceLevelId);
  }

  private static int findLevelId(String name) {
    for (Levels.Entry entry : Riiablo.files.Levels) {
      if (entry != null && name.equals(entry.LevelName)) return entry.Id;
    }
    return -1;
  }

  private static void assertNativeUndergroundVisRoutes() {
    Levels.Entry stony = Riiablo.files.Levels.get(D2LevelIds.LEVEL_STONYFIELD);
    Levels.Entry darkWood = Riiablo.files.Levels.get(D2LevelIds.LEVEL_DARKWOOD);
    Levels.Entry underground =
        Riiablo.files.Levels.get(D2LevelIds.LEVEL_UNDERGROUNDPASSAGELVL1);
    assertEquals(D2LevelIds.LEVEL_UNDERGROUNDPASSAGELVL1, stony.Vis[4]);
    assertEquals(D2LevelIds.LEVEL_UNDERGROUNDPASSAGELVL1, darkWood.Vis[3]);
    assertEquals(D2LevelIds.LEVEL_STONYFIELD, underground.Vis[0]);
    assertEquals(D2LevelIds.LEVEL_DARKWOOD, underground.Vis[1]);
    assertEquals(D2LevelIds.LEVEL_UNDERGROUNDPASSAGELVL2, underground.Vis[4]);
    assertWarpTableEntry(stony, 4);
    assertWarpTableEntry(darkWood, 3);
    assertWarpTableEntry(underground, 0);
    assertWarpTableEntry(underground, 1);
    assertWarpTableEntry(underground, 4);
  }

  private static void assertWarpTableEntry(Levels.Entry level, int mainIndex) {
    assertTrue(level.Warp[mainIndex] >= 0,
        "negative LvlWarp id for level " + level.Id + " mainIndex " + mainIndex);
    assertNotNull(Riiablo.files.LvlWarp.get(level.Warp[mainIndex]),
        "missing LvlWarp row for level " + level.Id + " mainIndex " + mainIndex);
  }

  /** Guards the LvlSub wall-grid regression that blanked most outdoor cells. */
  private static void assertFixedSeedOutdoorCoverage(D2DrlgStrc drlg) {
    assertEquals(91, DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_STONYFIELD).getRooms(),
        "fixed seed Stony Field native border/LvlSub room count changed");
    assertEquals(96, DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_COLDPLAINS).getRooms(),
        "fixed seed Cold Plains native border/LvlSub room count changed");
    assertEquals(80, DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_BLOODMOOR).getRooms(),
        "fixed seed Blood Moor native border/LvlSub room count changed");
    assertEquals(30, DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_BURIALGROUNDS).getRooms(),
        "fixed seed Burial Grounds native graveyard room count changed");
    assertEquals(94, DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_BLACKMARSH).getRooms(),
        "fixed seed Black Marsh native border/LvlSub room count changed");
    assertEquals(95, DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_TAMOEHIGHLAND).getRooms(),
        "fixed seed Tamoe Highland native border/LvlSub room count changed");
    assertEquals(90, DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_DARKWOOD).getRooms(),
        "fixed seed Dark Wood native border/LvlSub room count changed");
    assertEquals(7,
        DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_UNDERGROUNDPASSAGELVL1).getRooms(),
        "fixed seed Underground Passage level 1 native maze room count changed");
    assertBloodMoorNativeLinks(
        DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_BLOODMOOR));
    assertPresetFileSelectionsResolveToDs1(
        DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_STONYFIELD));
    assertPresetFileSelectionsResolveToDs1(
        DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_COLDPLAINS));
    assertPresetFileSelectionsResolveToDs1(
        DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_BLOODMOOR));
    assertPresetFileSelectionsResolveToDs1(
        DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_BURIALGROUNDS));
    assertPresetFileSelectionsResolveToDs1(
        DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_BLACKMARSH));
    assertPresetFileSelectionsResolveToDs1(
        DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_TAMOEHIGHLAND));
    assertPresetFileSelectionsResolveToDs1(
        DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_DARKWOOD));
    assertNativeDirtPaths(DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_STONYFIELD), false);
    assertNativeDirtPaths(DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_COLDPLAINS), false);
    assertNativeDirtPaths(DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_BLOODMOOR), false);
    assertNativeDirtPaths(DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_BLACKMARSH), false);
    assertNativeDirtPaths(DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_TAMOEHIGHLAND), false);
    assertNativeDirtPaths(DrlgDrlg.getLevel(drlg, D2LevelIds.LEVEL_DARKWOOD), false);
    assertPresetUnitListsAreAcyclic(drlg);
  }

  private static void assertNativeDirtPaths(D2DrlgLevel level, boolean roomsInitialized) {
    D2DrlgOutdoorInfoStrc outdoors = level.getOutdoors();
    assertNotNull(outdoors, "outdoor data missing for level " + level.getLevelId());
    assertTrue(outdoors.getNVertices() > 0,
        "native dirt path has no endpoints for level " + level.getLevelId());

    int paths = 0;
    for (int i = 0; i < outdoors.getNVertices(); i++) {
      if (outdoors.getPPathStarts(i) != null) paths++;
    }
    assertTrue(paths > 0, "native dirt path search failed for every endpoint in level "
        + level.getLevelId());

    int topologyCells = 0;
    for (int y = 0; y < outdoors.getNGridHeight(); y++) {
      for (int x = 0; x < outdoors.getNGridWidth(); x++) {
        D2DrlgOutdoorPackedGrid2InfoStrc packed =
            new D2DrlgOutdoorPackedGrid2InfoStrc(outdoors.getPGrid(2).getFlag(x, y));
        if (packed.isNUnkb07()) topologyCells++;
      }
    }
    assertTrue(topologyCells > 0,
        "native dirt path did not mark Grid2 topology for level " + level.getLevelId());

    if (!roomsInitialized) return;
    int floorCells = 0;
    for (D2DrlgRoom room = level.getFirstRoomEx(); room != null;
        room = room.getDrlgRoomNext()) {
      if (!(room.getMazeOrOutdoor() instanceof D2DrlgOutdoorRoomStrc)) continue;
      D2DrlgGridStrc floor =
          ((D2DrlgOutdoorRoomStrc) room.getMazeOrOutdoor()).getPFloorGrid();
      if (floor == null || floor.getPCellsFlags() == null) continue;
      for (int flags : floor.getPCellsFlags()) {
        if ((flags & 0xFF) == 0x82 && ((flags >>> 8) & 0xFF) != 0) floorCells++;
      }
    }
    assertTrue(floorCells > 0,
        "native dirt path did not reach RoomEx floor grids for level " + level.getLevelId());
  }

  private static void assertBloodMoorNativeLinks(D2DrlgLevel bloodMoor) {
    D2DrlgOutdoorInfoStrc outdoors = bloodMoor.getOutdoors();
    assertNotNull(outdoors, "Blood Moor outdoor data missing");
    D2DrlgVertexStrc head = outdoors.getPVertex();
    assertNotNull(head, "Blood Moor boundary vertices missing");

    int flaggedEdges = 0;
    boolean southTownPresetEdge = false;
    D2DrlgVertexStrc vertex = head;
    do {
      if ((vertex.getDwFlags() & 1) != 0) {
        flaggedEdges++;
        if ((vertex.getDwFlags() & 2) != 0
            && vertex.getNPosY() == outdoors.getNGridHeight() - 1) {
          southTownPresetEdge = true;
        }
      }
      vertex = vertex.getPNext();
    } while (vertex != head);
    assertEquals(2, flaggedEdges,
        "Blood Moor must retain both Cold Plains and town boundary edges");
    assertTrue(southTownPresetEdge,
        "Rogue Encampment preset link must remain on Blood Moor's south edge");

    int levelLinks = 0;
    int pickedFile3Links = 0;
    for (int y = 0; y < outdoors.getNGridHeight(); y++) {
      for (int x = 0; x < outdoors.getNGridWidth(); x++) {
        D2DrlgOutdoorPackedGrid2InfoStrc packed =
            new D2DrlgOutdoorPackedGrid2InfoStrc(outdoors.getPGrid(2).getFlag(x, y));
        if (packed.isBLvlLink()) {
          levelLinks++;
          if (packed.getNPickedFile() == 3) pickedFile3Links++;
        }
      }
    }
    assertEquals(1, levelLinks,
        "native preset town edge is flag=3 and must not become a second bLvlLink");
    assertEquals(1, pickedFile3Links,
        "Blood Moor's ordinary outdoor link must select native file variant 3");
  }

  private static void assertPresetUnitListsAreAcyclic(D2DrlgStrc drlg) {
    for (D2DrlgLevel level = drlg.getLevel(); level != null; level = level.getPNextLevel()) {
      for (D2DrlgRoom room = level.getFirstRoomEx(); room != null;
          room = room.getDrlgRoomNext()) {
        java.util.IdentityHashMap<D2PresetUnit, Boolean> seen = new java.util.IdentityHashMap<>();
        for (D2PresetUnit unit = room.getPresetUnits(); unit != null; unit = unit.getPNext()) {
          assertTrue(seen.put(unit, Boolean.TRUE) == null,
              "cyclic preset-unit list in level " + level.getLevelId() + " room ("
                  + room.getNTileXPos() + ',' + room.getNTileYPos() + ')');
        }
      }
    }
  }

  /** Level links may use a non-random File4, but must never select a "0" placeholder. */
  private static void assertPresetFileSelectionsResolveToDs1(D2DrlgLevel level) {
    for (D2DrlgRoom room = level.getFirstRoomEx(); room != null;
        room = room.getDrlgRoomNext()) {
      if (!(room.getMazeOrOutdoor() instanceof D2DrlgPresetRoomStrc)) continue;
      D2DrlgPresetRoomStrc preset = (D2DrlgPresetRoomStrc) room.getMazeOrOutdoor();
      assertNotNull(preset.getPMap(), "preset room has no map");
      assertNotNull(preset.getPMap().getPLvlPrestTxtRecord(), "preset map has no LvlPrest");
      int pickedFile = preset.getPMap().getNPickedFile();
      String path = preset.getPMap().getPLvlPrestTxtRecord().getSzFile(pickedFile);
      assertTrue(path != null && !path.isEmpty() && !"0".equals(path),
          "level " + level.getLevelId() + " preset " + preset.getNLevelPrest()
              + " selected unresolved file " + pickedFile + " path=" + path);
    }
  }

  private static String summarize(D2DrlgStrc drlg, int[] levelIds) {
    StringBuilder out = new StringBuilder();
    for (int levelId : levelIds) {
      D2DrlgLevel level = DrlgDrlg.getLevel(drlg, levelId);
      assertNotNull(level, "missing D2MOO level " + levelId);
      assertNotNull(level.getLevelCoords(), "missing coordinates for level " + levelId);
      if (levelId != D2LevelIds.LEVEL_ROGUEENCAMPMENT) {
        assertTrue(level.getRooms() > 0, "no rooms generated for level " + levelId);
      }
      out.append(levelId).append(':')
          .append(level.getDrlgType()).append(':')
          .append(level.getLevelCoords().getNPosX()).append(',')
          .append(level.getLevelCoords().getNPosY()).append(',')
          .append(level.getLevelCoords().getNWidth()).append('x')
          .append(level.getLevelCoords().getNHeight()).append(':')
          .append(level.getRooms()).append(';');
    }
    return out.toString();
  }

  private static void exportAndReport(D2DrlgStrc drlg) {
    D2MooTileApplier applier = new D2MooTileApplier();
    for (int levelId : new int[] {
        D2LevelIds.LEVEL_STONYFIELD,
        D2LevelIds.LEVEL_COLDPLAINS,
        D2LevelIds.LEVEL_BLOODMOOR,
        D2LevelIds.LEVEL_BURIALGROUNDS,
        D2LevelIds.LEVEL_BLACKMARSH,
        D2LevelIds.LEVEL_TAMOEHIGHLAND,
        D2LevelIds.LEVEL_DARKWOOD,
        D2LevelIds.LEVEL_UNDERGROUNDPASSAGELVL1 }) {
      D2DrlgLevel level = DrlgDrlg.getLevel(drlg, levelId);
      int width = Math.max(1, level.getLevelCoords().getNWidth());
      int height = Math.max(1, level.getLevelCoords().getNHeight());
      TileGrid grid = new TileGrid(width, height);
      applier.putGrid(levelId, grid);
      applier.resetLastExportedFloorCount();
      int attempted = DrlgExport.exportLevelTiles(drlg, levelId, applier);
      int[] unitStats = new int[4];
      int presetUnits = DrlgExport.exportLevelPresetUnits(drlg, levelId,
          (exportLevelId, unitType, index, mode, x, y, ds1Raw, spawned) -> {
            unitStats[0]++;
            if (unitType == D2UnitTypes.UNIT_OBJECT && !spawned) {
              unitStats[1]++;
              if (ds1Raw) {
                unitStats[2]++;
                assertTrue(index >= 0 && index < Riiablo.files.obj.getSize(1),
                    "Act I DS1 object preset index is invalid: level=" + levelId
                        + " index=" + index);
                int objectId = Riiablo.files.obj.getObjectId(1, index);
                com.riiablo.codec.excel.Objects.Entry base = Riiablo.files.objects.get(objectId);
                if (base != null
                    && (base.SubClass & com.riiablo.engine.Engine.Object.SUBCLASS_WAYPOINT) != 0) {
                  unitStats[3]++;
                }
              }
            }
          });
      assertEquals(presetUnits, unitStats[0],
          "preset-unit callback accounting mismatch for level " + levelId);
      int expectedObjects = expectedFixedSeedRawObjects(levelId);
      assertEquals(expectedObjects, unitStats[2],
          "fixed-seed native DS1 object coverage changed for level " + levelId);
      int expectedWaypoints = levelId == D2LevelIds.LEVEL_COLDPLAINS
              || levelId == D2LevelIds.LEVEL_STONYFIELD
              || levelId == D2LevelIds.LEVEL_DARKWOOD
              || levelId == D2LevelIds.LEVEL_BLACKMARSH
          ? 1 : 0;
      assertEquals(expectedWaypoints, unitStats[3],
          "native waypoint object coverage changed for level " + levelId);
      if (levelId != D2LevelIds.LEVEL_BURIALGROUNDS
          && levelId != D2LevelIds.LEVEL_UNDERGROUNDPASSAGELVL1) {
        assertNativeDirtPaths(level, true);
      }
      SourceGridStats source = sourceGridStats(level);
      assertTrue(attempted > 0, "D2MOO exported no floor tiles for level " + levelId);
      int expectedFloors = expectedFixedSeedFloors(levelId);
      if (expectedFloors >= 0) {
        assertEquals(expectedFloors, attempted,
            "fixed-seed floor coverage changed for level " + levelId);
      }
      assertEquals(0, applier.getMissingGridCount(), "missing target grid for level " + levelId);
      assertEquals(0, applier.getOutOfBoundsCount(), "out-of-bounds tile for level " + levelId);
      assertEquals(0, applier.getInvalidTileCount(), "invalid tile id for level " + levelId);
      assertEquals(expectedFixedSeedClippedFloors(levelId),
          applier.getClippedBoundaryFloorCount(),
          "fixed-seed native shared-boundary floor count changed for level " + levelId);
      assertEquals(attempted - applier.getClippedBoundaryFloorCount(),
          applier.getLastExportedFloorCount(),
          "not every exported floor tile was written for level " + levelId);
      assertEquals(levelId == D2LevelIds.LEVEL_DARKWOOD ? 1 : 0,
          applier.getDuplicatePositionCount(),
          "unexpected shared floor coordinates for level " + levelId);
      assertEquals(0, applier.getNonFloorOrientationCount(),
          "floor export referenced non-floor DT1 entries for level " + levelId);
      assertEquals(0, applier.getWallLayerOverflowCount(),
          "more than four distinct walls occupied one coordinate for level " + levelId);
      assertEquals(0, applier.getNonWallOrientationCount(),
          "wall export referenced an incompatible orientation for level " + levelId);
      assertEquals(0, applier.getNonShadowOrientationCount(),
          "shadow export referenced a non-shadow DT1 entry for level " + levelId);
      assertTrue(applier.getExportedWallCount() > 0,
          "Act1 picked presets produced no wall tiles for level " + levelId);
      if (levelId == D2LevelIds.LEVEL_STONYFIELD
          || levelId == D2LevelIds.LEVEL_DARKWOOD
          || levelId == D2LevelIds.LEVEL_UNDERGROUNDPASSAGELVL1) {
        assertTrue(countWarpSpecials(grid) > 0,
            "native entry/exit produced no warp special for level " + levelId);
        assertNativeUndergroundSpecials(levelId, grid);
      }
      assertTrue(source.presetRooms > 0,
          "Act1 picked preset cells produced no preset rooms for level " + levelId);
      assertEquals(applier.getCallbackCount(), attempted
              + applier.getExportedWallCount() + applier.getExportedShadowCount()
              + applier.getClippedBoundaryCount() - applier.getClippedBoundaryFloorCount()
              + applier.getDuplicateWallCount() + applier.getWallLayerOverflowCount(),
          "layer callback accounting mismatch for level " + levelId);
      System.out.println("[ACT1-DIAG] level=" + levelId
          + " size=" + width + 'x' + height
          + " attemptedFloor=" + attempted
          + " callbacks=" + applier.getCallbackCount()
          + " writtenFloor=" + applier.getLastExportedFloorCount()
          + " writtenWall=" + applier.getExportedWallCount()
          + " writtenShadow=" + applier.getExportedShadowCount()
          + " ignoredLayer=" + applier.getIgnoredLayerCount()
          + " missingGrid=" + applier.getMissingGridCount()
          + " outOfBounds=" + applier.getOutOfBoundsCount()
          + " clippedBoundary=" + applier.getClippedBoundaryCount()
          + " clippedFloor=" + applier.getClippedBoundaryFloorCount()
          + " invalidTile=" + applier.getInvalidTileCount()
          + " duplicatePosition=" + applier.getDuplicatePositionCount()
          + " duplicateWall=" + applier.getDuplicateWallCount()
          + " duplicateShadow=" + applier.getDuplicateShadowCount()
          + " wallOverflow=" + applier.getWallLayerOverflowCount()
          + " nonFloorOrientation=" + applier.getNonFloorOrientationCount()
          + " nonWallOrientation=" + applier.getNonWallOrientationCount()
          + " nonShadowOrientation=" + applier.getNonShadowOrientationCount()
          + " zeroTileId=" + applier.getZeroTileIdCount()
          + " uniqueFloorIds=" + applier.getUniqueFloorIdCount()
          + " uniqueWallIds=" + applier.getUniqueWallIdCount()
          + " uniqueShadowIds=" + applier.getUniqueShadowIdCount()
          + " warpSpecials=" + formatWarpSpecials(grid)
          + " presetUnits=" + presetUnits
          + " objects=" + unitStats[1]
          + " rawDs1Objects=" + unitStats[2]
          + " sourceWallFlags=" + source.wallFlags
          + " sourceShadowFlags=" + source.shadowFlags
          + " sourceNonzeroWallCells=" + source.nonzeroWallCells
          + " outdoorRooms=" + source.outdoorRooms
          + " presetRooms=" + source.presetRooms);
    }
  }

  private static int expectedFixedSeedFloors(int levelId) {
    switch (levelId) {
      case D2LevelIds.LEVEL_STONYFIELD: return 5824;
      case D2LevelIds.LEVEL_COLDPLAINS: return 6144;
      case D2LevelIds.LEVEL_BLOODMOOR: return 5122;
      case D2LevelIds.LEVEL_BURIALGROUNDS: return 1920;
      case D2LevelIds.LEVEL_BLACKMARSH: return 6016;
      case D2LevelIds.LEVEL_TAMOEHIGHLAND: return 6080;
      case D2LevelIds.LEVEL_DARKWOOD: return 5761;
      case D2LevelIds.LEVEL_UNDERGROUNDPASSAGELVL1: return 4032;
      default: throw new IllegalArgumentException("unexpected level " + levelId);
    }
  }

  private static int expectedFixedSeedClippedFloors(int levelId) {
    return levelId == D2LevelIds.LEVEL_BLOODMOOR ? 2 : 0;
  }

  private static int expectedFixedSeedRawObjects(int levelId) {
    switch (levelId) {
      case D2LevelIds.LEVEL_STONYFIELD: return 24;
      case D2LevelIds.LEVEL_COLDPLAINS: return 14;
      case D2LevelIds.LEVEL_BLOODMOOR: return 6;
      case D2LevelIds.LEVEL_BURIALGROUNDS: return 11;
      case D2LevelIds.LEVEL_BLACKMARSH: return 31;
      case D2LevelIds.LEVEL_TAMOEHIGHLAND: return 7;
      case D2LevelIds.LEVEL_DARKWOOD: return 30;
      case D2LevelIds.LEVEL_UNDERGROUNDPASSAGELVL1: return 32;
      default: throw new IllegalArgumentException("unexpected level " + levelId);
    }
  }

  private static int countWarpSpecials(TileGrid grid) {
    int count = 0;
    for (int layer = 0; layer < TileGrid.MAX_WALL_LAYERS; layer++) {
      for (int y = 0; y < grid.height; y++) {
        for (int x = 0; x < grid.width; x++) {
          int id = grid.wallIds[layer][y][x];
          if (id != -1 && com.riiablo.map.Orientation.isSpecial(
              com.riiablo.map.DT1.Tile.Index.orientation(id))) count++;
        }
      }
    }
    return count;
  }

  private static void assertNativeUndergroundSpecials(int levelId, TileGrid grid) {
    switch (levelId) {
      case D2LevelIds.LEVEL_STONYFIELD:
        assertTrue(containsTileId(grid, Map.ID.VIS_4_40));
        assertTrue(containsTileId(grid, Map.ID.VIS_4_41));
        break;
      case D2LevelIds.LEVEL_DARKWOOD:
        assertTrue(containsTileId(grid, Map.ID.VIS_3_30));
        assertTrue(containsTileId(grid, Map.ID.VIS_3_31));
        break;
      case D2LevelIds.LEVEL_UNDERGROUNDPASSAGELVL1:
        assertTrue(containsTileId(grid, Map.ID.VIS_0_03));
        assertTrue(containsTileId(grid, Map.ID.VIS_1_15));
        assertTrue(containsTileId(grid, Map.ID.VIS_4_38));
        break;
      default:
        throw new IllegalArgumentException("unexpected level " + levelId);
    }
  }

  private static boolean containsTileId(TileGrid grid, int expected) {
    for (int layer = 0; layer < TileGrid.MAX_WALL_LAYERS; layer++) {
      for (int y = 0; y < grid.height; y++) {
        for (int x = 0; x < grid.width; x++) {
          if (grid.wallIds[layer][y][x] == expected) return true;
        }
      }
    }
    return false;
  }

  private static String formatWarpSpecials(TileGrid grid) {
    StringBuilder out = new StringBuilder("[");
    int count = 0;
    for (int layer = 0; layer < TileGrid.MAX_WALL_LAYERS; layer++) {
      for (int y = 0; y < grid.height; y++) {
        for (int x = 0; x < grid.width; x++) {
          int id = grid.wallIds[layer][y][x];
          if (id == -1 || !com.riiablo.map.Orientation.isSpecial(
              com.riiablo.map.DT1.Tile.Index.orientation(id))) continue;
          count++;
          if (count <= 8) {
            if (count > 1) out.append(',');
            out.append(String.format("0x%08X@%d,%d", id, x, y));
          }
        }
      }
    }
    if (count > 8) out.append(",...");
    return out.append(']').toString();
  }

  private static SourceGridStats sourceGridStats(D2DrlgLevel level) {
    SourceGridStats stats = new SourceGridStats();
    for (D2DrlgRoom room = level.getFirstRoomEx(); room != null; room = room.getDrlgRoomNext()) {
      if (room.getMazeOrOutdoor() instanceof D2DrlgOutdoorRoomStrc) {
        stats.outdoorRooms++;
        D2DrlgOutdoorRoomStrc outdoor = (D2DrlgOutdoorRoomStrc) room.getMazeOrOutdoor();
        collectGridStats(outdoor.getPWallGrid(), stats, true);
        collectGridStats(outdoor.getPFloorGrid(), stats, false);
      } else if (room.getMazeOrOutdoor() instanceof D2DrlgPresetRoomStrc) {
        stats.presetRooms++;
        D2DrlgPresetRoomStrc preset = (D2DrlgPresetRoomStrc) room.getMazeOrOutdoor();
        for (D2DrlgGridStrc grid : preset.getPWallGrid()) collectGridStats(grid, stats, true);
        for (D2DrlgGridStrc grid : preset.getPFloorGrid()) collectGridStats(grid, stats, false);
        collectGridStats(preset.getPCellGrid(), stats, false);
      }
    }
    return stats;
  }

  private static void collectGridStats(D2DrlgGridStrc grid, SourceGridStats stats, boolean wallGrid) {
    if (grid == null || grid.getPCellsFlags() == null) return;
    for (int value : grid.getPCellsFlags()) {
      if (wallGrid && value != 0) stats.nonzeroWallCells++;
      if ((value & 0x00000001) != 0) stats.wallFlags++;
      if ((value & 0x08000000) != 0) stats.shadowFlags++;
    }
  }

  private static final class SourceGridStats {
    int wallFlags;
    int shadowFlags;
    int nonzeroWallCells;
    int outdoorRooms;
    int presetRooms;
  }
}
