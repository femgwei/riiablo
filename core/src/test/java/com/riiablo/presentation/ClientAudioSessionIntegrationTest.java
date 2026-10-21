package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.utils.Array;
import com.riiablo.Files;
import com.riiablo.Riiablo;
import com.riiablo.audio.Audio;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.Objects;
import com.riiablo.codec.excel.SoundEnviron;
import com.riiablo.codec.excel.Sounds;
import com.riiablo.engine.Engine;
import com.riiablo.drlg.TileGrid;
import com.riiablo.engine.SimulationMode;
import com.riiablo.engine.SimulationSession;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.presentation.component.SoundEmitter;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.map.EnvironmentCycle;
import com.riiablo.map.Map;
import com.riiablo.screen.GameScreen;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Real ECS ticks with fake playback endpoints; no MPQ, device or GL is required. */
class ClientAudioSessionIntegrationTest {
  private Application previousApp;
  private Audio previousAudio;
  private Files previousFiles;
  private GameScreen previousGame;

  @BeforeEach
  void disconnectLegacyGlobals() {
    previousApp = Gdx.app;
    previousAudio = Riiablo.audio;
    previousFiles = Riiablo.files;
    previousGame = Riiablo.game;
    Gdx.app = (Application) Proxy.newProxyInstance(getClass().getClassLoader(),
        new Class<?>[] {Application.class}, (proxy, method, args) -> null);
    Riiablo.game = null;
    Riiablo.files = null;
    Riiablo.audio = mock(Audio.class);
  }

  @AfterEach
  void restoreGlobals() {
    Gdx.app = previousApp;
    Riiablo.audio = previousAudio;
    Riiablo.files = previousFiles;
    Riiablo.game = previousGame;
  }

  @ParameterizedTest
  @EnumSource(value = SimulationMode.class, names = {"LOCAL", "REMOTE_CLIENT"})
  void interleavedWorldsKeepListenerClockTablesAndPlaybackSeparate(SimulationMode mode) {
    try (Fixture first = new Fixture(mode); Fixture second = new Fixture(mode)) {
      int firstPlayer = first.player(0, first.zone);
      int secondPlayer = second.player(8, second.zone);
      assertEquals(firstPlayer, secondPlayer, "the worlds deliberately reuse entity IDs");
      first.listener.set(firstPlayer);
      second.listener.set(secondPlayer);
      int firstTorch = first.torch(10);
      int secondTorch = second.torch(10);
      assertEquals(firstTorch, secondTorch);

      first.world.process();
      first.played(70, 1);
      verify(first.playback(2578)).setVolume(0.5f);
      verifyNoInteractions(second.audio);
      second.world.process();
      second.played(70, 1);
      verify(second.playback(2578)).setVolume(0.9f);
      assertNotSame(first.sounds.get(70), second.sounds.get(70));

      first.environment.setPeriodOfDay(EnvironmentCycle.PERIOD_NIGHT);
      first.world.process();
      first.played(71, 1);
      verify(first.playback(70)).fadeOutAndStop(0.4f);
      second.world.process();
      second.played(71, 0);
      first.world.process();
      first.world.process();
      first.played(81, 1);
      second.played(80, 0);
      second.world.process();
      second.played(80, 1);
      verifyNoInteractions(Riiablo.audio);
    }
  }

  @Test
  void latePlayerAssignmentAndReplacementUpdateBothZoneAndSpatialGain() {
    try (Fixture fixture = new Fixture(SimulationMode.REMOTE_CLIENT)) {
      int first = fixture.player(0, fixture.zone);
      int replacement = fixture.player(18, zone(2));
      int emitter = fixture.world.create();
      fixture.world.getMapper(Position.class).create(emitter).position.set(10, 0);
      fixture.world.getMapper(SoundEmitter.class).create(emitter)
          .set(fixture.playback(2578), Interpolation.linear);
      fixture.world.process();
      fixture.played(70, 0);
      verify(fixture.playback(2578), never()).setVolume(anyFloat());

      fixture.listener.set(first);
      fixture.world.process();
      fixture.played(70, 1);
      verify(fixture.playback(2578)).setVolume(0.5f);
      fixture.listener.set(replacement);
      fixture.world.process();
      fixture.played(90, 1);
      verify(fixture.playback(2578)).setVolume(0.6f);
      verify(fixture.playback(70)).fadeOutAndStop(0.4f);
    }
  }

  @Test
  void backgroundPauseFreezesEventSchedulingAndLoopDuration() {
    try (Fixture fixture = new Fixture(SimulationMode.LOCAL)) {
      fixture.listener.set(fixture.player(0, fixture.zone));
      fixture.world.process();
      when(fixture.audio.isBackgroundPaused()).thenReturn(true);
      for (int tick = 0; tick < 10; tick++) fixture.world.process();
      fixture.played(80, 0);
      when(fixture.audio.isBackgroundPaused()).thenReturn(false);
      fixture.world.process();
      fixture.played(80, 0);
      fixture.world.process();
      fixture.played(80, 1);
      fixture.world.process();
      verify(fixture.playback(80)).fadeOutAndStop(0.04f);
    }
  }

