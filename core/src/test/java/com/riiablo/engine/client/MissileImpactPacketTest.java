package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.net.packet.d2gs.MissileImpactP;
import org.junit.jupiter.api.Test;

class MissileImpactPacketTest {
  @Test void healingDecisionSurvivesTheOneShotPacket() {
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    int overlay = builder.createString("healing");
    builder.finish(MissileImpactP.createMissileImpactP(builder,
        14, 27, 3, 8, 10f, 12f, 0f, 1f, overlay));
    MissileImpactP packet = MissileImpactP.getRootAsMissileImpactP(builder.dataBuffer());
    assertEquals("healing", packet.targetOverlay());
    assertEquals(8, packet.targetEntityId());
    assertEquals(27, packet.missileId());
  }

  @Test void oldImpactPacketsHaveNoImplicitHealingOverlay() {
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    MissileImpactP.startMissileImpactP(builder);
    MissileImpactP.addMissileId(builder, 27);
    MissileImpactP.addTargetEntityId(builder, 8);
    builder.finish(MissileImpactP.endMissileImpactP(builder));
    MissileImpactP packet = MissileImpactP.getRootAsMissileImpactP(builder.dataBuffer());
    assertNull(packet.targetOverlay());
    assertEquals(8, packet.targetEntityId());
  }
}
