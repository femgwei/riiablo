package com.riiablo.engine.server.ai;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.MapWrapper;

/** D2MOO AITHINK_Fn126: timed, grouped catapult shots at ground coordinates. */
public class CatapultSpotter extends NativeDecisionAI {
  private static final int[] SKILLS = {287, 288, 303, 304, 305};
  private long lastShot;
  private int shotType;
  private int shotsRemaining;

  public CatapultSpotter(int entityId) { super(entityId); }

  @Override protected boolean beforeDecision() {
    if (lastShot != 0 || rollAiPercentage() >= 3) return true;
    MapWrapper wrapper = mMapWrapper.get(entityId);
    if (wrapper == null || wrapper.map == null || wrapper.zone == null) return true;
    Vector2 source = mPosition.get(entityId).position;
    com.riiablo.map.Map.RoomEx room = wrapper.zone.findRoomEx(source.x, source.y);
    if (room == null) return true;
    com.riiablo.codec.excel.MonStats.Entry variant = files().monstats.get(monster.monstats.BaseId);
    int depth = 0;
    while (variant != null && variant.hcIdx != monster.monstats.hcIdx && depth < 4) {
      variant = files().monstats.get(variant.NextInClass); depth++;
    }
    if (depth >= 4) return true;
    int[] dx = {0, -1, -1, 1}, dy = {-1, 0, 0, 0};
    int x = room.x, y = room.y;
    com.artemis.utils.IntBag units = Riiablo.engine.getAspectSubscriptionManager().get(
        com.artemis.Aspect.all(com.riiablo.engine.server.component.Monster.class)).getEntities();
    for (int step = 0; step < 3; step++) {
      x += dx[depth] * room.width; y += dy[depth] * room.height;
      com.riiablo.map.Map.Zone zone = wrapper.map.getZone(x, y);
      com.riiablo.map.Map.RoomEx checked = zone == null ? null : zone.findRoomEx(x, y);
      if (checked == null) continue;
      if (checked.hasDeadCatapult(monster.monstats.hcIdx - 19)) { kill(); return false; }
      for (int i = 0; i < units.size(); i++) {
        int id = units.get(i);
        if (mMonster.get(id).monstats.hcIdx != monster.monstats.hcIdx - 19
            || nativeLifePercent(id) > 0 || !mPosition.has(id)) continue;
        MapWrapper mapping = mMapWrapper.get(id);
        if (mapping != null && mapping.zone == zone && zone.findRoomEx(
            mPosition.get(id).position.x, mPosition.get(id).position.y) == checked) {
          kill(); return false;
        }
      }
    }
    return true;
  }

  @Override protected void thinkWithoutTarget() {
    if (lastShot == 0) lastShot = 1;
    idle(params[1]);
  }

  @Override protected void think(int targetId, float distance, boolean combat) {
    if (lastShot == 0) lastShot = 1;
    if (lastShot + params[1] > nativeFrame() || !rollAiChance(params[0])) {
      idle(params[1]);
      return;
    }
    if (shotsRemaining > 0) shotsRemaining--;
    else {
      shotType = (int) (rollAiRandomNumber() % SKILLS.length);
      shotsRemaining = params[4];
    }
    int range = params[3];
    Vector2 target = mPosition.get(targetId).position;
    Vector2 desired = new Vector2((int) target.x + offset(range),
        (int) target.y + offset(range));
    Vector2 free = new Vector2();
    MapWrapper wrapper = mMapWrapper.get(entityId);
    if (wrapper == null || wrapper.zone == null
        || !wrapper.zone.findFreeCoordinates(desired, 2, 3, 2053, true, free)) {
      idle(15);
      return;
    }
    Skills.Entry skill = files().skills.get(SKILLS[shotType]);
    if (skill != null && castFixed(skill.Id, Engine.Monster.MODE_A1,
        Engine.INVALID_ENTITY, free)) lastShot = nativeFrame();
    else idle(15);
  }

  private int offset(int range) {
    return range > 0 ? (int) (rollAiRandomNumber() % (2L * range)) - range : 0;
  }
}
