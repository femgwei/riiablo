package com.riiablo.engine.client;

import com.riiablo.presentation.Act1QuestDialogController;
import com.riiablo.presentation.Act1QuestIndicatorSystem;
import com.riiablo.presentation.QuestDialogController;
import com.riiablo.presentation.DialogManager;
import com.riiablo.presentation.MenuManager;
import com.riiablo.presentation.LabelManager;
import com.riiablo.presentation.MonsterLabelManager;
import com.riiablo.presentation.ZoneEntryDisplayer;

import com.artemis.WorldConfigurationBuilder;
import com.artemis.managers.TagManager;
import com.riiablo.attributes.ExperienceManager;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.SimulationClock;
import com.riiablo.engine.SimulationMode;
import com.riiablo.engine.SimulationSession;
import com.riiablo.engine.SimulationSystemGroups;
import com.riiablo.engine.client.debug.Box2DDebugger;
import com.riiablo.engine.client.debug.PathDebugger;
import com.riiablo.engine.client.debug.PathfindDebugger;
import com.riiablo.engine.client.debug.RenderSystemDebugger;
import com.riiablo.engine.server.AngularVelocity;
import com.riiablo.engine.server.AnimDataResolver;
import com.riiablo.engine.server.Box2DDisposer;
import com.riiablo.engine.server.Box2DSynchronizerPost;
import com.riiablo.engine.server.Box2DSynchronizerPre;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.DeathRewardSystem;
import com.riiablo.engine.server.ItemInteractor;
import com.riiablo.engine.server.ItemManager;
import com.riiablo.engine.server.LeapSystem;
import com.riiablo.engine.server.ObjectInitializer;
import com.riiablo.engine.server.ObjectInteractor;
import com.riiablo.engine.server.Pathfinder;
import com.riiablo.engine.server.PlayerCorpseRetrievalSystem;
import com.riiablo.engine.server.PlayerItemHandler;
import com.riiablo.engine.server.WarpInteractor;
import com.riiablo.engine.server.ZoneMovementModesChanger;
import com.riiablo.engine.server.combat.CombatPositionCaptureSystem;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.VendorGenerator;
import com.riiablo.map.Box2DPhysics;
import com.riiablo.map.MapManager;
import com.riiablo.map.RenderSystem;
import com.riiablo.profiler.ProfilerPlugin;
import java.util.Objects;
import net.mostlyoriginal.api.event.common.EventSystem;

/**
 * Ordered client world recipe. Hosts retain resource ownership and platform selection.
 * Each call creates fresh systems except for the explicitly supplied host systems.
 * The returned builder remains extensible by NetworkedGameScreen before build().
 */
public final class ClientSimulationAssembly {
  private ClientSimulationAssembly() {}

