package com.riiablo.server.d2gs;

import static org.junit.Assert.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.utils.IntIntMap;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.event.UnitOverlayEvent;
import com.riiablo.map.Map;
import com.riiablo.net.packet.d2gs.D2GSData;
import com.riiablo.net.packet.d2gs.UnitOverlayP;
import java.util.concurrent.ArrayBlockingQueue;
import org.junit.Test;

public class UnitOverlayTransportTest {
  @Test public void targetOverlayReachesOnlyPlayersInTheTargetsLevel() {
    World world = new World(new WorldConfigurationBuilder().build());
    try {
      NetworkSynchronizer sync = new NetworkSynchronizer();
      sync.mMapWrapper = world.getMapper(MapWrapper.class);
      sync.players = new IntIntMap(); sync.outPackets = new ArrayBlockingQueue<>(2);
      Map map = new Map(1, 0);
      Map.Zone here = zone(1), elsewhere = zone(2);
      int target = world.create(), local = world.create(), remote = world.create();
      sync.mMapWrapper.create(target).set(map, here);
      sync.mMapWrapper.create(local).set(map, here);
      sync.mMapWrapper.create(remote).set(map, elsewhere);
      sync.players.put(0, local); sync.players.put(1, remote);
      sync.onUnitOverlay(UnitOverlayEvent.obtain(target, "fire_hit"));
      Packet packet = sync.outPackets.poll();
      assertNotNull(packet); assertEquals(1, packet.id);
      assertEquals(D2GSData.UnitOverlayP, packet.data.dataType());
      UnitOverlayP data = (UnitOverlayP) packet.data.data(new UnitOverlayP());
      assertEquals(target, data.entityId()); assertEquals("fire_hit", data.overlay());
      sync.mMapWrapper.create(local).set(map, elsewhere);
      sync.onUnitOverlay(UnitOverlayEvent.obtain(target, "fire_hit"));
      sync.onUnitOverlay(UnitOverlayEvent.obtain(target, ""));
      assertTrue(sync.outPackets.isEmpty());
    } finally { world.dispose(); }
  }

  private static Map.Zone zone(int id) {
    Map.Zone zone = new Map.Zone();
    zone.level = new com.riiablo.codec.excel.Levels.Entry(); zone.level.Id = id;
    return zone;
  }
}
