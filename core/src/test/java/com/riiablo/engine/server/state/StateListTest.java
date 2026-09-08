package com.riiablo.engine.server.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import com.riiablo.attributes.NativeStatResolver;
import com.riiablo.attributes.Stat;

public class StateListTest {
  @Test
  public void snapshotReplacesStaleStatesAndPreservesPayload() {
    StateList states = new StateList(42);
    states.addState(StateId.POISON, 90, 2, 7);
    states.addState(StateId.COLD, 20, 1, 8);

    states.replaceFromSnapshot(
        new int[] {StateId.FREEZE},
        new int[] {12},
        new int[] {3});

    assertEquals(1, states.size());
    assertFalse(states.hasState(StateId.POISON));
    assertFalse(states.hasState(StateId.COLD));
    assertEquals(12, states.getStateDuration(StateId.FREEZE));
    assertEquals(3, states.getStateLevel(StateId.FREEZE));
  }

  @Test
  public void nativeContributionProjectsToLegacyConsumersWithoutDoubleCounting() {
    StateList states = new StateList(42);
    UnitState might = states.addStateLayer(StateId.MIGHT, 25, 3, 7, 98);
    might.setStatContribution(
        Stat.damagepercent, 0, NativeStatResolver.Operation.ADD, 40);

    assertEquals(40, might.damageModifier);
    assertEquals(40, might.resolvedDamageModifier());
    assertEquals(40, states.getTotalDamageModifier());
    assertEquals(1, might.getStatContributions().size());
  }

  @Test
  public void sameStateFromDifferentOwnersRetainsAndRemovesExactLayer() {
    StateList states = new StateList(42);
    UnitState weak = states.addStateLayer(StateId.MIGHT, 25, 1, 7, 98);
    weak.setStatContribution(
        Stat.damagepercent, 0, NativeStatResolver.Operation.ADD, 20);
    UnitState strong = states.addStateLayer(StateId.MIGHT, 50, 5, 8, 98);
    strong.setStatContribution(
        Stat.damagepercent, 0, NativeStatResolver.Operation.ADD, 60);

    assertSame(weak, states.getStateLayer(StateId.MIGHT, 7, 98));
    assertSame(strong, states.getStateLayer(StateId.MIGHT, 8, 98));
    assertEquals(80, states.getTotalDamageModifier());

    states.removeStateLayer(StateId.MIGHT, 7, 98);

    assertNull(states.getStateLayer(StateId.MIGHT, 7, 98));
    assertSame(strong, states.getState(StateId.MIGHT));
    assertEquals(60, states.getTotalDamageModifier());
  }

  @Test
  public void expiringOneOwnerDoesNotClearSharedStateFlag() {
    StateList states = new StateList(42);
    states.addStateLayer(StateId.MIGHT, 1, 1, 7, 98);
    states.addStateLayer(StateId.MIGHT, 10, 1, 8, 98);

    states.update();

    assertEquals(1, states.size());
    assertEquals(8, states.getState(StateId.MIGHT).sourceEntityId);
  }

  @Test
  public void nativeEntriesUseStableStatLayerOrderAndDropZeroValues() {
    UnitState state = new UnitState(StateId.SHOUT);
    state.setStatContribution(Stat.fireresist, 2, NativeStatResolver.Operation.ADD, 5);
    state.setStatContribution(Stat.damagepercent, 3, NativeStatResolver.Operation.PERCENT, 10);
    state.setStatContribution(Stat.damagepercent, 1, NativeStatResolver.Operation.PERCENT, 20);

    assertEquals(Stat.damagepercent, state.getStatContributions().get(0).statId);
    assertEquals(1, state.getStatContributions().get(0).layer);
    assertEquals(3, state.getStatContributions().get(1).layer);
    assertEquals(Stat.fireresist, state.getStatContributions().get(2).statId);

    state.addStatContribution(
        Stat.damagepercent, 3, NativeStatResolver.Operation.PERCENT, -10);
    assertEquals(2, state.getStatContributions().size());
    assertEquals(20, state.damageModifier);
  }

  @Test
  public void legacyScalarWritesAreImportedOnFirstAggregateRead() {
    StateList states = new StateList(42);
    UnitState state = states.addState(StateId.BATTLEORDERS, 30, 1, 7);
    state.damageModifier = 35;
    state.fireResistModifier = 20;

    assertEquals(35, states.getTotalDamageModifier());
    assertEquals(20, states.getTotalResistModifier(0));
    assertEquals(2, state.getStatContributions().size());

    state.damageModifier = 50;
    assertEquals(50, states.getTotalDamageModifier());
    assertEquals(50, state.getStatContributionValue(Stat.damagepercent));
  }
}
