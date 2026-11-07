package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Interpolation;
import com.riiablo.Riiablo;
import com.riiablo.audio.Audio;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.presentation.component.SoundEmitter;
import com.riiablo.engine.server.event.DamageAppliedEvent;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.map.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SpatialAudioTest {
  @Test void poisonCloudsTransferOneLoopWithoutRestartingOrStoppingIt() {
    try (Fixture fixture = new Fixture()) {
      Audio.Instance playback = mock(Audio.Instance.class);
      when(fixture.audio.play("object_poison_loop", true)).thenReturn(playback);
      int previous = -1;
      for (int i = 0; i < 25; i++) {
        int cloud = fixture.world.create();
        fixture.position(cloud, i * 0.1f);
        com.riiablo.engine.server.component.Missile missile = fixture.world
            .getMapper(com.riiablo.engine.server.component.Missile.class).create(cloud);
        missile.ownerId = fixture.source;
        missile.missile = new com.riiablo.codec.excel.Missiles.Entry();
        missile.missile.Missile = "poisonjavcloud";
        MissileTravelAudio.play(fixture.world, fixture.context, cloud, "object_poison_loop");
        assertSame(playback, fixture.world.getMapper(SoundEmitter.class).get(cloud).sound);
        if (previous >= 0) {
          assertFalse(fixture.world.getMapper(SoundEmitter.class).has(previous));
          fixture.world.delete(previous);
        }
        fixture.world.process();
        verify(playback, never()).stop();
        previous = cloud;
      }
      verify(fixture.audio, times(1)).play("object_poison_loop", true);
    }
  }

  @Test void hurricaneLoopIsOwnedByTheActiveStateInsteadOfEveryParticle() {
    com.riiablo.codec.excel.Missiles.Entry swoosh =
        new com.riiablo.codec.excel.Missiles.Entry();
    swoosh.Missile = "hurricaneswoosh";
    com.riiablo.codec.excel.Missiles.Entry rock =
        new com.riiablo.codec.excel.Missiles.Entry();
    rock.Missile = "hurricanerock";
    assertTrue(MissileTravelAudio.isStateOwnedLoop(swoosh, "druid_hurricane"));
    assertTrue(MissileTravelAudio.isStateOwnedLoop(rock, "druid_hurricane"));
    assertFalse(MissileTravelAudio.isStateOwnedLoop(swoosh, "druid_windcast"));
    assertFalse(MissileTravelAudio.isStateOwnedLoop(null, "druid_hurricane"));
  }

  @Test void playbackUsesTheOwningWorldAndLiveListenerDespiteOtherGlobals() {
    World oldWorld = Riiablo.engine;
    Audio oldAudio = Riiablo.audio;
    try (Fixture first = new Fixture(); Fixture second = new Fixture()) {
      Riiablo.engine = second.world;
      Riiablo.audio = second.audio;
      first.position(first.source, 10f);
      second.position(second.source, 100f);
      Audio.Instance playback = mock(Audio.Instance.class);
      when(first.audio.play("travel", true)).thenReturn(playback);
      SpatialAudio.play(first.world, first.context, first.source, "travel", true);
      verify(playback).setVolume(0.5f);
      first.listener.set(first.source);
      SpatialAudio.play(first.world, first.context, first.source, "travel", true);
      verify(playback).setVolume(1f);
      verifyNoInteractions(second.audio);
    } finally {
      Riiablo.engine = oldWorld;
      Riiablo.audio = oldAudio;
    }
  }

  @Test void zonesMissingPositionsAndNativeRadiusStillGateOneShots() {
    try (Fixture fixture = new Fixture()) {
      assertEquals(1f, SpatialAudio.gain(fixture.world, fixture.listener.get(), fixture.source));
      fixture.position(fixture.source, 20f);
      assertTrue(SpatialAudio.isAudible(fixture.world, fixture.listener.get(), fixture.source));
      assertEquals(0f, SpatialAudio.gain(fixture.world, fixture.listener.get(), fixture.source));
      fixture.position(fixture.source, 20.01f);
      assertFalse(SpatialAudio.isAudible(fixture.world, fixture.listener.get(), fixture.source));
      fixture.position(fixture.source, 1f);
      fixture.world.getMapper(MapWrapper.class).create(fixture.listener.get()).zone = new Map.Zone();
      fixture.world.getMapper(MapWrapper.class).create(fixture.source).zone = new Map.Zone();
      assertNull(SpatialAudio.play(fixture.world, fixture.context, fixture.source, "travel", true));
      fixture.world.getMapper(Position.class).remove(fixture.source);
      assertEquals(0f, SpatialAudio.gain(fixture.world, fixture.listener.get(), fixture.source));
      fixture.listener.set(-1);
      assertEquals(0f, SpatialAudio.gain(fixture.world, fixture.listener.get(), fixture.source));
      verifyNoInteractions(fixture.audio);
    }
  }

  @Test void travelStartsAtSpatialGainAndKeepsItsOriginalEmitterPolicy() {
    try (Fixture fixture = new Fixture()) {
      fixture.position(fixture.source, 10f);
      Audio.Instance playback = mock(Audio.Instance.class);
      when(fixture.audio.play("travel", true)).thenReturn(playback);
      MissileTravelAudio.play(fixture.world, fixture.context, fixture.source, "travel");
      verify(playback).setVolume(0.5f);
      SoundEmitter emitter = fixture.world.getMapper(SoundEmitter.class).get(fixture.source);
      assertSame(playback, emitter.sound);
      assertSame(Interpolation.pow2OutInverse, emitter.interpolator);
      assertEquals(SoundEmitter.DEFAULT_RADIUS, emitter.radius);
      assertTrue(emitter.sameZoneOnly);
    }
  }

  @Test void silentAndMissingPlaybackCannotAttachAnEmitter() {
    try (Fixture fixture = new Fixture()) {
      when(fixture.context.audio()).thenReturn(null);
      MissileTravelAudio.play(fixture.world, fixture.context, fixture.source, "travel");
      assertFalse(fixture.world.getMapper(SoundEmitter.class).has(fixture.source));
      when(fixture.context.audio()).thenReturn(fixture.audio);
      MissileTravelAudio.play(fixture.world, fixture.context, fixture.source, "");
      verifyNoInteractions(fixture.audio);
      MissileTravelAudio.play(fixture.world, fixture.context, fixture.source, "missing");
      assertFalse(fixture.world.getMapper(SoundEmitter.class).has(fixture.source));
    }
  }

  @Test void committedMonsterPainUsesItsInjectedSessionAndKeepsDotSilent() {
    try (Fixture fixture = new Fixture()) {
      fixture.position(fixture.source, 5f);
      Monster monster = fixture.world.getMapper(Monster.class).create(fixture.source);
      monster.monstats = new MonStats.Entry();
      monster.monstats.MonSound = "fallen";
      Audio.Instance playback = mock(Audio.Instance.class);
      when(fixture.audio.play("fallen_hit_1", true)).thenReturn(playback);
      DamageHandler handler = fixture.world.getSystem(DamageHandler.class);
      handler.onDamageEvent(new DamageAppliedEvent(
          DamageEvent.obtainMissile(-1, fixture.source, 1, 0, null), 10f, 9f));
      verify(fixture.audio).play("fallen_hit_1", true);
      verify(playback).setVolume(0.75f);
      handler.onDamageEvent(new DamageAppliedEvent(
          DamageEvent.obtainDamageOverTime(-1, fixture.source, 1), 9f, 8f));
      verifyNoMoreInteractions(fixture.audio);
    }
  }

  private static final class Fixture implements AutoCloseable {
    final AtomicInteger listener = new AtomicInteger(-1);
    final Audio audio = mock(Audio.class);
    final ClientGameplayContext context = mock(ClientGameplayContext.class);
    final World world;
    final int source;

    Fixture() {
      when(context.audio()).thenReturn(audio);
      when(context.listener()).thenAnswer(invocation -> listener.get());
      world = new World(new WorldConfigurationBuilder().with(new DamageHandler()).build()
          .register("clientContext", context));
      listener.set(world.create());
      source = world.create();
      position(listener.get(), 0f);
      position(source, 0f);
    }

    void position(int entity, float x) {
      world.getMapper(Position.class).create(entity).position.set(x, 0f);
    }

    @Override public void close() { world.dispose(); }
  }
}
