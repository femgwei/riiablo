package com.riiablo.screen;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.BaseSystem;
import com.artemis.WorldConfiguration;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.utils.Bag;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFontCache;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.riiablo.Colors;
import com.riiablo.Fonts;
import com.riiablo.Riiablo;
import com.riiablo.Textures;
import com.riiablo.codec.FontTBL;
import com.riiablo.codec.excel.SoundEnviron;
import com.riiablo.codec.excel.Sounds;
import com.riiablo.engine.SimulationMode;
import com.riiablo.engine.SimulationSession;
import com.riiablo.presentation.ClientAudioSession;
import com.riiablo.presentation.ClientEntityFactory;
import com.riiablo.engine.client.ClientItemManager;
import com.riiablo.presentation.ClientSimulationAssembly;
import com.riiablo.engine.client.NetworkedClientItemManager;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.map.EnvironmentCycle;
import com.riiablo.map.Map;
import com.riiablo.map.MapManager;
import com.riiablo.map.RenderSystem;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Calls real screen assembly without starting World, GL, networking or MPQ loading. */
class GameScreenAssemblyTest {
  private final Application previousApp = Gdx.app;
  private final com.badlogic.gdx.Files previousFiles = Gdx.files;
  private final Colors previousColors = Riiablo.colors;
  private final Fonts previousFonts = Riiablo.fonts;
  private final Textures previousTextures = Riiablo.textures;

  @BeforeEach void installResourceFreeUi() throws Exception {
    Gdx.files = mock(com.badlogic.gdx.Files.class);
    // The real quest marker constructs a Label eagerly, even before World initialization.
    Riiablo.colors = new Colors();
    Riiablo.textures = mock(Textures.class);
    Texture white = mock(Texture.class);
    when(white.getWidth()).thenReturn(1);
    when(white.getHeight()).thenReturn(1);
    Field whiteBinding = Textures.class.getDeclaredField("white");
    whiteBinding.setAccessible(true);
    whiteBinding.set(Riiablo.textures, white);
    Riiablo.fonts = mock(Fonts.class);
    FontTBL.BitmapFont font = mock(FontTBL.BitmapFont.class);
    BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData();
    BitmapFont.Glyph glyph = new BitmapFont.Glyph();
    glyph.id = '!'; glyph.width = 8; glyph.height = 16; glyph.xadvance = 8;
    data.setGlyph('!', glyph);
    data.lineHeight = 16;
    // BitmapFontCache reads these package-private fields directly, not their getters.
    Field regions = BitmapFont.class.getDeclaredField("regions");
    regions.setAccessible(true);
    regions.set(font, Array.with(new TextureRegion()));
    Field fontData = BitmapFont.class.getDeclaredField("data");
    fontData.setAccessible(true);
    fontData.set(font, data);
    when(font.getData()).thenReturn(data);
    when(font.getColor()).thenReturn(Color.WHITE);
    when(font.getRegions()).thenReturn(Array.with(new TextureRegion()));
    when(font.newFontCache()).thenAnswer(ignored -> new BitmapFontCache(font));
    Riiablo.fonts.font16 = font;
  }

  @AfterEach void restoreHost() {
    Gdx.app = previousApp;
    Gdx.files = previousFiles;
    Riiablo.colors = previousColors;
    Riiablo.fonts = previousFonts;
    Riiablo.textures = previousTextures;
  }

  static Stream<Arguments> clientModes() {
    return Stream.of(SimulationMode.LOCAL, SimulationMode.REMOTE_CLIENT).flatMap(mode ->
        Stream.of(Application.ApplicationType.Desktop, Application.ApplicationType.Android,
            Application.ApplicationType.iOS).map(platform -> Arguments.of(mode, platform)));
  }

