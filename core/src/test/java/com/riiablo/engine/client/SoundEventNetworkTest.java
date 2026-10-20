package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.audio.Audio;
import com.riiablo.codec.excel.SoundEnviron;
import com.riiablo.codec.excel.Sounds;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.event.SoundEvent;
import com.riiablo.map.EnvironmentCycle;
import com.riiablo.net.packet.d2gs.D2GS;
import com.riiablo.net.packet.d2gs.D2GSData;
import com.riiablo.net.packet.d2gs.SoundP;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import net.mostlyoriginal.api.event.common.EventSystem;

class SoundEventNetworkTest {
  @Test
  void soundPacketCarriesIntentAndCapturedPosition() {
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    int sound = builder.createString("fallen_attack_1");
    int data = SoundP.createSoundP(builder, 17, sound, true, 12.5f, -3.25f, true);
    int root = D2GS.createD2GS(builder, D2GSData.SoundP, data);
    D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    ByteBuffer frame = com.google.flatbuffers.ByteBufferUtil.removeSizePrefix(
        builder.dataBuffer());
    D2GS packet = D2GS.getRootAsD2GS(frame);
    SoundP wire = (SoundP) packet.data(new SoundP());

    assertEquals(D2GSData.SoundP, packet.dataType());
    assertEquals(17, wire.entityId());
    assertEquals("fallen_attack_1", wire.sound());
    assertTrue(wire.global());
    assertEquals(12.5f, wire.x(), 0.0001f);
    assertEquals(-3.25f, wire.y(), 0.0001f);
    assertTrue(wire.positionPresent());
  }

  @Test
  void clientConsumesRemoteSoundWhenSourceEntityWasRemoved() {
    Audio audio = mock(Audio.class);
    Audio.Instance playback = mock(Audio.Instance.class);
    when(audio.play("fallen_death_1", true)).thenReturn(playback);
    AtomicInteger listener = new AtomicInteger(Engine.INVALID_ENTITY);
    ClientAudioSession session = new ClientAudioSession(new EnvironmentCycle(), listener::get,
        audio, new SoundEnviron(), new Sounds());
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new SoundEventSystem(session)).build());
    try {
      int player = world.create();
      world.getMapper(com.riiablo.engine.server.component.Position.class)
          .create(player).position.set(new Vector2(0f, 0f));
      listener.set(player);

      world.getSystem(EventSystem.class).dispatch(
          SoundEvent.obtain(99, "fallen_death_1", true).withPosition(10f, 0f));

      verify(audio).play("fallen_death_1", true);
      verify(playback).setVolume(eq(0.5f));
    } finally {
      world.dispose();
    }
  }
}
