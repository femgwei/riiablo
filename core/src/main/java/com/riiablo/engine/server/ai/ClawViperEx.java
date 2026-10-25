package com.riiablo.engine.server.ai;

import com.badlogic.gdx.ai.utils.Collision;
import com.badlogic.gdx.ai.utils.Ray;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.Animation;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.map.Map;

/** D2MOO AITHINK_Fn142_ClawViperEx, 0x6FCD3B90. */
public class ClawViperEx extends NativeDecisionAI {
  private long nextShot;
  private boolean charged;
  @Override void writeRuntime(java.io.DataOutputStream out) throws java.io.IOException {
    super.writeRuntime(out);
    out.writeLong(nextShot);
    out.writeBoolean(charged);
  }
  @Override void readRuntime(java.io.DataInputStream in, boolean apply) throws java.io.IOException {
    super.readRuntime(in, apply);
    long savedNextShot = in.readLong();
    boolean savedCharged = com.riiablo.engine.replay.CheckpointIO.readBoolean(in);
    if (apply) {
      nextShot = savedNextShot;
      charged = savedCharged;
    }
  }

  public ClawViperEx(int entityId) { super(entityId); }
  private void clearColor() {
    UnitStates states = mUnitStates.get(entityId);
    if (params[5] != 0 && states != null && states.stateList != null)
      states.stateList.removeState(params[5] == 2 ? StateId.RED : StateId.BLUE);
  }
  @Override protected void think(int targetId, float distance, boolean combat) {
    if (charged) clearColor();
    if (combat) {
      if (rollAiChance(params[2])) attack(targetId, Engine.Monster.MODE_A2);
      else idle(params[4]);
      return;
    }
    long frame = nativeFrame();
    if (distance < params[6] && rollAiChance(params[3])) {
      if (nextShot < frame) {
        attack(targetId);
        nextShot = frame + params[7];
      } else idle(params[4]);
      return;
    }
    if (hasSkill(0) && distance < params[1] && rollAiChance(params[0])
        && canCharge(targetId)) {
      cast(0, targetId);
      clearColor();
      charged = true;
    } else if (rollAiPercentage() >= 50) idle(params[4]);
    else approach(targetId, 0);
  }
  private boolean canCharge(int targetId) {
    MapWrapper source = mMapWrapper.get(entityId);
    if (source == null || source.map == null || source.zone == null) return false;
    Vector2 target = mPosition.get(targetId).position;
    Map.Zone destination = source.map.getZone(target);
    if (destination == null || destination.isTown()) return false;
    Ray<Vector2> ray = new Ray<>(mPosition.get(entityId).position.cpy(), target.cpy());
    return !source.map.castRay(source.zone, ray, 0xC01, 0,
        new Collision<>(new Vector2(), new Vector2()), false);
  }
}
