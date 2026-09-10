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
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Headless contract for native SrvDo124 Armageddon/Hurricane states. */
class DruidStormAuraIntegrationTest extends RiiabloTest {
  @Test
  void nativeRowsUseSrvDo124AndAuraStates() {
    Skills.Entry hurricane = Riiablo.files.skills.get(SkillId.HURRICANE);
    Skills.Entry armageddon = Riiablo.files.skills.get(SkillId.ARMAGEDDON);
    assertNotNull(hurricane);
    assertNotNull(armageddon);
    assertEquals(124, hurricane.srvdofunc);
    assertEquals(124, armageddon.srvdofunc);
    assertTrue(hurricane.aurastate != null && !hurricane.aurastate.isEmpty());
    assertTrue(armageddon.aurastate != null && !armageddon.aurastate.isEmpty());
  }

  @Test
  void castInstallsRefreshableNativeState() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int druid = createDruid(world, 6);
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.HURRICANE);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          druid, SkillId.HURRICANE, Engine.INVALID_ENTITY, new Vector2(6, 0),
          skill.srvdofunc, skill.cltdofunc));
      UnitStates states = world.getMapper(UnitStates.class).get(druid);
      assertNotNull(states.stateList.getState(StateId.HURRICANE));
      assertEquals(SkillId.HURRICANE, states.stateList.getState(StateId.HURRICANE).skillId);
      assertTrue(states.stateList.getState(StateId.HURRICANE).duration > 0);
      assertTrue(states.stateList.getState(StateId.HURRICANE).periodicDelayFrames > 0);

      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          druid, SkillId.HURRICANE, Engine.INVALID_ENTITY, new Vector2(6, 0),
          skill.srvdofunc, skill.cltdofunc));
      assertNotNull(states.stateList.getState(StateId.HURRICANE));
    } finally {
      world.dispose();
    }
  }

  private static int createDruid(World world, int level) {
    int id = world.create();
    CharData data = CharData.createRemote("storm", (byte) Riiablo.DRUID);
    data.setSkillLevel(SkillId.HURRICANE, level);
    world.getMapper(Player.class).create(id).data = data;
    world.getMapper(UnitStates.class).create(id).init(id);
    world.getMapper(Position.class).create(id).position.set(0, 0);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes();
    return id;
  }

  private static Attributes attributes() {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.level, 6);
    attrs.base().put(Stat.hitpoints, 1000);
    attrs.base().put(Stat.maxhp, 1000);
    attrs.base().put(Stat.mana, 1000);
    attrs.base().put(Stat.maxmana, 1000);
    attrs.reset();
    return attrs;
  }

  private static final class RecordingFactory extends EntityFactory {
    @Override public int createMissile(int id, Vector2 direction, Vector2 position) {
      return Engine.INVALID_ENTITY;
    }
    @Override public int createPlayer(CharData data, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createMonster(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(Item item, float x, float y) { return Engine.INVALID_ENTITY; }
  }
}
