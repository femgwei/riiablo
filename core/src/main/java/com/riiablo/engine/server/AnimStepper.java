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
  protected ComponentMapper<com.riiablo.engine.server.component.SummonedPet> mSummonedPet;

  protected CofManager cofs;
  @com.artemis.annotations.Wire(name = "serverContext", failOnNull = false)
  protected ServerGameplayContext serverContext;

  protected EventSystem events;

  private com.riiablo.Files files() {
    return serverContext != null ? serverContext.files() : com.riiablo.Riiablo.files;
  }

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
    com.riiablo.engine.server.component.SummonedPet trap = mSummonedPet.get(entityId);
    if (trap != null && trap.infernoChanneling) return;
    UnitStates states = mUnitStates.get(entityId);
    if (states != null && states.stateList != null
        && states.stateList.hasState(com.riiablo.engine.server.state.StateId.FREEZE)) {
      // FREEZE is a live-unit control state.  Check it before both ordinary
      // COF playback and native Jab sequences so no attack/death animation
      // advances while the unit is frozen.
      return;
    }
    Sequence sequence = mSequence.get(entityId);
    Casting casting = mCasting.get(entityId);
    if (casting != null && casting.diabRun != null && sequence != null && sequence.started) {
      NativeDiabRun run = casting.diabRun;
      if (run.remaining <= 0) {
        events.dispatch(AnimDataFinishedEvent.obtain(entityId));
        return;
      }
      int point = run.cursor >>> 8;
      animData.frame = point << 8;
      events.dispatch(new com.riiablo.engine.server.event.DiabRunUpdateEvent(entityId));
      if (run.tail && NativeDiabRun.action(point) == Engine.KEYFRAME_ATK && !run.hitProcessed)
        events.dispatch(AnimDataKeyframeEvent.obtain(entityId, Engine.KEYFRAME_ATK));
      run.cursor = (run.cursor + 256) % (22 << 8);
      run.remaining -= 256;
      animData.frame = run.cursor;
      return;
    }
    NativeMonInferno inferno = casting == null ? null : casting.monInferno;
    if (inferno != null && sequence != null && sequence.started) {
      inferno.frame++;
      if (inferno.horrorSequence && !inferno.started) {
        // seq_horrorarcticblast: fourteen A2 source points, action 1 on point 9.
        // UNITS_InitializeSequence uses 256, independent of the backing COF rate.
        int delta = 256;
        if (states != null && states.stateList != null)
          delta = scaleStateAnimationSpeed(delta, states.stateList.getTotalAnimationRateModifier());
        int previous = inferno.sequenceCursor;
        inferno.sequenceCursor = Math.min(14 << 8, previous + delta);
        animData.frame = Math.min(13 << 8, inferno.sequenceCursor);
        if (previous < (9 << 8) && inferno.sequenceCursor >= (9 << 8))
          events.dispatch(AnimDataKeyframeEvent.obtain(entityId, Engine.KEYFRAME_ATK));
        return;
      }
      if (inferno.started) {
        if (inferno.horrorSequence) {
          int delta = 256;
          if (states != null && states.stateList != null)
            delta = scaleStateAnimationSpeed(delta, states.stateList.getTotalAnimationRateModifier());
          inferno.sequenceCursor = (inferno.sequenceCursor + delta) % (14 << 8);
          animData.frame = inferno.sequenceCursor;
        }
        // Once SrvDo095/152 runs it deletes the ordinary COF MODECHANGE/ENDANIM
        // events. Only the scheduled callback and InfernoLen tail may fire.
        if (inferno.nextUpdate >= 0 && inferno.frame >= inferno.nextUpdate)
          events.dispatch(com.riiablo.engine.server.event.MonInfernoUpdateEvent.obtain(entityId));
        else if (inferno.endFrame >= 0 && inferno.frame >= inferno.endFrame) {
          log.info("[MON_INFERNO] phase=end source={} skill={} frame={} missiles={}",
              entityId, casting.skillId, inferno.frame, inferno.missiles);
          events.dispatch(AnimDataFinishedEvent.obtain(entityId));
        } else if (!inferno.horrorSequence && animData.numFrames > 0) {
          animData.frame = (animData.frame + Math.max(0, animData.speed)) % animData.numFrames;
        }
        return;
      }
    }
    if (sequence != null && sequence.nativeFrogHidden) {
      // UNITS_InitializeSequence starts at source frame 0. The native mode
      // update invokes the used skill even with no MonSeq nActionFrame.
      if (sequence.started && !sequence.nativeSubmergeHeld) {
        animData.frame = 0;
        events.dispatch(com.riiablo.engine.server.event.SubmergeSequenceUpdateEvent.obtain(entityId));
      }
      return;
    }
    if (sequence != null && sequence.nativeSubmerge) {
      processNativeSubmerge(entityId, sequence, animData, states);
      return;
    }
    if (sequence != null && sequence.nativeDeathMaul) {
      processNativeDeathMaul(entityId, sequence, animData, states);
      return;
    }
    if (sequence != null && sequence.nativeMummy) {
      processNativeMummy(entityId, sequence, animData, states);
      return;
    }
    if (sequence != null && sequence.nativeJab) {
      processNativeJab(entityId, sequence, animData);
      return;
    }
    if (sequence != null && sequence.nativeMonster != null) {
      if (!sequence.started) return;
      Monster monster = mMonster.get(entityId);
      boolean sandLeap = casting != null && casting.monsterLeapStarted
          && monster != null && monster.monstats != null
          && "sandleaper1".equals(monster.monstats.BaseId)
          && files().skills.get(casting.skillId) != null
          && files().skills.get(casting.skillId).srvdofunc == 77;
      // SKILLS_Leap holds leapers at frame 8 while their path is active.
      // Resume at 12 after landing, skipping the flight callbacks at 9/11.
      if (world.getMapper(com.riiablo.engine.server.component.Leap.class).has(entityId)) {
        if (sandLeap) animData.frame = 8 << 8;
        return;
      }
      if (sandLeap && !casting.monsterLeapHit) {
        casting.monsterLeapHit = true;
        sequence.nativeMonsterCursor = 12 << 8;
        animData.frame = 12 << 8;
        events.dispatch(com.riiablo.engine.server.event.LeapLandEvent.obtain(entityId));
        return;
      }
      NativeMonsterSequence data = sequence.nativeMonster;
      int delta = 256;
      if (states != null && states.stateList != null)
        delta = scaleStateAnimationSpeed(delta, states.stateList.getTotalAnimationRateModifier());
      int previous = sequence.nativeMonsterCursor;
      if (previous >= data.frames.length << 8) return;
      int next = Math.min(data.frames.length << 8, previous + delta);
      boolean charge = casting != null && files().skills.get(casting.skillId) != null
          && files().skills.get(casting.skillId).srvdofunc == 67;
      int pointIndex = Math.min(data.frames.length - 1, next >>> 8);
      boolean movingPoint = data.modes[pointIndex] == Engine.Monster.MODE_WL
          || data.modes[pointIndex] == Engine.Monster.MODE_RN;
      if (charge && !movingPoint && sequence.mode1 != Engine.Monster.MODE_A2) {
        com.riiablo.engine.server.component.Position source = world.getMapper(
            com.riiablo.engine.server.component.Position.class).get(entityId);
        com.riiablo.engine.server.component.Position target = casting.targetId < 0 ? null : world.getMapper(
            com.riiablo.engine.server.component.Position.class).get(casting.targetId);
        if (source == null || target == null || ++sequence.nativeMonsterWaitFrames > 125) {
          events.dispatch(AnimDataFinishedEvent.obtain(entityId)); return;
        }
        Actioneer actions = world.getSystem(Actioneer.class);
        if (actions != null ? !actions.isInMeleeRange(entityId, casting.targetId, 0)
            : source.position.dst2(target.position) > 9) return;
        Pathfinder paths = world.getSystem(Pathfinder.class);
        if (paths != null) paths.findPath(entityId, null);
      }
      if (sequence.mode1 != data.modes[pointIndex]) {
        sequence.mode1 = data.modes[pointIndex];
        if (mCofReference.has(entityId)) cofs.setMode(entityId, sequence.mode1, true);
      }
      sequence.nativeMonsterCursor = next;
      animData.frame = data.frames[Math.min(data.frames.length - 1, next >>> 8)] << 8;
      // DATATBLS_ComputeSequenceAnimation discards intermediate events: only
      // the last nonzero event crossed by this tick survives, even a sound.
      int actionPoint = data.lastActionPoint(previous, next);
      if (actionPoint >= 0) {
        boolean movement = data.modes[actionPoint] == Engine.Monster.MODE_WL
            || data.modes[actionPoint] == Engine.Monster.MODE_RN;
        byte action = data.actions[actionPoint];
        if (action != Engine.KEYFRAME_SND && !(charge && movement))
          events.dispatch(AnimDataKeyframeEvent.obtain(entityId, action));
      }
      if (next == data.frames.length << 8) events.dispatch(AnimDataFinishedEvent.obtain(entityId));
      return;
    }
    if (animData.numFrames <= 0) return;

    int delta = animData.override >= 0 ? animData.override : animData.speed;
    if (states != null && states.stateList != null) {
      // Native FREEZE stops the current action frame while the unit is alive.
      // Death processing removes/retains the state before the next animation
      // tick, allowing an ordinary DT sequence to play when appropriate.
      delta = scaleStateAnimationSpeed(
          delta, states.stateList.getTotalAnimationRateModifier());
    }
    if (delta < 0) delta = 0;
    // Zero-speed idle data is valid for native scenery monsters (windows).
    // Diagnose only an actual player stall, but do not change native rates.
    if (delta == 0 && mPlayer.has(entityId)) {
      log.warn("[ATTACK_ANIM] stalled entity={} frame={} mode animation has zero delta",
          entityId, animData.frame);
    }
    int nextFrame = animData.frame + delta;
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

  private static final int[] DEATH_MAUL_FRAMES = {
      0,0,1,1,2,2,3,3,4,4,5,6,7,7,7,7,8,8,8,8,9,9,9,9,9,10,10,10,10,11,11,11,11,12,13
  };

  private void processNativeDeathMaul(int entityId, Sequence sequence, AnimData animData,
      UnitStates states) {
    int end = DEATH_MAUL_FRAMES.length << 8;
    if (!sequence.started || sequence.nativeDeathMaulFrame >= end) return;
    // D2Common UNITS_InitializeSequence defaults to 256, independent of A2 COF speed.
    int delta = 256;
    if (states != null && states.stateList != null)
      delta = scaleStateAnimationSpeed(delta, states.stateList.getTotalAnimationRateModifier());
    int previous = sequence.nativeDeathMaulFrame;
    int next = Math.min(end, previous + delta);
    sequence.nativeDeathMaulFrame = next;
    animData.frame = DEATH_MAUL_FRAMES[Math.min(DEATH_MAUL_FRAMES.length - 1, next >>> 8)] << 8;
    if (previous < (13 << 8) && next >= (13 << 8))
      events.dispatch(AnimDataKeyframeEvent.obtain(entityId, Engine.KEYFRAME_ATK));
    if (next == end) events.dispatch(AnimDataFinishedEvent.obtain(entityId));
  }

  /** MonSeq event points supersede variant-specific SC COF keyframes. */
  private void processNativeMummy(int entityId, Sequence sequence, AnimData animData,
      UnitStates states) {
    if (!sequence.started) return;
    int delta = Math.max(0, animData.speed);
    if (states != null && states.stateList != null) {
      delta = scaleStateAnimationSpeed(delta, states.stateList.getTotalAnimationRateModifier());
    }
    int previous = sequence.nativeMummyFrame;
    int next = Math.min(16 << 8, previous + delta);
    sequence.nativeMummyFrame = next;
    animData.frame = Math.min(15 << 8, next);
    if (previous < (13 << 8) && next >= (13 << 8)) {
      events.dispatch(AnimDataKeyframeEvent.obtain(entityId, Engine.KEYFRAME_MIS));
    }
    if (next >= (16 << 8)) events.dispatch(AnimDataFinishedEvent.obtain(entityId));
  }

  /** Reverse S1 points; SrvDo094 holds at source frame zero until AI emerges. */
  private void processNativeSubmerge(int entityId, Sequence sequence, AnimData animData,
      UnitStates states) {
    if (!sequence.started || sequence.nativeSubmergeHeld) return;
    int speed = Math.max(0, animData.speed);
    if (states != null && states.stateList != null)
      speed = scaleStateAnimationSpeed(speed, states.stateList.getTotalAnimationRateModifier());
    int last = sequence.nativeSubmergeLastFrame;
    int next = Math.min(last << 8, sequence.nativeSubmergeFrame + speed);
    sequence.nativeSubmergeFrame = next;
    int point = next >>> 8;
    for (int i = sequence.nativeSubmergeLastPoint + 1; i <= point; i++) {
      int sourceFrame = last - i;
      animData.frame = sourceFrame << 8;
      // These action points are from MonSeq, not S1's emergence COF markers.
      if (i < 4 || sourceFrame <= (last == 12 ? 3 : 2)) {
        events.dispatch(AnimDataKeyframeEvent.obtain(entityId, Engine.KEYFRAME_ATK));
      }
    }
    sequence.nativeSubmergeLastPoint = point;
    animData.frame = (last - point) << 8;
    // No AnimDataFinished: native sequence speed is zero at the last point.
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
