package com.d2moo.testutil;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.NativePlayerTargetSlots;
import com.riiablo.engine.server.ai.AI;
import com.riiablo.engine.server.ai.Zombie;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.party.CombatOwnership;
import com.riiablo.engine.server.party.PartyManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Slot8TargetTest {
  @Test void membershipIsIndependentOfPlayerGroupsAndNode9() {
    NativeTargetNode node = new NativeTargetNode();
    node.usePlayerSlots(new NativePlayerTargetSlots());
    assertTrue(node.insert(NativeTargetNode.ALLIED, -1));
    assertTrue(node.isNode8()); assertFalse(node.isNode9()); assertEquals(-1, node.ownerId());
    assertFalse(node.insert(NativeTargetNode.WALL, -1));
    node.detach(); assertFalse(node.isNode8());
    assertTrue(node.insert(NativeTargetNode.WALL, -1));
    assertTrue(node.isNode9()); assertFalse(node.isNode8());
  }

  private int soldier(World w, float x, boolean registered) {
    int id = w.create();
    Monster monster = w.getMapper(Monster.class).create(id);
    monster.monstats = new MonStats.Entry(); monster.monstats.Align = 1;
    w.getMapper(Position.class).create(id).position.set(x, 0);
    if (registered) w.getMapper(NativeTargetNode.class).create(id).insert(NativeTargetNode.ALLIED, -1);
    return id;
  }

  @Test void ordinaryScanSeesSlot8WithoutPlayersAndRespectsTiesAndDetach() throws Exception {
    World w = new World(new WorldConfiguration());
    World previous = Riiablo.engine; Riiablo.engine = w;
    try {
      int attacker = soldier(w, 0, false);
      Monster monster = w.getMapper(Monster.class).get(attacker); monster.monstats.Align = 0;
      int unregistered = soldier(w, 1, false);
      int first = soldier(w, 6, true), newest = soldier(w, 6, true);
      AI ai = new Zombie(attacker);
      CombatOwnershipTest.wire(w, new PartyManager(), ai);
      Field field = AI.class.getDeclaredField("monster"); field.setAccessible(true); field.set(ai, monster);
      Method scan = AI.class.getDeclaredMethod("findNearestOrdinaryEnemy", float[].class, float.class);
      scan.setAccessible(true);
      w.process();
      assertEquals(newest, scan.invoke(ai, new float[1], 35f));
      assertTrue(CombatOwnership.canDamage(w, null, attacker, newest, false, false));
      assertFalse(CombatOwnership.canDamage(w, null, first, newest, false, false));
      int player = w.create(); w.getMapper(Player.class).create(player);
      w.getMapper(Position.class).create(player).position.set(6, 0); w.process();
      assertEquals(player, scan.invoke(ai, new float[1], 35f), "player group wins a slot8 tie");
      w.getMapper(Position.class).get(player).position.set(20, 0);
      assertEquals(newest, scan.invoke(ai, new float[1], 35f));
      w.getMapper(NativeTargetNode.class).get(newest).detach();
      assertEquals(first, scan.invoke(ai, new float[1], 35f));
      w.getMapper(Corpse.class).create(first);
      assertEquals(player, scan.invoke(ai, new float[1], 35f));
      w.getMapper(Position.class).get(player).position.set(100, 0);
      assertEquals(-1, scan.invoke(ai, new float[1], 35f));
      assertNotEquals(unregistered, scan.invoke(ai, new float[1], 35f));
      int boundary = soldier(w, 35, true); w.process();
      assertEquals(-1, scan.invoke(ai, new float[1], 35f), "slot8 uses strict native distance gate");
      w.getMapper(Position.class).get(boundary).position.set(34, 0);
      assertEquals(boundary, scan.invoke(ai, new float[1], 35f));
    } finally { Riiablo.engine = previous; w.dispose(); }
  }
}
