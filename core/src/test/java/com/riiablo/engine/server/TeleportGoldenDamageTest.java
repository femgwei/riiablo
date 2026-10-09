package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.skill.NativeSkillResolver;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import org.junit.jupiter.api.Test;

/** Fifty-third DMG-04 audit: Teleport relocates the caster without dealing damage. */
class TeleportGoldenDamageTest extends RiiabloTest {
  @Test
  void levelOneToTwentyAreNondamagingNativePointRelocations() {
    Skills.Entry skill = Riiablo.files.skills.get("Teleport");
    assertNotNull(skill);
    assertEquals(54, skill.Id);
    assertEquals("sor", skill.charclass);
    assertEquals(0, skill.srvstfunc);
    assertEquals(27, skill.srvdofunc);
    assertEquals(0, skill.SrcDam);
    assertEquals(0, skill.MinDam);
    assertEquals(0, skill.MaxDam);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.MinLevDam);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.MaxLevDam);
    assertTrue(blank(skill.EType));
    assertEquals(0, skill.EMin);
    assertEquals(0, skill.EMax);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.EMinLev);
    assertArrayEquals(new int[] {0, 0, 0, 0, 0}, skill.EMaxLev);
    assertEquals(0, skill.ELen);
    assertTrue(blank(skill.srvmissile));
    assertTrue(blank(skill.srvmissilea));
    assertTrue(blank(skill.srvmissileb));
    assertTrue(blank(skill.srvmissilec));
    assertTrue(blank(skill.srvmissiled));

    for (int level = 1; level <= 20; level++) {
      assertEquals(25 - level, NativeSkillResolver.manaCost(skill, level), 0.001f,
          "native mana progression level " + level);
    }
  }

  @Test
  void productionTeleportMovesAndEmitsNoMissileOrDamagePacket() {
    CountingFactory factory = new CountingFactory();
    DamageProbe probe = new DamageProbe();
    Actioneer actioneer = new Actioneer();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), actioneer, new Pathfinder(), factory, probe)
        .build().register("factory", factory).register("map", new ClearMap()));
    try {
      int caster = world.create();
      world.getMapper(Position.class).create(caster).position.set(1, 1);
      world.getMapper(Velocity.class).create(caster).velocity.set(3, 0);
      world.getMapper(Pathfind.class).create(caster);
      world.getMapper(UnitStates.class).create(caster).init(caster);

      assertTrue(actioneer.resolveTeleport(caster, new Vector2(10, 10)));
      assertEquals(new Vector2(10, 10),
          world.getMapper(Position.class).get(caster).position);
      assertEquals(0, factory.missilesCreated);
      assertEquals(0, probe.damageEvents);
    } finally {
      world.dispose();
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isEmpty();
  }

  private static final class DamageProbe extends PassiveSystem {
    int damageEvents;
    @Subscribe public void onDamage(DamageEvent event) { damageEvents++; }
  }

  private static final class ClearMap extends Map {
    ClearMap() { super(0, 0); }
    @Override public int flags(Vector2 position) { return 0; }
  }

  private static final class CountingFactory extends EntityFactory {
    int missilesCreated;

    @Override public int createPlayer(CharData data, Vector2 position) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createDynamicObject(int act, int id, float x, float y) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createStaticObject(int act, int id, float x, float y) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createStaticObjectByClassId(int id, float x, float y) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createMonster(int id, float x, float y) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createWarp(int index, float x, float y) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createItem(Item item, float x, float y) {
      return Engine.INVALID_ENTITY;
    }

    @Override public int createMissile(int missileId, Vector2 direction, Vector2 position) {
      missilesCreated++;
      return Engine.INVALID_ENTITY;
    }
  }
}
