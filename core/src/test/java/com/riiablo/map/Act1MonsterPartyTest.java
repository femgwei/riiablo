package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;
import com.riiablo.codec.excel.MonStats;

class Act1MonsterPartyTest {
  @Test
  void partyRollIsInclusiveAndDeterministic() {
    assertEquals(2, Act1MapBuilderD2MOD.partySize(2, 4, 0));
    assertEquals(3, Act1MapBuilderD2MOD.partySize(2, 4, 1));
    assertEquals(4, Act1MapBuilderD2MOD.partySize(2, 4, 2));
    assertEquals(0, Act1MapBuilderD2MOD.partySize(0, 0, 42));
  }

  @Test
  void partyMinionsAlternateAndFallbackToTheDefinedEntry() {
    MonStats.Entry leader = new MonStats.Entry();
    leader.minion1 = "fallen1";
    leader.minion2 = "cr_lancer1";
    assertEquals("fallen1", Act1MapBuilderD2MOD.partyMinion(leader, 0));
    assertEquals("cr_lancer1", Act1MapBuilderD2MOD.partyMinion(leader, 1));
    leader.minion2 = "";
    assertEquals("fallen1", Act1MapBuilderD2MOD.partyMinion(leader, 1));
    leader.minion1 = "0";
    assertNull(Act1MapBuilderD2MOD.partyMinion(leader, 0));
  }
}
