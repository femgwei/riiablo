package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.serializer.MissileSerializer;
import com.riiablo.net.packet.d2gs.MissileP;
import org.junit.jupiter.api.Test;

class MissileLifetimeSnapshotTest {
  @Test
  void lateJoinAndRepeatedSnapshotsPreserveRemainingLifetime() {
    Missile source = new Missile();
    source.nativeFrame = 60;
    source.nativeLifetimeFrames = 77;
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    builder.finish(new MissileSerializer().putData(builder, source));
    MissileP packet = MissileP.getRootAsMissileP(builder.dataBuffer());
    Missile local = new Missile();
    local.nativeLifetimeFrames = 120;
    ClientNetworkReceiver.applyMissileSnapshot(local, packet);
    assertEquals(60, local.nativeFrame);
    assertEquals(17, local.nativeLifetimeFrames - local.nativeFrame);
    assertFalse(local.authoritative);
    local.nativeFrame = 70;
    ClientNetworkReceiver.applyMissileSnapshot(local, packet);
    assertEquals(60, local.nativeFrame, "snapshot restores the server clock");
  }

  @Test
  void oldPacketsDoNotResetAnExistingClock() {
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    MissileP.startMissileP(builder);
    MissileP.addMissileId(builder, 1);
    builder.finish(MissileP.endMissileP(builder));
    Missile local = new Missile();
    local.nativeFrame = 9;
    local.nativeLifetimeFrames = 25;
    ClientNetworkReceiver.applyMissileSnapshot(local,
        MissileP.getRootAsMissileP(builder.dataBuffer()));
    assertEquals(9, local.nativeFrame);
    assertEquals(25, local.nativeLifetimeFrames);
  }

  @Test
  void explicitZeroClearsAFactoryDerivedLifetime() {
    Missile source = new Missile();
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    builder.finish(new MissileSerializer().putData(builder, source));
    Missile local = new Missile();
    local.nativeFrame = 12;
    local.nativeLifetimeFrames = 77;
    ClientNetworkReceiver.applyMissileSnapshot(local,
        MissileP.getRootAsMissileP(builder.dataBuffer()));
    assertEquals(0, local.nativeFrame);
    assertEquals(0, local.nativeLifetimeFrames);
  }
}
