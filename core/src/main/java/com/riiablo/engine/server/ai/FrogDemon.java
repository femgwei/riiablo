package com.riiablo.engine.server.ai;

import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;

/** D2MOO AiThink.cpp AITHINK_Fn052_FrogDemon (0x6FCDAAA0). */
public final class FrogDemon extends NativeDecisionAI {
  private int phase;
  private int hiddenDecisions;

  @Override void writeRuntime(java.io.DataOutputStream out) throws java.io.IOException {
    super.writeRuntime(out); out.writeInt(phase); out.writeInt(hiddenDecisions);
  }
  @Override void readRuntime(java.io.DataInputStream in, boolean apply) throws java.io.IOException {
    super.readRuntime(in, apply);
    int savedPhase = in.readInt(), savedHiddenDecisions = in.readInt();
    if (apply) { phase = savedPhase; hiddenDecisions = savedHiddenDecisions; }
  }

  public FrogDemon(int entityId) { super(entityId); }

  @Override protected boolean actionBlocksDecision() {
    Sequence seq = mSequence.get(entityId);
    if (seq != null && seq.nativeFrogHidden && seq.nativeSubmergeHeld) return false;
    return super.actionBlocksDecision();
  }

  @Override protected void thinkWithoutTarget() {
    think(Engine.INVALID_ENTITY, Float.MAX_VALUE, false);
  }

  @Override protected void think(int targetId, float distance, boolean combat) {
    if (phase == 0) {
      if (hasSkill(0) && distance > 12) {
        if (castAt(0, targetId, targetId >= 0 ? mPosition.get(targetId).position
            : mPosition.get(entityId).position, Engine.Monster.MODE_S1)) {
          mSequence.get(entityId).nativeFrogHidden = true;
        }
        phase = 1;
        hide();
        delayDecision(12); // last Idle replaces the preceding Idle(8)
        return;
      }
      if (hasSkill(1) && factory.restoreFrogPresence(entityId)) {
        emerge();
        return;
      }
      MapWrapper wrapper = mMapWrapper.get(entityId);
      if (wrapper != null && wrapper.map != null) {
        com.badlogic.gdx.math.Vector2 pos = mPosition.get(entityId).position;
        if (!wrapper.map.isBlockedForPath(Map.round(pos.x), Map.round(pos.y),
            DT1.Tile.FLAG_BLOCK_WALK, false)) phase = 2;
      }
      idle(12);
      cofs.setMode(entityId, Engine.Monster.MODE_NU);
      return;
    }
    if (phase == 1) {
      if (hasSkill(1)) {
        if (distance < params[7] && factory.restoreFrogPresence(entityId)) {
          emerge();
          return;
        }
        if (distance < 20 && hiddenDecisions > 64 && factory.restoreFrogPresence(entityId)) {
          emerge();
          return;
        }
      }
      hide();
      idle(24); // last Idle replaces Idle(12); count decisions, not simulation ticks
      hiddenDecisions++;
      return;
    }
    if (targetId == Engine.INVALID_ENTITY) { idle(32); return; }
    if (combat) {
      if (rollAiChance(params[1])) attack(targetId, Engine.Monster.MODE_A2);
      else if (rollAiChance(params[0])) attack(targetId);
      else if (rollAiChance(params[2])) circle(targetId, 3);
      else idle(params[6]);
    } else if (distance >= params[5]) {
      if (rollAiChance(params[3])) circle(targetId, 3);
      else approach(targetId, 0);
    } else if (rollAiChance(params[4])) attack(targetId, Engine.Monster.MODE_A2);
    else if (rollAiChance(params[3])) circle(targetId, 4);
    else idle(params[6]);
    // State 2 persists after attacks: Fn052 does not periodically re-submerge.
  }

  private void hide() {
    stopMovement();
    factory.enterFrogHidden(entityId);
  }

  private void emerge() {
    cast(1, entityId);
    phase = 2;
  }
}
