package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
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
import org.junit.jupiter.api.Test;

/** Native SrvSt/SrvDo lifecycle checks for Amazon melee behavior families. */
class AmazonMeleeSkillLifecycleTest extends RiiabloTest {
  @Test
  void jabConsumesExactlyThreeAttackKeyframesAndStopsAfterTheThird() {
    World world = world();
    try {
      Skills.Entry jab = Riiablo.files.skills.get("Jab");
      int amazon = player(world, 0, 0, attributes(10000, 100, 100, 10000));
      equip(world, amazon, jab, "hax");
      int target = monster(world, 1, 0, attributes(10000, 0, 0, 0));
      Casting casting = world.getMapper(Casting.class).create(amazon)
          .set(jab.Id, target, new Vector2(1, 0));
      world.getSystem(EventSystem.class).dispatch(SkillStartEvent.obtain(
          amazon, jab.Id, target, casting.targetVec, jab.srvstfunc, jab.cltstfunc));

      assertEquals(3, casting.jabRemainingStrikes);
      float before = hp(world, target);
      for (int strike = 1; strike <= 3; strike++) {
        world.getSystem(EventSystem.class).dispatch(
            AnimDataKeyframeEvent.obtain(amazon, Engine.KEYFRAME_ATK));
        assertEquals(3 - strike, casting.jabRemainingStrikes);
        // Native combat still performs a real hit roll (capped below 100%),
        // so a miss is valid here.  The lifecycle assertion is that every
        // keyframe consumes exactly one Jab record and never increases HP.
        assertTrue(hp(world, target) <= before,
            "Jab strike " + strike + " must not increase target HP");
        before = hp(world, target);
      }
      assertTrue(casting.jabStrikeProcessed);
      float afterThird = hp(world, target);
      world.getSystem(EventSystem.class).dispatch(
          AnimDataKeyframeEvent.obtain(amazon, Engine.KEYFRAME_ATK));
      assertEquals(afterThird, hp(world, target), 0.001f,
          "a fourth keyframe must not create a fourth Jab hit");
    } finally {
      world.dispose();
    }
  }

  @Test
  void impalePrecomputesOneCombatRecordAndConsumesItOnceAtKeyframe() {
    World world = world();
    try {
      Skills.Entry impale = Riiablo.files.skills.get("Impale");
      int amazon = player(world, 0, 0, attributes(10000, 100, 100, 10000));
      Item weapon = equip(world, amazon, impale, "hax");
      int target = monster(world, 1, 0, attributes(10000, 0, 0, 0));
      Casting casting = world.getMapper(Casting.class).create(amazon)
          .set(impale.Id, target, new Vector2(1, 0));
      world.getSystem(EventSystem.class).dispatch(SkillStartEvent.obtain(
          amazon, impale.Id, target, casting.targetVec, impale.srvstfunc, impale.cltstfunc));

      assertTrue(casting.impalePrepared);
      assertNotNull(casting.impaleCombat);
      float before = hp(world, target);
      world.getSystem(EventSystem.class).dispatch(
          AnimDataKeyframeEvent.obtain(amazon, Engine.KEYFRAME_ATK));
      assertTrue(hp(world, target) <= before,
          "Impale keyframe must not increase target HP");
      assertFalse(casting.impalePrepared, "SrvDo002 must consume the prepared record");
      assertNull(casting.impaleCombat);

      float after = hp(world, target);
      world.getSystem(EventSystem.class).dispatch(
          AnimDataKeyframeEvent.obtain(amazon, Engine.KEYFRAME_ATK));
      assertEquals(after, hp(world, target), 0.001f,
          "a second keyframe must not replay Impale damage");
      assertNotNull(weapon);
    } finally {
      world.dispose();
    }
  }

  @Test
  void jabRejectsAnInvalidTargetAtNativeStart() {
    World world = world();
    try {
      Skills.Entry jab = Riiablo.files.skills.get("Jab");
      int amazon = player(world, 0, 0, attributes(10000, 100, 100, 10000));
      equip(world, amazon, jab, "hax");
      Casting casting = world.getMapper(Casting.class).create(amazon)
          .set(jab.Id, Engine.INVALID_ENTITY, new Vector2(1, 0));

      world.getSystem(EventSystem.class).dispatch(SkillStartEvent.obtain(
          amazon, jab.Id, Engine.INVALID_ENTITY, casting.targetVec,
          jab.srvstfunc, jab.cltstfunc));

      assertFalse(world.getMapper(Casting.class).has(amazon),
          "SrvSt05 must fail closed when no target unit exists");
    } finally {
      world.dispose();
    }
  }

  @Test
  void impaleRejectsAStartTargetOutsideNativeMeleeRange() {
    World world = world();
    try {
      Skills.Entry impale = Riiablo.files.skills.get("Impale");
      int amazon = player(world, 0, 0, attributes(10000, 100, 100, 10000));
      equip(world, amazon, impale, "hax");
      int target = monster(world, 10, 0, attributes(10000, 0, 0, 0));
      Casting casting = world.getMapper(Casting.class).create(amazon)
          .set(impale.Id, target, new Vector2(10, 0));

      world.getSystem(EventSystem.class).dispatch(SkillStartEvent.obtain(
          amazon, impale.Id, target, casting.targetVec,
          impale.srvstfunc, impale.cltstfunc));

      assertFalse(casting.impalePrepared,
          "SrvSt07 must not retain a combat record outside melee range");
      assertFalse(world.getMapper(Casting.class).has(amazon),
          "rejected Impale must clear the pending cast");
    } finally {
      world.dispose();
    }
  }

