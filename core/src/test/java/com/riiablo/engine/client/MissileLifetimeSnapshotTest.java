package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.d2moo.common.drlg.D2Seed;
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

  @Test
  void presentationSeedInitializesOnceAndDoesNotRewindOnRepeat() {
    Missile source = new Missile();
    source.presentationSeed = 0;
    source.presentationSeedKnown = true;
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    builder.finish(new MissileSerializer().putData(builder, source));
    MissileP packet = MissileP.getRootAsMissileP(builder.dataBuffer());

    Missile local = new Missile();
    ClientNetworkReceiver.applyMissileSnapshot(local, packet);
    assertTrue(local.presentationSeedKnown);
    assertEquals(0, local.presentationSeed);
    assertNotNull(local.clientSeed);
    assertEquals(0, local.clientSeed.getNLowSeed());

    local.clientSeed.setSeeds(1234, 666);
    ClientNetworkReceiver.applyMissileSnapshot(local, packet);
    assertEquals(1234, local.clientSeed.getNLowSeed(),
        "equal snapshots must not rewind the presentation RNG");
  }

  @Test
  void oldPacketsDoNotEraseKnownPresentationSeedOrTouchAuthoritativeRandom() {
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    MissileP.startMissileP(builder);
    MissileP.addMissileId(builder, 1);
    builder.finish(MissileP.endMissileP(builder));
    Missile local = new Missile();
    local.presentationSeed = 37;
    local.presentationSeedKnown = true;
    local.clientSeed = new D2Seed(1234, 9876);
    local.nativeSeed = new D2Seed(77, 88);
    local.rngState = 77;
    D2Seed presentation = local.clientSeed;
    D2Seed authoritative = local.nativeSeed;

    ClientNetworkReceiver.applyMissileSnapshot(local,
        MissileP.getRootAsMissileP(builder.dataBuffer()));

    assertTrue(local.presentationSeedKnown);
    assertEquals(37, local.presentationSeed);
    assertSame(presentation, local.clientSeed);
    assertEquals(1234, local.clientSeed.getNLowSeed());
    assertSame(authoritative, local.nativeSeed);
    assertEquals(77, local.rngState);
    assertEquals(88, local.nativeSeed.getNHighSeed());
  }

  @Test
  void changedPresentationSeedReseedsOnlyVisualRandomStream() {
    Missile source = new Missile();
    source.presentationSeed = Integer.MIN_VALUE;
    source.presentationSeedKnown = true;
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    builder.finish(new MissileSerializer().putData(builder, source));
    Missile local = new Missile();
    local.presentationSeed = 37;
    local.presentationSeedKnown = true;
    local.clientSeed = new D2Seed(1234, 9876);
    local.nativeSeed = new D2Seed(77, 88);
    local.rngState = 77;
    local.clientFrame = 16;
    local.clientPathX = 91;
    local.clientPathY = 92;
    local.clientFlightInitialized = true;

    ClientNetworkReceiver.applyMissileSnapshot(local,
        MissileP.getRootAsMissileP(builder.dataBuffer()));

    assertEquals(Integer.MIN_VALUE, local.presentationSeed);
    assertEquals(Integer.MIN_VALUE, local.clientSeed.getNLowSeed());
    assertEquals(666, local.clientSeed.getNHighSeed());
    assertEquals(77, local.nativeSeed.getNLowSeed());
    assertEquals(88, local.nativeSeed.getNHighSeed());
    assertEquals(77, local.rngState);
    assertEquals(16, local.clientFrame);
    assertEquals(91, local.clientPathX);
    assertEquals(92, local.clientPathY);
    assertTrue(local.clientFlightInitialized);
  }
}
