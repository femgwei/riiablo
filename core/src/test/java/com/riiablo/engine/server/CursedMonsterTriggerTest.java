package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.riiablo.engine.server.event.MeleeAttackEvent;
import org.junit.jupiter.api.Test;

class CursedMonsterTriggerTest {
  @Test void scarabModifierIsTwentyAndSixteenIsChampion() {
    assertEquals(com.riiablo.engine.server.monster.MonsterAffix.CHARGED_BOLTS,
        com.riiablo.engine.server.monster.MonsterAffix.fromUniqueMods(new int[] {20}));
    assertEquals(0L,
        com.riiablo.engine.server.monster.MonsterAffix.fromUniqueMods(new int[] {16}));
  }
  @Test void nativeLowBitsRejectExactlyOneOfFourOutcomes() {
    int successes = 0;
    for (int roll = 0; roll < 256; roll++) {
      if (ServerSkillSystem.cursedHitRollSucceeds(roll)) successes++;
    }
    assertEquals(192, successes);
  }

  @Test void successfulZeroDamageHitIsDistinctFromMissAndBlock() {
    ServerSkillSystem system = spy(new ServerSkillSystem());
    doNothing().when(system).onCursedMonsterHit(anyInt());
    system.onMonsterMeleeHit(MeleeAttackEvent.obtain(7, 8, false, false));
    system.onMonsterMeleeHit(MeleeAttackEvent.obtain(7, 8, true, true));
    verify(system, never()).onCursedMonsterHit(anyInt());
    system.onMonsterMeleeHit(MeleeAttackEvent.obtain(7, 8, true, false));
    verify(system).onCursedMonsterHit(7);
  }
}
