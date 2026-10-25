package com.riiablo.engine.server.ai;

import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.Animation;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Sequence;
import com.badlogic.gdx.math.Vector2;

/** D2MOO AITHINK_Fn026_Arach (0x6FCD69E0). */
public class Arach extends AI {
  static final int PARAM_ATTACK_CHANCE = 0;
  static final int PARAM_CIRCLE_IN_MELEE_CHANCE = 1;
  static final int PARAM_ENGAGE_CHANCE = 2;
  static final int PARAM_RUN_DISTANCE = 3;
  static final int PARAM_HURT_PCT = 4;

  private float nextThink;
  private int aiParam0;
  private int aiParam1;
  private int aiParam2;
  private String state = "IDLE";
  // Escape scratch, overwritten from current positions before every read.
  private final Vector2 tmp = new Vector2();

  @Override void writeRuntime(java.io.DataOutputStream out) throws java.io.IOException {
    super.writeRuntime(out);
    AiRuntimeState.writeTimer(out, nextThink);
    out.writeInt(aiParam0);
    out.writeInt(aiParam1);
    out.writeInt(aiParam2);
    com.riiablo.engine.replay.CheckpointIO.writeString(out, state);
  }
  @Override void readRuntime(java.io.DataInputStream in, boolean apply) throws java.io.IOException {
    super.readRuntime(in, apply);
    float savedNextThink = AiRuntimeState.readTimer(in);
    int savedAiParam0 = in.readInt();
    int savedAiParam1 = in.readInt();
    int savedAiParam2 = in.readInt();
    String savedState = com.riiablo.engine.replay.CheckpointIO.readString(in);
    if (apply) {
      nextThink = savedNextThink;
      aiParam0 = savedAiParam0;
      aiParam1 = savedAiParam1;
      aiParam2 = savedAiParam2;
      state = savedState;
    }
  }

  public Arach(int entityId) {
    super(entityId);
  }

  @Override
  public void update(float delta) {
    if (monster == null || !mPosition.has(entityId)) return;
    nextThink -= delta;
    if (nextThink > 0f || mCasting.has(entityId) || mSequence.has(entityId)) return;
    nextThink = Math.max(Animation.FRAME_DURATION, SLEEP);

    float[] outDistance = {Float.MAX_VALUE};
    int targetId = findNearestTargetWithAidist(outDistance);
    if (targetId == Engine.INVALID_ENTITY) {
      idle(15);
      return;
    }

    int life = lifePercentage();
    boolean combat = isInNativeMeleeRange(targetId);
    if (aiParam0 == 1) {
      aiParam1 = 0;
      if (life > 75) {
        aiParam0 = 0;
        if (!rollAiChance(params[PARAM_ENGAGE_CHANCE])) {
          walkClose(targetId, 6);
        } else {
          aiParam0 = 2;
          walkClose(targetId, 0);
        }
        return;
      }
      if (!combat || params[PARAM_ATTACK_CHANCE] <= 25
          || rollAiChance(params[PARAM_ATTACK_CHANCE] - 25)) {
        if (outDistance[0] < params[PARAM_RUN_DISTANCE]) {
          escape(targetId, 4);
        } else {
          aiParam0 = 0;
          walkClose(targetId, 12);
        }
        return;
      }
      attack(targetId);
      return;
    }

    if (!combat) {
      aiParam2 = (aiParam2 + 1) % 21;
      aiParam1 = 0;
      if (aiParam2 == 1 && rollAiChance(params[PARAM_ENGAGE_CHANCE])) {
        aiParam1 = 1;
      } else {
        if (rollAiChance(20)) walkClose(targetId, 6);
        else idle(15);
        return;
      }
      walkClose(targetId, 0);
      return;
    }

    aiParam0 = 2;
    if (rollAiChance(params[PARAM_ATTACK_CHANCE])) {
      attack(targetId);
      return;
    }
    if (life >= params[PARAM_HURT_PCT]) {
      if (!rollAiChance(params[PARAM_CIRCLE_IN_MELEE_CHANCE])) idle(15);
      else walkClose(targetId, 4);
    } else {
      aiParam0 = 1;
      if (hasSkill(0)) cast(0, targetId);
      else escape(targetId, 8);
    }
  }

  private void walkClose(int targetId, int distance) {
    state = "APPROACH";
    if (distance == 0) walkTo(mPosition.get(targetId).position, targetId);
    else walkTo(mPosition.get(targetId).position, targetId);
  }

  private boolean escape(int targetId, float distance) {
    Vector2 source = mPosition.get(entityId).position;
    Vector2 target = mPosition.get(targetId).position;
    tmp.set(source).sub(target);
    if (tmp.isZero()) tmp.set(1, 0);
    tmp.nor().scl(distance).add(source);
    state = "ESCAPE";
    return moveTo(tmp, false, 0, true, Engine.INVALID_ENTITY);
  }

  private void attack(int targetId) {
    stopMovement();
    lookAt(targetId);
    state = "ATTACK";
    mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
    mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId,
        mPosition.get(targetId).position);
  }

  private void cast(int slot, int targetId) {
    if (useMonsterSkill(slot, targetId, mPosition.get(targetId).position)) state = "CAST";
    else idle(10);
  }

  private boolean hasSkill(int slot) {
    String name = slot == 0 ? monster.monstats.Skill1 : monster.monstats.Skill2;
    return name != null && !name.isEmpty();
  }

  private void idle(int frames) {
    stopMovement();
    state = "IDLE";
    nextThink = Math.max(1, frames) * Animation.FRAME_DURATION;
  }

  private int lifePercentage() {
    if (!mAttributesWrapper.has(entityId)) return 100;
    Attributes attrs = mAttributesWrapper.get(entityId).attrs;
    StatRef hp = attrs.get(Stat.hitpoints);
    StatRef max = attrs.get(Stat.maxhp);
    if (hp == null || max == null || max.asFixed() <= 0f) return 0;
    return (int) (100f * hp.asFixed() / max.asFixed());
  }

  @Override
  public void kill() {
    stopMovement();
    mCasting.remove(entityId);
    mSequence.create(entityId).sequence(Engine.Monster.MODE_DT, Engine.Monster.MODE_DD);
    state = "DEAD";
  }

  @Override
  public String getState() {
    return state;
  }
}
