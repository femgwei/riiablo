package com.riiablo.engine.server;

import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Synchronous callback tests: no world.process() can hide a deferred restore. */
class NativeConversionLifecycleTest extends RiiabloTest {
  @Test
  void everyRemovalRestoresBeforeReturningRegardlessOfSaveInsertionOrder() {
    for (boolean saveFirst : new boolean[] {true, false}) {
      for (int path = 0; path < 9; path++) {
        Fixture f = new Fixture(saveFirst);
        f.states.addState(StateId.COLD, 100, 1, 9);
        switch (path) {
          case 0: f.states.removeState(StateId.CONVERSION); break;
          case 1: f.states.removeStateLayer(StateId.CONVERSION, 7, -1); break;
          case 2: f.states.removeStatesFromSource(7); break;
          case 3: f.states.removeNegativeEffects(null); break;
          case 4: f.states.clearAll(); break;
          case 5: f.states.clearRemovable(null); break;
          case 6: f.states.retainForDeath(null, StateList.DeathUnitType.MONSTER); break;
          case 7: f.conversion.duration = 1; f.states.update(); break;
          case 8: f.conversion.expired = true; f.states.update(); break;
          default: throw new AssertionError(path);
        }
        f.assertRestored();
        assertEquals(0, f.states.nativeConversionOrder());
        assertFalse(f.states.removeState(StateId.CONVERSION));
        f.states.clearAll();
        f.assertRestored(); // no second restore after pooling or duplicate clear
      }
    }
  }

  @Test
  void callbackReadsCurrentAttributesWrapperAndDoesNotDependOnGlobalWorld() {
    Fixture first = new Fixture(false);
    Fixture second = new Fixture(true);
    first.wrapper.attrs = attributes(10, 25f, 100f);
    first.states.clearAll();
    assertEquals(50f, first.wrapper.attrs.get(Stat.hitpoints).asFixed(), 0.01f);
    assertTrue(second.monster.converted);
    assertEquals(50f, second.wrapper.attrs.get(Stat.hitpoints).asFixed(), 0.01f);
    second.states.clearAll();
    second.assertRestored();
  }

  @Test
  void deathCleanupDoesNotRestorePositiveLifeOrResurrectAllegiance() {
    for (boolean clearAllegianceFirst : new boolean[] {true, false}) {
      Fixture f = new Fixture(false);
      f.wrapper.attrs.get(Stat.hitpoints).set(0f);
      if (clearAllegianceFirst) f.monster.converted = false;
      f.states.retainForDeath(null, StateList.DeathUnitType.MONSTER);
      assertEquals(0f, f.wrapper.attrs.get(Stat.hitpoints).asFixed(), 0.01f);
      assertFalse(f.monster.converted);
      assertEquals(-1, f.monster.conversionOwnerId);
      assertEquals(0, f.states.size());
    }
  }

  @Test
  void saveRemovalAloneDoesNotInventAConversionEndOrMissingOriginalStats() {
    Fixture f = new Fixture(false);
    f.states.removeState(StateId.CONVERSION_SAVE);
    assertTrue(f.monster.converted);
    f.states.removeState(StateId.CONVERSION);
    assertFalse(f.monster.converted);
    assertEquals(-1, f.monster.conversionOwnerId);
    assertEquals(10, f.wrapper.attrs.get(Stat.level).asInt());
    assertEquals(100f, f.wrapper.attrs.get(Stat.maxhp).asFixed(), 0.01f);

    Fixture retained = new Fixture(false);
    retained.conversion.basicStatList = true;
    retained.states.clearRemovable(null);
    assertTrue(retained.states.hasState(StateId.CONVERSION));
    assertTrue(retained.monster.converted, "priority must not force removal of a policy-excluded Conversion");
    assertNotNull(retained.conversion.removalCallback);
    retained.states.discardAll();
  }

  @Test
  void pooledComponentResetDiscardsHooksWithoutMutatingFormerUnit() {
    Fixture f = new Fixture(false);
    ResettableUnitStates component = new ResettableUnitStates();
    component.stateList = f.states;
    component.resetForTest();
    assertTrue(f.monster.converted, "component teardown is not a living-unit dispel");
    assertEquals(10, f.wrapper.attrs.get(Stat.level).asInt());
    assertEquals(0, f.states.size());
    assertNull(f.conversion.removalCallback);
    component.init(99);
    UnitState reused = component.stateList.addState(StateId.CONVERSION, 1, 1, 8);
    assertNull(reused.removalCallback);
    component.stateList.update();
    assertTrue(f.monster.converted, "reused state must not call an old unit's hook");
  }

  private static final class ResettableUnitStates extends UnitStates {
    void resetForTest() { reset(); }
  }

  private static Attributes attributes(int level, float hp, float maxHp) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.level, level);
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, maxHp);
    attrs.reset();
    return attrs;
  }

  private static final class Fixture {
    final StateList states = new StateList(42);
    final Monster monster = new Monster();
    final AttributesWrapper wrapper = new AttributesWrapper();
    final UnitState conversion;

    Fixture(boolean saveFirst) {
      wrapper.attrs = attributes(10, 50f, 100f);
      monster.converted = true;
      monster.conversionOwnerId = 7;
      if (saveFirst) save();
      conversion = states.addState(StateId.CONVERSION, 100, 3, 7);
      if (!saveFirst) save();
      NativeConversionLifecycle.bind(conversion, monster, wrapper);
    }

    void save() {
      UnitState save = states.addPermanentState(StateId.CONVERSION_SAVE);
      save.conversionOriginalLevel = 20;
      save.conversionOriginalMaxHpEncoded = 200 * 256;
    }

    void assertRestored() {
      assertFalse(monster.converted);
      assertEquals(-1, monster.conversionOwnerId);
      assertFalse(states.hasState(StateId.CONVERSION));
      assertFalse(states.hasState(StateId.CONVERSION_SAVE));
      assertEquals(20, wrapper.attrs.get(Stat.level).asInt());
      assertEquals(200f, wrapper.attrs.get(Stat.maxhp).asFixed(), 0.01f);
      assertEquals(100f, wrapper.attrs.get(Stat.hitpoints).asFixed(), 0.01f);
    }
  }
}