  @ParameterizedTest
  @MethodSource("clientModes")
  void productionScreenKeepsCompleteOrderAndBindings(
      SimulationMode mode, Application.ApplicationType platform) throws Exception {
    Fixture host = new Fixture(mode, platform);
    List<BaseSystem> systems = installed(host.screen.getWorldConfigurationBuilder().build());
    assertEquals(expected(mode, platform), names(systems));
    assertEquals(systems.size() - 2, new HashSet<>(names(systems)).size(), "no duplicate system type");
    assertSame(host.screen.mapManager, find(systems, "MapManager"));
    assertSame(host.screen.itemController, find(systems,
        mode == SimulationMode.LOCAL ? "ClientItemManager" : "NetworkedClientItemManager"));
    assertSame(host.screen.factory, find(systems, "ClientEntityFactory"));
    assertSame(host.screen.renderer, find(systems, "RenderSystem"));
    for (String name : Arrays.asList("MenuManager", "DialogManager", "Act1QuestDialogController",
        "QuestDialogController", "Act1QuestIndicatorSystem", "ZoneEntryDisplayer",
        "LabelManager", "MonsterLabelManager", "OverlayManager", "MissileImpactPresentationSystem",
        "DamageHandler", "ObjectAmbientSoundSystem", "ObjectBreakSoundEmitter", "SoundEmitterHandler",
        "ItemLoader", "MissileLoader", "SoundEnvironmentSystem", "FootstepEmitter", "SoundEventSystem",
        "MonsterSoundEmitter", "BlockSoundEmitter", "WeatherRenderSystem", "GroundItemGleamSystem",
        "AutomapRenderer", "SkillCastHandler", "DeathHandler", "ElementalHitPresentationSystem",
        "NativeShrinePresentationSystem", "StateOverlaySystem", "ClientEntityFactory",
        "AnimationStepper", "CofAlphaHandler", "CofLayerCacher", "CofLayerLoader", "CofLayerUnloader",
        "CofLoader", "CofResolver", "CofTransformHandler", "CofUnloader", "DirectionResolver",
        "OverlayStepper", "ItemEffectManager")) {
      assertEquals("com.riiablo.presentation." + name, find(systems, name).getClass().getName());
    }
    assertSame(host.session.map(), field(find(systems, "EnvironmentSystem"), "map"));
    assertSame(host.session.environment(), field(find(systems, "EnvironmentSystem"), "environment"));
    assertSame(host.session.partyManager(), field(find(systems, "ExperienceManager"), "partyManager"));
    for (String name : Arrays.asList("SoundEnvironmentSystem", "ObjectAmbientSoundSystem",
        "SoundEmitterHandler", "SoundEventSystem")) assertSame(host.audio, field(find(systems, name), "session"));
    boolean local = mode == SimulationMode.LOCAL;
    assertEquals(local, field(find(systems, "DynamicUnitCollisionSystem"), "enabled"));
    assertEquals(true, field(find(systems, "VelocityModeChanger"), "applyLocalRunInput"));
    assertEquals(local, field(find(systems, "VelocityModeChanger"), "updateNetworkedModes"));
    if (local) {
      assertSame(host.session.partyManager(), field(find(systems, "DeathRewardSystem"), "partyManager"));
      assertSame(host.session.partyManager(), field(find(systems, "PartyMemberSyncSystem"), "partyManager"));
      assertSame(host.session.environment(), field(find(systems, "EnvironmentObjectSystem"), "environment"));
      assertNull(field(find(systems, "NativeShrineEffectSystem"), "authoritativeItems"));
    }
  }

  @Test void networkSubclassRetainsHighPriorityReceiveAndInterpolation() throws Exception {
    Fixture host = new Fixture(SimulationMode.REMOTE_CLIENT, Application.ApplicationType.Desktop);
    NetworkedGameScreen network = mock(NetworkedGameScreen.class, CALLS_REAL_METHODS);
    copyBindings(host, network);
    List<String> expected = expected(SimulationMode.REMOTE_CLIENT, Application.ApplicationType.Desktop);
    // Artemis defaults remain first; network HIGH systems precede the base NORMAL systems.
    expected.addAll(3, Arrays.asList("AuthoritativeInterpolationSystem", "ClientNetworkReceiver"));
    expected.addAll(expected.indexOf("ProfilerManager"),
        Arrays.asList("ClientNetworkSynchronizer", "Pinger", "NetworkProfiler"));
    assertEquals(expected, names(installed(network.getWorldConfigurationBuilder().build())));
  }

