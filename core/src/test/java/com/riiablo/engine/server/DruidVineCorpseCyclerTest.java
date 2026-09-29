package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.SkillStartEvent;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Direct ECS coverage for D2MOO SrvSt63_Corpse_VineCycler. */
class DruidVineCorpseCyclerTest extends RiiabloTest {
  @Test
  void startSelectsCorpseAndCreatesOwnerMissileAtCorpseOrigin() {
    RecordingFactory factory = new RecordingFactory();
    Actioneer actioneer = new Actioneer();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), actioneer, new Pathfinder(), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      // Cycle of Life/Vines use the native VineCycler monster skill.  The
      // parent summon rows (231/241) only install this skill through AI.
      Skills.Entry skill = Riiablo.files.skills.get("VineCycler");
      assertNotNull(skill);
      assertEquals(63, skill.srvstfunc);
      assertNotNull(skill.srvmissilea);

      int owner = world.create();
      world.getMapper(Position.class).create(owner).position.set(1, 2);
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(20, 100);

      int vine = world.create();
      Monster vineMonster = world.getMapper(Monster.class).create(vine);
      vineMonster.monstats = new MonStats.Entry();
      vineMonster.monstats.Skill1 = skill.skill;
      vineMonster.monstats.Sk1lvl = 7;
      world.getMapper(Class.class).create(vine).type = Class.Type.MON;
      world.getMapper(Position.class).create(vine).position.set(3, 4);
      world.getMapper(AttributesWrapper.class).create(vine).attrs = attributes(1, 20);
      world.getMapper(SummonedPet.class).create(vine)
          .set(owner, "vine", skill.Id, 7, false, 0);
      world.getMapper(Casting.class).create(vine).set(skill.Id, -1, Vector2.Zero);

      int corpseId = world.create();
      Monster corpseMonster = world.getMapper(Monster.class).create(corpseId);
      corpseMonster.monstats = new MonStats.Entry();
      corpseMonster.monstats2 = new MonStats2.Entry();
      corpseMonster.monstats2.corpseSel = true;
      world.getMapper(Class.class).create(corpseId).type = Class.Type.MON;
      world.getMapper(Position.class).create(corpseId).position.set(12, 13);
      world.getMapper(AttributesWrapper.class).create(corpseId).attrs = attributes(1, 0);
      world.getMapper(UnitStates.class).create(corpseId).init(corpseId);
      world.getMapper(Corpse.class).create(corpseId).reset(Corpse.DEFAULT_DURATION, true);

      actioneer.onSkillStart(SkillStartEvent.obtain(
          vine, skill.Id, corpseId, new Vector2(12, 13), skill.srvstfunc, skill.cltstfunc));

      assertTrue(world.getMapper(UnitStates.class).get(corpseId)
          .stateList.hasState(StateId.CORPSE_NOSELECT));
      assertTrue(world.getMapper(Corpse.class).get(corpseId).usable,
          "SrvSt63 only toggles CORPSE_NOSELECT; native corpse lifecycle remains active");
      IntBag missiles = world.getAspectSubscriptionManager()
          .get(Aspect.all(Missile.class)).getEntities();
      assertEquals(1, missiles.size());
      Missile projectile = world.getMapper(Missile.class).get(missiles.get(0));
      assertEquals(owner, projectile.ownerId);
      assertEquals(skill.Id, projectile.skillId);
      assertEquals(7, projectile.damageLevel);
      assertEquals(corpseId, projectile.targetId);
      assertEquals(12f, world.getMapper(Position.class).get(missiles.get(0)).position.x);
      assertEquals(13f, world.getMapper(Position.class).get(missiles.get(0)).position.y);

      // A second start cannot reserve the same corpse or create a duplicate.
      actioneer.onSkillStart(SkillStartEvent.obtain(
          vine, skill.Id, corpseId, new Vector2(12, 13), skill.srvstfunc, skill.cltstfunc));
      assertEquals(1, world.getAspectSubscriptionManager()
          .get(Aspect.all(Missile.class)).getEntities().size());
    } finally {
      world.dispose();
    }
  }

  @Test
  void recyclerDelayUsesNativeLifetimeAndDoesNotConsumeCorpse() {
    Skills.Entry skill = Riiablo.files.skills.get("VineCycler");
    assertNotNull(skill);
    Missiles.Entry delay = Riiablo.files.Missiles.get(skill.srvmissilea);
    assertNotNull(delay);
    assertEquals("vine recycler delay", delay.Missile);
    assertEquals(33, delay.pSrvDoFunc);
    assertEquals(0, delay.pSrvHitFunc);
    assertEquals(0, delay.pSrvDmgFunc);
    assertEquals(45, delay.Param[0]);
    assertEquals(47, delay.Range);
    assertEquals(0, delay.Vel);

    Missile projectile = new Missile().set(delay, Vector2.Zero, delay.Range);
    assertEquals(delay.Range, projectile.nativeLifetimeFrames,
        "zero-velocity SrvDo33 delay must expire on its native Range frame");
    assertTrue(MissileCollisionSystem.hasNativeCollision(projectile),
        "SrvDo33 remains a server missile mode even without hit/damage callbacks");
    // D2MOO SrvSt63 never removes the corpse or clears CORPSE_NOSELECT;
    // corpse cleanup remains owned by the normal corpse lifecycle.
  }

  private static Attributes attributes(int level, float hp) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.level, level);
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, Math.max(1, hp));
    attrs.reset();
    return attrs;
  }

  private static final class RecordingFactory extends EntityFactory {
    @Override public int createPlayer(CharData data, Vector2 position) { return -1; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return -1; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return -1; }
    @Override public int createMonster(int id, float x, float y) { return -1; }
    @Override public int createWarp(int index, float x, float y) { return -1; }
    @Override public int createItem(Item item, float x, float y) { return -1; }
    @Override public int createMissile(int id, Vector2 angle, Vector2 position) { return -1; }

    @Override public int createMissile(Missiles.Entry missile, Vector2 angle, Vector2 position,
        int ownerId, int skillLevel) {
      int id = world.create();
      world.getMapper(Missile.class).create(id).set(missile, position, missile.Range)
          .setOwner(ownerId);
      world.getMapper(Position.class).create(id).position.set(position);
      return id;
    }
  }
}
