package com.riiablo.engine.server;

import com.artemis.World;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeTargetOrderTest {
  @Test
  void componentAndStateEventsShareOrderButDoNotChangePetQuotaSequence() {
    SummonedPet first = new SummonedPet().set(1, "skeleton", 70, 1, false, 0);
    Mercenary merc = new Mercenary().set(1, 0, 1, 1, 0);
    StateList states = new StateList(42);
    states.addState(StateId.ATTRACT, 100, 1, 1);
    StateList conversionStates = new StateList(43);
    conversionStates.addState(StateId.CONVERSION, 100, 1, 1);
    SummonedPet last = new SummonedPet().set(1, "skeleton", 70, 1, false, 0);
    assertTrue(first.nativeTargetOrder > 0);
    assertTrue(merc.nativeTargetOrder > first.nativeTargetOrder);
    assertTrue(states.nativeNode9Order() > merc.nativeTargetOrder);
    assertEquals(0, states.nativeConversionOrder(), "one unit is not in both state buckets");
    assertTrue(conversionStates.nativeConversionOrder() > states.nativeNode9Order());
    assertTrue(last.nativeTargetOrder > conversionStates.nativeConversionOrder());
    assertEquals(first.spawnOrder + 1, last.spawnOrder, "target ordering is not quota eviction ordering");
  }

  @Test
  void stateResetAndComponentRecreationCannotLeakOldMembershipOrder() {
    // The default Gradle build disables pooled weaving. Exercise its state
    // reset contract explicitly, then check normal ECS component recreation.
    ResettableUnitStates resettable = new ResettableUnitStates();
    StateList resetStates = resettable.init(42).stateList;
    resetStates.addState(StateId.ATTRACT, 100, 1, 1);
    resetStates.removeState(StateId.ATTRACT);
    resetStates.addState(StateId.CONVERSION, 100, 1, 1);
    assertTrue(resetStates.nativeConversionOrder() > 0);
    resettable.resetForTest();
    assertEquals(0, resetStates.nativeNode9Order());
    assertEquals(0, resetStates.nativeConversionOrder());
    World world = new World();
    try {
      int first = world.create();
      world.getMapper(Mercenary.class).create(first).set(1, 0, 1, 1, 0);
      world.getMapper(SummonedPet.class).create(first).set(1, "skeleton", 70, 1, false, 0);
      StateList states = world.getMapper(UnitStates.class).create(first).init(first).stateList;
      states.addState(StateId.ATTRACT, 100, 1, 1);
      states.addState(StateId.CONVERSION, 100, 1, 1);
      world.process();
      world.delete(first);
      world.process();
      int next = world.create();
      assertEquals(0, world.getMapper(Mercenary.class).create(next).nativeTargetOrder);
      assertEquals(0, world.getMapper(SummonedPet.class).create(next).nativeTargetOrder);
      StateList reused = world.getMapper(UnitStates.class).create(next).init(next).stateList;
      assertEquals(0, reused.nativeNode9Order());
      assertEquals(0, reused.nativeConversionOrder());
    } finally {
      world.dispose();
    }
  }

  private static class ResettableUnitStates extends UnitStates {
    void resetForTest() { super.reset(); }
  }
}
