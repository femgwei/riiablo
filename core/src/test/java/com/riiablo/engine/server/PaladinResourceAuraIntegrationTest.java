package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.engine.server.skill.AuraManager;
import com.riiablo.engine.server.skill.CorpseConsumption;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.Engine;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import com.riiablo.map.Map;
import com.riiablo.engine.server.party.PartyManager;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Native 1.10f contracts for Cleansing, Meditation and Redemption. */
class PaladinResourceAuraIntegrationTest extends RiiabloTest {
  @Test
  void cleansingAndMeditationExposeNativeRowsAndFormulas() {
    AuraManager manager = new AuraManager();
    AuraManager.AuraDefinition cleansing = manager.getAuraDefinition(SkillId.CLEANSING);
    AuraManager.AuraDefinition meditation = manager.getAuraDefinition(SkillId.MEDITATION);
    assertNotNull(cleansing);
    assertNotNull(cleansing.nativeSkill);
    assertEquals(65, cleansing.nativeSkill.srvdofunc);
    assertEquals("item_poisonlengthresist", cleansing.nativeSkill.aurastat[0]);
    assertEquals("100-dm34", cleansing.nativeSkill.aurastatcalc[0]);
    assertEquals(100 - SkillFormula.evaluate("dm34", cleansing.nativeSkill, 1),
        SkillFormula.evaluate(cleansing.nativeSkill.aurastatcalc[0], cleansing.nativeSkill, 1));

    assertNotNull(meditation);
    assertNotNull(meditation.nativeSkill);
    assertEquals(65, meditation.nativeSkill.srvdofunc);
    assertEquals("manarecoverybonus", meditation.nativeSkill.aurastat[0]);
    assertEquals("ln34", meditation.nativeSkill.aurastatcalc[0]);
    assertEquals("skill('Prayer'.edns)", meditation.nativeSkill.aurastatcalc[1]);
  }

  @Test
  void cleansingShortensPoisonAndCurableCursesButNotAuraStates() {
    try (TestWorld test = new TestWorld()) {
      int entity = test.world.create();
      test.world.getMapper(com.riiablo.engine.server.component.UnitStates.class)
          .create(entity).init(entity);
      StateList states = test.world.getMapper(com.riiablo.engine.server.component.UnitStates.class)
          .get(entity).stateList;
      UnitState poison = states.addState(StateId.POISON, 100, 1, 7);
      UnitState curse = states.addState(StateId.DECREPIFY, 100, 1, 7);
      UnitState aura = states.addState(StateId.CLEANSING, 100, 1, 7);
      test.stateUpdater.applyCleansingReduction(entity, 50, 2, SkillId.CLEANSING);
      assertEquals(50, poison.duration);
      assertEquals(50, curse.duration);
      assertEquals(100, aura.duration);
    }
  }

  @Test
  void redemptionReservesOneCorpseAndBlocksReuse() {
    Corpse corpse = new Corpse().reset(Float.POSITIVE_INFINITY, true);
    Monster monster = new Monster();
    MonStats2.Entry stats = new MonStats2.Entry();
    stats.corpseSel = true;
    monster.monstats2 = stats;
    Attributes dead = Attributes.obtainStandard();
    dead.base().clear();
    dead.base().put(Stat.hitpoints, 0);
    dead.base().put(Stat.maxhp, 100);
    dead.reset();
    StateList states = new StateList(99);

    assertTrue(CorpseConsumption.selectable(corpse, monster, dead, states));
    assertTrue(CorpseConsumption.tryReserve(corpse, monster, dead, states,
        true, 4, 10, SkillId.REDEMPTION));
    assertFalse(corpse.usable);
    assertTrue(states.hasState(StateId.CORPSE_NOSELECT));
    assertTrue(states.hasState(StateId.CORPSE_NODRAW));
    assertFalse(CorpseConsumption.selectable(corpse, monster, dead, states));
    assertFalse(CorpseConsumption.tryReserve(corpse, monster, dead, states,
        true, 4, 10, SkillId.REDEMPTION));
  }

  @Test
  void redemptionUsesNativeChanceAndRecoveryColumns() {
    AuraManager.AuraDefinition redemption = new AuraManager().getAuraDefinition(SkillId.REDEMPTION);
    assertNotNull(redemption);
    assertNotNull(redemption.nativeSkill);
    assertEquals(82, redemption.nativeSkill.srvdofunc);
    assertEquals("", redemption.nativeSkill.auratargetstate);
    assertEquals("dm34", redemption.nativeSkill.calc1);
    assertEquals("ln56", redemption.nativeSkill.calc2);
    assertEquals("ln56", redemption.nativeSkill.calc3);
    assertTrue(SkillFormula.evaluate(redemption.nativeSkill.calc1, redemption.nativeSkill, 20) >
        SkillFormula.evaluate(redemption.nativeSkill.calc1, redemption.nativeSkill, 1));
  }

  private static final class TestWorld implements AutoCloseable {
    final com.artemis.World world;
    final StateUpdater stateUpdater;

    TestWorld() {
      EntityFactory factory = new NoopFactory();
      EventSystem events = new EventSystem();
      com.artemis.WorldConfiguration configuration = new com.artemis.WorldConfigurationBuilder()
          .with(events, new StateUpdater(), factory).build()
          .register("factory", factory)
          .register("map", new Map(0, 0))
          .register("partyManager", new PartyManager());
      world = new com.artemis.World(configuration);
      stateUpdater = world.getSystem(StateUpdater.class);
      world.setDelta(1f / 25f);
      world.process();
    }

    @Override public void close() { world.dispose(); }
  }

  private static final class NoopFactory extends EntityFactory {
    @Override public int createPlayer(com.riiablo.save.CharData data, Vector2 position) {
      return Engine.INVALID_ENTITY;
    }
    @Override public int createDynamicObject(int act, int id, float x, float y) {
      return Engine.INVALID_ENTITY;
    }
    @Override public int createStaticObject(int act, int id, float x, float y) {
      return Engine.INVALID_ENTITY;
    }
    @Override public int createStaticObjectByClassId(int id, float x, float y) {
      return Engine.INVALID_ENTITY;
    }
    @Override public int createMonster(int id, float x, float y) {
      return Engine.INVALID_ENTITY;
    }
    @Override public int createWarp(int index, float x, float y) {
      return Engine.INVALID_ENTITY;
    }
    @Override public int createItem(Item item, float x, float y) {
      return Engine.INVALID_ENTITY;
    }
    @Override public int createMissile(int id, Vector2 direction, Vector2 position) {
      return Engine.INVALID_ENTITY;
    }
  }
}
