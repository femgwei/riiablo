package com.riiablo.map.d2moo;

import com.badlogic.gdx.Gdx;
import com.d2moo.common.drlg.D2DrlgLevel;
import com.d2moo.common.drlg.D2DrlgTileInfoStrc;
import com.riiablo.map.DT1;
import com.riiablo.map.Map.Zone;

/** Retains the positioned A2-A5 town spawn before the native DRLG is freed. */
final class NativeTownPortalAnchor {
  private static final String TAG = "NativeTownPortalAnchor";

  private NativeTownPortalAnchor() {}

  /** Call after exportLevelTiles has activated rooms and scanned DS1 spawn markers. */
  static boolean capture(D2DrlgLevel level, Zone zone) {
    if (level == null || zone == null || !zone.isTown()
        || level.getLevelCoords() == null || level.getNTileInfo() <= 0) return false;

    // D2GAME_CreateLinkPortal requests index 11. For positioned towns,
    // sub_6FD788D0 selects the first tile-info record when no index matches:
    // its random counter stays zero and the <= 0 test is unconditional.
    // Retail 1.10f Harrogath has only index 0, so this fallback is required.
    D2DrlgTileInfoStrc marker = level.getPTileInfo(0);
    for (int i = 0; i < level.getNTileInfo(); i++) {
      D2DrlgTileInfoStrc candidate = level.getPTileInfo(i);
      if (candidate != null && candidate.getNTileIndex() == 11) {
        marker = candidate;
        break;
      }
    }
    if (marker == null) return false;

    // GAME/Clients.cpp requests index 0 for players, independently of portals
    // (11).  For positioned levels D2Common groups indices 0..4 together and
    // selects one using the level seed.  Picking the first literal index 0 is
    // wrong for Kurast Docks: that record can be the water-side preset while
    // the native roll selects the berth beside the ship.
    D2DrlgTileInfoStrc playerMarker = selectPlayerMarker(level);
    if (playerMarker != null) {
      zone.setTownPlayerSpawn(
          zone.x() + (playerMarker.getNPosX() - level.getLevelCoords().getNPosX()) * 5 + 3,
          zone.y() + (playerMarker.getNPosY() - level.getLevelCoords().getNPosY()) * 5 + 3);
    }

    // DUNGEON_FindActSpawnLocationEx uses gameTile * 5 + 3. Native
    // markers are absolute game tiles; Zone stores translated world subtiles.
    // The registry still performs collision and portal-separation adjustment.
    int x = zone.x() + (marker.getNPosX() - level.getLevelCoords().getNPosX())
        * DT1.Tile.SUBTILE_SIZE + 3;
    int y = zone.y() + (marker.getNPosY() - level.getLevelCoords().getNPosY())
        * DT1.Tile.SUBTILE_SIZE + 3;
    zone.setTownPortalSpawn(x, y);
    if (Gdx.app != null) Gdx.app.log(TAG, "[TOWN_PORTAL_ANCHOR] level="
        + level.getLevelId() + " tile=" + marker.getNTileIndex()
        + " local=(" + (x - zone.x()) + "," + (y - zone.y())
        + ") world=(" + x + "," + y + ")");
    return true;
  }

  private static D2DrlgTileInfoStrc selectPlayerMarker(D2DrlgLevel level) {
    int matches = 0;
    for (int i = 0; i < level.getNTileInfo(); i++) {
      D2DrlgTileInfoStrc candidate = level.getPTileInfo(i);
      if (isPlayerSpawnIndex(candidate)) matches++;
    }
    if (matches == 0) return null;
    int lowSeed = level.getSeed() == null ? 0 : level.getSeed().getNLowSeed();
    int pick = Math.floorMod(lowSeed, matches);
    for (int i = 0; i < level.getNTileInfo(); i++) {
      D2DrlgTileInfoStrc candidate = level.getPTileInfo(i);
      if (isPlayerSpawnIndex(candidate) && pick-- == 0) return candidate;
    }
    return null;
  }

  private static boolean isPlayerSpawnIndex(D2DrlgTileInfoStrc candidate) {
    if (candidate == null) return false;
    int index = candidate.getNTileIndex();
    return index >= 0 && index <= 4;
  }
}
