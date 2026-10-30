package com.riiablo.screen;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.utils.IntBag;
import net.mostlyoriginal.api.event.common.EventSystem;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.net.Socket;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Touchpad;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.UIUtils;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import com.riiablo.Client;
import com.riiablo.Cvars;
import com.riiablo.Keys;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloVersion;
import com.riiablo.camera.IsometricCamera;
import com.riiablo.codec.COF;
import com.riiablo.codec.DC;
import com.riiablo.codec.DC6;
import com.riiablo.codec.DCC;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.Sounds;
import com.riiablo.cvar.Cvar;
import com.riiablo.cvar.CvarStateAdapter;
import com.riiablo.engine.Engine;
import com.riiablo.engine.SimulationClock;
import com.riiablo.engine.EngineConfig;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.client.AuthoritativeInterpolationSystem;
import com.riiablo.presentation.AutomapRenderer;
import com.riiablo.presentation.ClientEntityFactory;
import com.riiablo.engine.client.ClientItemManager;
import com.riiablo.engine.client.ClientRenderSystemRunner;
import com.riiablo.engine.client.ClientNetworkSynchronizer;
import com.riiablo.engine.client.ClientNetworkSession;
import com.riiablo.presentation.CofLayerCacher;
import com.riiablo.presentation.CofLayerLoader;
import com.riiablo.presentation.CofLoader;
import com.riiablo.presentation.CofResolver;
import com.riiablo.engine.client.CursorMovementSystem;
import com.riiablo.engine.client.TouchpadMovementSystem;
import com.riiablo.presentation.DeathHandler;
import com.riiablo.presentation.DialogManager;
import com.riiablo.presentation.MenuManager;
import com.riiablo.engine.client.NetworkedClientItemManager;
import com.riiablo.engine.client.NetworkedActionSender;
import com.riiablo.presentation.debug.Box2DDebugger;
import com.riiablo.presentation.debug.PathDebugger;
import com.riiablo.presentation.debug.PathfindDebugger;
import com.riiablo.presentation.debug.RenderSystemDebugger;
import com.riiablo.engine.client.FixedStepAccumulator;
import com.riiablo.engine.SimulationMode;
import com.riiablo.engine.SimulationSession;
import com.riiablo.engine.SimulationTickCoordinator;
import com.riiablo.presentation.ClientAudioSession;
import com.riiablo.presentation.ClientGameplayContext;
import com.riiablo.presentation.ClientPointerUiAdapter;
import com.riiablo.presentation.ClientGameplayInputAdapter;
import com.riiablo.presentation.ClientNetworkPresentationAdapter;
import com.riiablo.presentation.ClientItemContextAdapter;
import com.riiablo.presentation.ClientSimulationAssembly;
import com.riiablo.engine.server.Actioneer;
import com.riiablo.engine.server.ServerGameplayContext;
import com.riiablo.engine.server.InteractionRange;
import com.riiablo.engine.server.combat.CombatPositionHistory;
import com.riiablo.engine.server.AnimDataResolver;
import com.riiablo.engine.server.quest.NativeMercenaryRewardSystem;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.player.PlayerStatsManager;
import com.riiablo.engine.server.VelocityModeChanger;
import com.riiablo.engine.server.portal.TownPortalRegistry;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.Box2DBody;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofComponents;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Size;
import com.riiablo.engine.server.component.TemporaryRunning;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.ZoneChangeEvent;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.graphics.PaletteIndexedColorDrawable;
import com.riiablo.item.Item;
import com.riiablo.key.MappedKey;
import com.riiablo.key.MappedKeyStateAdapter;
import com.riiablo.map.Act1MapBuilder;
import com.riiablo.map.Act1MapBuilderD2MOD;
import com.riiablo.map.Box2DPhysics;
import com.riiablo.map.EnvironmentCycle;
import com.riiablo.map.Map;
import com.riiablo.map.MapManager;
import com.riiablo.map.RenderSystem;
import com.riiablo.profiler.ProfilerManager;
import com.riiablo.profiler.SystemProfiler;
import com.riiablo.profiler.GpuSystem;
import com.riiablo.save.CharData;
import com.riiablo.save.D2SWriter;
import com.riiablo.screen.panel.CharacterPanel;
import com.riiablo.screen.panel.ControlPanel;
import com.riiablo.screen.panel.CubePanel;
import com.riiablo.screen.panel.EscapeController;
import com.riiablo.screen.panel.EscapePanel;
import com.riiablo.screen.panel.HirelingPanel;
import com.riiablo.screen.panel.MercenaryHud;
import com.riiablo.screen.panel.HelpPanel;
import com.riiablo.screen.panel.InventoryPanel;
import com.riiablo.screen.panel.MobileControls;
import com.riiablo.screen.panel.MobilePanel;
import com.riiablo.screen.panel.QuestsPanel;
import com.riiablo.screen.panel.PartyPanel;
import com.riiablo.screen.panel.SpellsPanel;
import com.riiablo.screen.panel.SpellsQuickPanel;
import com.riiablo.screen.panel.StashPanel;
import com.riiablo.screen.panel.TradePanel;
import com.riiablo.screen.panel.VendorPanel;
import com.riiablo.screen.panel.WaygatePanel;
import com.riiablo.util.Disposal;
import com.riiablo.widget.TextArea;

public class GameScreen extends ScreenAdapter implements GameLoadingScreen.Loadable {
  private static final String TAG = "GameScreen";
  private static final boolean DEBUG          = true;
  private static final boolean DEBUG_TOUCHPAD = !true;
  private static final boolean DEBUG_MOBILE   = !true;

  private static final boolean PRECACHE_CURSOR = true;
  private static final boolean PRECACHE_ITEMS = true;

  /**
   * Deltas above this threshold represent a suspended/backgrounded window,
   * not a frame the simulation should catch up. Feeding such a delta into
   * Artemis interval systems leaves several seconds in their accumulators and
   * makes animations, movement, and AI run fast after focus returns.
   */
  static final float BACKGROUND_DELTA_THRESHOLD = 0.25f;

  static final int MAX_SIMULATION_STEPS_PER_RENDER = 4;

  /**
   * Preserve visible-frame time so the fixed-step accumulator can maintain the
   * native 25 Hz simulation when rendering falls below 25 FPS. Catch-up remains
   * bounded, while suspended/background deltas are discarded separately.
   */
  static final float MAX_SIMULATION_DELTA =
      SimulationClock.STEP_SECONDS * MAX_SIMULATION_STEPS_PER_RENDER;

