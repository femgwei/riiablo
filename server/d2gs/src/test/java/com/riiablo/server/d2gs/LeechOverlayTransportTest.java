package com.riiablo.server.d2gs;

import static org.junit.Assert.*;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.utils.IntIntMap;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.event.DamageAppliedEvent;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.map.Map;
import com.riiablo.net.packet.d2gs.D2GSData;
import com.riiablo.net.packet.d2gs.UnitOverlayP;
import java.util.concurrent.ArrayBlockingQueue;
import org.junit.Test;

public class LeechOverlayTransportTest {
  @Test public void selectedCueUsesAttackerVisibilityEvenWhenPoolsWereFull() {
    World world=new World(new WorldConfigurationBuilder().build());
    try {
      NetworkSynchronizer sync=new NetworkSynchronizer();
      sync.mMapWrapper=world.getMapper(MapWrapper.class); sync.players=new IntIntMap();
      sync.outPackets=new ArrayBlockingQueue<>(2);
      Map map=new Map(1,0); Map.Zone here=zone(1),elsewhere=zone(2);
      int source=world.create(),target=world.create(),watcher=world.create();
      sync.mMapWrapper.create(source).set(map,here); sync.mMapWrapper.create(target).set(map,elsewhere);
      sync.mMapWrapper.create(watcher).set(map,here); sync.players.put(0,watcher); sync.players.put(1,target);
      DamageEvent hit=DamageEvent.obtainMelee(source,target,20,20); hit.leechOverlay="stealmana";
      assertEquals(0,hit.manaLeeched,0f); // Cue must not be gated on actual restoration.
      sync.onDamageApplied(new DamageAppliedEvent(hit,100,80));
      Packet packet=sync.outPackets.poll(); assertNotNull(packet); assertEquals(1,packet.id);
      assertEquals(D2GSData.UnitOverlayP,packet.data.dataType());
      UnitOverlayP data=(UnitOverlayP)packet.data.data(new UnitOverlayP());
      assertEquals(source,data.entityId()); assertEquals("stealmana",data.overlay());
      assertTrue(sync.outPackets.isEmpty());
    } finally { world.dispose(); }
  }

  @Test public void absentCueAndReactiveDamageDoNotManufactureLeech() {
    NetworkSynchronizer sync=new NetworkSynchronizer(); sync.outPackets=new ArrayBlockingQueue<>(2);
    DamageEvent normal=DamageEvent.obtainMelee(1,2,20,20);
    sync.onDamageApplied(new DamageAppliedEvent(normal,100,80));
    DamageEvent reactive=DamageEvent.obtainReactive(1,2,20,20); reactive.leechOverlay="steallife";
    sync.onDamageApplied(new DamageAppliedEvent(reactive,100,80)); assertTrue(sync.outPackets.isEmpty());
  }

  private static Map.Zone zone(int id) {
    Map.Zone zone=new Map.Zone(); zone.level=new com.riiablo.codec.excel.Levels.Entry(); zone.level.Id=id; return zone;
  }
}
