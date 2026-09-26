package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.combat.StatusEffectApplier;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.state.StateId;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import org.junit.jupiter.api.Test;

/** Regression for native freeze/shatter state ordering at a lethal missile hit. */
class FreezingArrowDeathOrderTest extends RiiabloTest {
  @Test
  void lethalFreezingArrowInstallsFreezeAndShatterBeforeDeathEvent() {
    RecordingFactory factory = new RecordingFactory();
    DeathProbe probe = new DeathProbe();
    StateUpdater states = new StateUpdater();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), probe, states, new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    StatusEffectApplier.INSTANCE.setStateSink(states);
    try {
      int amazon = world.create();
      world.getMapper(Player.class).create(amazon);
      world.getMapper(Position.class).create(amazon).position.set(-1, 0);
      Attributes owner = attributes(20, 100);
      owner.base().put(Stat.mindamage, 10);
      owner.base().put(Stat.maxdamage, 10);
      owner.base().put(Stat.tohit, 100);
      owner.reset();
      world.getMapper(AttributesWrapper.class).create(amazon).attrs = owner;

      int target = world.create();
      MonStats.Entry stats = new MonStats.Entry();
      MonStats2.Entry stats2 = new MonStats2.Entry();
      stats2.deadCol = true;
      Monster monster = world.getMapper(Monster.class).create(target).set(stats, stats2);
      monster.rngState = 1; // first native shatter roll is 90%, i.e. success
      world.getMapper(Position.class).create(target).position.set(0, 0);
      world.getMapper(AttributesWrapper.class).create(target).attrs = attributes(1, 1);
      probe.targetStates = world.getMapper(UnitStates.class).create(target).init(target);

      Skills.Entry skill = Riiablo.files.skills.get("Freezing Arrow");
      Missile projectile = factory.create(Riiablo.files.Missiles.get("freezingarrowexp3"), amazon);
      MissileDamageResolver.initializeSkillArea(projectile, skill, owner, 1);
      world.setDelta(com.riiablo.codec.Animation.FRAME_DURATION);
      world.process();

      assertTrue(probe.deathObserved, "the lethal explosion must dispatch DeathEvent");
      assertTrue(probe.freezeAtDeath, "FREEZE must be visible before DeathEvent");
      assertTrue(probe.shatterAtDeath, "deadCol monster must retain SHATTER before DeathEvent");
    } finally {
      StatusEffectApplier.INSTANCE.setStateSink(null);
      world.dispose();
    }
  }

  private static Attributes attributes(int level, float hp) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.level, level);
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.reset();
    return attrs;
  }

  private static final class DeathProbe extends PassiveSystem {
    boolean deathObserved;
    boolean freezeAtDeath;
    boolean shatterAtDeath;
    UnitStates targetStates;

    @Subscribe
    public void onDeath(DeathEvent event) {
      deathObserved = true;
      // Inspect the state on the event boundary rather than after the
      // death-mode transition/corpse processing.
      freezeAtDeath = targetStates != null && targetStates.stateList != null
          && targetStates.stateList.hasState(StateId.FREEZE);
      shatterAtDeath = targetStates != null && targetStates.stateList != null
          && targetStates.stateList.hasState(StateId.SHATTER);
    }
  }

  private static final class RecordingFactory extends EntityFactory {
    @Override
    public int createMissile(int id, Vector2 angle, Vector2 position, int ownerId) {
      Missiles.Entry row = Riiablo.files.Missiles.get(id);
      int entity = world.create();
      Missile missile = world.getMapper(Missile.class).create(entity)
          .set(row, position, row.Range).setOwner(ownerId);
      world.getMapper(Position.class).create(entity).position.set(position);
      return entity;
    }

    Missile create(Missiles.Entry row, int ownerId) {
      int entity = world.create();
      Missile missile = world.getMapper(Missile.class).create(entity)
          .set(row, new Vector2(), row.Range).setOwner(ownerId);
      world.getMapper(Position.class).create(entity).position.set(0, 0);
      world.getMapper(Velocity.class).create(entity).velocity.set(1, 0).setLength(row.Vel);
      return missile;
    }

    @Override public int createPlayer(com.riiablo.save.CharData data, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMonster(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(com.riiablo.item.Item item, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMissile(int id, Vector2 angle, Vector2 position) {
      return createMissile(id, angle, position, Engine.INVALID_ENTITY);
    }
  }
}
