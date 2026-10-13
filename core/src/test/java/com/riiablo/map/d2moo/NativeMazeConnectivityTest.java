package com.riiablo.map.d2moo;

import com.d2moo.common.datatbls.DataTbls;
import com.d2moo.common.drlg.*;
import com.d2moo.common.util.D2FileReader;
import com.d2moo.common.util.D2MemoryPool;
import com.riiablo.RiiabloTest;
import com.riiablo.drlg.TileGrid;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual selected MPQ collision diagnostics for the tour's disconnected tomb. */
class NativeMazeConnectivityTest extends RiiabloTest {
  @Test void act4ProgressionSeamsComeFromNativeOrthMetadata() {
    D2DrlgStrc drlg = null;
    try {
      DataTbls.setLevelDefBinCache(Act1D2MOOLayoutBridge.buildLevelDefCache(0, -1));
      DataTbls.setLevelTypesTxtCache(Act1D2MOOLayoutBridge.buildLevelTypesCache());
      D2FileReader.ArchiveReader archive = Act1D2MOOLayoutBridge::readArchiveFile;
      DataTbls.loadLvlPrestTxt(archive, 0);
      DataTbls.loadLvlSubTxt(archive);
      DataTbls.loadLvlMazeTxt(archive);
      DataTbls.loadLvlWarpTxt(archive);
      D2DrlgAct act = new D2DrlgAct();
      act.setAct(D2C_Acts.ACT_IV);
      act.setTownId(103);
      act.setPMemPool(new D2MemoryPool());
      drlg = DrlgDrlg.allocDrlg(act, D2C_Acts.ACT_IV, archive, 12345,
          103, 0, null, (byte) 0, null, null);
      assertNotNull(drlg);
      for (int id = 103; id <= 108; id++) {
        D2DrlgLevel level = DrlgDrlg.getLevel(drlg, id);
        if (level.getFirstRoomEx() == null) DrlgDrlg.initLevel(level);
      }
      for (int[] edge : new int[][] {{104, 105}, {105, 106}, {107, 108}}) {
        D2DrlgLevel source = DrlgDrlg.getLevel(drlg, edge[0]);
        D2DrlgLevel destination = DrlgDrlg.getLevel(drlg, edge[1]);
        int[] forward = NativeLevelSeamAnchor.resolve(source, destination);
        int[] reverse = NativeLevelSeamAnchor.resolve(destination, source);
        System.out.println("[ACT4_NATIVE_SEAM] edge=" + edge[0] + "->" + edge[1]
            + " forward=" + java.util.Arrays.toString(forward)
            + " reverse=" + java.util.Arrays.toString(reverse)
            + " vis=" + java.util.Arrays.toString(DrlgDrlgRoom.getVisArrayFromLevelId(drlg, edge[0]))
            + " warp=" + java.util.Arrays.toString(DrlgDrlgWarp.getWarpIdArrayFromLevelId(drlg, edge[0])));
        assertNotNull(forward, "native seamless source " + edge[0]);
        assertNotNull(reverse, "native seamless reverse " + edge[1]);
        assertEquals(source.getLevelCoords().getNPosX() * 5 + forward[0],
            destination.getLevelCoords().getNPosX() * 5 + reverse[0]);
        assertEquals(source.getLevelCoords().getNPosY() * 5 + forward[1],
            destination.getLevelCoords().getNPosY() * 5 + reverse[1]);
      }
      D2DrlgLevel river = DrlgDrlg.getLevel(drlg, 107);
      TileGrid riverGrid = new TileGrid(river.getLevelCoords().getNWidth(), river.getLevelCoords().getNHeight());
      D2MooTileApplier riverApplier = new D2MooTileApplier();
      riverApplier.putGrid(107, riverGrid);
      DrlgExport.exportLevelTiles(drlg, 107, riverApplier);
      int riverRoom = 0;
      for (D2DrlgRoom room = river.getFirstRoomEx(); room != null; room = room.getDrlgRoomNext()) {
        D2DrlgPresetRoomStrc preset = (D2DrlgPresetRoomStrc) room.getMazeOrOutdoor();
        System.out.println("[RIVER_ROOM] id=" + riverRoom++ + " local="
            + (room.getNTileXPos() - river.getLevelCoords().getNPosX()) * 5 + ","
            + (room.getNTileYPos() - river.getLevelCoords().getNPosY()) * 5
            + " preset=" + preset.getNLevelPrest() + " file=" + preset.getNPickedFile()
            + " flags=" + Integer.toHexString(room.getFlags()) + " source="
            + preset.getPMap().getPLvlPrestTxtRecord().getSzFile(preset.getNPickedFile()));
      }
      DrlgExport.exportLevelPresetUnits(drlg, 107,
          (levelId, roomId, type, index, mode, x, y, raw, spawned, external, source) -> {
            if (type == D2UnitTypes.UNIT_TILE) System.out.println("[RIVER_WARP_UNIT] room="
                + roomId + " id=" + index + " local=" + x + "," + y);
          });
      System.out.println("[RIVER_STAIRS_CONNECTIVITY] native="
          + riverComponent(riverGrid, 280, 871, false).cardinality() + " extraSideFootprints="
          + riverComponent(riverGrid, 280, 871, true).cardinality());
      assertTrue(riverComponent(riverGrid, 280, 871, false).get(12 * riverGrid.width * 5 + 301),
          "native SMALL candidate footprints must connect the arrival stairs to the Chaos bridge");
      assertEquals(43, riverComponent(riverGrid, 280, 871, true).cardinality(),
          "regression reproducer: extra side footprints strand the player on the stairs");
    } finally {
      if (drlg != null) DrlgDrlg.freeDrlg(drlg);
      Act1D2MOOLayoutBridge.releaseDataTables();
    }
  }

