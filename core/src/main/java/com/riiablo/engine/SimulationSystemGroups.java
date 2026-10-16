package com.riiablo.engine;

import com.artemis.BaseSystem;
import com.riiablo.engine.server.AIStepper;
import com.riiablo.engine.server.Actioneer;
import com.riiablo.engine.server.AnimStepper;
import com.riiablo.engine.server.AssassinTrapSystem;
import com.riiablo.engine.server.BoneWallCollisionSystem;
import com.riiablo.engine.server.ManaRecoverySystem;
import com.riiablo.engine.server.MercenaryFollowSystem;
import com.riiablo.engine.server.MercenaryPotionSystem;
import com.riiablo.engine.server.MercenarySkillSystem;
import com.riiablo.engine.server.MissileCollisionSystem;
import com.riiablo.engine.server.NativePlayerTargetSlots;
import com.riiablo.engine.server.ObjectCollisionUpdater;
import com.riiablo.engine.server.PotionRecoverySystem;
import com.riiablo.engine.server.RoomActivationSystem;
import com.riiablo.engine.server.RoomEntityTrackingSystem;
import com.riiablo.engine.server.SequenceHandler;
import com.riiablo.engine.server.ServerMonsterCorpseSystem;
import com.riiablo.engine.server.StaminaSystem;
import com.riiablo.engine.server.StateUpdater;
import com.riiablo.engine.server.SummonedPetSystem;
import com.riiablo.engine.server.UnitLifecycleSystem;
import com.riiablo.engine.server.item.AuthoritativeItemMoveService;
import com.riiablo.engine.server.object.NativeObjectDropSystem;
import com.riiablo.engine.server.object.NativeShrineEffectSystem;
import com.riiablo.engine.server.object.NativeShrineSystem;
import com.riiablo.engine.server.object.NativeTrapFireSystem;
import com.riiablo.engine.server.object.NativeTrapSystem;
import com.riiablo.engine.server.quest.Act1QuestSystem;
import com.riiablo.engine.server.quest.Act2DurielQuestSystem;
import com.riiablo.engine.server.quest.Act2HoradricStaffDropSystem;
import com.riiablo.engine.server.quest.Act2QuestSystem;
import com.riiablo.engine.server.quest.Act2TaintedSunQuestSystem;
import com.riiablo.engine.server.quest.Act3MephistoQuestSystem;
import com.riiablo.engine.server.quest.Act3QuestSystem;
import com.riiablo.engine.server.quest.Act4QuestSystem;
import com.riiablo.engine.server.quest.Act5QuestSystem;
import com.riiablo.engine.server.quest.NativeCharsiImbueSystem;
import com.riiablo.engine.server.quest.NativeCountessRewardSystem;
import com.riiablo.engine.server.quest.NativeMercenaryRewardSystem;

/**
 * Shared, ordered registration groups for local games and dedicated servers.
 *
 * <p>Each call creates session-owned systems. Pass the returned array directly
 * to {@code WorldConfigurationBuilder.with}; Artemis plugins are deliberately
 * avoided because their deferred installation would move systems to build time.
 * The composition roots still choose where each group runs: their existing
 * schedules differ, and grouping must not silently reorder a fixed tick.
 *
 * <p>Authority-only groups (quests, owned units, quest item rewards, world object
 * effects and room population) must not be installed in a remote client.
 * State/actions, lifecycle, animation and collision retain the existing replica
 * registration as well. Presentation, transport, physics and mode-specific
 * skill execution remain explicit in the composition roots.
 */
public final class SimulationSystemGroups {
  private SimulationSystemGroups() {}

  /** State/stat decay precedes missile collision and queued unit actions. */
  public static BaseSystem[] stateAndActions() {
    return new BaseSystem[] {
        new StateUpdater(),
        new StaminaSystem(),
        new ManaRecoverySystem(),
        new PotionRecoverySystem(),
        new MercenaryPotionSystem(),
        new MissileCollisionSystem(),
        new Actioneer()
    };
  }

  public static BaseSystem[] unitLifecycle() {
    return new BaseSystem[] {
        new ServerMonsterCorpseSystem(),
        new UnitLifecycleSystem(),
        new NativePlayerTargetSlots()
    };
  }

  /** Quest state and the free mercenary reward share one authority boundary. */
  public static BaseSystem[] quests() {
    return new BaseSystem[] {
        new Act1QuestSystem(),
        new Act2QuestSystem(),
        new Act3QuestSystem(),
        new Act3MephistoQuestSystem(),
        new Act4QuestSystem(),
        new Act5QuestSystem(),
        new Act2TaintedSunQuestSystem(),
        new Act2DurielQuestSystem(),
        new NativeMercenaryRewardSystem()
    };
  }

  /** Follow/lifetime updates and skill selection must both be installed. */
  public static BaseSystem[] ownedUnits() {
    return new BaseSystem[] {
        new MercenaryFollowSystem(),
        new SummonedPetSystem(),
        new AssassinTrapSystem(),
        new MercenarySkillSystem()
    };
  }

  public static BaseSystem[] questItemRewards() {
    return new BaseSystem[] {
        new NativeCountessRewardSystem(),
        new NativeCharsiImbueSystem()
    };
  }

  /**
   * @param authoritativeItems dedicated-server inventory revisions; null keeps
   *     the existing local inventory path
   */
  public static BaseSystem[] worldObjectEffects(AuthoritativeItemMoveService authoritativeItems) {
    return new BaseSystem[] {
        new NativeObjectDropSystem(),
        new Act2HoradricStaffDropSystem(),
        new NativeShrineEffectSystem(authoritativeItems),
        new NativeShrineSystem(),
        new NativeTrapSystem(),
        new NativeTrapFireSystem()
    };
  }

  /** Room tracking/activation must precede monster AI. */
  public static BaseSystem[] roomPopulation(boolean traceAiTour) {
    return new BaseSystem[] {
        new RoomEntityTrackingSystem(),
        new RoomActivationSystem(),
        new AIStepper(traceAiTour)
    };
  }

  /** Select the queued mode before advancing its animation/keyframes. */
  public static BaseSystem[] sequenceAnimation() {
    return new BaseSystem[] {new SequenceHandler(), new AnimStepper()};
  }

  /** Bone Wall/Prison use unit footprints rather than map-object footprints. */
  public static BaseSystem[] objectCollision() {
    return new BaseSystem[] {new ObjectCollisionUpdater(), new BoneWallCollisionSystem()};
  }
}
