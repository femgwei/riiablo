package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.d2moo.common.drlg.D2LevelIds;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.SuperUnique;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.event.NativeCountessQuestEvent;
import com.riiablo.engine.server.event.NativeActTransitionEvent;
import com.riiablo.engine.server.event.NpcQuestMessageEvent;
import com.riiablo.engine.server.event.NativeQuestRewardEvent;
import com.riiablo.engine.server.event.QuestItemPickedUpEvent;
import com.riiablo.engine.server.event.QuestObjectInteractionEvent;
import com.riiablo.engine.server.event.NativeCainQuestEvent;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.engine.server.party.PartyManager;
import com.d2moo.common.drlg.D2SuperUniques;
import com.riiablo.engine.server.object.NativeQuestObjectResolver;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.ItemReader;
import com.riiablo.item.ItemWriter;
import com.riiablo.item.Quality;
import com.riiablo.io.ByteInput;
import com.riiablo.io.ByteOutput;
import io.netty.buffer.Unpooled;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import org.junit.jupiter.api.Test;

class Act1QuestSystemTest extends RiiabloTest {
  @Test
  void keepsPlayerAndDifficultyRecordsIndependent() {
    Harness harness = new Harness();
    try {
      CharData first = character("First", Riiablo.NORMAL);
      CharData second = character("Second", Riiablo.NORMAL);
      int firstId = harness.createPlayer(first);
      harness.createPlayer(second);
      int akara = harness.createAkara();
      harness.process();

      harness.events.dispatch(NpcQuestMessageEvent.obtain(
          firstId, akara, Act1DenOfEvilQuest.MESSAGE_INIT));

      assertTrue(NativeQuestRecord.has(record(first), NativeQuestRecord.STARTED));
      assertEquals(0, Short.toUnsignedInt(record(second)));

      first.diff = Riiablo.NIGHTMARE;
      assertEquals(0, Short.toUnsignedInt(record(first)));
      harness.events.dispatch(NpcQuestMessageEvent.obtain(
          firstId, akara, Act1DenOfEvilQuest.MESSAGE_INIT));
      assertTrue(NativeQuestRecord.has(record(first), NativeQuestRecord.STARTED));

      first.diff = Riiablo.NORMAL;
      assertTrue(NativeQuestRecord.has(record(first), NativeQuestRecord.STARTED));
    } finally {
      harness.dispose();
    }
  }

  @Test
  void completesOnlyAfterEveryCurrentDenMonsterIsDead() {
    Harness harness = new Harness();
    try {
      CharData data = character("DenHero", Riiablo.NORMAL);
      int player = harness.createPlayer(data);
      int first = harness.createDenMonster(10f);
      int second = harness.createDenMonster(10f);
      harness.process();

      harness.setLife(first, 0f);
      harness.events.dispatch(DeathEvent.obtain(player, first));
      assertFalse(NativeQuestRecord.has(record(data), NativeQuestRecord.PRIMARY_GOAL_DONE));

      // Fallen Shaman resurrection removes Corpse and restores life. The
      // resurrected unit must be counted again instead of staying in a dead-ID set.
      harness.setLife(first, 10f);
      harness.world.getMapper(Corpse.class).remove(first);
      assertEquals(2, harness.quests.countLivingMonsters(D2LevelIds.LEVEL_DENOFEVIL));

      harness.setLife(second, 0f);
      harness.events.dispatch(DeathEvent.obtain(player, second));
      assertFalse(NativeQuestRecord.has(record(data), NativeQuestRecord.PRIMARY_GOAL_DONE));

      harness.setLife(first, 0f);
      harness.events.dispatch(DeathEvent.obtain(player, first));
      short completed = record(data);
      assertTrue(NativeQuestRecord.has(completed, NativeQuestRecord.PRIMARY_GOAL_DONE));
      assertTrue(NativeQuestRecord.has(completed, NativeQuestRecord.REWARD_PENDING));
      assertTrue(NativeQuestRecord.has(completed, NativeQuestRecord.COMPLETED_NOW));
    } finally {
      harness.dispose();
    }
  }

  @Test
  void creditsDenCompletionToMercenaryOwner() {
    Harness harness = new Harness();
    try {
      CharData data = character("MercOwner", Riiablo.NORMAL);
      int player = harness.createPlayer(data);
      int merc = harness.createMercenary(player);
      int monster = harness.createDenMonster(1f);
      harness.process();
      harness.setLife(monster, 0f);
      harness.events.dispatch(DeathEvent.obtain(merc, monster));
      assertTrue(NativeQuestRecord.has(record(data), NativeQuestRecord.PRIMARY_GOAL_DONE));
    } finally {
      harness.dispose();
    }
  }

  @Test
  void creditsSameLevelPartyMemberButNotDifferentLevelMember() {
    Harness harness = new Harness();
    try {
      CharData ownerData = character("DenOwner", Riiablo.NORMAL);
      CharData nearbyData = character("DenNearby", Riiablo.NORMAL);
      CharData remoteData = character("DenRemote", Riiablo.NORMAL);
      int owner = harness.createPlayer(ownerData);
      int nearby = harness.createPlayer(nearbyData);
      int remote = harness.createPlayer(remoteData);
      harness.setPlayerLevel(owner, D2LevelIds.LEVEL_DENOFEVIL);
      harness.setPlayerLevel(nearby, D2LevelIds.LEVEL_DENOFEVIL);
      harness.setPlayerLevel(remote, D2LevelIds.LEVEL_BLOODMOOR);
      assertTrue(harness.parties.sendInvitation(owner, nearby));
      assertTrue(harness.parties.acceptInvitation(nearby));
      assertTrue(harness.parties.sendInvitation(owner, remote));
      assertTrue(harness.parties.acceptInvitation(remote));
      int monster = harness.createDenMonster(1f);
      harness.process();
      harness.setLife(monster, 0f);
      harness.events.dispatch(DeathEvent.obtain(owner, monster));
      assertTrue(NativeQuestRecord.has(record(ownerData), NativeQuestRecord.PRIMARY_GOAL_DONE));
      assertTrue(NativeQuestRecord.has(record(nearbyData), NativeQuestRecord.PRIMARY_GOAL_DONE));
      assertFalse(NativeQuestRecord.has(record(remoteData), NativeQuestRecord.PRIMARY_GOAL_DONE));
    } finally {
      harness.dispose();
    }
  }

