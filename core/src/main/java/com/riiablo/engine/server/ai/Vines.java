package com.riiablo.engine.server.ai;

import com.badlogic.gdx.math.Vector2;
import com.artemis.Aspect;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;

import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.skill.CorpseConsumption;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * Rooted AI used by the Druid vine summons (SrvDo115).
 *
 * <p>A vine is a stationary summon. It may acquire a hostile unit and use
 * the projectile skill configured by its MonStats row, but it must never run
 * the generic monster chase/melee fallback. That fallback allowed a Plague
 * Poppy to leave its spawn point and request a non-existent walk layer.</p>
 */
public class Vines extends AI {
  private static final Logger log = LogManager.getLogger(Vines.class);
  private float nextThink;
  private String state = "IDLE";

  protected com.artemis.ComponentMapper<Position> mPosition;
  protected com.artemis.ComponentMapper<Sequence> mSequence;
  protected com.artemis.ComponentMapper<AttributesWrapper> mAttributesWrapper;
  private EntitySubscription corpseEntities;

  @Override
  public void initialize() {
    super.initialize();
    if (Riiablo.engine != null) {
      corpseEntities = Riiablo.engine.getAspectSubscriptionManager().get(
          Aspect.all(Corpse.class, Monster.class, Position.class, AttributesWrapper.class));
    }
  }

  public Vines(int entityId) {
    super(entityId);
  }

  @Override
  public void update(float delta) {
    if (monster == null || !mPosition.has(entityId)) return;

    // A rooted summon never retains an old path, including a path left by a
    // pre-fix GenericMonster instance during a live reload.
    stopMovement();
    if (mAttributesWrapper.has(entityId)) {
      com.riiablo.attributes.StatRef hp = mAttributesWrapper.get(entityId).attrs
          .get(com.riiablo.attributes.Stat.hitpoints,
              com.riiablo.attributes.StatRef.obtain());
      if (hp != null && hp.asFixed() <= 0f) return;
    }

    nextThink -= delta;
    if (nextThink > 0f) return;
    nextThink = Math.max(0.15f, SLEEP);

    // Actioneer owns the animation keyframe and clears these components when
    // the cast completes. Do not issue a second cast while one is pending.
    if (mCasting.has(entityId) || mSequence.has(entityId)) return;

    int skillSlot = resolveProjectileSkillSlot();
    if (skillSlot < 0) {
      // A malformed/modded MonStats row must not turn a rooted vine into a
      // melee monster. Keep it inert until the row is fixed.
      state = "IDLE";
      return;
    }

    Skills.Entry projectileSkill = skillForSlot(skillSlot);
    boolean corpseCycler = projectileSkill != null && projectileSkill.srvstfunc == 63;
    float[] outDistance = { Float.MAX_VALUE };
    int targetId = corpseCycler
        ? findNearbyCorpse(outDistance, 20f)
        : findSummonTarget(Engine.INVALID_ENTITY, outDistance, 20f);
    if (targetId == Engine.INVALID_ENTITY || !mPosition.has(targetId)) {
      state = "IDLE";
      return;
    }

    float range = resolveSkillRange(skillSlot);
    if (outDistance[0] > range) {
      state = "WAIT_RANGE";
      return;
    }

    lookAt(targetId);
    state = "CAST";
    if (!useMonsterSkill(skillSlot, targetId, mPosition.get(targetId).position)) {
      state = "WAIT_CAST";
    }
  }

  private Skills.Entry skillForSlot(int skillSlot) {
    if (monster == null || monster.monstats == null || skillSlot < 0 || skillSlot >= 8) return null;
    String[] names = {
        monster.monstats.Skill1, monster.monstats.Skill2,
        monster.monstats.Skill3, monster.monstats.Skill4,
        monster.monstats.Skill5, monster.monstats.Skill6,
        monster.monstats.Skill7, monster.monstats.Skill8
    };
    String name = names[skillSlot];
    return name == null || name.isEmpty() ? null : Riiablo.files.skills.get(name);
  }

  private int findNearbyCorpse(float[] outDistance, float maxRange) {
    if (corpseEntities == null || !mPosition.has(entityId) || maxRange <= 0f) {
      return Engine.INVALID_ENTITY;
    }
    Vector2 source = mPosition.get(entityId).position;
    float bestDistance2 = maxRange * maxRange;
    int best = Engine.INVALID_ENTITY;
    IntBag entities = corpseEntities.getEntities();
    for (int i = 0; i < entities.size(); i++) {
      int candidateId = entities.get(i);
      if (candidateId == entityId || !mPosition.has(candidateId)
          || !mMonster.has(candidateId) || !mCorpse.has(candidateId)
          || !mAttributesWrapper.has(candidateId)) continue;
      if (mMapWrapper.has(entityId) && mMapWrapper.has(candidateId)) {
        com.riiablo.map.Map.Zone sourceZone = mMapWrapper.get(entityId).zone;
        com.riiablo.map.Map.Zone corpseZone = mMapWrapper.get(candidateId).zone;
        if (sourceZone != null && corpseZone != null && sourceZone != corpseZone) continue;
      }
      com.riiablo.attributes.Attributes attrs = mAttributesWrapper.get(candidateId).attrs;
      com.riiablo.engine.server.component.UnitStates unitStates =
          mUnitStates.has(candidateId) ? mUnitStates.get(candidateId) : null;
      com.riiablo.engine.server.state.StateList states =
          unitStates != null ? unitStates.stateList : null;
      if (!CorpseConsumption.selectable(mCorpse.get(candidateId), mMonster.get(candidateId),
          attrs, states)) continue;
      float distance2 = source.dst2(mPosition.get(candidateId).position);
      if (distance2 > bestDistance2) continue;
      bestDistance2 = distance2;
      best = candidateId;
    }
    if (best != Engine.INVALID_ENTITY) {
      outDistance[0] = (float) Math.sqrt(bestDistance2);
      log.info("[VINE_CORPSE] phase=search source={} range={} selected={} distance={}",
          entityId, maxRange, best, outDistance[0]);
    }
    return best;
  }

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
    String name;
    switch (skillSlot) {
      case 0: name = monster.monstats.Skill1; break;
      case 1: name = monster.monstats.Skill2; break;
      case 2: name = monster.monstats.Skill3; break;
      case 3: name = monster.monstats.Skill4; break;
      case 4: name = monster.monstats.Skill5; break;
      case 5: name = monster.monstats.Skill6; break;
      case 6: name = monster.monstats.Skill7; break;
      case 7: name = monster.monstats.Skill8; break;
      default: return 0f;
    }
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
    // Converted rows occasionally omit Range. Keep the conservative fallback
    // used by the generic projectile resolver rather than making it inert.
    return range > 0f ? Math.max(2f, range - 2f) : 12f;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isEmpty();
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
