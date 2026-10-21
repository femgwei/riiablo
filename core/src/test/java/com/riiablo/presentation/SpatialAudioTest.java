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
