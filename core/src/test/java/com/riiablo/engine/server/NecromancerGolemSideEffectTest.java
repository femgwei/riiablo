package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.skill.NecromancerSkills;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Authoritative hit callbacks and periodic side effects for native Golems. */
class NecromancerGolemSideEffectTest extends RiiabloTest {
  @Test
  void clayGolemSlowsTheMeleeAttackerForSevenHundredFiftyFrames() {
    World world = world();
    try {
      int owner = player(world, 0, 0);
      int attacker = monster(world, 1, 0, 100, 100);
      int clay = monster(world, 0, 0, 100, 100);
      world.getMapper(SummonedPet.class).create(clay)
          .set(owner, "golem", SkillId.CLAY_GOLEM, 5, false, 0);
      world.getMapper(AttributesWrapper.class).get(clay).attrs.aggregate()
          .put(Stat.item_slow, 80);

      world.getSystem(EventSystem.class).dispatch(
          DamageEvent.obtainMelee(attacker, clay, 10, 10));

      UnitState slow = world.getMapper(UnitStates.class).get(attacker).stateList
          .getState(StateId.SLOWED);
      assertNotNull(slow);
      assertEquals(750, slow.duration);
      assertEquals(-80, slow.getStatContributionValue(Stat.velocitypercent));
      assertEquals(-80, slow.getStatContributionValue(Stat.attackrate));
      assertEquals(-80, slow.getStatContributionValue(Stat.other_animrate));
    } finally {
      world.dispose();
    }
  }

  @Test
  void bloodGolemMeleeDamageHealsBothPetAndOwnerWithNativeCurve() {
    World world = world();
    try {
      int owner = player(world, 0, 0);
      Attributes ownerAttrs = attrs(world, owner);
      ownerAttrs.aggregate().put(Stat.hitpoints, 20f);
      int target = monster(world, 1, 0, 100, 100);
      world.getMapper(Monster.class).get(target).monstats = drainableMonster();
      int blood = monster(world, 0, 0, 50, 100);
      world.getMapper(SummonedPet.class).create(blood)
          .set(owner, "golem", SkillId.BLOOD_GOLEM, 5, false, 0);
      int percent = NecromancerSkills.nativeDiminishingPercent(
          Riiablo.files.skills.get(SkillId.BLOOD_GOLEM), 5, 0, 1);
      assertEquals(112, percent);

      world.getSystem(EventSystem.class).dispatch(
          DamageEvent.obtainMelee(blood, target, 100, 100));

      assertEquals(100, attrs(world, blood).get(Stat.hitpoints).asInt());
      assertTrue(ownerAttrs.get(Stat.hitpoints).asFixed() > 20f,
          "overflow and configured owner share must restore the owner");
    } finally {
      world.dispose();
    }
  }

  @Test
  void bloodGolemDamageCopiesOwnerLifeEvenWhen110fShareIsZero() {
    World world = world();
    try {
      int owner = player(world, 0, 0);
      attrs(world, owner).aggregate().put(Stat.hitpoints, 60f);
      int attacker = monster(world, 1, 0, 100, 100);
      int blood = monster(world, 0, 0, 20, 100);
      world.getMapper(SummonedPet.class).create(blood)
          .set(owner, "golem", SkillId.BLOOD_GOLEM, 5, false, 0);
      DamageEvent hit = DamageEvent.obtainMelee(attacker, blood, 10, 10);

      world.getSystem(EventSystem.class).dispatch(hit);

      assertEquals(60f, attrs(world, blood).get(Stat.hitpoints).asFixed());
      assertEquals(10f, hit.damage, "1.10f Param5=0 must not absorb incoming damage");
    } finally {
      world.dispose();
    }
  }

  @Test
  void ironThornsReflectsAndFireGolemHolyFirePulses() {
    World world = world();
    try {
      int owner = player(world, 0, 0);
      int attacker = monster(world, 1, 0, 100, 100);
      int iron = monster(world, 0, 0, 100, 100);
      world.getMapper(SummonedPet.class).create(iron)
          .set(owner, "golem", SkillId.IRON_GOLEM, 5, false, 0);
      attrs(world, iron).aggregate().put(Stat.thorns_percent, 200);
      world.getSystem(EventSystem.class).dispatch(
          DamageEvent.obtainMelee(attacker, iron, 10, 10));
      assertEquals(80, attrs(world, attacker).get(Stat.hitpoints).asInt());

      int fire = monster(world, 0, 0, 100, 100);
      world.getMapper(SummonedPet.class).create(fire)
          .set(owner, "golem", SkillId.FIRE_GOLEM, 5, false, 0);
      UnitState aura = world.getMapper(UnitStates.class).get(fire).stateList
          .addStateLayer(StateId.HOLYFIRE, 0, 5, fire, SkillId.HOLY_FIRE);
      aura.periodicDelayFrames = 25;
      aura.periodicCountdownFrames = 0;
      int pulseTarget = monster(world, 2, 0, 100, 100);
      world.setDelta(1f / 25f);
      world.process();
      assertTrue(attrs(world, pulseTarget).get(Stat.hitpoints).asFixed() < 100f);
      assertEquals(25, aura.periodicCountdownFrames);
      int pulses = aura.runtimeValue;
      for (int i = 0; i < 24; i++) world.process();
      assertEquals(pulses, aura.runtimeValue);
      world.process();
      assertEquals(pulses + 1, aura.runtimeValue,
          "Holy Fire must pulse again exactly 25 native frames later");
    } finally {
      world.dispose();
    }
  }

  private static com.riiablo.codec.excel.MonStats.Entry drainableMonster() {
    for (com.riiablo.codec.excel.MonStats.Entry row : Riiablo.files.monstats) {
      if (row != null && row.Drain != null && row.Drain.length > 0 && row.Drain[0] == 100) {
        return row;
      }
    }
    throw new AssertionError("1.10f contains no normal monster with 100 drain effectiveness");
  }

  private static World world() {
    NoopFactory factory = new NoopFactory();
    return new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new StateUpdater(), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
  }

  private static int player(World world, float x, float y) {
    int id = world.create();
    world.getMapper(Player.class).create(id).data =
        CharData.createRemote("necromancer", (byte) Riiablo.NECROMANCER);
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(100, 100);
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static int monster(World world, float x, float y, float hp, float maxHp) {
    int id = world.create();
    world.getMapper(Monster.class).create(id).rank = 0;
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(hp, maxHp);
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static Attributes attrs(World world, int id) {
    return world.getMapper(AttributesWrapper.class).get(id).attrs;
  }

  private static Attributes attributes(float hp, float maxHp) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.level, 10);
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, maxHp);
    attrs.base().put(Stat.damageresist, 0);
    attrs.reset();
    return attrs;
  }

  private static final class NoopFactory extends EntityFactory {
    @Override public int createPlayer(CharData data, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMonster(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(com.riiablo.item.Item item, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMissile(int id, Vector2 angle, Vector2 position) { return Engine.INVALID_ENTITY; }
  }
}