  public static WorldConfigurationBuilder builder(SimulationSession simulationSession,
      ClientAudioSession clientAudioSession, MapManager mapManager,
      ClientItemManager itemController, EntityFactory factory, RenderSystem renderer,
      boolean desktopInput, boolean autoInteract) {
    Objects.requireNonNull(simulationSession, "simulationSession");
    Objects.requireNonNull(clientAudioSession, "clientAudioSession");
    Objects.requireNonNull(mapManager, "mapManager");
    Objects.requireNonNull(itemController, "itemController");
    Objects.requireNonNull(factory, "factory");
    Objects.requireNonNull(renderer, "renderer");
    SimulationMode mode = simulationSession.mode();
    if (mode == SimulationMode.DEDICATED_SERVER) {
      throw new IllegalArgumentException("Client assembly requires a client mode");
    }
    if (clientAudioSession.environment() != simulationSession.environment()) {
      throw new IllegalArgumentException("Audio and simulation must share the same environment");
    }
    WorldConfigurationBuilder builder = new WorldConfigurationBuilder()
        // Must precede input, AI and animation keyframes in every fixed tick.
        .with(new CombatPositionCaptureSystem())
        .with(new NetworkIdManager())
        .with(new EventSystem())
        .with(new TagManager())
        .with(mapManager)
        .with(itemController, new ItemManager())
        .with(new CofManager())
        .with(new com.riiablo.engine.server.EnvironmentSystem(
            simulationSession.map(), simulationSession.environment()))
        .with(new ObjectInitializer())
        .with(new ObjectInteractor(), new WarpInteractor(), new ItemInteractor())
        .with(new MenuManager(), new DialogManager())
        // Hover selection owns all pointer hit testing and must be current
        // before CursorMovementSystem consumes a captured click.
        .with(new SelectableManager())
        .with(new HoveredManager())
        ;
    if (desktopInput) {
      builder.with(new CursorMovementSystem());
      builder.with(new KeyboardMovementSystem()); // 键盘方向键移动支持
    }
    builder
        // Explicit native 25 Hz order: state/stat decay, missile movement and
        // collision, then unit/AI behavior and death processing.
        .with(SimulationSystemGroups.stateAndActions())
        .with(SimulationSystemGroups.unitLifecycle())
        .with(new SkillCastHandler()) // TODO: move to more appropriate spot in list
        .with(new OverlayManager()) // TODO: move to more appropriate spot in list
        .with(new com.riiablo.engine.client.MissileImpactPresentationSystem())
        .with(new com.riiablo.engine.client.ElementalHitPresentationSystem())
        .with(new com.riiablo.engine.client.NativeShrinePresentationSystem())
        .with(new OverlayStepper()) // TODO: move to more appropriate spot in list
        .with(new DamageHandler()) // TODO: move to more appropriate spot in list
        .with(new DeathHandler()) // TODO: move to more appropriate spot in list
        .with(new CorpseManager()) // Manages corpse lifetime and removal
        ;
    // Presentation is shared by local and remote clients; only authority
    // worlds install quest mutation systems below.
    builder.with(new Act1QuestDialogController(), new QuestDialogController());
    if (mode.isAuthoritative()) {
      // Local games own the authoritative combat world.  Dedicated servers
      // already register this system; network clients must not create a
      // duplicate missile for the same SkillDoEvent.
      builder.with(new ActTransitionSystem());
      builder.with(new com.riiablo.engine.server.EnvironmentObjectSystem(
          simulationSession.environment()));
      builder.with(SimulationSystemGroups.quests());
      builder.with(SimulationSystemGroups.ownedUnits());
      builder.with(SimulationSystemGroups.questItemRewards());
      builder.with(mode.createServerSkillSystem());
      builder.with(new com.riiablo.engine.server.item.NativeItemQuantityRegenSystem());
      builder.with(new com.riiablo.engine.server.AuraEcsSystem());
      builder.with(new ItemGenerator());
      // Local games dispatch authoritative DeathEvent in this world. Without
      // the reward subscriber, XP still advances but no monster drop is ever
      // rolled or materialized. Network clients receive D2GS-created items.
      builder.with(new DeathRewardSystem(simulationSession.partyManager()));
      builder.with(SimulationSystemGroups.worldObjectEffects(null));
      builder.with(new VendorGenerator());
      builder.with(SimulationSystemGroups.roomPopulation(false));
      builder.with(new com.riiablo.engine.server.party.PartyMemberSyncSystem(
          simulationSession.partyManager()));
    } else {
      builder.with(new ItemGenerator()); // TODO: #89
      builder.with(new VendorGenerator()); // TODO: #89
    }
    builder
        // D2 uses a dynamic collision footprint rather than physics-engine
        // pushing.  Keep the local simulation authoritative while networked
        // clients remain replicas of D2GS positions.
        .with(mode.createDynamicUnitCollisionSystem())
        .with(new Pathfinder())
        .with(new LeapSystem())

        .with(new ObjectAmbientSoundSystem(clientAudioSession))
        .with(new ObjectBreakSoundEmitter())
        .with(new SoundEmitterHandler(clientAudioSession))

        .with(factory)
        .with(new AnimDataResolver())
        // A queued sequence must select its attack/cast COF before AnimStepper
        // advances the previous neutral animation. Otherwise a neutral wrap
        // can finish and remove the new sequence without firing its keyframe.
        .with(SimulationSystemGroups.sequenceAnimation())
        .with(new CofUnloader(), new CofResolver(), new CofLoader())
        .with(new CofLayerUnloader(), new CofLayerLoader(), new CofLayerCacher())
        .with(new CofAlphaHandler(), new CofTransformHandler())
        .with(new ItemLoader())
        .with(new MissileLoader())
        .with(new AnimationStepper())
        // Bone Wall/Prison are monster units, not map objects. Their
        // temporary walk footprint must exist in local and network clients.
        .with(SimulationSystemGroups.objectCollision())
        // Apply state-driven transforms after COF/layer systems have finished
        // their frame. Otherwise a late layer refresh can overwrite cblu.
        .with(new com.riiablo.engine.client.StateOverlaySystem());
    // Local portal replacement/consumption deletes entities too. Retire their
    // physics bodies in every client mode so invisible obstacles cannot remain.
    builder.with(new Box2DDisposer());
    if (mode.isAuthoritative()) builder.with(new com.riiablo.engine.server.VelocityAdder(true));
    builder
        .with(new Box2DSynchronizerPre())
        .with(new Box2DPhysics(SimulationClock.STEP_SECONDS))
        .with(new Box2DSynchronizerPost())

        // Resolve movement modes from post-collision displacement. A path can
        // still request movement into a torch or wall after Box2D has stopped
        // the body; using the requested velocity leaves the player running in
        // place. Multiplayer replicas retain their D2GS-owned modes.
        .with(mode.createVelocityModeChanger())

        .with(new com.riiablo.engine.server.DruidShapeShiftResolver())
        .with(new ExperienceManager(simulationSession.partyManager()))
        .with(new PlayerCorpseRetrievalSystem())

        .with(new ZoneChangeTracker())
        .with(new ZoneMovementModesChanger())
        .with(new com.riiablo.engine.server.npc.NpcVendorSessionSystem())
        .with(new ZoneEntryDisplayer())

        .with(new SoundEnvironmentSystem(clientAudioSession))
        .with(new FootstepEmitter())
        .with(new SoundEventSystem(clientAudioSession))
        .with(new MonsterSoundEmitter())
        .with(new BlockSoundEmitter())

        .with(new WarpSubstManager())
        ;
    if (mode.isAuthoritative()) {
      // Local games own Whirlwind movement and periodic damage. Multiplayer
      // clients render the authoritative D2GS position/mode/state snapshots.
      builder.with(new com.riiablo.engine.server.WhirlwindSystem());
    }
    if (autoInteract) {
      builder.with(new AutoInteracter());
    }
    builder
        .with(new PlayerItemHandler())

        .with(new AngularVelocity())
        .with(new DirectionResolver())

        .with(renderer)
        // Levels.txt Rain drives native precipitation: rain in Acts I-IV and
        // the dedicated snow presentation in Act V.
        .with(new WeatherRenderSystem())
        // Native D2 flashes dropped ground items with Gleam.dcc periodically;
        // the item itself remains absent from the Automap entity layer.
        .with(new GroundItemGleamSystem())
        // Quest markers are GPU systems and must run after the world renderer
        // so the map cannot overwrite the NPC "!" labels.
        .with(new Act1QuestIndicatorSystem())
        .with(new LabelManager())
        .with(new MonsterLabelManager())

        .with(new ItemEffectManager())

        .with(new AutomapRenderer())

        .with(new PathDebugger())
        .with(new Box2DDebugger())
        .with(new PathfindDebugger())
        .with(new RenderSystemDebugger())

        .dependsOn(ProfilerPlugin.class)
        ;
    return builder;
  }
}
