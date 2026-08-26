package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Flags;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.serializer.CofReferenceSerializer;
import com.riiablo.engine.server.component.serializer.VitalsSerializer;
import com.riiablo.net.packet.d2gs.CofReferenceP;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.net.packet.d2gs.PlayerP;
import com.riiablo.net.packet.d2gs.VitalsP;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Server serializer to client ECS regression for authoritative combat state. */
class ServerClientCombatSyncEcsScenarioTest extends RiiabloTest {
  @Test
  void serverDeathAndProgressionReplaceStaleClientState() {
    SerializationManager serialization = new SerializationManager();
    World server = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new CofManager(), serialization)
        .build());
    try {
      int serverPlayer = server.create();
      server.getMapper(Class.class).create(serverPlayer).type = Class.Type.PLR;
      server.getMapper(Flags.class).create(serverPlayer);

      CharData authoritative = character("ServerHero", 2, 600L, 1);
      server.getMapper(Player.class).create(serverPlayer).data = authoritative;
      server.getMapper(AttributesWrapper.class).create(serverPlayer).attrs =
          vitals(0f, 64f, 7f, 30f);
      server.getMapper(CofReference.class).create(serverPlayer)
          .set(Engine.Player.getToken(Riiablo.AMAZON), Engine.Player.MODE_DT);

      EntitySync packet = serialize(serialization, serverPlayer);
      assertTrue(find(packet, ComponentP.PlayerP) >= 0);
      assertTrue(find(packet, ComponentP.VitalsP) >= 0);
      assertTrue(find(packet, ComponentP.CofReferenceP) >= 0);

      AttributesWrapper clientVitals = new AttributesWrapper();
      clientVitals.attrs = vitals(41f, 41f, 19f, 19f);
      CofReference clientCof = new CofReference()
          .set(Engine.Player.getToken(Riiablo.AMAZON), Engine.Player.MODE_RN);

      int vitalsIndex = find(packet, ComponentP.VitalsP);
      VitalsP wireVitals = (VitalsP) packet.component(new VitalsP(), vitalsIndex);
      new VitalsSerializer().getData(packet, vitalsIndex, clientVitals);
      int cofIndex = find(packet, ComponentP.CofReferenceP);
      new CofReferenceSerializer().getData(packet, cofIndex, clientCof);
      PlayerP wirePlayer = (PlayerP) packet.component(
          new PlayerP(), find(packet, ComponentP.PlayerP));

      assertTrue(wireVitals.dead());
      assertEquals(0f, value(clientVitals.attrs, Stat.hitpoints), 0.001f);
      assertEquals(64f, value(clientVitals.attrs, Stat.maxhp), 0.001f);
      assertEquals(7f, value(clientVitals.attrs, Stat.mana), 0.001f);
      assertEquals(30f, value(clientVitals.attrs, Stat.maxmana), 0.001f);
      assertEquals(Engine.Player.MODE_DT, clientCof.mode);
      assertEquals(2, wirePlayer.level());
      assertEquals(600L, wirePlayer.experience());
      assertEquals(1, wirePlayer.skillPoints());

      System.out.println("[SERVER_CLIENT_COMBAT_SYNC] entity=" + serverPlayer
          + " hp=0/64 dead=true mode=DT experience=600 level=2 skillPoints=1 status=PASS");
    } finally {
      server.dispose();
    }
  }

  private static EntitySync serialize(SerializationManager serialization, int entityId) {
    FlatBufferBuilder builder = new FlatBufferBuilder(512);
    int root = serialization.serialize(builder, entityId);
    builder.finish(root);
    return EntitySync.getRootAsEntitySync(builder.dataBuffer());
  }

  private static int find(EntitySync sync, byte type) {
    for (int i = 0; i < sync.componentLength(); i++) {
      if (sync.componentType(i) == type) return i;
    }
    return -1;
  }

  private static CharData character(String name, int level, long experience, int skillPoints) {
    CharData data = CharData.obtain().clear().set(Riiablo.NORMAL, false, name, Riiablo.AMAZON);
    data.level = (byte) level;
    data.getStats().base().put(Stat.level, level);
    data.getStats().base().put(Stat.experience, experience);
    data.getStats().base().put(Stat.newskills, skillPoints);
    data.getStats().aggregate().put(Stat.level, level);
    data.getStats().aggregate().put(Stat.experience, experience);
    data.getStats().aggregate().put(Stat.newskills, skillPoints);
    return data;
  }

  private static Attributes vitals(float hp, float maxHp, float mana, float maxMana) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, maxHp);
    attrs.base().put(Stat.mana, mana);
    attrs.base().put(Stat.maxmana, maxMana);
    attrs.reset();
    return attrs;
  }

  private static float value(Attributes attrs, short stat) {
    return attrs.aggregate().getValue(stat, 0f);
  }
}
