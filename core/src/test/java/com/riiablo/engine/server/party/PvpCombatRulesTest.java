package com.riiablo.engine.server.party;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/** Regression tests for the single authoritative PvP target policy. */
class PvpCombatRulesTest {
  @Test
  void playersCannotDamageEachOtherUntilHostilityIsDeclared() {
    PartyManager parties = new PartyManager();
    assertFalse(PvpCombatRules.canDamage(parties, 1, 2, true, true));
    assertTrue(parties.declareHostility(1, 2));
    assertTrue(parties.areHostile(1, 2));
    assertTrue(parties.areHostile(2, 1));
    assertEquals(PartyRelation.HOSTILE, parties.getRelation(2, 1));
    assertTrue(PvpCombatRules.canDamage(parties, 1, 2, true, true));
    // Hostility is effective in both directions, matching D2MOO's player list
    // flag semantics even though the request originates from one player.
    assertTrue(PvpCombatRules.canDamage(parties, 2, 1, true, true));
    parties.removeHostility(1, 2);
    assertFalse(PvpCombatRules.canDamage(parties, 1, 2, true, true));
  }

  @Test
  void partyMembershipOverridesStaleHostility() {
    PartyManager parties = new PartyManager();
    assertTrue(parties.declareHostility(1, 2));
    // Simulate a party transition after an earlier hostile relation.
    assertTrue(parties.sendInvitation(1, 2));
    assertTrue(parties.acceptInvitation(2));
    assertFalse(PvpCombatRules.canDamage(parties, 1, 2, true, true));
  }

  @Test
  void leavingPartyClearsBilateralHostilityFlags() {
    PartyManager parties = new PartyManager();
    assertTrue(parties.sendInvitation(1, 2));
    assertTrue(parties.acceptInvitation(2));
    // Simulate a stale relation inserted while the members were allied.
    parties.setRelation(1, 2, PartyRelation.HOSTILE);
    parties.setRelation(2, 1, PartyRelation.HOSTILE);
    parties.leaveParty(2);
    assertFalse(parties.areHostile(1, 2));
    assertFalse(parties.areHostile(2, 1));
  }

  @Test
  void monsterPlayerAndMonsterMonsterRulesMatchNativeTeams() {
    PartyManager parties = new PartyManager();
    assertTrue(PvpCombatRules.canDamage(parties, 10, 20, false, true));
    assertTrue(PvpCombatRules.canDamage(parties, 20, 10, true, false));
    assertFalse(PvpCombatRules.canDamage(parties, 10, 11, false, false));
  }

  @Test
  void nativeHostilityDeclarationRequiresTownAndLevelNine() {
    assertFalse(PvpCombatRules.canDeclareHostility(8, 9, true));
    assertFalse(PvpCombatRules.canDeclareHostility(9, 8, true));
    assertFalse(PvpCombatRules.canDeclareHostility(9, 9, false));
    assertTrue(PvpCombatRules.canDeclareHostility(9, 9, true));
  }

  @Test
  void nativeHostilityDelaySurvivesRemovalAndExpiresAfterSixtySeconds() {
    AtomicLong now = new AtomicLong(1_000L);
    PartyManager parties = new PartyManager(now::get);
    assertTrue(parties.declareHostility(1, 2));
    parties.removeHostility(1, 2);

    assertEquals(60_000L, parties.hostilityCooldownRemaining(1, 2));
    assertFalse(parties.declareHostility(1, 2));
    now.addAndGet(59_999L);
    assertEquals(1L, parties.hostilityCooldownRemaining(1, 2));
    assertFalse(parties.declareHostility(1, 2));
    now.incrementAndGet();
    assertEquals(0L, parties.hostilityCooldownRemaining(1, 2));
    assertTrue(parties.declareHostility(1, 2));
  }

  @Test
  void nativeHostilityDelayIsDirectional() {
    AtomicLong now = new AtomicLong(1_000L);
    PartyManager parties = new PartyManager(now::get);
    assertTrue(parties.declareHostility(1, 2));
    parties.removeHostility(1, 2);

    assertEquals(60_000L, parties.hostilityCooldownRemaining(1, 2));
    assertEquals(0L, parties.hostilityCooldownRemaining(2, 1));
    assertTrue(parties.declareHostility(2, 1));
  }

  @Test
  void idempotentRequestDoesNotRestartDelayAndDisconnectClearsIt() {
    AtomicLong now = new AtomicLong(1_000L);
    PartyManager parties = new PartyManager(now::get);
    assertTrue(parties.declareHostility(1, 2));
    now.addAndGet(10_000L);

    assertTrue(parties.declareHostility(1, 2));
    assertEquals(50_000L, parties.hostilityCooldownRemaining(1, 2));
    parties.removePlayer(1);
    assertEquals(0L, parties.hostilityCooldownRemaining(1, 2));
  }
}
