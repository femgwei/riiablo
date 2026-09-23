package com.riiablo.engine.server.ai;

import com.badlogic.gdx.math.Vector2;

import com.riiablo.Riiablo;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Sequence;

/**
 * Native-style Blood Raven AI (D2MOO AITHINK_Fn059_BloodRaven).
 *
 * <p>Blood Raven is not a generic ranged monster.  She has a leash around
 * her spawn point, moves back to that point when pulled too far away, and
 * periodically chooses the second monster skill (Quick Strike) instead of
 * the ordinary bow attack.  Keeping this state here also makes her behavior
 * deterministic and prevents the missing-class fallback from changing her
 * attack cadence.
 */
public final class BloodRaven extends AI {
  private static final float TARGET_LEASH = 45f;
  private static final float ANCHOR_LEASH = 50f;
  private static final float ANCHOR_STOP = 5f;
  private static final float CLOSE_DISTANCE = 5f;
  private static final int QUICK_STRIKE_SLOT = 1; // MonStats Skill2

  private final Vector2 anchor = new Vector2();
  private float nextThink;
  private int specialUses;
  private String state = "IDLE";

  protected com.artemis.ComponentMapper<Monster> mMonster;
  protected com.artemis.ComponentMapper<Position> mPosition;
  protected com.artemis.ComponentMapper<Sequence> mSequence;
  protected com.artemis.ComponentMapper<AttributesWrapper> mAttributesWrapper;

  public BloodRaven(int entityId) {
    super(entityId);
  }

  @Override
  public void initialize() {
    super.initialize();
    if (mPosition.has(entityId)) anchor.set(mPosition.get(entityId).position);
    nextThink = 0f;
    specialUses = 0;
  }

  @Override
  public void update(float delta) {
    if (monster == null || !mPosition.has(entityId) || !isLive(entityId)) return;
    if (mCasting.has(entityId) || mSequence.has(entityId)) return;

    nextThink -= delta;
    if (nextThink > 0f) return;
    nextThink = Math.max(0.15f, SLEEP);

    float[] outDistance = {Float.MAX_VALUE};
    int targetId = findNearestTargetWithAidist(outDistance);
    if (targetId == Engine.INVALID_ENTITY || !isLive(targetId)) {
      stopMovement();
      state = "IDLE";
      return;
    }

    float targetDistance = outDistance[0];
    Vector2 position = mPosition.get(entityId).position;
    Vector2 target = mPosition.get(targetId).position;

    // Native AI idles when the target is outside its 45-unit combat leash.
    if (targetDistance > TARGET_LEASH) {
      stopMovement();
      state = "IDLE";
      return;
    }

    // Keep the boss close to its native spawn point.  The original uses a
    // running path for this branch, not an unrestricted chase.
    float anchorDistance = position.dst(anchor);
    if (anchorDistance >= ANCHOR_LEASH) {
      state = "RETURN";
      runTo(anchor, 100, Engine.INVALID_ENTITY);
      return;
    }
    if (anchorDistance > ANCHOR_STOP) {
      state = "RETURN";
      runTo(anchor, 100, Engine.INVALID_ENTITY);
      return;
    }

    if (targetDistance > 20f) {
      // Native Blood Raven closes only part of the gap before deciding again.
      state = "APPROACH";
      runTo(target, 100, targetId);
      return;
    }

    lookAt(targetId);

    // Skill2 is Quick Strike in the 1.10 data.  Native chance is
    // 10 * (difficulty + 4); normal difficulty therefore uses 40%.
    if (targetDistance > CLOSE_DISTANCE
        && hasSkill(QUICK_STRIKE_SLOT)
        && specialUses < quickStrikeLimit()
        && rollAiChance(quickStrikeChance())) {
      // The 1.10 data names a dedicated BR sequence (XX), but a number of
      // reduced asset sets do not contain CRXXBOW.cof.  Use the native bow
      // attack mode as a presentation-safe fallback; SrvSt50/SrvDo092 and
      // the authoritative missile row still provide Quick Strike behavior.
      if (useMonsterSkill(QUICK_STRIKE_SLOT, targetId, target.cpy(),
          Engine.Monster.MODE_A1)) {
        specialUses++;
        state = "QUICK_STRIKE";
        return;
      }
    }

    if (targetDistance > CLOSE_DISTANCE) {
      // Ranged normal attack.  The regular Actioneer keyframe path will pick
      // the native MissA1/MissA2 missile and calculate hit/damage normally.
      state = "ATTACK";
      mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
      mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, target);
      com.riiablo.audio.MonsterAudio.play(entityId, monsound + "_attack_1", true);
      return;
    }

    // The native close-range branch still uses the ordinary attack pipeline.
    state = "ATTACK";
    mSequence.create(entityId).sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
    mCasting.create(entityId).set(com.riiablo.skill.SkillCodes.attack, targetId, target);
    com.riiablo.audio.MonsterAudio.play(entityId, monsound + "_attack_1", true);
  }

  private boolean isLive(int entityId) {
    if (!mAttributesWrapper.has(entityId)) return true;
    com.riiablo.attributes.StatRef hp = mAttributesWrapper.get(entityId).attrs.get(
        com.riiablo.attributes.Stat.hitpoints, com.riiablo.attributes.StatRef.obtain());
    return hp == null || hp.asFixed() > 0f;
  }

  private boolean hasSkill(int slot) {
    String name;
    switch (slot) {
      case 0: name = monster.monstats.Skill1; break;
      case 1: name = monster.monstats.Skill2; break;
      case 2: name = monster.monstats.Skill3; break;
      default: return false;
    }
    return name != null && !name.isEmpty() && Riiablo.files.skills.get(name) != null;
  }

  private int difficulty() {
    if (!mMapWrapper.has(entityId) || mMapWrapper.get(entityId).map == null) return 0;
    return Math.max(0, Math.min(2, mMapWrapper.get(entityId).map.getDifficulty()));
  }

  private int quickStrikeChance() {
    return Math.min(100, 10 * (difficulty() + 4));
  }

  private int quickStrikeLimit() {
    return 2 * difficulty() + 8;
  }

  @Override
  public void kill() {
    stopMovement();
    state = "DEAD";
    mSequence.create(entityId).sequence(Engine.Monster.MODE_DT, Engine.Monster.MODE_DD);
    com.riiablo.audio.MonsterAudio.play(entityId, monsound + "_death_1", true);
  }

  @Override
  public String getState() {
    return state;
  }
}
