package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.audio.Audio;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import org.junit.jupiter.api.Test;

class InfernoAudioSystemTest extends RiiabloTest {
  @Test void deferredLoopIsNotStackedByChannelRefreshesOrFlameMissiles() {
    try (Fixture f = new Fixture()) {
      f.channel(SkillId.INFERNO);
      for (int i = 0; i < 80; i++) {
        int id = f.world.create();
        Missile flame = f.world.getMapper(Missile.class).create(id);
        flame.missile = Riiablo.files.Missiles.get(i % 2 == 0 ? "infernoflame1" : "infernoflame2");
        flame.ownerId = f.owner;
        MissileTravelAudio.play(f.world, f.context, id, flame.missile.TravelSound);
        f.channel(SkillId.INFERNO);
        f.world.process();
        f.world.delete(id);
      }
      verify(f.audio, times(1)).play("sorceress_inferno", true);
      verify(f.inferno, never()).stop();
      f.world.getMapper(UnitStates.class).get(f.owner).stateList.removeState(StateId.INFERNO);
      f.world.process();
      verify(f.inferno, times(1)).stop();
      f.channel(SkillId.INFERNO);
      f.world.process();
      verify(f.audio, times(2)).play("sorceress_inferno", true);
    }
  }

  @Test void switchingChannelsAndRemovingCasterStopTheirOwnedLoops() {
    try (Fixture f = new Fixture()) {
      f.channel(SkillId.INFERNO);
      f.world.process();
      f.channel(SkillId.ARCTIC_BLAST);
      f.world.process();
      verify(f.inferno, times(1)).stop();
      verify(f.audio, times(1)).play("druid_arcticblast", true);
      f.world.delete(f.owner);
      f.world.process();
      verify(f.arctic, times(1)).stop();
      assertTrue(MissileTravelAudio.isStateOwnedLoop(
          Riiablo.files.Missiles.get("arcticblast1"), "druid_arcticblast"));
      assertFalse(MissileTravelAudio.isStateOwnedLoop(
          Riiablo.files.Missiles.get("fetishinferno1"), "sorceress_inferno"));
    }
  }

  private static final class Fixture implements AutoCloseable {
    final Audio audio = mock(Audio.class);
    final Audio.Instance inferno = mock(Audio.Instance.class), arctic = mock(Audio.Instance.class);
    final ClientGameplayContext context = mock(ClientGameplayContext.class);
    final World world;
    final int owner;
    Fixture() {
      when(context.audio()).thenReturn(audio);
      when(audio.play("sorceress_inferno", true)).thenReturn(inferno);
      when(audio.play("druid_arcticblast", true)).thenReturn(arctic);
      world = new World(new WorldConfigurationBuilder().with(new InfernoAudioSystem())
          .build().register("clientContext", context));
      owner = world.create();
      when(context.listener()).thenReturn(owner);
      world.getMapper(Position.class).create(owner);
      world.getMapper(UnitStates.class).create(owner).init(owner);
    }
    void channel(int skillId) {
      UnitStates states = world.getMapper(UnitStates.class).get(owner);
      UnitState state = states.stateList.getState(StateId.INFERNO);
      if (state == null) state = states.stateList.addState(StateId.INFERNO, 20, 1, owner);
      state.skillId = skillId;
    }
    @Override public void close() { world.dispose(); }
  }
}
