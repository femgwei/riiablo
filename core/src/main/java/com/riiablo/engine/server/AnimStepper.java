package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IntervalIteratingSystem;
import net.mostlyoriginal.api.event.common.EventSystem;

import com.riiablo.engine.Engine;
import com.riiablo.engine.SimulationClock;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.AnimDataFinishedEvent;
import com.riiablo.engine.server.event.AnimDataKeyframeEvent;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

@All(AnimData.class)
public class AnimStepper extends IntervalIteratingSystem {
  private static final Logger log = LogManager.getLogger(AnimStepper.class);

  protected ComponentMapper<AnimData> mAnimData;
  protected ComponentMapper<Casting> mCasting;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<Sequence> mSequence;

  protected CofManager cofs;

  protected EventSystem events;

  public AnimStepper() {
    super(null, SimulationClock.STEP_SECONDS);
  }

  @Override
  protected void process(int entityId) {
    AnimData animData = mAnimData.get(entityId);
    // Artemis may remove components while an interval system is draining its
    // subscription.  A death/removal event can therefore leave an entity id
    // in this batch with no AnimData anymore; treat it as a completed
    // animation instead of dereferencing a stale component.
    if (animData == null) return;
    Sequence sequence = mSequence.get(entityId);
    if (sequence != null && sequence.nativeJab) {
      processNativeJab(entityId, sequence, animData);
      return;
    }
    if (animData.numFrames <= 0) return;

    int delta = animData.override >= 0 ? animData.override : animData.speed;
    UnitStates states = mUnitStates.get(entityId);
    if (states != null && states.stateList != null) {
      delta = scaleStateAnimationSpeed(
          delta, states.stateList.getTotalAnimationRateModifier());
    }
    if (delta < 0) delta = 0;
    int nextFrame = animData.frame + delta;
    if (delta == 0) {
      log.warn("[ATTACK_ANIM] stalled entity={} frame={} mode animation has zero delta",
          entityId, animData.frame);
    }
    while (nextFrame >= animData.numFrames) {
      nextFrame -= animData.numFrames;
      dispatchKeyframes(entityId, animData, true);
      logMonsterMeleeFinished(entityId, animData);
      if (mCasting.has(entityId) && mPlayer.has(entityId)) {
        com.badlogic.gdx.Gdx.app.log("AnimStepper", String.format(
            "[ATTACK_ANIM] phase=finished entity=%d frame=%d frames=%d lastKeyframe=%d",
            entityId, animData.frame, animData.numFrames, animData.lastKeyframeIndex));
      }
      // A repeated native action may seek the animation during the finished
      // event (Strafe's Param6 rollback). Preserve that authoritative seek
      // instead of overwriting it with the wrapped frame below.
      int frameBeforeFinished = animData.frame;
      events.dispatch(AnimDataFinishedEvent.obtain(entityId));
      boolean animationWasRewound = animData.frame != frameBeforeFinished;
      if (animationWasRewound) {
        nextFrame = animData.frame;
      }
      if (!animationWasRewound) animData.lastKeyframeIndex = -1;
    }
    animData.frame = nextFrame;

    if (animData.keyframes == null || animData.keyframes.length == 0) {
      return;
    }
    dispatchKeyframes(entityId, animData, false);
  }

  static int scaleStateAnimationSpeed(int baseSpeed, int modifierPercent) {
    if (baseSpeed <= 0) return 0;
    int percent = Math.max(10, Math.min(300, 100 + modifierPercent));
    return Math.max(1, (int) ((long) baseSpeed * percent / 100L));
  }

  private void dispatchKeyframes(int entityId, AnimData animData, boolean beforeWrap) {
    if (animData.keyframes == null || animData.keyframes.length == 0) return;
    int currentIndex = animData.frame >>> 8;
    int maxIndex = animData.keyframes.length - 1;
    if (currentIndex > maxIndex) currentIndex = maxIndex;

    int start = animData.lastKeyframeIndex + 1;
    if (start < 0) start = 0;
    int frameBeforeDispatch = animData.frame;
    if (start <= currentIndex) {
      dispatchRange(entityId, animData.keyframes, start, currentIndex);
    } else if (beforeWrap) {
      dispatchRange(entityId, animData.keyframes, start, maxIndex);
    }
    // A native sequence callback can rewind the animation immediately after
    // its keyframe (Strafe's Param6 path). Do not overwrite that seek with
    // the pre-rewind keyframe index, or the next cycle will skip the marker.
    if (animData.frame != frameBeforeDispatch) {
      animData.lastKeyframeIndex = Math.max(-1, (animData.frame >>> 8) - 1);
    } else {
      animData.lastKeyframeIndex = currentIndex;
    }
  }

  /** Advances Jab's D2Common sequence cursor and emits its native hit points. */
  private void processNativeJab(int entityId, Sequence sequence, AnimData animData) {
    int frameCount = sequence.nativeJabFrameCount;
    if (frameCount <= 0) return;
    int oldFrame = sequence.nativeJabFrame >>> 8;
    if (!sequence.started) return;
    if (sequence.nativeJabFrame == 0) {
      // UNITS_InitializeSequence immediately resolves sequence point zero;
      // it is not the zero frame of the A1 COF.
      animData.frame = NativeJabSequence.sourceFrame(
          sequence.nativeJabWeaponClass, 0) << 8;
      animData.lastKeyframeIndex = Math.max(-1, (animData.frame >>> 8) - 1);
    }

    int delta = Math.max(1, sequence.nativeJabSpeed);
    UnitStates states = mUnitStates.get(entityId);
    if (states != null && states.stateList != null) {
      delta = scaleStateAnimationSpeed(delta, states.stateList.getTotalAnimationRateModifier());
    }
    int next = sequence.nativeJabFrame + delta;
    int end = frameCount << 8;
    if (next >= end) {
      dispatchNativeJabPoints(entityId, sequence, oldFrame, frameCount - 1, animData);
      sequence.nativeJabFrame = end;
      events.dispatch(AnimDataFinishedEvent.obtain(entityId));
      return;
    }

    sequence.nativeJabFrame = next;
    int current = next >>> 8;
    if (current >= oldFrame) {
      dispatchNativeJabPoints(entityId, sequence, oldFrame, current, animData);
    }
  }

