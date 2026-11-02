package com.riiablo.engine.server.ai;

/**
 * Native Bone Wall segments are stationary summon units.  Their lifetime,
 * health and walk collision are maintained by the skill and wall systems;
 * they must never enter the generic monster movement/attack loop.
 */
public final class BoneWall extends AI {
  public BoneWall(int entityId) {
    super(entityId);
  }

  @Override
  public void update(float delta) {}

  @Override
  public String getState() {
    return "IDLE";
  }
}
