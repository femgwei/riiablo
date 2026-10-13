package com.riiablo.map.d2moo;

import com.badlogic.gdx.math.Vector2;
import com.d2moo.common.drlg.*;
import com.riiablo.map.Map;
import com.riiablo.map.MapTourPlan;

/** Native outdoor orths, retained before DRLG is freed; no centre portal fallback. */
public final class NativeTownExit {
  private NativeTownExit() {}

  public static void capture(Map map, D2DrlgStrc drlg, int town) {
    int outdoor = MapTourPlan.townOutdoorDestination(town);
    if (outdoor < 0) return;
    captureDirection(map, drlg, town, outdoor);
    captureDirection(map, drlg, outdoor, town);
  }

  private static void captureDirection(Map map, D2DrlgStrc drlg, int from, int to) {
    Map.Zone zone = find(map, from);
    D2DrlgLevel source = DrlgDrlg.getLevel(drlg, from);
    D2DrlgLevel destination = DrlgDrlg.getLevel(drlg, to);
    int[] anchor = NativeLevelSeamAnchor.resolve(source, destination);
    if (zone == null || anchor == null) return;
    int[] vis = DrlgDrlgRoom.getVisArrayFromLevelId(drlg, from);
    int slot = -1;
    for (int i = 0; vis != null && i < Math.min(8, vis.length); i++) {
      if (vis[i] == to) { slot = i; break; }
    }
    if (slot < 0) for (int i = 0; i < 8; i++) {
      if ((vis == null || i >= vis.length || vis[i] == 0)
          && map.getWarpDestinationOverride(from, i) <= 0) { slot = i; break; }
    }
    if (slot < 0) return;
    map.addWarpDestinationOverride(from, slot, to);
    zone.setNativeSeamAnchor(slot, anchor[0], anchor[1]);
  }

  public static void materialize(Map map) {
    for (Map.Zone zone : map.getZones()) {
      if (zone.level == null) continue;
      for (int slot = 0; slot < 8; slot++) {
        int to = map.getWarpDestinationOverride(zone.level.Id, slot);
        if (!MapTourPlan.requiresTownExit(zone.level.Id, to)
            && !MapTourPlan.requiresTownExit(to, zone.level.Id)) continue;
        Vector2 anchor = zone.nativeSeamAnchor(slot);
        if (anchor == null) continue;
        if (zone.hasWarpMarker(slot)) continue;
        int[] best = null;
        float score = Float.MAX_VALUE;
        int mask = com.riiablo.engine.server.NativeGroundPathProfile.PLAYER.terrainFlags;
        for (int y = 0; y < zone.height(); y += 5) for (int x = 0; x < zone.width(); x += 5) {
          float distance = anchor.dst2(x, y);
          if (distance > 40 * 40 || distance >= score
              || map.isBlockedForPath(zone, zone.x() + x, zone.y() + y, mask, 2, false)) continue;
          best = new int[] {x, y}; score = distance;
        }
        if (best != null) zone.addRuntimeWarpMarker(slot, best[0], best[1]);
      }
    }
  }

  private static Map.Zone find(Map map, int id) {
    for (Map.Zone zone : map.getZones()) if (zone.levelId() == id) return zone;
    return null;
  }
}