  @Test void recipesDoNotShareNewSystemsAcrossSessions() throws Exception {
    Fixture first = new Fixture(SimulationMode.LOCAL, Application.ApplicationType.Desktop);
    Fixture second = new Fixture(SimulationMode.LOCAL, Application.ApplicationType.Desktop);
    List<BaseSystem> a = installed(first.screen.getWorldConfigurationBuilder().build());
    List<BaseSystem> b = installed(second.screen.getWorldConfigurationBuilder().build());
    assertEquals(names(a), names(b));
    for (int i = 0; i < 3; i++) { assertNull(a.get(i)); assertNull(b.get(i)); }
    for (int i = 3; i < a.size(); i++) assertNotSame(a.get(i), b.get(i));
  }

  @Test void rejectsMismatchedAudioClockAndServerModeBeforeAssembly() {
    Fixture host = new Fixture(SimulationMode.LOCAL, Application.ApplicationType.Desktop);
    SimulationSession server = new SimulationSession(SimulationMode.DEDICATED_SERVER,
        host.session.map(), host.session.environment(), host.session.partyManager());
    assertThrows(IllegalArgumentException.class, () -> host.builder(server, host.audio));
    ClientAudioSession stale = new ClientAudioSession(new EnvironmentCycle(), () -> -1,
        null, new SoundEnviron(), new Sounds());
    assertThrows(IllegalArgumentException.class, () -> host.builder(host.session, stale));
    assertThrows(NullPointerException.class, () -> host.builder(host.session, null));
  }

  private static final class Fixture {
    final GameScreen screen = mock(GameScreen.class, CALLS_REAL_METHODS);
    final SimulationSession session;
    final ClientAudioSession audio;

    Fixture(SimulationMode mode, Application.ApplicationType platform) {
      Gdx.app = mock(Application.class);
      when(Gdx.app.getType()).thenReturn(platform);
      session = new SimulationSession(mode, new Map(0, 0), new EnvironmentCycle(), new PartyManager());
      audio = new ClientAudioSession(session.environment(), () -> -1, null, new SoundEnviron(), new Sounds());
      screen.mapManager = new MapManager();
      screen.itemController = mode == SimulationMode.LOCAL
          ? new ClientItemManager() : new NetworkedClientItemManager();
      screen.factory = new ClientEntityFactory();
      screen.renderer = mock(RenderSystem.class);
      try { copyBindings(this, screen); } catch (Exception e) { throw new AssertionError(e); }
    }

    WorldConfigurationBuilder builder(SimulationSession simulation, ClientAudioSession sound) {
      return ClientSimulationAssembly.builder(simulation, sound, screen.mapManager,
          screen.itemController, screen.factory, screen.renderer, true, false);
    }
  }

  private static void copyBindings(Fixture host, GameScreen target) throws Exception {
    for (String name : Arrays.asList("simulationSession", "clientAudioSession")) {
      Field binding = GameScreen.class.getDeclaredField(name);
      binding.setAccessible(true);
      binding.set(target, name.equals("simulationSession") ? host.session : host.audio);
    }
    target.mapManager = host.screen.mapManager;
    target.itemController = host.screen.itemController;
    target.factory = host.screen.factory;
    target.renderer = host.screen.renderer;
  }

