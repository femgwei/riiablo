package com.riiablo.engine.server.ai;

import com.badlogic.gdx.math.Vector2;

import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Sequence;

/**
 * Safe fallback for a native monster AI that has not been ported yet.
 *
 * <p>This is intentionally conservative: it does not guess special skills,
 * charge attacks, or pack tactics. It does provide the minimum authoritative
 * loop needed by every spawned monster: acquire a live player, approach using
 * the native movement base, stop at melee/ranged distance, and trigger the
 * normal attack animation/keyframe pipeline.</p>
 */
public final class GenericMonster extends AI {
  private final String nativeAiName;
  private float nextThink;
  private String state = "IDLE";

  protected com.artemis.ComponentMapper<Class> mClass;
  protected com.artemis.ComponentMapper<Monster> mMonster;
  protected com.artemis.ComponentMapper<Position> mPosition;
  protected com.artemis.ComponentMapper<Sequence> mSequence;
  protected com.artemis.ComponentMapper<AttributesWrapper> mAttributesWrapper;

  public GenericMonster(int entityId, String nativeAiName) {
    super(entityId);
    this.nativeAiName = nativeAiName;
  }

  @Override
  public void update(float delta) {
    if (monster == null || !mPosition.has(entityId)) return;
    if (mAttributesWrapper.has(entityId)) {
      com.riiablo.attributes.StatRef hp = mAttributesWrapper.get(entityId).attrs
          .get(com.riiablo.attributes.Stat.hitpoints,
              com.riiablo.attributes.StatRef.obtain());
      if (hp != null && hp.asFixed() <= 0f) return;
    }

    // Native D2 data uses zero velocity for stationary objects (for example
    // Foul Crow nests). Keep those entities rooted instead of feeding them
    // through the generic chase loop, which would otherwise create movement
    // commands and make a static monster appear to slide toward the player.
    if (monster.monstats != null && monster.monstats.Velocity <= 0) {
      state = "IDLE";
      stopMovement();
      return;
    }

    nextThink -= delta;
    if (nextThink > 0f) return;
    nextThink = Math.max(0.15f, SLEEP);

    // Do not replace an attack that is still waiting for its keyframe or
    // finish event. Actioneer will clear Casting/Sequence when it completes.
    if (mCasting.has(entityId) || mSequence.has(entityId)) return;

    float[] outDistance = { Float.MAX_VALUE };
    int targetId = findNearestLiveTarget(outDistance);
    if (targetId == Engine.INVALID_ENTITY) {
      state = "IDLE";
      stopMovement();
      return;
    }

    Vector2 target = mPosition.get(targetId).position;
    float distance = outDistance[0];
    float attackRange = resolveAttackRange();
    if (distance <= attackRange) {
      stopMovement();
      lookAt(targetId);
      state = "ATTACK";
      mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
      mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, target);
      Riiablo.audio.play(monsound + "_attack_1", true);
      return;
    }

    state = "APPROACH";
    walkTo(target, targetId);
  }

  private int findNearestLiveTarget(float[] outDistance) {
    int targetId = findNearestTargetWithAidist(outDistance);
    if (targetId == Engine.INVALID_ENTITY || !mAttributesWrapper.has(targetId)) {
      return Engine.INVALID_ENTITY;
    }
    com.riiablo.attributes.StatRef hp = mAttributesWrapper.get(targetId).attrs
        .get(com.riiablo.attributes.Stat.hitpoints,
            com.riiablo.attributes.StatRef.obtain());
    return hp != null && hp.asFixed() > 0f ? targetId : Engine.INVALID_ENTITY;
  }

  private float resolveAttackRange() {
    float melee = 1f + (monster.monstats2 != null ? monster.monstats2.MeleeRng : 0);
    String missileName = firstNonEmpty(monster.monstats.MissA1, monster.monstats.MissA2);
    if (missileName == null) return melee;
    Missiles.Entry missile = Riiablo.files.Missiles.get(missileName);
    if (missile == null || missile.Range <= 0) return melee;
    return Math.max(melee, missile.Range - 2f);
  }

  private static String firstNonEmpty(String first, String second) {
    if (first != null && !first.isEmpty()) return first;
    if (second != null && !second.isEmpty()) return second;
    return null;
  }

  @Override
  public void kill() {
    stopMovement();
    state = "DEAD";
    mSequence.create(entityId).sequence(Engine.Monster.MODE_DT, Engine.Monster.MODE_DD);
    Riiablo.audio.play(monsound + "_death_1", true);
  }

  @Override
  public String getState() {
    return state + (nativeAiName == null || nativeAiName.isEmpty()
        ? "" : "(fallback:" + nativeAiName + ")");
  }
}
