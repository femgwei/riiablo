package com.riiablo.engine.server.ai;

import com.artemis.ComponentMapper;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.NativeTargetNode;
import com.riiablo.map.Map;

/** D2MOO AITHINK_Fn127_NpcBarb (AiThink.cpp, 0x6FCEE450). */
public final class NpcBarb extends NativeDecisionAI {
  protected ComponentMapper<NativeTargetNode> mNativeTargetNode;

  public NpcBarb(int entityId) { super(entityId); }

  @Override protected boolean beforeDecision() {
    // Native node 11 is unregistered; registration must not move an existing node.
    mNativeTargetNode.create(entityId).insert(NativeTargetNode.ALLIED, Engine.INVALID_ENTITY);
    return true;
  }

  @Override protected void think(int targetId, float distance, boolean combat) {
    if (combat) {
      attack(targetId);
      delayDecision(params[0]);
    } else if (distance < params[2] && rollAiChance(params[1])) {
      approach(targetId, 100, true);
    } else {
      wander();
    }
  }

  @Override protected void thinkWithoutTarget() { wander(); }

  private void wander() {
    Vector2 source = mPosition.get(entityId).position;
    int x = Map.round(source.x), y = Map.round(source.y);
    if (walk(x - 40, y - 10)) return;
    boolean side = (rollAiRandomNumber() & 1) != 0;
    if (walk(x - 10, y - 10 - 30 * (side ? 1 : -1))) return;
    if (!walk(x - 10, y - 10 - 30 * (side ? -1 : 1))) idle(15);
  }

  private boolean walk(int x, int y) {
    int tx = x + (int) (rollAiRandomNumber() % 20);
    int ty = y + (int) (rollAiRandomNumber() % 20);
    return walkToPoint(new Vector2(tx, ty), 0, Engine.INVALID_ENTITY);
  }
}