  private static List<String> expected(SimulationMode mode, Application.ApplicationType platform) {
    boolean local = mode == SimulationMode.LOCAL;
    // Artemis fills these three reserved slots only when a World is created.
    String recipe = "<reserved> <reserved> <reserved> "
        + "CombatPositionCaptureSystem NetworkIdManager EventSystem TagManager MapManager "
        + (local ? "ClientItemManager " : "NetworkedClientItemManager ")
        + "ClientItemIntentSystem ItemManager ClientQuestIntentSystem ClientVendorSystem CofManager EnvironmentSystem ObjectInitializer ObjectInteractor WarpInteractor "
        + "ItemInteractor MenuManager DialogManager SelectableManager HoveredManager "
        + (platform == Application.ApplicationType.Desktop ? "CursorMovementSystem KeyboardMovementSystem " : "")
        + "TouchpadMovementSystem ClientUnitLifecycleSystem "
        + "StateUpdater StaminaSystem ManaRecoverySystem PotionRecoverySystem MercenaryPotionSystem "
        + "MissileCollisionSystem Actioneer ServerMonsterCorpseSystem UnitLifecycleSystem NativePlayerTargetSlots "
        + "SkillCastHandler OverlayManager MissileImpactPresentationSystem ElementalHitPresentationSystem "
        + "NativeShrinePresentationSystem OverlayStepper DamageHandler DeathHandler CorpseManager "
        + "Act1QuestDialogController QuestDialogController "
        + (local ? "ActTransitionSystem EnvironmentObjectSystem "
            + "NativeQuestRandomState Act1QuestSystem Act2QuestSystem Act3QuestSystem Act3MephistoQuestSystem Act4QuestSystem "
            + "Act5QuestSystem Act2TaintedSunQuestSystem Act2DurielQuestSystem NativeMercenaryRewardSystem "
            + "MercenaryFollowSystem SummonedPetSystem AssassinTrapSystem MercenarySkillSystem "
            + "NativeCountessRewardSystem NativeCharsiImbueSystem ServerSkillSystem "
            + "NativeItemQuantityRegenSystem AuraEcsSystem ItemGenerator DeathRewardSystem "
            + "NativeObjectDropSystem Act2HoradricStaffDropSystem NativeShrineEffectSystem NativeShrineSystem "
            + "NativeTrapSystem NativeTrapFireSystem VendorGenerator RoomEntityTrackingSystem "
            + "RoomActivationSystem AIStepper PartyMemberSyncSystem " : "ItemGenerator VendorGenerator ")
        + "DynamicUnitCollisionSystem Pathfinder LeapSystem ObjectAmbientSoundSystem ObjectBreakSoundEmitter "
        + "SoundEmitterHandler ClientEntityFactory AnimDataResolver SequenceHandler AnimStepper "
        + "CofUnloader CofResolver CofLoader CofLayerUnloader CofLayerLoader CofLayerCacher "
        + "CofAlphaHandler CofTransformHandler ItemLoader MissileLoader AnimationStepper "
        + "ObjectCollisionUpdater BoneWallCollisionSystem StateOverlaySystem "
        + "Box2DDisposer "
        + (local ? "VelocityAdder " : "")
        + "Box2DSynchronizerPre Box2DPhysics Box2DSynchronizerPost VelocityModeChanger "
        + "DruidShapeShiftResolver ExperienceManager PlayerCorpseRetrievalSystem ZoneChangeTracker "
        + "ZoneMovementModesChanger NpcVendorSessionSystem ZoneEntryDisplayer SoundEnvironmentSystem "
        + "FootstepEmitter SoundEventSystem MonsterSoundEmitter BlockSoundEmitter WarpSubstManager "
        + (local ? "WhirlwindSystem " : "")
        + (platform == Application.ApplicationType.Android ? "AutoInteracter " : "")
        + "PlayerItemHandler AngularVelocity DirectionResolver RenderSystem WeatherRenderSystem "
        + "GroundItemGleamSystem Act1QuestIndicatorSystem LabelManager MonsterLabelManager "
        + "ItemEffectManager AutomapRenderer PathDebugger Box2DDebugger PathfindDebugger RenderSystemDebugger "
        + "ProfilerManager ProfilerSystem";
    return new ArrayList<>(Arrays.asList(recipe.split(" ")));
  }

  private static List<String> names(List<BaseSystem> systems) {
    return systems.stream().map(s -> s == null ? "<reserved>" : s.getClass().getSimpleName())
        .collect(Collectors.toList());
  }

  private static BaseSystem find(List<BaseSystem> systems, String name) {
    return systems.stream().filter(s -> s != null && s.getClass().getSimpleName().equals(name)).findFirst().get();
  }

  @SuppressWarnings("unchecked")
  private static List<BaseSystem> installed(WorldConfiguration config) throws Exception {
    Bag<BaseSystem> bag = (Bag<BaseSystem>) field(config, "systems");
    List<BaseSystem> result = new ArrayList<>();
    for (int i = 0; i < bag.size(); i++) result.add(bag.get(i));
    return result;
  }

  private static Object field(Object owner, String name) throws Exception {
    Field field = owner.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(owner);
  }
}
