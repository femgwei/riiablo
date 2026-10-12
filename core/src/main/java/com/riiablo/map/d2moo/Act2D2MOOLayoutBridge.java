package com.riiablo.map.d2moo;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.d2moo.common.datatbls.DataTbls;
import com.d2moo.common.drlg.D2C_Acts;
import com.d2moo.common.drlg.D2DrlgAct;
import com.d2moo.common.drlg.D2DrlgLevel;
import com.d2moo.common.drlg.D2DrlgStrc;
import com.d2moo.common.drlg.D2LevelIds;
import com.d2moo.common.drlg.D2UnitTypes;
import com.d2moo.common.drlg.DrlgDrlg;
import com.d2moo.common.drlg.DrlgExport;
import com.d2moo.common.util.D2FileReader;
import com.d2moo.common.util.D2MemoryPool;
import com.riiablo.Riiablo;
import com.riiablo.map.Map;
import com.riiablo.map.Map.Zone;

/** Resource-gated native Act II town UNIT_MONSTER export. */
public final class Act2D2MOOLayoutBridge {
  private static final String TAG = "Act2D2MOOLayoutBridge";
  private static final int TOWN = D2LevelIds.LEVEL_LUTGHOLEIN;

  private Act2D2MOOLayoutBridge() {}

  /**
   * Exports Lut Gholein's native town monster units into the compatibility
   * Zone. Terrain remains owned by {@code Act2MapBuilderD2MOD}; this bridge
   * only fills the authoritative NPC list consumed by MapManager.
   */
  public static boolean populateTown(int gameSeed, int diff, Zone town, String source) {
    if (town == null || town.levelId() != TOWN || !town.isTown()
        || source == null || RiiabloAccess.mpqsMissing()) return false;

    D2DrlgStrc drlg = null;
    try {
      DataTbls.setLevelDefBinCache(Act1D2MOOLayoutBridge.buildLevelDefCache(diff, -1));
      DataTbls.setLevelTypesTxtCache(Act1D2MOOLayoutBridge.buildLevelTypesCache());
      D2FileReader.ArchiveReader archive = Act1D2MOOLayoutBridge::readArchiveFile;
      DataTbls.loadLvlPrestTxt(archive, 0);
      DataTbls.loadLvlSubTxt(archive);
      DataTbls.loadLvlMazeTxt(archive);
      DataTbls.loadLvlWarpTxt(archive);

      D2DrlgAct act = new D2DrlgAct();
      act.setAct(D2C_Acts.ACT_II);
      act.setTownId(TOWN);
      act.setPMemPool(new D2MemoryPool());
      drlg = DrlgDrlg.allocDrlg(act, D2C_Acts.ACT_II, archive, gameSeed,
          TOWN, 0, null, (byte) diff, null, null);
      if (drlg == null) return false;

      D2DrlgLevel level = DrlgDrlg.getLevel(drlg, TOWN);
      if (level == null) return false;
      if (level.getFirstRoomEx() == null) DrlgDrlg.initLevel(level);
      if (level.getFirstRoomEx() == null) return false;

      return projectTownMonsters(drlg, town, source);
    } catch (Throwable t) {
      if (Gdx.app != null) {
        Gdx.app.error(TAG,
            "Native Act II town NPC export unavailable; keeping compatibility town", t);
      }
      return false;
    } finally {
      if (drlg != null) DrlgDrlg.freeDrlg(drlg);
      Act1D2MOOLayoutBridge.releaseDataTables();
    }
  }

  /** Transactional projection, also exercised with deterministic native RoomEx fixtures. */
  static boolean projectTownMonsters(D2DrlgStrc drlg, Zone town, String source) {
      if (drlg == null || town == null || town.levelId() != TOWN || !town.isTown()
          || source == null || Riiablo.files == null || Riiablo.files.monstats == null) return false;
      D2DrlgLevel level = DrlgDrlg.getLevel(drlg, TOWN);
      if (level == null || level.getLevelCoords() == null) return false;
      int width = level.getLevelCoords().getNWidth();
      int height = level.getLevelCoords().getNHeight();
      if (width <= 0 || height <= 0) return false;
      Array<Map.NativeMonster> exported = new Array<>();
      int[] counts = {0, 0, 0}; // exported, rejected, non-external
      DrlgExport.exportLevelPresetUnits(drlg, TOWN,
          (exportLevelId, roomId, unitType, index, mode, x, y, ds1Raw, spawned,
              externalEntity, sourceFile) -> {
            if (unitType != D2UnitTypes.UNIT_MONSTER) return;
            if (!externalEntity) {
              counts[2]++;
              return;
            }
            if (!sameSource(source, sourceFile) || x < 0 || y < 0
                    || x >= width * com.riiablo.map.DT1.Tile.SUBTILE_SIZE
                    || y >= height * com.riiablo.map.DT1.Tile.SUBTILE_SIZE
                    || x >= town.width() || y >= town.height()) {
              counts[1]++;
              return;
            }
            if (ds1Raw && (Riiablo.files.MonPreset == null || index < 0
                || index >= Riiablo.files.MonPreset.getSize(2))) {
              counts[1]++;
              return;
            }
            Act2ArcaneD2MOOLayoutBridge.NativeMonsterResolution resolved =
                Act2ArcaneD2MOOLayoutBridge.resolveNativeMonster(index, ds1Raw);
            if (resolved.monsterId < 0 || Riiablo.files.monstats.get(resolved.monsterId) == null) {
              counts[1]++;
              return;
            }
            exported.add(new Map.NativeMonster(roomId, resolved.monsterId, mode,
                x, y, resolved.superUniqueId, resolved.superUniqueKey, spawned));
            counts[0]++;
          });

      if (exported.isEmpty() || counts[1] > 0) return false;
      town.replaceNativeTownMonsters(source, exported);
      if (Gdx.app != null) {
        Gdx.app.log(TAG, String.format(
            "Projected native Act II town NPCs: source=%s exported=%d rejected=%d nonExternal=%d",
            source, counts[0], counts[1], counts[2]));
      }
      return true;
  }

  /** MPQ file names are case-insensitive and may include the tile-root prefix. */
  public static boolean sameSource(String a, String b) {
    return a != null && b != null && normalizeSource(a).equals(normalizeSource(b));
  }

  private static String normalizeSource(String source) {
    String normalized = source.replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
    String root = "data/global/tiles/";
    return normalized.startsWith(root) ? normalized.substring(root.length()) : normalized;
  }

  private static final class RiiabloAccess {
    static boolean mpqsMissing() {
      return com.riiablo.Riiablo.mpqs == null;
    }
  }
}
