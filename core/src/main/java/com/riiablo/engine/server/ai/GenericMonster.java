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
      if (!followSummonOwner()) stopMovement();
      return;
    }

    Vector2 target = mPosition.get(targetId).position;
    float distance = outDistance[0];
    int skillSlot = resolveProjectileSkillSlot();
    boolean stationary = monster.monstats != null && monster.monstats.Velocity <= 0;
    float skillRange = skillSlot >= 0 ? resolveSkillRange(skillSlot) : 0f;
    if (skillSlot >= 0 && skillRange > 0f && distance <= skillRange) {
      stopMovement();
      lookAt(targetId);
      state = "CAST";
      if (useMonsterSkill(skillSlot, targetId, target)) return;
      // A malformed skill row must not leave the fallback AI stalled. The
      // normal attack path below remains available when lookup/animation data
      // cannot be resolved.
      state = "ATTACK";
    }

    // D2MOO's stationary traps/nests remain rooted, but a stationary unit with
    // a projectile skill is still allowed to acquire a target and cast. Only
    // suppress the chase/melee fallback after the skill decision has run.
    if (stationary) {
      stopMovement();
      if (state.equals("IDLE")) state = "CAST_WAIT";
      return;
    }

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

  private boolean followSummonOwner() {
    if (!mSummonedPet.has(entityId)) return false;
    com.riiablo.engine.server.component.SummonedPet pet = mSummonedPet.get(entityId);
    if (pet == null || pet.passive || !mPosition.has(pet.ownerId)) return false;
    Vector2 owner = mPosition.get(pet.ownerId).position;
    float distance = mPosition.get(entityId).position.dst(owner);
    if (distance <= 6f) return false;
    state = "FOLLOW";
    return walkTo(owner, pet.ownerId);
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

  /**
   * Finds the first native monster skill that has a server/client missile.
   * Generic fallback AIs must not guess resurrect/heal/buff skills: those need
   * a target/state-specific implementation. Projectile skills, however, can
   * use the same Actioneer -> ServerSkillSystem chain as specialized AIs.
   */
  private int resolveProjectileSkillSlot() {
    if (monster == null || monster.monstats == null) return -1;
    String[] names = {
        monster.monstats.Skill1, monster.monstats.Skill2,
        monster.monstats.Skill3, monster.monstats.Skill4,
        monster.monstats.Skill5, monster.monstats.Skill6,
        monster.monstats.Skill7, monster.monstats.Skill8
    };
    for (int i = 0; i < names.length; i++) {
      String name = names[i];
      if (name == null || name.isEmpty()) continue;
      com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(name);
      if (skill == null || skill.srvdofunc == 97) continue;
      if (hasText(skill.srvmissilea) || hasText(skill.srvmissileb)
          || hasText(skill.srvmissilec) || hasText(skill.srvmissiled)
          || hasText(skill.cltmissilea) || hasText(skill.cltmissileb)
          || hasText(skill.cltmissilec) || hasText(skill.cltmissiled)) {
        return i;
      }
    }
    return -1;
  }

  private float resolveSkillRange(int skillSlot) {
    String name = monsterSkillName(skillSlot);
    if (name == null || name.isEmpty()) return 0f;
    com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(name);
    if (skill == null) return 0f;
    String[] missiles = {skill.srvmissilea, skill.srvmissileb,
        skill.srvmissilec, skill.srvmissiled, skill.cltmissilea,
        skill.cltmissileb, skill.cltmissilec, skill.cltmissiled};
    float range = 0f;
    for (String missileName : missiles) {
      if (!hasText(missileName)) continue;
      Missiles.Entry missile = Riiablo.files.Missiles.get(missileName);
      if (missile != null && missile.Range > 0) range = Math.max(range, missile.Range);
    }
    // Some converted missile rows omit Range. Keep a useful native-like
    // fallback so the monster can still cast instead of silently using melee.
    return range > 0f ? Math.max(2f, range - 2f) : 12f;
  }

  private String monsterSkillName(int index) {
    switch (index) {
      case 0: return monster.monstats.Skill1;
      case 1: return monster.monstats.Skill2;
      case 2: return monster.monstats.Skill3;
      case 3: return monster.monstats.Skill4;
      case 4: return monster.monstats.Skill5;
      case 5: return monster.monstats.Skill6;
      case 6: return monster.monstats.Skill7;
      case 7: return monster.monstats.Skill8;
      default: return null;
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.isEmpty();
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
