package com.riiablo.map;

import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.Objects;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeObjectPopulationResourceTest extends com.riiablo.RiiabloTest {
  @Test void objectGroupRowsMatchOriginalCompiledMpqTable() {
    com.riiablo.codec.excel.ObjGroup groups = com.riiablo.codec.excel.Excel.load(
        com.riiablo.codec.excel.ObjGroup.class, Riiablo.mpqs.resolve("data\\global\\excel\\ObjGroup.txt"));
    java.nio.ByteBuffer binary = java.nio.ByteBuffer.wrap(
        Riiablo.mpqs.resolve("data\\global\\excel\\objgroup.bin").readBytes())
        .order(java.nio.ByteOrder.LITTLE_ENDIAN);
    int rows = binary.getInt();
    assertEquals(rows, groups.size(), "physical row indexing including expansion divider");
    assertEquals(rows * 0x34, binary.remaining(), "D2ObjGroupTxt record size");
    for (int row = 0; row < rows; row++) {
      com.riiablo.codec.excel.ObjGroup.Entry group = groups.get(row);
      int start = 4 + row * 0x34;
      for (int member = 0; member < 8; member++) {
        String where = "row=" + row + " member=" + member;
        assertEquals(binary.getInt(start + 4 * member), group.ID[member], where);
        assertEquals(binary.get(start + 0x20 + member) & 0xff, group.DENSITY[member], where);
        assertEquals(binary.get(start + 0x28 + member) & 0xff, group.PROB[member], where);
      }
      assertEquals(binary.get(start + 0x30) & 0xff, group.SHRINES);
      assertEquals(binary.get(start + 0x31) & 0xff, group.WELLS);
    }
  }
  @Test void objectPopulationFieldsMatchOriginalCompiledMpqTable() {
    java.nio.ByteBuffer binary = java.nio.ByteBuffer.wrap(
        Riiablo.mpqs.resolve("data\\global\\excel\\objects.bin").readBytes())
        .order(java.nio.ByteOrder.LITTLE_ENDIAN);
    int rows = binary.getInt();
    assertEquals(rows, Riiablo.files.objects.size());
    assertEquals(rows * 0x1c0, binary.remaining());
    for (int row = 0; row < rows; row++) {
      Objects.Entry base = Riiablo.files.objects.get(row);
      int start = 4 + row * 0x1c0;
      assertEquals(binary.getInt(start + 0xd0), base.SizeX, "SizeX row=" + row);
      assertEquals(binary.getInt(start + 0xd4), base.SizeY, "SizeY row=" + row);
      assertEquals(binary.get(start + 0x1b1) & 0xff, base.InitFn, "InitFn row=" + row);
      assertEquals(binary.get(start + 0x1b2) & 0xff, base.PopulateFn, "PopulateFn row=" + row);
      assertEquals(binary.get(start + 0x1b3) & 0xff, base.OperateFn, "OperateFn row=" + row);
      for (int mode = 0; mode < 8; mode++)
        assertEquals(binary.get(start + 0x120 + mode) != 0, base.HasCollision[mode],
            "HasCollision row=" + row + " mode=" + mode);
    }
  }
  @Test void levelObjectGroupsAndProbabilitiesMatchOriginalCompiledMpqTable() {
    java.nio.ByteBuffer binary = java.nio.ByteBuffer.wrap(
        Riiablo.mpqs.resolve("data\\global\\excel\\levels.bin").readBytes())
        .order(java.nio.ByteOrder.LITTLE_ENDIAN);
    int rows = binary.getInt();
    assertEquals(rows * 0x220, binary.remaining());
    for (int row = 0; row < rows; row++) {
      int start = 4 + row * 0x220;
      Levels.Entry base = Riiablo.files.Levels.get(row);
      assertNotNull(base, "level=" + row);
      assertEquals(binary.getShort(start) & 0xffff, base.Id, "Id level=" + row);
      for (int slot = 0; slot < 8; slot++) {
        String where = "level=" + row + " slot=" + slot;
        assertEquals(binary.get(start + 0xe5 + slot) & 0xff, base.ObjGrp[slot], "ObjGrp " + where);
        assertEquals(binary.get(start + 0xed + slot) & 0xff, base.ObjPrb[slot], "ObjPrb " + where);
      }
      assertEquals(binary.getInt(start + 0x210), base.Themes, "Themes level=" + row);
    }
  }
  @Test void realObjGroupsGenerateLooseRocksChestsBarrelsAndUrnsExactlyOnce() {
    for (int group : new int[]{38,3,10,29}) {
      Map.Zone zone = zone(group);
      NativeObjectPopulation population = new NativeObjectPopulation();
      int count = population.populate(zone, null);
      assertTrue(count > 0, "missing native group " + group);
      for (Map.NativeObject object : zone.getNativeObjects()) {
        Objects.Entry base = Riiablo.files.objects.get(object.presetIndex);
        assertNotNull(base);
        assertTrue(object.sourceFile.startsWith("ObjGroup/"));
        assertFalse(object.ds1Raw);
        assertEquals(object.roomId, zone.findRoomEx(zone.x() + object.x, zone.y() + object.y).id);
        if (group == 38) assertTrue(object.presetIndex == 155 || object.presetIndex == 174 || object.presetIndex == 175);
        if (group == 3) assertTrue(object.presetIndex == 5 || object.presetIndex == 6);
        if (group == 10) {
          assertTrue(base.Token.equals("B1") || base.Token.equals("BX"), base.Description);
          assertTrue(object.presetIndex == 7 || object.presetIndex == 11);
        }
        if (group == 29) assertTrue(java.util.Arrays.stream(new int[]{4,9,52,94,95}).anyMatch(id -> id == object.presetIndex));
      }
      assertEquals(0, population.populate(zone, null), "room reactivation must not duplicate population");
      assertEquals(count, zone.getNativeObjects().size);
    }
  }
  @Test void blockedTerrainDoesNotGetForcedObjectsAndDensityUsesNativeShifts() {
    Map.Zone zone = zone(3);
    java.util.Arrays.fill(zone.flags, (byte) DT1.Tile.FLAG_BLOCK_WALK);
    assertEquals(0, new NativeObjectPopulation().populate(zone, null));
    assertEquals(3, NativeObjectPopulation.densityCount(64,40,40));
  }
  @Test void themeTwoUsesNativeBarrelGrid() {
    boolean generated = false;
    for (int seed = 1; seed <= 32 && !generated; seed++) {
      Map.Zone zone = zone(0);
      zone.map = new Map(seed, 0);
      zone.level.Themes = 2;
      NativeObjectPopulation population = new NativeObjectPopulation();
      int count = population.populate(zone, null);
      generated = count > 0;
      if (generated) {
        assertEquals("Theme/2/barrels", zone.getNativeObjects().first().sourceFile);
        assertTrue(zone.getNativeObjects().first().presetIndex == 7
            || zone.getNativeObjects().first().presetIndex == 11);
      }
    }
    assertTrue(generated, "theme 2 must populate outdoor barrels");
  }
  @Test void clusteredContainerSpacingAllowsPresetFloorOutsideItsFootprint() {
    Map.Zone zone = zone(29);
    // Every third subtile has PRESET: no 8x8 spacing box can avoid it,
    // but the actual urn footprint still has valid positions.
    for (int y = 0; y < zone.height; y++) for (int x = 0; x < zone.width; x += 3)
      zone.flags[y * zone.width + x] = 0x10;
    assertTrue(new NativeObjectPopulation().populate(zone, null) > 0,
        "Fn1 must use 0xC01 for spacing and PLACEMENT only for the actual footprint");
    zone = zone(29);
    java.util.Arrays.fill(zone.flags, (byte) 0x10);
    assertEquals(0, new NativeObjectPopulation().populate(zone, null),
        "PRESET under the actual footprint must still reject placement");
  }
  @Test void uniqueMonsterMaximumComesFromItsOwnLevelsColumn() {
    Levels table = com.riiablo.codec.excel.Excel.load(Levels.class,
        Riiablo.mpqs.resolve("data\\global\\excel\\Levels.txt"));
    Levels.Entry dryHills = table.get(42);
    assertEquals(1, dryHills.MonUMin[0]);
    assertEquals(2, dryHills.MonUMax[0]);
  }
  private static Map.Zone zone(int group) {
    Map.Zone zone = new Map.Zone();
    zone.map = new Map(1,0);
    zone.level = new Levels.Entry(); zone.level.Id = 2;
    zone.level.ObjGrp = new int[]{group}; zone.level.ObjPrb = new int[]{100};
    zone.width = zone.height = 400; zone.flags = new byte[400*400];
    for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) {
      zone.addRoomEx(x*100,y*100,100,100).setAdjacentRoomIds(new int[0]);
    }
    return zone;
  }
}
