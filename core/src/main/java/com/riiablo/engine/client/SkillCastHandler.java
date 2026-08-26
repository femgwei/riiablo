package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ObjectMap;

import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Direction;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.ServerSkillSystem;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.SkillCastEvent;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.event.SkillStartEvent;
import com.riiablo.item.Item;
import com.riiablo.item.Type;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.skill.SkillCodes;

public class SkillCastHandler extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(SkillCastHandler.class);

  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Monster> mMonster;

  protected OverlayManager overlays;

  @Wire(name = "factory")
  protected EntityFactory factory;

  private final Vector2 tmpVec = new Vector2();

  @Subscribe
  public void onSkillCast(SkillCastEvent event) {
  }

  private static final ObjectMap<String, String> HITCLASS = new ObjectMap<>();
  static {
    HITCLASS.put("1hss", "weapon_1hs_small_1");
    HITCLASS.put("1hsl", "weapon_1hs_large_1");
    HITCLASS.put("2hss", "weapon_2hs_small_1");
    HITCLASS.put("2hsl", "weapon_2hs_large_1");
  }

  @Subscribe
  public void srvstfunc(SkillStartEvent event) {
    log.traceEntry("srvstfunc(entityId: {}, srvstfunc: {}, targetId: {}, targetVec: {})",
        event.entityId, event.srvstfunc, event.targetId, event.targetVec);
    switch (event.srvstfunc) {
      case 0:
        break;
      case 1: // attack
      case 2: // kick
      case 3: // throw
      case 4: // unsummon
      case 5: // left hand throw
      case 6: // left hand swing
        break;
      case 65: // Throw skill (skillId=2) - same as case 3
        break;
      case 42: // Fire Hit performs its native pre-hit setup on the server
        break;
      case 40: // Leap landing/path setup is server-authoritative
        break;
      default:
        log.warn("Unsupported srvstfunc({}) for {} casting {}", event.srvstfunc, event.entityId, event.skillId);
    }
  }

  @Subscribe
  public void srvdofunc(SkillDoEvent event) {
    log.traceEntry("srvdofunc(entityId: {}, srvdofunc: {}, targetId: {}, targetVec: {})",
        event.entityId, event.srvdofunc, event.targetId, event.targetVec);
    switch (event.srvdofunc) {
      case 0:
        break;
      case 1: // attack
        break;
      case 68: // shouts
        Riiablo.audio.play("barbarian_circle_1", true);
        break;
      default:
        // Server-side functions don't need client handling for most cases
        break;
    }
  }

  @Subscribe
  public void cltstfunc(SkillStartEvent event) {
    log.traceEntry("cltstfunc(entityId: {}, cltstfunc: {}, targetId: {}, targetVec: {})",
        event.entityId, event.cltstfunc, event.targetId, event.targetVec);
    final Skills.Entry skill = Riiablo.files.skills.get(event.skillId);
    if (skill == null) {
      log.warn("[SKILL_PRESENTATION] phase=start_missing_skill entity={} skillId={}",
          event.entityId, event.skillId);
      return;
    }

    String presentationMarker = mMonster.has(event.entityId)
        ? "[MONSTER_SKILL_PRESENTATION]" : "[SKILL_PRESENTATION]";
    log.info("{} phase=start entity={} skillId={} skill={} "
            + "stsound={} castoverlay={} srvstfunc={} cltstfunc={}",
        presentationMarker, event.entityId, event.skillId, skill.skill,
        skill.stsound, skill.castoverlay, event.srvstfunc, event.cltstfunc);
    if (skill.stsound == null || skill.stsound.isEmpty()) {
      log.debug("{} phase=sound_skipped entity={} skill={} reason=empty_stsound",
          presentationMarker,
          event.entityId, skill.skill);
    } else {
      Riiablo.audio.play(skill.stsound, true);
    }

    if (skill.castoverlay != null && !skill.castoverlay.isEmpty()) {
      overlays.set(event.entityId, skill.castoverlay);
      log.info("{} phase=overlay_requested entity={} skill={} overlay={}",
          presentationMarker,
          event.entityId, skill.skill, skill.castoverlay);
    } else {
      log.debug("{} phase=overlay_skipped entity={} skill={} reason=empty_castoverlay",
          presentationMarker,
          event.entityId, skill.skill);
    }

    switch (event.cltstfunc) {
      case 0:
        break;
      case 1: // Generic start function
        break;
      case 2: // Channeled skills (inferno, arctic blast)
        // Start channeling effect
        break;
      case 3: // Aura toggle
        // Visual aura effect
        break;
      default:
        log.warn("Unsupported cltstfunc({}) for {} casting {}", event.cltstfunc, event.entityId, event.skillId);
    }
  }

  @Subscribe
  public void cltdofunc(SkillDoEvent event) {
    log.traceEntry("cltdofunc(entityId: {}, cltdofunc: {}, targetId: {}, targetVec: {})",
        event.entityId, event.cltdofunc, event.targetId, event.targetVec);
    final Skills.Entry skill = Riiablo.files.skills.get(event.skillId);
    if (skill == null) {
      log.warn("Skill {} not found for cltdofunc, skipping", event.skillId);
      return;
    }
    Riiablo.audio.play(skill.dosound, true);

    Vector2 position = mPosition.has(event.entityId) ? mPosition.get(event.entityId).position : null;

    // In a local game ServerSkillSystem creates and renders the authoritative
    // monster missile in this same ECS world.  Do not create a second,
    // ownerless client copy at the same keyframe.  Network clients do not
    // have ServerSkillSystem and retain the presentation path below.
    if (mMonster.has(event.entityId)
        && world.getSystem(ServerSkillSystem.class) != null
        && hasServerMissile(skill)) {
      log.info("[MONSTER_SKILL] phase=client_visual_reuses_server entity={} skill={} srvDoFunc={}",
          event.entityId, skill.skill, skill.srvdofunc);
      return;
    }

    // In the shipped Skills.txt data Throw and Left Hand Throw use
    // cltdofunc=2, while their authoritative server functions are 3 and 5.
    // cltdofunc=2 is therefore not sufficient to classify the action as a
    // kick/melee presentation.  Route the explicit throw skills first so the
    // client creates the visible javelin/throwing-weapon missile as soon as
    // the server reaches the skill-do keyframe.
    if (event.skillId == SkillCodes.throw_ || event.skillId == SkillCodes.left_hand_throw
        || event.srvdofunc == 3 || event.srvdofunc == 5) {
      log.info("[THROW_VISUAL] entity={} skill={} srvDoFunc={} cltDoFunc={} position=({}, {})",
          event.entityId, event.skillId, event.srvdofunc, event.cltdofunc,
          position != null ? position.x : Float.NaN,
          position != null ? position.y : Float.NaN);
      cltDoThrowMissile(event, skill, position);
      return;
    }

    switch (event.cltdofunc) {
      case 0:
        break;

      case 1: // Attack - play weapon swing sound
        String hitClass = getHitClassSound(event.entityId);
        Riiablo.audio.play(hitClass, true);
        break;

      case 2: // Kick / melee hit with specific sound
        // Explicit Throw/Left Hand Throw has already been handled above.
        // Keep this branch for genuine non-throwing skills that use the same
        // client function number.
        Riiablo.audio.play("weapon_1hs_small_1", true);
        break;

      case 3: // Throw - create thrown missile visual
        // Older/custom skill tables may still use cltdofunc=3 directly.
        cltDoThrowMissile(event, skill, position);
        break;

      case 4: // Unsummon visual effect
        break;

      case 5: // Left hand throw
        cltDoThrowMissile(event, skill, position);
        break;

      case 6: // Inner Sight / Slow Missiles - debuff aura visual
        cltDoDebuffAuraVisual(event, skill, position);
        break;

      case 7: // Jab - multiple hit sounds
        for (int i = 0; i < 3; i++) {
          Riiablo.audio.play("weapon_1hs_small_1", true);
        }
        break;

      case 8: // Multiple Shot / Teeth - fan missiles
        cltDoFanMissiles(event, skill, position);
        break;

      case 9: // Frenzy - dual weapon sounds
        Riiablo.audio.play("weapon_1hs_small_1", true);
        Riiablo.audio.play("weapon_1hs_small_1", true);
        break;

      case 10: // Guided Arrow / Bone Spirit - homing missile
        cltDoSingleMissile(event, skill, position);
        break;

      case 11: // Charged Strike - melee with lightning effect
        Riiablo.audio.play("weapon_1hs_small_1", true);
        // TODO: spawn charged bolt visuals from hit position
        break;

      case 12: // Strafe - rapid arrow visuals
        cltDoStrafeMissiles(event, skill, position);
        break;

      case 13: // Zeal / Fend / Fury - multi-hit sounds
        for (int i = 0; i < 4; i++) {
          Riiablo.audio.play("weapon_1hs_small_1", true);
        }
        break;

      case 14: // Lightning Strike - chain lightning visual
        Riiablo.audio.play("weapon_1hs_small_1", true);
        break;

      case 15: // Decoy spawn visual
        break;

      case 16: // Valkyrie spawn visual
        break;

      case 17: // Charged Bolt - spread bolts
        cltDoChargedBoltMissiles(event, skill, position);
        break;

      case 18: // Defensive buff visual
        // Buff overlay handled by cltstfunc
        break;

      case 19: // Inferno / Arctic Blast - stream visual
        cltDoStreamMissile(event, skill, position);
        break;

      case 20: // Static Field - pulse visual
        cltDoStaticFieldVisual(event, skill, position);
        break;

      case 21: // Telekinesis visual
        break;

      case 22: // Nova - circular missiles
        cltDoNovaMissiles(event, skill, position);
        break;

      case 23: // Blaze / Energy Shield / Spider Lay - state visual
        break;

      case 24: // Fire Wall - line of fire
        cltDoFireWallMissiles(event, skill, position);
        break;

      case 25: // Shouts / Nova (legacy) - circular missiles
        cltDoNovaMissiles(event, skill, position);
        break;

      case 26: // Chain Lightning
        cltDoSingleMissile(event, skill, position);
        break;

      case 27: // Teleport visual (handled server-side)
        // Teleport visual effect
        break;

      case 28: // Meteor / Blizzard - delayed AoE visual
        cltDoDelayedAoEMissile(event, skill);
        break;

      case 29: // Thunder Storm visual
        break;

      case 30: // Curse visual
        break;

      case 31: // Raise Skeleton visual
        break;

      case 32: // Poison Dagger hit
        Riiablo.audio.play("weapon_1hs_small_1", true);
        break;

      case 33: // Psychic Hammer visual
        cltDoSingleMissile(event, skill, position);
        break;

      case 43: // Native Leap movement uses the server-synchronized unit position
        break;

      case 55: // Corpse Explosion
        cltDoCorpseExplosionVisual(event, skill);
        break;

      case 65: // Aura toggle visual
        break;

      case 66: // Holy Fire / Shock / Sanctuary aura pulse
        cltDoAuraPulse(event, skill, position);
        break;

      case 67: // Charge - rush visual
        break;

      case 68: // Shout - barbarian war cry
        Riiablo.audio.play("barbarian_circle_1", true);
        break;

      case 70: // Double Swing
        Riiablo.audio.play("weapon_1hs_small_1", true);
        Riiablo.audio.play("weapon_1hs_small_1", true);
        break;

      case 73: // Blessed Hammer
        cltDoSingleMissile(event, skill, position);
        break;

      case 74: // Double Throw
        cltDoDoubleThrowMissiles(event, skill, position);
        break;

      case 76: // Whirlwind visual
        break;

      case 77: // Leap visual
        break;

      case 78: // Leap Attack
        Riiablo.audio.play("weapon_1hs_large_1", true);
        break;

      case 80: // Fist of the Heavens
        cltDoFistOfHeavensMissile(event, skill);
        break;

      default:
        log.warn("Unsupported cltdofunc({}) for {} casting {}", event.cltdofunc, event.entityId, event.skillId);
    }
  }

  //==========================================================================
  // Client-side Visual Effect Helper Methods
  //==========================================================================

  private String getHitClassSound(int entityId) {
    // TODO: get actual weapon hit class from equipped weapon
    return "weapon_1hs_large_1";
  }

  private void cltDoThrowMissile(SkillDoEvent event, Skills.Entry skill, Vector2 position) {
    if (position == null) {
      return;
    }
    
    String missileName = null;
    Item weapon = null;
    
    // First, try to use skill's missile
    if (skill != null && !skill.cltmissilea.isEmpty()) {
      missileName = skill.cltmissilea;
    } else {
      // If skill doesn't have missile, try to get from equipped throwable weapon
      try {
        if (Riiablo.charData != null && Riiablo.charData.getItems() != null) {
          weapon = Riiablo.charData.getItems().getEquippedThrowableWeapon();
          if (weapon != null && weapon.base != null) {
            boolean isThrowable = weapon.type.is(Type.JAVE) || 
                                 weapon.type.is(Type.TKNI) || 
                                 weapon.type.is(Type.TAXE);
            if (isThrowable) {
              // For throwable weapons, try different missile name patterns
              // Based on missile table, throwable weapons may use "electric" prefix (e.g., "electric throwaxe")
              String[] missileCandidates = {
                weapon.code,  // "jav"
                weapon.code + "s",  // "javs" (plural)
                "electric" + weapon.code,  // "electricjav" (like "electric throwaxe")
                "electric " + weapon.code,  // "electric jav" (with space)
                "throwing" + weapon.code,  // "throwingjav"
                weapon.code + "throw",  // "javthrow"
                "javelin",  // Full name
                "javelins",  // Plural
              };
              
              for (String candidate : missileCandidates) {
                Missiles.Entry testMissile = Riiablo.files.Missiles.get(candidate);
                if (testMissile != null) {
                  missileName = candidate;
                  break;
                }
              }
              
              // If still not found, use weapon code as fallback
              if (missileName == null) {
                missileName = weapon.code;
              }
            }
          }
        }
      } catch (Exception e) {
        // Ignore errors
      }
    }
    
    if (missileName == null || missileName.isEmpty()) {
      return;
    }
    
    Missiles.Entry missile = Riiablo.files.Missiles.get(missileName);
    if (missile == null) {
      log.warn("[MISSILE_CREATE] phase=client_resolve owner={} skill={} weaponCode={} missileName={} result=missing",
          event.entityId, event.skillId,
          weapon != null ? weapon.code : "none", missileName);
      return;
    }

    Vector2 angle = tmpVec.set(event.targetVec).sub(position).nor();
    log.info("[MISSILE_CREATE] phase=client_throw owner={} skill={} weaponCode={} missile={} "
            + "speed={} range={} start=({}, {}) direction=({}, {})",
        event.entityId, event.skillId, weapon != null ? weapon.code : "none",
        missile.Missile, missile.Vel, missile.Range,
        position.x, position.y, angle.x, angle.y);
    // Pass event.entityId as ownerId so the missile can properly check collisions
    factory.createMissile(missile, angle, position, event.entityId);
  }

  private void cltDoDebuffAuraVisual(SkillDoEvent event, Skills.Entry skill, Vector2 position) {
    // Debuff aura visual effect - usually an overlay
    if (skill != null && !skill.castoverlay.isEmpty()) {
      overlays.set(event.entityId, skill.castoverlay);
    }
  }

  private void cltDoFanMissiles(SkillDoEvent event, Skills.Entry skill, Vector2 position) {
    if (skill == null || skill.cltmissilea.isEmpty() || position == null) return;
    Missiles.Entry missile = Riiablo.files.Missiles.get(skill.cltmissilea);
    if (missile == null) return;

    Vector2 baseAngle = tmpVec.set(event.targetVec).sub(position).nor();
    int numMissiles = skill.Param != null && skill.Param.length > 0 ? skill.Param[0] : 5;
    float spreadAngle = MathUtils.PI / 6;
    float angleStep = numMissiles > 1 ? spreadAngle / (numMissiles - 1) : 0;
    float startAngle = baseAngle.angleRad() - spreadAngle / 2;

    for (int i = 0; i < numMissiles; i++) {
      Vector2 missileAngle = new Vector2(1, 0).setAngleRad(startAngle + angleStep * i);
      factory.createMissile(missile, missileAngle, position);
    }
  }

  private void cltDoSingleMissile(SkillDoEvent event, Skills.Entry skill, Vector2 position) {
    if (skill == null || skill.cltmissilea.isEmpty() || position == null) {
      return;
    }
    Missiles.Entry missile = Riiablo.files.Missiles.get(skill.cltmissilea);
    if (missile == null) {
      return;
    }

    Vector2 angle = tmpVec.set(event.targetVec).sub(position).nor();
    // Pass event.entityId as ownerId so the missile can properly check collisions
    factory.createMissile(missile, angle, position, event.entityId);
  }

  private void cltDoStrafeMissiles(SkillDoEvent event, Skills.Entry skill, Vector2 position) {
    if (skill == null || skill.cltmissilea.isEmpty() || position == null) return;
    Missiles.Entry missile = Riiablo.files.Missiles.get(skill.cltmissilea);
    if (missile == null) return;

    Vector2 baseAngle = tmpVec.set(event.targetVec).sub(position).nor();
    int numArrows = skill.Param != null && skill.Param.length > 0 ? skill.Param[0] : 5;

    for (int i = 0; i < numArrows; i++) {
      Vector2 missileAngle = new Vector2(baseAngle).rotateRad(MathUtils.random(-0.1f, 0.1f));
      factory.createMissile(missile, missileAngle, position);
    }
  }

  private void cltDoChargedBoltMissiles(SkillDoEvent event, Skills.Entry skill, Vector2 position) {
    if (skill == null || skill.cltmissilea.isEmpty() || position == null) return;
    Missiles.Entry missile = Riiablo.files.Missiles.get(skill.cltmissilea);
    if (missile == null) return;

    Vector2 baseAngle = tmpVec.set(event.targetVec).sub(position).nor();
    int numBolts = skill.Param != null && skill.Param.length > 0 ? skill.Param[0] : 5;
    float spreadAngle = MathUtils.PI / 3;

    for (int i = 0; i < numBolts; i++) {
      float randomOffset = MathUtils.random(-spreadAngle / 2, spreadAngle / 2);
      Vector2 missileAngle = new Vector2(baseAngle).rotateRad(randomOffset);
      factory.createMissile(missile, missileAngle, position);
    }
  }

  private void cltDoStreamMissile(SkillDoEvent event, Skills.Entry skill, Vector2 position) {
    if (skill == null || skill.cltmissilea.isEmpty() || position == null) return;
    Missiles.Entry missile = Riiablo.files.Missiles.get(skill.cltmissilea);
    if (missile == null) return;

    Vector2 angle = tmpVec.set(event.targetVec).sub(position).nor();
    factory.createMissile(missile, angle, position);
  }

  private void cltDoStaticFieldVisual(SkillDoEvent event, Skills.Entry skill, Vector2 position) {
    // Static field creates a visual pulse - could be an overlay or expanding circle
    if (skill != null && !skill.castoverlay.isEmpty()) {
      overlays.set(event.entityId, skill.castoverlay);
    }
  }

  private void cltDoNovaMissiles(SkillDoEvent event, Skills.Entry skill, Vector2 position) {
    if (skill == null || skill.cltmissilea.isEmpty() || position == null) return;
    Missiles.Entry missile = Riiablo.files.Missiles.get(skill.cltmissilea);
    if (missile == null) return;

    int numMissiles = 64;
    for (int i = 0; i < numMissiles; i++) {
      Vector2 angle = new Vector2(Vector2.X);
      angle.setAngleRad(Direction.directionToRadians(i, numMissiles));
      factory.createMissile(missile, angle, position);
    }
  }

  private void cltDoFireWallMissiles(SkillDoEvent event, Skills.Entry skill, Vector2 position) {
    if (skill == null || skill.cltmissilea.isEmpty() || position == null) return;
    Missiles.Entry missile = Riiablo.files.Missiles.get(skill.cltmissilea);
    if (missile == null) return;

    // Create line of fire at target location
    Vector2 direction = tmpVec.set(event.targetVec).sub(position).nor().rotate90(1);
    int segments = 5;

    for (int i = -segments / 2; i <= segments / 2; i++) {
      Vector2 pos = new Vector2(event.targetVec).add(direction.x * i * 2, direction.y * i * 2);
      factory.createMissile(missile, Vector2.Zero, pos);
    }
  }

  private void cltDoDelayedAoEMissile(SkillDoEvent event, Skills.Entry skill) {
    if (skill == null || skill.cltmissilea.isEmpty()) return;
    Missiles.Entry missile = Riiablo.files.Missiles.get(skill.cltmissilea);
    if (missile == null) return;

    // Create missile at target location
    factory.createMissile(missile, Vector2.Zero, event.targetVec);
  }

  private void cltDoCorpseExplosionVisual(SkillDoEvent event, Skills.Entry skill) {
    if (skill == null || skill.cltmissilea.isEmpty()) return;
    Missiles.Entry missile = Riiablo.files.Missiles.get(skill.cltmissilea);
    if (missile == null) return;

    // Explosion at target corpse location
    factory.createMissile(missile, Vector2.Zero, event.targetVec);
  }

  private void cltDoAuraPulse(SkillDoEvent event, Skills.Entry skill, Vector2 position) {
    if (skill == null || skill.cltmissilea.isEmpty() || position == null) return;
    Missiles.Entry missile = Riiablo.files.Missiles.get(skill.cltmissilea);
    if (missile == null) return;

    // Aura pulse emanates from caster
    int numMissiles = 16;
    for (int i = 0; i < numMissiles; i++) {
      Vector2 angle = new Vector2(Vector2.X);
      angle.setAngleRad(Direction.directionToRadians(i, numMissiles));
      factory.createMissile(missile, angle, position);
    }
  }

  private void cltDoDoubleThrowMissiles(SkillDoEvent event, Skills.Entry skill, Vector2 position) {
    if (skill == null || skill.cltmissilea.isEmpty() || position == null) return;
    Missiles.Entry missile = Riiablo.files.Missiles.get(skill.cltmissilea);
    if (missile == null) return;

    Vector2 baseAngle = tmpVec.set(event.targetVec).sub(position).nor();

    // Throw two missiles with slight angular offset
    factory.createMissile(missile, new Vector2(baseAngle).rotateRad(0.1f), position);
    factory.createMissile(missile, new Vector2(baseAngle).rotateRad(-0.1f), position);
  }

  private void cltDoFistOfHeavensMissile(SkillDoEvent event, Skills.Entry skill) {
    if (skill == null || skill.cltmissilea.isEmpty()) return;
    Missiles.Entry missile = Riiablo.files.Missiles.get(skill.cltmissilea);
    if (missile == null) return;

    // Lightning bolt from sky at target location
    factory.createMissile(missile, new Vector2(0, -1), event.targetVec);
  }

  private static boolean hasServerMissile(Skills.Entry skill) {
    return skill != null && (hasText(skill.srvmissilea) || hasText(skill.srvmissileb)
        || hasText(skill.srvmissilec) || hasText(skill.srvmissiled));
  }

  private static boolean hasText(String value) {
    return value != null && !value.isEmpty();
  }
}
