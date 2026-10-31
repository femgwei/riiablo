package com.riiablo.engine.server.quest;

import com.riiablo.engine.server.monster.MonsterType;
import static com.riiablo.engine.server.quest.NativeQuestRecord.*;

/** Shared A2-A5 dialogue/attention policy for the implemented authoritative branches.
 * Native references: ACT2Q1/2/5/6, ACT3Q3/4, ACT4Q1/2/3, ACT5Q1..6.
 * Session fState is not inferred to be identical to persisted character flags.
 * Act I retains its verified controller and marker policy. */
public final class QuestDialogue {
  private QuestDialogue() {}

  public static final class Offer {
    public final int message;
    public final boolean attention;
    private Offer(int message, boolean attention) {
      this.message = message;
      this.attention = attention;
    }
  }

  private static Offer offer(int message, boolean attention) {
    return message < 0 ? null : new Offer(message, attention);
  }

  private static boolean finished(short record) {
    return has(record, REWARD_GRANTED) || has(record, COMPLETED_BEFORE);
  }

  private static boolean owns(QuestPlayer player, String code) {
    return player.items() != null && player.items().containsItemCode(code);
  }

  /** NPCs with implemented non-A1 quest branches, including replay-only menus. */
  public static boolean supportsNpc(int npc) {
    if (summonerMessage(npc) >= 0) return true;
    switch (npc) {
      case MonsterType.TYRAEL1: case 257:
      case MonsterType.CAIN3: case MonsterType.MESHIF2: case MonsterType.ALKOR:
      case MonsterType.HRATLI: case MonsterType.ORMUS: case MonsterType.ASHEARA:
      case MonsterType.TYRAEL2: case MonsterType.CAIN4:
      case MonsterType.LARZUK: case MonsterType.QUAL_KEHK: case MonsterType.MALAH:
      case MonsterType.DREHYA: case Act5BaalQuest.TYRAEL3_CLASS:
        return true;
      default: return false;
    }
  }