  @Test
  void excludesOfflineAndDeadPartyMembersFromDenCredit() {
    Harness harness = new Harness();
    try {
      CharData ownerData = character("DenCreditOwner", Riiablo.NORMAL);
      CharData eligibleData = character("DenCreditEligible", Riiablo.NORMAL);
      CharData offlineData = character("DenCreditOffline", Riiablo.NORMAL);
      CharData deadData = character("DenCreditDead", Riiablo.NORMAL);
      int owner = harness.createPlayer(ownerData);
      int eligible = harness.createPlayer(eligibleData);
      int offline = harness.createPlayer(offlineData);
      int dead = harness.createPlayer(deadData);
      harness.setPlayerLevel(owner, D2LevelIds.LEVEL_DENOFEVIL);
      harness.setPlayerLevel(eligible, D2LevelIds.LEVEL_DENOFEVIL);
      harness.setPlayerLevel(offline, D2LevelIds.LEVEL_DENOFEVIL);
      harness.setPlayerLevel(dead, D2LevelIds.LEVEL_DENOFEVIL);
      assertTrue(harness.parties.sendInvitation(owner, eligible));
      assertTrue(harness.parties.acceptInvitation(eligible));
      assertTrue(harness.parties.sendInvitation(owner, offline));
      assertTrue(harness.parties.acceptInvitation(offline));
      assertTrue(harness.parties.sendInvitation(owner, dead));
      assertTrue(harness.parties.acceptInvitation(dead));
      harness.parties.setOnline(offline, false);
      harness.parties.updateMember(dead, 1, 0, 100, 0, 100,
          D2LevelIds.LEVEL_DENOFEVIL, 0, 0, false);
      int monster = harness.createDenMonster(1f);
      harness.process();

      harness.setLife(monster, 0f);
      harness.events.dispatch(DeathEvent.obtain(owner, monster));

      assertTrue(NativeQuestRecord.has(record(ownerData), NativeQuestRecord.PRIMARY_GOAL_DONE));
      assertTrue(NativeQuestRecord.has(record(eligibleData), NativeQuestRecord.PRIMARY_GOAL_DONE));
      assertFalse(NativeQuestRecord.has(record(offlineData), NativeQuestRecord.PRIMARY_GOAL_DONE));
      assertFalse(NativeQuestRecord.has(record(deadData), NativeQuestRecord.PRIMARY_GOAL_DONE));
    } finally {
      harness.dispose();
    }
  }

  @Test
  void grantsAkaraSkillPointOnlyOnce() {
    Harness harness = new Harness();
    try {
      CharData data = character("RewardHero", Riiablo.NORMAL);
      data.getQuests(Riiablo.ACT1)[Act1DenOfEvilQuest.RECORD] =
          Act1DenOfEvilQuest.completeObjective((short) 0);
      int player = harness.createPlayer(data);
      int akara = harness.createAkara();
      harness.process();

      harness.events.dispatch(NpcQuestMessageEvent.obtain(
          player, akara, Act1DenOfEvilQuest.MESSAGE_SUCCESS));
      harness.events.dispatch(NpcQuestMessageEvent.obtain(
          player, akara, Act1DenOfEvilQuest.MESSAGE_SUCCESS));

      short claimed = record(data);
      assertTrue(NativeQuestRecord.has(claimed, NativeQuestRecord.REWARD_GRANTED));
      assertFalse(NativeQuestRecord.has(claimed, NativeQuestRecord.REWARD_PENDING));
      assertEquals(1, data.getStats().base().getValue(Stat.newskills, 0));
      assertEquals(1, data.getStats().aggregate().getValue(Stat.newskills, 0));
    } finally {
      harness.dispose();
    }
  }

