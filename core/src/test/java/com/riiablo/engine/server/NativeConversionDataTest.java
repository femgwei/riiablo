package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.excel.Skills;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.skill.PaladinSkills;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Data-contract checks for the 1.10f Conversion row and native roll domain. */
public class NativeConversionDataTest extends RiiabloTest {
  @Test
  void rowRetainsNativeStartDoStateAndFormulas() {
    Skills.Entry row = Riiablo.files.skills.get(SkillId.CONVERSION);
    assertEquals("Conversion", row.skill);
    assertEquals(32, row.srvstfunc);
    assertEquals(79, row.srvdofunc);
    assertEquals("conversion", row.auratargetstate);
    assertTrue(PaladinSkills.getConversionChance(row, 1, name -> 0) > 0);
    assertTrue(PaladinSkills.getConversionDuration(row, 1) > 0);
  }

  @Test
  void conversionUsesCalcAndMinimumOneFrameDuration() {
    Skills.Entry row = new Skills.Entry();
    row.calc1 = "4+lvl*4";
    row.auralencalc = "25+lvl*5";
    assertEquals(8, PaladinSkills.getConversionChance(row, 1, name -> 0));
    assertEquals(24, PaladinSkills.getConversionChance(row, 5, name -> 0));
    assertEquals(50, PaladinSkills.getConversionDuration(row, 5));
  }

  @Test
  void conversionChanceIsClampedToNativePercentRoll() {
    Skills.Entry row = new Skills.Entry();
    row.calc1 = "250";
    row.auralencalc = "0";
    assertEquals(100, PaladinSkills.getConversionChance(row, 1, name -> 0));
    assertEquals(1, PaladinSkills.getConversionDuration(row, 1));
  }

  @Test
  void expiryRestoresSavedLevelMaximumLifeAndCurrentLifeRatio() {
    NoopFactory factory = new NoopFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new StateUpdater(), factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
    try {
      int entity = world.create();
      Monster monster = world.getMapper(Monster.class).create(entity);
      monster.converted = true;
      monster.conversionOwnerId = 7;
      Attributes attrs = Attributes.obtainStandard();
      attrs.base().put(Stat.level, 10);
      attrs.base().put(Stat.maxhp, 100f);
      attrs.base().put(Stat.hitpoints, 50f);
      attrs.reset();
      world.getMapper(AttributesWrapper.class).create(entity).attrs = attrs;
      UnitStates states = world.getMapper(UnitStates.class).create(entity).init(entity);
      states.stateList.addState(StateId.CONVERSION, 1, 3, 7);
      UnitState save = states.stateList.addPermanentState(StateId.CONVERSION_SAVE);
      assertNotNull(save);
      save.conversionOriginalLevel = 20;
      save.conversionOriginalMaxHpEncoded = 200 * 256;

      world.process();

      assertFalse(monster.converted);
      assertEquals(-1, monster.conversionOwnerId);
      assertFalse(states.stateList.hasState(StateId.CONVERSION));
      assertFalse(states.stateList.hasState(StateId.CONVERSION_SAVE));
      assertEquals(20, attrs.get(Stat.level).asInt());
      assertEquals(200f, attrs.get(Stat.maxhp).asFixed(), 0.01f);
      assertEquals(100f, attrs.get(Stat.hitpoints).asFixed(), 0.01f);
    } finally {
      world.dispose();
    }
  }

  private static final class NoopFactory extends EntityFactory {
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
