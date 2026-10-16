package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import java.util.ArrayList;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class PrimePoisonNovaIntegrationTest extends RiiabloTest {
  @Test void realTrapSkillCreatesNativeTwoGroupsInsteadOfOneGenericMissile() {
    MonStats.Entry mon = Riiablo.files.monstats.get("trap-poisoncloud");
    Skills.Entry skill = Riiablo.files.skills.get(mon.Skill1);
    Missiles.Entry row = Riiablo.files.Missiles.get(skill.srvmissilea);
    assertEquals(99, skill.srvdofunc);
    assertEquals("primepoisoncloud", row.Missile);
    assertEquals(3, row.pSrvDoFunc);
    int[] expectedX = {0, 2, 2, 2, 0, -2, -2, -2};
    int[] expectedY = {2, 2, 0, -2, -2, -2, 0, 2};
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      RecordingFactory factory = new RecordingFactory();
      World world = new World(new WorldConfigurationBuilder()
          .with(new EventSystem(), new ServerSkillSystem(true), factory)
          .build().register("factory", factory).register("map", new Map(1, difficulty)));
      try {
        int actor = world.create();
        world.getMapper(Monster.class).create(actor).set(mon,
            Riiablo.files.monstats2.get(mon.MonStatsEx));
        world.getMapper(Position.class).create(actor).position.set(10, 10);
        world.process();
        world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
            actor, skill.Id, Engine.INVALID_ENTITY, new Vector2(20, 10),
            skill.srvdofunc, skill.cltdofunc));
        int level = Math.max(1, mon.Sk1lvl);
        int step = Math.max(1, SkillFormula.evaluate(skill.calc2, skill, level));
        int count = 8 + (step > 1 ? (14 / step + 1) : 0);
        assertEquals(count, factory.ids.size());
        for (int i = 0; i < count; i++) {
          int id = factory.ids.get(i);
          Missile cloud = world.getMapper(Missile.class).get(id);
          assertEquals(actor, cloud.ownerId);
          assertEquals(skill.Id, cloud.skillId);
          assertEquals(level, cloud.damageLevel);
          assertEquals(SkillFormula.evaluate(skill.calc1, skill, level), cloud.poisonCloudLoops);
          assertTrue(cloud.poisonCloudTraveling);
          assertEquals(new Vector2(10, 10), world.getMapper(Position.class).get(id).position);
          int index = i < 8 ? i * 2 : (i - 8) * step + 1;
          int[] offsetsX = {0, 1, 2, 2, 2, 2, 2, 1, 0, -1, -2, -2, -2, -2, -2, -1};
          int[] offsetsY = {2, 2, 2, 1, 0, -1, -2, -2, -2, -2, -2, -1, 0, 1, 2, 2};
          if (i < 8) {
            assertEquals(new Vector2(10 + expectedX[i], 10 + expectedY[i]), cloud.poisonCloudTarget);
          } else {
            assertEquals(new Vector2(10 + offsetsX[index], 10 + offsetsY[index]), cloud.poisonCloudTarget);
          }
          Vector2 velocity = world.getMapper(Velocity.class).get(id).velocity;
          assertEquals(row.Param[i < 8 ? 0 : 1] / 4f, velocity.len(), .0001f);
          assertTrue(velocity.dot(new Vector2(cloud.poisonCloudTarget).sub(10, 10)) > 0);
        }
      } finally {
        world.dispose();
      }
    }
  }

  private static class RecordingFactory extends EntityFactory {
    final ArrayList<Integer> ids = new ArrayList<>();
    @Override public int createMissile(int id, Vector2 direction, Vector2 start, int owner) {
      int entity = world.create();
      Missiles.Entry row = Riiablo.files.Missiles.get(id);
      world.getMapper(Missile.class).create(entity).set(row, start, row.Range).setOwner(owner);
      world.getMapper(Position.class).create(entity).position.set(start);
      world.getMapper(Velocity.class).create(entity).velocity.set(direction).scl(row.Vel);
      ids.add(entity);
      return entity;
    }
    @Override public int createMissile(int id, Vector2 d, Vector2 p) { return createMissile(id, d, p, -1); }
    @Override public int createPlayer(CharData d, Vector2 p) { return -1; }
    @Override public int createDynamicObject(int a, int id, float x, float y) { return -1; }
    @Override public int createStaticObject(int a, int id, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return -1; }
    @Override public int createMonster(int id, float x, float y) { return -1; }
    @Override public int createWarp(int id, float x, float y) { return -1; }
    @Override public int createItem(Item i, float x, float y) { return -1; }
  }
}