  @Test
  void keepsCainRewardPendingWhenItemServiceCannotCreateReward() {
    Harness harness = new Harness();
    try {
      CharData data = character("CainRewardPending", Riiablo.NORMAL);
      data.getQuests(Riiablo.ACT1)[Act1CainQuest.RECORD] = Act1CainQuest.releaseCain((short) 0);
      int player = harness.createPlayer(data);
      int akara = harness.createAkara();
      harness.process();

      harness.events.dispatch(NpcQuestMessageEvent.obtain(
          player, akara, Act1CainQuest.MESSAGE_REWARD));

      short record = data.getQuests(Riiablo.ACT1)[Act1CainQuest.RECORD];
      assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
      assertFalse(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED));
    } finally {
      harness.dispose();
    }
  }

  @Test
  void CainTownMessageDoesNotTriggerAkaraReward() {
    Harness harness = new Harness();
    try {
      CharData data = character("CainDialogue", Riiablo.NORMAL);
      data.getQuests(Riiablo.ACT1)[Act1CainQuest.RECORD] = Act1CainQuest.releaseCain((short) 0);
      int player = harness.createPlayer(data);
      int cain = harness.createCainTown();
      harness.process();

      harness.events.dispatch(NpcQuestMessageEvent.obtain(
          player, cain, Act1CainQuest.MESSAGE_CAIN_TOWN));

      short record = data.getQuests(Riiablo.ACT1)[Act1CainQuest.RECORD];
      assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
      assertFalse(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED));
    } finally {
      harness.dispose();
    }
  }

  @Test
  void completesBloodRavenForPlayerInBurialGrounds() {
    Harness harness = new Harness();
    try {
      CharData data = character("BloodRavenHero", Riiablo.NORMAL);
      int player = harness.createPlayer(data);
      harness.setPlayerLevel(player, D2LevelIds.LEVEL_BURIALGROUNDS);
      int kashya = harness.createKashya();
      int bloodRaven = harness.createBloodRaven();
      harness.process();

      harness.events.dispatch(NpcQuestMessageEvent.obtain(
          player, kashya, Act1BloodRavenQuest.MESSAGE_INIT));
      harness.events.dispatch(DeathEvent.obtain(player, bloodRaven));

      short record = data.getQuests(Riiablo.ACT1)[Act1BloodRavenQuest.RECORD];
      assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.PRIMARY_GOAL_DONE));
      assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
      assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.COMPLETED_NOW));
    } finally {
      harness.dispose();
    }
  }

  @Test
  void propagatesBloodRavenCompletionToSamePartyOutsideBurialGrounds() {
    Harness harness = new Harness();
    try {
      CharData hunterData = character("BloodRavenHunter", Riiablo.NORMAL);
      CharData partyOutsideData = character("BloodRavenParty", Riiablo.NORMAL);
      CharData unrelatedData = character("BloodRavenUnrelated", Riiablo.NORMAL);
      int hunter = harness.createPlayer(hunterData);
      int partyOutside = harness.createPlayer(partyOutsideData);
      int unrelated = harness.createPlayer(unrelatedData);
      harness.setPlayerLevel(hunter, D2LevelIds.LEVEL_BURIALGROUNDS);
      harness.setPlayerLevel(partyOutside, D2LevelIds.LEVEL_BLOODMOOR);
      harness.setPlayerLevel(unrelated, D2LevelIds.LEVEL_BLOODMOOR);
      assertTrue(harness.parties.sendInvitation(hunter, partyOutside));
      assertTrue(harness.parties.acceptInvitation(partyOutside));
      int kashya = harness.createKashya();
      int bloodRaven = harness.createBloodRaven();
      harness.process();

      harness.events.dispatch(NpcQuestMessageEvent.obtain(
          hunter, kashya, Act1BloodRavenQuest.MESSAGE_INIT));
      harness.events.dispatch(DeathEvent.obtain(hunter, bloodRaven));

      short hunterRecord = hunterData.getQuests(Riiablo.ACT1)[Act1BloodRavenQuest.RECORD];
      short partyRecord = partyOutsideData.getQuests(Riiablo.ACT1)[Act1BloodRavenQuest.RECORD];
      short unrelatedRecord = unrelatedData.getQuests(Riiablo.ACT1)[Act1BloodRavenQuest.RECORD];
      assertTrue(NativeQuestRecord.has(hunterRecord, NativeQuestRecord.REWARD_PENDING));
      assertTrue(NativeQuestRecord.has(partyRecord, NativeQuestRecord.PRIMARY_GOAL_DONE));
      assertFalse(NativeQuestRecord.has(partyRecord, NativeQuestRecord.REWARD_PENDING));
      assertTrue(NativeQuestRecord.has(partyRecord, NativeQuestRecord.COMPLETED_NOW));
      assertFalse(NativeQuestRecord.has(unrelatedRecord, NativeQuestRecord.PRIMARY_GOAL_DONE));
      assertTrue(NativeQuestRecord.has(unrelatedRecord, NativeQuestRecord.COMPLETED_NOW));
    } finally {
      harness.dispose();
    }
  }

  @Test
  void KashyaRewardEmitsFreeRogueRequestWithoutCommittingEarly() {
    Harness harness = new Harness();
    try {
      CharData data = character("BloodRavenReward", Riiablo.NORMAL);
      data.getQuests(Riiablo.ACT1)[Act1BloodRavenQuest.RECORD] =
          Act1BloodRavenQuest.completeObjective((short) 0);
      int player = harness.createPlayer(data);
      int kashya = harness.createKashya();
      harness.process();

      harness.events.dispatch(NpcQuestMessageEvent.obtain(
          player, kashya, Act1BloodRavenQuest.MESSAGE_REWARD));

      short record = data.getQuests(Riiablo.ACT1)[Act1BloodRavenQuest.RECORD];
      assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
      assertFalse(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED));
    } finally {
      harness.dispose();
    }
  }

  @Test
  void commitsBloodRavenRewardAfterMercenaryServiceAcknowledges() {
    Harness harness = new Harness();
    try {
      CharData data = character("BloodRavenGranted", Riiablo.NORMAL);
      data.getQuests(Riiablo.ACT1)[Act1BloodRavenQuest.RECORD] =
          Act1BloodRavenQuest.completeObjective((short) 0);
      int player = harness.createPlayer(data);
      harness.process();

      harness.events.dispatch(NativeQuestRewardEvent.granted(player,
          QuestId.A1Q2_BLOOD_RAVEN, NativeQuestRewardEvent.BLOOD_RAVEN_FREE_ROGUE));

      short record = data.getQuests(Riiablo.ACT1)[Act1BloodRavenQuest.RECORD];
      assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_GRANTED));
      assertFalse(NativeQuestRecord.has(record, NativeQuestRecord.REWARD_PENDING));
    } finally {
      harness.dispose();
    }
  }

  @Test
  void towerTomeStartsCountessQuest() {
    Harness harness = new Harness();
    try {
      CharData data = character("TowerReader", Riiablo.NORMAL);
      int player = harness.createPlayer(data);
      harness.process();

      QuestObjectInteractionEvent tome = QuestObjectInteractionEvent.obtain(
          player, 80, NativeQuestObjectResolver.TOWER_TOME,
          NativeQuestObjectResolver.Type.TOWER_TOME);
      harness.events.dispatch(tome);

      assertTrue(tome.accepted);
      assertTrue(NativeQuestRecord.has(
          data.getQuests(Riiablo.ACT1)[Act1CountessQuest.RECORD],
          NativeQuestRecord.STARTED));
    } finally {
      harness.dispose();
    }
  }

  @Test
  void countessDeathRewardsPlayersInCellarAndEmitsTreasureRequest() {
    Harness harness = new Harness();
    try {
      CharData nearby = character("CountessHunter", Riiablo.NORMAL);
      CharData outside = character("CountessOutside", Riiablo.NORMAL);
      int hunter = harness.createPlayer(nearby);
      int observer = harness.createPlayer(outside);
      harness.setPlayerLevel(hunter, D2LevelIds.LEVEL_TOWERCELLARLVL5);
      harness.setPlayerLevel(observer, D2LevelIds.LEVEL_BLOODMOOR);
      int countess = harness.createCountess();
      harness.process();

      harness.events.dispatch(DeathEvent.obtain(hunter, countess));

      short hunterRecord = nearby.getQuests(Riiablo.ACT1)[Act1CountessQuest.RECORD];
      short observerRecord = outside.getQuests(Riiablo.ACT1)[Act1CountessQuest.RECORD];
      assertTrue(NativeQuestRecord.has(hunterRecord, NativeQuestRecord.REWARD_GRANTED));
      assertTrue(NativeQuestRecord.has(hunterRecord, NativeQuestRecord.PRIMARY_GOAL_DONE));
      assertTrue(NativeQuestRecord.has(observerRecord, NativeQuestRecord.COMPLETED_NOW));
      assertFalse(NativeQuestRecord.has(observerRecord, NativeQuestRecord.REWARD_GRANTED));
      assertEquals(1, harness.countessConsumer.requests);
      assertEquals(countess, harness.countessConsumer.victim);

      harness.events.dispatch(DeathEvent.obtain(hunter, countess));
      assertEquals(1, harness.countessConsumer.requests);
    } finally {
      harness.dispose();
    }
  }

  @Test
  void propagatesCountessRewardToAct1PartyMembersAndPreservesFinishedRecords() {
    Harness harness = new Harness();
    try {
      CharData hunterData = character("CountessHunterParty", Riiablo.NORMAL);
      CharData townData = character("CountessTownParty", Riiablo.NORMAL);
      CharData fieldData = character("CountessFieldParty", Riiablo.NORMAL);
      CharData act2Data = character("CountessAct2Party", Riiablo.NORMAL);
      CharData unrelatedData = character("CountessUnrelated", Riiablo.NORMAL);
      CharData finishedData = character("CountessAlreadyDone", Riiablo.NORMAL);
      int hunter = harness.createPlayer(hunterData);
      int townParty = harness.createPlayer(townData);
      int fieldParty = harness.createPlayer(fieldData);
      int act2Party = harness.createPlayer(act2Data);
      int unrelated = harness.createPlayer(unrelatedData);
      int alreadyFinished = harness.createPlayer(finishedData);
      harness.setPlayerLevel(hunter, D2LevelIds.LEVEL_TOWERCELLARLVL5);
      harness.setPlayerLevel(townParty, D2LevelIds.LEVEL_ROGUEENCAMPMENT);
      harness.setPlayerLevel(fieldParty, D2LevelIds.LEVEL_BLOODMOOR);
      harness.setPlayerLevel(act2Party, D2LevelIds.LEVEL_LUTGHOLEIN);
      harness.setPlayerLevel(unrelated, D2LevelIds.LEVEL_BLOODMOOR);
      harness.setPlayerLevel(alreadyFinished, D2LevelIds.LEVEL_BLOODMOOR);
      assertTrue(harness.parties.sendInvitation(hunter, townParty));
      assertTrue(harness.parties.acceptInvitation(townParty));
      assertTrue(harness.parties.sendInvitation(hunter, fieldParty));
      assertTrue(harness.parties.acceptInvitation(fieldParty));
      assertTrue(harness.parties.sendInvitation(hunter, act2Party));
      assertTrue(harness.parties.acceptInvitation(act2Party));
      finishedData.getQuests(Riiablo.ACT1)[Act1CountessQuest.RECORD] =
          NativeQuestRecord.set((short) 0, NativeQuestRecord.REWARD_GRANTED);

      int countess = harness.createCountess();
      harness.process();
      harness.events.dispatch(DeathEvent.obtain(hunter, countess));

      assertTrue(NativeQuestRecord.has(countessRecord(hunterData), NativeQuestRecord.REWARD_GRANTED));
      assertTrue(NativeQuestRecord.has(countessRecord(townData), NativeQuestRecord.REWARD_GRANTED),
          "Rogue Encampment is still Act I in D2MOO");
      assertTrue(NativeQuestRecord.has(countessRecord(fieldData), NativeQuestRecord.REWARD_GRANTED));
      assertFalse(NativeQuestRecord.has(countessRecord(act2Data), NativeQuestRecord.REWARD_GRANTED));
      assertTrue(NativeQuestRecord.has(countessRecord(act2Data), NativeQuestRecord.COMPLETED_NOW));
      assertFalse(NativeQuestRecord.has(countessRecord(unrelatedData), NativeQuestRecord.REWARD_GRANTED));
      assertTrue(NativeQuestRecord.has(countessRecord(unrelatedData), NativeQuestRecord.COMPLETED_NOW));
      assertTrue(NativeQuestRecord.has(countessRecord(finishedData), NativeQuestRecord.REWARD_GRANTED));
      assertFalse(NativeQuestRecord.has(countessRecord(townData), NativeQuestRecord.REWARD_PENDING));
      assertFalse(NativeQuestRecord.has(countessRecord(fieldData), NativeQuestRecord.REWARD_PENDING));
      assertEquals(1, harness.countessConsumer.requests);

      // A second death notification for the same super-unique is ignored and
      // cannot issue a second native treasure request or roll records back.
      harness.events.dispatch(DeathEvent.obtain(hunter, countess));
      assertEquals(1, harness.countessConsumer.requests);
      assertTrue(NativeQuestRecord.has(countessRecord(townData), NativeQuestRecord.REWARD_GRANTED));
    } finally {
      harness.dispose();
    }
  }

  @Test
  void propagatesAndarielRewardToAct1PartyAndMarksOtherActsComplete() {
    Harness harness = new Harness();
    try {
      CharData hunterData = character("AndarielHunter", Riiablo.NORMAL);
      CharData townData = character("AndarielTownParty", Riiablo.NORMAL);
      CharData fieldData = character("AndarielFieldParty", Riiablo.NORMAL);
      CharData act2Data = character("AndarielAct2Party", Riiablo.NORMAL);
      CharData unrelatedData = character("AndarielUnrelated", Riiablo.NORMAL);
      int hunter = harness.createPlayer(hunterData);
      int townParty = harness.createPlayer(townData);
      int fieldParty = harness.createPlayer(fieldData);
      int act2Party = harness.createPlayer(act2Data);
      int unrelated = harness.createPlayer(unrelatedData);
      harness.setPlayerLevel(hunter, D2LevelIds.LEVEL_CATACOMBSLVL4);
      harness.setPlayerLevel(townParty, D2LevelIds.LEVEL_ROGUEENCAMPMENT);
      harness.setPlayerLevel(fieldParty, D2LevelIds.LEVEL_BLOODMOOR);
      harness.setPlayerLevel(act2Party, D2LevelIds.LEVEL_LUTGHOLEIN);
      harness.setPlayerLevel(unrelated, D2LevelIds.LEVEL_BLOODMOOR);
      assertTrue(harness.parties.sendInvitation(hunter, townParty));
      assertTrue(harness.parties.acceptInvitation(townParty));
      assertTrue(harness.parties.sendInvitation(hunter, fieldParty));
      assertTrue(harness.parties.acceptInvitation(fieldParty));
      assertTrue(harness.parties.sendInvitation(hunter, act2Party));
      assertTrue(harness.parties.acceptInvitation(act2Party));
      int andariel = harness.createAndariel();
      harness.process();
      harness.events.dispatch(DeathEvent.obtain(hunter, andariel));

      assertTrue(NativeQuestRecord.has(andarielRecord(hunterData), NativeQuestRecord.PRIMARY_GOAL_DONE));
      assertTrue(NativeQuestRecord.has(andarielRecord(hunterData), NativeQuestRecord.REWARD_PENDING));
      assertTrue(NativeQuestRecord.has(andarielRecord(townData), NativeQuestRecord.PRIMARY_GOAL_DONE));
      assertTrue(NativeQuestRecord.has(andarielRecord(townData), NativeQuestRecord.REWARD_PENDING),
          "Rogue Encampment is part of Act I for A1Q6 party propagation");
      assertTrue(NativeQuestRecord.has(andarielRecord(fieldData), NativeQuestRecord.REWARD_PENDING));
      assertFalse(NativeQuestRecord.has(andarielRecord(act2Data), NativeQuestRecord.REWARD_PENDING));
      assertFalse(NativeQuestRecord.has(andarielRecord(act2Data), NativeQuestRecord.PRIMARY_GOAL_DONE));
      assertTrue(NativeQuestRecord.has(andarielRecord(act2Data), NativeQuestRecord.COMPLETED_NOW));
      assertTrue(NativeQuestRecord.has(andarielRecord(unrelatedData), NativeQuestRecord.COMPLETED_NOW));

      harness.events.dispatch(DeathEvent.obtain(hunter, andariel));
      assertTrue(NativeQuestRecord.has(andarielRecord(townData), NativeQuestRecord.REWARD_PENDING));
    } finally {
      harness.dispose();
    }
  }

  @Test
  void WarrivRewardIsAtomicAndIdempotent() {
    Harness harness = new Harness();
    try {
      CharData data = character("AndarielWarriv", Riiablo.NORMAL);
      int player = harness.createPlayer(data);
      harness.setPlayerLevel(player, D2LevelIds.LEVEL_ROGUEENCAMPMENT);
      int warriv = harness.createWarriv();
      data.getQuests(Riiablo.ACT1)[Act1AndarielQuest.RECORD] =
          Act1AndarielQuest.completePending((short) 0);
      harness.process();
      harness.events.dispatch(NpcQuestMessageEvent.obtain(
          player, warriv, Act1AndarielQuest.MESSAGE_WARRIV_REWARD));
      short claimed = andarielRecord(data);
      assertTrue(NativeQuestRecord.has(claimed, NativeQuestRecord.REWARD_GRANTED));
      assertFalse(NativeQuestRecord.has(claimed, NativeQuestRecord.REWARD_PENDING));
      assertEquals(1, harness.transitionConsumer.requests);
      assertEquals(D2LevelIds.LEVEL_LUTGHOLEIN, harness.transitionConsumer.destination);

      harness.events.dispatch(NpcQuestMessageEvent.obtain(
          player, warriv, Act1AndarielQuest.MESSAGE_WARRIV_REWARD));
      assertEquals(claimed, andarielRecord(data));
      assertEquals(1, harness.transitionConsumer.requests);
    } finally {
      harness.dispose();
    }
  }

  @Test
  void createsPersistableCainMagicAndRareRings() {
    ItemGenerator generator = new ItemGenerator();
    Item normal = generator.generateQuestReward("rin", 7, Quality.MAGIC, 0x1001);
    assertEquals("rin", normal.code);
    assertEquals(7, normal.ilvl);
    assertEquals(Quality.MAGIC, normal.quality);
    assertTrue(normal.qualityId != 0);

    Item nightmare = generator.generateQuestReward("rin", 30, Quality.RARE, 0x1002);
    assertEquals(30, nightmare.ilvl);
    assertEquals(Quality.RARE, nightmare.quality);
    assertNotNull(nightmare.qualityData);
    assertNotNull(nightmare.getNameString());

    ByteOutput encoded = ByteOutput.wrap(Unpooled.buffer());
    new ItemWriter().writeItem(nightmare, encoded);
    Item decoded = new ItemReader().readItem(ByteInput.wrap(encoded.buffer()));
    assertEquals("rin", decoded.code);
    assertEquals(30, decoded.ilvl);
    assertEquals(Quality.RARE, decoded.quality);
    assertNotNull(decoded.qualityData);
  }

  @Test
  void marksMalusRecordWhenQuestItemIsPickedUp() {
    Harness harness = new Harness();
    try {
      CharData data = character("MalusHero", Riiablo.NORMAL);
      int player = harness.createPlayer(data);
      harness.process();

      harness.events.dispatch(QuestItemPickedUpEvent.obtain(player, 77,
          Act1MalusQuest.MALUS_CODE));
      short record = data.getQuests(Riiablo.ACT1)[Act1MalusQuest.RECORD];
      assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.STARTED));
      assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.CUSTOM2));
    } finally {
      harness.dispose();
    }
  }

  @Test
  void acceptsMalusStandOnlyAtNativeMinimumLevel() {
    Harness harness = new Harness();
    try {
      CharData data = character("MalusLevelHero", Riiablo.NORMAL);
      data.getStats().base().put(Stat.level, 7);
      data.getStats().reset();
      int player = harness.createPlayer(data);
      harness.process();

      QuestObjectInteractionEvent tooLow = QuestObjectInteractionEvent.obtain(
          player, 70, 108, NativeQuestObjectResolver.Type.HORADRIC_MALUS);
      harness.events.dispatch(tooLow);
      assertFalse(tooLow.accepted);

      data.getStats().base().put(Stat.level, 8);
      data.getStats().reset();
      QuestObjectInteractionEvent eligible = QuestObjectInteractionEvent.obtain(
          player, 70, 108, NativeQuestObjectResolver.Type.HORADRIC_MALUS);
      harness.events.dispatch(eligible);
      assertTrue(eligible.accepted);
      short record = data.getQuests(Riiablo.ACT1)[Act1MalusQuest.RECORD];
      assertTrue(NativeQuestRecord.has(record, NativeQuestRecord.LEFT_TOWN));
    } finally {
      harness.dispose();
    }
  }

  @Test
  void propagatesMalusTurnInToEligibleAct1PartyMembersOnly() {
    Harness harness = new Harness();
    try {
      CharData turnInData = character("MalusTurnIn", Riiablo.NORMAL);
      CharData partyData = character("MalusParty", Riiablo.NORMAL);
      CharData act2Data = character("MalusAct2", Riiablo.NORMAL);
      CharData lowLevelData = character("MalusLow", Riiablo.NORMAL);
      short started = Act1MalusQuest.markMalusPickedUp((short) 0);
      turnInData.getQuests(Riiablo.ACT1)[Act1MalusQuest.RECORD] = started;
      partyData.getQuests(Riiablo.ACT1)[Act1MalusQuest.RECORD] = started;
      act2Data.getQuests(Riiablo.ACT1)[Act1MalusQuest.RECORD] = started;
      lowLevelData.getQuests(Riiablo.ACT1)[Act1MalusQuest.RECORD] = started;
      turnInData.getStats().base().put(Stat.level, 8);
      turnInData.getStats().reset();
      partyData.getStats().base().put(Stat.level, 8);
      partyData.getStats().reset();
      act2Data.getStats().base().put(Stat.level, 8);
      act2Data.getStats().reset();

      // The compact test table does not include the quest-only mdh row; use a
      // valid misc item shape and replace its code, which is all the quest
      // bridge needs for ownership/removal validation.
      Item malus = new ItemGenerator().generate("rin");
      assertNotNull(malus);
      malus.code = Act1MalusQuest.MALUS_CODE;
      assertTrue(turnInData.getItems().addToInventory(malus));

      int turnIn = harness.createPlayer(turnInData);
      int party = harness.createPlayer(partyData);
      int act2 = harness.createPlayer(act2Data);
      int low = harness.createPlayer(lowLevelData);
      int charsi = harness.createCharsi();
      harness.setPlayerLevel(turnIn, D2LevelIds.LEVEL_ROGUEENCAMPMENT);
      harness.setPlayerLevel(party, D2LevelIds.LEVEL_BLOODMOOR);
      harness.setPlayerLevel(act2, D2LevelIds.LEVEL_LUTGHOLEIN);
      harness.setPlayerLevel(low, D2LevelIds.LEVEL_BLOODMOOR);
      lowLevelData.getStats().base().put(Stat.level, 7);
      lowLevelData.getStats().reset();
      assertTrue(harness.parties.sendInvitation(turnIn, party));
      assertTrue(harness.parties.acceptInvitation(party));
      assertTrue(harness.parties.sendInvitation(turnIn, act2));
      assertTrue(harness.parties.acceptInvitation(act2));
      assertTrue(harness.parties.sendInvitation(turnIn, low));
      assertTrue(harness.parties.acceptInvitation(low));
      harness.process();

      harness.events.dispatch(NpcQuestMessageEvent.obtain(
          turnIn, charsi, Act1MalusQuest.MESSAGE_MALUS));

      short partyRecord = partyData.getQuests(Riiablo.ACT1)[Act1MalusQuest.RECORD];
      assertTrue(NativeQuestRecord.has(partyRecord, NativeQuestRecord.PRIMARY_GOAL_DONE));
      assertTrue(NativeQuestRecord.has(partyRecord, NativeQuestRecord.REWARD_PENDING));
      assertFalse(NativeQuestRecord.has(
          act2Data.getQuests(Riiablo.ACT1)[Act1MalusQuest.RECORD],
          NativeQuestRecord.REWARD_PENDING));
      assertFalse(NativeQuestRecord.has(
          lowLevelData.getQuests(Riiablo.ACT1)[Act1MalusQuest.RECORD],
          NativeQuestRecord.REWARD_PENDING));
    } finally {
      harness.dispose();
    }
  }

  @Test
  void releasesCainForEveryEligiblePlayerInTristram() {
    Harness harness = new Harness();
    try {
      CharData rescuerData = character("CainRescuer", Riiablo.NORMAL);
      CharData witnessData = character("CainWitness", Riiablo.NORMAL);
      rescuerData.getQuests(Riiablo.ACT1)[Act1CainQuest.RECORD] =
          Act1CainQuest.openTristramPortal((short) 0);
      witnessData.getQuests(Riiablo.ACT1)[Act1CainQuest.RECORD] =
          Act1CainQuest.openTristramPortal((short) 0);
      int rescuer = harness.createPlayer(rescuerData);
      int witness = harness.createPlayer(witnessData);
      harness.setPlayerLevel(rescuer, D2LevelIds.LEVEL_TRISTRAM);
      harness.setPlayerLevel(witness, D2LevelIds.LEVEL_TRISTRAM);
      harness.process();

      QuestObjectInteractionEvent gibbet = QuestObjectInteractionEvent.obtain(
          rescuer, 71, NativeQuestObjectResolver.CAIN_GIBBET,
          NativeQuestObjectResolver.Type.CAIN_GIBBET);
      harness.events.dispatch(gibbet);

      assertTrue(gibbet.accepted);
      assertTrue(NativeQuestRecord.has(
          rescuerData.getQuests(Riiablo.ACT1)[Act1CainQuest.RECORD],
          NativeQuestRecord.REWARD_PENDING));
      assertTrue(NativeQuestRecord.has(
          witnessData.getQuests(Riiablo.ACT1)[Act1CainQuest.RECORD],
          NativeQuestRecord.REWARD_PENDING));
    } finally {
      harness.dispose();
    }
  }

  @Test
  void propagatesCainReleaseToAct1PartyMembersButNotUnrelatedPlayers() {
    Harness harness = new Harness();
    try {
      CharData rescuerData = character("CainPartyRescuer", Riiablo.NORMAL);
      CharData tristramData = character("CainTristramWitness", Riiablo.NORMAL);
      CharData partyOutsideData = character("CainPartyOutside", Riiablo.NORMAL);
      CharData unrelatedData = character("CainUnrelated", Riiablo.NORMAL);
      short initial = Act1CainQuest.openTristramPortal((short) 0);
      rescuerData.getQuests(Riiablo.ACT1)[Act1CainQuest.RECORD] = initial;
      tristramData.getQuests(Riiablo.ACT1)[Act1CainQuest.RECORD] = initial;
      partyOutsideData.getQuests(Riiablo.ACT1)[Act1CainQuest.RECORD] = initial;
      unrelatedData.getQuests(Riiablo.ACT1)[Act1CainQuest.RECORD] = initial;

      int rescuer = harness.createPlayer(rescuerData);
      int witness = harness.createPlayer(tristramData);
      int partyOutside = harness.createPlayer(partyOutsideData);
      int unrelated = harness.createPlayer(unrelatedData);
      harness.setPlayerLevel(rescuer, D2LevelIds.LEVEL_TRISTRAM);
      harness.setPlayerLevel(witness, D2LevelIds.LEVEL_TRISTRAM);
      harness.setPlayerLevel(partyOutside, D2LevelIds.LEVEL_BLOODMOOR);
      harness.setPlayerLevel(unrelated, D2LevelIds.LEVEL_BLOODMOOR);
      assertTrue(harness.parties.sendInvitation(rescuer, partyOutside));
      assertTrue(harness.parties.acceptInvitation(partyOutside));
      harness.process();

      QuestObjectInteractionEvent gibbet = QuestObjectInteractionEvent.obtain(
          rescuer, 71, NativeQuestObjectResolver.CAIN_GIBBET,
          NativeQuestObjectResolver.Type.CAIN_GIBBET);
      harness.events.dispatch(gibbet);

      assertTrue(gibbet.accepted);
      assertTrue(NativeQuestRecord.has(
          rescuerData.getQuests(Riiablo.ACT1)[Act1CainQuest.RECORD],
          NativeQuestRecord.REWARD_PENDING));
      assertTrue(NativeQuestRecord.has(
          tristramData.getQuests(Riiablo.ACT1)[Act1CainQuest.RECORD],
          NativeQuestRecord.REWARD_PENDING));
      assertTrue(NativeQuestRecord.has(
          partyOutsideData.getQuests(Riiablo.ACT1)[Act1CainQuest.RECORD],
          NativeQuestRecord.REWARD_PENDING));
      assertFalse(NativeQuestRecord.has(
          unrelatedData.getQuests(Riiablo.ACT1)[Act1CainQuest.RECORD],
          NativeQuestRecord.REWARD_PENDING));
    } finally {
      harness.dispose();
    }
  }

  private static CharData character(String name, int difficulty) {
    CharData data = CharData.obtain().set(difficulty, false, name, Riiablo.AMAZON);
    data.getStats().base().put(Stat.newskills, 0);
    data.getStats().reset();
    return data;
  }

  private static short record(CharData data) {
    return data.getQuests(Riiablo.ACT1)[Act1DenOfEvilQuest.RECORD];
  }

  private static short countessRecord(CharData data) {
    return data.getQuests(Riiablo.ACT1)[Act1CountessQuest.RECORD];
  }

  private static short andarielRecord(CharData data) {
    return data.getQuests(Riiablo.ACT1)[Act1AndarielQuest.RECORD];
  }

  private static final class Harness {
    final EventSystem events = new EventSystem();
    final Act1QuestSystem quests = new Act1QuestSystem();
    final CainQuestConsumer cainConsumer = new CainQuestConsumer();
    final CountessQuestConsumer countessConsumer = new CountessQuestConsumer();
    final TransitionConsumer transitionConsumer = new TransitionConsumer();
    final PartyManager parties = new PartyManager();
    final World world = new World(new WorldConfigurationBuilder()
        .with(events, quests, cainConsumer, countessConsumer, transitionConsumer)
        .build()
        .register("partyManager", parties));

    int createPlayer(CharData data) {
      int entityId = world.create();
      world.getMapper(Player.class).create(entityId).data = data;
      world.getMapper(AttributesWrapper.class).create(entityId).attrs = data.getStats();
      return entityId;
    }

    int createAkara() {
      MonStats.Entry monstats = new MonStats.Entry();
      monstats.hcIdx = MonsterType.AKARA;
      int entityId = world.create();
      world.getMapper(Monster.class).create(entityId).monstats = monstats;
      return entityId;
    }

    int createCharsi() {
      MonStats.Entry monstats = new MonStats.Entry();
      monstats.hcIdx = MonsterType.CHARSI;
      int entityId = world.create();
      world.getMapper(Monster.class).create(entityId).monstats = monstats;
      return entityId;
    }

    int createKashya() {
      MonStats.Entry monstats = new MonStats.Entry();
      monstats.hcIdx = MonsterType.KASHYA;
      int entityId = world.create();
      world.getMapper(Monster.class).create(entityId).monstats = monstats;
      return entityId;
    }

    int createBloodRaven() {
      Levels.Entry level = new Levels.Entry();
      level.Id = D2LevelIds.LEVEL_BURIALGROUNDS;
      Map.Zone zone = new Map.Zone();
      zone.level = level;
      MonStats.Entry monstats = new MonStats.Entry();
      monstats.hcIdx = MonsterType.BLOODRAVEN;
      int entityId = world.create();
      world.getMapper(Monster.class).create(entityId).monstats = monstats;
      world.getMapper(MapWrapper.class).create(entityId).zone = zone;
      return entityId;
    }

    int createCountess() {
      Levels.Entry level = new Levels.Entry();
      level.Id = D2LevelIds.LEVEL_TOWERCELLARLVL5;
      Map.Zone zone = new Map.Zone();
      zone.level = level;
      MonStats.Entry monstats = new MonStats.Entry();
      monstats.hcIdx = 45;
      int entityId = world.create();
      world.getMapper(Monster.class).create(entityId).monstats = monstats;
      world.getMapper(SuperUnique.class).create(entityId).id =
          D2SuperUniques.SUPERUNIQUE_THE_COUNTESS;
      world.getMapper(MapWrapper.class).create(entityId).zone = zone;
      return entityId;
    }

    int createAndariel() {
      Levels.Entry level = new Levels.Entry();
      level.Id = D2LevelIds.LEVEL_CATACOMBSLVL4;
      Map.Zone zone = new Map.Zone();
      zone.level = level;
      MonStats.Entry monstats = new MonStats.Entry();
      monstats.hcIdx = MonsterType.ANDARIEL;
      int entityId = world.create();
      world.getMapper(Monster.class).create(entityId).monstats = monstats;
      world.getMapper(MapWrapper.class).create(entityId).zone = zone;
      return entityId;
    }

    int createWarriv() {
      MonStats.Entry monstats = new MonStats.Entry();
      monstats.hcIdx = MonsterType.WARRIV;
      int entityId = world.create();
      world.getMapper(Monster.class).create(entityId).monstats = monstats;
      return entityId;
    }

    int createCainTown() {
      MonStats.Entry monstats = new MonStats.Entry();
      monstats.hcIdx = MonsterType.DECKARDCAIN_TOWN;
      int entityId = world.create();
      world.getMapper(Monster.class).create(entityId).monstats = monstats;
      return entityId;
    }

    void setPlayerLevel(int entityId, int levelId) {
      Levels.Entry level = new Levels.Entry();
      level.Id = levelId;
      Map.Zone zone = new Map.Zone();
      zone.level = level;
      world.getMapper(MapWrapper.class).create(entityId).zone = zone;
    }

    int createDenMonster(float life) {
      Levels.Entry level = new Levels.Entry();
      level.Id = D2LevelIds.LEVEL_DENOFEVIL;
      Map.Zone zone = new Map.Zone();
      zone.level = level;

      int entityId = world.create();
      world.getMapper(Monster.class).create(entityId);
      world.getMapper(MapWrapper.class).create(entityId).zone = zone;
      Attributes attrs = Attributes.obtainStandard();
      attrs.base().put(Stat.hitpoints, life);
      attrs.reset();
      world.getMapper(AttributesWrapper.class).create(entityId).attrs = attrs;
      return entityId;
    }

    int createMercenary(int ownerId) {
      int entityId = world.create();
      world.getMapper(Mercenary.class).create(entityId).ownerId = ownerId;
      return entityId;
    }

    void setLife(int entityId, float life) {
      Attributes attrs = world.getMapper(AttributesWrapper.class).get(entityId).attrs;
      attrs.get(Stat.hitpoints).set(life);
    }

    void process() {
      world.process();
    }

    void dispose() {
      world.dispose();
    }
  }

  private static final class CainQuestConsumer extends PassiveSystem {
    @Subscribe
    public void onCainQuest(NativeCainQuestEvent event) {
      if (event != null && event.action == NativeCainQuestEvent.CAIN_GIBBET) event.accept();
    }
  }

  private static final class CountessQuestConsumer extends PassiveSystem {
    int requests;
    int victim = -1;

    @Subscribe
    public void onCountessQuest(NativeCountessQuestEvent event) {
      requests++;
      victim = event.countessId;
    }
  }

  private static final class TransitionConsumer extends PassiveSystem {
    int requests;
    int destination = -1;

    @Subscribe
    public void onTransition(NativeActTransitionEvent event) {
      requests++;
      destination = event.destinationLevelId;
      event.accept();
    }
  }
}