  private static final int[] ITEMS = {
      205, 206, 207, 208, 209, 210, 211, 212, 213, 214, 215, 216, 217, 218, 219, 220, 221, 222, 223,
      224, 225, 226, 227, 228, 229, 230, 231, 232, 233, 234, 235, 236, 237, 238, 239, 240, 241, 242,
      243, 244, 245, 246, 247, 248, 249, 250
  };
  private static final int[] CURSORS = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16 };

  private final Vector2 tmpVec2 = new Vector2();
  private final Vector2 tmpVec2b = new Vector2();

  final AssetDescriptor<DC6> loadingscreenDescriptor = new AssetDescriptor<>("data\\local\\ui\\loadingscreen.dc6", DC6.class);
  final AssetDescriptor<Sound> windowopenDescriptor = new AssetDescriptor<>("data\\global\\sfx\\cursor\\windowopen.wav", Sound.class);
  
  // 小地图精灵资源
  final AssetDescriptor<DC6> automapDescriptor = new AssetDescriptor<>("data\\global\\ui\\AUTOMAP\\MaxiMap.dc6", DC6.class);

  final AssetDescriptor<Texture> touchpadBackgroundDescriptor = new AssetDescriptor<>("textures/touchBackground.png", Texture.class);
  final AssetDescriptor<Texture> touchpadKnobDescriptor = new AssetDescriptor<>("textures/touchKnob.png", Texture.class);
  Touchpad touchpad;

  final Array<AssetDescriptor> preloadedAssets = new Array<AssetDescriptor>() {{
    add(loadingscreenDescriptor);
    add(windowopenDescriptor);
    add(automapDescriptor);
    if (Gdx.app.getType() == Application.ApplicationType.Android || DEBUG_TOUCHPAD) {
      add(touchpadBackgroundDescriptor);
      add(touchpadKnobDescriptor);
    }
    if (PRECACHE_CURSOR) {
      for (int id : CURSORS) {
        Sounds.Entry sound = Riiablo.files.Sounds.get(id);
        add(new AssetDescriptor<>("data\\global\\sfx\\" + sound.FileName, Sound.class));
      }
    }
    if (PRECACHE_ITEMS) {
      for (int id : ITEMS) {
        Sounds.Entry sound = Riiablo.files.Sounds.get(id);
        add(new AssetDescriptor<>("data\\global\\sfx\\" + sound.FileName, Sound.class));
      }
    }
  }};
  private final ScreenAssetScope sessionAssets = new ScreenAssetScope(Riiablo.assets);


  Stage stage;
  Stage scaledStage;
  Viewport viewport;
  GameLoadingScreen loadingScreen;
  boolean created;
  private boolean disposed;
  private boolean subscriptionsConfigured;
  private final ScreenSubscriptionScope visibleSubscriptions = new ScreenSubscriptionScope();
  private final ScreenSubscriptionScope sessionSubscriptions = new ScreenSubscriptionScope();
  boolean isDebug;
  boolean discardNextSimulationDelta;
  private final FixedStepAccumulator simulationAccumulator =
      new FixedStepAccumulator(SimulationClock.STEP_SECONDS, MAX_SIMULATION_STEPS_PER_RENDER);
  private final SimulationTickCoordinator simulationTickCoordinator =
      new SimulationTickCoordinator();
  private long lastSlowSimulationLogNanos;
  private ClientRenderSystemRunner renderSystemRunner;
  private AuthoritativeInterpolationSystem interpolationSystem;
  
  // Automap 持续缩放累加器（用于按住键持续放大/缩小）
  private float automapZoomAccumulator = 0f;
  // Automap 持续平移累加器（用于按住键持续移动）
  private float automapPanAccumulator = 0f;
  MappedKeyStateAdapter debugKeyListener = new MappedKeyStateAdapter() {
    @Override
    public void onPressed(MappedKey key, int keycode) {
      RenderSystemDebugger debugger = engine.getSystem(RenderSystemDebugger.class);
      debugger.setEnabled(!debugger.isEnabled());
    }
  };

  World engine;
  EntityFactory factory;
  RenderSystem renderer;
  public int player;
  CharData charData;
  boolean characterSavedForExit;
  Socket socket;

  EngineConfig config;
  private final SimulationSession simulationSession;
  private final ClientAudioSession clientAudioSession;
  private final ClientGameplayContext clientGameplayContext;
  Map map;
  MapManager mapManager;
  com.riiablo.engine.server.party.PartyManager partyManager;
  Levels.Entry pendingWaypointTarget;
  int activeWaygateEntity = Engine.INVALID_ENTITY;
  IsometricCamera iso;
  InputProcessor testingInputProcessor;

  ClientItemManager itemController;

  public void requestWeaponSwap() { itemController.intents().swapWeapons(); }

  public EscapePanel escapePanel;
  public HelpPanel helpPanel;
  public ControlPanel controlPanel;
  MobilePanel mobilePanel;
  MobileControls mobileControls;

  Client.ScreenBoundsListener screenBoundsListener;
  public TextArea input;
  public TextArea output;

  Actor left;
  Actor right;
  MappedKeyStateAdapter mappedKeyStateListener;
  public InventoryPanel inventoryPanel;
  public CharacterPanel characterPanel;
  public SpellsPanel spellsPanel;
  public StashPanel stashPanel;
  public HirelingPanel hirelingPanel;
  public com.riiablo.screen.panel.HireMercenaryPanel hireMercenaryPanel;
  public MercenaryHud mercenaryHud;
  public WaygatePanel waygatePanel;
  public QuestsPanel questsPanel;
  public PartyPanel partyPanel;
  public TradePanel tradePanel;
  public CubePanel cubePanel;
  public VendorPanel vendorPanel;
  public SpellsQuickPanel spellsQuickPanelL;
  public SpellsQuickPanel spellsQuickPanelR;

  Actor details;

  private static boolean isInside(Actor target, Actor ancestor) {
    if (target == null || ancestor == null) return false;
    return target == ancestor || target.isDescendantOf(ancestor);
  }

  /**
   * FIXME: there has to be a better way of doing this -- some way to layout the stage (or relevant
   *        parts) and get the coordinates I need. Right now it flashes the control panel for a
   *        frame before hiding it.
   */
  boolean firstRender = true;

  @Override
  public Array<AssetDescriptor> getDependencies() {
    return preloadedAssets;
  }

  public GameScreen(CharData charData) {
    this(charData, null);
  }

  public GameScreen(CharData charData, Socket socket) {
    this.charData = charData;
    this.socket = socket;
    clientGameplayContext = new ClientGameplayContext(
        charData, () -> player, () -> this, this::setAct,
        Riiablo.assets, Riiablo.audio, Riiablo.cursor, Riiablo.files, Riiablo.batch,
        Riiablo.fonts, Riiablo.colors, Riiablo.string, Riiablo.saves, Riiablo.home,
        Riiablo.language == null ? null : Riiablo.language.resourceCode, Riiablo.mpqs,
        Riiablo.palettes);
    ClientItemContextAdapter itemContext = new ClientItemContextAdapter(clientGameplayContext);
    // Create the item controller before any HUD/panel captures it.  The
    // mercenary portrait accepts cursor potions through this controller; the
    // previous order constructed MercenaryHud first and permanently retained
    // a null reference.
    itemController = socket == null
        ? new ClientItemManager(itemContext)
        : new NetworkedClientItemManager(itemContext);

    // Native Diablo II key bindings are character-scoped (unlike the
    // preferences fallback used by the controls screen).  Load them as soon
    // as the local character is known, before any gameplay input is handled.
    if (socket == null && charData != null && charData.isManaged()
        && Riiablo.keys != null && Riiablo.saves != null
        && charData.name != null && !charData.name.isEmpty()) {
      Riiablo.keys.loadCharacterKey(Riiablo.saves.child(charData.name + ".key"));
    }

    Riiablo.viewport = viewport = Riiablo.extendViewport;
    stage = new Stage(viewport, Riiablo.batch);

    input = new TextArea("", new TextArea.TextFieldStyle() {{
      this.font = Riiablo.fonts.fontformal12;
      this.fontColor = Riiablo.colors.white;
      this.background = new PaletteIndexedColorDrawable(Riiablo.colors.modal50);
      this.cursor = new TextureRegionDrawable(Riiablo.textures.white);

      float padding = 4;
      background.setLeftWidth(padding);
      background.setTopHeight(padding);
      background.setRightWidth(padding);
      background.setBottomHeight(padding);
    }}) {
      {
        writeEnters = false;
      }

      @Override
      public void setVisible(boolean visible) {
        if (!visible) input.setText("");
        else {
          stage.setKeyboardFocus(this);
          Gdx.input.setOnscreenKeyboardVisible(true);
        }
        super.setVisible(visible);
      }
    };
    //input.setDebug(true);
    input.setSize(stage.getWidth() * 0.75f, Riiablo.fonts.fontformal12.getLineHeight() * 3);
    input.setPosition(stage.getWidth() / 2 - input.getWidth() / 2, 100);
    input.setAlignment(Align.topLeft);
    input.setVisible(false);
    if (Gdx.app.getType() != Application.ApplicationType.Android) input.setTouchable(Touchable.disabled);
    stage.addActor(input);

    output = new TextArea("", new TextArea.TextFieldStyle() {{
      this.font = Riiablo.fonts.fontformal12;
      this.fontColor = Riiablo.colors.white;
      this.cursor = new TextureRegionDrawable(Riiablo.textures.white);
    }});
    //output.setDebug(true);
    output.setSize(stage.getWidth() * 0.75f, Riiablo.fonts.fontformal12.getLineHeight() * 8);
    output.setPosition(10, stage.getHeight() - 10, Align.topLeft);
    output.setAlignment(Align.topLeft);
    output.setDisabled(true);
    output.setVisible(true);
    output.setTouchable(Touchable.disabled);
    stage.addActor(output);

    escapePanel = new EscapePanel(this);
    stage.addActor(escapePanel);

    controlPanel = new ControlPanel(this);
    controlPanel.setPosition(stage.getWidth() / 2, 0, Align.bottom | Align.center);
    controlPanel.pack();
    stage.addActor(controlPanel);

    if (DEBUG_MOBILE || Gdx.app.getType() == Application.ApplicationType.Android) {
      mobilePanel = new MobilePanel(this);
      mobilePanel.setPosition(0, 0);
      mobilePanel.setWidth(stage.getWidth());
      stage.addActor(mobilePanel);

      mobileControls = new MobileControls(this);
      mobileControls.setPosition(
          stage.getWidth() - mobileControls.getWidth(),
          mobilePanel.getHeight());
      stage.addActor(mobileControls);
    }

    inventoryPanel = new InventoryPanel(this);
    inventoryPanel.setPosition(
        stage.getWidth() - inventoryPanel.getWidth(),
        stage.getHeight() - inventoryPanel.getHeight());
    stage.addActor(inventoryPanel);

    hirelingPanel = new HirelingPanel(this);
    hirelingPanel.setPosition(0, stage.getHeight() - hirelingPanel.getHeight());
    stage.addActor(hirelingPanel);
    hireMercenaryPanel = new com.riiablo.screen.panel.HireMercenaryPanel(this);
    stage.addActor(hireMercenaryPanel);

    mercenaryHud = new MercenaryHud(this, itemController.intents());
    // Keep the native portrait inset symmetrical: its top and left margins are
    // both eight virtual pixels instead of touching the top edge.
    mercenaryHud.setPosition(8, stage.getHeight() - mercenaryHud.getHeight() - 8);
    stage.addActor(mercenaryHud);

    stashPanel = new StashPanel(this);
    stashPanel.setPosition(0, stage.getHeight() - stashPanel.getHeight());
    stage.addActor(stashPanel);

    cubePanel = new CubePanel(this);
    cubePanel.setPosition(0, stage.getHeight() - cubePanel.getHeight());
    stage.addActor(cubePanel);

    vendorPanel = new VendorPanel(this);
    vendorPanel.setPosition(0, stage.getHeight() - vendorPanel.getHeight());
    stage.addActor(vendorPanel);

    characterPanel = new CharacterPanel(this);
    characterPanel.setPosition(0, stage.getHeight() - characterPanel.getHeight());
    stage.addActor(characterPanel);

    questsPanel = new QuestsPanel(this);
    questsPanel.setPosition(0, stage.getHeight() - questsPanel.getHeight());
    stage.addActor(questsPanel);

    partyPanel = new PartyPanel(this);
    partyPanel.setPosition(0, stage.getHeight() - partyPanel.getHeight());
    stage.addActor(partyPanel);

    tradePanel = new TradePanel(this);
    tradePanel.setPosition(0, stage.getHeight() - tradePanel.getHeight());
    stage.addActor(tradePanel);

    waygatePanel = new WaygatePanel(this, charData);
    waygatePanel.setPosition(0, stage.getHeight() - waygatePanel.getHeight());
    stage.addActor(waygatePanel);

    spellsPanel = new SpellsPanel(this);
    spellsPanel.setPosition(
        stage.getWidth() - spellsPanel.getWidth(),
        stage.getHeight() - spellsPanel.getHeight());
    stage.addActor(spellsPanel);

    spellsQuickPanelL = new SpellsQuickPanel(this, controlPanel.getLeftSkill(), true, socket);
    spellsQuickPanelL.setPosition(0, 100, Align.bottomLeft);
    spellsQuickPanelL.setVisible(false);
    stage.addActor(spellsQuickPanelL);

    spellsQuickPanelR = new SpellsQuickPanel(this, controlPanel.getRightSkill(), false, socket);
    spellsQuickPanelR.setPosition(stage.getWidth(), 100, Align.bottomRight);
    spellsQuickPanelR.setVisible(false);
    stage.addActor(spellsQuickPanelR);

    // Native quick-skill grids are transient. Any pointer press outside the
    // open grid closes it before world/UI input continues. The HUD skill
    // buttons are excluded so their own toggle listener can close the grid
    // without the capture phase immediately reopening it.
    stage.addCaptureListener(new InputListener() {
      @Override
      public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
        Actor target = event.getTarget();
        if (spellsQuickPanelL.isVisible()
            && !isInside(target, spellsQuickPanelL)
            && !isInside(target, controlPanel.getLeftSkill())) {
          spellsQuickPanelL.setVisible(false);
        }
        if (spellsQuickPanelR.isVisible()
            && !isInside(target, spellsQuickPanelR)
            && !isInside(target, controlPanel.getRightSkill())) {
          spellsQuickPanelR.setVisible(false);
        }
        return false;
      }
    });

    helpPanel = new HelpPanel(this);
    stage.addActor(helpPanel);

    mappedKeyStateListener = new MappedKeyStateAdapter() {
      @Override
      public void onPressed(MappedKey key, int keycode) {
        if (Riiablo.cursor != null && Riiablo.cursor.isIdentifying() && key == Keys.Esc) {
          Riiablo.cursor.resetCursor();
          return;
        }
        if (inventoryPanel.isGoldDialogOpen() || stashPanel.isGoldDialogOpen()) {
          return;
        }
        if (hireMercenaryPanel != null && hireMercenaryPanel.isVisible()) {
          return;
        }

        if (input.isVisible() && (key != Keys.Enter && key != Keys.Esc)) {
          return;
        }

        if (helpPanel.isVisible()) {
          if (key == Keys.Help || key == Keys.Esc) helpPanel.close();
          return;
        }

        if (key == Keys.Esc) {
          // D2MOD: Priority 1 - Handle player death respawn
          // If player is dead, respawn at town instead of showing menu
          if (Riiablo.game.player >= 0 && engine != null) {
            com.riiablo.engine.client.ClientUnitLifecycleSystem lifecycle = engine.getSystem(
                com.riiablo.engine.client.ClientUnitLifecycleSystem.class);
            if (lifecycle != null && lifecycle.isPlayerDead(Riiablo.game.player)) {
              lifecycle.requestRespawn(Riiablo.game.player);
              return; // Never show the menu during DT/DD
            }
          }
          
          // Priority 2 - Normal ESC key handling (show/hide menu)
          if (escapePanel.isVisible()) {
            if (!escapePanel.navigateBack()) escapePanel.close();
          } else if (input.isVisible()) {
            input.setVisible(false);
          } else if (left != null || right != null) {
            setLeftPanel(null);
            setRightPanel(null);
          } else {
            escapePanel.open();
          }
        } else if (key == Keys.Help) {
          escapePanel.close();
          setLeftPanel(null);
          setRightPanel(null);
          spellsQuickPanelL.setVisible(false);
          spellsQuickPanelR.setVisible(false);
          helpPanel.open();
        } else if (key == Keys.Enter) {
          boolean visible = !input.isVisible();
          if (!visible) {
            String text = input.getText();
            if (!text.isEmpty()) {
              Gdx.app.debug(TAG, text);
              //Message message = new Message(player.stats.getName(), text);
              //out.println(Packets.build(message));
              output.appendText(text);
              output.appendText("\n");
              input.setText("");
            }
          }

          input.setVisible(visible);
          if (visible) {
            input.setText("");
            stage.setKeyboardFocus(input);
          }
        } else if (key == Keys.Run && keycode != Input.Keys.SHIFT_LEFT) {
          toggleRunWalk();
        } else if (key == Keys.Inventory) {
          setRightPanel(inventoryPanel.isVisible() ? null : inventoryPanel);
        } else if (key == Keys.Character) {
          setLeftPanel(characterPanel.isVisible() ? null : characterPanel);
        } else if (key == Keys.Stash) {
          setLeftPanel(stashPanel.isVisible() ? null : stashPanel);
        } else if (key == Keys.Hireling) {
          setLeftPanel(hirelingPanel.isVisible() ? null : hirelingPanel);
        } else if (key == Keys.Spells) {
          setRightPanel(spellsPanel.isVisible() ? null : spellsPanel);
        } else if (key == Keys.Quests) {
          if (!questsPanel.isVisible() && controlPanel != null) {
            controlPanel.acknowledgeQuestLog();
          }
          setLeftPanel(questsPanel.isVisible() ? null : questsPanel);
        } else if (key == Keys.Party) {
          setLeftPanel(partyPanel.isVisible() ? null : partyPanel);
        } else if (key == Keys.Vendor) {
          setLeftPanel(vendorPanel.isVisible() ? null : vendorPanel);
        } else if (key == Keys.SwapWeapons) {
          itemController.intents().swapWeapons();
        } else if (key == Keys.Belt1) {
          itemController.intents().useBeltSlot(0);
        } else if (key == Keys.Belt2) {
          itemController.intents().useBeltSlot(1);
        } else if (key == Keys.Belt3) {
          itemController.intents().useBeltSlot(2);
        } else if (key == Keys.Belt4) {
          itemController.intents().useBeltSlot(3);
        }
      }
    };

    testingInputProcessor = new InputAdapter() {
      private final float ZOOM_AMOUNT = 0.1f;

      @Override
      public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (hireMercenaryPanel != null && hireMercenaryPanel.isVisible()) return false;
        if (Riiablo.cursor == null || !Riiablo.cursor.isIdentifying()) return false;
        Vector2 point = stage.screenToStageCoordinates(new Vector2(screenX, screenY));
        Actor actor = stage.hit(point.x, point.y, true);
        com.riiablo.item.Item target = null;
        if (button == Input.Buttons.LEFT) {
          target = com.riiablo.screen.panel.ItemGrid.targetItem(actor);
          if (target == null) target = inventoryPanel.targetEquippedItem(actor);
        }
        Riiablo.cursor.identify(target);
        return true;
      }

      @Override
      public boolean scrolled(float amountX, float amountY) {
        if (hireMercenaryPanel != null && hireMercenaryPanel.isVisible()) return false;
        if (helpPanel != null && helpPanel.isVisible()) return true;
        if (amountY < 0) {
          if (UIUtils.ctrl()) {
            renderer.zoom(Math.max(0.20f, renderer.zoom() - ZOOM_AMOUNT));
          }
        } else {
          if (UIUtils.ctrl()) {
            renderer.zoom(Math.min(5.00f, renderer.zoom() + ZOOM_AMOUNT));
          }
        }

        return true;
      }

      @Override
      public boolean keyDown(int keycode) {
        if (hireMercenaryPanel != null && hireMercenaryPanel.isVisible()) return false;
        if (helpPanel != null && helpPanel.isVisible()) return true;
        switch (keycode) {
          case Input.Keys.TAB:
            if (UIUtils.shift()) {
              // Shift+Tab: toggle debug walkable overlay
              RenderSystem.RENDER_DEBUG_WALKABLE = RenderSystem.RENDER_DEBUG_WALKABLE == 0 ? 1 : 0;
            } else if (UIUtils.ctrl()) {
              // Ctrl+Tab: cycle debug grid modes
              RenderSystem.RENDER_DEBUG_GRID++;
              if (RenderSystem.RENDER_DEBUG_GRID > RenderSystem.DEBUG_GRID_MODES) {
                RenderSystem.RENDER_DEBUG_GRID = 0;
              }
            } else {
              // Tab: toggle automap (like original Diablo 2)
              RenderSystem.toggleAutomap();
            }
            return true;

          // 小地图方向键平移 - 仅当小地图打开时
          case Input.Keys.UP:
            if (RenderSystem.isAutomapVisible()) {
              AutomapRenderer automapRenderer = engine.getSystem(AutomapRenderer.class);
              if (automapRenderer != null) automapRenderer.panUp();
              return true;
            }
            return false;
          case Input.Keys.DOWN:
            if (RenderSystem.isAutomapVisible()) {
              AutomapRenderer automapRenderer = engine.getSystem(AutomapRenderer.class);
              if (automapRenderer != null) automapRenderer.panDown();
              return true;
            }
            return false;
          case Input.Keys.LEFT:
            if (RenderSystem.isAutomapVisible()) {
              AutomapRenderer automapRenderer = engine.getSystem(AutomapRenderer.class);
              if (automapRenderer != null) automapRenderer.panLeft();
              return true;
            }
            return false;
          case Input.Keys.RIGHT:
            if (RenderSystem.isAutomapVisible()) {
              AutomapRenderer automapRenderer = engine.getSystem(AutomapRenderer.class);
              if (automapRenderer != null) automapRenderer.panRight();
              return true;
            }
            return false;
            
          // 小地图缩放控制
          case Input.Keys.EQUALS:
          case Input.Keys.NUMPAD_ADD:
            if (RenderSystem.isAutomapVisible()) {
              AutomapRenderer automapRenderer = engine.getSystem(AutomapRenderer.class);
              if (automapRenderer != null) automapRenderer.zoomIn();
              return true;
            }
            return false;
          case Input.Keys.MINUS:
          case Input.Keys.NUMPAD_SUBTRACT:
            if (RenderSystem.isAutomapVisible()) {
              AutomapRenderer automapRenderer = engine.getSystem(AutomapRenderer.class);
              if (automapRenderer != null) automapRenderer.zoomOut();
              return true;
            }
            return false;
          case Input.Keys.HOME:
            if (RenderSystem.isAutomapVisible()) {
              AutomapRenderer automapRenderer = engine.getSystem(AutomapRenderer.class);
              if (automapRenderer != null) automapRenderer.reset();
              return true;
            }
            return false;

          case Input.Keys.F9: {
            PathDebugger debugger = engine.getSystem(PathDebugger.class);
            debugger.setEnabled(!debugger.isEnabled());
            return true;
          }

          case Input.Keys.F11: {
            Box2DDebugger debugger = engine.getSystem(Box2DDebugger.class);
            debugger.setEnabled(!debugger.isEnabled());
            return true;
          }

          case Input.Keys.F10: {
            PathfindDebugger pathfindDebugger = engine.getSystem(PathfindDebugger.class);
            pathfindDebugger.setEnabled(!pathfindDebugger.isEnabled());
            // F10 同时切换 RenderSystemDebugger，使 D2MOD 土路路径在 drawDebug 中正确绘制
            RenderSystemDebugger renderDebugger = engine.getSystem(RenderSystemDebugger.class);
            if (renderDebugger != null) {
              renderDebugger.setEnabled(pathfindDebugger.isEnabled());
            }
            return true;
          }
          
          default:
            return false;
        }
      }
    };

    // 使用角色存档的 mapSeed 和 diff，与 D2 一致：
    // - 新角色：CreateCharacterScreen 用 System.currentTimeMillis() 初始化 mapSeed
    // - 加载角色：D2SReader 从 d2s 读取 mapSeed
    // 同一角色每次进入游戏地图相同；不同角色/新游戏地图不同
    int mapSeed = resolveMapSeed(charData);
    config = new EngineConfig(mapSeed, charData.diff);
    map = new Map(config.seed(), config.diff(), com.riiablo.map.MapResources.fromContext(
        clientGameplayContext.files(), clientGameplayContext.assets(), clientGameplayContext.mpqs(),
        clientGameplayContext.cofs(), clientGameplayContext.cvars()));
    mapManager = new MapManager();
    partyManager = new com.riiablo.engine.server.party.PartyManager();
    renderer = new RenderSystem(Riiablo.batch, map);
    iso = renderer.iso();
    scaledStage = new Stage(new ScreenViewport(iso), Riiablo.batch);
    factory = new ClientEntityFactory();
    TownPortalRegistry townPortalRegistry = new TownPortalRegistry();
    EnvironmentCycle environment = new EnvironmentCycle();
    simulationSession = new SimulationSession(
        socket == null ? SimulationMode.LOCAL : SimulationMode.REMOTE_CLIENT,
        map, environment, partyManager);
    clientAudioSession = new ClientAudioSession(simulationSession.environment(), () -> player,
        Riiablo.audio, Riiablo.files.SoundEnviron, Riiablo.files.Sounds);

    CombatPositionHistory combatPositionHistory = new CombatPositionHistory(map);
    com.riiablo.engine.server.quest.Act5QuestGameState act5QuestGameState =
        new com.riiablo.engine.server.quest.Act5QuestGameState();
    WorldConfiguration config = simulationSession.registerServices(getWorldConfiguration());
    ClientGameplayInputAdapter gameplayInput = new ClientGameplayInputAdapter(clientGameplayContext);
    config
        .register("clientSession", new ClientNetworkSession(charData, () -> player,
            new ClientNetworkSession.ResultRouter() {
              @Override public void onNpcService(com.riiablo.net.packet.d2gs.NpcServiceResult result) {
                if (hireMercenaryPanel != null) hireMercenaryPanel.applyNetworkResult(result);
                if (vendorPanel != null) vendorPanel.applyNetworkResult(result);
              }
              @Override public void onTradeState(com.riiablo.engine.client.ClientNetworkReceiver receiver) {
                if (tradePanel != null) tradePanel.applyNetworkState(receiver);
              }
            }))
        .register("iso", iso)
        .register("clientContext", clientGameplayContext)
        .register("clientActionInput", (com.riiablo.engine.client.ClientActionInputPort) gameplayInput)
        .register("clientKeyboardInput", (com.riiablo.engine.client.ClientKeyboardInputPort) gameplayInput)
        .register("networkPresentation", new ClientNetworkPresentationAdapter(clientGameplayContext))
        .register("clientActTransition", (com.riiablo.engine.client.ClientActTransitionPort)
            clientGameplayContext::transitionAct)
        .register("clientPlayer", (com.riiablo.engine.client.ClientPlayerPort)
            clientGameplayContext::playerId)
        .register("serverContext", new ServerGameplayContext(Riiablo.files, map.seed(), charData,
            Riiablo.anim, Riiablo.mpqs, data -> {
              if (Riiablo.saves != null) com.riiablo.save.D2SWriter.INSTANCE.save(data);
            }))
        .register("config", config)
        .register("factory", factory)
        .register("itemController", itemController.intents())
        .register("townPortalRegistry", townPortalRegistry)
        .register("combatPositionHistory", combatPositionHistory)
        .register("act5QuestGameState", act5QuestGameState)
        .register("batch", Riiablo.batch)
        .register("shapes", Riiablo.shapes)
        .register("stage", stage)
        .register("scaledStage", scaledStage)
        .register("pointerUi", new ClientPointerUiAdapter(stage, scaledStage,
            clientGameplayContext, () -> engine == null ? null : engine.getSystem(MenuManager.class),
            () -> engine == null ? null : engine.getSystem(DialogManager.class)))
        .register("input", input)
        .register("output", output)
        .register("clientMessages", (java.util.function.Consumer<String>) output::appendText)
        .register("simulationTickCoordinator", simulationTickCoordinator)
        ;
    if (socket != null) config.register("client.socket", socket);
    engine = Riiablo.engine = new World(config);
    renderSystemRunner = new ClientRenderSystemRunner(engine);
    interpolationSystem = engine.getSystem(AuthoritativeInterpolationSystem.class);
    Gdx.app.log(TAG, String.format(
        "[SIM_CLOCK] rate=%dHz step=%.3fs maxCatchUp=%d renderSystems=%d",
        SimulationClock.TICKS_PER_SECOND, SimulationClock.STEP_SECONDS,
        MAX_SIMULATION_STEPS_PER_RENDER, renderSystemRunner.size()));

    // Bind session capabilities and runtime metadata outside map generation.
    com.riiablo.engine.MapRuntimeBindings.bind(map, engine, factory, socket != null);

    if (mobileControls != null) engine.inject(mobileControls);

    injectPanels();

    bindItemLocationListeners();

    // FIXME: #75 Initial CharData update event
    charData.update();

    loadingScreen = new GameLoadingScreen(map, getDependencies(), sessionAssets, clientGameplayContext);
  }

  private void bindItemLocationListeners() {
    final com.riiablo.save.ItemData playerItems = charData.getItems();
    final com.riiablo.save.ItemData mercItems = charData.getMerc().getItems();
    final com.riiablo.save.ItemData.LocationListener cursor = Riiablo.cursor;
    sessionSubscriptions.add(() -> playerItems.addLocationListener(cursor),
        () -> playerItems.removeLocationListener(cursor));
    sessionSubscriptions.add(() -> mercItems.addLocationListener(cursor),
        () -> mercItems.removeLocationListener(cursor));
    sessionSubscriptions.activate();
  }

  private void injectPanels() {
    inventoryPanel.bindWorld(engine);
    vendorPanel.bindWorld(engine);
    characterPanel.bindWorld(engine);
    helpPanel.bindWorld(engine);
    spellsPanel.bindWorld(engine);
    spellsQuickPanelL.bindWorld(engine);
    spellsQuickPanelR.bindWorld(engine);
    questsPanel.bindWorld(engine);
    escapePanel.bindWorld(engine);
    cubePanel.bindWorld(engine);
    stashPanel.bindWorld(engine);
    waygatePanel.bindWorld(engine);
    if (mobilePanel != null) mobilePanel.bindWorld(engine);
    if (mobileControls != null) mobileControls.bindWorld(engine);
    hirelingPanel.bindWorld(engine);
    hireMercenaryPanel.bindWorld(engine);
    mercenaryHud.bindWorld(engine);
    controlPanel.bindWorld(engine);
    partyPanel.bindWorld(engine);
    tradePanel.bindWorld(engine);
    engine.inject(inventoryPanel);
    engine.inject(hirelingPanel);
    engine.inject(controlPanel);
    engine.inject(cubePanel);
    engine.inject(stashPanel);
    engine.inject(vendorPanel);
    if (socket != null) {
      vendorPanel.setNetworkSynchronizer(engine.getSystem(ClientNetworkSynchronizer.class));
      partyPanel.setNetworkSystems(engine.getSystem(ClientNetworkSynchronizer.class),
          engine.getSystem(com.riiablo.engine.client.ClientNetworkReceiver.class));
      tradePanel.setNetworkSystems(engine.getSystem(ClientNetworkSynchronizer.class),
          engine.getSystem(com.riiablo.engine.client.ClientNetworkReceiver.class));
    }
// TODO: maybe it would be better to do more like?:
//    for (Actor actor : stage.getActors()) {
//      engine.inject(actor);
//    }
  }

  protected WorldConfiguration getWorldConfiguration() {
    return getWorldConfigurationBuilder().build();
  }

  protected WorldConfigurationBuilder getWorldConfigurationBuilder() {
    return ClientSimulationAssembly.builder(
        simulationSession, clientAudioSession, mapManager, itemController, factory, renderer,
        !DEBUG_TOUCHPAD && Gdx.app.getType() == Application.ApplicationType.Desktop,
        DEBUG_TOUCHPAD || Gdx.app.getType() == Application.ApplicationType.Android);
  }

  public void create() {
    if (created) return;
    created = true;

    isDebug = DEBUG && Gdx.app.getType() == Application.ApplicationType.Desktop;

    if (DEBUG_TOUCHPAD || Gdx.app.getType() == Application.ApplicationType.Android) {
      touchpad = new Touchpad(10, new Touchpad.TouchpadStyle() {{
        //background = new TextureRegionDrawable(Riiablo.assets.get(touchpadBackgroundDescriptor));
        background = null;
        knob = new TextureRegionDrawable(Riiablo.assets.get(touchpadKnobDescriptor));
      }});
      touchpad.setSize(164, 164);
      touchpad.setPosition(0, mobilePanel != null ? mobilePanel.getHeight() : 0);
      stage.addActor(touchpad);
      if (!DEBUG_TOUCHPAD) touchpad.toBack();
    }

    // TODO: sort children based on custom indexes
    controlPanel.toFront();
    output.toFront();
    if (mobilePanel != null) mobilePanel.toFront();
    if (mercenaryHud != null) mercenaryHud.toFront();
//  if (mobileControls != null) mobileControls.toFront();
    if (touchpad != null) touchpad.toBack();
    input.toFront();
    escapePanel.toFront();
    helpPanel.toFront();

    if (Gdx.app.getType() == Application.ApplicationType.Android
     || Riiablo.defaultViewport.getWorldHeight() == Riiablo.MOBILE_VIEWPORT_HEIGHT) {
      renderer.zoom(Riiablo.MOBILE_VIEWPORT_HEIGHT / (float) Gdx.graphics.getHeight());
    } else {
      renderer.zoom(Riiablo.DESKTOP_VIEWPORT_HEIGHT / (float) Gdx.graphics.getHeight());
    }
    renderer.resize();
  }

  @Override
  public void resume() {
    if (disposed) return;
    discardNextSimulationDelta = true;
    Riiablo.engine = engine;
    Riiablo.game = this;
  }

  protected int resolveMapSeed(CharData charData) {
    int mapSeed = charData.mapSeed;
    if (mapSeed == 0) {
      mapSeed = (int) (System.nanoTime() & 0x7FFF_FFFF);
    }
    return mapSeed;
  }

  /** Allocates locally in single-player or sends intent to the authoritative D2GS. */
  public boolean spendSkillPoint(int skillId) {
    if (socket != null) return NetworkedActionSender.spendSkillPoint(socket, skillId);
    return new PlayerStatsManager().spendSkillPoint(charData, skillId);
  }

  /** Allocates one attribute point locally or through the authoritative D2GS. */
  public boolean spendStatPoint(int statType) {
    if (socket != null) return NetworkedActionSender.spendStatPoint(socket, statType);
    return new PlayerStatsManager().spendStatPoint(charData, statType)
        == PlayerStatsManager.RESULT_SUCCESS;
  }

  @Override
  public void pause() {
    discardNextSimulationDelta = true;
    // Do not retain a partial tick across an application pause/background
    // transition. The next visible frame starts from a clean native tick.
    simulationAccumulator.reset();
    simulationTickCoordinator.clear();
  }

  @Override
  public void resize(int width, int height) {
    super.resize(width, height);
    if (stage == null) return;

    // Client.resize updates the shared viewport before delegating here. Keep
    // the HUD root centered after a window/viewport change; child widgets,
    // including the experience bar, then re-anchor from the new layout.
    stage.getViewport().update(width, height, true);
    if (controlPanel == null) return;
    if (inventoryPanel != null) inventoryPanel.setPosition(
        stage.getWidth() - inventoryPanel.getWidth(),
        stage.getHeight() - inventoryPanel.getHeight());
    if (mercenaryHud != null) mercenaryHud.setPosition(
        8, stage.getHeight() - mercenaryHud.getHeight() - 8);
    if (Boolean.TRUE.equals(Cvars.Client.Display.KeepControlPanelGrouped.get())) {
      controlPanel.setWidth(stage.getWidth());
      controlPanel.layout();
    } else {
      controlPanel.pack();
      controlPanel.setPosition(stage.getWidth() / 2f, 0,
          Align.bottom | Align.center);
    }
    if (helpPanel != null) {
      helpPanel.setSize(stage.getWidth(), stage.getHeight());
      helpPanel.invalidate();
    }
  }

  @Override
  public void render(float delta) {
    float rawDelta = delta;
    if (discardNextSimulationDelta) {
      discardNextSimulationDelta = false;
      delta = sanitizeResumedSimulationDelta(delta);
    } else {
      delta = sanitizeSimulationDelta(delta);
    }
    if (rawDelta != delta) {
      Gdx.app.debug(TAG, String.format(
          "Adjusting simulation delta: raw=%.3fs simulation=%.3fs",
          rawDelta, delta));
    }

    TouchpadMovementSystem touchMovement = engine.getSystem(TouchpadMovementSystem.class);
    if (touchMovement != null) {
      tmpVec2b.setZero();
      if (touchpad != null && player >= 0 && engine.getMapper(Position.class).has(player)
          && !clientGameplayContext.movementBlocked()) {
        tmpVec2.set(touchpad.getKnobPercentX(), touchpad.getKnobPercentY()).nor();
        if (!tmpVec2.isZero()) {
          Vector2 position = engine.getMapper(Position.class).get(player).position;
          iso.toScreen(tmpVec2b.set(position)).add(tmpVec2);
          iso.toWorld(tmpVec2b).sub(position).nor();
          engine.getSystem(DialogManager.class).setDialog(null);
          engine.getSystem(MenuManager.class).setMenu(null, Engine.INVALID_ENTITY);
        }
      }
      touchMovement.capture(tmpVec2b.x, tmpVec2b.y);
    }

    // 检测持续按键：automap 缩放和平移（按住键持续操作）
    if (RenderSystem.isAutomapVisible()
        && (hireMercenaryPanel == null || !hireMercenaryPanel.isVisible())) {
      AutomapRenderer automapRenderer = engine.getSystem(AutomapRenderer.class);
      if (automapRenderer != null) {
        float actionInterval = 0.1f;  // 操作间隔（秒）
        
        // 检查 +/- 键是否持续按下，每 0.1 秒触发一次缩放
        if (Gdx.input.isKeyPressed(Input.Keys.EQUALS) || Gdx.input.isKeyPressed(Input.Keys.NUMPAD_ADD)) {
          automapZoomAccumulator += delta;
          if (automapZoomAccumulator >= actionInterval) {
            automapRenderer.zoomIn();
            automapZoomAccumulator = 0f;
          }
        } else if (Gdx.input.isKeyPressed(Input.Keys.MINUS) || Gdx.input.isKeyPressed(Input.Keys.NUMPAD_SUBTRACT)) {
          automapZoomAccumulator += delta;
          if (automapZoomAccumulator >= actionInterval) {
            automapRenderer.zoomOut();
            automapZoomAccumulator = 0f;
          }
        } else {
          // 缩放按键释放时重置累加器
          automapZoomAccumulator = 0f;
        }
        
        // 检查箭头键是否持续按下，每 0.1 秒触发一次平移
        boolean anyPanKeyPressed = false;
        if (Gdx.input.isKeyPressed(Input.Keys.UP)) {
          automapPanAccumulator += delta;
          if (automapPanAccumulator >= actionInterval) {
            automapRenderer.panUp();
            automapPanAccumulator = 0f;
          }
          anyPanKeyPressed = true;
        } else if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
          automapPanAccumulator += delta;
          if (automapPanAccumulator >= actionInterval) {
            automapRenderer.panDown();
            automapPanAccumulator = 0f;
          }
          anyPanKeyPressed = true;
        } else if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
          automapPanAccumulator += delta;
          if (automapPanAccumulator >= actionInterval) {
            automapRenderer.panLeft();
            automapPanAccumulator = 0f;
          }
          anyPanKeyPressed = true;
        } else if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
          automapPanAccumulator += delta;
          if (automapPanAccumulator >= actionInterval) {
            automapRenderer.panRight();
            automapPanAccumulator = 0f;
          }
          anyPanKeyPressed = true;
        }
        
        if (!anyPanKeyPressed) {
          // 所有平移按键释放时重置累加器
          automapPanAccumulator = 0f;
        }
      }
    }

    Riiablo.assets.update();
    updateTemporaryRunInput();
    CursorMovementSystem cursorMovement = engine.getSystem(CursorMovementSystem.class);
    if (cursorMovement != null) cursorMovement.capturePointerInput();
    int simulationSteps;
    renderSystemRunner.beginSimulation();
    try {
      simulationSteps = simulationAccumulator.advance(delta, stepSeconds -> {
        engine.setDelta(stepSeconds);
        long simulationStartNanos = System.nanoTime();
        simulationTickCoordinator.advance(command -> {
              if (command.payload instanceof Runnable) ((Runnable) command.payload).run();
            },
            tick -> engine.process(), event -> {
              if (event.payload instanceof Runnable) ((Runnable) event.payload).run();
            });
        long simulationElapsedNanos = System.nanoTime() - simulationStartNanos;
        if (simulationElapsedNanos > 30_000_000L
            && simulationStartNanos - lastSlowSimulationLogNanos > 1_000_000_000L) {
          lastSlowSimulationLogNanos = simulationStartNanos;
          Gdx.app.log(TAG, String.format(
              "[SIM_SLOW] elapsedMs=%.2f budgetMs=%.2f step=%.3fs systems=%s",
              simulationElapsedNanos / 1_000_000f, SimulationClock.STEP_SECONDS * 1000f,
              stepSeconds, slowSimulationSystems()));
        }
      });
    } finally {
      renderSystemRunner.endSimulation();
    }
    if (simulationSteps > 1) {
      Gdx.app.debug(TAG, String.format(
          "Fixed simulation catch-up: steps=%d step=%.3fs remainder=%.4fs",
          simulationSteps, simulationAccumulator.getStepSeconds(),
          simulationAccumulator.getAccumulated()));
    }
    closeWaygatePanelIfOutOfRange();
    if (interpolationSystem != null) interpolationSystem.beginRender(delta);
    try {
      renderSystemRunner.render(delta);
    } finally {
      if (interpolationSystem != null) interpolationSystem.endRender();
    }

    scaledStage.act(delta);
    scaledStage.draw();

    details = null;
    stage.act(delta);
    stage.draw();
    
    // Draw death message overlay if player is dead (like d2mod)
    drawDeathMessage();
    
    if (firstRender) {
      firstRender = false;
      for (Actor actor : stage.getActors()) {
        if (actor == controlPanel) continue; // FIXME: renders over belt
        if (actor instanceof EscapeController) {
          EscapeController escapeController = (EscapeController) actor;
          Actor escape = escapeController.getEscapeButton();
          if (escape == null) continue; // A retired controller no longer exposes a live control.
          escape.localToStageCoordinates(tmpVec2.set(0, 0));
          escape.setPosition(tmpVec2.x, tmpVec2.y);
          stage.addActor(escape);
        }
      }

      controlPanel.setMinipanelVisible(false);
    }

    if (details != null) {
      Riiablo.batch.begin();
      details.draw(Riiablo.batch, 1);
      Riiablo.batch.end();
    }

    //3 modes
    //  client and server (single player)
    //    pausing client pauses engine
    //    possibility of interpolating server frames
    //  client hosts server (tcp multiplayer)
    //    similar to previous -- pausing doesn't pause engine
    //  client connects server (bnet)
    //    client maintains copy of engine

    //map object
    //  reset using seed
    //  list of used dt1s
    //  list of used ds1s
  }
  
  /**
   * Draw death message overlay when player is dead
   * Reference: d2mod - shows "Press ESC to continue" message in center of screen
   */
  private void drawDeathMessage() {
    if (Riiablo.game.player < 0) {
      return;
    }
    
    DeathHandler deathHandler = engine.getSystem(DeathHandler.class);
    if (deathHandler == null) {
      return;
    }
    
    if (!deathHandler.isPlayerDead(Riiablo.game.player)) {
      return;
    }
    
    // Draw death message in center of screen
    // D2MOD: Show "Press ESC to continue" message when player is dead
    com.badlogic.gdx.graphics.g2d.BitmapFont font = Riiablo.fonts.fontformal12;
    if (font == null) {
      // Font not loaded yet, skip drawing
      return;
    }
    
    String message = Riiablo.bundle.get("death_continue");
    
    // Calculate text position (center of screen)
    float screenWidth = Gdx.graphics.getWidth();
    float screenHeight = Gdx.graphics.getHeight();
    com.badlogic.gdx.graphics.g2d.GlyphLayout layout = new com.badlogic.gdx.graphics.g2d.GlyphLayout(font, message);
    float textX = (screenWidth - layout.width) / 2;
    float textY = screenHeight / 2;
    
    // Draw text with semi-transparent background
    // Note: This is called after stage.draw(), so batch should not be active
    Riiablo.batch.begin();
    
    // Draw semi-transparent black background using white texture with color modulation
    com.badlogic.gdx.graphics.Color oldColor = Riiablo.batch.getColor();
    Riiablo.batch.setColor(0, 0, 0, 0.7f); // Black with 70% opacity
    // Use TextureRegion to draw a rectangle
    if (Riiablo.textures.white != null) {
      com.badlogic.gdx.graphics.g2d.TextureRegion whiteRegion = new com.badlogic.gdx.graphics.g2d.TextureRegion(Riiablo.textures.white);
      Riiablo.batch.draw(whiteRegion, textX - 10, textY - layout.height - 10, layout.width + 20, layout.height + 20);
    }
    Riiablo.batch.setColor(oldColor);
    
    // Draw text
    font.setColor(com.badlogic.gdx.graphics.Color.WHITE);
    font.draw(Riiablo.batch, message, textX, textY);
    Riiablo.batch.end();
  }

  @Override
  public void show() {
    if (disposed) throw new IllegalStateException("GameScreen is disposed");
    Gdx.app.log("BUILD_VERSION", "[BUILD_VERSION] version=" + RiiabloVersion.VERSION
        + " buildDate=" + RiiabloVersion.BUILD_DATE
        + " git=" + System.getProperty("riiablo.git.commit", "unknown"));
    if (map.getAct() == -1) {
      int savedAct = charData == null ? 0 : (charData.towns[charData.diff] & 0x7);
      if (savedAct < 0 || savedAct >= Riiablo.NUM_ACTS) savedAct = 0;
      setAct(savedAct);
      return;
    }

    create();

    publishPanelSession();
    configureSubscriptions();
    visibleSubscriptions.activate();

    Riiablo.viewport = viewport;
    Riiablo.music.stop();
    Riiablo.assets.get(windowopenDescriptor).play();

    if (socket == null) {
      mapManager.createEntities();
    }
    // D2Common creates river water and its ambient markers only in the
    // client DRLG. They have no D2Game entity to arrive over the socket, and
    // must not wait for the server-style RoomEx preset-unit activation used
    // by local games. Materialize them eagerly for both local and network
    // clients; per-object creationStatus keeps the local pass idempotent.
    mapManager.createClientPresentationEntities();

    engine.getSystem(Box2DPhysics.class).createBodies();

    Levels.Entry waypointTarget = pendingWaypointTarget;
    Vector2 origin = waypointTarget != null && waypointTarget.Act == map.getAct()
        ? mapManager.findWaypointPosition(waypointTarget, new Vector2())
        : null;
    if (origin != null) {
      Gdx.app.log(TAG, "Arriving at waypoint: level=" + waypointTarget.LevelName
          + "(" + waypointTarget.Id + ") position=" + origin);
    }
    if (waypointTarget != null && origin == null) {
      Gdx.app.error(TAG, "Unable to travel to cross-act waypoint: level="
          + waypointTarget.LevelName + "(" + waypointTarget.Id
          + "); falling back to current-act town spawn, not a successful waypoint arrival");
    }
    if (origin == null) origin = map.townPlayerSpawn(3);
    if (origin == null) origin = map.find(Map.ID.TOWN_ENTRY_1);
    if (origin == null) origin = map.find(Map.ID.TOWN_ENTRY_2);
    if (origin == null) origin = map.find(Map.ID.TP_LOCATION);
    Map.Zone zone = origin != null ? map.getZone(origin) : null;
    if (origin == null || zone == null) {
      // 无传送点或 D2MOO 回退布局时：使用当前 Act 的城镇 zone 中心。
      // 旧代码硬编码 Levels id=1，只对 Act I 有效；Act III–V 会因此
      // 找不到出生区，留下 INVALID_ENTITY 并在首个模拟 tick 崩溃。
      for (Map.Zone candidate : map.getZones()) {
        if (candidate == null || !candidate.isTown()
            || candidate.levelAct() != map.getAct() + 1) continue;
        origin = new Vector2(candidate.x() + candidate.width() / 2f,
            candidate.y() + candidate.height() / 2f);
        zone = candidate;
        Gdx.app.log(TAG, "Using current-act town center fallback: level="
            + candidate.levelId() + " position=" + origin);
        break;
      }
    }
    if (origin == null || zone == null) {
      Gdx.app.error("GameScreen", "No spawn position or zone available");
      return;
    }
    player = factory.createPlayer(charData, origin);
    engine.getSystem(EventSystem.class).dispatch(ZoneChangeEvent.obtain(player, zone));
    if (socket == null && charData.getCorpseItems().size > 0) {
      DeathHandler deathHandler = engine.getSystem(DeathHandler.class);
      if (deathHandler != null) deathHandler.restoreSavedCorpse(player, charData, origin);
    }
    if (socket == null && charData.hasMerc()) {
      NativeMercenaryRewardSystem mercenaries =
          engine.getSystem(NativeMercenaryRewardSystem.class);
      if (mercenaries == null || !mercenaries.restorePersistedMercenary(player)) {
        Gdx.app.error(TAG, "Failed to restore persisted mercenary for " + charData.name);
      }
    }
    pendingWaypointTarget = null;

    if (socket == null) {
      // Artemis dispatches entity-insert callbacks when the world is
      // processed.  MapManager created the town entities before the player,
      // but the presentation pass must run after both are present: systems
      // such as sound/automap use the local player as their listener.  The
      // first process tick now publishes the NPC COF/DCC requests, including
      // Warriv's immediate NU -> WL transition, before the screen is visible.
      long presentationStart = System.nanoTime();
      // CursorMovementSystem is part of the normal world tick and reads the
      // renderer source entity.  Set it before the initialization tick so the
      // prewarm pass cannot observe its default INVALID_ENTITY value.
      renderer.setSrc(player);
      engine.process();
      processInitialPresentationPass(); // queue COF descriptors
      Riiablo.assets.finishLoading();    // decode COFs
      processInitialPresentationPass(); // queue DCC/DC6 layers
      Riiablo.assets.finishLoading();    // decode layers
      processInitialPresentationPass(); // attach decoded layers to Animation
      // Warriv starts in NU, then approaches the player after his first AI
      // delay.  That WL transition happens after this generic pipeline, so
      // retain its COF/DCC explicitly while the loading screen is still up.
      prewarmMonsterMode(MonsterType.WARRIV, Engine.Monster.MODE_WL);
      long presentationElapsed = System.nanoTime() - presentationStart;
      if (presentationElapsed >= 1_000_000L) {
        Gdx.app.log(TAG, String.format(
            "[INITIAL_PRESENTATION_ASSETS] elapsedMs=%.2f queuedTownEntities=true",
            presentationElapsed / 1_000_000f));
      }
    }

    renderer.updatePosition(true);
  }

  /** Replay the already computed UI snapshot only after this owner is published. */
  void publishPanelSession() {
    Riiablo.game = this;
    charData.notifySkillChanged();
  }

  /**
   * Runs only the presentation-resource systems used to prime initial town
   * assets. A complete ECS tick is unsafe here because the player entity is
   * created later in show(), while input/AI systems already become eligible
   * as soon as MapManager creates the town entities.
   */
  private void processInitialPresentationPass() {
    engine.getSystem(CofManager.class).process();
    engine.getSystem(AnimDataResolver.class).process();
    engine.getSystem(CofResolver.class).process();
    engine.getSystem(CofLoader.class).process();
    engine.getSystem(CofLayerLoader.class).process();
    engine.getSystem(CofLayerCacher.class).process();
  }

  /**
   * Loads and retains one alternate monster animation mode without changing
   * the entity's authoritative mode.  This is used for town NPC transitions
   * that occur only after an AI delay and therefore cannot be discovered by
   * the ordinary initial presentation pass.
   */
  private void prewarmMonsterMode(int monsterType, byte mode) {
    ComponentMapper<Monster> monsters = engine.getMapper(Monster.class);
    ComponentMapper<Class> classes = engine.getMapper(Class.class);
    ComponentMapper<CofReference> references = engine.getMapper(CofReference.class);
    ComponentMapper<CofComponents> components = engine.getMapper(CofComponents.class);
    IntBag entities = engine.getAspectSubscriptionManager()
        .get(Aspect.all(Monster.class, Class.class, CofReference.class, CofComponents.class))
        .getEntities();

    for (int i = 0, size = entities.size(); i < size; i++) {
      int entityId = entities.get(i);
      Monster monster = monsters.get(entityId);
      if (monster == null || monster.monstats == null
          || monster.monstats.hcIdx != monsterType) continue;

      Class.Type logicalType = classes.get(entityId).type;
      CofReference reference = references.get(entityId);
      Class.Type type = reference.effectiveType(logicalType);
      String token = reference.effectiveToken();
      byte wclass = reference.effectiveWClass();
      String modeName = type.getMode(mode);
      String cofName = token + modeName + Engine.getWClass(wclass);
      String cofPath = type.PATH + '\\' + token + "\\cof\\" + cofName + ".cof";
      if (!Riiablo.mpqs.contains(cofPath)) {
        Gdx.app.error(TAG, "[NPC_PRESENTATION_PREWARM] missingCof=" + cofPath);
        return;
      }

      AssetDescriptor<COF> cofDescriptor = new AssetDescriptor<>(cofPath, COF.class);
      sessionAssets.load(cofDescriptor);
      sessionAssets.assets.finishLoading();
      COF cof = sessionAssets.assets.get(cofDescriptor);

      Array<AssetDescriptor<? extends DC>> layerDescriptors = new Array<>();
      int[] componentCodes = components.get(entityId).component;
      for (int layerIndex = 0; layerIndex < cof.getNumLayers(); layerIndex++) {
        COF.Layer layer = cof.getLayer(layerIndex);
        int component = layer.component;
        int componentCode = componentCodes[component];
        if (componentCode == CofComponents.COMPONENT_NIL) continue;
        if (componentCode == CofComponents.COMPONENT_NULL) {
          componentCode = CofComponents.COMPONENT_LIT;
        }

        String composite = Engine.getComposite(component);
        String basePath = type.PATH + '\\' + token + '\\' + composite + '\\'
            + token + composite + type.COMP[componentCode] + modeName + layer.weaponClass;
        AssetDescriptor<? extends DC> descriptor;
        String dccPath = basePath + '.' + DCC.EXT;
        if (Riiablo.mpqs.contains(dccPath)) {
          descriptor = new AssetDescriptor<>(dccPath, DCC.class);
        } else {
          String dc6Path = basePath + '.' + DC6.EXT;
          if (!Riiablo.mpqs.contains(dc6Path)) {
            Gdx.app.error(TAG, "[NPC_PRESENTATION_PREWARM] missingLayer=" + basePath);
            continue;
          }
          descriptor = new AssetDescriptor<>(dc6Path, DC6.class);
        }
        sessionAssets.load(descriptor);
        layerDescriptors.add(descriptor);
      }

      sessionAssets.assets.finishLoading();
      for (AssetDescriptor<? extends DC> descriptor : layerDescriptors) {
        sessionAssets.assets.get(descriptor).loadDirections();
      }
      Gdx.app.log(TAG, "[NPC_PRESENTATION_PREWARM] entity=" + entityId
          + " monster=" + monster.monstats.Id + " mode=" + modeName
          + " layers=" + layerDescriptors.size + " action=complete");
      return;
    }

    Gdx.app.error(TAG, "[NPC_PRESENTATION_PREWARM] monsterType=" + monsterType
        + " mode=" + mode + " result=entity_not_found");
  }

  private void configureSubscriptions() {
    if (subscriptionsConfigured) return;
    subscriptionsConfigured = true;
    final Client client = Riiablo.client;
    final Client.InputProcessor inputProcessors = Riiablo.input;
    visibleSubscriptions.add(() -> Keys.DebugMode.addStateListener(debugKeyListener),
        () -> Keys.DebugMode.removeStateListener(debugKeyListener));
    for (MappedKey key : new MappedKey[] {Keys.Esc, Keys.Help, Keys.Enter, Keys.Run,
        Keys.Inventory, Keys.Character, Keys.Hireling, Keys.Spells, Keys.Quests,
        Keys.SwapWeapons, Keys.Stash, Keys.Vendor, Keys.Belt1, Keys.Belt2, Keys.Belt3, Keys.Belt4}) {
      visibleSubscriptions.add(() -> key.addStateListener(mappedKeyStateListener),
          () -> key.removeStateListener(mappedKeyStateListener));
    }
    for (InputProcessor processor : new InputProcessor[] {testingInputProcessor, stage, scaledStage}) {
      visibleSubscriptions.add(() -> inputProcessors.addProcessor(processor),
          () -> inputProcessors.removeProcessor(processor));
    }
    visibleSubscriptions.add(() -> {
      screenBoundsListener = new Client.ScreenBoundsListener() {
        final float THRESHOLD = 150;
        float prevY = 0;

        @Override
        public void updateScreenBounds(float x, float y, float width, float height) {
          if (y < THRESHOLD && prevY >= THRESHOLD) input.setVisible(false);
          input.setPosition(stage.getWidth() / 2, y + 100, Align.bottom | Align.center);
          prevY = y;
        }
      };
      client.addScreenBoundsListener(screenBoundsListener);
      screenBoundsListener.updateScreenBounds(0, 0, 0, 0);
    }, () -> client.removeScreenBoundsListener(screenBoundsListener));
    if (!DEBUG_MOBILE && Gdx.app.getType() == Application.ApplicationType.Desktop) {
      Cvar.StateListener<Boolean> groupedListener = new CvarStateAdapter<Boolean>() {
        @Override
        public void onChanged(Cvar<Boolean> cvar, Boolean from, Boolean to) {
          if (to) {
            controlPanel.pack();
            controlPanel.setPosition(stage.getWidth() / 2, 0, Align.bottom | Align.center);
          } else {
            controlPanel.setX(0);
            controlPanel.setWidth(stage.getWidth());
            controlPanel.layout();
          }
        }
      };
      visibleSubscriptions.add(
          () -> Cvars.Client.Display.KeepControlPanelGrouped.addStateListener(groupedListener),
          () -> Cvars.Client.Display.KeepControlPanelGrouped.removeStateListener(groupedListener));
    }
  }

  @Override
  public void hide() {
    if (disposed) return;
    visibleSubscriptions.deactivate();
    // Persist exploration on every screen transition as well as final
    // disposal.  Waypoint/act loading keeps this GameScreen on the screen
    // stack, so waiting for dispose would otherwise lose the latest reveal.
    if (engine != null) {
      AutomapRenderer automapRenderer = engine.getSystem(AutomapRenderer.class);
      if (automapRenderer != null) automapRenderer.saveNativeAutomap();
    }
  }

  @Override
  public void dispose() {
    if (disposed) return;
    disposed = true;
    try (Disposal cleanup = new Disposal()) {
      cleanup.run(visibleSubscriptions::dispose);
      cleanup.run(sessionSubscriptions::dispose);
      cleanup.run(() -> {
        if (!characterSavedForExit && !saveCharacter()) {
          Gdx.app.error(TAG, "Failed to save character while disposing GameScreen");
        }
      });
      cleanup.run(() -> charData.clearListeners());
      cleanup.run(() -> {
        AutomapRenderer automapRenderer = engine.getSystem(AutomapRenderer.class);
        if (automapRenderer != null) automapRenderer.saveNativeAutomap();
      });
      cleanup.run(() -> engine.dispose());
      disposeStage(stage, cleanup);
      if (scaledStage != stage) disposeStage(scaledStage, cleanup);
      // Save exploration and retire map consumers before releasing map textures.
      cleanup.run(() -> map.dispose());
      cleanup.run(() -> { if (loadingScreen != null) loadingScreen.dispose(); });
      cleanup.run(sessionAssets::dispose);
    } finally {
      // An old screen must never clear a newer session's legacy aliases.
      if (Riiablo.game == this) Riiablo.game = null;
      if (Riiablo.engine == engine) Riiablo.engine = null;
    }
  }

  private static void disposeStage(Stage stage, Disposal cleanup) {
    if (stage == null) return;
    // Only Button's own sound reference is recursive. Existing panel dispose
    // methods remain responsible for their other children and shared assets.
    cleanup.run(() -> com.riiablo.widget.Button.disposeClickSounds(stage.getRoot()));
    cleanup.run(() -> {
      // Actors may remove themselves while disposing. Keep the original list stable.
      for (Actor actor : stage.getActors().toArray(Actor.class)) {
        if (actor instanceof Disposable) cleanup.dispose((Disposable) actor);
      }
    });
    // Both stages borrow Client's batch. Stage.dispose only clears their own state.
    cleanup.dispose(stage);
  }

  /** Persists the local character before leaving the current game. */
  public boolean saveCharacter() {
    if (charData == null || !charData.isManaged()) {
      characterSavedForExit = true;
      return true;
    }
    if (Riiablo.saves == null) {
      Gdx.app.error(TAG, "Cannot save character: save directory is unavailable");
      return false;
    }

    characterSavedForExit = D2SWriter.INSTANCE.save(charData);
    boolean keySaved = Riiablo.keys == null || Riiablo.keys.saveCharacterKey(
        Riiablo.saves.child(charData.name + ".key"));
    if (!keySaved) {
      Gdx.app.error(TAG, "Failed to save native key bindings for " + charData.name);
    }
    characterSavedForExit &= keySaved;
    return characterSavedForExit;
  }

  public boolean toggleRunWalk() {
    if (engine == null || player < 0) return false;
    VelocityModeChanger movement = engine.getSystem(VelocityModeChanger.class);
    return movement != null && movement.toggleRunWalk(player);
  }

  public boolean isRunModeEnabled() {
    if (engine == null || player < 0) return false;
    VelocityModeChanger movement = engine.getSystem(VelocityModeChanger.class);
    return movement != null && movement.isRunning(player);
  }

  private void updateTemporaryRunInput() {
    if (engine == null || player < 0 || Gdx.input == null) return;
    boolean controlPressed = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
        || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
    com.artemis.ComponentMapper<TemporaryRunning> temporaryRunning =
        engine.getMapper(TemporaryRunning.class);
    if (shouldUseTemporaryRun(controlPressed, isRunModeEnabled())) {
      temporaryRunning.create(player);
    } else {
      temporaryRunning.remove(player);
    }
  }

  static boolean shouldUseTemporaryRun(boolean controlPressed, boolean persistentRun) {
    return controlPressed && !persistentRun;
  }

  /** Closes paired item-trading panels before a world movement command is issued. */
  public boolean closeTradePanelsForMovement() {
    if (tradePanel != null && tradePanel.isVisible()) {
      tradePanel.setVisible(false);
      return true;
    }
    if (!shouldCloseTradePanelsForMovement(
        inventoryPanel != null && inventoryPanel.isVisible(),
        stashPanel != null && stashPanel.isVisible(),
        vendorPanel != null && vendorPanel.isVisible())) {
      return false;
    }

    // Use the panel setters so VendorPanel also clears its active trade state.
    setLeftPanel(null);
    setRightPanel(null);
    return true;
  }

  /** Closes both halves of the vendor interaction and clears its active mode. */
  public void closeVendorTradePanels() {
    setLeftPanel(null);
    setRightPanel(null);
  }

  /** Cancels a vendor Buy/Sell/Repair mode while preserving the open panels. */
  public void cancelVendorTradeMode() {
    if (vendorPanel != null && vendorPanel.isVisible()) vendorPanel.cancelTradeMode();
  }

  /** Opens the network trade window after an invitation or accepted request. */
  public void openTradePanel() {
    setLeftPanel(tradePanel);
  }

  static boolean shouldCloseTradePanelsForMovement(
      boolean inventoryVisible, boolean stashVisible, boolean vendorVisible) {
    return inventoryVisible && (stashVisible || vendorVisible);
  }

  public void openWaygatePanel(int waypointEntity) {
    activeWaygateEntity = waypointEntity;
    waygatePanel.refresh(map.getAct());
    setLeftPanel(waygatePanel);
  }

  private void closeWaygatePanelIfOutOfRange() {
    if (activeWaygateEntity == Engine.INVALID_ENTITY) return;
    if (waygatePanel == null || !waygatePanel.isVisible() || engine == null
        || player == Engine.INVALID_ENTITY) {
      activeWaygateEntity = Engine.INVALID_ENTITY;
      return;
    }

    ComponentMapper<Position> positions = engine.getMapper(Position.class);
    if (!positions.has(player) || !positions.has(activeWaygateEntity)) {
      setLeftPanel(null);
      return;
    }

    Interactable waypoint = engine.getMapper(Interactable.class).get(activeWaygateEntity);
    Size playerSize = engine.getMapper(Size.class).get(player);
    Vector2 playerPosition = positions.get(player).position;
    Vector2 waypointPosition = positions.get(activeWaygateEntity).position;
    if (isWaygateWithinRange(playerPosition, waypointPosition, waypoint, playerSize)) return;

    Gdx.app.debug(TAG, "Closing waypoint panel after leaving interaction range: entity="
        + activeWaygateEntity + " distance=" + playerPosition.dst(waypointPosition));
    setLeftPanel(null);
  }

  static boolean isWaygateWithinRange(Vector2 playerPosition, Vector2 waypointPosition,
      Interactable waypoint, Size playerSize) {
    return playerPosition != null && waypointPosition != null && waypoint != null
        && InteractionRange.contains(playerPosition.dst(waypointPosition), waypoint, playerSize);
  }

  public void setRightPanel(Actor actor) {
    if (right != null) {
      right.setVisible(false);
      right = null;
    }
    if (actor == null) return;
    actor.setVisible(true);
    // The control panel is intentionally brought to the front during screen
    // creation.  An opened equipment/side panel must be promoted afterwards,
    // otherwise the health/mana bar paints over its lower edge.
    actor.toFront();
    right = actor;
  }

  public void setLeftPanel(Actor actor) {
    if (actor == null || actor != waygatePanel) activeWaygateEntity = Engine.INVALID_ENTITY;
    if (left != null) {
      left.setVisible(false);
      left = null;
    }
    if (actor == null) return;
    actor.setVisible(true);
    actor.toFront();
    left = actor;
  }

  /** Returns the three slowest CPU Artemis systems from the just-finished Tick. */
  private String slowSimulationSystems() {
    if (engine == null) return "[]";
    ProfilerManager manager = engine.getSystem(ProfilerManager.class);
    if (manager == null) return "[]";
    BaseSystem[] slowest = new BaseSystem[3];
    float[] times = new float[3];
    for (int i = 0; i < engine.getSystems().size(); i++) {
      BaseSystem system = engine.getSystems().get(i);
      if (system == null || system.getClass().isAnnotationPresent(GpuSystem.class)) continue;
      SystemProfiler profiler = manager.getFor(system);
      if (profiler == null) continue;
      float millis = profiler.getLastSampleMillis();
      for (int slot = 0; slot < times.length; slot++) {
        if (millis <= times[slot]) continue;
        for (int move = times.length - 1; move > slot; move--) {
          times[move] = times[move - 1];
          slowest[move] = slowest[move - 1];
        }
        times[slot] = millis;
        slowest[slot] = system;
        break;
      }
    }
    StringBuilder result = new StringBuilder("[");
    boolean first = true;
    for (int i = 0; i < slowest.length; i++) {
      if (slowest[i] == null || times[i] <= 0f) continue;
      if (!first) result.append(", ");
      first = false;
      result.append(slowest[i].getClass().getSimpleName()).append('=').append(
          String.format(java.util.Locale.ROOT, "%.2fms", times[i]));
    }
    return result.append(']').toString();
  }

  /** True when an inventory/character/quest/vendor panel is actually visible. */
  public boolean hasVisibleSidePanel() {
    return (left != null && left.isVisible()) || (right != null && right.isVisible());
  }

  // TODO: #90
  public void setDetails(Actor details, Item item, Actor parent, Actor slot) {
    if (this.details == details) return;
    this.details = details;
    if (slot != null) {
      details.setPosition(slot.getX() + slot.getWidth() / 2, slot.getY() + slot.getHeight(), Align.bottom | Align.center);
      tmpVec2.set(details.getX(), details.getY());
      parent.localToStageCoordinates(tmpVec2);
      tmpVec2.x = MathUtils.clamp(tmpVec2.x, 0, stage.getWidth()  - details.getWidth());
      tmpVec2.y = MathUtils.clamp(tmpVec2.y, 0, stage.getHeight() - details.getHeight());
      details.setPosition(tmpVec2.x, tmpVec2.y);
      tmpVec2.set(slot.getX(), slot.getY());
      parent.localToStageCoordinates(tmpVec2);
      if (details.getY() < tmpVec2.y + slot.getHeight()) {
        details.setPosition(slot.getX() + slot.getWidth() / 2, slot.getY(), Align.top | Align.center);
        tmpVec2.set(details.getX(), details.getY());
        parent.localToStageCoordinates(tmpVec2);
        tmpVec2.x = MathUtils.clamp(tmpVec2.x, 0, stage.getWidth()  - details.getWidth());
        tmpVec2.y = MathUtils.clamp(tmpVec2.y, 0, stage.getHeight() - details.getHeight());
        details.setPosition(tmpVec2.x, tmpVec2.y);
      }
    } else {
      details.setPosition(item.getX() + item.getWidth() / 2, item.getY(), Align.top | Align.center);
      tmpVec2.set(details.getX(), details.getY());
      parent.localToStageCoordinates(tmpVec2);
      tmpVec2.x = MathUtils.clamp(tmpVec2.x, 0, stage.getWidth()  - details.getWidth());
      tmpVec2.y = MathUtils.clamp(tmpVec2.y, 0, stage.getHeight() - details.getHeight());
      details.setPosition(tmpVec2.x, tmpVec2.y);
    }
  }

  public void setAct(int act) {
    if (charData != null && act >= 0 && act < Riiablo.NUM_ACTS) {
      // D2S stores the character's current town/act in the low three bits;
      // preserve the expansion/active flag in bit 7.
      charData.towns[charData.diff] = (byte) ((charData.towns[charData.diff] & 0xF8) | act);
    }
    player = Engine.INVALID_ENTITY;
    IntBag entities = engine.getAspectSubscriptionManager().get(Aspect.all()).getEntities();
    for (int i = 0, size = entities.size(); i < size; i++) {
      engine.delete(entities.get(i));
    }

    engine.getSystem(Box2DPhysics.class).clear();

    loadingScreen = loadingScreen.nextLoad();
    loadingScreen.loadAct(act);
    Riiablo.client.pushScreen(loadingScreen);
  }

  public void setLevel(Levels.Entry target) {
    assert target.Waypoint != 0xFF;
    if (socket != null) {
      Gdx.app.error(TAG, "Waypoint travel requires a server-authoritative request in multiplayer; "
          + "rejecting client-side travel to " + target.LevelName + "(" + target.Id + ")");
      return;
    }
    if (!charData.isWaypointActivated(target.Act, target.Waypoint)) {
      Gdx.app.error(TAG, "Rejected travel to inactive waypoint: level=" + target.LevelName
          + "(" + target.Id + ") act=" + target.Act + " index=" + target.Waypoint);
      waygatePanel.refresh();
      return;
    }

    if (target.Act != map.getAct()) {
      pendingWaypointTarget = target;
      setLeftPanel(null);
      setAct(target.Act);
      return;
    }

    Vector2 destination = mapManager.findWaypointPosition(target, new Vector2());
    if (destination == null) {
      Gdx.app.error(TAG, "Unable to travel: target waypoint entity is missing for "
          + target.LevelName + "(" + target.Id + ")");
      return;
    }

    Actioneer actioneer = engine.getSystem(Actioneer.class);
    actioneer.moveTo(player, Engine.INVALID_ENTITY);

    Position position = engine.getMapper(Position.class).get(player);
    position.invalidateNativePrecision();
    position.position.set(destination);
    Box2DBody box2d = engine.getMapper(Box2DBody.class).get(player);
    if (box2d != null && box2d.body != null) box2d.body.setTransform(destination, 0);

    Map.Zone zone = map.getZone(destination);
    MapWrapper mapWrapper = engine.getMapper(MapWrapper.class).get(player);
    mapWrapper.set(map, zone);
    engine.getSystem(EventSystem.class).dispatch(ZoneChangeEvent.obtain(player, zone));
    // Waypoint travel uses the same one-shot transition sound as entering a
    // town portal.  The waypoint pad ambience is a looping object sound and
    // is not the travel confirmation effect.
    if (Riiablo.audio != null) Riiablo.audio.play("player_townportal_enter", true);

    setLeftPanel(null);
    renderer.updatePosition(true);
    Gdx.app.log(TAG, "Waypoint travel complete: level=" + target.LevelName
        + "(" + target.Id + ") position=" + destination);
  }

  static float sanitizeSimulationDelta(float delta) {
    if (!Float.isFinite(delta) || delta < 0f
        || delta > BACKGROUND_DELTA_THRESHOLD) {
      return SimulationClock.STEP_SECONDS;
    }
    return Math.min(delta, MAX_SIMULATION_DELTA);
  }

  static float sanitizeResumedSimulationDelta(float delta) {
    return Math.min(sanitizeSimulationDelta(delta), SimulationClock.STEP_SECONDS);
  }
}

