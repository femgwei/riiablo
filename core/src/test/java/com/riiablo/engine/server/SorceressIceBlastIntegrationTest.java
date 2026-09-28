package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.combat.StatusEffectApplier;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** ECS contract for D2MOO MISSMODE_SrvDmg04_IceBlast. */
class SorceressIceBlastIntegrationTest extends RiiabloTest {
  @Test
  void iceBlastCreatesFreezeWithoutASecondColdStateAndHitsOnce() {
    RecordingFactory factory = new RecordingFactory();
    StateUpdater states = new StateUpdater();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), states, new MissileCollisionSystem(), factory).build()
        .register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    StatusEffectApplier.INSTANCE.setStateSink(states);
    try {
      int caster = caster(world);
      int target = target(world, 0);
      Missile projectile = missile(world, caster, target);
      Attributes owner = world.getMapper(AttributesWrapper.class).get(caster).attrs;
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.ICE_BLAST);
      assertTrue(MissileDamageResolver.initializeSkill(projectile, skill, owner, 1,
          name -> "Glacial Spike".equalsIgnoreCase(name) ? 1 : 0));
      projectile.skillId = SkillId.ICE_BLAST;

      world.setDelta(com.riiablo.codec.Animation.FRAME_DURATION);
      world.process();

      UnitStates targetStates = world.getMapper(UnitStates.class).get(target);
      assertTrue(targetStates.stateList.hasState(StateId.FREEZE));
      assertFalse(targetStates.stateList.hasState(StateId.COLD),
          "SrvDmg04 moves ColdLen to FrzLen instead of applying both states");
      assertEquals(82, targetStates.stateList.getStateDuration(StateId.FREEZE));
      float afterHit = life(world, target);
      assertTrue(afterHit < 100f && afterHit > 0f);

      // The projectile is non-piercing and its hit set must prevent a second
      // damage/state application if the ECS keeps the entity for another tick.
      world.process();
      assertEquals(afterHit, life(world, target), 0.0001f);
    } finally {
      StatusEffectApplier.INSTANCE.setStateSink(null);
      world.dispose();
    }
  }

  @Test
  void iceBlastColdImmunityRejectsBothDamageAndFreeze() {
    RecordingFactory factory = new RecordingFactory();
    StateUpdater states = new StateUpdater();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), states, new MissileCollisionSystem(), factory).build()
        .register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
    StatusEffectApplier.INSTANCE.setStateSink(states);
    try {
      int caster = caster(world);
      int target = target(world, 100);
      Missile projectile = missile(world, caster, target);
      Attributes owner = world.getMapper(AttributesWrapper.class).get(caster).attrs;
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.ICE_BLAST);
      assertTrue(MissileDamageResolver.initializeSkill(projectile, skill, owner, 1,
          name -> 0));
      projectile.skillId = SkillId.ICE_BLAST;

      world.setDelta(com.riiablo.codec.Animation.FRAME_DURATION);
      world.process();

      UnitStates targetStates = world.getMapper(UnitStates.class).get(target);
      assertFalse(targetStates.stateList.hasState(StateId.FREEZE));
      assertFalse(targetStates.stateList.hasState(StateId.COLD));
      assertEquals(100f, life(world, target), 0.0001f);
    } finally {
      StatusEffectApplier.INSTANCE.setStateSink(null);
      world.dispose();
    }
  }

  private static int caster(World world) {
    int id = world.create();
    world.getMapper(Player.class).create(id);
    world.getMapper(Position.class).create(id).position.set(0, 0);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(20, 0, 0);
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static int target(World world, int coldResistance) {
    int id = world.create();
    world.getMapper(Monster.class).create(id).set(new MonStats.Entry(), new MonStats2.Entry());
    world.getMapper(Position.class).create(id).position.set(0, 0);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(1, 100, coldResistance);
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static Missile missile(World world, int caster, int target) {
    Missiles.Entry row = Riiablo.files.Missiles.get("iceblast");
    int id = world.create();
    Missile missile = world.getMapper(Missile.class).create(id)
        .set(row, new Vector2(), row.Range).setOwner(caster);
    world.getMapper(Position.class).create(id).position.set(0, 0);
    world.getMapper(Velocity.class).create(id).velocity.setZero();
    return missile;
  }

  private static Attributes attributes(int level, float hp, int coldResistance) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.level, level);
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.base().put(Stat.coldresist, coldResistance);
    attrs.reset();
    return attrs;
  }

  private static float life(World world, int id) {
    return world.getMapper(AttributesWrapper.class).get(id).attrs.get(Stat.hitpoints).asFixed();
  }

  private static final class RecordingFactory extends EntityFactory {
    @Override public int createPlayer(CharData data, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMonster(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(Item item, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMissile(int id, Vector2 direction, Vector2 position) { return Engine.INVALID_ENTITY; }
  }
}
