package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.MovementModes;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.AnimDataKeyframeEvent;
import com.riiablo.engine.server.skill.PaladinSkills;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Headless ECS coverage for native Paladin SrvSt35/SrvDo002 Vengeance. */
class VengeanceIntegrationTest extends RiiabloTest {
  @Test
  void oneSuccessfulHitAddsFireColdAndLightningFromOneWeaponRoll() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.VENGEANCE);
    Actioneer actioneer = new Actioneer();
    NoopFactory factory = new NoopFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), actioneer, new Pathfinder(), factory)
        .build().register("map", new Map(0, 0)).register("factory", factory));
    try {
      int source = world.create();
      world.getMapper(Player.class).create(source);
      world.getMapper(Class.class).create(source).type = Class.Type.PLR;
      world.getMapper(Position.class).create(source).position.set(10, 10);
      world.getMapper(Angle.class).create(source);
      world.getMapper(MovementModes.class).create(source).set(
          Engine.Player.MODE_NU, Engine.Player.MODE_WL, Engine.Player.MODE_RN);
      Attributes attacker = combatAttributes(200, 10, 10, 10000);
      world.getMapper(AttributesWrapper.class).create(source).attrs = attacker;

      int target = world.create();
      world.getMapper(Class.class).create(target).type = Class.Type.MON;
      world.getMapper(Position.class).create(target).position.set(11, 10);
      Attributes defender = combatAttributes(200, 1, 1, 1);
      world.getMapper(AttributesWrapper.class).create(target).attrs = defender;

      int fire = PaladinSkills.getVengeanceElementPercent(skill, 1, 0) * 10 / 100;
      int cold = PaladinSkills.getVengeanceElementPercent(skill, 1, 1) * 10 / 100;
      int lightning = PaladinSkills.getVengeanceElementPercent(skill, 1, 2) * 10 / 100;
      MathUtils.random.setSeed(0x11135L);
      float before = defender.get(Stat.hitpoints).asFixed();
      actioneer.cast(source, skill.Id, target, new Vector2(11, 10));
      world.getSystem(EventSystem.class).dispatch(
          AnimDataKeyframeEvent.obtain(source, Engine.KEYFRAME_ATK));
      float dealt = before - defender.get(Stat.hitpoints).asFixed();

      assertTrue(dealt >= 10 + fire + cold + lightning,
          "one Vengeance hit must include physical plus all three elemental packets");
      assertEquals(35, skill.srvstfunc);
      assertEquals(2, skill.srvdofunc);
      System.out.println("[PALADIN_VENGEANCE] physical=10 fire=" + fire + " cold=" + cold
          + " lightning=" + lightning + " dealt=" + dealt + " status=PASS");
    } finally {
      world.dispose();
    }
  }

  private static Attributes combatAttributes(float life, int min, int max, int toHit) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.maxhp, life);
    attrs.base().put(Stat.hitpoints, life);
    attrs.base().put(Stat.level, 1);
    attrs.base().put(Stat.mindamage, min);
    attrs.base().put(Stat.maxdamage, max);
    attrs.base().put(Stat.tohit, toHit);
    attrs.base().put(Stat.armorclass, 0);
    attrs.reset();
    return attrs;
  }

  private static final class NoopFactory extends EntityFactory {
    @Override public int createPlayer(CharData data, Vector2 position) { return -1; }
    @Override public int createDynamicObject(int act, int preset, float x, float y) { return -1; }
    @Override public int createStaticObject(int act, int object, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int object, float x, float y) { return -1; }
    @Override public int createMonster(int monster, float x, float y) { return -1; }
    @Override public int createWarp(int index, float x, float y) { return -1; }
    @Override public int createItem(Item item, float x, float y) { return -1; }
    @Override public int createMissile(int missile, Vector2 angle, Vector2 position) { return -1; }
  }
}