  @Test
  void missingRecordsRemainSilentAndSilentHostsStillTickSafely() {
    try (Fixture fixture = new Fixture(SimulationMode.LOCAL)) {
      fixture.listener.set(fixture.player(0, zone(999)));
      fixture.world.process();
      verify(fixture.audio, never()).play(any(Sounds.Entry.class), anyBoolean(), any());
      fixture.environs.add(3, 999, 999, 999, 999, 1);
      fixture.world.getMapper(MapWrapper.class).get(fixture.listener.get()).zone = zone(3);
      fixture.world.process();
      verify(fixture.audio, never()).play(any(Sounds.Entry.class), anyBoolean(), any());
    }
    ClientAudioSession silent = new ClientAudioSession(new EnvironmentCycle(),
        () -> Engine.INVALID_ENTITY, null, new SoundEnviron(), new Sounds());
    World world = new World(new WorldConfigurationBuilder()
        .with(new ObjectAmbientSoundSystem(silent), new SoundEmitterHandler(silent),
            new SoundEnvironmentSystem(silent)).build());
    try {
      assertDoesNotThrow(world::process);
    } finally {
      world.dispose();
    }
  }

  @Test
  void terrainRiverUsesTheBoundListenerAndStopsWhenItLeavesRange() {
    try (Fixture fixture = new Fixture(SimulationMode.LOCAL)) {
      TileGrid grid = new TileGrid(1, 1);
      grid.exportedFloorCells[0][0] = true;
      grid.floorSourceFiles[0][0] = grid.registerSourceFile("River.dt1");
      fixture.zone.setNativeTileGrid(grid, 0);
      fixture.map.zones.add(fixture.zone);
      int player = fixture.player(0, fixture.zone);
      fixture.listener.set(player);
      fixture.world.process();
      fixture.played(2599, 1);
      verify(fixture.playback(2599)).setVolume(1f);
      fixture.world.process();
      fixture.played(2599, 1);
      fixture.world.getMapper(Position.class).get(player).position.set(100, 0);
      fixture.world.process();
      verify(fixture.playback(2599)).stop();
      fixture.world.getMapper(Position.class).get(player).position.set(0, 0);
      fixture.world.process();
      fixture.played(2599, 2);
    }
  }

  @Test
  void extinguishingAndLeavingRangeRemoveOnlyOwnedEmitters() {
    try (Fixture fixture = new Fixture(SimulationMode.LOCAL)) {
      int player = fixture.player(0, fixture.zone);
      fixture.listener.set(player);
      int torch = fixture.torch(10);
      int unrelated = fixture.player(10, fixture.zone);
      com.riiablo.engine.server.component.Object otherObject = fixture.world
          .getMapper(com.riiablo.engine.server.component.Object.class).create(unrelated);
      otherObject.base = new Objects.Entry();
      otherObject.base.Id = 999;
      Audio.Instance otherPlayback = mock(Audio.Instance.class);
      when(otherPlayback.isLoaded()).thenReturn(true);
      fixture.world.getMapper(SoundEmitter.class).create(unrelated)
          .set(otherPlayback, Interpolation.linear);
      fixture.world.process();
      assertTrue(fixture.world.getMapper(SoundEmitter.class).has(torch));
      fixture.world.getMapper(com.riiablo.engine.server.component.Object.class)
          .get(torch).mode = Engine.Object.MODE_NU;
      fixture.world.process();
      assertFalse(fixture.world.getMapper(SoundEmitter.class).has(torch));
      assertSame(otherPlayback, fixture.world.getMapper(SoundEmitter.class).get(unrelated).sound);
      verify(otherPlayback, never()).stop();
      fixture.world.getMapper(com.riiablo.engine.server.component.Object.class)
          .get(torch).mode = Engine.Object.MODE_ON;
      fixture.world.process();
      assertTrue(fixture.world.getMapper(SoundEmitter.class).has(torch));
      fixture.world.getMapper(Position.class).get(player).position.set(100, 0);
      fixture.world.process();
      assertFalse(fixture.world.getMapper(SoundEmitter.class).has(torch));
      assertSame(otherPlayback, fixture.world.getMapper(SoundEmitter.class).get(unrelated).sound);
      verify(otherPlayback, never()).stop();
    }
  }

