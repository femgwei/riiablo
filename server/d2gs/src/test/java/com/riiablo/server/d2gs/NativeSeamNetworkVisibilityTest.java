package com.riiablo.server.d2gs;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.utils.IntIntMap;
import com.d2moo.common.drlg.D2DrlgLevel;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.map.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeSeamNetworkVisibilityTest {
  @Test public void ordinaryEntitiesReachOnlyTheNativeAdjacentRoomAcrossASeam() throws Exception {
    com.badlogic.gdx.Application previousApp = com.badlogic.gdx.Gdx.app;
    com.badlogic.gdx.backends.headless.HeadlessApplication app =
        new com.badlogic.gdx.backends.headless.HeadlessApplication(new com.badlogic.gdx.ApplicationAdapter() {});
    World world = new World(new WorldConfigurationBuilder().build());
    try {
      NetworkSynchronizer sync = new NetworkSynchronizer();
      sync.mMapWrapper = world.getMapper(MapWrapper.class);
      sync.mPosition = world.getMapper(Position.class);
      sync.players = new IntIntMap();
      Map map = new Map(1, 0);
      Map.Zone source = zone(map, 26, 0), neighbor = zone(map, 27, 80);
      Map.Zone detached = zone(map, 28, 80), overlap = zone(map, 29, 0);
      Map.RoomEx a = source.addRoomEx(0, 0, 80, 80);
      Map.RoomEx b = neighbor.addRoomEx(80, 0, 40, 80);
      neighbor.addRoomEx(120, 0, 40, 80).setAdjacentRoomIds(new int[0]);
      a.setAdjacentRoomIds(new int[0]); b.setAdjacentRoomIds(new int[0]);
      detached.addRoomEx(80, 0, 80, 80).setAdjacentRoomIds(new int[0]);
      overlap.addRoomEx(0, 0, 80, 80).setAdjacentRoomIds(new int[0]);
      D2DrlgLevel nativeA = new D2DrlgLevel(), nativeB = new D2DrlgLevel();
      nativeA.setLevelId(26); nativeB.setLevelId(27);
      D2DrlgRoom rawA = new D2DrlgRoom(), rawB = new D2DrlgRoom();
      rawA.setLevel(nativeA); rawB.setLevel(nativeB);
      nativeA.setFirstRoomEx(rawA); nativeB.setFirstRoomEx(rawB);
      rawA.setPpRoomsNear(new D2DrlgRoom[]{rawB}); rawA.setNRoomsNear(1);
      rawB.setPpRoomsNear(new D2DrlgRoom[]{rawA}); rawB.setNRoomsNear(1);
      a.setNativePopulationMetadata(rawA); b.setNativePopulationMetadata(rawB);
      int entity = world.create(), player = world.create();
      sync.mMapWrapper.create(entity).set(map, source);
      sync.mPosition.create(entity).position.set(79, 10);
      sync.mMapWrapper.create(player).set(map, neighbor);
      sync.mPosition.create(player).position.set(81, 10);
      sync.players.put(0, player);
      world.process();
      java.lang.reflect.Method mask = NetworkSynchronizer.class.getDeclaredMethod("recipientMask", int.class);
      mask.setAccessible(true);
      assertEquals(0, mask.invoke(sync, entity));
      map.addWarpDestinationOverride(26, 0, 27); source.setNativeSeamAnchor(0, 79, 10);
      assertEquals(1, mask.invoke(sync, entity));
      sync.mPosition.get(player).position.set(121, 10);
      assertEquals(0, mask.invoke(sync, entity));
      sync.mPosition.get(player).position.set(81, 10);
      sync.mMapWrapper.get(player).zone = detached;
      assertEquals(0, mask.invoke(sync, entity));
      sync.mMapWrapper.get(player).zone = overlap;
      sync.mPosition.get(player).position.set(10, 10);
      assertEquals(0, mask.invoke(sync, entity));
      sync.mMapWrapper.get(player).set(new Map(1, 0), neighbor);
      sync.mPosition.get(player).position.set(81, 10);
      assertEquals(0, mask.invoke(sync, entity));
    } finally { world.dispose(); app.exit(); com.badlogic.gdx.Gdx.app = previousApp; }
  }

  private static Map.Zone zone(Map map, int level, int x) throws Exception {
    Map.Zone zone = new Map.Zone();
    zone.level = new com.riiablo.codec.excel.Levels.Entry(); zone.level.Id = level;
    for (String name : new String[]{"width", "height"}) {
      java.lang.reflect.Field field = Map.Zone.class.getDeclaredField(name);
      field.setAccessible(true); field.setInt(zone, 80);
    }
    zone.setPosition(x, 0); map.getZones().add(zone); return zone;
  }
}
