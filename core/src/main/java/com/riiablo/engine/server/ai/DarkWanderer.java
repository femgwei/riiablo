package com.riiablo.engine.server.ai;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.quest.Act3QuestSystem;

/** Fn091; the Act III quest owns destination selection and the departure timer. */
public class DarkWanderer extends NativeDecisionAI {
  private int phase;
  private int attempts;
  private final Vector2 destination = new Vector2();

  public DarkWanderer(int entityId) { super(entityId); }

  @Override protected boolean beforeDecision() {
    Act3QuestSystem quests = Riiablo.engine.getSystem(Act3QuestSystem.class);
    if (quests == null || !quests.getDarkWandererCoordinates(entityId, destination)) {
      idle(10); return false;
    }
    if (phase == 0) { phase = 1; attempts = 0; }
    return true;
  }

  @Override protected void thinkWithoutTarget() { idle(10); }

  @Override protected void think(int targetId, float distance, boolean combat) {
    if (distance >= 20) { idle(40); return; }
    if (phase == 1) {
      walkToPoint(destination, 0, Engine.INVALID_ENTITY);
      phase = 2;
      return;
    }
    if (phase != 2) { idle(40); return; }
    Vector2 self = mPosition.get(entityId).position;
    int dx = Math.abs((int) self.x - (int) destination.x);
    int dy = Math.abs((int) self.y - (int) destination.y);
    if (Math.max(dx, dy) + Math.min(dx, dy) / 2 < 2 || attempts >= 3) {
      mNativeUnitFlags.create(entityId).set(NativeUnitFlags.NO_TREASURE_CLASS);
      Act3QuestSystem quests = Riiablo.engine.getSystem(Act3QuestSystem.class);
      if (quests != null) quests.scheduleDarkWandererDeparture(entityId, nativeFrame());
      phase = 3;
      kill();
    } else {
      attempts++;
      if (!walkToPoint(destination, 0, Engine.INVALID_ENTITY)) idle(10);
    }
  }
}
