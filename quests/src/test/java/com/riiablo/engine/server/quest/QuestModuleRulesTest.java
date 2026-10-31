package com.riiablo.engine.server.quest;

import java.util.HashSet;
import java.util.Set;
import com.riiablo.engine.server.monster.MonsterType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.riiablo.engine.server.quest.NativeQuestRecord.*;

class QuestModuleRulesTest {
  private static final class Player implements QuestPlayer {
    final short[][] records = new short[5][8];
    final Set<String> inventory = new HashSet<>();
    boolean expansion = true;
    @Override public short record(int act, int index) { return records[act][index]; }
    @Override public QuestInventory items() { return inventory::contains; }
    @Override public boolean expansion() { return expansion; }
  }

  private static QuestDialogue.Offer offer(Player p, int npc, int message, boolean attention) {
    QuestDialogue.Offer result = QuestDialogue.select(npc, p, Act5BaalQuest.HARROGATH);
    assertNotNull(result);
    assertEquals(message, result.message);
    assertEquals(attention, result.attention);
    return result;
  }

  @Test void atmaMarkerLifecycleMatchesNativeActiveFilter() {
    Player p = new Player();
    offer(p, MonsterType.ATMA, 304, true);
    p.records[1][1] = Act2RadamentQuest.start((short) 0);
    offer(p, MonsterType.ATMA, Act2RadamentQuest.selectAtmaMessage(p.records[1][1]), false);
    p.records[1][1] = Act2RadamentQuest.completeObjective(p.records[1][1]);
    offer(p, MonsterType.ATMA, 334, true);
    p.records[1][1] = Act2RadamentQuest.claimReward(p.records[1][1]);
    assertNull(QuestDialogue.select(MonsterType.ATMA, p, -1));
    p.records[1][1] = set((short) 0, COMPLETED_BEFORE);
    assertNull(QuestDialogue.select(MonsterType.ATMA, p, -1));
  }

  @Test void townIntroOffersCompleteBeforeFollowupQuestOffers() {
    Player p = new Player();
    offer(p, MonsterType.JERHYN, Act2JerhynIntroQuest.MESSAGE, true);
    p.records[1][Act2JerhynIntroQuest.RECORD] =
        Act2JerhynIntroQuest.acknowledge((short) 0);
    // Jerhyn's later quest chain is now allowed to replace the intro offer.
    assertNull(QuestDialogue.select(MonsterType.JERHYN, p, -1));

    offer(p, MonsterType.HRATLI, Act3HratliIntroQuest.MESSAGE, true);
    p.records[2][Act3HratliIntroQuest.RECORD] =
        Act3HratliIntroQuest.acknowledge((short) 0);
    offer(p, MonsterType.HRATLI, Act3GidbinnQuest.MESSAGE_HRATLI_INIT, true);
  }

  @Test void cainStaffDoesNotAdvertiseAcknowledgedAssemblyForever() {
    Player p = new Player();
    assertNull(QuestDialogue.select(MonsterType.DECKARDCAIN_ACT2, p, -1));
    p.inventory.add(Act2HoradricStaffQuest.HORADRIC_STAFF);
    offer(p, MonsterType.DECKARDCAIN_ACT2, 339, true);
    p.records[1][2] = Act2HoradricStaffQuest.acknowledgeAssembly((short) 0);
    assertNull(QuestDialogue.select(MonsterType.DECKARDCAIN_ACT2, p, -1));
  }

  @Test void goldenBirdTurnInsUseItemsAndOneShotFlags() {
    Player p = new Player();
    assertNull(QuestDialogue.select(MonsterType.CAIN3, p, -1));
    p.inventory.add("j34");
    offer(p, MonsterType.CAIN3, 527, true);
    offer(p, MonsterType.MESHIF2, 529, true);
    p.inventory.clear();
    p.inventory.add("g34");
    offer(p, MonsterType.CAIN3, 531, true);
    offer(p, MonsterType.ALKOR, 534, true);
    p.inventory.clear();
    p.records[2][1] = Act3GoldenBirdQuest.acceptAtAlkor((short) 0);
    offer(p, MonsterType.ALKOR, 538, true);
    p.records[2][1] = Act3GoldenBirdQuest.claimReward(p.records[2][1]);
    assertNull(QuestDialogue.select(MonsterType.ALKOR, p, -1));
  }

  @Test void gidbinnRewardsRemainIndependent() {
    Player p = new Player();
    p.records[2][Act3HratliIntroQuest.RECORD] =
        Act3HratliIntroQuest.acknowledge((short) 0);
    offer(p, MonsterType.HRATLI, 571, true);
    p.inventory.add("g33");
    offer(p, MonsterType.ORMUS, 587, true);
    p.inventory.clear();
    p.records[2][2] = Act3GidbinnQuest.markBroughtToOrmus((short) 0);
    offer(p, MonsterType.ORMUS, 593, true);
    offer(p, MonsterType.ASHEARA, 589, true);
    p.records[2][2] = Act3GidbinnQuest.markOrmusReward(p.records[2][2]);
    assertNull(QuestDialogue.select(MonsterType.ORMUS, p, -1));
    offer(p, MonsterType.ASHEARA, 589, true);
    p.records[2][2] = Act3GidbinnQuest.completeIfBothRewards(
        Act3GidbinnQuest.markAshearaReward(p.records[2][2]));
    assertNull(QuestDialogue.select(MonsterType.ASHEARA, p, -1));
  }