  @Test void tourSeedMonasteryExportsSelectedCollisionForItsNativeDoors() {
    Act1D2MOOLayoutBridge.LayoutAndDrlg generated = Act1D2MOOLayoutBridge.getLayoutAndDrlg(12345, 0, 17);
    assertNotNull(generated);
    try {
      D2DrlgLevel level = DrlgDrlg.getLevel(generated.drlg, 26);
      TileGrid grid = new TileGrid(level.getLevelCoords().getNWidth(), level.getLevelCoords().getNHeight());
      D2MooTileApplier applier = new D2MooTileApplier();
      applier.putGrid(26, grid);
      assertTrue(DrlgExport.exportLevelTiles(generated.drlg, 26, applier) > 0);
      printCollision(26, grid);
      DrlgExport.exportLevelPresetUnits(generated.drlg, 26,
          (levelId, roomId, type, index, mode, x, y, raw, spawned, external, source) -> {
            if (type == D2UnitTypes.UNIT_OBJECT || type == D2UnitTypes.UNIT_TILE) {
              System.out.println("[MONASTERY_UNIT] room=" + roomId + " type=" + type + " index=" + index
                  + " pos=" + x + "," + y + " source=" + source);
            }
          });
    } finally {
      DrlgDrlg.freeDrlg(generated.drlg);
      Act1D2MOOLayoutBridge.releaseDataTables();
    }
  }

