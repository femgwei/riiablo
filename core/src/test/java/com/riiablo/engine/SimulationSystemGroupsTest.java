package com.riiablo.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.artemis.BaseSystem;
import com.artemis.WorldConfiguration;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.utils.Bag;
import com.riiablo.engine.server.item.AuthoritativeItemMoveService;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Registration contracts: no World, MPQ, GPU or application startup required. */
class SimulationSystemGroupsTest {
  static Stream<Arguments> orderedGroups() {
    return Stream.of(
        group(SimulationSystemGroups::stateAndActions,
            "StateUpdater StaminaSystem ManaRecoverySystem PotionRecoverySystem "
                + "MercenaryPotionSystem MissileCollisionSystem Actioneer"),
        group(SimulationSystemGroups::unitLifecycle,
            "ServerMonsterCorpseSystem UnitLifecycleSystem NativePlayerTargetSlots"),
        group(SimulationSystemGroups::quests,
            "NativeQuestRandomState Act1QuestSystem Act2QuestSystem Act3QuestSystem Act3MephistoQuestSystem "
                + "Act4QuestSystem Act5QuestSystem Act2TaintedSunQuestSystem "
                + "Act2DurielQuestSystem NativeMercenaryRewardSystem"),
        group(SimulationSystemGroups::ownedUnits,
            "MercenaryFollowSystem SummonedPetSystem AssassinTrapSystem MercenarySkillSystem"),
        group(SimulationSystemGroups::questItemRewards,
            "NativeCountessRewardSystem NativeCharsiImbueSystem"),
        group(() -> SimulationSystemGroups.worldObjectEffects(null),
            "NativeObjectDropSystem Act2HoradricStaffDropSystem NativeShrineEffectSystem "
                + "NativeShrineSystem NativeTrapSystem NativeTrapFireSystem"),
        group(() -> SimulationSystemGroups.roomPopulation(false),
            "RoomEntityTrackingSystem RoomActivationSystem AIStepper"),
        group(SimulationSystemGroups::sequenceAnimation, "SequenceHandler AnimStepper"),
        group(SimulationSystemGroups::objectCollision,
            "ObjectCollisionUpdater BoneWallCollisionSystem"));
  }

  private static Arguments group(Supplier<BaseSystem[]> factory, String expected) {
    return Arguments.of(factory, expected);
  }

  @ParameterizedTest(name = "preserves ordered group: {1}")
  @MethodSource("orderedGroups")
  void preservesRegistrationOrder(Supplier<BaseSystem[]> factory, String expected) throws Exception {
    BaseSystem[] systems = factory.get();
    assertEquals(Arrays.asList(expected.split(" ")), names(systems));
    // Inspect the actual Artemis builder output too: plugins would be installed
    // later than an inline .with and could change ordering around host systems.
    BeforeGroup before = new BeforeGroup();
    AfterGroup after = new AfterGroup();
    WorldConfiguration config = new WorldConfigurationBuilder()
        .with(before).with(systems).with(after).build();
    List<BaseSystem> installed = installedSystems(config);
    int start = installed.indexOf(before);
    assertEquals(Arrays.asList(systems), installed.subList(start + 1, start + 1 + systems.length));
    assertSame(after, installed.get(start + 1 + systems.length));
  }

  @ParameterizedTest(name = "session isolation: {1}")
  @MethodSource("orderedGroups")
  void neverSharesSystemsAcrossSessions(Supplier<BaseSystem[]> factory, String expected) {
    BaseSystem[] local = factory.get();
    BaseSystem[] dedicated = factory.get();
    assertNotSame(local, dedicated);
    for (int i = 0; i < local.length; i++) assertNotSame(local[i], dedicated[i]);
    local[0] = null;
    assertEquals(Arrays.asList(expected.split(" ")), names(factory.get()));
  }

  @Test
  void groupsHaveNoOverlappingSystemOwnership() {
    List<Class<?>> classes = new ArrayList<>();
    orderedGroups().forEach(arguments -> {
      @SuppressWarnings("unchecked")
      Supplier<BaseSystem[]> factory = (Supplier<BaseSystem[]>) arguments.get()[0];
      for (BaseSystem system : factory.get()) classes.add(system.getClass());
    });
    assertEquals(39, classes.size());
    assertEquals(classes.size(), new HashSet<>(classes).size());
  }

  @Test
  void retainsDedicatedInventoryRevisionServiceAndLocalFallback() throws Exception {
    AuthoritativeItemMoveService items = new AuthoritativeItemMoveService();
    BaseSystem dedicatedShrine = SimulationSystemGroups.worldObjectEffects(items)[2];
    BaseSystem localShrine = SimulationSystemGroups.worldObjectEffects(null)[2];
    assertSame(items, field(dedicatedShrine, "authoritativeItems"));
    assertNull(field(localShrine, "authoritativeItems"));
  }

  @Test
  void aiTourTracingIsAnExplicitHostOption() throws Exception {
    assertEquals(false, field(SimulationSystemGroups.roomPopulation(false)[2], "traceAiTour"));
    assertEquals(true, field(SimulationSystemGroups.roomPopulation(true)[2], "traceAiTour"));
  }

  private static List<String> names(BaseSystem[] systems) {
    return Arrays.stream(systems).map(system -> system.getClass().getSimpleName())
        .collect(Collectors.toList());
  }

  @SuppressWarnings("unchecked")
  private static List<BaseSystem> installedSystems(WorldConfiguration config) throws Exception {
    Bag<BaseSystem> bag = (Bag<BaseSystem>) field(config, "systems");
    List<BaseSystem> systems = new ArrayList<>();
    for (int i = 0; i < bag.size(); i++) systems.add(bag.get(i));
    return systems;
  }

  private static Object field(Object owner, String name) throws Exception {
    Field field = owner.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(owner);
  }

  private static class BeforeGroup extends BaseSystem {
    @Override protected void processSystem() {}
  }

  private static class AfterGroup extends BaseSystem {
    @Override protected void processSystem() {}
  }
}