  @Test void act4RewardPrecedesReplayableExpansionPortal() {
    Player p = new Player();
    offer(p, MonsterType.TYRAEL2, Act4IzualQuest.MESSAGE_TYRAEL_INIT, true);
    p.records[3][1] = Act4IzualQuest.completeObjective((short) 0);
    p.records[3][2] = set((short) 0, PRIMARY_GOAL_DONE);
    offer(p, MonsterType.TYRAEL2, Act4IzualQuest.MESSAGE_TYRAEL_REWARD, true);
    p.records[3][1] = Act4IzualQuest.claimReward(p.records[3][1]);
    offer(p, MonsterType.TYRAEL2, Act4DiabloQuest.MESSAGE_TYRAEL_ACT5, true);
    p.records[3][2] = Act4DiabloQuest.claimCompletion(p.records[3][2]);
    offer(p, MonsterType.TYRAEL2, Act4DiabloQuest.MESSAGE_TYRAEL_ACT5, false);
    p.expansion = false;
    assertNull(QuestDialogue.select(MonsterType.TYRAEL2, p, -1));
  }

  @Test void act5StartsRewardsPrerequisitesAndExpansionGate() {
    Player p = new Player();
    offer(p, MonsterType.LARZUK, 20077, true);
    p.records[4][1] = Act5ShenkQuest.complete((short) 0);
    offer(p, MonsterType.LARZUK, 20090, true);
    offer(p, MonsterType.QUAL_KEHK, 20096, true);
    p.records[4][2] = Act5RescueQuest.complete((short) 0);
    offer(p, MonsterType.QUAL_KEHK, 20110, true);
    offer(p, MonsterType.MALAH, Act5PrisonQuest.MESSAGE_MALAH_INIT, true);
    assertNull(QuestDialogue.select(MonsterType.DREHYA, p, -1));
    p.records[4][3] = Act5PrisonQuest.complete((short) 0);
    offer(p, MonsterType.MALAH, Act5PrisonQuest.MESSAGE_MALAH_REWARD, true);
    offer(p, MonsterType.DREHYA, 20137, true);
    p.records[4][4] = Act5NihlathakQuest.start((short) 0);
    offer(p, MonsterType.DREHYA, 20137, false);
    p.records[4][4] = Act5NihlathakQuest.complete(p.records[4][4]);
    offer(p, MonsterType.DREHYA, 20148, true);
    p.records[4][2] = Act5RescueQuest.claimReward(p.records[4][2]);
    offer(p, MonsterType.QUAL_KEHK, 20153, true);
    p.expansion = false;
    assertNull(QuestDialogue.select(MonsterType.LARZUK, p, -1));
    assertNull(QuestDialogue.select(MonsterType.DREHYA, p, -1));
  }

  @Test void baalTerminalMessageRequiresNativeChamber() {
    Player p = new Player();
    p.records[4][6] = Act5BaalQuest.complete((short) 0);
    assertNull(QuestDialogue.select(Act5BaalQuest.TYRAEL3_CLASS, p, Act5BaalQuest.HARROGATH));
    QuestDialogue.Offer terminal = QuestDialogue.select(Act5BaalQuest.TYRAEL3_CLASS, p,
        Act5BaalQuest.WORLDSTONE_CHAMBER);
    assertNotNull(terminal);
    assertEquals(20175, terminal.message);
    assertTrue(terminal.attention);
    p.records[4][6] = set(p.records[4][6], CUSTOM3);
    assertFalse(QuestDialogue.select(Act5BaalQuest.TYRAEL3_CLASS, p,
        Act5BaalQuest.WORLDSTONE_CHAMBER).attention);
  }

  @Test void resourceKeysDoNotConfuseUiOrderWithSaveSlots() {
    for (int act = 0; act < 5; act++) {
      int count = act == 3 ? 3 : 6;
      for (int q = 1; q <= count; q++) {
        int expected = act == 2 && q <= 4 ? 5 - q : q;
        assertEquals(expected, QuestCatalog.recordIndex(act, "qstsa" + (act + 1) + "q" + q));
      }
    }
    assertEquals(-1, QuestCatalog.recordIndex(4, "qstsa1q1"));
    assertEquals(-1, QuestCatalog.recordIndex(4, "qstsa5q0"));
    assertEquals(-1, QuestCatalog.recordIndex(5, "qstsa6q1"));
  }

  @Test void independentModuleCannotLoadGameHost() {
    assertThrows(ClassNotFoundException.class, () -> Class.forName("com.riiablo.Riiablo"));
  }

  @Test void laterActNpcsHaveExplicitQuestMenuSupportButMonstersDoNot() {
    for (int npc : new int[] {MonsterType.ATMA, MonsterType.CAIN3, MonsterType.ALKOR,
        MonsterType.MALAH, MonsterType.DREHYA, Act5BaalQuest.TYRAEL3_CLASS}) {
      assertTrue(QuestDialogue.supportsNpc(npc));
    }
    assertFalse(QuestDialogue.supportsNpc(MonsterType.SUMMONER));
    assertFalse(QuestDialogue.supportsNpc(-1));
  }
}
