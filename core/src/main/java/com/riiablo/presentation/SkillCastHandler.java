package com.riiablo.presentation;

import com.badlogic.gdx.math.MathUtils;
import com.riiablo.engine.client.ClientNetworkReceiver;

import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;


import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ObjectMap;

import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Direction;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.ServerSkillSystem;
import com.riiablo.engine.server.skill.PaladinSkills;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.skill.SorceressSkills;
import com.riiablo.engine.server.skill.NativeSkillResolver;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.event.SkillCastEvent;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.event.SkillStartEvent;
import com.riiablo.item.Item;
import com.riiablo.item.Type;
import com.riiablo.item.BodyLoc;
import com.riiablo.codec.excel.Weapons;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.skill.SkillCodes;

public class SkillCastHandler extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(SkillCastHandler.class);

  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Angle> mAngle;
  protected ComponentMapper<Missile> mMissile;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<UnitStates> mUnitStates;

  protected OverlayManager overlays;

  @Wire(name = "factory")
  protected EntityFactory factory;

  @Wire(name = "clientContext")
  protected ClientGameplayContext clientContext ;

  private void playSound(String sound) {
    if (sound != null && !sound.isEmpty() && clientContext.audio() != null) {
      clientContext.audio().play(sound, true);
    }
  }

  private final Vector2 tmpVec = new Vector2();

  @Subscribe
  public void onLeapLand(com.riiablo.engine.server.event.LeapLandEvent event) {
    // 1.10f D2Client cltdo43 -> 6FAC1E40, landing branch at 6FAC1FD7:
    // overlay 0x50 (Overlay.txt dust / BAJumpLandPuff01), once on arrival.
    if (mPosition.has(event.entityId)) overlays.set(event.entityId, "dust");
  }

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
      case 12: // Telekinesis / Dragon Flight target validation is server-authoritative
        break;
      case 15: // Raise Skeleton/Mage is created by the authoritative server path.
      case 17: // Corpse skills reserve and consume corpses server-side.
      case 18: // Native Attract start callback is a successful no-op.
      case 19: // Bone Prison validation is server-authoritative.
      case 20: // Iron Golem validation is server-authoritative.
      case 21: // Revive validation is server-authoritative.
        break;
      case 11: // Arctic Blast channel setup is driven by the server missile.
        break;
      case 65: // Throw skill (skillId=2) - same as case 3
        break;
      case 42: // Fire Hit performs its native pre-hit setup on the server
        break;
      case 50: // Monster Quick Strike uses the authoritative SrvDo092 missile.
        break;
      case 49: // Nest spawn reservation is owned by Actioneer.
      case 53: // MonInferno channel setup is owned by Actioneer.
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
      case 68: // shouts: vocal bank is played from Skills.txt stsoundclass
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
    if (clientContext.files() == null || clientContext.files().skills == null) return;
    final Skills.Entry skill = clientContext.files().skills.get(event.skillId);
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
    String startSound = skill.stsound != null && !skill.stsound.isEmpty()
        ? skill.stsound : skill.stsoundclass;
    if (startSound == null || startSound.isEmpty()) {
      log.debug("{} phase=sound_skipped entity={} skill={} reason=empty_stsound",
          presentationMarker,
          event.entityId, skill.skill);
    } else {
      // Barbarian warcries store their vocal cast bank in stsoundclass
      // (e.g. barbarian_howl_1), while stsound/dosound are empty in 1.10f.
      // D2Client resolves this class bank at the skill-start callback.
      playSound(startSound);
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
      case 11: // Arrow/Bolt client start; cltdofunc owns the presentation.
        break;
      case 15: // Arctic Blast channel start; server missile owns the stream.
        break;
      case 2: // Channeled skills (inferno, arctic blast)
        // Start channeling effect
        break;
      case 3: // Aura toggle
        // Visual aura effect
        break;
      case 12: // Jab uses the server-synchronized native SQ presentation.
        break;
      case 29: // Leap: MonSeq/LeapSystem owns the action and airborne position.
        // Sound/overlay was applied above. Reinitializing movement here would
        // restart the shared server leap when the client receives SkillStart.
        break;
      case 39: // Resurrect: ServerEntityFactory owns the revive; the client
                // already presents stsound/castoverlay above.
        break;
      case 36: // Nest: the server creates the summoned monster at the MIS
                // keyframe; client presentation is limited to sound/overlay.
        break;
      case 45: // Werewolf/Werebear: DruidShapeShiftResolver owns the form
                // presentation; stsound/castoverlay were handled above.
        break;
      default:
        log.warn("Unsupported cltstfunc({}) for {} casting {}", event.cltstfunc, event.entityId, event.skillId);
    }
  }

  @Subscribe
  public void cltdofunc(SkillDoEvent event) {
    log.traceEntry("cltdofunc(entityId: {}, cltdofunc: {}, targetId: {}, targetVec: {})",
        event.entityId, event.cltdofunc, event.targetId, event.targetVec);
    if (clientContext.files() == null || clientContext.files().skills == null) return;
    final Skills.Entry skill = clientContext.files().skills.get(event.skillId);
    if (skill == null) {
      log.warn("Skill {} not found for cltdofunc, skipping", event.skillId);
      return;
    }
    playSound(skill.dosound);

    // Necromancer curses use the native client area carrier (CltDo30). It is
    // a cast-range presentation, not a target state overlay.
    if (event.cltdofunc == 30 || skill.cltdofunc == 30) {
      cltDoCurseArea(event, skill);
      return;
    }

    Vector2 position = mPosition.has(event.entityId) ? mPosition.get(event.entityId).position : null;

    // Network clients receive the authoritative D2GS missile entity shortly
    // after this event. Creating another local visual here produces doubled
    // arrows and travel sounds. Local games normally retain the legacy player
    // projectile, except for skills explicitly admitted by their monsters-only
    // ServerSkillSystem (Blessed Hammer is now one such authoritative path).
    boolean networkClient = world.getSystem(ClientNetworkReceiver.class) != null;
    boolean localServer = world.getSystem(ServerSkillSystem.class) != null;
    // SrvDo022/068 already emit the complete authoritative 64-way warcry
    // ring in local games. NumDirections is the DCC layout, not ring size.
    if (localServer && isWarCryWave(skill)) return;
    if (localServer && (skill.srvdofunc == 118 || skill.Id == SkillId.MOLTEN_BOULDER)) return;
    // Local single-player worlds use the monsters-only server adapter to keep
    // player projectiles compatible with the legacy presentation path. Amazon
    // bow skills are the exception: their cold/fire/impact packet must be
    // initialized by the authoritative server missile, otherwise Ice Arrow
    // reaches collision with only the owner's physical weapon damage.
    boolean localAmazonBowServer = localServer
        && NativeSkillResolver.isAmazonBowSkill(skill);
    boolean localAmazonElementalMissileServer = localServer
        && NativeSkillResolver.isAmazonElementalMissileSkill(skill);
    boolean localMonsterServer = mMonster.has(event.entityId) && localServer;
    boolean localBlessedHammerServer = localServer
        && (event.srvdofunc == 73 || skill.srvdofunc == 73);
    boolean localFistOfHeavensServer = localServer
        && (event.srvdofunc == 80 || skill.srvdofunc == 80);
    boolean localHolyBoltServer = localServer && PaladinSkills.isHolyBolt(skill);
    // Charged Bolt and Charged Strike are emitted as authoritative missiles by
    // the local ServerSkillSystem.  Do not also create the legacy client
    // visual: it has no owner, cannot collide, and would add a misleading
    // extra bolt to Charged Strike (whose native count starts at three).
    boolean localChargedBoltServer = localServer
        && (event.skillId == com.riiablo.engine.server.skill.SkillId.CHARGED_BOLT
            || "Charged Bolt".equalsIgnoreCase(skill.skill)
            || event.skillId == com.riiablo.engine.server.skill.SkillId.CHARGED_STRIKE
            || event.srvdofunc == 11 || skill.srvdofunc == 11);
    // Multiple Shot uses SrvDo008 to emit one authoritative arrow per lane
    // in the local server. Do not also create the legacy client fan, whose
    // three-argument factory call has no owner and therefore cannot damage
    // monsters in the server collision system.
    boolean localMultipleShotServer = localServer
        && (event.srvdofunc == 8 || skill.srvdofunc == 8);
    boolean localStrafeServer = localServer
        && (event.srvdofunc == 12 || skill.srvdofunc == 12);
    boolean localStreamServer = localServer
        && (event.srvdofunc == 19 || skill.srvdofunc == 19);

    // Blessed Hammer's SrvDo073 path is authoritative on both local server
    // worlds and network replicas.  The generic CltDo73 helper creates a
    // straight-line presentation missile, which can race the real native
    // spiral and is the source of the hammer visibly flying away from the
    // paladin.  Reuse the authoritative missile explicitly before the broad
    // server-missile predicate below.
    if (PaladinSkills.isBlessedHammer(skill) && (localServer || networkClient)) {
      log.info("[BLESSED_HAMMER] phase=reuse_server_missile entity={} networkClient={} localServer={}",
          event.entityId, networkClient, localServer);
      return;
    }
    // Meteor/Blizzard use SrvDo28 to create a resident centre controller.
    // Their CltDo28 callback is only the retail client-side packet hook; in a
    // local server world the controller already owns the delayed visual and
    // the impact clock. Creating cltmissilea here adds a second frozen centre
    // sprite at the target (and makes the falling children appear duplicated).
    boolean localDelayedAreaServer = localServer
        && (event.srvdofunc == 28 || skill.srvdofunc == 28)
        && (skill.Id == SkillId.METEOR || skill.Id == SkillId.BLIZZARD
            || "Meteor".equalsIgnoreCase(skill.skill)
            || "Blizzard".equalsIgnoreCase(skill.skill));
    boolean localDoubleThrowServer = localServer
        && (event.srvdofunc == 74 || skill.srvdofunc == 74);
    // Lightning Strike's shipped cltDoFunc is shared with Nova/Frost Nova in
    // some table exports.  When the local server owns SrvDo014, dispatching
    // that callback would incorrectly fan the LightningStrike missile in all
    // directions.  The authoritative root/continuation missiles already
    // carry the correct directional LightningStrike visual.
    if (isLocalAuthoritativeLightningStrike(skill, localServer, event.srvdofunc)) {
      log.info("[AMAZON_LIGHTNING_STRIKE] phase=client_reuse_server_missile entity={} skill={}",
          event.entityId, skill.skill);
      return;
    }
    // Native SrvDo022 (Nova) and SrvDo026 (Chain Lightning) create the
    // complete authoritative missile(s) in the local server.  Their client
    // callbacks (22 and 27) are only packet-side presentation hooks; running
    // them here would add an unowned duplicate (or a second nova ring).
    if (localDelayedAreaServer
        || localServer && (event.srvdofunc == 22 || skill.srvdofunc == 22
        || event.srvdofunc == 26 || skill.srvdofunc == 26
        || event.skillId == SkillId.CHAIN_LIGHTNING)) {
      log.info("[SKILL_PRESENTATION] phase=reuse_server_missile entity={} skill={} srvDoFunc={}",
          event.entityId, skill.skill, event.srvdofunc);
      return;
    }
    // Blaze's missile is a parameter for the server's movement-triggered
    // ground-fire trail, not a projectile released when the buff is cast.
    if (skill.Id == SkillId.BLAZE) return;
    boolean authoritativeThrow = event.skillId == SkillCodes.throw_
        || event.skillId == SkillCodes.left_hand_throw;
    // Throwing weapons are created by ServerSkillSystem (including the local
    // single-player server). Creating the legacy client javelin as well makes
    // it collide independently, and the shared javelin Missiles.txt row has
    // Pierce=1, which used to produce an apparent 100% piercing throw.
    if (authoritativeThrow && (networkClient || localServer)) {
      log.info("[SKILL_PRESENTATION] phase=reuse_server_throw entity={} skill={} networkClient={} localServer={}",
          event.entityId, event.skillId, networkClient, localServer);
      return;
    }
    if (localDoubleThrowServer || (networkClient && (event.srvdofunc == 74 || skill.srvdofunc == 74))) {
      log.info("[SKILL_PRESENTATION] phase=reuse_server_double_throw entity={} skill={}",
          event.entityId, skill.skill);
      return;
    }
    if (localAmazonBowServer || localAmazonElementalMissileServer) {
      log.info("[SKILL_PRESENTATION] phase=reuse_server_amazon_missile entity={} skill={}"
          + " localServer={} elementalMissile={}", event.entityId, skill.skill,
          localServer, localAmazonElementalMissileServer);
      return;
    }
    boolean fistOfHeavens = event.srvdofunc == 80 || skill.srvdofunc == 80;
    if (fistOfHeavens && event.targetId >= 0 && mPosition.has(event.targetId)
        && clientContext.files() != null && clientContext.files().NativeSkills != null) {
      com.riiablo.codec.excel.NativeSkills.Entry nativeSkill =
          clientContext.files().NativeSkills.get(skill.Id);
      String serverOverlay = nativeSkill != null
          ? nativeSkill.string("srvoverlay") : null;
      if (hasText(serverOverlay) && clientContext.colors() != null && clientContext.assets() != null) {
        overlays.set(event.targetId, serverOverlay);
        log.info("[FIST_OF_HEAVENS] phase=target_overlay source={} target={} overlay={}",
            event.entityId, event.targetId, serverOverlay);
      }
    }
    // Poison Explosion's server missiles are the eight damaging clouds, while
    // cltdofunc 33 owns a separate corpse-burst presentation. Keep that local
    // visual on every client even when the cloud entities are authoritative.
    boolean separateCorpseBurst = (event.srvdofunc == 63 || skill.srvdofunc == 63)
        && event.cltdofunc == 33;
    if (shouldReuseServerMissile(skill, networkClient, localMonsterServer,
        localBlessedHammerServer, localFistOfHeavensServer, localHolyBoltServer,
        localChargedBoltServer, separateCorpseBurst, localMultipleShotServer,
        localStrafeServer, localStreamServer)) {
      log.info("[SKILL_PRESENTATION] phase=reuse_server_missile entity={} skill={} "
              + "srvDoFunc={} networkClient={} localMonster={} localBlessedHammer={} "
          + "localFistOfHeavens={} localHolyBolt={} localChargedBolt={}",
          event.entityId, skill.skill, skill.srvdofunc, networkClient, localMonsterServer,
          localBlessedHammerServer, localFistOfHeavensServer, localHolyBoltServer,
          localChargedBoltServer);
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
        // Blaze (cltDoFunc=0) is a movement trail.  Its server-side
        // StateUpdater emits ground missiles only after movement; the cast
        // callback must not place a static flame under the caster.
        if ("Blaze".equalsIgnoreCase(skill.skill) || event.srvdofunc == 23) break;
        cltDoGenericMissile(event, skill, position);
        break;

      case 1: // Attack - play weapon swing sound
        String hitClass = getHitClassSound(event.entityId);
        playSound(hitClass);
        break;

      case 2: // Kick / melee hit with specific sound
        // Explicit Throw/Left Hand Throw has already been handled above.
        // Keep this branch for genuine non-throwing skills that use the same
        // client function number.
        playSound("weapon_1hs_small_1");
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

      case 7: // Jab / Dragon Tail share this client callback
        // Dragon Tail already played its Skills.txt do sound above and uses
        // the authoritative dragontail missile entity for the explosion.
        // Only Jab owns the callback's three additional weapon impacts.
        if (event.srvdofunc == 7 || skill.srvdofunc == 7) {
          for (int i = 0; i < 3; i++) {
            playSound("weapon_1hs_small_1");
          }
        }
        break;

      case 8: // Multiple Shot / Teeth - fan missiles
        cltDoFanMissiles(event, skill, position);
        break;

      case 9: // Frenzy - dual weapon sounds
        playSound("weapon_1hs_small_1");
        playSound("weapon_1hs_small_1");
        break;

      case 39: // Double Swing/Frenzy native dual-hit callback
        playSound("weapon_1hs_small_1");
        playSound("weapon_1hs_small_1");
        break;

      case 10: // Guided Arrow / Bone Spirit - homing missile
        cltDoSingleMissile(event, skill, position);
        break;

      case 11: // Charged Strike - melee with lightning effect
        playSound("weapon_1hs_small_1");
        // TODO: spawn charged bolt visuals from hit position
        break;

      case 12: // Strafe - rapid arrow visuals
        cltDoStrafeMissiles(event, skill, position);
        break;

      case 13: // Zeal / Fend / Fury - multi-hit sounds
        // The server emits one authoritative SkillDo/keyframe for each
        // repeated strike. Playing four impacts per event multiplied Fury's
        // audio while its animation sequence was already repeating.
        playSound("weapon_1hs_small_1");
        break;

      case 14: // Lightning Strike - chain lightning visual
        playSound("weapon_1hs_small_1");
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

      case 50: // Nest: the authoritative spawned monster owns its presentation.
        break;

      case 19: // Inferno / Arctic Blast - stream visual
        cltDoStreamMissile(event, skill, position);
        break;

      case 20: // Strafe - rapid attack animation/facing sequence
        if (event.srvdofunc == 12 || skill.srvdofunc == 12
            || "Strafe".equalsIgnoreCase(skill.skill)) {
          cltDoStrafeAnimation(event, skill, position);
        } else {
          cltDoStaticFieldVisual(event, skill, position);
        }
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
        // Affected-unit overlays follow authoritative States.txt rows in
        // StateOverlaySystem, including curses which deal no damage.
        cltDoCurseArea(event, skill);
        break;

      case 31: // Raise Skeleton visual
        break;

      case 32: // Poison Dagger hit / Corpse Explosion burst
        if (event.srvdofunc == 55 || skill.srvdofunc == 55) {
          cltDoCorpseExplosionVisual(event, skill);
        } else {
          playSound("weapon_1hs_small_1");
        }
        break;

      case 33: // Psychic Hammer / Poison Explosion corpse burst
        if (event.srvdofunc == 63 || skill.srvdofunc == 63) {
          cltDoCorpseExplosionVisual(event, skill);
        } else {
          cltDoSingleMissile(event, skill, position);
        }
        break;

      case 38: // Find Potion corpse action; Skills.txt do sound is the visual cue.
      case 40: // Find Item corpse action; corpse state is server-authoritative.
      case 41: // Grim Ward; the synchronized ward missile owns its DCC.
        break;

      case 43: // Native Leap movement uses the server-synchronized unit position
        break;

      case 44: // Leap Attack landing hit; dust is emitted by onLeapLand.
        playSound("weapon_1hs_large_1");
        break;

      case 45: // Whirlwind uses the synchronized SQ animation and position
        break;

      case 55: // Legacy/custom Corpse Explosion callback
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
        break;

      case 70: // Double Swing
        playSound("weapon_1hs_small_1");
        playSound("weapon_1hs_small_1");
        break;

      case 73: // Blessed Hammer
        cltDoSingleMissile(event, skill, position);
        break;

      case 42: // Double Throw native client callback
        cltDoDoubleThrowMissiles(event, skill, position);
        break;

      case 76: // Whirlwind visual
        break;

      case 77: // Leap visual
        break;

      case 78: // Leap Attack
        playSound("weapon_1hs_large_1");
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
    // Attack's stsound is empty in the native Skills.txt row.  D2Client
    // selects the swing bank from the active weapon class at the attack
    // keyframe; using a fixed 1hs sound makes bow attacks silent/mismatched.
    if (clientContext.charData() != null && clientContext.charData().getItems() != null) {
      com.riiablo.save.ItemData items = clientContext.charData().getItems();
      Item weapon = items.getEquippedRangedWeapon();
      if (weapon != null) {
        return weapon.type.is(Type.XBOW) ? "weapon_xbow_1" : "weapon_bow_1";
      }
      weapon = items.getEquipped(BodyLoc.RARM);
      if (weapon == null || !items.isActive(weapon)) {
        weapon = items.getEquipped(BodyLoc.LARM);
      }
      if (weapon != null && items.isActive(weapon)
          && weapon.base instanceof Weapons.Entry) {
        Weapons.Entry entry = (Weapons.Entry) weapon.base;
        return entry._2handed ? "weapon_2hs_large_1" : "weapon_1hs_large_1";
      }
    }
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
        if (clientContext.charData() != null && clientContext.charData().getItems() != null) {
          weapon = clientContext.charData().getItems().getEquippedThrowableWeapon();
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
                Missiles.Entry testMissile = clientContext.files().Missiles.get(candidate);
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
    
    Missiles.Entry missile = clientContext.files().Missiles.get(missileName);
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
    Missiles.Entry missile = clientContext.files().Missiles.get(skill.cltmissilea);
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
    Missiles.Entry missile = clientContext.files().Missiles.get(skill.cltmissilea);
    if (missile == null) {
      return;
    }

    Vector2 angle = resolveTargetDirection(event, position, tmpVec);
    // Pass event.entityId as ownerId so the missile can properly check collisions
    factory.createMissile(missile, angle, position, event.entityId);
  }

  private void cltDoStrafeMissiles(SkillDoEvent event, Skills.Entry skill, Vector2 position) {
    if (skill == null || skill.cltmissilea.isEmpty() || position == null) return;
    Missiles.Entry missile = clientContext.files().Missiles.get(skill.cltmissilea);
    if (missile == null) return;

    Vector2 baseAngle = tmpVec.set(event.targetVec).sub(position).nor();
    int numArrows = skill.Param != null && skill.Param.length > 0 ? skill.Param[0] : 5;

    for (int i = 0; i < numArrows; i++) {
      Vector2 missileAngle = new Vector2(baseAngle).rotateRad(PresentationRandom.range(-0.1f, 0.1f));
      factory.createMissile(missile, missileAngle, position);
    }
  }

  private void cltDoChargedBoltMissiles(SkillDoEvent event, Skills.Entry skill, Vector2 position) {
    if (skill == null || skill.cltmissilea.isEmpty() || position == null) return;
    Missiles.Entry missile = clientContext.files().Missiles.get(skill.cltmissilea);
    if (missile == null) return;

    Vector2 baseAngle = tmpVec.set(event.targetVec).sub(position).nor();
    int numBolts = skill.Param != null && skill.Param.length > 0 ? skill.Param[0] : 5;
    float spreadAngle = MathUtils.PI / 3;

    for (int i = 0; i < numBolts; i++) {
      float randomOffset = PresentationRandom.range(-spreadAngle / 2, spreadAngle / 2);
      Vector2 missileAngle = new Vector2(baseAngle).rotateRad(randomOffset);
      factory.createMissile(missile, missileAngle, position);
    }
  }

  private void cltDoStreamMissile(SkillDoEvent event, Skills.Entry skill, Vector2 position) {
    if (skill == null || skill.cltmissilea.isEmpty() || position == null) return;
    Missiles.Entry missile = clientContext.files().Missiles.get(skill.cltmissilea);
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
    Missiles.Entry missile = clientContext.files().Missiles.get(skill.cltmissilea);
    if (missile == null) return;

    int numMissiles = isWarCryWave(skill) ? 64 : nativeDirectionCount(missile, 64);
    for (int i = 0; i < numMissiles; i++) {
      Vector2 angle = new Vector2(Vector2.X);
      if (isWarCryWave(skill)) warCryDirection(i, angle);
      else angle.setAngleRad(Direction.directionToRadians(i, numMissiles));
      factory.createMissile(missile, angle, position);
    }
  }

  private void cltDoFireWallMissiles(SkillDoEvent event, Skills.Entry skill, Vector2 position) {
    if (skill == null || skill.cltmissilea.isEmpty() || position == null) return;
    // SrvDo24 already creates the authoritative maker/segment missiles.  The
    // client callback is not an additional projectile emitter; doing so draws
    // the unwanted single "launching" flame at the cast point (and duplicates
    // damage in local games).
    if (event.srvdofunc == 24 || "Fire Wall".equalsIgnoreCase(skill.skill)) return;
    Missiles.Entry missile = clientContext.files().Missiles.get(skill.cltmissilea);
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
    Missiles.Entry missile = clientContext.files().Missiles.get(skill.cltmissilea);
    if (missile == null) return;

    // Create missile at target location
    factory.createMissile(missile, Vector2.Zero, event.targetVec);
  }

  private void cltDoCorpseExplosionVisual(SkillDoEvent event, Skills.Entry skill) {
    if (skill == null || skill.cltmissilea.isEmpty()) return;
    Missiles.Entry missile = clientContext.files().Missiles.get(skill.cltmissilea);
    if (missile == null) return;

    // Explosion at target corpse location
    Vector2 target = event.targetId >= 0 && mPosition.has(event.targetId)
        ? mPosition.get(event.targetId).position : event.targetVec;
    if (target != null) createPresentationMissile(missile, Vector2.Zero, target);
  }

  private void cltDoCurseArea(SkillDoEvent event, Skills.Entry skill) {
    if (!hasText(skill.cltmissilea) || factory == null) return;
    Missiles.Entry child = clientContext.files().Missiles.get(skill.cltmissilea);
    Missiles.Entry center = clientContext.files().Missiles.get("cursecenter");
    if (child == null || center == null) return;
    Vector2 at = event.targetId >= 0 && mPosition.has(event.targetId)
        ? mPosition.get(event.targetId).position : event.targetVec;
    if (at == null) return;
    int level = mPlayer.has(event.entityId) && mPlayer.get(event.entityId).data != null
        ? mPlayer.get(event.entityId).data.getSkill(skill.Id) : 1;
    if (mUnitStates.has(event.entityId) && mUnitStates.get(event.entityId).stateList != null)
      level += mUnitStates.get(event.entityId).stateList.getTotalSkillModifier();
    // cursecenter has no CelFile: allocate a callback carrier without asking
    // ClientEntityFactory to load the nonexistent null.dcc.
    int id = world.create();
    mPosition.create(id).position.set(at);
    world.getMapper(com.riiablo.engine.server.component.Velocity.class).create(id);
    Missile visual = mMissile.create(id);
    visual.missile = center;
    visual.authoritative = false;
    visual.presentationOnly = true;
    visual.ownerId = -1;
    visual.nativeLifetimeFrames = center.Range;
    // CltDo17 uses native TargetX/TargetY as radius and visual missile ID.
    // These presentation-local path slots are not used by callback 17 movement.
    visual.clientPathX = curseVisualRadius(skill, Math.max(1, level));
    visual.clientPathY = child.Id;
    visual.skillId = skill.Id;
    visual.damageLevel = Math.max(1, level);
  }

  static int curseVisualRadius(Skills.Entry skill, int level) {
    // D2Client 6FAC8083..97 subtracts three and clamps to two.
    return Math.max(2, SkillFormula.evaluate(skill.aurarangecalc, skill, level) - 3);
  }

  /** Client cast effects never participate in authoritative damage or collision. */
  private int createPresentationMissile(Missiles.Entry row, Vector2 facing, Vector2 at) {
    int id = factory instanceof ClientEntityFactory
        ? ((ClientEntityFactory) factory).createMissilePresentation(row, facing, new Vector2(at))
        : factory.createMissile(row, facing, new Vector2(at), -1);
    if (id >= 0 && mMissile.has(id)) {
      Missile effect = mMissile.get(id);
      effect.presentationOnly = true;
      effect.authoritative = false;
      effect.ownerId = -1;
      effect.persistent = false;
      effect.nativeLifetimeFrames = MissileImpactPresentationSystem.nativePresentationLifetimeFrames(row);
    }
    return id;
  }

  private void cltDoAuraPulse(SkillDoEvent event, Skills.Entry skill, Vector2 position) {
    if (skill == null || skill.cltmissilea.isEmpty() || position == null) return;
    Missiles.Entry missile = clientContext.files().Missiles.get(skill.cltmissilea);
    if (missile == null) return;

    // Aura pulse emanates from caster
    int numMissiles = nativeDirectionCount(missile, 16);
    for (int i = 0; i < numMissiles; i++) {
      Vector2 angle = new Vector2(Vector2.X);
      angle.setAngleRad(Direction.directionToRadians(i, numMissiles));
      factory.createMissile(missile, angle, position);
    }
  }

  private void cltDoDoubleThrowMissiles(SkillDoEvent event, Skills.Entry skill, Vector2 position) {
    if (position == null) return;
    if (skill == null || skill.cltmissilea.isEmpty()) {
      // Retail Skills.txt leaves Double Throw's missile columns empty: the
      // client resolves the equipped throwing weapon just like Throw.
      cltDoThrowMissile(event, skill, position);
      cltDoThrowMissile(event, skill, position);
      return;
    }
    Missiles.Entry missile = clientContext.files().Missiles.get(skill.cltmissilea);
    if (missile == null) return;

    Vector2 baseAngle = tmpVec.set(event.targetVec).sub(position).nor();

    // Throw two missiles with slight angular offset
    factory.createMissile(missile, new Vector2(baseAngle).rotateRad(0.1f), position);
    factory.createMissile(missile, new Vector2(baseAngle).rotateRad(-0.1f), position);
  }

  private void cltDoFistOfHeavensMissile(SkillDoEvent event, Skills.Entry skill) {
    if (skill == null || skill.cltmissilea.isEmpty()) return;
    Missiles.Entry missile = clientContext.files().Missiles.get(skill.cltmissilea);
    if (missile == null) return;

    // Lightning bolt from sky at target location
    factory.createMissile(missile, new Vector2(0, -1), event.targetVec);
  }

  /** Skills.txt/CltMissile path used by Fire/Cold/Ice/Freezing Arrow. */
  private void cltDoGenericMissile(SkillDoEvent event, Skills.Entry skill, Vector2 position) {
    if (skill == null || position == null) return;
    // SrvDo018 only installs the armor state/event handlers. Chilling Armor's
    // missile A is a retaliation parameter (D2MOO EventFunc01), not a cast
    // projectile. StateUpdater emits the owned return bolt on hitbymissile.
    if (SorceressSkills.isDefensiveArmor(skill)) return;
    String missileName = hasText(skill.cltmissile) ? skill.cltmissile
        : hasText(skill.cltmissilea) ? skill.cltmissilea : null;
    if (!hasText(missileName)) return;
    Missiles.Entry missile = clientContext.files().Missiles.get(missileName);
    if (missile == null) {
      log.warn("[SKILL_PRESENTATION] phase=missing_missile entity={} skill={} missile={}",
          event.entityId, skill.skill, missileName);
      return;
    }
    Vector2 angle = resolveTargetDirection(event, position, tmpVec);
    int missileId = factory.createMissile(missile, angle, position, event.entityId);
    log.info("[SKILL_PRESENTATION] phase=generic_missile entity={} skill={} missile={} "
            + "missileId={} direction=({}, {})",
        event.entityId, skill.skill, missileName, missileId, angle.x, angle.y);
  }

  private Vector2 resolveTargetDirection(SkillDoEvent event, Vector2 position, Vector2 out) {
    if (event.targetId >= 0 && mPosition.has(event.targetId)) {
      out.set(mPosition.get(event.targetId).position).sub(position);
    } else if (event.targetVec != null) {
      out.set(event.targetVec).sub(position);
    } else {
      out.set(1f, 0f);
    }
    if (out.isZero(0.0001f)) out.set(1f, 0f);
    return out.nor();
  }

  static boolean shouldReuseServerMissile(Skills.Entry skill, boolean networkClient,
      boolean localMonsterServer, boolean localBlessedHammerServer,
      boolean localFistOfHeavensServer, boolean localHolyBoltServer,
      boolean localChargedBoltServer, boolean separateCorpseBurst) {
    return shouldReuseServerMissile(skill, networkClient, localMonsterServer,
        localBlessedHammerServer, localFistOfHeavensServer, localHolyBoltServer,
        localChargedBoltServer, separateCorpseBurst, false);
  }

  static boolean isLocalAuthoritativeLightningStrike(
      Skills.Entry skill, boolean localServer, int srvdofunc) {
    return localServer && skill != null
        && (srvdofunc == 14 || skill.srvdofunc == 14
            || skill.Id == SkillId.LIGHTNING_STRIKE);
  }

  /**
   * Strafe's client callback is not a static field pulse.  D2's sequence
   * repeats the A1 bow animation for each released arrow while the unit turns
   * toward the next target.  The authoritative server owns the missiles; this
   * callback only keeps the local presentation facing synchronized.
   */
  private void cltDoStrafeAnimation(SkillDoEvent event, Skills.Entry skill, Vector2 position) {
    if (position == null || !mAngle.has(event.entityId)) return;
    Vector2 direction = resolveTargetDirection(event, position, tmpVec);
    mAngle.get(event.entityId).target.set(direction);
    log.info("[STRAFE_ANIM] phase=client_keyframe entity={} target={} direction=({}, {})",
        event.entityId, event.targetId, direction.x, direction.y);
  }

  /** Uses Missiles.txt.NumDirections before falling back to legacy callback defaults. */
  static int nativeDirectionCount(Missiles.Entry missile, int fallback) {
    return missile != null && missile.NumDirections > 0
        ? missile.NumDirections : fallback;
  }

  static boolean shouldReuseServerMissile(Skills.Entry skill, boolean networkClient,
      boolean localMonsterServer, boolean localBlessedHammerServer,
      boolean localFistOfHeavensServer, boolean localHolyBoltServer,
      boolean localChargedBoltServer, boolean separateCorpseBurst,
      boolean localMultipleShotServer) {
    return shouldReuseServerMissile(skill, networkClient, localMonsterServer,
        localBlessedHammerServer, localFistOfHeavensServer, localHolyBoltServer,
        localChargedBoltServer, separateCorpseBurst, localMultipleShotServer, false);
  }

  static boolean shouldReuseServerMissile(Skills.Entry skill, boolean networkClient,
      boolean localMonsterServer, boolean localBlessedHammerServer,
      boolean localFistOfHeavensServer, boolean localHolyBoltServer,
      boolean localChargedBoltServer, boolean separateCorpseBurst,
      boolean localMultipleShotServer, boolean localStrafeServer) {
    return (hasServerMissile(skill) || localMultipleShotServer) && !separateCorpseBurst
        && (networkClient || localMonsterServer || localBlessedHammerServer
            || localFistOfHeavensServer || localHolyBoltServer || localChargedBoltServer
            || localMultipleShotServer || localStrafeServer);
  }

  static boolean shouldReuseServerMissile(Skills.Entry skill, boolean networkClient,
      boolean localMonsterServer, boolean localBlessedHammerServer,
      boolean localFistOfHeavensServer, boolean localHolyBoltServer,
      boolean localChargedBoltServer, boolean separateCorpseBurst,
      boolean localMultipleShotServer, boolean localStrafeServer,
      boolean localStreamServer) {
    return (hasServerMissile(skill) || localMultipleShotServer || localStreamServer)
        && !separateCorpseBurst
        && (networkClient || localMonsterServer || localBlessedHammerServer
            || localFistOfHeavensServer || localHolyBoltServer || localChargedBoltServer
            || localMultipleShotServer || localStrafeServer || localStreamServer);
  }

  static boolean hasServerMissile(Skills.Entry skill) {
    return skill != null && (hasText(skill.srvmissile)
        || hasText(skill.srvmissilea) || hasText(skill.srvmissileb)
        || hasText(skill.srvmissilec) || hasText(skill.srvmissiled)
        // Native SrvDo124 stores the presentation missile in cltMissileA,
        // but StateUpdater emits it as an authoritative periodic entity.
        || skill.Id == com.riiablo.engine.server.skill.SkillId.HURRICANE
        || skill.Id == com.riiablo.engine.server.skill.SkillId.ARMAGEDDON);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isEmpty();
  }

  static boolean isWarCryWave(Skills.Entry skill) {
    if (skill == null) return false;
    switch (skill.Id) {
      case 130: case 138: case 146: case 149: case 154: case 155: return true;
      default: return false;
    }
  }

  static boolean isDoubleThrow(int skillId) { return skillId == 140; }

  // First quadrant of D2Client's 64 target offsets at 6FB6E2E0/6FB6E3E0.
  // These are integer target vectors, not uniformly spaced floating angles.
  private static final int[] WAR_CRY_QUARTER =
      {30, 29, 29, 28, 27, 26, 24, 23, 21, 19, 16, 14, 11, 8, 5, 2, 0};

  static Vector2 warCryDirection(int index, Vector2 out) {
    int lane = Math.floorMod(index, 64);
    int offset = lane % 16;
    int x = WAR_CRY_QUARTER[offset], y = WAR_CRY_QUARTER[16 - offset];
    switch (lane / 16) {
      case 0: return out.set(x, y).nor();
      case 1: return out.set(-y, x).nor();
      case 2: return out.set(-x, -y).nor();
      default: return out.set(y, -x).nor();
    }
  }
}
