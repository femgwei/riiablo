package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.Actioneer;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.Pathfinder;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.event.SkillCastEvent;
import com.riiablo.engine.server.event.SkillStartEvent;
import com.riiablo.map.Map;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import org.junit.jupiter.api.Test;

/** Verifies native monster skills enter the shared cast/start presentation lifecycle. */
class MonsterSkillStartIntegrationTest extends RiiabloTest {
  @Test
  void fallenShamanResurrectDispatchesCastAndPresentationStartOnce() {
    MonStats.Entry shaman = Riiablo.files.monstats.get("fallenshaman1");
    assertNotNull(shaman);
    assertEquals("Resurrect", shaman.Skill1);
    Skills.Entry resurrect = Riiablo.files.skills.get(shaman.Skill1);
    assertNotNull(resurrect);
    assertEquals("fallenshaman_resurrect_cast", resurrect.stsound);
    assertEquals("healing", resurrect.castoverlay);

    Probe probe = new Probe();
    NoopFactory factory = new NoopFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), probe, new Actioneer(), new Pathfinder(), new CofManager(), factory)
        .build()
        .register("factory", factory)
        .register("map", new Map(0, 0)));
    try {
      int casterId = world.create();
      world.getMapper(Monster.class).create(casterId).set(shaman, null);
      world.getMapper(Position.class).create(casterId).position.set(10, 20);
      world.getMapper(Angle.class).create(casterId);

      int corpseId = world.create();
      Vector2 corpsePosition = world.getMapper(Position.class).create(corpseId).position.set(14, 21);

      ProbeAI ai = new ProbeAI(casterId);
      world.getInjector().inject(ai);
      ai.bind(shaman);

      assertTrue(ai.castSlot(0, corpseId, corpsePosition, 5));
      assertEquals(1, probe.castEvents);
      assertEquals(1, probe.startEvents);
      assertEquals(resurrect.Id, probe.skillId);
      assertEquals(corpseId, probe.targetId);
      assertEquals(resurrect.srvstfunc, probe.srvstfunc);
      assertEquals(resurrect.cltstfunc, probe.cltstfunc);
      assertEquals(corpsePosition, probe.target);
      assertTrue(world.getMapper(Casting.class).has(casterId));
      assertTrue(world.getMapper(Sequence.class).has(casterId));

      // Events and Casting must own their coordinates. Native AI frequently
      // reuses temporary vectors on the next decision tick.
      corpsePosition.set(99, 99);
      assertEquals(new Vector2(14, 21), probe.target);
      assertEquals(new Vector2(14, 21),
          world.getMapper(Casting.class).get(casterId).targetVec);

      System.out.println("[MONSTER_SKILL_START_TEST] monster=" + shaman.Id
          + " skill=" + resurrect.skill + " castEvents=" + probe.castEvents
          + " startEvents=" + probe.startEvents + " stsound=" + resurrect.stsound
          + " castoverlay=" + resurrect.castoverlay + " status=PASS");
    } finally {
      world.dispose();
    }
  }

  private static final class ProbeAI extends AI {
    ProbeAI(int entityId) {
      super(entityId);
    }

    void bind(MonStats.Entry monstats) {
      monster = mMonster.get(entityId);
      monster.monstats = monstats;
    }

    boolean castSlot(int slot, int targetId, Vector2 target, int mode) {
      return useMonsterSkill(slot, targetId, target, mode);
    }

    @Override
    protected void stopMovement() {
      // Movement is unrelated to this event-lifecycle test.
    }
  }

  private static final class Probe extends BaseSystem {
    int castEvents;
    int startEvents;
    int skillId;
    int targetId;
    int srvstfunc;
    int cltstfunc;
    Vector2 target;

    @Subscribe
    public void onCast(SkillCastEvent event) {
      castEvents++;
    }

    @Subscribe
    public void onStart(SkillStartEvent event) {
      startEvents++;
      skillId = event.skillId;
      targetId = event.targetId;
      srvstfunc = event.srvstfunc;
      cltstfunc = event.cltstfunc;
      target = event.targetVec.cpy();
    }

    @Override
    protected void processSystem() {}
  }

  private static final class NoopFactory extends EntityFactory {
    @Override public int createPlayer(CharData data, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMonster(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(Item item, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMissile(int id, Vector2 angle, Vector2 position) { return Engine.INVALID_ENTITY; }
  }
}
