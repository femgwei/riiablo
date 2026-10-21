package com.riiablo.server.d2gs;

import com.artemis.WorldConfigurationBuilder;
import com.riiablo.attributes.ExperienceManager;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.SimulationMode;
import com.riiablo.engine.SimulationSession;
import com.riiablo.engine.SimulationSystemGroups;
import com.riiablo.engine.server.AnimDataResolver;
import com.riiablo.engine.server.AuraEcsSystem;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.DeathRewardSystem;
import com.riiablo.engine.server.ItemInteractor;
import com.riiablo.engine.server.ItemManager;
import com.riiablo.engine.server.LeapSystem;
import com.riiablo.engine.server.ObjectInitializer;
import com.riiablo.engine.server.ObjectInteractor;
import com.riiablo.engine.server.Pathfinder;
import com.riiablo.engine.server.PlayerCorpseRetrievalSystem;
import com.riiablo.engine.server.SerializationManager;
import com.riiablo.engine.server.ServerNetworkIdManager;
import com.riiablo.engine.server.ServerPlayerDeathSystem;
import com.riiablo.engine.server.VelocityAdder;
import com.riiablo.engine.server.WarpInteractor;
import com.riiablo.engine.server.item.AuthoritativeItemMoveService;
import com.riiablo.engine.server.npc.NpcVendorSessionManager;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.VendorGenerator;
import com.riiablo.map.MapManager;
import java.util.Objects;
import net.mostlyoriginal.api.event.common.EventSystem;

/**
 * Dedicated server's ordered recipe; deliberately not the client's tick order.
 * Does not start networking, create a World, register host aliases or own resources.
 */
final class D2GSSimulationAssembly {
  private D2GSSimulationAssembly() {}

  static WorldConfigurationBuilder builder(SimulationSession session, MapManager mapManager,
      ItemManager itemManager, EntityFactory factory, NetworkSynchronizer sync,
      NpcVendorSessionManager npcVendors, AuthoritativeItemMoveService authoritativeItems,
      boolean traceAiTour) {
    Objects.requireNonNull(session, "session");
    Objects.requireNonNull(mapManager, "mapManager");
    Objects.requireNonNull(itemManager, "itemManager");
    Objects.requireNonNull(factory, "factory");
    Objects.requireNonNull(sync, "sync");
    Objects.requireNonNull(npcVendors, "npcVendors");
    Objects.requireNonNull(authoritativeItems, "authoritativeItems");
    if (session.mode() != SimulationMode.DEDICATED_SERVER) {
      throw new IllegalArgumentException("D2GS assembly requires dedicated server mode");
    }
    SimulationMode mode = session.mode();
    WorldConfigurationBuilder builder = new WorldConfigurationBuilder()
        .with(new EventSystem())
        .with(new com.riiablo.engine.server.npc.NpcVendorSessionSystem(npcVendors))
        // Zone changes must invalidate owner-bound missiles and periodic
        // state layers before the next snapshot baseline is serialized.
        .with(new com.riiablo.engine.server.ZoneTransitionCleanupSystem())
        .with(SimulationSystemGroups.quests())
        .with(SimulationSystemGroups.questItemRewards())
        .with(new ServerNetworkIdManager())
        .with(new SerializationManager())
        .with(mapManager)
        .with(itemManager)
        .with(new ItemGenerator())
        .with(new CofManager())
        .with(new com.riiablo.engine.server.EnvironmentSystem(session.map(), session.environment()))
        .with(new ObjectInitializer())
        .with(new com.riiablo.engine.server.EnvironmentObjectSystem(session.environment()))
        .with(new ObjectInteractor(), new WarpInteractor(), new ItemInteractor())
        .with(SimulationSystemGroups.worldObjectEffects(authoritativeItems))

        // Native 25 Hz phase order: state/stat decay first, then projectile
        // movement and collision, then unit/AI behavior and death handlers.
        .with(SimulationSystemGroups.stateAndActions())
        .with(SimulationSystemGroups.ownedUnits())
        .with(SimulationSystemGroups.unitLifecycle())
        .with(new com.riiablo.engine.server.item.GroundDropCleanupSystem())
        .with(new AuraEcsSystem())
        .with(new ServerPlayerDeathSystem())
        .with(new PlayerCorpseRetrievalSystem())
        .with(mode.createServerSkillSystem())
        .with(new DeathRewardSystem(session.partyManager()))
        // Apply a newly queued attack mode before advancing animation. If the
        // old neutral animation wraps first, its Finished event can otherwise
        // consume the fresh attack sequence before the attack keyframe runs.
        .with(SimulationSystemGroups.sequenceAnimation())
        .with(SimulationSystemGroups.objectCollision())
        .with(new com.riiablo.engine.server.DruidShapeShiftResolver())
        .with(new com.riiablo.engine.server.item.NativeItemQuantityRegenSystem())
        .with(new ExperienceManager(session.partyManager()))
        .with(new com.riiablo.engine.server.party.PartyMemberSyncSystem(session.partyManager()))

        .with(new VendorGenerator())
        .with(SimulationSystemGroups.roomPopulation(traceAiTour))
        .with(mode.createDynamicUnitCollisionSystem())
        .with(new Pathfinder())

        .with(new VelocityAdder()) // FIXME: temp until proper physics implemented

        // Resolve modes after collision-aware movement has replaced a blocked
        // requested velocity with zero. D2GS remains the authoritative owner
        // of player and monster movement modes.
        .with(mode.createVelocityModeChanger())
        .with(new com.riiablo.engine.server.WhirlwindSystem())
        .with(new LeapSystem())

        .with(factory)
        .with(sync)
        .with(new AnimDataResolver())
        ;
    return builder;
  }
}
