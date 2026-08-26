package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.BaseSystem;
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
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.event.AnimDataKeyframeEvent;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import org.junit.jupiter.api.Test;

/** Real ECS event-chain scenarios for representative Act I monster skills. */
class Act1MonsterEcsScenarioTest extends RiiabloTest {
  @Test
  void maggotDownKeyframeHealsThroughActioneer() {
    MonStats.Entry row = Riiablo.files.monstats.get("sandmaggot1");
    Skills.Entry skill = Riiablo.files.skills.get(row.Skill2);
    Scenario scenario = new Scenario(row, null);
    try {
      Attributes life = scenario.attributes(100, 200);
      scenario.attributes(scenario.source).attrs = life;
      scenario.actioneer.cast(scenario.source, skill.Id, Engine.INVALID_ENTITY, scenario.position(10, 10));
      scenario.keyframe(scenario.source);
      assertEquals(110f, life.get(Stat.hitpoints).asFixed(), 0.001f);
      assertEquals(1, scenario.probe.skillDo);
      System.out.println("[ACT1_ECS_CHAIN] skill=MaggotDown keyframe=1 hp=100->"
          + life.get(Stat.hitpoints).asFixed() + " status=PASS");
    } finally {
      scenario.close();
    }
  }

  @Test
  void nestKeyframeCreatesConfiguredSpawnThroughFactory() {
    MonStats.Entry row = Riiablo.files.monstats.get("crownest3");
    Skills.Entry skill = Riiablo.files.skills.get(row.Skill1);
    Scenario scenario = new Scenario(row, null);
    try {
      System.out.println("[ACT1_ECS_DEBUG] nest row=" + row.Id + " skill1=" + row.Skill1
          + " skillId=" + skill.Id + " spawn=" + row.spawn + " minion1=" + row.minion1
          + " minion2=" + row.minion2);
      int target = scenario.target(12, 10);
      scenario.actioneer.cast(scenario.source, skill.Id, target, scenario.position(12, 10));
      scenario.keyframe(scenario.source);
      assertEquals(1, scenario.factory.monstersCreated);
      String expectedSpawn = row.spawn != null && !row.spawn.isEmpty() ? row.spawn
          : (row.minion1 != null && !row.minion1.isEmpty() ? row.minion1 : row.minion2);
      assertEquals(expectedSpawn, scenario.factory.lastMonster);
      assertEquals(1, scenario.probe.skillDo);
      System.out.println("[ACT1_ECS_CHAIN] skill=Nest spawn=" + row.spawn
          + " created=" + scenario.factory.monstersCreated + " status=PASS");
    } finally {
      scenario.close();
    }
  }

  @Test
  void vampireMeteorKeyframeDelegatesToServerMissileSystem() {
    MonStats.Entry row = Riiablo.files.monstats.get("vampire1");
    Skills.Entry skill = Riiablo.files.skills.get("VampireMeteor");
    Scenario scenario = new Scenario(row, new ServerSkillSystem(true));
    try {
      int target = scenario.target(16, 10);
      scenario.actioneer.cast(scenario.source, skill.Id, target, scenario.position(16, 10));
      scenario.keyframe(scenario.source);
      assertEquals(1, scenario.factory.missilesCreated);
      assertEquals(1, scenario.probe.skillDo);
      System.out.println("[ACT1_ECS_CHAIN] skill=VampireMeteor missile="
          + skill.srvmissilea + " created=" + scenario.factory.missilesCreated + " status=PASS");
    } finally {
      scenario.close();
    }
  }

  private static final class Scenario {
    final Probe probe = new Probe();
    final TestFactory factory = new TestFactory();
    final Actioneer actioneer = new Actioneer();
    final World world;
    final int source;

    Scenario(MonStats.Entry row, BaseSystem extra) {
      WorldConfigurationBuilder builder = new WorldConfigurationBuilder()
          .with(new EventSystem(), probe, actioneer, new Pathfinder(), factory);
      if (extra != null) builder.with(extra);
      world = new World(builder.build().register("map", new WalkableMap())
          .register("factory", factory));
      factory.world = world;
      source = world.create();
      MonStats2.Entry row2 = Riiablo.files.monstats2.get(row.MonStatsEx);
      world.getMapper(Monster.class).create(source).set(row, row2);
      world.getMapper(Class.class).create(source).type = Class.Type.MON;
      world.getMapper(Position.class).create(source).position.set(10, 10);
      world.getMapper(Angle.class).create(source);
      world.getMapper(MovementModes.class).create(source).set(
          Engine.Monster.MODE_NU, Engine.Monster.MODE_WL, Engine.Monster.MODE_RN);
      world.getMapper(AttributesWrapper.class).create(source).attrs = attributes(100, 100);
    }

    AttributesWrapper attributes(int entity) {
      return world.getMapper(AttributesWrapper.class).get(entity);
    }

    Attributes attributes(float current, float max) {
      Attributes attrs = Attributes.obtainStandard();
      attrs.base().put(Stat.hitpoints, current);
      attrs.base().put(Stat.maxhp, max);
      attrs.base().put(Stat.level, 1);
      attrs.reset();
      return attrs;
    }

    int target(float x, float y) {
      int id = world.create();
      world.getMapper(Class.class).create(id).type = Class.Type.PLR;
      world.getMapper(Position.class).create(id).position.set(x, y);
      world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(100, 100);
      return id;
    }

    Vector2 position(float x, float y) {
      return new Vector2(x, y);
    }

    void keyframe(int entity) {
      world.getSystem(EventSystem.class).dispatch(
          AnimDataKeyframeEvent.obtain(entity, Engine.KEYFRAME_ATK));
    }

    void close() {
      world.dispose();
    }
  }

  private static final class Probe extends BaseSystem {
    int skillDo;
    int damage;
    @Subscribe public void onSkillDo(SkillDoEvent event) { skillDo++; }
    @Subscribe public void onDamage(DamageEvent event) { damage++; }
    @Override protected void processSystem() {}
  }

  private static final class TestFactory extends EntityFactory {
    World world;
    int monstersCreated;
    int missilesCreated;
    String lastMonster;

    @Override public int createPlayer(CharData data, Vector2 position) { return -1; }
    @Override public int createDynamicObject(int act, int preset, float x, float y) { return -1; }
    @Override public int createStaticObject(int act, int object, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int object, float x, float y) { return -1; }
    @Override public int createMonster(int monster, float x, float y) {
      monstersCreated++;
      MonStats.Entry row = Riiablo.files.monstats.get(monster);
      lastMonster = row != null ? row.Id : null;
      return 9000 + monstersCreated;
    }
    @Override public int createWarp(int index, float x, float y) { return -1; }
    @Override public int createItem(Item item, float x, float y) { return -1; }
    @Override public int createMissile(int missile, Vector2 angle, Vector2 position) {
      missilesCreated++;
      int id = world.create();
      world.getMapper(Missile.class).create(id);
      return id;
    }
    @Override public int createMissile(int missile, Vector2 angle, Vector2 position, int ownerId) {
      missilesCreated++;
      int id = world.create();
      world.getMapper(Missile.class).create(id);
      return id;
    }
  }

  /** Minimal walkable map so the real Nest placement search can run headless. */
  private static final class WalkableMap extends Map {
    WalkableMap() { super(0, 0); }
    @Override public int flags(int x, int y) { return 0; }
  }
}
