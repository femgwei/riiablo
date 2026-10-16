package com.d2moo.testutil;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.riiablo.engine.server.*;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.party.CombatOwnership;
import com.riiablo.engine.server.party.PartyManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatOwnershipTest {
  private int player(World w) { int id = w.create(); w.getMapper(Player.class).create(id); return id; }
  private int monster(World w, int owner) {
    int id = w.create(); w.getMapper(Monster.class).create(id).minionOwnerId = owner; return id;
  }
  private int pet(World w, int owner) {
    int id = monster(w, -1); w.getMapper(SummonedPet.class).create(id).ownerId = owner; return id;
  }

  @Test void targetTraversesPetsMercenariesAndOrdinaryMonsterOwners() {
    World w = new World(new WorldConfiguration());
    try {
      int p = player(w), merc = monster(w, -1);
      w.getMapper(Mercenary.class).create(merc).ownerId = p;
      int child = pet(w, merc), minion = monster(w, child);
      assertEquals(p, CombatOwnership.target(w, minion));
      assertEquals(child, CombatOwnership.source(w, minion));
      assertEquals(merc, CombatOwnership.source(w, child));
      int missile = w.create(); w.getMapper(Missile.class).create(missile).ownerId = child;
      assertEquals(child, CombatOwnership.source(w, missile));
      assertEquals(missile, CombatOwnership.target(w, missile));
      // A player never follows an accidentally attached owner-bearing component.
      w.getMapper(SummonedPet.class).create(p).ownerId = minion;
      assertEquals(p, CombatOwnership.target(w, p));
    } finally { w.dispose(); }
  }

  @Test void missingSelfAndCyclicOwnersTerminateSafely() {
    World w = new World(new WorldConfiguration());
    try {
      int a = monster(w, -1), b = monster(w, a);
      assertEquals(a, CombatOwnership.target(w, a));
      w.getMapper(Monster.class).get(a).minionOwnerId = a;
      assertEquals(a, CombatOwnership.target(w, a));
      w.getMapper(Monster.class).get(a).minionOwnerId = 100000;
      assertEquals(a, CombatOwnership.target(w, a));
      w.getMapper(Monster.class).get(a).minionOwnerId = b;
      assertEquals(-1, CombatOwnership.target(w, a));
      assertFalse(CombatOwnership.canDamage(w, null, b, a, false, false));
      assertEquals(-1, CombatOwnership.target(w, -1));
    } finally { w.dispose(); }
  }

  /** Invoke real relation entry points with real ECS mappers, without unrelated system ticks. */
  private Object system(World w, PartyManager parties, Class<?> type) throws Exception {
    Object system = type.getDeclaredConstructor().newInstance();
    wire(w, parties, system);
    return system;
  }

  private void wire(World w, PartyManager parties, Object system) throws Exception {
    for (Class<?> c = system.getClass(); c != null; c = c.getSuperclass()) for (Field f : c.getDeclaredFields()) {
      f.setAccessible(true);
      if (f.getType() == ComponentMapper.class) {
        Class<?> component = (Class<?>) ((ParameterizedType) f.getGenericType()).getActualTypeArguments()[0];
        f.set(system, w.getMapper(component.asSubclass(com.artemis.Component.class)));
      } else if (f.getName().equals("world")) f.set(system, w);
      else if (f.getName().equals("partyManager")) f.set(system, parties);
    }
  }

  private boolean gate(Object system, int source, int target) throws Exception {
    boolean flags = system instanceof Actioneer;
    String name = flags ? "canDamageRelation" : system instanceof MissileCollisionSystem ? "isEnemy" : "isHostile";
    Method m = flags ? system.getClass().getDeclaredMethod(name, int.class, int.class, boolean.class, boolean.class)
        : system.getClass().getDeclaredMethod(name, int.class, int.class);
    m.setAccessible(true);
    return (Boolean) (flags ? m.invoke(system, source, target, true, true) : m.invoke(system, source, target));
  }

  @Test void productionGatesUseRootTargetHostilityAndRejectSameOwner() throws Exception {
    World w = new World(new WorldConfiguration());
    try {
      int p = player(w), q = player(w), parent = pet(w, q), child = pet(w, parent);
      for (Class<?> type : new Class<?>[] {Actioneer.class, MissileCollisionSystem.class,
          ServerSkillSystem.class, StateUpdater.class, AuraEcsSystem.class}) {
        PartyManager parties = new PartyManager();
        Object system = system(w, parties, type);
        assertFalse(gate(system, p, child), type.getSimpleName());
        assertTrue(parties.declareHostility(p, q));
        assertTrue(gate(system, p, child), type.getSimpleName());
        int ordinaryMinion = monster(w, parent);
        if (type != Actioneer.class) assertTrue(gate(system, p, ordinaryMinion), type.getSimpleName());
        assertFalse(gate(system, q, child), type.getSimpleName());
        parties.removeHostility(p, q);
      }
    } finally { w.dispose(); }
  }

  @Test void aiAcquiresNestedHostilePetAndRejectsOwnerCycle() throws Exception {
    World w = new World(new WorldConfiguration());
    World previous = com.riiablo.Riiablo.engine;
    com.riiablo.Riiablo.engine = w;
    try {
      PartyManager parties = new PartyManager();
      int p = player(w), q = player(w), source = pet(w, p), parent = pet(w, q), child = pet(w, parent);
      w.getMapper(com.riiablo.engine.server.component.Position.class).create(source);
      w.getMapper(com.riiablo.engine.server.component.Position.class).create(child);
      com.riiablo.engine.server.ai.AI ai = new com.riiablo.engine.server.ai.Zombie(source);
      wire(w, parties, ai);
      Class<?> type = com.riiablo.engine.server.ai.AI.class;
      Method gate = type.getDeclaredMethod("isValidOrdinaryEnemyTarget", int.class, boolean.class, boolean.class);
      gate.setAccessible(true);
      assertFalse((Boolean) gate.invoke(ai, child, false, false));
      assertTrue(parties.declareHostility(p, q));
      assertTrue((Boolean) gate.invoke(ai, child, false, false));
      w.getMapper(SummonedPet.class).get(parent).ownerId = child;
      assertFalse((Boolean) gate.invoke(ai, child, false, false));
    } finally { com.riiablo.Riiablo.engine = previous; w.dispose(); }
  }

  @Test void conversionBranchesStillRejectPlayerDamage() throws Exception {
    World w = new World(new WorldConfiguration());
    try {
      int p = player(w), converted = monster(w, p);
      w.getMapper(Monster.class).get(converted).converted = true;
      for (Class<?> type : new Class<?>[] {Actioneer.class, MissileCollisionSystem.class, StateUpdater.class}) {
        assertFalse(gate(system(w, new PartyManager(), type), p, converted), type.getSimpleName());
      }
    } finally { w.dispose(); }
  }
}
