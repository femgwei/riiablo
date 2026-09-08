package com.riiablo.engine.server.state;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.riiablo.attributes.NativeStatResolver;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.States;

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

  @Test
  public void attackRateIsAnimationSpeedAndNeverAttackRating() {
    StateList states = new StateList(42);
    UnitState cold = states.addState(StateId.COLD, 20, 1, 7);
    cold.setStatContribution(
        Stat.attackrate, 0, NativeStatResolver.Operation.ADD, -50);
    cold.setStatContribution(
        Stat.other_animrate, 0, NativeStatResolver.Operation.ADD, -50);

    assertEquals(0, states.getTotalAttackModifier());
    assertEquals(-50, states.getTotalAnimationRateModifier());
  }

  @Test
  public void curseLayersAreOwnedBySourceAndRefreshExactKey() throws Exception {
    StateList states = new StateList(42);
    States table = curseTable();
    UnitState weak = states.applyCurseState(table, StateId.AMPLIFYDAMAGE,
        30, 1, 7, 100, 25, Stat.damagepercent, 25,
        NativeStatResolver.Operation.ADD);
    UnitState strong = states.applyCurseState(table, StateId.AMPLIFYDAMAGE,
        40, 3, 8, 100, 60, Stat.damagepercent, 60,
        NativeStatResolver.Operation.ADD);
    assertNotNull(weak);
    assertNotNull(strong);
    assertEquals(2, states.size());
    assertEquals(85, states.getTotalDamageModifier());

    // Reapplying a weaker value from the same owner must not overwrite it.
    states.applyCurseState(table, StateId.AMPLIFYDAMAGE,
        90, 1, 7, 100, 10, Stat.damagepercent, 10,
        NativeStatResolver.Operation.ADD);
    assertEquals(25, weak.curseStrength);
    assertEquals(30, weak.duration);

    // Removing the strong source reveals the still-active weaker layer.
    assertTrue(states.removeStateLayer(StateId.AMPLIFYDAMAGE, 8, 100));
    assertEquals(25, states.getTotalDamageModifier());
  }

  @Test
  public void curseGroupsReplaceOnlyDifferentStateIdsAndRejectWeaker() throws Exception {
    StateList states = new StateList(42);
    States table = curseTable();
    UnitState amplify = states.applyCurseState(table, StateId.AMPLIFYDAMAGE,
        50, 1, 1, 10, 30);
    assertNotNull(amplify);
    assertNotNull(states.applyCurseState(table, StateId.WEAKEN,
        50, 1, 2, 11, 40));
    // A weaker curse in the same group cannot displace Weaken.
    assertNull(states.applyCurseState(table, StateId.AMPLIFYDAMAGE,
        50, 1, 3, 12, 10));
    assertTrue(states.hasState(StateId.WEAKEN));
    assertFalse(states.hasState(StateId.AMPLIFYDAMAGE));
  }

  @Test
  public void coldReapplicationOnlyExtendsExpiryAndKeepsOriginalOwner() {
    StateList states = new StateList(42);
    UnitState cold = states.extendState(StateId.COLD, 50, 1, 7);
    cold.velocityModifier = -50;

    assertSame(cold, states.extendState(StateId.COLD, 20, 5, 8));
    assertEquals(50, cold.duration);
    assertEquals(1, cold.level);
    assertEquals(7, cold.sourceEntityId);

    assertSame(cold, states.extendState(StateId.COLD, 80, 5, 8));
    assertEquals(80, cold.duration);
    assertEquals(1, cold.level);
    assertEquals(7, cold.sourceEntityId);
  }

  @Test
  public void deathRetentionUsesNativeUnitSpecificMasksAndRemovesStatDeltas() throws Exception {
    States table = deathPolicyTable();

    StateList player = policyStates();
    assertEquals(4, player.retainForDeath(table, StateList.DeathUnitType.PLAYER));
    assertEquals(1, player.size());
    assertEquals(10, player.getTotalDamageModifier());

    StateList monster = policyStates();
    assertEquals(4, monster.retainForDeath(table, StateList.DeathUnitType.MONSTER));
    assertEquals(2, monster.getState(StateId.POISON).stateId);
    assertEquals(20, monster.getTotalDamageModifier());

    StateList boss = policyStates();
    assertEquals(4, boss.retainForDeath(table, StateList.DeathUnitType.BOSS));
    assertEquals(3, boss.getState(StateId.RESISTFIRE).stateId);
    assertEquals(30, boss.getTotalDamageModifier());
  }

  @Test
  public void ordinaryClearRetainsOnlyNoClearMask() throws Exception {
    StateList states = policyStates();

    assertEquals(4, states.clearRemovable(deathPolicyTable()));
    assertEquals(1, states.size());
    assertEquals(StateId.RESISTCOLD, states.getState(StateId.RESISTCOLD).stateId);
    assertEquals(40, states.getTotalDamageModifier());
  }

  @Test
  public void deathRetentionAlwaysKeepsBasicPermanentLayer() throws Exception {
    StateList states = new StateList(42);
    UnitState passive = states.addStateLayer(StateId.IRONSKIN, 0, 4, 42, 205);
    passive.basicStatList = true;
    passive.damageModifier = 25;

    assertEquals(0, states.retainForDeath(deathPolicyTable(), StateList.DeathUnitType.PLAYER));
    assertEquals(25, states.getTotalDamageModifier());
  }

  private static StateList policyStates() {
    StateList states = new StateList(42);
    for (int stateId = 1; stateId <= 5; stateId++) {
      UnitState state = states.addStateLayer(stateId, 100, 1, stateId, 10 + stateId);
      state.setStatContribution(
          Stat.damagepercent, 0, NativeStatResolver.Operation.ADD, stateId * 10);
    }
    return states;
  }

  private static States deathPolicyTable() throws Exception {
    String txt = "state\tplrstaydeath\tmonstaydeath\tbossstaydeath\tnoclear\n"
        + "none\t\t\t\t\n"
        + "playerstay\t1\t\t\t\n"
        + "monsterstay\t\t1\t\t\n"
        + "bossstay\t\t\t1\t\n"
        + "noclear\t\t\t\t1\n"
        + "ordinary\t\t\t\t\n";
    return States.parse(txt.getBytes(StandardCharsets.US_ASCII));
  }

  private static States curseTable() throws Exception {
    StringBuilder txt = new StringBuilder("state\tgroup\tcurse\tcurable\n");
    for (int i = 0; i <= StateId.WEAKEN; i++) {
      if (i == StateId.AMPLIFYDAMAGE) {
        txt.append("amplify\t1\t1\t1\n");
      } else if (i == StateId.WEAKEN) {
        txt.append("weaken\t1\t1\t1\n");
      } else {
        txt.append("state").append(i).append("\t0\t\t\n");
      }
    }
    return States.parse(txt.toString().getBytes(StandardCharsets.US_ASCII));
  }
}
