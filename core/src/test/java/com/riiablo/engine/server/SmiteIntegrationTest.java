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
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.MovementModes;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.AnimDataKeyframeEvent;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Headless coverage for native monster SrvDo150 Smite. */
class SmiteIntegrationTest extends RiiabloTest {
  @Test
  void blunderboreSmiteUsesA2DamageAtAnimationKeyframe() {
    MonStats.Entry row = Riiablo.files.monstats.get("blunderbore1");
    Skills.Entry skill = Riiablo.files.skills.get("Smite");
    assertTrue(row != null && skill != null);
    assertEquals(150, skill.srvdofunc);
    Actioneer actioneer = new Actioneer();
    NoopFactory factory = new NoopFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), actioneer, new Pathfinder(), factory)
        .build().register("map", new Map(0, 0)).register("factory", factory));
    try {
      int source = world.create();
      Monster monster = world.getMapper(Monster.class).create(source)
          .set(row, Riiablo.files.monstats2.get(row.MonStatsEx))
          .setAttack2Profile(20, 20, 1000);
      world.getMapper(Class.class).create(source).type = Class.Type.MON;
      world.getMapper(Position.class).create(source).position.set(10, 10);
      world.getMapper(Angle.class).create(source);
      world.getMapper(MovementModes.class).create(source).set(
          Engine.Monster.MODE_NU, Engine.Monster.MODE_WL, Engine.Monster.MODE_RN);
      world.getMapper(AttributesWrapper.class).create(source).attrs = life(100, 100);

      int target = world.create();
      world.getMapper(Class.class).create(target).type = Class.Type.PLR;
      world.getMapper(Position.class).create(target).position.set(11, 10);
      Attributes targetAttrs = life(100, 100);
      world.getMapper(AttributesWrapper.class).create(target).attrs = targetAttrs;

      MathUtils.random.setSeed(0x5151L);
      actioneer.cast(source, skill.Id, target, new Vector2(11, 10));
      world.getSystem(EventSystem.class).dispatch(
          AnimDataKeyframeEvent.obtain(source, Engine.KEYFRAME_ATK));
      float after = targetAttrs.get(Stat.hitpoints).asFixed();
      assertEquals(80f, after, 0.001f);
      System.out.println("[SMITE_CHAIN] source=" + source + " target=" + target
          + " a2Damage=20 hp=100.0->" + after + " stunFormula=" + skill.calc2
          + " status=PASS");
    } finally {
      world.dispose();
    }
  }

  private static Attributes life(float max, float current) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.maxhp, max);
    attrs.base().put(Stat.hitpoints, current);
    attrs.base().put(Stat.level, 1);
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