  @Test void tourSeedTombPresetsMatchTheirRoomMetadata() {
    for (int objectId : new int[] {27, 24, 73, 74, 297}) {
      com.riiablo.codec.excel.Objects.Entry object = com.riiablo.Riiablo.files.objects.get(objectId);
      System.out.println("[CONNECTIVITY_OBJECT] id=" + objectId + " size=" + object.SizeX + "x" + object.SizeY
          + " collision=" + java.util.Arrays.toString(object.HasCollision));
    }
    D2DrlgStrc drlg = null;
    try {
      DataTbls.setLevelDefBinCache(Act1D2MOOLayoutBridge.buildLevelDefCache(0, -1));
      DataTbls.setLevelTypesTxtCache(Act1D2MOOLayoutBridge.buildLevelTypesCache());
      D2FileReader.ArchiveReader archive = Act1D2MOOLayoutBridge::readArchiveFile;
      DataTbls.loadLvlPrestTxt(archive, 0);
      DataTbls.loadLvlSubTxt(archive);
      DataTbls.loadLvlMazeTxt(archive);
      DataTbls.loadLvlWarpTxt(archive);
      D2DrlgAct act = new D2DrlgAct();
      act.setAct(D2C_Acts.ACT_II);
      act.setTownId(40);
      act.setPMemPool(new D2MemoryPool());
      drlg = DrlgDrlg.allocDrlg(act, D2C_Acts.ACT_II, archive, 12345,
          40, 0, null, (byte) 0, null, null);
      assertNotNull(drlg);
      for (int id = 40; id <= 73; id++) {
        D2DrlgLevel level = DrlgDrlg.getLevel(drlg, id);
        if (level.getFirstRoomEx() == null) DrlgDrlg.initLevel(level);
      }
      for (int id : new int[] {47, 48, 49, 65, 55, 60, 63}) {
        D2DrlgLevel level = DrlgDrlg.getLevel(drlg, id);
        D2MooTileApplier applier = new D2MooTileApplier();
        TileGrid grid = new TileGrid(level.getLevelCoords().getNWidth(),
            level.getLevelCoords().getNHeight());
        applier.putGrid(id, grid);
        assertTrue(DrlgExport.exportLevelTiles(drlg, id, applier) > 0);
        for (D2DrlgRoom room = level.getFirstRoomEx(); room != null; room = room.getDrlgRoomNext()) {
          D2DrlgPresetRoomStrc preset = (D2DrlgPresetRoomStrc) room.getMazeOrOutdoor();
          assertNotNull(preset.getPMap());
          assertEquals(preset.getNLevelPrest(), preset.getPMap().getNLevelPrest(), "stale preset level=" + id);
          StringBuilder directions = new StringBuilder();
          for (D2DrlgOrth orth = room.getDrlgOrth(); orth != null; orth = orth.getPNext()) {
            directions.append(orth.getNDirection()).append(',');
          }
          System.out.println("[MAZE_CONNECTIVITY] level=" + id + " room="
              + room.getNTileXPos() + "," + room.getNTileYPos() + " preset="
              + preset.getNLevelPrest() + " file=" + preset.getNPickedFile() + " orth=" + directions
              + " source=" + preset.getPMap().getPLvlPrestTxtRecord().getSzFile(preset.getNPickedFile()));
        }
        printCollision(id, grid);
        if (id == 47 || id == 48 || id == 49 || id == 65) {
          java.util.Set<Integer> warpIds = new java.util.HashSet<>();
          DrlgExport.exportLevelPresetUnits(drlg, id,
              (levelId, roomId, type, index, mode, x, y, raw, spawned, external, source) -> {
                if (type == D2UnitTypes.UNIT_TILE) warpIds.add(index);
              });
          int[] expected = DrlgDrlgWarp.getWarpIdArrayFromLevelId(drlg, id);
          System.out.println("[SEWER_WARP_UNITS] level=" + id + " ids=" + warpIds);
          for (int warp : expected) if (warp >= 0) assertTrue(warpIds.contains(warp), "missing native entrance " + id + ":" + warp);
        }
        if (id == 63) {
          for (char direction : new char[] {'l', 'r'}) for (int warpId : new int[] {48, 49}) {
            D2LvlWarpTxt bounds = DataTbls.getLvlWarpTxtRecordFromLevelIdAndDirection(warpId, direction);
            if (bounds != null) System.out.println("[MAGGOT_WARP_DEF] id=" + warpId
                + " direction=" + direction + " offsets=" + bounds.getDwOffsetX() + "," + bounds.getDwOffsetY());
          }
          DrlgExport.exportLevelPresetUnits(drlg, id,
              (levelId, roomId, type, index, mode, x, y, raw, spawned, external, source) -> {
                if (type == D2UnitTypes.UNIT_TILE) System.out.println("[MAGGOT_WARP_UNIT] room="
                    + roomId + " id=" + index + " local=" + x + "," + y);
              });
          for (int layer = 0; layer < TileGrid.MAX_WALL_LAYERS; layer++) {
            for (int y = 0; y < grid.height; y++) for (int x = 0; x < grid.width; x++) {
              int tile = grid.wallIds[layer][y][x];
              if (com.riiablo.map.Map.ID.isWarp(tile)) System.out.println("[MAGGOT_WARP_WALL] tile="
                  + Integer.toHexString(tile) + " localTile=" + x + "," + y);
            }
          }
        }
        if (id == 55) {
          int stride = grid.width * 5;
          for (int y = 35; y < 50; y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 55; x < 175; x++) {
              row.append((grid.nativeCollisionFlags[y * stride + x] & 9) == 0 ? '.' : '#');
            }
            System.out.println("[TOMB_CORRIDOR] x=55 y=" + y + " " + row);
          }
          DrlgExport.exportLevelTiles(drlg, id, new DrlgTileExporter() {
            int layer, tileId;
            String source;
            @Override public void onTile(int levelId, int tileLayer, int x, int y, int selected) {
              layer = tileLayer; tileId = selected;
            }
            @Override public void onTile(int levelId, int tileLayer, int x, int y, int selected,
                int flags, String selectedSource) {
              onTile(levelId, tileLayer, x, y, selected); source = selectedSource;
            }
            @Override public void onTileCollision(int levelId, int x, int y, byte[] flags, int nativeFlags) {
              if (x < 12 || x > 33 || y < 7 || y > 9 || layer == 0) return;
              StringBuilder mask = new StringBuilder();
              for (int sy = 4; sy >= 0; sy--) {
                for (int sx = 0; sx < 5; sx++) mask.append((flags[sy * 5 + sx] & 9) == 0 ? '.' : '#');
                mask.append('/');
              }
              System.out.println("[TOMB_LAYER] pos=" + x + "," + y + " layer=" + layer
                  + " tile=" + Integer.toHexString(tileId) + " flags=" + Integer.toHexString(nativeFlags)
                  + " source=" + source + " mask=" + mask);
            }
          });
        }
        D2MooTileApplier linkedApplier = new D2MooTileApplier();
        TileGrid linkedGrid = new TileGrid(grid.width, grid.height);
        linkedApplier.putGrid(id, linkedGrid);
        DrlgExport.exportLevelTiles(drlg, id, linkedApplier);
        assertArrayEquals(linkedGrid.nativeCollisionFlags, grid.nativeCollisionFlags,
            "first export must not retain collisions superseded by later room linking level=" + id);
      }
    } finally {
      if (drlg != null) DrlgDrlg.freeDrlg(drlg);
      Act1D2MOOLayoutBridge.releaseDataTables();
    }
  }

  // One character per selected 5x5 tile: number of player-free cells (0-9,A-P).
  private static java.util.BitSet riverComponent(TileGrid grid, int startX, int startY, boolean sideFootprints) {
    int width = grid.width * 5, height = grid.height * 5;
    java.util.BitSet seen = new java.util.BitSet(width * height);
    int[] queue = new int[width * height]; int head = 0, tail = 0;
    int start = startY * width + startX; seen.set(start); queue[tail++] = start;
    while (head < tail) {
      int p = queue[head++], px = p % width, py = p / width;
      for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
        int x = px + dx, y = py + dy;
        if (dx == 0 && dy == 0 || !riverFree(grid, x, y)) continue;
        int next = y * width + x;
        if (seen.get(next)) continue;
        if (sideFootprints && dx != 0 && dy != 0
            && (!riverFree(grid, px + dx, py) || !riverFree(grid, px, py + dy))) continue;
        seen.set(next); queue[tail++] = next;
      }
    }
    return seen;
  }

  private static boolean riverFree(TileGrid grid, int x, int y) {
    int width = grid.width * 5, height = grid.height * 5;
    for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
      if (dx != 0 && dy != 0) continue;
      int px = x + dx, py = y + dy;
      if (px < 0 || py < 0 || px >= width || py >= height
          || !grid.nativeCollisionCells[py / 5][px / 5]
          || (grid.nativeCollisionFlags[py * width + px] & 9) != 0) return false;
    }
    return true;
  }

  private static void printCollision(int id, TileGrid grid) {
        int maxX = 0, maxY = 0;
        for (int y = 0; y < grid.height; y++) for (int x = 0; x < grid.width; x++) {
          if (grid.exportedFloorCells[y][x]) { maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); }
        }
        for (int y = 0; y <= maxY; y++) {
          StringBuilder row = new StringBuilder();
          for (int x = 0; x <= maxX; x++) {
            int free = 0;
            if (grid.exportedFloorCells[y][x] && grid.nativeCollisionCells[y][x]) {
              for (int sy = 0; sy < 5; sy++) for (int sx = 0; sx < 5; sx++) {
                if ((grid.nativeCollisionFlags[(y * 5 + sy) * grid.width * 5 + x * 5 + sx] & 9) == 0) free++;
              }
            }
            row.append("0123456789ABCDEFGHIJKLMNOP".charAt(free));
          }
          System.out.println("[MAZE_COLLISION] level=" + id + " y=" + y + " " + row);
        }
  }
}
