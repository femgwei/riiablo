package com.riiablo.presentation;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.utils.IntMap;
import com.riiablo.audio.Audio;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;

/** One native looping sound for the caster's Inferno/Arctic Blast state. */
@All({UnitStates.class, Position.class})
public final class InfernoAudioSystem extends IteratingSystem {
  protected ComponentMapper<UnitStates> mUnitStates;
  @Wire(name = "clientContext") protected ClientGameplayContext clientContext;
  private final IntMap<Audio.Instance> loops = new IntMap<>();
  private final IntMap<String> sounds = new IntMap<>();

  @Override protected void process(int entityId) {
    UnitStates states = mUnitStates.get(entityId);
    UnitState channel = states.stateList == null ? null : states.stateList.getState(StateId.INFERNO);
    String sound = channel == null ? null
        : channel.skillId == SkillId.INFERNO ? "sorceress_inferno"
        : channel.skillId == SkillId.ARCTIC_BLAST ? "druid_arcticblast" : null;
    if (sound == null) {
      stop(entityId);
      return;
    }
    if (!sound.equals(sounds.get(entityId))) stop(entityId);
    Audio.Instance loop = loops.get(entityId);
    if (loop == null && clientContext.audio() != null) {
      loop = clientContext.audio().play(sound, true);
      if (loop != null) {
        loops.put(entityId, loop);
        sounds.put(entityId, sound);
      }
    }
    // A deferred/paused instance is already owned; never start it again.
    if (loop != null) loop.setVolume(SpatialAudio.gain(world, clientContext.listener(), entityId));
  }

  @Override protected void removed(int entityId) { stop(entityId); }

  private void stop(int entityId) {
    sounds.remove(entityId);
    Audio.Instance loop = loops.remove(entityId);
    if (loop != null) loop.stop();
  }

  @Override protected void dispose() {
    for (Audio.Instance loop : loops.values()) loop.stop();
    loops.clear();
    sounds.clear();
  }
}
