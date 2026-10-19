package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.map.Map;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class MonsterComponentMissileIntegrationTest extends RiiabloTest {
  @Test void actualFactoryRetainsOrdinalsAndOblivionLayersMatch() {
    try (Fixture f = new Fixture()) {
      for (String row : new String[] {"doomknight2", "doomknight3", "dkmag1", "necromage", "skeleton1"}) {
        for (int i = 0; i < 8; i++) {
          int id = f.spawn(row);
          Monster monster = f.world.getMapper(Monster.class).get(id);
          int[] codes = f.world.getMapper(CofComponents.class).get(id).component;
          for (int slot = 0; slot < 16; slot++) {
            String values = monster.monstats2.ComponentV[slot].replace("\"", "");
            if (values.isEmpty()) continue;
            String[] variants = values.split(",");
            int ordinal = monster.componentVariants[slot];
            assertTrue(ordinal >= 0 && ordinal < variants.length, row + " slot " + slot);
            assertEquals(Riiablo.files.compcode.index(variants[ordinal]), codes[slot]);
          }
          if (row.startsWith("doomknight")) {
            assertTrue(monster.componentVariants[10] < 4);
            assertTrue(codes[10] >= 4);
          }
          if (row.equals("doomknight3")) {
            assertEquals(monster.componentVariants[10], monster.componentVariants[11]);
          }
        }
      }
    }
  }

  @Test void bothCallbacksCreateExactlyTheSelectedMissileForEveryVariant() {
    try (Fixture f = new Fixture()) {
      for (int function : new int[] {148, 149}) {
        Skills.Entry skill = null;
        for (Skills.Entry candidate : Riiablo.files.skills) {
          if (candidate.srvdofunc == function) { skill = candidate; break; }
        }
        assertNotNull(skill);
        int source = f.spawn(function == 148 ? "doomknight2" : "necromage");
        Monster monster = f.world.getMapper(Monster.class).get(source);
        int target = f.world.create();
        f.world.getMapper(Position.class).create(target).position.set(30, 10);
        int slot = function == 148 ? 10 : 11;
        int base = Riiablo.files.Missiles.get(skill.srvmissilea).Id;
        for (int v = 0; v < 4; v++) {
          monster.componentVariants[slot] = v;
          // The other callback's slot must not affect this selection.
          monster.componentVariants[slot == 10 ? 11 : 10] = 3 - v;
          f.world.process();
          int before = f.missiles();
          f.events().dispatch(SkillDoEvent.obtain(source, skill.Id, target,
              new Vector2(30, 10), function, skill.cltdofunc));
          f.world.process();
          assertEquals(before + 1, f.missiles());
          int found = 0;
          com.artemis.utils.IntBag ids = f.world.getAspectSubscriptionManager()
              .get(com.artemis.Aspect.all(Missile.class)).getEntities();
          for (int j = 0; j < ids.size(); j++) {
            Missile missile = f.world.getMapper(Missile.class).get(ids.get(j));
            if (missile.ownerId == source && missile.missile.Id == base + v) {
              found++;
              assertEquals((function == 148
                  ? new String[] {"pois", "fire", "cold", ""}
                  : new String[] {"pois", "cold", "fire", "ltng"})[v], missile.missile.EType);
              assertTrue(missile.damageSnapshot, "selected table damage must survive empty Skills damage");
            }
          }
          assertEquals(1, found, "SrvDo" + function + " variant " + v);
        }
        int before = f.missiles();
        f.events().dispatch(SkillDoEvent.obtain(source, skill.Id, -1,
            new Vector2(30, 10), function, skill.cltdofunc));
        f.world.process();
        assertEquals(before, f.missiles(), "native callback requires unit target");
      }
    }
  }

  @Test void summonedMageUsesOwnerRollForBothElementalLayers() {
    try (Fixture f = new Fixture()) {
      int owner = f.world.create();
      f.world.getMapper(Player.class).create(owner);
      f.world.getMapper(Position.class).create(owner).position.set(10, 10);
      Map.Zone zone = mock(Map.Zone.class);
      when(zone.findFreeCoordinates(any(Vector2.class), anyInt(), anyInt(), eq(true), any(Vector2.class)))
          .thenReturn(true);
      when(f.map.getZone(anyFloat(), anyFloat())).thenReturn(zone);
      MonStats.Entry row = Riiablo.files.monstats.get("necromage");
      for (int i = 0; i < 4; i++) {
        UnitRandomSeed ownerSeed = f.world.getMapper(UnitRandomSeed.class).create(owner);
        ownerSeed.low = 1234 + i;
        ownerSeed.seed = null;
        NativeSeedReference expected = new NativeSeedReference(1234 + i);
        int variant = (int) (expected.nextUnsigned() & 3);
        int id = f.factory.createSummonedPet(owner, row, "skeletonmage", 80, 1, 8,
            false, 0, 10, 10);
        assertTrue(id >= 0);
        Monster monster = f.world.getMapper(Monster.class).get(id);
        assertEquals(variant, monster.componentVariants[11]);
        assertEquals(variant, monster.componentVariants[12]);
        assertEquals(expected.state(), ownerSeed.low, "one owner roll, not a pet roll");
        int[] codes = f.world.getMapper(CofComponents.class).get(id).component;
        assertEquals(codes[11], codes[12]);
      }
    }
  }

  @Test void invalidBaseOrResultCannotFallBackAndMonsterReinitializationClearsChoices() {
    Monster monster = new Monster();
    monster.componentVariants[10] = 3;
    monster.set(null, null);
    for (int v : monster.componentVariants) assertEquals(0, v);
    assertNull(ServerSkillSystem.resolveMonsterComponentMissile(Riiablo.files, monster, "missing-missile", 10));
    monster.componentVariants[10] = 100000;
    assertNull(ServerSkillSystem.resolveMonsterComponentMissile(Riiablo.files, monster, "undeadmissile1", 10));
    monster.componentVariants[10] = -1;
    assertNull(ServerSkillSystem.resolveMonsterComponentMissile(Riiablo.files, monster, "undeadmissile1", 10));
    assertEquals("undeadmissile1", ServerSkillSystem.resolveMonsterComponentMissile(
        Riiablo.files, null, "undeadmissile1", 10));
  }

  static final class Fixture implements AutoCloseable {
    final World previous = Riiablo.engine;
    final ServerEntityFactory factory = new ServerEntityFactory();
    final Map map = mock(Map.class);
    final World world;
    Fixture() {
      Map.Zone zone = new Map.Zone();
      zone.level = Riiablo.files.Levels.get(2);
      when(map.getZone(anyFloat(), anyFloat())).thenReturn(zone);
      world = new World(new WorldConfigurationBuilder().with(new EventSystem(), new ItemManager(),
          new ObjectInteractor(), new WarpInteractor(), new ItemInteractor(), new CofManager(),
          new Pathfinder(), new ServerSkillSystem(true), factory)
          .build().register("map", map).register("factory", factory)
          .register("serverContext", new ServerGameplayContext(Riiablo.files, 1234))
          .register("townPortalRegistry", new com.riiablo.engine.server.portal.TownPortalRegistry()));
      Riiablo.engine = world;
    }
    int spawn(String name) {
      MonStats.Entry row = Riiablo.files.monstats.get(name);
      assertNotNull(row, name);
      return factory.createMonster(row.hcIdx, 10, 10);
    }
    EventSystem events() { return world.getSystem(EventSystem.class); }
    int missiles() {
      return world.getAspectSubscriptionManager().get(com.artemis.Aspect.all(Missile.class)).getEntities().size();
    }
    @Override public void close() { world.dispose(); Riiablo.engine = previous; }
  }
}
