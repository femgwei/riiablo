package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.engine.server.component.Monster;
import org.junit.jupiter.api.Test;

class Act5BaalWaveMemberTest {
  @Test
  void markerKeepsWaveIdentityWhenLeaderDies() {
    Monster member = new Monster().set(null, null);
    member.setBaalWaveMember(1, Act5BaalQuest.WAVE_SUPER_UNIQUES[1], false);

    assertEquals(1, member.baalWaveIndex);
    assertEquals(Act5BaalQuest.WAVE_SUPER_UNIQUES[1], member.baalWaveSuperUniqueId);
    assertFalse(member.baalWaveLeader);
  }

  @Test
  void pooledMonsterResetClearsWaveMarker() {
    Monster member = new Monster().set(null, null)
        .setBaalWaveMember(0, Act5BaalQuest.WAVE_SUPER_UNIQUES[0], true);
    member.set(null, null);

    assertEquals(-1, member.baalWaveIndex);
    assertEquals(-1, member.baalWaveSuperUniqueId);
    assertFalse(member.baalWaveLeader);
  }
}
