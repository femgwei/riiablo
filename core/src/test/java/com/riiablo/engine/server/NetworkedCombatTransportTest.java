package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Flags;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Networked;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.net.packet.d2gs.CastSkillRequest;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.D2GS;
import com.riiablo.net.packet.d2gs.D2GSData;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.net.packet.d2gs.MissileP;
import com.riiablo.net.packet.d2gs.SpendSkillPointRequest;
import com.riiablo.net.packet.d2gs.SpendSkillPointResult;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

/** Headless transport regressions for the multiplayer combat boundary. */
class NetworkedCombatTransportTest extends RiiabloTest {
  @Test
  void skillPointPacketsCarryIntentAndAuthoritativeResult() {
    FlatBufferBuilder requestBuilder = new FlatBufferBuilder(128);
    int requestData = SpendSkillPointRequest.createSpendSkillPointRequest(
        requestBuilder, 99, 6);
    int requestRoot = D2GS.createD2GS(
        requestBuilder, D2GSData.SpendSkillPointRequest, requestData);
    D2GS.finishSizePrefixedD2GSBuffer(requestBuilder, requestRoot);
    D2GS requestPacket = D2GS.getRootAsD2GS(com.google.flatbuffers.ByteBufferUtil
        .removeSizePrefix(requestBuilder.dataBuffer()));
    SpendSkillPointRequest request = (SpendSkillPointRequest) requestPacket.data(
        new SpendSkillPointRequest());
    assertEquals(99, request.requestId());
    assertEquals(6, request.skillId());

    FlatBufferBuilder resultBuilder = new FlatBufferBuilder(128);
    int reason = resultBuilder.createString("OK");
    int resultData = SpendSkillPointResult.createSpendSkillPointResult(
        resultBuilder, 99, true, reason, 6, 2, 3);
    int resultRoot = D2GS.createD2GS(
        resultBuilder, D2GSData.SpendSkillPointResult, resultData);
    D2GS.finishSizePrefixedD2GSBuffer(resultBuilder, resultRoot);
    D2GS resultPacket = D2GS.getRootAsD2GS(com.google.flatbuffers.ByteBufferUtil
        .removeSizePrefix(resultBuilder.dataBuffer()));
    SpendSkillPointResult result = (SpendSkillPointResult) resultPacket.data(
        new SpendSkillPointResult());
    assertTrue(result.success());
    assertEquals(6, result.skillId());
    assertEquals(2, result.skillLevel());
    assertEquals(3, result.skillPoints());
    System.out.println("[SKILL_POINT_NET] request=99 skill=6 result=OK level=2 points=3 status=PASS");
  }

  @Test
  void castRequestCarriesOnlyClientIntent() {
    FlatBufferBuilder builder = new FlatBufferBuilder(128);
    int request = CastSkillRequest.createCastSkillRequest(builder, 2, 77, 12.5f, -3.25f);
    int root = D2GS.createD2GS(builder, D2GSData.CastSkillRequest, request);
    D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    ByteBuffer data = builder.dataBuffer();
    D2GS packet = D2GS.getRootAsD2GS(com.google.flatbuffers.ByteBufferUtil.removeSizePrefix(data));
    CastSkillRequest wire = (CastSkillRequest) packet.data(new CastSkillRequest());

    assertEquals(D2GSData.CastSkillRequest, packet.dataType());
    assertEquals(2, wire.skillId());
    assertEquals(77, wire.targetId());
    assertEquals(12.5f, wire.targetX(), 0.001f);
    assertEquals(-3.25f, wire.targetY(), 0.001f);
    System.out.println("[NET_CAST_CHAIN] skill=2 target=77 intentOnly=true status=PASS");
  }

  @Test
  void missileEntitySyncCarriesIdentityAndMotion() {
    SerializationManager serializer = new SerializationManager();
    World world = new World(new WorldConfigurationBuilder()
        .with(new net.mostlyoriginal.api.event.common.EventSystem(), new CofManager(), serializer)
        .build());
    try {
      int id = world.create();
      world.getMapper(Class.class).create(id).type = Class.Type.MIS;
      world.getMapper(Flags.class).create(id);
      world.getMapper(Networked.class).create(id).serverId = id;
      Missiles.Entry row = com.riiablo.Riiablo.files.Missiles.get("shafire3");
      world.getMapper(Missile.class).create(id).set(row, new Vector2(2, 3), 42f).setOwner(11);
      world.getMapper(Position.class).create(id).position.set(2, 3);
      world.getMapper(Velocity.class).create(id).velocity.set(4, 0);
      world.getMapper(Angle.class).create(id).target.set(1, 0);

      FlatBufferBuilder builder = new FlatBufferBuilder(512);
      int offset = serializer.serialize(builder, id);
      builder.finish(offset);
      EntitySync sync = EntitySync.getRootAsEntitySync(builder.dataBuffer());
      int missileIndex = -1;
      for (int i = 0; i < sync.componentLength(); i++) {
        if (sync.componentType(i) == ComponentP.MissileP) missileIndex = i;
      }
      assertTrue(missileIndex >= 0);
      MissileP wire = (MissileP) sync.component(new MissileP(), missileIndex);
      assertEquals(row.Id, wire.missileId());
      assertEquals(11, wire.ownerId());
      assertEquals(42f, wire.range(), 0.001f);
      System.out.println("[MISSILE_SYNC_CHAIN] entity=" + id + " missileId=" + row.Id
          + " owner=11 range=42 identity=PASS");
    } finally {
      world.dispose();
    }
  }

  @Test
  void clientMissileReplicaNeverMovesOrDealsDamageInServerCollisionSystem() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new net.mostlyoriginal.api.event.common.EventSystem(), new MissileCollisionSystem())
        .build());
    try {
      int id = world.create();
      Missile missile = world.getMapper(Missile.class).create(id);
      missile.authoritative = false;
      missile.ownerId = 99;
      world.getMapper(Position.class).create(id).position.set(4, 5);
      world.getMapper(Velocity.class).create(id).velocity.set(100, 0);
      world.setDelta(1f);
      world.process();

      assertEquals(4f, world.getMapper(Position.class).get(id).position.x, 0.001f);
      assertEquals(5f, world.getMapper(Position.class).get(id).position.y, 0.001f);
      assertTrue(world.getMapper(Missile.class).has(id));
      System.out.println("[MISSILE_SYNC_CHAIN] replica=true localCollision=false status=PASS");
    } finally {
      world.dispose();
    }
  }
}
