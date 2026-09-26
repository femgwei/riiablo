package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.NativeObjectState;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.WhirlwindRuntime;
import com.riiablo.engine.server.event.AnimDataFinishedEvent;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.Weapons;
import com.riiablo.item.Item;
import com.riiablo.Riiablo;

import net.mostlyoriginal.api.event.common.Subscribe;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

@All({CofReference.class, Sequence.class, AnimData.class})
public class SequenceHandler extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(SequenceHandler.class);
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<NativeObjectState> mNativeObjectState;
  protected ComponentMapper<Sequence> mSequence;
  protected ComponentMapper<AnimData> mAnimData;
  protected ComponentMapper<Casting> mCasting;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<WhirlwindRuntime> mWhirlwindRuntime;
  protected ComponentMapper<Monster> mMonster;

  protected CofManager cofs;

  @Subscribe
  public void onAnimDataFinished(AnimDataFinishedEvent event) {
    if (!mSequence.has(event.entityId)) return;
    Sequence sequence = mSequence.get(event.entityId);
    NativeObjectState shrine = mNativeObjectState.get(event.entityId);
    if (shrine != null && shrine.kind == com.riiablo.map.NativePresetObjectResolver.Kind.SHRINE) {
      AnimData anim = mAnimData.get(event.entityId);
      log.info("[SHRINE_ANIM] phase=finished entity={} mode1={} mode2={} cofMode={} "
              + "frame={} frames={} activated={} persistentMode={} sequenceStillPresent=true",
          event.entityId, sequence.mode1, sequence.mode2,
          mCofReference.get(event.entityId).mode, anim == null ? -1 : anim.frame,
          anim == null ? -1 : anim.numFrames, shrine.activated, shrine.currentMode);
    }
    Casting casting = mCasting.get(event.entityId);
    if (casting != null && casting.dragonTalonInitialized
        && casting.dragonTalonRemainingKicks > 0
        && casting.dragonTalonKickProcessed) {
      sequence.sequence(sequence.mode1, sequence.mode2);
      mAnimData.get(event.entityId).override = -1;
      com.riiablo.logger.LogManager.getLogger(SequenceHandler.class).info(
          "[ASSASSIN_DRAGON_TALON] phase=repeat_animation entity={} remaining={}",
          event.entityId, casting.dragonTalonRemainingKicks);
      return;
    }
    if (casting != null && casting.dragonClawInitialized
        && casting.dragonClawRemainingStrikes > 0
        && casting.dragonClawStrikeProcessed) {
      byte nextMode = com.riiablo.engine.Engine.Player.MODE_S4;
      sequence.sequence(nextMode, sequence.mode2);
      mAnimData.get(event.entityId).override = -1;
      com.riiablo.logger.LogManager.getLogger(SequenceHandler.class).info(
          "[ASSASSIN_DRAGON_CLAW] phase=second_animation entity={} remaining={} mode={}",
          event.entityId, casting.dragonClawRemainingStrikes, (int) nextMode);
      return;
    }
    if (casting != null && casting.dragonFlightInitialized
        && casting.dragonFlightWarped
        && !casting.dragonFlightKickProcessed) {
      byte nextMode = com.riiablo.engine.Engine.Player.MODE_KK;
      sequence.sequence(nextMode, sequence.mode2);
      mAnimData.get(event.entityId).override = -1;
      com.riiablo.logger.LogManager.getLogger(SequenceHandler.class).info(
          "[ASSASSIN_DRAGON_FLIGHT] phase=kick_animation entity={} mode={}",
          event.entityId, (int) nextMode);
      return;
    }
    if (casting != null && casting.jabRemainingStrikes > 0
        && casting.jabStrikeProcessed) {
      sequence.sequence(com.riiablo.engine.Engine.Player.MODE_A2, sequence.mode2);
      casting.jabStrikeProcessed = false;
      mAnimData.get(event.entityId).override = -1;
      com.riiablo.logger.LogManager.getLogger(SequenceHandler.class).info(
          "[AMAZON_JAB] phase=next_animation entity={} remaining={} mode={}",
          event.entityId, casting.jabRemainingStrikes,
          (int) com.riiablo.engine.Engine.Player.MODE_A2);
      return;
    }
    if (casting != null && casting.strafeInitialized
        && casting.strafeRemainingArrows > 0) {
      sequence.sequence(sequence.mode1, sequence.mode2);
      restartStrafeAnimation(event.entityId, casting);
      return;
    }
    if (casting != null && casting.furyInitialized
        && casting.furyRemainingStrikes > 0
        && casting.furyStrikeProcessed) {
      sequence.sequence(sequence.mode1, sequence.mode2);
      mAnimData.get(event.entityId).override = -1;
      com.riiablo.logger.LogManager.getLogger(SequenceHandler.class).info(
          "[DRUID_FURY] phase=repeat_animation source={} remaining={} nextTarget={}",
          event.entityId, casting.furyRemainingStrikes, casting.furyCurrentTargetId);
      return;
    }
    if (casting != null && mWhirlwindRuntime.has(event.entityId)) {
      sequence.sequence(sequence.mode1, sequence.mode2);
      mAnimData.get(event.entityId).override = -1;
      com.riiablo.logger.LogManager.getLogger(SequenceHandler.class).debug(
          "[WHIRLWIND] phase=repeat_animation entity={}", event.entityId);
      return;
    }
    // Log sequence transition for debugging
    com.riiablo.logger.Logger log = com.riiablo.logger.LogManager.getLogger(SequenceHandler.class);
    log.trace("Sequence finished for entity {}: mode1={} -> mode2={}", event.entityId, sequence.mode1, sequence.mode2);
    log.info("=== SequenceHandler.onAnimDataFinished ===");
    log.info("Entity: {}, Removing Sequence, mode1={} -> mode2={}", event.entityId, sequence.mode1, sequence.mode2);
    cofs.setMode(event.entityId, sequence.mode2);
    mAnimData.get(event.entityId).override = -1;
    mSequence.remove(event.entityId);
    log.info("After removal: Has Sequence: {}", mSequence.has(event.entityId));
  }

  @Override
  protected void process(int entityId) {
    Sequence sequence = mSequence.get(entityId);
    Casting casting = mCasting.get(entityId);
    Monster monster = mMonster.get(entityId);
    boolean monsterMelee = monster != null && casting != null
        && casting.skillId == com.riiablo.skill.SkillCodes.attack
        && Monster.isMeleeMode(sequence.mode1);
    if (monsterMelee && !sequence.started) {
      AnimData anim = mAnimData.get(entityId);
      log.info("[MONSTER_MELEE] phase=sequence_start entity={} monster={} mode1={} mode2={} "
              + "cofMode={} animFrame={} animFrames={} speed={} keyframes={} target={} skill={}",
          entityId, monster.monstats != null ? monster.monstats.Id : "unknown",
          Monster.modeName(sequence.mode1), Monster.modeName(sequence.mode2),
          Monster.modeName(mCofReference.get(entityId).mode),
          anim.frame, anim.numFrames, anim.speed,
          anim.keyframes != null ? anim.keyframes.length : 0,
          casting.targetId, casting.skillId);
    }
    if (monsterMelee && sequence.started
        && mCofReference.get(entityId).mode != sequence.mode1) {
      log.warn("[MONSTER_MELEE] phase=sequence_mode_mismatch entity={} monster={} "
              + "requestedMode={} actualMode={} target={} animFrame={} animFrames={} keyframes={}",
          entityId, monster.monstats != null ? monster.monstats.Id : "unknown",
          Monster.modeName(sequence.mode1), Monster.modeName(mCofReference.get(entityId).mode),
          casting.targetId, mAnimData.get(entityId).frame, mAnimData.get(entityId).numFrames,
          mAnimData.get(entityId).keyframes != null ? mAnimData.get(entityId).keyframes.length : 0);
    }
    if (!sequence.started) {
      if (casting != null && casting.dragonTalonInitialized) {
        casting.dragonTalonKickProcessed = false;
      }
      if (casting != null && casting.dragonClawInitialized) {
        casting.dragonClawStrikeProcessed = false;
      }
      if (casting != null && casting.furyInitialized) {
        casting.furyStrikeProcessed = false;
      }
      sequence.started = true;
      NativeObjectState shrine = mNativeObjectState.get(entityId);
      if (shrine != null && shrine.kind == com.riiablo.map.NativePresetObjectResolver.Kind.SHRINE) {
        AnimData anim = mAnimData.get(entityId);
        log.info("[SHRINE_ANIM] phase=started entity={} mode1={} mode2={} cofMode={} "
                + "frame={} frames={} activated={} persistentMode={}",
            entityId, sequence.mode1, sequence.mode2, mCofReference.get(entityId).mode,
            anim == null ? -1 : anim.frame, anim == null ? -1 : anim.numFrames,
            shrine.activated, shrine.currentMode);
      }
      // D2 starts each action at frame zero. Force the COF event even when a
      // repeated action uses the same mode (for example consecutive Throws),
      // otherwise it inherits the previous frame and can skip its keyframe.
      cofs.setMode(entityId, sequence.mode1, true);
      mAnimData.get(entityId).override = -1;
    } else if (mCofReference.get(entityId).mode != sequence.mode1) {
      // Log sequence start for debugging
      com.riiablo.logger.Logger log = com.riiablo.logger.LogManager.getLogger(SequenceHandler.class);
      log.trace("Starting sequence for entity {}: setting mode to {}", entityId, sequence.mode1);
      cofs.setMode(entityId, sequence.mode1);
      mAnimData.get(entityId).override = -1;
    }
    if (casting != null && (casting.jabRemainingStrikes > 0
        || casting.jabStrikeProcessed)) {
      // D2's hard-coded Jab sequence selects roughly one third of each A1/A2
      // animation. Advancing the complete COFs at 3x preserves its native
      // three-thrust cadence while retaining their real attack keyframes.
      AnimData animData = mAnimData.get(entityId);
      animData.override = Math.max(1, animData.speed * 3);
    }
    if (casting != null && casting.strafeInitialized) {
      AnimData animData = mAnimData.get(entityId);
      animData.override = strafeAnimationSpeed(entityId, animData.speed);
    }
  }

  /**
   * D2's sub_6FCBCFD0(Param6) does not replay Strafe from frame zero.  It
   * rewinds the current action by Param6 percent and schedules the next
   * attack event.  Keep the rewind short enough to cross the first attack
   * marker, which is what produces the rapid bow-firing cadence.
   */
  private void restartStrafeAnimation(int entityId, Casting casting) {
    AnimData anim = mAnimData.get(entityId);
    int currentFrame = anim.frame >>> 8;
    int rollbackPercent = 50;
    com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(casting.skillId);
    if (skill != null && skill.Param != null && skill.Param.length > 5
        && skill.Param[5] > 0) rollbackPercent = skill.Param[5];
    // Broadcast a forced same-mode restart so the client seeks its resident
    // animation as well. AnimDataResolver may reset the server record while
    // handling this event; the precise rollback is applied immediately after.
    cofs.setMode(entityId, mSequence.get(entityId).mode1, true);
    anim = mAnimData.get(entityId);
    // D2MOO uses the current sequence frame, not the table's total frame
    // count: nCalc = (100 - Param6) * currentFrame / 100, then it resumes at
    // currentFrame - nCalc.  With Param6=50 and a 13-frame terminal position,
    // that is frame 6 (integer truncation), not frame 7 of a 14-frame COF.
    int rollbackFrame = Math.max(0, currentFrame * rollbackPercent / 100);
    int attackFrame = firstAttackFrame(anim);
    int restartFrame = attackFrame >= 0
        ? Math.min(rollbackFrame, Math.max(0, attackFrame - 1)) : rollbackFrame;
    anim.frame = Math.min(Math.max(0, restartFrame), Math.max(0, anim.numFrames - 1));
    anim.lastKeyframeIndex = Math.max(-1, (anim.frame >>> 8) - 1);
    anim.override = strafeAnimationSpeed(entityId, anim.speed);
    // Keep SequenceHandler from invoking CofManager's ordinary frame-zero
    // mode transition on the next tick; the client receives the forced mode
    // restart and applies the same seek in CofLayerLoader.
    mSequence.get(entityId).started = true;
    log.info("[STRAFE_ANIM] phase=rollback entity={} index={} remaining={} frame={} "
            + "attackFrame={} rollbackPercent={} speed={}", entityId,
        casting.strafeArrowIndex, casting.strafeRemainingArrows, anim.frame,
        attackFrame, rollbackPercent, anim.override);
  }

  private int firstAttackFrame(AnimData anim) {
    if (anim.keyframes == null) return -1;
    for (int i = 0; i < anim.keyframes.length; i++) {
      if (anim.keyframes[i] == com.riiablo.engine.Engine.KEYFRAME_ATK) return i;
    }
    return -1;
  }

  /** Weapon IAS/base bow speed affects Strafe's visible attack cadence. */
  private int strafeAnimationSpeed(int entityId, int baseSpeed) {
    // Mirrors D2Common's UNITS_UpdateAttackAnimRateAndVelocity:
    // nRate = effective IAS + STAT_ATTACKRATE - 30 (SEQUENCE), clamped to
    // [15,175], then multiplied by the animation table's base speed.
    int attackRate = 100;
    if (mAttributesWrapper.has(entityId)
        && mAttributesWrapper.get(entityId).attrs != null) {
      StatRef base = mAttributesWrapper.get(entityId).attrs
          .get(Stat.attackrate, StatRef.obtain());
      if (base != null) attackRate = base.asInt();
    }
    boolean aggregateIas = false;
    int fasterAttackRate = 0;
    if (mAttributesWrapper.has(entityId)
        && mAttributesWrapper.get(entityId).attrs != null) {
      com.riiablo.attributes.Attributes attrs = mAttributesWrapper.get(entityId).attrs;
      StatRef ias = attrs.get(Stat.item_fasterattackrate, StatRef.obtain());
      // ItemStatCost entries that are not character-base stats remain in the
      // character's remaining list after ItemData.updateStats(). D2 still
      // includes them in STATLIST_UnitGetItemStatOrSkillStatValue().
      if (ias == null) ias = attrs.remaining().get(Stat.item_fasterattackrate, StatRef.obtain());
      if (ias != null) {
        fasterAttackRate = ias.asInt();
        aggregateIas = true;
      }
    }
    if (mPlayer.has(entityId) && mPlayer.get(entityId).data != null) {
      Item weapon = mPlayer.get(entityId).data.getItems().getEquippedRangedWeapon();
      if (weapon != null) {
        if (weapon.base instanceof Weapons.Entry) {
          int weaponAttackRate = itemStatInt(weapon, Stat.attackrate,
              -((Weapons.Entry) weapon.base).speed);
          attackRate += weaponAttackRate;
        }
        StatRef ias = !aggregateIas && weapon.attrs != null
            ? weapon.attrs.get(Stat.item_fasterattackrate, StatRef.obtain()) : null;
        if (ias == null && !aggregateIas && weapon.attrs != null) {
          ias = weapon.attrs.base().get(Stat.item_fasterattackrate, StatRef.obtain());
        }
        if (ias != null) fasterAttackRate = ias.asInt();
      }
    }
    if (fasterAttackRate > 0) {
      fasterAttackRate = 120 * fasterAttackRate / (fasterAttackRate + 120);
    }
    int sequenceRate = attackRate + fasterAttackRate - 30;
    sequenceRate = Math.max(15, Math.min(175, sequenceRate));
    return Math.max(1, baseSpeed * sequenceRate / 100);
  }

  private static int itemStatInt(Item item, short stat, int fallback) {
    if (item == null || item.attrs == null) return fallback;
    StatRef ref = item.attrs.get(stat, StatRef.obtain());
    if (ref == null) ref = item.attrs.base().get(stat, StatRef.obtain());
    return ref == null ? fallback : ref.asInt();
  }
}
