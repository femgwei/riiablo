package com.riiablo.screen;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.managers.TagManager;
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
import com.riiablo.camera.IsometricCamera;
import com.riiablo.codec.Animation;
import com.riiablo.codec.DC6;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.Sounds;
import com.riiablo.cvar.Cvar;
import com.riiablo.cvar.CvarStateAdapter;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EngineConfig;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.client.AnimationStepper;
import com.riiablo.engine.client.Act1QuestDialogController;
import com.riiablo.engine.client.Act1QuestIndicatorSystem;
import com.riiablo.engine.client.ActTransitionSystem;
import com.riiablo.engine.client.AutoInteracter;
import com.riiablo.engine.client.AutomapRenderer;
import com.riiablo.engine.client.ClientEntityFactory;
import com.riiablo.engine.client.ClientItemManager;
import com.riiablo.engine.client.ClientNetworkSynchronizer;
import com.riiablo.engine.client.CofAlphaHandler;
import com.riiablo.engine.client.CofLayerCacher;
import com.riiablo.engine.client.CofLayerLoader;
import com.riiablo.engine.client.CofLayerUnloader;
import com.riiablo.engine.client.CofLoader;
import com.riiablo.engine.client.CofResolver;
import com.riiablo.engine.client.CofTransformHandler;
import com.riiablo.engine.client.CofUnloader;
import com.riiablo.engine.client.CursorMovementSystem;
import com.riiablo.engine.client.KeyboardMovementSystem;
import com.riiablo.engine.client.DamageHandler;
import com.riiablo.engine.client.DeathHandler;
import com.riiablo.engine.client.CorpseManager;
import com.riiablo.engine.client.DialogManager;
import com.riiablo.engine.client.DirectionResolver;
import com.riiablo.engine.client.FootstepEmitter;
import com.riiablo.engine.client.HoveredManager;
import com.riiablo.engine.client.ItemEffectManager;
import com.riiablo.engine.client.ItemLoader;
import com.riiablo.engine.client.LabelManager;
import com.riiablo.engine.client.MenuManager;
import com.riiablo.engine.client.MissileLoader;
import com.riiablo.engine.client.MonsterLabelManager;
import com.riiablo.engine.client.NetworkIdManager;
import com.riiablo.engine.client.NetworkedClientItemManager;
import com.riiablo.engine.client.NetworkedActionSender;
import com.riiablo.engine.client.OverlayManager;
import com.riiablo.engine.client.OverlayStepper;
import com.riiablo.engine.client.SelectableManager;
import com.riiablo.engine.client.SkillCastHandler;
import com.riiablo.engine.client.SoundEmitterHandler;
import com.riiablo.engine.client.WarpSubstManager;
import com.riiablo.engine.client.ZoneChangeTracker;
import com.riiablo.engine.client.ZoneEntryDisplayer;
import com.riiablo.engine.client.debug.Box2DDebugger;
import com.riiablo.engine.client.debug.PathDebugger;
import com.riiablo.engine.client.debug.PathfindDebugger;
import com.riiablo.engine.client.debug.RenderSystemDebugger;
import com.riiablo.engine.server.AIStepper;
import com.riiablo.engine.server.RoomActivationSystem;
import com.riiablo.engine.server.RoomEntityTrackingSystem;
import com.riiablo.engine.server.Actioneer;
import com.riiablo.engine.server.AngularVelocity;
import com.riiablo.engine.server.AnimDataResolver;
import com.riiablo.engine.server.AnimStepper;
import com.riiablo.engine.server.StateUpdater;
import com.riiablo.engine.server.PlayerCorpseRetrievalSystem;
import com.riiablo.engine.server.quest.Act1QuestSystem;
import com.riiablo.engine.server.quest.NativeMercenaryRewardSystem;
import com.riiablo.engine.server.quest.NativeCountessRewardSystem;
import com.riiablo.engine.server.quest.NativeCharsiImbueSystem;
import com.riiablo.engine.server.Box2DDisposer;
import com.riiablo.engine.server.Box2DSynchronizerPost;
import com.riiablo.engine.server.Box2DSynchronizerPre;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.ItemInteractor;
import com.riiablo.engine.server.ItemManager;
import com.riiablo.engine.server.ObjectCollisionUpdater;
import com.riiablo.engine.server.ObjectInitializer;
import com.riiablo.engine.server.ObjectInteractor;
import com.riiablo.engine.server.object.NativeObjectDropSystem;
import com.riiablo.engine.server.object.NativeShrineSystem;
import com.riiablo.engine.server.Pathfinder;
import com.riiablo.engine.server.LeapSystem;
import com.riiablo.engine.server.PlayerItemHandler;
import com.riiablo.engine.server.SequenceHandler;
import com.riiablo.engine.server.MissileCollisionSystem;
import com.riiablo.engine.server.ServerSkillSystem;
import com.riiablo.engine.server.DeathRewardSystem;
import com.riiablo.engine.server.player.PlayerStatsManager;
import com.riiablo.attributes.ExperienceManager;
import com.riiablo.engine.server.VelocityModeChanger;
import com.riiablo.engine.server.WarpInteractor;
import com.riiablo.engine.server.ZoneMovementModesChanger;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.Box2DBody;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.ZoneChangeEvent;
import com.riiablo.graphics.PaletteIndexedColorDrawable;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.VendorGenerator;
import com.riiablo.key.MappedKey;
import com.riiablo.key.MappedKeyStateAdapter;
import com.riiablo.map.Act1MapBuilder;
import com.riiablo.map.Act1MapBuilderD2MOD;
import com.riiablo.map.Box2DPhysics;
import com.riiablo.map.Map;
import com.riiablo.map.MapManager;
import com.riiablo.map.RenderSystem;
import com.riiablo.profiler.ProfilerPlugin;
import com.riiablo.save.CharData;
import com.riiablo.screen.panel.CharacterPanel;
import com.riiablo.screen.panel.ControlPanel;
import com.riiablo.screen.panel.CubePanel;
import com.riiablo.screen.panel.EscapeController;
import com.riiablo.screen.panel.EscapePanel;
import com.riiablo.screen.panel.HirelingPanel;
import com.riiablo.screen.panel.InventoryPanel;
import com.riiablo.screen.panel.MobileControls;
import com.riiablo.screen.panel.MobilePanel;
import com.riiablo.screen.panel.QuestsPanel;
import com.riiablo.screen.panel.PartyPanel;
import com.riiablo.screen.panel.SpellsPanel;
import com.riiablo.screen.panel.SpellsQuickPanel;
import com.riiablo.screen.panel.StashPanel;
import com.riiablo.screen.panel.VendorPanel;
import com.riiablo.screen.panel.WaygatePanel;
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

  /**
   * D2 advances authoritative game state at 25 Hz and, in its bounded update
   * path, retains at most one additional 40 ms tick after a delayed frame.
   * Keep the same upper bound while client and server systems still share one
   * Artemis world; a multi-step loop here would also repeat input and renders.
   */
  static final float MAX_SIMULATION_DELTA = Animation.FRAME_DURATION * 2f;

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


  Stage stage;
  Stage scaledStage;
  Viewport viewport;
  GameLoadingScreen loadingScreen;
  boolean created;
  boolean isDebug;
  boolean discardNextSimulationDelta;
  
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
  Socket socket;

  EngineConfig config;
  Map map;
  MapManager mapManager;
  com.riiablo.engine.server.party.PartyManager partyManager;
  Levels.Entry pendingWaypointTarget;
  IsometricCamera iso;
  InputProcessor testingInputProcessor;

  ClientItemManager itemController;

  public EscapePanel escapePanel;
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
  public WaygatePanel waygatePanel;
  public QuestsPanel questsPanel;
  public PartyPanel partyPanel;
  public CubePanel cubePanel;
  public VendorPanel vendorPanel;
  public SpellsQuickPanel spellsQuickPanelL;
  public SpellsQuickPanel spellsQuickPanelR;

  Actor details;

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

    escapePanel = new EscapePanel();
    stage.addActor(escapePanel);

    controlPanel = new ControlPanel();
    controlPanel.setPosition(stage.getWidth() / 2, 0, Align.bottom | Align.center);
    controlPanel.pack();
    stage.addActor(controlPanel);

    if (DEBUG_MOBILE || Gdx.app.getType() == Application.ApplicationType.Android) {
      mobilePanel = new MobilePanel();
      mobilePanel.setPosition(0, 0);
      mobilePanel.setWidth(stage.getWidth());
      stage.addActor(mobilePanel);

      mobileControls = new MobileControls();
      mobileControls.setPosition(
          stage.getWidth() - mobileControls.getWidth(),
          mobilePanel.getHeight());
      stage.addActor(mobileControls);
    }

    inventoryPanel = new InventoryPanel();
    inventoryPanel.setPosition(
        stage.getWidth() - inventoryPanel.getWidth(),
        stage.getHeight() - inventoryPanel.getHeight());
    stage.addActor(inventoryPanel);

    hirelingPanel = new HirelingPanel();
    hirelingPanel.setPosition(0, stage.getHeight() - hirelingPanel.getHeight());
    stage.addActor(hirelingPanel);

    stashPanel = new StashPanel();
    stashPanel.setPosition(0, stage.getHeight() - stashPanel.getHeight());
    stage.addActor(stashPanel);

    cubePanel = new CubePanel();
    cubePanel.setPosition(0, stage.getHeight() - cubePanel.getHeight());
    stage.addActor(cubePanel);

    vendorPanel = new VendorPanel();
    vendorPanel.setPosition(0, stage.getHeight() - vendorPanel.getHeight());
    stage.addActor(vendorPanel);

    characterPanel = new CharacterPanel();
    characterPanel.setPosition(0, stage.getHeight() - characterPanel.getHeight());
    stage.addActor(characterPanel);

    questsPanel = new QuestsPanel();
    questsPanel.setPosition(0, stage.getHeight() - questsPanel.getHeight());
    stage.addActor(questsPanel);

    partyPanel = new PartyPanel();
    partyPanel.setPosition(0, stage.getHeight() - partyPanel.getHeight());
    stage.addActor(partyPanel);

    waygatePanel = new WaygatePanel(charData);
    waygatePanel.setPosition(0, stage.getHeight() - waygatePanel.getHeight());
    stage.addActor(waygatePanel);

    spellsPanel = new SpellsPanel();
    spellsPanel.setPosition(
        stage.getWidth() - spellsPanel.getWidth(),
        stage.getHeight() - spellsPanel.getHeight());
    stage.addActor(spellsPanel);

    spellsQuickPanelL = new SpellsQuickPanel(controlPanel.getLeftSkill(), true, socket);
    spellsQuickPanelL.setPosition(0, 100, Align.bottomLeft);
    spellsQuickPanelL.setVisible(false);
    stage.addActor(spellsQuickPanelL);

    spellsQuickPanelR = new SpellsQuickPanel(controlPanel.getRightSkill(), false, socket);
    spellsQuickPanelR.setPosition(stage.getWidth(), 100, Align.bottomRight);
    spellsQuickPanelR.setVisible(false);
    stage.addActor(spellsQuickPanelR);

    mappedKeyStateListener = new MappedKeyStateAdapter() {
      @Override
      public void onPressed(MappedKey key, int keycode) {
        if (input.isVisible() && (key != Keys.Enter && key != Keys.Esc)) {
          return;
        }

        if (key == Keys.Esc) {
          // D2MOD: Priority 1 - Handle player death respawn
          // If player is dead, respawn at town instead of showing menu
          if (Riiablo.game.player >= 0 && engine != null) {
            com.riiablo.engine.client.DeathHandler deathHandler = engine.getSystem(com.riiablo.engine.client.DeathHandler.class);
            if (deathHandler != null && deathHandler.isPlayerDead(Riiablo.game.player)) {
              boolean ready = deathHandler.canRespawnPlayer(Riiablo.game.player);
              Gdx.app.log(TAG, "[PLAYER_REVIVE_INPUT] entity=" + Riiablo.game.player
                  + " ready=" + ready);
              if (ready) {
                if (socket == null) {
                  deathHandler.respawnPlayerAtTown(Riiablo.game.player);
                } else {
                  ClientNetworkSynchronizer network = engine.getSystem(
                      ClientNetworkSynchronizer.class);
                  if (network != null) network.requestPlayerRespawn();
                }
              }
              return; // Never show the menu during DT/DD
            }
          }
          
          // Priority 2 - Normal ESC key handling (show/hide menu)
          if (escapePanel.isVisible()) {
            escapePanel.setVisible(false);
          } else if (input.isVisible()) {
            input.setVisible(false);
          } else if (left != null || right != null) {
            setLeftPanel(null);
            setRightPanel(null);
          } else {
            escapePanel.setVisible(true);
          }
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
          setLeftPanel(questsPanel.isVisible() ? null : questsPanel);
        } else if (key == Keys.Party) {
          setLeftPanel(partyPanel.isVisible() ? null : partyPanel);
        } else if (key == Keys.Vendor) {
          setLeftPanel(vendorPanel.isVisible() ? null : vendorPanel);
        } else if (key == Keys.SwapWeapons) {
          Riiablo.charData.getItems().alternate();
        }
      }
    };

    testingInputProcessor = new InputAdapter() {
      private final float ZOOM_AMOUNT = 0.1f;

      @Override
      public boolean scrolled(float amountX, float amountY) {
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
            com.riiablo.engine.client.debug.RenderSystemDebugger renderDebugger =
                engine.getSystem(com.riiablo.engine.client.debug.RenderSystemDebugger.class);
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
    map = new Map(config.seed(), config.diff());
    mapManager = new MapManager();
    partyManager = new com.riiablo.engine.server.party.PartyManager();
    renderer = new RenderSystem(Riiablo.batch, map);
    iso = renderer.iso();
    scaledStage = new Stage(new ScreenViewport(iso), Riiablo.batch);
    factory = new ClientEntityFactory();
    itemController = socket == null ? new ClientItemManager() : new NetworkedClientItemManager();

    WorldConfiguration config = getWorldConfiguration();
    config
        .register("iso", iso)
        .register("config", config)
        .register("map", map)
        .register("factory", factory)
        .register("itemController", itemController)
        .register("partyManager", partyManager)
        .register("batch", Riiablo.batch)
        .register("shapes", Riiablo.shapes)
        .register("stage", stage)
        .register("scaledStage", scaledStage)
        .register("input", input)
        .register("output", output)
        ;
    if (socket != null) config.register("client.socket", socket);
    engine = Riiablo.engine = new World(config);

    // hacked until I can rewrite into proper system
    engine.inject(map);
    map.setEntityFactory(factory);
    engine.inject(Act1MapBuilder.INSTANCE);
    engine.inject(Act1MapBuilderD2MOD.INSTANCE);

    if (mobileControls != null) engine.inject(mobileControls);

    injectPanels();

    // TODO: better place to put this?
    charData.getItems().addLocationListener(Riiablo.cursor);
    charData.getMerc().getItems().addLocationListener(Riiablo.cursor);

    // FIXME: #75 Initial CharData update event
    charData.update();

    loadingScreen = new GameLoadingScreen(map, getDependencies());
  }

  private void injectPanels() {
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
    WorldConfigurationBuilder builder = new WorldConfigurationBuilder()
        .with(new NetworkIdManager())
        .with(new EventSystem())
        .with(new TagManager())
        .with(mapManager)
        .with(itemController, new ItemManager())
        .with(new CofManager())
        .with(new ObjectInitializer())
        .with(new ObjectInteractor(), new WarpInteractor(), new ItemInteractor())
        .with(new MenuManager(), new DialogManager())
        ;
    if (!DEBUG_TOUCHPAD && Gdx.app.getType() == Application.ApplicationType.Desktop) {
      builder.with(new CursorMovementSystem());
      builder.with(new KeyboardMovementSystem()); // 键盘方向键移动支持
    }
    builder
        .with(new Actioneer()) // TODO: move to more appropriate spot in list
        .with(new com.riiablo.engine.server.ServerMonsterCorpseSystem())
        .with(new SkillCastHandler()) // TODO: move to more appropriate spot in list
        .with(new OverlayManager()) // TODO: move to more appropriate spot in list
        .with(new OverlayStepper()) // TODO: move to more appropriate spot in list
        .with(new DamageHandler()) // TODO: move to more appropriate spot in list
        .with(new DeathHandler()) // TODO: move to more appropriate spot in list
        .with(new CorpseManager()) // Manages corpse lifetime and removal
        ;
    if (socket == null) {
      // Local games own the authoritative combat world.  Dedicated servers
      // already register this system; network clients must not create a
      // duplicate missile for the same SkillDoEvent.
      builder.with(new Act1QuestDialogController(), new Act1QuestIndicatorSystem(),
          new ActTransitionSystem());
      builder.with(new Act1QuestSystem());
      builder.with(new NativeMercenaryRewardSystem());
      builder.with(new NativeCountessRewardSystem());
      builder.with(new NativeCharsiImbueSystem());
      builder.with(new ServerSkillSystem(true));
      builder.with(new com.riiablo.engine.server.AuraEcsSystem());
      builder.with(new ItemGenerator());
      // Local games dispatch authoritative DeathEvent in this world. Without
      // the reward subscriber, XP still advances but no monster drop is ever
      // rolled or materialized. Network clients receive D2GS-created items.
      builder.with(new DeathRewardSystem());
      builder.with(new NativeObjectDropSystem());
      builder.with(new NativeShrineSystem());
      builder.with(new VendorGenerator());
      builder.with(new RoomEntityTrackingSystem(), new RoomActivationSystem(), new AIStepper());
      builder.with(new com.riiablo.engine.server.party.PartyMemberSyncSystem());
    } else {
      builder.with(new ItemGenerator()); // TODO: #89
      builder.with(new VendorGenerator()); // TODO: #89
    }
    builder
        .with(new Pathfinder())
        .with(new LeapSystem())

        .with(new SoundEmitterHandler())

        .with(factory)
        .with(new AnimDataResolver())
        // A queued sequence must select its attack/cast COF before AnimStepper
        // advances the previous neutral animation. Otherwise a neutral wrap
        // can finish and remove the new sequence without firing its keyframe.
        .with(new SequenceHandler())
        .with(new AnimStepper())
        .with(new CofUnloader(), new CofResolver(), new CofLoader())
        .with(new CofLayerUnloader(), new CofLayerLoader(), new CofLayerCacher())
        .with(new CofAlphaHandler(), new CofTransformHandler())
        .with(new ItemLoader())
        .with(new MissileLoader())
        .with(new AnimationStepper())
        .with(new ObjectCollisionUpdater())

        // In multiplayer, D2GS owns CofReference modes. The client still
        // computes local movement velocity, but must not overwrite server
        // attack/walk/neutral modes for Networked replicas.
        .with(socket == null
            ? new VelocityModeChanger()
            : new VelocityModeChanger(true, false));
//        .with(new VelocityAdder());
    if (socket != null) {
      // FIXME: crash when changing acts in multiplayer
      builder.with(new Box2DDisposer());
    }
    builder
        .with(new Box2DSynchronizerPre())
        .with(new Box2DPhysics(1 / 60f))
        .with(new Box2DSynchronizerPost())

        .with(new MissileCollisionSystem()) // 处理导弹的碰撞和伤害
        .with(new StateUpdater())
        .with(new ExperienceManager())
        .with(new PlayerCorpseRetrievalSystem())

        .with(new ZoneChangeTracker())
        .with(new ZoneMovementModesChanger())
        .with(new ZoneEntryDisplayer())

        .with(new FootstepEmitter())

        .with(new SelectableManager())
        .with(new HoveredManager())
        .with(new WarpSubstManager())
        ;
    if (DEBUG_TOUCHPAD || Gdx.app.getType() == Application.ApplicationType.Android) {
      builder.with(new AutoInteracter());
    }
    builder
        .with(new PlayerItemHandler())

        .with(new AngularVelocity())
        .with(new DirectionResolver())

        .with(renderer)
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
//  if (mobileControls != null) mobileControls.toFront();
    if (touchpad != null) touchpad.toBack();
    input.toFront();
    escapePanel.toFront();

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
    return PlayerStatsManager.INSTANCE.spendSkillPoint(charData, skillId);
  }

  @Override
  public void pause() {
    discardNextSimulationDelta = true;
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
    if (Boolean.TRUE.equals(Cvars.Client.Display.KeepControlPanelGrouped.get())) {
      controlPanel.setWidth(stage.getWidth());
      controlPanel.layout();
    } else {
      controlPanel.pack();
      controlPanel.setPosition(stage.getWidth() / 2f, 0,
          Align.bottom | Align.center);
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

    // TODO: move to a separate system TouchpadMovementSystem
    if (touchpad != null) {
      tmpVec2.set(touchpad.getKnobPercentX(), touchpad.getKnobPercentY()).nor();
      if (tmpVec2.isZero()) {
        Velocity velocity = engine.getMapper(Velocity.class).get(player);
        velocity.velocity.setZero();
      } else {
        Vector2 position = engine.getMapper(Position.class).get(player).position;
        iso.toScreen(tmpVec2b.set(position)).add(tmpVec2);
        iso.toWorld(tmpVec2b).sub(position);

        Angle angle = engine.getMapper(Angle.class).get(player);
        angle.target.set(tmpVec2b).nor();

        Velocity velocity = engine.getMapper(Velocity.class).get(player);
        velocity.velocity.set(tmpVec2b);

        engine.getSystem(DialogManager.class).setDialog(null);
        engine.getSystem(MenuManager.class).setMenu(null, Engine.INVALID_ENTITY);
      }
    }

    // 检测持续按键：automap 缩放和平移（按住键持续操作）
    if (RenderSystem.isAutomapVisible()) {
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
    engine.setDelta(delta);
    engine.process();

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
    
    com.riiablo.engine.client.DeathHandler deathHandler = engine.getSystem(com.riiablo.engine.client.DeathHandler.class);
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
    
    String message = "You have died, Press ESC to continue"; // "You have died, Press ESC to continue"
    
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
    if (map.getAct() == -1) {
      setAct(0);
      return;
    }

    create();

    Riiablo.game = this;
    Keys.DebugMode.addStateListener(debugKeyListener);
    Keys.Esc.addStateListener(mappedKeyStateListener);
    Keys.Enter.addStateListener(mappedKeyStateListener);
    Keys.Inventory.addStateListener(mappedKeyStateListener);
    Keys.Character.addStateListener(mappedKeyStateListener);
    Keys.Hireling.addStateListener(mappedKeyStateListener);
    Keys.Spells.addStateListener(mappedKeyStateListener);
    Keys.Quests.addStateListener(mappedKeyStateListener);
    Keys.SwapWeapons.addStateListener(mappedKeyStateListener);
    Keys.Stash.addStateListener(mappedKeyStateListener);
    Keys.Vendor.addStateListener(mappedKeyStateListener);
    Riiablo.input.addProcessor(testingInputProcessor);
    Riiablo.input.addProcessor(stage);
    Riiablo.input.addProcessor(scaledStage);
    Riiablo.client.addScreenBoundsListener(screenBoundsListener = new Client.ScreenBoundsListener() {
      final float THRESHOLD = 150;
      float prevY = 0;

      @Override
      public void updateScreenBounds(float x, float y, float width, float height) {
        if (y < THRESHOLD && prevY >= THRESHOLD) input.setVisible(false);
        input.setPosition(stage.getWidth() / 2, y + 100, Align.bottom | Align.center);
        prevY = y;
      }
    });
    screenBoundsListener.updateScreenBounds(0, 0, 0, 0);

    if (!DEBUG_MOBILE && Gdx.app.getType() == Application.ApplicationType.Desktop) {
      Cvars.Client.Display.KeepControlPanelGrouped.addStateListener(new CvarStateAdapter<Boolean>() {
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
      });
    }

    Riiablo.viewport = viewport;
    Riiablo.music.stop();
    Riiablo.assets.get(windowopenDescriptor).play();

    if (socket == null) mapManager.createEntities();

    engine.getSystem(Box2DPhysics.class).createBodies();

    Levels.Entry waypointTarget = pendingWaypointTarget;
    Vector2 origin = waypointTarget != null && waypointTarget.Act == map.getAct()
        ? mapManager.findWaypointPosition(waypointTarget, new Vector2())
        : null;
    if (origin != null) {
      Gdx.app.log(TAG, "Arriving at waypoint: level=" + waypointTarget.LevelName
          + "(" + waypointTarget.Id + ") position=" + origin);
    }
    if (origin == null) origin = map.find(Map.ID.TOWN_ENTRY_1);
    if (origin == null) origin = map.find(Map.ID.TOWN_ENTRY_2);
    if (origin == null) origin = map.find(Map.ID.TP_LOCATION);
    Map.Zone zone = origin != null ? map.getZone(origin) : null;
    if (origin == null || zone == null) {
      // 无传送点或 D2MOO 回退布局时：使用城镇 zone 中心作为出生点
      com.riiablo.codec.excel.Levels.Entry townLevel = Riiablo.files.Levels.get(1);
      if (townLevel != null) {
        Map.Zone townZone = map.findZone(townLevel);
        if (townZone != null) {
          origin = new Vector2(townZone.x() + townZone.width() / 2f, townZone.y() + townZone.height() / 2f);
          zone = townZone;
        }
      }
    }
    if (origin == null || zone == null) {
      Gdx.app.error("GameScreen", "No spawn position or zone available");
      return;
    }
    player = factory.createPlayer(charData, origin);
    engine.getSystem(EventSystem.class).dispatch(ZoneChangeEvent.obtain(player, zone));
    if (socket == null && charData.hasMerc()) {
      NativeMercenaryRewardSystem mercenaries =
          engine.getSystem(NativeMercenaryRewardSystem.class);
      if (mercenaries == null || !mercenaries.restorePersistedMercenary(player)) {
        Gdx.app.error(TAG, "Failed to restore persisted mercenary for " + charData.name);
      }
    }
    pendingWaypointTarget = null;

    renderer.setSrc(player);
    renderer.updatePosition(true);
  }

  @Override
  public void hide() {
    Keys.DebugMode.removeStateListener(debugKeyListener);
    Keys.Esc.removeStateListener(mappedKeyStateListener);
    Keys.Enter.removeStateListener(mappedKeyStateListener);
    Keys.Inventory.removeStateListener(mappedKeyStateListener);
    Keys.Character.removeStateListener(mappedKeyStateListener);
    Keys.Hireling.removeStateListener(mappedKeyStateListener);
    Keys.Spells.removeStateListener(mappedKeyStateListener);
    Keys.Quests.removeStateListener(mappedKeyStateListener);
    Keys.SwapWeapons.removeStateListener(mappedKeyStateListener);
    Keys.Stash.removeStateListener(mappedKeyStateListener);
    Keys.Vendor.removeStateListener(mappedKeyStateListener);
    Riiablo.input.removeProcessor(testingInputProcessor);
    Riiablo.input.removeProcessor(stage);
    Riiablo.input.removeProcessor(scaledStage);
    Riiablo.client.removeScreenBoundsListener(screenBoundsListener);
    Cvars.Client.Display.KeepControlPanelGrouped.clearStateListeners();
  }

  @Override
  public void dispose() {
    //map.dispose(); // FIXME: additional instances aren't reloading textures properly (DT1s disposal)
    charData.clearListeners();
    engine.dispose();
    for (Actor actor : stage.getActors()) if (actor instanceof Disposable) ((Disposable) actor).dispose();
    stage.dispose();
    for (AssetDescriptor asset : preloadedAssets) Riiablo.assets.unload(asset.fileName);
  }

  public void setRightPanel(Actor actor) {
    if (right != null) {
      right.setVisible(false);
      right = null;
    }
    if (actor == null) return;
    actor.setVisible(true);
    right = actor;
  }

  public void setLeftPanel(Actor actor) {
    if (left != null) {
      left.setVisible(false);
      left = null;
    }
    if (actor == null) return;
    actor.setVisible(true);
    left = actor;
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
    player = Engine.INVALID_ENTITY;
    IntBag entities = engine.getAspectSubscriptionManager().get(Aspect.all()).getEntities();
    for (int i = 0, size = entities.size(); i < size; i++) {
      engine.delete(entities.get(i));
    }

    engine.getSystem(Box2DPhysics.class).clear();

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
    position.position.set(destination);
    Box2DBody box2d = engine.getMapper(Box2DBody.class).get(player);
    if (box2d != null && box2d.body != null) box2d.body.setTransform(destination, 0);

    Map.Zone zone = map.getZone(destination);
    MapWrapper mapWrapper = engine.getMapper(MapWrapper.class).get(player);
    mapWrapper.set(map, zone);
    engine.getSystem(EventSystem.class).dispatch(ZoneChangeEvent.obtain(player, zone));

    setLeftPanel(null);
    renderer.updatePosition(true);
    Gdx.app.log(TAG, "Waypoint travel complete: level=" + target.LevelName
        + "(" + target.Id + ") position=" + destination);
  }

  static float sanitizeSimulationDelta(float delta) {
    if (!Float.isFinite(delta) || delta < 0f
        || delta > BACKGROUND_DELTA_THRESHOLD) {
      return Animation.FRAME_DURATION;
    }
    return Math.min(delta, MAX_SIMULATION_DELTA);
  }

  static float sanitizeResumedSimulationDelta(float delta) {
    return Math.min(sanitizeSimulationDelta(delta), Animation.FRAME_DURATION);
  }
}
