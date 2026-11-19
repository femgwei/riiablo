package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.AnimDataKeyframeEvent;
import com.riiablo.engine.server.event.SkillStartEvent;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import java.util.stream.IntStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** End-to-end server-side Bash start/keyframe smoke test. */
class BarbarianBashLifecycleTest extends RiiabloTest {
  static IntStream bashLevels() { return IntStream.rangeClosed(1, 40); }

  @ParameterizedTest(name = "Bash level {0}")
  @MethodSource("bashLevels")
  void bashUsesSrvSt32RecordAndSettlesDamageAtAttackKeyframe(int level) {
    Actioneer actioneer = new Actioneer();
    CombatRandomFixture.bind(actioneer, bound -> 0);
    DummyFactory factory = new DummyFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), actioneer, new Pathfinder(), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      Skills.Entry bash = Riiablo.files.skills.get("Bash");
      CharData data = CharData.obtain().clear().set(Riiablo.NORMAL, false,
          "bash", Riiablo.BARBARIAN);
      data.setSkillLevel(bash.Id, level);
      data.getStats().base().put(Stat.strength, 10);
      data.getStats().base().put(Stat.tohit, 100000);
      data.getItems().equipItem(BodyLoc.RARM, data.getItems().add(weapon("hax", 10)));

      int attacker = unit(world, true, 0, 0, attributes(1000, 1, 2, 100000));
      world.getMapper(Player.class).get(attacker).data = data;
      int target = unit(world, false, 1, 0, attributes(1000, 0, 0, 0));
      Casting casting = world.getMapper(Casting.class).create(attacker)
          .set(bash.Id, target, new Vector2(1, 0));
      actioneer.onSkillStart(SkillStartEvent.obtain(
          attacker, bash.Id, target, casting.targetVec, bash.srvstfunc, bash.cltstfunc));
      assertTrue(casting.weaponSkillPrepared, "Bash must allocate its SrvSt32 combat record");
      assertNotNull(casting.weaponSkillCombat);
      float cachedDamage = casting.weaponSkillCombat.totalDamageHp();
      assertEquals(level, casting.weaponSkillCombat.physicalDamageHp() - cachedDamage, 0.0001f,
          "SrvSt32 adds calc2 to physical after caching life damage");

      float before = hp(world, target);
      actioneer.onAnimDataKeyframe(AnimDataKeyframeEvent.obtain(attacker, Engine.KEYFRAME_ATK));
      assertTrue(hp(world, target) < before, "Bash keyframe must settle damage");
      assertEquals(cachedDamage, before - hp(world, target), 0.0001f,
          "The ECS must settle cached total, not the later physical addition");
    } finally {
      world.dispose();
    }
  }

  private static int unit(World world, boolean player, float x, float y, Attributes attrs) {
    int id = world.create();
    world.getMapper(Class.class).create(id).type = player ? Class.Type.PLR : Class.Type.MON;
    if (player) world.getMapper(Player.class).create(id);
    else world.getMapper(Monster.class).create(id);
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static Attributes attributes(float hp, int min, int max, int toHit) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.hitpoints, hp); attrs.base().put(Stat.maxhp, hp);
    attrs.base().put(Stat.mindamage, min); attrs.base().put(Stat.maxdamage, max);
    attrs.base().put(Stat.tohit, toHit); attrs.base().put(Stat.level, 1);
    attrs.base().put(Stat.armorclass, 0); attrs.reset();
    return attrs;
  }

  private static Item weapon(String code, int damage) {
    Item item = new Item(); item.reset(); item.setBase(Riiablo.files.weapons.get(code));
    item.attrs.base().get(Stat.mindamage).set(damage);
    item.attrs.base().get(Stat.maxdamage).set(damage); item.attrs.reset();
    return item;
  }

  private static float hp(World world, int entityId) {
    return world.getMapper(AttributesWrapper.class).get(entityId)
        .attrs.get(Stat.hitpoints).asFixed();
  }

  private static final class DummyFactory extends EntityFactory {
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
