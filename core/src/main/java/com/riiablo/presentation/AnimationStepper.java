package com.riiablo.presentation;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IntervalIteratingSystem;
import com.riiablo.engine.SimulationClock;
import com.riiablo.presentation.component.AnimationWrapper;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.Item;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;

@All(AnimationWrapper.class)
public class AnimationStepper extends IntervalIteratingSystem {
  protected ComponentMapper<AnimationWrapper> mAnimationWrapper;
  protected ComponentMapper<Item> mItem;
  protected ComponentMapper<AnimData> mAnimData;
  protected ComponentMapper<Sequence> mSequence;
  protected ComponentMapper<com.riiablo.engine.server.component.Casting> mCasting;
  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<com.riiablo.engine.server.component.SummonedPet> mSummonedPet;
  protected ComponentMapper<com.riiablo.engine.server.component.Object> mObject;
  protected ComponentMapper<com.riiablo.presentation.component.CofWrapper> mCofWrapper;
  protected ComponentMapper<com.riiablo.engine.shared.component.CofDirtyComponents> mCofDirty;
  protected ComponentMapper<com.riiablo.presentation.component.CofLoadingComponents> mCofLoading;

  public AnimationStepper() {
    super(null, SimulationClock.STEP_SECONDS);
  }

  @Override
  protected void process(int entityId) {
    AnimationWrapper wrapper = mAnimationWrapper.get(entityId);
    if (wrapper == null || wrapper.animation == null) return;

    // Ground items use a standalone DC6 flippy animation.  COF/DCC loading
    // markers belong to composite units only; allowing them to gate a flippy
    // can leave a dropped coin permanently on an intermediate bounce frame.
    if (mItem.has(entityId)) {
      wrapper.animation.update(SimulationClock.STEP_SECONDS);
      return;
    }

    // Mode resolution and DCC publication are asynchronous. Keep every action
    // on its resident frame until the selected composite is ready; otherwise
    // an attack's rate advances the previous walk/idle layers while loading.
    com.riiablo.presentation.component.CofWrapper cof = mCofWrapper.get(entityId);
    if (mCofDirty.has(entityId) || mCofLoading.has(entityId)
        || cof != null && cof.cof != wrapper.animation.getCOF()) return;

    UnitStates states = mUnitStates.get(entityId);
    if (states != null && states.stateList != null
        && states.stateList.hasState(StateId.FREEZE)) {
      // The authoritative server freezes AnimData as well. Keep the client
      // presentation on the current frame instead of continuing to animate
      // a monster whose world position is already stationary.
      return;
    }

    Sequence sequence = mSequence.get(entityId);
    AnimData animData = mAnimData.get(entityId);
    com.riiablo.engine.server.component.Casting cast = mCasting.get(entityId);
    boolean inferno = cast != null && cast.monInferno != null
        && (cast.monInferno.started || cast.monInferno.horrorSequence);
    com.riiablo.engine.server.component.SummonedPet trap = mSummonedPet.get(entityId);
    if (animData != null && ((trap != null && trap.infernoChanneling)
        || inferno || sequence != null && (sequence.nativeMonster != null || sequence.nativeJab || sequence.nativeSubmerge || sequence.nativeFrogHidden || sequence.nativeDiabRun || sequence.nativeDeathMaul || sequence.nativeMummy))) {
      // SQ presentation follows the authoritative source-frame mapping.
      // In particular Submerge reverses S1 and holds at zero; looping the
      // resident emergence COF forward would show the opposite action.
      int frameCount = wrapper.animation.getNumFramesPerDir();
      if (frameCount > 0) {
        int frame = Math.max(0, Math.min(frameCount - 1, animData.frame >>> 8));
        wrapper.animation.setFrame(frame);
      }
      return;
    }

    // CofLayerCacher only runs while assets are loading. Resident COFs must
    // follow live authoritative rates too, including recovery from zero and
    // clearing an attack/movement override. Do not restart fractional playback.
    // Objects retain Objects.txt's per-unit visual rates set by the cacher.
    if (animData != null && !mObject.has(entityId)) {
      int rate = Math.max(0, animData.override >= 0 ? animData.override : animData.speed);
      if (wrapper.animation.getFrameDelta() != rate) wrapper.animation.setFrameDelta(rate);
    }
    wrapper.animation.update(SimulationClock.STEP_SECONDS);
  }
}
