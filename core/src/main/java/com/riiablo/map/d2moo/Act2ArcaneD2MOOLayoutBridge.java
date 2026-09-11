package com.riiablo.map.d2moo;

import com.badlogic.gdx.Gdx;
import com.d2moo.common.datatbls.DataTbls;
import com.d2moo.common.drlg.D2C_Acts;
import com.d2moo.common.drlg.D2DrlgAct;
import com.d2moo.common.drlg.D2DrlgLevel;
import com.d2moo.common.drlg.D2DrlgPresetRoomStrc;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.d2moo.common.drlg.D2DrlgStrc;
import com.d2moo.common.drlg.D2LevelIds;
import com.d2moo.common.drlg.DrlgDrlg;
import com.d2moo.common.drlg.D2DrlgTypes;
import com.d2moo.common.util.D2FileReader;
import com.d2moo.common.util.D2MemoryPool;
import com.riiablo.map.Map;
import com.riiablo.map.Map.Zone;
import com.riiablo.drlg.TileGrid;

import java.util.IdentityHashMap;

/** Minimal native bridge for Arcane Sanctuary RoomEx and preset metadata. */
public final class Act2ArcaneD2MOOLayoutBridge {
  private static final String TAG = "Act2ArcaneD2MOOLayoutBridge";
  private static final int LEVEL_ARCANE = D2LevelIds.LEVEL_ARCANESANCTUARY;

  private Act2ArcaneD2MOOLayoutBridge() {}

  /**
   * Generates only Level 75 with D2MOO's maze code and projects its native
   * RoomEx rectangles/preset ids into an existing Zone.  Tile rendering keeps
   * the current Zone pipeline until the exported DS1 tile bridge is enabled.
   */
  public static boolean populateZone(int gameSeed, int diff, Zone zone) {
    if (zone == null || zone.levelId() != LEVEL_ARCANE || RiiabloAccess.mpqsMissing()) {
      return false;
    }
    D2DrlgStrc drlg = null;
    try {
      DataTbls.setLevelDefBinCache(
          Act1D2MOOLayoutBridge.buildLevelDefCache(diff, -1));
      DataTbls.setLevelTypesTxtCache(Act1D2MOOLayoutBridge.buildLevelTypesCache());
      D2FileReader.ArchiveReader archive = Act1D2MOOLayoutBridge::readArchiveFile;
      DataTbls.loadLvlPrestTxt(archive, 0);
      DataTbls.loadLvlSubTxt(archive);
      DataTbls.loadLvlMazeTxt(archive);
      DataTbls.loadLvlWarpTxt(archive);

      D2DrlgAct act = new D2DrlgAct();
      act.setAct(D2C_Acts.ACT_II);
      act.setTownId(D2LevelIds.LEVEL_LUTGHOLEIN);
      act.setPMemPool(new D2MemoryPool());
      drlg = DrlgDrlg.allocDrlg(act, D2C_Acts.ACT_II, archive, gameSeed,
          D2LevelIds.LEVEL_LUTGHOLEIN, 0, null, (byte) diff, null, null);
      if (drlg == null) return false;
      D2DrlgLevel level = DrlgDrlg.getLevel(drlg, LEVEL_ARCANE);
      if (level == null) return false;
      DrlgDrlg.initLevel(level);
      if (level.getFirstRoomEx() == null || level.getLevelCoords() == null) return false;

      projectRooms(level, zone);
      D2MooTileApplier applier = new D2MooTileApplier();
      TileGrid grid = new TileGrid(level.getLevelCoords().getNWidth(),
          level.getLevelCoords().getNHeight());
      applier.putGrid(LEVEL_ARCANE, grid);
      int exportedFloors = com.d2moo.common.drlg.DrlgExport.exportLevelTiles(
          drlg, LEVEL_ARCANE, applier);
      int dt1Mask = com.d2moo.common.drlg.DrlgExport.collectLevelDt1Mask(
          drlg, LEVEL_ARCANE);
      if (exportedFloors > 0) zone.setNativeTileGrid(grid, dt1Mask);
      if (Gdx.app != null) {
        Gdx.app.log(TAG, String.format(
            "Projected native Arcane RoomEx/tiles: seed=%d levelSeed=%d rooms=%d topology=%s floors=%d walls=%d shadows=%d dt1Mask=0x%X",
            gameSeed, zone.levelSeed(), zone.getRoomsEx().size, zone.hasNativeRoomTopology(),
            applier.getLastExportedFloorCount(), applier.getExportedWallCount(),
            applier.getExportedShadowCount(), dt1Mask));
      }
      return !zone.getRoomsEx().isEmpty() && exportedFloors > 0;
    } catch (Throwable t) {
      if (Gdx.app != null) Gdx.app.error(TAG,
          "Native Arcane generation unavailable; keeping compatibility Zone", t);
      return false;
    } finally {
      if (drlg != null) DrlgDrlg.freeDrlg(drlg);
      Act1D2MOOLayoutBridge.releaseDataTables();
    }
  }

  private static void projectRooms(D2DrlgLevel level, Zone zone) {
    int levelX = level.getLevelCoords().getNPosX();
    int levelY = level.getLevelCoords().getNPosY();
    zone.getRoomsEx().clear();
    IdentityHashMap<D2DrlgRoom, Integer> ids = new IdentityHashMap<>();
    java.util.ArrayList<D2DrlgRoom> nativeRooms = new java.util.ArrayList<>();
    for (D2DrlgRoom room = level.getFirstRoomEx(); room != null;
        room = room.getDrlgRoomNext()) {
      ids.put(room, nativeRooms.size());
      nativeRooms.add(room);
      int localTileX = room.getNTileXPos() - levelX;
      int localTileY = room.getNTileYPos() - levelY;
      Map.RoomEx projected = zone.addRoomEx(
          zone.x() + localTileX * com.riiablo.map.DT1.Tile.SUBTILE_SIZE,
          zone.y() + localTileY * com.riiablo.map.DT1.Tile.SUBTILE_SIZE,
          room.getNTileWidth() * com.riiablo.map.DT1.Tile.SUBTILE_SIZE,
          room.getNTileHeight() * com.riiablo.map.DT1.Tile.SUBTILE_SIZE);
      Object maze = room.getMazeOrOutdoor();
      if (maze instanceof D2DrlgPresetRoomStrc) {
        D2DrlgPresetRoomStrc preset = (D2DrlgPresetRoomStrc) maze;
        projected.setPreset(preset.getNLevelPrest(), preset.getNPickedFile());
      }
    }
    for (int i = 0; i < nativeRooms.size(); i++) {
      D2DrlgRoom room = nativeRooms.get(i);
      D2DrlgRoom[] near = room.getPpRoomsNear();
      int count = near == null ? 0 : Math.min(room.getNRoomsNear(), near.length);
      com.badlogic.gdx.utils.IntArray adjacent = new com.badlogic.gdx.utils.IntArray(count);
      for (int j = 0; j < count; j++) {
        Integer id = near[j] == null ? null : ids.get(near[j]);
        if (id != null && id != i) adjacent.add(id);
      }
      zone.getRoomsEx().get(i).setAdjacentRoomIds(adjacent.toArray());
    }
  }

  /** Avoids touching MPQ handles from a headless test JVM. */
  private static final class RiiabloAccess {
    static boolean mpqsMissing() {
      return com.riiablo.Riiablo.mpqs == null;
    }
  }
}
