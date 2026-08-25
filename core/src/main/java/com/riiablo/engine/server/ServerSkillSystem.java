package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.SkillCastEvent;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.item.Item;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.skill.SkillCodes;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.IntSet;

/**
 * Server-authoritative part of the player skill pipeline.
 *
 * <p>Actioneer owns animation state and emits skill events. This system keeps
 * validation and projectile creation on the server, while the client remains
 * responsible for presentation. Effects are created on {@link SkillDoEvent}
 * so a projectile cannot be spawned before the cast animation reaches its
 * active frame.</p>
 */
public class ServerSkillSystem extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(ServerSkillSystem.class);
  private static final float MULTI_MISSILE_SPREAD_RADIANS = 0.12f;
  private static final int NOVA_MISSILE_COUNT = 64;
  private final boolean monstersOnly;

  public ServerSkillSystem() {
    this(false);
  }

  /** Local games use this mode because legacy player presentation owns its projectiles. */
  public ServerSkillSystem(boolean monstersOnly) {
    this.monstersOnly = monstersOnly;
  }

  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Missile> mMissile;

  /** Registered as "factory" by D2GS. */
  @Wire(name = "factory")
  protected EntityFactory factory;

  @Subscribe
  public void onSkillCast(SkillCastEvent event) {
    if (monstersOnly) return;
    // Monsters use their existing AI/casting path and do not have player mana.
    if (!mPlayer.has(event.entityId)) return;

    Skills.Entry skill = Riiablo.files.skills.get(event.skillId);
    if (skill == null) {
      reject(event, 7, "skill data is missing");
      return;
    }

    Player player = mPlayer.get(event.entityId);
    int skillLevel = player.data != null ? player.data.getSkill(event.skillId) : 1;
    skillLevel = Math.max(1, skillLevel);
    int casterLevel = 1;
    Attributes attrs = mAttributesWrapper.has(event.entityId)
        ? mAttributesWrapper.get(event.entityId).attrs : null;
    if (attrs == null) {
      reject(event, 7, "caster attributes are missing");
      return;
    }
    StatRef level = attrs.get(Stat.level, StatRef.obtain());
    if (level != null) casterLevel = Math.max(1, level.asInt());
    if (casterLevel < skill.reqlevel) {
      reject(event, 5, "caster level is below skill requirement");
      return;
    }

    StatRef mana = attrs.get(Stat.mana, StatRef.obtain());
    if (mana == null) {
      reject(event, 1, "caster has no mana stat");
      return;
    }

    float manaCost = getManaCost(skill, skillLevel);
    event.manaCost = manaCost;
    if (manaCost > 0 && mana.asFixed() + 0.0001f < manaCost) {
      reject(event, 1, "not enough mana");
      return;
    }
    if (manaCost > 0) {
      mana.sub(manaCost);
      log.debug("Server skill accepted: entity={}, skill={}, level={}, manaCost={}, manaLeft={}",
          event.entityId, event.skillId, skillLevel, manaCost, mana.asFixed());
    }
  }

  @Subscribe
  public void onSkillDo(SkillDoEvent event) {
    if (!mPosition.has(event.entityId)) return;
    if (!mPlayer.has(event.entityId) && !mMonster.has(event.entityId)) return;
    if (monstersOnly && !mMonster.has(event.entityId)) return;
    Skills.Entry skill = Riiablo.files.skills.get(event.skillId);
    if (skill == null) return;
    int skillLevel = getSkillLevel(event.entityId, event.skillId);

    Vector2 start = mPosition.get(event.entityId).position;
    if (event.srvdofunc == 22 || skill.srvdofunc == 22) {
      spawnNova(event, skill, start);
      return;
    }
    if (event.srvdofunc == 8 || skill.srvdofunc == 8) {
      spawnMultipleShotTeethShockWave(event, skill, start);
      return;
    }

    Vector2 target = new Vector2();
    if (event.targetId >= 0 && mPosition.has(event.targetId)) {
      target.set(mPosition.get(event.targetId).position);
    } else if (event.targetVec != null) {
      target.set(event.targetVec);
    } else {
      target.set(start).add(1, 0);
    }
    target.sub(start);
    if (target.isZero(0.0001f)) target.set(1, 0);
    target.nor();

    String throwableMissile = resolveThrowableMissile(event.entityId, event.skillId, skill);
    boolean hasServerMissile = hasText(skill.srvmissilea) || hasText(skill.srvmissileb)
        || hasText(skill.srvmissilec) || hasText(skill.srvmissiled);
    // Client missiles are presentation fallbacks, not additional
    // authoritative projectiles.  Mixing cltMissileB with srvMissileA made
    // skills such as FetishInferno spawn an extra damaging stream.
    String[] missileNames = hasServerMissile
        ? new String[] {skill.srvmissilea, skill.srvmissileb,
            skill.srvmissilec, skill.srvmissiled}
        : new String[] {skill.cltmissilea, skill.cltmissileb,
            skill.cltmissilec, skill.cltmissiled};
    if ((event.srvdofunc == 85 || skill.srvdofunc == 85) && mMonster.has(event.entityId)) {
      missileNames[0] = resolveMonsterChainMissile(mMonster.get(event.entityId), missileNames[0]);
    }
    int configuredCount = 0;
    for (String name : missileNames) {
      if (name != null && !name.isEmpty()) configuredCount++;
    }
    if (configuredCount == 0 && throwableMissile != null && !throwableMissile.isEmpty()) {
      missileNames[0] = throwableMissile;
      configuredCount = 1;
    }
    if (event.skillId == SkillCodes.throw_ || event.skillId == SkillCodes.left_hand_throw
        || event.srvdofunc == 3 || event.srvdofunc == 5) {
      log.info("[THROW_ATTACK] phase=missile_resolve entity={} skill={} srvDoFunc={} "
              + "weaponCode={} configuredMissiles={} missileA={} missileB={} missileC={} missileD={}",
          event.entityId, event.skillId, event.srvdofunc, throwableMissile, configuredCount,
          missileNames[0], missileNames[1], missileNames[2], missileNames[3]);
    }
    if (configuredCount == 0) return;

    IntSet sharedHitTargets = configuredCount > 1 ? new IntSet() : null;
    int ordinal = 0;
    for (String missileName : missileNames) {
      if (missileName == null || missileName.isEmpty()) continue;
      Missiles.Entry missile = Riiablo.files.Missiles.get(missileName);
      if (missile == null) {
        log.warn("Server skill missile lookup failed: entity={}, skill={}, missile={}",
            event.entityId, event.skillId, missileName);
        continue;
      }

      Vector2 direction = new Vector2(target);
      if (configuredCount > 1) {
        float offset = (ordinal - (configuredCount - 1) * 0.5f)
            * MULTI_MISSILE_SPREAD_RADIANS;
        direction.rotateRad(offset);
      }
      int missileId = createMissile(missile, direction, start, event.entityId,
          sharedHitTargets, skillLevel);
      if (missileId < 0) {
        log.warn("[MISSILE_CREATE] phase=failed entity={} owner={} skillId={} missile={}",
            event.entityId, event.entityId, event.skillId, missile.Missile);
        ordinal++;
        continue;
      }
      if (event.skillId == SkillCodes.throw_ || event.skillId == SkillCodes.left_hand_throw
          || event.srvdofunc == 3 || event.srvdofunc == 5) {
        log.info("[MISSILE_CREATE] phase=throw entity={} missileId={} owner={} missile={} "
                + "speed={} range={} start=({}, {}) direction=({}, {})", event.entityId,
            missileId, event.entityId, missile.Missile, missile.Vel, missile.Range,
            start.x, start.y, direction.x, direction.y);
      }
      log.debug("Server skill projectile: entity={}, skill={}, missile={}, entityId={}, dir=({}, {})",
          event.entityId, event.skillId, missileName, missileId, direction.x, direction.y);
      if (mMonster.has(event.entityId)) {
        log.info("[MONSTER_SKILL] phase=missile entity={} skillId={} missileId={} missile={} "
                + "speed={} range={} direction=({}, {})",
            event.entityId, event.skillId, missileId, missile.Missile,
            missile.Vel, missile.Range, direction.x, direction.y);
      }
      ordinal++;
    }
  }

  private void spawnNova(SkillDoEvent event, Skills.Entry skill, Vector2 start) {
    String missileName = firstNonEmpty(skill.srvmissilea, skill.cltmissilea);
    if (missileName == null) {
      log.warn("Server nova has no missile configured: entity={}, skill={}",
          event.entityId, event.skillId);
      return;
    }
    Missiles.Entry missile = Riiablo.files.Missiles.get(missileName);
    if (missile == null) {
      log.warn("Server nova missile lookup failed: entity={}, skill={}, missile={}",
          event.entityId, event.skillId, missileName);
      return;
    }

    IntSet sharedHitTargets = new IntSet();
    int skillLevel = getSkillLevel(event.entityId, event.skillId);
    Vector2 direction = new Vector2();
    int created = 0;
    for (int i = 0; i < NOVA_MISSILE_COUNT; i++) {
      radialDirection(i, NOVA_MISSILE_COUNT, direction);
      if (createMissile(missile, direction, start, event.entityId,
          sharedHitTargets, skillLevel) >= 0) {
        created++;
      }
    }
    log.debug("Server nova projectiles: entity={}, skill={}, missile={}, created={}",
        event.entityId, event.skillId, missileName, created);
  }

  /**
   * D2MOO's SKILLS_SrvDo008_MultipleShot_Teeth_ShockWave.  The native
   * implementation evaluates calc1 as the total count and calc3 as the
   * centre group (calc2 is the missile activation frame), then emits
   * left/centre/right groups along a perpendicular
   * target offset.  The entity factory currently accepts a direction rather
   * than an explicit target point, so the same lane layout is represented by
   * a deterministic narrow fan of directions.
   */
  private void spawnMultipleShotTeethShockWave(SkillDoEvent event, Skills.Entry skill,
      Vector2 start) {
    Vector2 target = new Vector2();
    if (event.targetId >= 0 && mPosition.has(event.targetId)) {
      target.set(mPosition.get(event.targetId).position);
    } else if (event.targetVec != null) {
      target.set(event.targetVec);
    } else {
      target.set(start).add(1, 0);
    }
    target.sub(start);
    if (target.isZero(0.0001f)) target.set(1, 0);
    target.nor();

    int skillLevel = getSkillLevel(event.entityId, event.skillId);
    int total = SkillFormula.evaluate(skill.calc1, skill, skillLevel);
    if (total <= 0) total = firstParam(skill, 1, 1);
    total = Math.max(1, Math.min(64, total));

    int centre = SkillFormula.evaluate(skill.calc3, skill, skillLevel);
    if (centre <= 0) centre = total;
    centre = Math.max(0, Math.min(total, centre));

    int left = (total - centre) / 2;
    int right = total - left - centre;
    String missileName = selectSrvDo008Missile(event.entityId, skill);
    if (missileName == null) {
      log.warn("SrvDo008 has no missile configured: entity={}, skill={}, total={}, centre={}",
          event.entityId, event.skillId, total, centre);
      return;
    }
    Missiles.Entry missile = Riiablo.files.Missiles.get(missileName);
    if (missile == null) {
      log.warn("SrvDo008 missile lookup failed: entity={}, skill={}, missile={}",
          event.entityId, event.skillId, missileName);
      return;
    }

    IntSet sharedHitTargets = total > 1 ? new IntSet() : null;
    Vector2 direction = new Vector2();
    int created = 0;
    for (int i = 0; i < total; i++) {
      fanDirection(target, i, total, direction);
      if (createMissile(missile, direction, start, event.entityId, sharedHitTargets,
          skillLevel) >= 0) {
        created++;
      }
    }
    log.debug("Server SrvDo008 projectiles: entity={}, skill={}, missile={}, level={}, total={}, "
            + "left={}, centre={}, right={}, created={}",
        event.entityId, event.skillId, missileName, skillLevel, total, left, centre, right, created);
  }

  private String selectSrvDo008Missile(int entityId, Skills.Entry skill) {
    // D2MOO selects wSrvMissileB for every weapon class except HTH.  The
    // component is absent for old/remote entities, where HTH is the safe
    // native default.
    boolean nonHandToHand = mCofReference != null && mCofReference.has(entityId)
        && mCofReference.get(entityId).wclass != Engine.WEAPON_HTH;
    if (nonHandToHand) {
      String missile = firstNonEmpty(skill.srvmissileb, skill.srvmissilea);
      if (missile != null) return missile;
    }
    return firstNonEmpty(skill.srvmissilea, skill.cltmissilea);
  }

  static int firstParam(Skills.Entry skill, int index, int fallback) {
    if (skill == null || skill.Param == null || index < 1 || index > skill.Param.length) return fallback;
    return skill.Param[index - 1];
  }

  static int getSrvDo008Total(Skills.Entry skill, int skillLevel) {
    int total = SkillFormula.evaluate(skill != null ? skill.calc1 : null, skill, skillLevel);
    return total > 0 ? Math.min(64, total) : Math.max(1, firstParam(skill, 1, 1));
  }

  static int getSrvDo008Centre(Skills.Entry skill, int skillLevel, int total) {
    int centre = SkillFormula.evaluate(skill != null ? skill.calc3 : null, skill, skillLevel);
    return Math.max(0, Math.min(total, centre > 0 ? centre : total));
  }

  static Vector2 fanDirection(Vector2 base, int index, int count, Vector2 out) {
    if (count <= 1) return out.set(base).nor();
    float offset = (index - (count - 1) * 0.5f) * MULTI_MISSILE_SPREAD_RADIANS;
    return out.set(base).rotateRad(offset).nor();
  }

  private int createMissile(Missiles.Entry missile, Vector2 direction, Vector2 start,
      int ownerId, IntSet sharedHitTargets, int damageLevel) {
    if (factory == null) return -1;
    int missileId = factory.createMissile(missile, direction, start, ownerId);
    if (missileId >= 0 && mMissile.has(missileId)) {
      Missile projectile = mMissile.get(missileId);
      if (sharedHitTargets != null) projectile.shareHitTargets(sharedHitTargets);
      Attributes ownerAttrs = mAttributesWrapper.has(ownerId)
          ? mAttributesWrapper.get(ownerId).attrs : null;
      Monster ownerMonster = mMonster.has(ownerId) ? mMonster.get(ownerId) : null;
      int ownerMode = mCofReference.has(ownerId) ? mCofReference.get(ownerId).mode : -1;
      MissileDamageResolver.initialize(projectile, ownerAttrs, ownerMonster,
          ownerMode, damageLevel, 0);
    }
    return missileId;
  }

  static Vector2 radialDirection(int index, int count, Vector2 out) {
    if (count <= 0) return out.setZero();
    float radians = MathUtils.PI2 * index / count;
    return out.set(MathUtils.cos(radians), MathUtils.sin(radians)).nor();
  }

  private static String firstNonEmpty(String primary, String fallback) {
    if (primary != null && !primary.isEmpty()) return primary;
    if (fallback != null && !fallback.isEmpty()) return fallback;
    return null;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isEmpty();
  }

  /**
   * Native SrvDo085 adds MonStats' derived chain id to srvMissileA.  The Java
   * table does not store that derived field, so reproduce it from the
   * BaseId/NextInClass chain generated by D2Common.
   */
  static String resolveMonsterChainMissile(Monster monster, String baseMissileName) {
    if (monster == null || monster.monstats == null || !hasText(baseMissileName)) {
      return baseMissileName;
    }
    Missiles.Entry baseMissile = Riiablo.files.Missiles.get(baseMissileName);
    if (baseMissile == null) return baseMissileName;
    int chainId = getMonsterChainId(monster.monstats);
    Missiles.Entry resolved = Riiablo.files.Missiles.get(baseMissile.Id + chainId);
    if (resolved == null) return baseMissileName;
    log.info("[SHAMAN_FIREBALL] phase=missile_resolve monster={} baseMissile={} "
            + "chainId={} missile={} missileId={} celFile={}",
        monster.monstats.Id, baseMissileName, chainId, resolved.Missile,
        resolved.Id, resolved.CelFile);
    return resolved.Missile;
  }

  static int getMonsterChainId(MonStats.Entry monster) {
    if (monster == null || !hasText(monster.Id)) return 0;
    String baseId = hasText(monster.BaseId) ? monster.BaseId : monster.Id;
    MonStats.Entry cursor = Riiablo.files.monstats.get(baseId);
    for (int chainId = 0; cursor != null && chainId < 256; chainId++) {
      if (monster.Id.equalsIgnoreCase(cursor.Id)) return chainId;
      if (!hasText(cursor.NextInClass) || cursor.NextInClass.equalsIgnoreCase(cursor.Id)) break;
      cursor = Riiablo.files.monstats.get(cursor.NextInClass);
    }
    return 0;
  }

  private float getManaCost(Skills.Entry skill, int level) {
    int shift = Math.max(0, Math.min(30, skill.manashift));
    float base = (1 << shift) / 256f * skill.mana;
    return Math.max(0, base + skill.lvlmana * level);
  }

  private int getSkillLevel(int entityId, int skillId) {
    if (mPlayer.has(entityId) && mPlayer.get(entityId).data != null) {
      return Math.max(1, mPlayer.get(entityId).data.getSkill(skillId));
    }
    if (mMonster.has(entityId)) {
      Monster monster = mMonster.get(entityId);
      if (monster.monstats != null) {
        String skillName = Riiablo.files.skills.get(skillId) != null
            ? Riiablo.files.skills.get(skillId).skill : null;
        String[] names = {
            monster.monstats.Skill1, monster.monstats.Skill2,
            monster.monstats.Skill3, monster.monstats.Skill4,
            monster.monstats.Skill5, monster.monstats.Skill6,
            monster.monstats.Skill7, monster.monstats.Skill8
        };
        int[] levels = {
            monster.monstats.Sk1lvl, monster.monstats.Sk2lvl,
            monster.monstats.Sk3lvl, monster.monstats.Sk4lvl,
            monster.monstats.Sk5lvl, monster.monstats.Sk6lvl,
            monster.monstats.Sk7lvl, monster.monstats.Sk8lvl
        };
        for (int i = 0; i < names.length; i++) {
          if (skillName != null && skillName.equals(names[i])) return Math.max(1, levels[i]);
        }
      }
    }
    return 1;
  }

  private String resolveThrowableMissile(int entityId, int skillId, Skills.Entry skill) {
    if (skillId != SkillCodes.throw_ && skillId != SkillCodes.left_hand_throw
        && skill.srvdofunc != 3 && skill.srvdofunc != 5) {
      return null;
    }
    if (!mPlayer.has(entityId)) return null;
    Player player = mPlayer.get(entityId);
    if (player.data == null || player.data.getItems() == null) return null;
    Item weapon = player.data.getItems().getEquippedThrowableWeapon();
    if (weapon == null || !hasText(weapon.code)) return null;

    // Item codes (for example "jav") are not necessarily Missiles.txt row
    // names.  Use the same native-data candidates as the presentation path,
    // but only return a name that actually resolves on the authoritative
    // server.  Returning the raw item code made Throw consume quantity while
    // silently failing to create a missile.
    String[] candidates = {
        weapon.code,
        weapon.code + "s",
        "electric" + weapon.code,
        "electric " + weapon.code,
        "throwing" + weapon.code,
        weapon.code + "throw",
        "javelin",
        "javelins"
    };
    for (String candidate : candidates) {
      Missiles.Entry missile = Riiablo.files.Missiles.get(candidate);
      if (missile != null) return missile.Missile;
    }
    log.warn("[THROW_ATTACK] phase=missile_resolve_failed entity={} skill={} weaponCode={}",
        entityId, skillId, weapon.code);
    return null;
  }

  private void reject(SkillCastEvent event, int resultCode, String reason) {
    event.accepted = false;
    event.resultCode = resultCode;
    log.debug("Server skill rejected: entity={}, skill={}, resultCode={}, reason={}",
        event.entityId, event.skillId, resultCode, reason);
  }
}
