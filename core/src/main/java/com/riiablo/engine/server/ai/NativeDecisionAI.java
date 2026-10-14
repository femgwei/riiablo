package com.riiablo.engine.server.ai;

import com.riiablo.codec.Animation;
import com.riiablo.engine.Engine;
import com.riiablo.skill.SkillCodes;

/** Shared action lifecycle for directly ported AITHINK decision functions. */
abstract class NativeDecisionAI extends AI {
  private float nextThink;
  private String state = "IDLE";
  private boolean dead;

  NativeDecisionAI(int entityId) {
    super(entityId);
  }

  @Override
  public final void update(float delta) {
    if (dead || monster == null || !mPosition.has(entityId)) return;
    beforeThink(delta);
    nextThink -= delta;
    if (mCasting.has(entityId) || mSequence.has(entityId) || nextThink > 0f) return;
    nextThink = Math.max(Animation.FRAME_DURATION, SLEEP);
    float[] distance = {Float.MAX_VALUE};
    int targetId = findNearestTargetWithAidist(distance);
    if (targetId == Engine.INVALID_ENTITY) {
      idle(1);
      return;
    }
    think(targetId, distance[0], isInNativeMeleeRange(targetId));
  }

  /** Hook for native AIs with timers that advance while an action is active. */
  protected void beforeThink(float delta) {}

  protected abstract void think(int targetId, float distance, boolean combat);

  protected final void idle(int frames) {
    stopMovement();
    state = "IDLE";
    nextThink = Math.max(1, frames) * Animation.FRAME_DURATION;
  }

  protected final void approach(int targetId, int velocityBonus) {
    state = "APPROACH";
    if (!walkTo(mPosition.get(targetId).position, velocityBonus, targetId)) idle(10);
  }

  /**
   * Starts the native orbit/strafe movement used by flying monsters. The
   * target remains attached to the movement action so a moving player is not
   * left behind while the pathfinder resolves the side-step.
   */
  protected final void circle(int targetId, float radius) {
    com.badlogic.gdx.math.Vector2 source = mPosition.get(entityId).position;
    com.badlogic.gdx.math.Vector2 target = mPosition.get(targetId).position;
    com.badlogic.gdx.math.Vector2 offset = new com.badlogic.gdx.math.Vector2(source)
        .sub(target);
    if (offset.isZero(0.0001f)) offset.set(1f, 0f);
    offset.rotate90(1).nor().scl(radius).add(target);
    state = "CIRCLE";
    if (!walkTo(offset, 0, targetId)) idle(10);
  }

  protected final void attack(int targetId) {
    attack(targetId, Engine.Monster.MODE_A1);
  }

  /** Starts the native ordinary attack action in the requested monster mode. */
  protected final void attack(int targetId, int attackMode) {
    stopMovement();
    lookAt(targetId);
    state = "ATTACK";
    mSequence.create(entityId).sequence((byte) attackMode, Engine.Monster.MODE_NU);
    mCasting.create(entityId).set(SkillCodes.attack, targetId, mPosition.get(targetId).position);
    com.riiablo.audio.MonsterAudio.play(entityId, monsound + "_attack_1", true);
    nextThink = 0f;
  }

  protected final boolean hasSkill(int slot) {
    if (monster == null || monster.monstats == null) return false;
    String name;
    switch (slot) {
      case 0: name = monster.monstats.Skill1; break;
      case 1: name = monster.monstats.Skill2; break;
      case 2: name = monster.monstats.Skill3; break;
      case 3: name = monster.monstats.Skill4; break;
      case 4: name = monster.monstats.Skill5; break;
      case 5: name = monster.monstats.Skill6; break;
      case 6: name = monster.monstats.Skill7; break;
      case 7: name = monster.monstats.Skill8; break;
      default: return false;
    }
    return name != null && !name.isEmpty();
  }

  protected final void cast(int slot, int targetId) {
    castAt(slot, targetId, mPosition.get(targetId).position);
  }

  protected final boolean castAt(int slot, int targetId, com.badlogic.gdx.math.Vector2 target) {
    if (useMonsterSkill(slot, targetId, target)) {
      state = "CAST";
      nextThink = 0f;
      return true;
    } else {
      // Native AITACTICS_UseSkill failure waits ten frames, not a basic attack.
      idle(10);
      return false;
    }
  }

  @Override
  public void kill() {
    if (dead) return;
    dead = true;
    state = "DEAD";
    stopMovement();
    mCasting.remove(entityId);
    mSequence.create(entityId).sequence(Engine.Monster.MODE_DT, Engine.Monster.MODE_DD);
    com.riiablo.audio.MonsterAudio.play(entityId, monsound + "_death_1", true);
  }

  @Override
  public void onOwnerWarp() {
    super.onOwnerWarp();
    nextThink = 0f;
    if (!dead) state = "IDLE";
  }

  @Override
  public String getState() {
    return state;
  }
}
