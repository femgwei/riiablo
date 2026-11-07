package com.riiablo.presentation;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.utils.IntMap;
import com.riiablo.audio.Audio;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;

/** One native Hurricane loop per active caster, independent of short-lived missiles. */
@All({UnitStates.class, Position.class})
public final class HurricaneAudioSystem extends IteratingSystem {
  protected ComponentMapper<UnitStates> mUnitStates;
  @Wire(name = "clientContext") protected ClientGameplayContext clientContext;
  private final IntMap<Audio.Instance> loops = new IntMap<>();

  @Override protected void process(int entityId) {
    UnitStates states = mUnitStates.get(entityId);
    if (states.stateList == null || !states.stateList.hasState(StateId.HURRICANE)) {
      stop(entityId);
      return;
    }
    Audio.Instance loop = loops.get(entityId);
    if (loop == null && clientContext.audio() != null) {
      loop = clientContext.audio().play("druid_hurricane", true);
      if (loop != null) loops.put(entityId, loop);
    }
    // Deferred playback is already owned: isPlaying()==false must not cause
    // another start while the WAV is loading or the application is paused.
    if (loop != null) loop.setVolume(SpatialAudio.gain(world, clientContext.listener(), entityId));
  }

  @Override protected void removed(int entityId) { stop(entityId); }

  private void stop(int entityId) {
    Audio.Instance loop = loops.remove(entityId);
    if (loop != null) loop.stop();
  }

  @Override protected void dispose() {
    for (Audio.Instance loop : loops.values()) loop.stop();
    loops.clear();
  }
}
