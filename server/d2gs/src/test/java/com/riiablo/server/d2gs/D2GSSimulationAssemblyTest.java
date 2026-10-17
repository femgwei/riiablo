package com.riiablo.server.d2gs;

import static org.junit.Assert.*;

import com.artemis.BaseSystem;
import com.artemis.WorldConfiguration;
import com.artemis.utils.Bag;
import com.riiablo.engine.SimulationMode;
import com.riiablo.engine.SimulationSession;
import com.riiablo.engine.server.ServerEntityFactory;
import com.riiablo.engine.server.ServerItemManager;
import com.riiablo.engine.server.item.AuthoritativeItemMoveService;
import com.riiablo.engine.server.npc.NpcVendorSessionManager;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.map.EnvironmentCycle;
import com.riiablo.map.Map;
import com.riiablo.map.MapManager;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

/** Inspects the production D2GS recipe without starting World, sockets or loading MPQs. */
public class D2GSSimulationAssemblyTest {
  @Test public void preservesCompleteDedicatedOrderAndHostBindings() throws Exception {
    Fixture host = new Fixture();
    List<BaseSystem> systems = host.systems(false);
    // Artemis reserves the three built-in slots until World construction.
    assertEquals(Arrays.asList(("<reserved> <reserved> <reserved> "
        + "EventSystem MenuManager DialogManager NpcVendorSessionSystem ZoneTransitionCleanupSystem "
        + "NativeQuestRandomState Act1QuestSystem Act2QuestSystem Act3QuestSystem Act3MephistoQuestSystem Act4QuestSystem "
        + "Act5QuestSystem Act2TaintedSunQuestSystem Act2DurielQuestSystem NativeMercenaryRewardSystem "
        + "NativeCountessRewardSystem NativeCharsiImbueSystem ServerNetworkIdManager SerializationManager "
        + "MapManager ServerItemManager ItemGenerator CofManager EnvironmentSystem ObjectInitializer "
        + "EnvironmentObjectSystem ObjectInteractor WarpInteractor ItemInteractor NativeObjectDropSystem "
        + "Act2HoradricStaffDropSystem NativeShrineEffectSystem NativeShrineSystem NativeTrapSystem "
        + "NativeTrapFireSystem StateUpdater StaminaSystem ManaRecoverySystem PotionRecoverySystem "
        + "MercenaryPotionSystem MissileCollisionSystem Actioneer MercenaryFollowSystem SummonedPetSystem "
        + "AssassinTrapSystem MercenarySkillSystem ServerMonsterCorpseSystem UnitLifecycleSystem "
        + "NativePlayerTargetSlots GroundDropCleanupSystem AuraEcsSystem ServerPlayerDeathSystem "
        + "PlayerCorpseRetrievalSystem ServerSkillSystem DeathRewardSystem SequenceHandler AnimStepper "
        + "ObjectCollisionUpdater BoneWallCollisionSystem DruidShapeShiftResolver NativeItemQuantityRegenSystem "
        + "ExperienceManager PartyMemberSyncSystem VendorGenerator RoomEntityTrackingSystem RoomActivationSystem "
        + "AIStepper DynamicUnitCollisionSystem Pathfinder VelocityAdder VelocityModeChanger WhirlwindSystem "
        + "LeapSystem ServerEntityFactory NetworkSynchronizer AnimDataResolver").split(" ")), names(systems));
    assertEquals(systems.size() - 2, new HashSet<>(names(systems)).size());
    assertSame(host.mapManager, find(systems, "MapManager"));
    assertSame(host.items, find(systems, "ServerItemManager"));
    assertSame(host.factory, find(systems, "ServerEntityFactory"));
    assertSame(host.sync, find(systems, "NetworkSynchronizer"));
    assertSame(host.vendors, field(find(systems, "NpcVendorSessionSystem"), "sessions"));
    assertSame(host.authoritativeItems, field(find(systems, "NativeShrineEffectSystem"), "authoritativeItems"));
    assertSame(host.session.map(), field(find(systems, "EnvironmentSystem"), "map"));
    assertSame(host.session.environment(), field(find(systems, "EnvironmentSystem"), "environment"));
    assertSame(host.session.environment(), field(find(systems, "EnvironmentObjectSystem"), "environment"));
    for (String type : Arrays.asList("ExperienceManager", "DeathRewardSystem", "PartyMemberSyncSystem")) {
      assertSame(host.session.partyManager(), field(find(systems, type), "partyManager"));
    }
    assertEquals(true, field(find(systems, "DynamicUnitCollisionSystem"), "enabled"));
    assertEquals(false, field(find(systems, "VelocityModeChanger"), "applyLocalRunInput"));
    assertEquals(true, field(find(systems, "VelocityModeChanger"), "updateNetworkedModes"));
    assertEquals(false, field(find(systems, "ServerSkillSystem"), "monstersOnly"));
  }

