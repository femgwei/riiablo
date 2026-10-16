package com.riiablo.engine.server.ai;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.Animation;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.map.Map;

/** D2MOO AITHINK_Fn078_TrapRightArrow (AiThink.cpp, 0x6FCDE570). */
public class TrapRightArrow extends NativeDecisionAI {
  private double elapsedFrames;
  private long nextAttackFrame;

  public TrapRightArrow(int entityId) { super(entityId); }

  @Override protected void beforeThink(float delta) {
    // Relative game frames are sufficient for the native absolute deadline.
    elapsedFrames += Math.max(0f, delta) / (double) Animation.FRAME_DURATION;
  }

  @Override protected void thinkWithoutTarget() { idle(40); }

  @Override protected void think(int targetId, float distance, boolean combat) {
    if (distance < params[0] || distance > params[1]) {
      idle(40);
      return;
    }
    Vector2 source = mPosition.get(entityId).position;
    Vector2 target = mPosition.get(targetId).position;
    long frame = (long) (elapsedFrames + .00001);
    if (alignmentDifference(source, target) > 2 || frame <= nextAttackFrame) {
      idle(30);
      return;
    }
    nextAttackFrame = frame + params[2];
    MapWrapper wrapper = mMapWrapper.get(entityId);
    if (wrapper == null || wrapper.map == null) {
      idle(30);
      return;
    }
    Map.Zone zone = wrapper.zone != null ? wrapper.zone : wrapper.map.getZone(source);
    int levelId = zone != null ? zone.levelId() : 0;
    int type = wrapper.map.nativeArrowTrapType(levelId, this::rollAiRandomNumber);
    if (type != 1) attack(targetId);
    else if (hasSkill(0)) cast(0, targetId);
    else idle(30);
  }

  /** Fn078 aligns on X; Fn079 reuses the same decisions aligned on Y. */
  protected int alignmentDifference(Vector2 source, Vector2 target) {
    return Math.abs((int) source.x - (int) target.x);
  }
}
