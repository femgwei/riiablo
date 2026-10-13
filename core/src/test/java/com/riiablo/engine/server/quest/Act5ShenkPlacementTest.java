package com.riiablo.engine.server.quest;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Levels;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.ZoneChangeEvent;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Act5ShenkPlacementTest extends RiiabloTest {
  @Test void enteringFoothillsDoesNotCreateBossAtPlayerOrTownBoundary() throws Exception {
    Act5QuestSystem system = new Act5QuestSystem();
    CountingFactory factory = new CountingFactory();
    World world = new World(new WorldConfigurationBuilder().with(system).build());
    try {
      system.factory = factory;
      int player = world.create();
      world.getMapper(Player.class).create(player).data = CharData.createRemote("seam", (byte) 0);
      world.getMapper(Position.class).create(player).position.set(-0.64f, 95.27f);
      world.process();
      Map.Zone outdoor = new Map.Zone();
      Levels.Entry row = new Levels.Entry(); row.Id = 110;
      java.lang.reflect.Field field = Map.Zone.class.getDeclaredField("level");
      field.setAccessible(true); field.set(outdoor, row);
      system.onZoneChanged(ZoneChangeEvent.obtain(player, outdoor));
      system.onZoneChanged(ZoneChangeEvent.obtain(player, outdoor));
      assertEquals(0, factory.monsters, "DS1 RoomEx activation owns Shenk placement");
    } finally { world.dispose(); }
  }

  static class CountingFactory extends EntityFactory {
    int monsters;
    public int createMonster(int id, float x, float y) { monsters++; return -1; }
    public int createPlayer(CharData data, Vector2 position) { return -1; }
    public int createDynamicObject(int act, int id, float x, float y) { return -1; }
    public int createStaticObject(int act, int id, float x, float y) { return -1; }
    public int createStaticObjectByClassId(int id, float x, float y) { return -1; }
    public int createWarp(int id, float x, float y) { return -1; }
    public int createItem(Item item, float x, float y) { return -1; }
    public int createMissile(int id, Vector2 angle, Vector2 position) { return -1; }
  }
}