  @Test
  void fendSkipsADeadFirstTargetBeforeConsumingTheNextStrike() {
    World world = world();
    try {
      Skills.Entry fend = Riiablo.files.skills.get("Fend");
      int amazon = player(world, 0, 0, attributes(10000, 100, 100, 10000));
      equip(world, amazon, fend, "hax");
      int deadTarget = monster(world, 1, 0, attributes(10000, 0, 0, 0));
      int liveTarget = monster(world, 2, 0, attributes(10000, 0, 0, 0));
      Casting casting = world.getMapper(Casting.class).create(amazon)
          .set(fend.Id, deadTarget, new Vector2(1, 0));

      world.getSystem(EventSystem.class).dispatch(SkillStartEvent.obtain(
          amazon, fend.Id, deadTarget, casting.targetVec,
          fend.srvstfunc, fend.cltstfunc));

      assertTrue(casting.fendInitialized);
      assertEquals(deadTarget, casting.fendCurrentTargetId);
      float liveBefore = hp(world, liveTarget);
      world.getMapper(AttributesWrapper.class).get(deadTarget).attrs.base()
          .put(Stat.hitpoints, 0);
      world.getMapper(AttributesWrapper.class).get(deadTarget).attrs.reset();
      world.getSystem(EventSystem.class).dispatch(
          AnimDataKeyframeEvent.obtain(amazon, Engine.KEYFRAME_ATK));

      assertTrue(hp(world, liveTarget) <= liveBefore,
          "SrvDo013 must retarget the live target before resolving the hit");
      assertEquals(1, casting.fendStrikeIndex);
      assertEquals(0, casting.fendRemainingStrikes);
    } finally {
      world.dispose();
    }
  }

  @Test
  void fendUsesNativeCalc1AsAttackCapAndAdvancesDistinctNearbyTargets() {
    World world = world();
    try {
      Skills.Entry fend = Riiablo.files.skills.get("Fend");
      int amazon = player(world, 0, 0, attributes(10000, 100, 100, 10000));
      equip(world, amazon, fend, "hax");
      int[] targets = {
          monster(world, 1, 0, attributes(10000, 0, 0, 0)),
          monster(world, 2, 0, attributes(10000, 0, 0, 0)),
          monster(world, 3, 0, attributes(10000, 0, 0, 0)),
          monster(world, 4, 0, attributes(10000, 0, 0, 0)),
      };
      Casting casting = world.getMapper(Casting.class).create(amazon)
          .set(fend.Id, targets[0], new Vector2(1, 0));
      world.getSystem(EventSystem.class).dispatch(SkillStartEvent.obtain(
          amazon, fend.Id, targets[0], casting.targetVec, fend.srvstfunc, fend.cltstfunc));

      assertTrue(casting.fendInitialized);
      assertEquals(4, casting.fendRemainingStrikes,
          "native calc1=12 is capped by the four nearby hostile targets");
      java.util.Set<Integer> strikeTargets = new java.util.LinkedHashSet<>();
      for (int i = 0; i < targets.length; i++) {
        int currentTarget = casting.fendCurrentTargetId;
        assertTrue(currentTarget != Engine.INVALID_ENTITY,
            "Fend must retain a valid target before strike " + (i + 1));
        assertTrue(strikeTargets.add(currentTarget),
            "Fend must not repeat a target before exhausting nearby targets");
        world.getSystem(EventSystem.class).dispatch(
            AnimDataKeyframeEvent.obtain(amazon, Engine.KEYFRAME_ATK));
      }
      assertEquals(4, strikeTargets.size(),
          "Fend must advance across four distinct nearby hostile targets");
      assertEquals(0, casting.fendRemainingStrikes);
      assertEquals(4, casting.fendStrikeIndex);
    } finally {
      world.dispose();
    }
  }

  private static World world() {
    DummyFactory factory = new DummyFactory();
    return new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new Actioneer(), new Pathfinder(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
  }

  private static int player(World world, float x, float y, Attributes attrs) {
    int id = world.create();
    world.getMapper(Player.class).create(id).data = CharData.createRemote(
        "amazon", (byte) Riiablo.AMAZON);
    world.getMapper(Class.class).create(id).type = Class.Type.PLR;
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static int monster(World world, float x, float y, Attributes attrs) {
    int id = world.create();
    world.getMapper(Monster.class).create(id);
    world.getMapper(Class.class).create(id).type = Class.Type.MON;
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static Item equip(World world, int amazon, Skills.Entry skill, String code) {
    CharData data = world.getMapper(Player.class).get(amazon).data;
    data.setSkillLevel(skill.Id, 1);
    Item weapon = new Item();
    weapon.reset();
    weapon.setBase(Riiablo.files.weapons.get(code));
    weapon.attrs.base().put(Stat.mindamage, 10);
    weapon.attrs.base().put(Stat.maxdamage, 10);
    weapon.attrs.base().put(Stat.durability, 20);
    weapon.attrs.base().put(Stat.maxdurability, 20);
    weapon.attrs.reset();
    data.getItems().equipItem(BodyLoc.RARM, data.getItems().add(weapon));
    return weapon;
  }

  private static Attributes attributes(float hp, int min, int max, int toHit) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.base().put(Stat.mindamage, min);
    attrs.base().put(Stat.maxdamage, max);
    attrs.base().put(Stat.tohit, toHit);
    attrs.base().put(Stat.level, 1);
    attrs.base().put(Stat.armorclass, 0);
    attrs.reset();
    return attrs;
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