  public static Offer select(int npc, QuestPlayer player, int levelId) {
    if (player == null) return null;
    short jerhynIntro = player.record(1, Act2JerhynIntroQuest.RECORD);
    if (npc == MonsterType.JERHYN && Act2JerhynIntroQuest.isAvailable(jerhynIntro))
      return offer(Act2JerhynIntroQuest.MESSAGE, true);
    short hratliIntro = player.record(2, Act3HratliIntroQuest.RECORD);
    if (npc == MonsterType.HRATLI && Act3HratliIntroQuest.isAvailable(hratliIntro))
      return offer(Act3HratliIntroQuest.MESSAGE, true);
    short radament = player.record(1, Act2RadamentQuest.RECORD);
    if (npc == MonsterType.ATMA && !finished(radament)) {
      boolean attention = !has(radament, STARTED) || has(radament, REWARD_PENDING);
      if (attention || !has(player.record(1, Act2SummonerQuest.RECORD), REWARD_PENDING))
        return offer(Act2RadamentQuest.selectAtmaMessage(radament), attention);
    }
    if (npc == MonsterType.DECKARDCAIN_ACT2) {
      short staff = player.record(1, Act2HoradricStaffQuest.RECORD);
      int message = Act2HoradricStaffQuest.selectCainMessage(staff, player.items());
      if (!finished(staff) && !has(staff, REWARD_PENDING) && message >= 0
          && (message != Act2HoradricStaffQuest.MESSAGE_ASSEMBLED || !has(staff, CUSTOM6))) {
        return offer(message, true);
      }
    }
    short duriel = player.record(1, Act2DurielQuest.RECORD);
    if ((npc == MonsterType.TYRAEL1 || npc == 257)
        && Act2DurielQuest.canAcceptTyraelPortal(duriel)) {
      return offer(Act2DurielQuest.MESSAGE_TYRAEL_PORTAL, !has(duriel, PRIMARY_GOAL_DONE));
    }
    if (npc == MonsterType.JERHYN && has(duriel, LEFT_TOWN)) {
      return offer(Act2DurielQuest.MESSAGE_JERHYN_END, true);
    }
    if (npc == MonsterType.MESHIF1 && has(duriel, ENTERED_AREA)) {
      return offer(Act2DurielQuest.MESSAGE_MESHIF_TRAVEL, true);
    }
    if (has(player.record(1, Act2SummonerQuest.RECORD), REWARD_PENDING)) {
      int message = summonerMessage(npc);
      if (message >= 0) return offer(message, true);
    }
    if (npc == MonsterType.JERHYN && Act2DurielQuest.canAcceptJerhynEnd(duriel))
      return offer(Act2DurielQuest.MESSAGE_JERHYN_END, false);
    if (npc == MonsterType.MESHIF1 && Act2DurielQuest.canAcceptMeshifTravel(duriel))
      return offer(Act2DurielQuest.MESSAGE_MESHIF_TRAVEL, false);

    short bird = player.record(2, Act3GoldenBirdQuest.RECORD);
    if (!finished(bird)) {
      if (npc == MonsterType.CAIN3 && Act3GoldenBirdQuest.canProgress(bird)) {
        if (owns(player, Act3GoldenBirdQuest.JADE_FIGURINE) && !has(bird, STARTED))
          return offer(Act3GoldenBirdQuest.MESSAGE_CAIN_INIT, true);
        if (owns(player, Act3GoldenBirdQuest.GOLDEN_BIRD) && !has(bird, ENTERED_AREA))
          return offer(Act3GoldenBirdQuest.MESSAGE_CAIN_ENTERED_AREA, true);
      }
      if (npc == MonsterType.MESHIF2 && Act3GoldenBirdQuest.canProgress(bird)
          && owns(player, Act3GoldenBirdQuest.JADE_FIGURINE))
        return offer(Act3GoldenBirdQuest.MESSAGE_MESHIF_EXCHANGE, true);
      if (npc == MonsterType.ALKOR) {
        if (has(bird, REWARD_PENDING)) return offer(Act3GoldenBirdQuest.MESSAGE_ALKOR_REWARD, true);
        if (owns(player, Act3GoldenBirdQuest.GOLDEN_BIRD))
          return offer(Act3GoldenBirdQuest.MESSAGE_ALKOR_RECEIVE, true);
      }
    }
    short gidbinn = player.record(2, Act3GidbinnQuest.RECORD);
    if (!finished(gidbinn)) {
      if (npc == MonsterType.HRATLI && gidbinn == 0 && !owns(player, Act3GidbinnQuest.GIDBINN))
        return offer(Act3GidbinnQuest.MESSAGE_HRATLI_INIT, true);
      if (npc == MonsterType.ORMUS) {
        if (!has(gidbinn, CUSTOM2) && owns(player, Act3GidbinnQuest.GIDBINN))
          return offer(Act3GidbinnQuest.MESSAGE_ORMUS_TURN_IN, true);
        if (has(gidbinn, CUSTOM2) && !has(gidbinn, CUSTOM4))
          return offer(Act3GidbinnQuest.MESSAGE_ORMUS_REWARD, true);
      }
      if (npc == MonsterType.ASHEARA && has(gidbinn, CUSTOM2) && !has(gidbinn, CUSTOM3))
        return offer(Act3GidbinnQuest.MESSAGE_ASHEARA_REWARD, true);
    }

    short izual = player.record(3, Act4IzualQuest.RECORD);
    short diablo = player.record(3, Act4DiabloQuest.RECORD);
    if (npc == MonsterType.TYRAEL2) {
      if (Act4IzualQuest.canClaimReward(izual)) return offer(Act4IzualQuest.MESSAGE_TYRAEL_REWARD, true);
      if (player.expansion() && has(diablo, PRIMARY_GOAL_DONE))
        return offer(Act4DiabloQuest.MESSAGE_TYRAEL_ACT5, !finished(diablo));
      if (!finished(izual)) return offer(Act4IzualQuest.MESSAGE_TYRAEL_INIT, izual == 0);
    }
    if (npc == MonsterType.CAIN4) {
      short forge = player.record(3, Act4HellforgeQuest.RECORD);
      if (!finished(forge)) return offer(Act4HellforgeQuest.selectCainMessage(forge,
          owns(player, Act4HellforgeQuest.SOULSTONE)),
          Act4HellforgeQuest.canClaimReward(forge) || forge == 0);
    }
    if (!player.expansion()) return null;
    short shenk = player.record(4, Act5ShenkQuest.RECORD);
    short rescue = player.record(4, Act5RescueQuest.RECORD);
    short prison = player.record(4, Act5PrisonQuest.RECORD);
    short nihlathak = player.record(4, Act5NihlathakQuest.RECORD);
    short ancients = player.record(4, Act5AncientsQuest.RECORD);
    if (npc == MonsterType.LARZUK) {
      if (Act5ShenkQuest.canClaimReward(shenk)) return offer(Act5ShenkQuest.MESSAGE_LARZUK_REWARD, true);
      if (shenk == 0) return offer(Act5ShenkQuest.MESSAGE_LARZUK_INIT, true);
    }
    if (npc == MonsterType.QUAL_KEHK) {
      if (Act5RescueQuest.canClaimReward(rescue)) return offer(Act5RescueQuest.MESSAGE_QUAL_KEHK_REWARD, true);
      if (rescue == 0) return offer(Act5RescueQuest.MESSAGE_QUAL_KEHK_INIT, true);
      if (ancients == 0 && (has(nihlathak, REWARD_PENDING) || finished(nihlathak)))
        return offer(Act5AncientsQuest.MESSAGE_QUAL_KEHK_START, true);
    }
    if (npc == MonsterType.MALAH) {
      if (Act5PrisonQuest.canClaimReward(prison)) return offer(Act5PrisonQuest.MESSAGE_MALAH_REWARD, true);
      if (prison == 0) return offer(Act5PrisonQuest.MESSAGE_MALAH_INIT, true);
    }
    if (npc == MonsterType.DREHYA) {
      if (Act5NihlathakQuest.canClaimReward(nihlathak)) return offer(Act5NihlathakQuest.MESSAGE_DREHYA_REWARD, true);
      boolean prisonDone = finished(prison) || has(prison, REWARD_PENDING);
      if (prisonDone && !finished(nihlathak) && !has(nihlathak, REWARD_PENDING))
        return offer(Act5NihlathakQuest.MESSAGE_DREHYA_START, nihlathak == 0);
    }
    if (Act5BaalQuest.isTyrael3(npc, null)
        && Act5BaalQuest.canTriggerLastPortal(player.record(4, Act5BaalQuest.RECORD),
            levelId, Act5BaalQuest.MESSAGE_TYRAEL)) return offer(Act5BaalQuest.MESSAGE_TYRAEL,
                !has(player.record(4, Act5BaalQuest.RECORD), CUSTOM3));
    return null;
  }

  /** A2Q5's NPC/message pairs, not a permissive numeric range. */
  public static int summonerMessage(int npc) {
    switch (npc) {
      case MonsterType.GREIZ: return 419;
      case MonsterType.GEGLASH: return 420;
      case MonsterType.JERHYN: return 421;
      case MonsterType.DROGNAN: return 422;
      case MonsterType.ELZIX: return 423;
      case MonsterType.WARRIV2: return 424;
      case MonsterType.MESHIF1: return 425;
      case MonsterType.LYSANDER: return 426;
      case MonsterType.ATMA: return 427;
      case MonsterType.FARA: return 428;
      case MonsterType.DECKARDCAIN_ACT2: return 429;
      default: return -1;
    }
  }
}
