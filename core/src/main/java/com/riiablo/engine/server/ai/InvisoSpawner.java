package com.riiablo.engine.server.ai;

import com.riiablo.Riiablo;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.map.Map;

/** D2MOO AITHINK_Fn082_InvisoSpawner. */
public class InvisoSpawner extends NativeDecisionAI {
  private long deadline;
  private int remaining;
  public InvisoSpawner(int entityId) { super(entityId); }
  @Override protected void think(int targetId, float distance, boolean combat) {
    if (deadline == 0) remaining = params[0];
    if (distance <= params[1]) {
      if (remaining <= 0) { kill(); return; }
      if (deadline <= nativeFrame() && spawn()) {
        remaining--; deadline = nativeFrame() + params[2];
      }
    }
    idle(15);
  }
  private boolean spawn() {
    MapWrapper owner = mMapWrapper.get(entityId);
    if (owner == null || owner.zone == null) return false;
    Map.RoomEx room = owner.zone.findRoomEx(mPosition.get(entityId).position.x,
        mPosition.get(entityId).position.y);
    if (room == null || room.width < 2 || room.height < 2) return false;
    MonStats.Entry row = regionalVariantForContext(owner.zone, "mummy1");
    if (row == null) return false;
    for (int i = 0; i < 20; i++) {
      int x = room.x + 1 + room.rollAiSpawnCoordinate(room.width - 1);
      int y = room.y + 1 + room.rollAiSpawnCoordinate(room.height - 1);
      if (owner.map.isBlockedForPath(owner.zone, x, y, 0x1C09, 1, false)) continue;
      int id = factory.createMonster(row, x, y);
      if (id != Engine.INVALID_ENTITY) {
        return true;
      }
    }
    return false;
  }

  static MonStats.Entry regionalVariant(Map.Zone zone, String base) {
    return regionalVariant(zone, base, com.riiablo.Riiablo.files);
  }

  private MonStats.Entry regionalVariantForContext(Map.Zone zone, String base) {
    return regionalVariant(zone, base, files());
  }

  private static MonStats.Entry regionalVariant(Map.Zone zone, String base,
      com.riiablo.Files files) {
    MonStats.Entry original = files.monstats.get(base);
    if (zone.level == null || zone.level.mon == null || zone.level.mon.length == 0) return original;
    for (String name : zone.level.mon) {
      MonStats.Entry row = name == null ? null : files.monstats.get(name);
      if (row != null && base.equals(row.BaseId)) return row;
    }
    MonStats.Entry selected = original;
    for (int i = 0; i < 128 && selected != null; i++) {
      MonStats.Entry next = files.monstats.get(selected.NextInClass);
      if (next == null || next.Level[0] > zone.level.MonLvlEx[0] + 1) break;
      selected = next;
    }
    return selected;
  }
}