  @Test public void freshSessionsNeverShareSystems() throws Exception {
    List<BaseSystem> first = new Fixture().systems(false);
    List<BaseSystem> second = new Fixture().systems(false);
    assertEquals(names(first), names(second));
    for (int i = 0; i < 3; i++) { assertNull(first.get(i)); assertNull(second.get(i)); }
    for (int i = 3; i < first.size(); i++) assertNotSame(first.get(i), second.get(i));
  }

  @Test public void aiTourIsAnExplicitOptionWithoutReordering() throws Exception {
    Fixture host = new Fixture();
    List<BaseSystem> quiet = host.systems(false);
    List<BaseSystem> traced = new Fixture().systems(true);
    assertEquals(names(quiet), names(traced));
    assertEquals(false, field(find(quiet, "AIStepper"), "traceAiTour"));
    assertEquals(true, field(find(traced, "AIStepper"), "traceAiTour"));
  }

  @Test public void rejectsBothClientModes() {
    Fixture host = new Fixture();
    for (SimulationMode mode : Arrays.asList(SimulationMode.LOCAL, SimulationMode.REMOTE_CLIENT)) {
      SimulationSession client = new SimulationSession(mode, host.session.map(),
          host.session.environment(), host.session.partyManager());
      try {
        D2GSSimulationAssembly.builder(client, host.mapManager, host.items, host.factory,
            host.sync, host.vendors, host.authoritativeItems, false);
        fail("Client mode must not install the D2GS recipe");
      } catch (IllegalArgumentException expected) { /* mode rejected before assembly */ }
    }
  }

  @Test(expected = NullPointerException.class)
  public void rejectsMissingDedicatedInventoryService() {
    Fixture host = new Fixture();
    D2GSSimulationAssembly.builder(host.session, host.mapManager, host.items, host.factory,
        host.sync, host.vendors, null, false);
  }

  private static final class Fixture {
    final SimulationSession session = new SimulationSession(SimulationMode.DEDICATED_SERVER,
        new Map(0, 0), new EnvironmentCycle(), new PartyManager());
    final MapManager mapManager = new MapManager();
    final ServerItemManager items = new ServerItemManager();
    final ServerEntityFactory factory = new ServerEntityFactory();
    final NetworkSynchronizer sync = new NetworkSynchronizer();
    final NpcVendorSessionManager vendors = new NpcVendorSessionManager();
    final AuthoritativeItemMoveService authoritativeItems = new AuthoritativeItemMoveService();

    List<BaseSystem> systems(boolean trace) throws Exception {
      WorldConfiguration config = D2GSSimulationAssembly.builder(session, mapManager, items,
          factory, sync, vendors, authoritativeItems, trace).build();
      @SuppressWarnings("unchecked") Bag<BaseSystem> bag = (Bag<BaseSystem>) field(config, "systems");
      List<BaseSystem> result = new ArrayList<>();
      for (int i = 0; i < bag.size(); i++) result.add(bag.get(i));
      return result;
    }
  }

  private static List<String> names(List<BaseSystem> systems) {
    return systems.stream().map(s -> s == null ? "<reserved>" : s.getClass().getSimpleName())
        .collect(Collectors.toList());
  }

  private static BaseSystem find(List<BaseSystem> systems, String name) {
    return systems.stream().filter(s -> s != null && s.getClass().getSimpleName().equals(name)).findFirst().get();
  }

  private static Object field(Object owner, String name) throws Exception {
    Field field = owner.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(owner);
  }
}
