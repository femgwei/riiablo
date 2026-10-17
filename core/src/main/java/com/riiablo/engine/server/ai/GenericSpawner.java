package com.riiablo.engine.server.ai;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.map.Map;

/** D2MOO AITHINK_Fn129_GenericSpawner, 0x6FCE5640. */
public class GenericSpawner extends NativeDecisionAI {
  private long lastSpawn;
  private int count;
  public GenericSpawner(int entityId) { super(entityId); }
  @Override public void initialize() { super.initialize(); lastSpawn = nativeFrame(); }
  @Override protected void thinkWithoutTarget() { idle(20); }
  @Override protected void think(int targetId, float distance, boolean combat) {
    MapWrapper owner = mMapWrapper.get(entityId);
    if (monster.nativeSpawnClassId < 0) {
      MonStats.Entry selected = Riiablo.files.monstats.get("imp5");
      MonStats.Entry[] region = owner != null && owner.zone != null
          ? owner.zone.nativeRegionMonsters : new MonStats.Entry[0];
      if (region.length > 0) {
        MonStats.Entry rolled = region[(int) (rollAiRandomNumber() % region.length)];
        if (rolled.genericSpawn) selected = rolled;
        else for (MonStats.Entry row : region) if (row.genericSpawn) { selected = row; break; }
      }
      if (selected == null) { idle(20); return; }
      monster.nativeSpawnClassId = selected.hcIdx;
    }
    if (distance > 20) { idle(20); return; }
    if (count >= params[2]) {
      mNativeUnitFlags.create(entityId).set(NativeUnitFlags.NO_TREASURE_CLASS);
      kill(); return;
    }
    long frame = nativeFrame();
    if (Math.abs(frame - lastSpawn) >= params[0]) {
      lastSpawn = frame;
      if (owner != null && owner.zone != null) {
        Vector2 point = mPosition.get(entityId).position.cpy();
        if (owner.zone.findFreeCoordinates(point, monsterFootprint("evilHut"),
            0, 0x1C09, false, point)) {
          count++;
          com.riiablo.codec.excel.Skills.Entry nest = Riiablo.files.skills.get("Nest");
          if (nest != null) castAt(0, targetId,
              mPosition.get(targetId).position);
          return;
        }
      }
    }
    idle(20);
  }
}