  @Test
  void disposalStopsOnlyTheOldWorldPlaybackAndRecreationStartsFresh() {
    Fixture old = new Fixture(SimulationMode.LOCAL);
    try (Fixture other = new Fixture(SimulationMode.REMOTE_CLIENT)) {
      old.listener.set(old.player(0, old.zone));
      other.listener.set(other.player(0, other.zone));
      old.torch(10);
      old.world.process();
      old.world.process();
      old.world.process();
      other.world.process();
      old.close();
      verify(old.playback(70)).stop();
      verify(old.playback(80)).stop();
      verify(old.playback(2578), atLeastOnce()).stop();
      verify(other.playback(70), never()).stop();
      assertEquals(0, old.map.disposeCalls, "the audio binding does not own map resources");
      try (Fixture fresh = new Fixture(SimulationMode.LOCAL)) {
        fresh.listener.set(fresh.player(0, fresh.zone));
        fresh.world.process();
        fresh.played(70, 1);
        fresh.played(80, 0);
        other.world.process();
        other.played(70, 1);
      }
    } finally {
      old.close();
    }
  }

  private static Map.Zone zone(int soundEnvironment) {
    Map.Zone zone = new Map.Zone();
    zone.level = new Levels.Entry();
    zone.level.SoundEnv = soundEnvironment;
    return zone;
  }

  private static class Fixture implements AutoCloseable {
    final AtomicInteger listener = new AtomicInteger(Engine.INVALID_ENTITY);
    final EnvironmentCycle environment = new EnvironmentCycle();
    final Audio audio = mock(Audio.class);
    final TestSounds sounds = new TestSounds();
    final TestEnvirons environs = new TestEnvirons();
    final HashMap<Integer, Audio.Instance> playback = new HashMap<>();
    final HostMap map = new HostMap();
    final Map.Zone zone = zone(1);
    final World world;
    private boolean closed;

    Fixture(SimulationMode mode) {
      for (int id : new int[] {70, 71, 80, 81, 90, 2578, 2599}) {
        Sounds.Entry sound = new Sounds.Entry();
        sound.Index = id;
        sound.FileName = "session-" + id + ".wav";
        sound.Fade_Out = id == 80 || id == 81 ? 1 : 10;
        sound.Loop = true;
        sound.Duration = 2;
        sounds.add(sound);
        Audio.Instance instance = mock(Audio.Instance.class);
        when(instance.isLoaded()).thenReturn(true);
        playback.put(id, instance);
        when(audio.play(sound, true, Audio.Channel.ENVIRONMENT)).thenReturn(instance);
      }
      environs.add(1, 70, 71, 80, 81, 3);
      environs.add(2, 90, 90, 0, 0, 0);
      ClientAudioSession binding = new ClientAudioSession(
          environment, listener::get, audio, environs, sounds);
      SimulationSession simulation = new SimulationSession(mode, map, environment, new PartyManager());
      WorldConfiguration config = simulation.registerServices(new WorldConfigurationBuilder()
          .with(new ObjectAmbientSoundSystem(binding), new SoundEmitterHandler(binding),
              new SoundEnvironmentSystem(binding)).build());
      // A conflicting legacy alias must not replace the explicit clock.
      config.register("environment", new EnvironmentCycle());
      world = new World(config);
    }

    int player(float x, Map.Zone zone) {
      int id = world.create();
      world.getMapper(Position.class).create(id).position.set(x, 0);
      world.getMapper(MapWrapper.class).create(id).set(map, zone);
      return id;
    }

    int torch(float x) {
      int id = player(x, zone);
      com.riiablo.engine.server.component.Object object = world
          .getMapper(com.riiablo.engine.server.component.Object.class).create(id);
      object.base = new Objects.Entry();
      object.base.Id = ObjectAmbientSoundSystem.TORCH_TIKI;
      object.mode = Engine.Object.MODE_ON;
      return id;
    }

    Audio.Instance playback(int id) { return playback.get(id); }

    void played(int id, int count) {
      verify(audio, times(count)).play(sounds.get(id), true, Audio.Channel.ENVIRONMENT);
    }

    @Override public void close() {
      if (!closed) {
        closed = true;
        world.dispose();
      }
    }
  }

  private static class TestSounds extends Sounds {
    void add(Entry sound) { put(sound.Index, sound); }
  }

  private static class TestEnvirons extends SoundEnviron {
    void add(int id, int day, int night, int dayEvent, int nightEvent, int delay) {
      Entry entry = new Entry();
      entry.Index = id;
      entry.Day_Ambience = day;
      entry.Night_Ambience = night;
      entry.Day_Event = dayEvent;
      entry.Night_Event = nightEvent;
      entry.Event_Delay = delay;
      put(id, entry);
    }
  }

  private static class HostMap extends Map {
    int disposeCalls;
    final Array<Map.Zone> zones = new Array<>();
    HostMap() { super(1, 0); }
    @Override public Array<Map.Zone> getZones() { return zones; }
    @Override public void dispose() { disposeCalls++; }
  }
}
