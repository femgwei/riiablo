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
      mAnimData.get(event.entityId).override = -1;
      log.info("[STRAFE_ANIM] phase=repeat_sequence entity={} index={} remaining={}",
          event.entityId, casting.strafeArrowIndex, casting.strafeRemainingArrows);
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
  }
}
