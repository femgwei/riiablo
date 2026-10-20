package com.riiablo.server.d2gs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.utils.IntIntMap;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.SoundEvent;
import com.riiablo.map.Map;
import com.riiablo.net.packet.d2gs.D2GSData;
import com.riiablo.net.packet.d2gs.SoundP;
import java.util.concurrent.ArrayBlockingQueue;
import org.junit.Test;

/** Verifies D2GS forwards sound intent without resolving client audio assets. */
public class SoundEventTransportTest {
  @Test public void broadcastsSoundIntentWithCapturedPosition() {
    World world = new World(new WorldConfigurationBuilder().build());
    try {
      NetworkSynchronizer sync = new NetworkSynchronizer();
      sync.mMapWrapper = world.getMapper(MapWrapper.class);
      sync.mPosition = world.getMapper(Position.class);
      sync.players = new IntIntMap();
      Map map = new Map(1, 0);
      Map.Zone zone = new Map.Zone();
      zone.level = new com.riiablo.codec.excel.Levels.Entry();
      zone.level.Id = 1;
      map.getZones().add(zone);
      int source = world.create();
      int player = world.create();
      sync.mMapWrapper.create(source).set(map, zone);
      sync.mMapWrapper.create(player).set(map, zone);
      sync.mPosition.create(source).position.set(12.5f, -3.25f);
      sync.mPosition.create(player).position.set(12.5f, -3.25f);
      sync.players.put(0, player);
      sync.outPackets = new ArrayBlockingQueue<>(2);

      sync.onSound(SoundEvent.obtain(source, "fallen_attack_1", true)
          .withPosition(12.5f, -3.25f));

      Packet packet = sync.outPackets.poll();
      assertTrue(packet != null);
      assertEquals(1, packet.id);
      assertEquals(D2GSData.SoundP, packet.data.dataType());
      SoundP wire = (SoundP) packet.data.data(new SoundP());
      assertEquals(source, wire.entityId());
      assertEquals("fallen_attack_1", wire.sound());
      assertTrue(wire.global());
      assertEquals(12.5f, wire.x(), 0.0001f);
      assertEquals(-3.25f, wire.y(), 0.0001f);
      assertTrue(wire.positionPresent());
    } finally {
      world.dispose();
    }
  }
}