  private void dispatchNativeJabPoints(int entityId, Sequence sequence,
      int oldFrame, int newFrame, AnimData animData) {
    for (int i = oldFrame + 1; i <= newFrame; i++) {
      if (i >= sequence.nativeJabFrameCount) break;
      byte mode = NativeJabSequence.mode(sequence.nativeJabWeaponClass, i);
      if (!mCofReference.has(entityId) || mCofReference.get(entityId).mode != mode) {
        cofs.setMode(entityId, mode);
        animData = mAnimData.get(entityId);
      }
      if (animData == null) return;
      int sourceFrame = NativeJabSequence.sourceFrame(sequence.nativeJabWeaponClass, i);
      animData.frame = sourceFrame << 8;
      animData.lastKeyframeIndex = Math.max(-1, sourceFrame - 1);
      // The client uses AnimData.override as its playback delta when a COF
      // mode changes. Keep it positive; zero would freeze the A1/A2 layer
      // even though the server-side SQ cursor continues to advance.
      animData.override = Math.max(1, sequence.nativeJabSpeed);
      if (NativeJabSequence.event(sequence.nativeJabWeaponClass, i)
          == NativeJabSequence.EVENT_MELEE_ATTACK) {
        events.dispatch(AnimDataKeyframeEvent.obtain(entityId, Engine.KEYFRAME_ATK));
      }
    }
  }

  private void dispatchRange(int entityId, byte[] keyframes, int start, int end) {
    if (start > end) return;
    for (int i = start; i <= end; i++) {
      byte keyframe = keyframes[i];
      if (keyframe > Engine.KEYFRAME_NIL) {
        log.debug("broadcasting AnimDataKeyframeEvent({},{})", entityId, Engine.getKeyframe(keyframe));
        logMonsterMeleeKeyframe(entityId, i, keyframe);
        if (mCasting.has(entityId) && mPlayer.has(entityId)) {
          com.badlogic.gdx.Gdx.app.log("AnimStepper", String.format(
              "[ATTACK_ANIM] phase=keyframe entity=%d frame=%d keyframe=%s",
              entityId, i, Engine.getKeyframe(keyframe)));
        }
        events.dispatch(AnimDataKeyframeEvent.obtain(entityId, keyframe));
      }
    }
  }

  private void logMonsterMeleeKeyframe(int entityId, int frame, byte keyframe) {
    if (!isMonsterMelee(entityId)) return;
    Casting casting = mCasting.get(entityId);
    Monster monster = mMonster.get(entityId);
    int mode = mCofReference.get(entityId).mode;
    log.info("[MONSTER_MELEE] phase=keyframe entity={} monster={} mode={} skill={} target={} "
            + "frame={} keyframe={} resurrected={} resurrectedBy={} playerRevive={}",
        entityId, monster.monstats != null ? monster.monstats.Id : "unknown",
        Monster.modeName(mode), casting.skillId, casting.targetId, frame,
        Engine.getKeyframe(keyframe), monster.resurrected, monster.resurrectedBy,
        monster.playerRevive);
  }

  private void logMonsterMeleeFinished(int entityId, AnimData animData) {
    if (!isMonsterMelee(entityId)) return;
    Casting casting = mCasting.get(entityId);
    Monster monster = mMonster.get(entityId);
    int mode = mCofReference.get(entityId).mode;
    int keyframeCount = animData.keyframes != null ? animData.keyframes.length : 0;
    if (keyframeCount == 0) {
      log.warn("[MONSTER_MELEE] phase=animation_finished entity={} monster={} mode={} skill={} "
              + "target={} result=no_keyframes resurrected={} resurrectedBy={} playerRevive={}",
          entityId, monster.monstats != null ? monster.monstats.Id : "unknown",
          Monster.modeName(mode), casting.skillId, casting.targetId, monster.resurrected,
          monster.resurrectedBy, monster.playerRevive);
    } else {
      log.info("[MONSTER_MELEE] phase=animation_finished entity={} monster={} mode={} skill={} "
              + "target={} keyframes={} lastKeyframe={} resurrected={} resurrectedBy={} playerRevive={}",
          entityId, monster.monstats != null ? monster.monstats.Id : "unknown",
          Monster.modeName(mode), casting.skillId, casting.targetId, keyframeCount,
          animData.lastKeyframeIndex, monster.resurrected, monster.resurrectedBy,
          monster.playerRevive);
    }
  }

  private boolean isMonsterMelee(int entityId) {
    if (!mMonster.has(entityId) || !mCasting.has(entityId) || !mCofReference.has(entityId)) {
      return false;
    }
    return mCasting.get(entityId).skillId == com.riiablo.skill.SkillCodes.attack
        && Monster.isMeleeMode(mCofReference.get(entityId).mode);
  }
}
