package com.riiablo.skill;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.artemis.ComponentMapper;
import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.managers.TagManager;
import com.artemis.utils.IntBag;
import net.mostlyoriginal.api.event.common.EventSystem;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisSelectBox;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextArea;
import com.kotcrab.vis.ui.widget.VisTextButton;
import com.riiablo.COFs;
import com.riiablo.Colors;
import com.riiablo.Files;
import com.riiablo.Fonts;
import com.riiablo.Palettes;
import com.riiablo.Riiablo;
import com.riiablo.Textures;
import com.riiablo.codec.COF;
import com.riiablo.codec.D2;
import com.riiablo.codec.DC6;
import com.riiablo.codec.DCC;
import com.riiablo.codec.FontTBL;
import com.riiablo.codec.Palette;
import com.riiablo.codec.StringTBLs;
import com.riiablo.loader.BitmapFontLoader;
import com.riiablo.loader.COFLoader;
import com.riiablo.loader.DC6Loader;
import com.riiablo.loader.DCCLoader;
import com.riiablo.loader.PaletteLoader;
import com.riiablo.logger.Level;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.mpq.MPQFileHandleResolver;
import com.riiablo.tool.Lwjgl3Tool;
import com.riiablo.tool.Tool;
import com.riiablo.util.InstallationFinder;
import com.riiablo.codec.excel.Skills;
import com.riiablo.camera.IsometricCamera;
import com.riiablo.engine.client.AnimationStepper;
import com.riiablo.engine.client.ClientEntityFactory;
import com.riiablo.engine.client.CofAlphaHandler;
import com.riiablo.engine.client.CofLayerCacher;
import com.riiablo.engine.client.CofLayerLoader;
import com.riiablo.engine.client.CofLayerUnloader;
import com.riiablo.engine.client.CofLoader;
import com.riiablo.engine.client.CofResolver;
import com.riiablo.engine.client.CofTransformHandler;
import com.riiablo.engine.client.CofUnloader;
import com.riiablo.engine.client.MissileLoader;
import com.riiablo.engine.server.AnimDataResolver;
import com.riiablo.engine.server.AnimStepper;
import com.riiablo.engine.client.SkillCastHandler;
import com.riiablo.engine.client.MenuManager;
import com.riiablo.engine.client.DialogManager;
import com.riiablo.engine.client.OverlayManager;
import com.riiablo.engine.client.component.AnimationWrapper;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.ServerSkillSystem;
import com.riiablo.engine.server.ServerMonsterCorpseSystem;
import com.riiablo.engine.server.MissileCollisionSystem;
import com.riiablo.engine.server.StateUpdater;
import com.riiablo.engine.server.AngularVelocity;
import com.riiablo.engine.server.ObjectInteractor;
import com.riiablo.engine.server.WarpInteractor;
import com.riiablo.engine.server.ItemInteractor;
import com.riiablo.engine.server.ItemManager;
import com.riiablo.engine.server.Actioneer;
import com.riiablo.engine.server.Pathfinder;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.engine.server.combat.CombatPositionHistory;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.event.SkillCastEvent;
import com.riiablo.engine.server.event.CofChangeEvent;
import com.riiablo.engine.server.event.ModeChangeEvent;
import com.riiablo.engine.EntityFactory;
import com.riiablo.map.Map;
import com.riiablo.map.DT1;
import com.riiablo.save.CharData;
import com.riiablo.graphics.PaletteIndexedBatch;
import com.riiablo.engine.Engine;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.Item;
import com.riiablo.item.BodyLoc;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.SystemUtils;
import java.util.Locale;

/**
 * Standalone shell for exercising skills without entering a generated game map.
 * The first milestone intentionally keeps the arena visual and deterministic;
 * ECS combat hooks are added behind the same controls in subsequent milestones.
 */
public class SkillViewer extends Tool {
  private static final Logger log = LogManager.getLogger(SkillViewer.class);
  private static final String[] CLASS_NAMES = {
      "Amazon", "Sorceress", "Necromancer", "Paladin", "Barbarian", "Druid", "Assassin"
  };
  private static final String[] CLASS_CODES = { "ama", "sor", "nec", "pal", "bar", "dru", "ass" };
  private static final SkillCharacterPreset[] PRESETS = {
      new SkillCharacterPreset(Riiablo.AMAZON, "Amazon", "Short Bow", true),
      new SkillCharacterPreset(Riiablo.SORCERESS, "Sorceress", "Short Staff", true),
      new SkillCharacterPreset(Riiablo.NECROMANCER, "Necromancer", "Short Staff", true),
      new SkillCharacterPreset(Riiablo.PALADIN, "Paladin", "Short Sword + Buckler", true),
      new SkillCharacterPreset(Riiablo.BARBARIAN, "Barbarian", "Short Sword (dual-wield)", true),
      new SkillCharacterPreset(Riiablo.DRUID, "Druid", "Club", true),
      new SkillCharacterPreset(Riiablo.ASSASSIN, "Assassin", "Katar (claw)", true)
  };
  private static final Color GRID = new Color(0.04f, 0.04f, 0.04f, 1f);

  public static void main(String[] args) {
    Lwjgl3Tool.create(SkillViewer.class, "skill-viewer", args)
        .title("Riiablo Skill Effect Viewer")
        .size(800, 450, true)
        .config((Lwjgl3Tool.Lwjgl3ToolConfigurator) config ->
            config.setWindowSizeLimits(640, 360, -1, -1))
        .start();
  }

  private com.badlogic.gdx.files.FileHandle home;
  private com.badlogic.gdx.files.FileHandle runtimeLog;
  private int selectedClass;
  private int skillLevel = 1;
  private long seed = 0x534B494CL;
  private boolean resourcesLoaded;
  private String resourceError;

  private AssetManager assets;
  private ShapeRenderer shapes;
  private PaletteIndexedBatch batch;
  private com.badlogic.gdx.graphics.glutils.ShaderProgram shader;
  private World engine;
  private Map arenaMap;
  private Map.Zone arenaZone;
  private ClientEntityFactory entityFactory;
  private IsometricCamera iso;
  private int playerEntity = Engine.INVALID_ENTITY;
  private CharData playerData;
  private final Array<Integer> monsterEntities = new Array<>();
  private final Array<Integer> corpseEntities = new Array<>();
  private final Array<String> queuedCofs = new Array<>();
  private final Array<Integer> cofRefreshSent = new Array<>();
  private ComponentMapper<Position> positions;
  private ComponentMapper<Velocity> velocities;
  private ComponentMapper<Angle> angles;
  private ComponentMapper<AnimationWrapper> animations;
  private Stage stage;
  private VisSelectBox<String> classSelect;
  private VisSelectBox<String> skillSelect;
  private VisSelectBox<String> monsterSelect;
  private VisTextArea notes;
  private VisLabel status;
  private VisLabel targetMode;
  private VisCheckBox ai;
  private VisCheckBox showGrid;
  private SkillSessionLog sessionLog;
  private VisTable notesPanel;
  private final Array<Marker> monsters = new Array<>();
  private final Array<Marker> corpses = new Array<>();
  private final Marker player = new Marker(0, 0, "player");
  private TargetMode targetModeValue = TargetMode.NONE;
  private boolean renderReadyLogged;
  private final com.badlogic.gdx.math.Vector2 moveTarget = new com.badlogic.gdx.math.Vector2();
  private boolean moving;

  @Override
  protected String getHelpHeader() {
    return "Standalone skill effect viewer.\n" +
        "Uses the same Diablo II installation discovery as the main Riiablo client.";
  }

  @Override
  protected void createCliOptions(Options options) {
    super.createCliOptions(options);
    options.addOption(Option.builder("d").longOpt("d2")
        .desc("directory containing D2 MPQ files").hasArg().argName("path").build());
  }

  @Override
  protected void handleCliOptions(String cmd, Options options, CommandLine cli) throws Exception {
    super.handleCliOptions(cmd, options, cli);
    InstallationFinder finder = InstallationFinder.getInstance();
    String override = cli.getOptionValue("d2");
    if (override != null) {
      home = finder.defaultHomeDir("d2", override);
    } else {
      Array<com.badlogic.gdx.files.FileHandle> homes = finder.getHomeDirs();
      home = homes.size > 0
          ? homes.first()
          : new com.badlogic.gdx.files.FileHandle(SystemUtils.USER_HOME).child("riiablo");
      home.mkdirs();
    }
    log.debug("d2Home: {}", home);
  }

  @Override
  public void create() {
    Gdx.app.setLogLevel(Application.LOG_DEBUG);
    LogManager.setLevel(SkillViewer.class.getName(), Level.DEBUG);
    shapes = new ShapeRenderer();
    // Keep controls in physical pixels so an 800x450 window remains usable
    // and enlarging the window only gives the arena more room.
    stage = new Stage(new ScreenViewport());
    runtimeLog = Gdx.files.local("logs/skill-viewer.log");
    runtimeLog.parent().mkdirs();
    runtimeLog.writeString("", false, "UTF-8");
    // Gradle and the standalone launcher both run with the skill-viewer
    // directory as the working directory. Keep session logs beside the tool.
    sessionLog = new SkillSessionLog(Gdx.files.local("logs"));
    try {
      loadResources();
    } catch (Throwable t) {
      resourceError = t.getMessage() == null ? t.toString() : t.getMessage();
      log.error("Unable to load Diablo II resources", t);
    }
    buildUi();
    InputMultiplexer input = new InputMultiplexer(stage, new ArenaInput());
    Gdx.input.setInputProcessor(input);
    resetScene();
  }

  private void loadResources() {
    disposeResources();
    Riiablo.home = home;
    MPQFileHandleResolver resolver = Riiablo.mpqs = new MPQFileHandleResolver(home);
    assets = Riiablo.assets = new AssetManager();
    assets.setLoader(COF.class, new COFLoader(resolver));
    assets.setLoader(DCC.class, new DCCLoader(resolver));
    assets.setLoader(DC6.class, new DC6Loader(resolver));
    assets.setLoader(Palette.class, new PaletteLoader(resolver));
    assets.setLoader(FontTBL.BitmapFont.class, new BitmapFontLoader(resolver));
    Riiablo.files = new Files(assets);
    Riiablo.fonts = new Fonts(assets);
    Riiablo.palettes = new Palettes(assets);
    Riiablo.colors = new Colors();
    Riiablo.textures = new Textures();
    Riiablo.string = new StringTBLs(resolver);
    Riiablo.cofs = new COFs(assets);
    Riiablo.anim = D2.loadFromFile(resolver.resolve("data\\global\\eanimdata.d2"));
    com.badlogic.gdx.graphics.glutils.ShaderProgram.pedantic = false;
    shader = Riiablo.shader = new com.badlogic.gdx.graphics.glutils.ShaderProgram(
        Gdx.files.internal("shaders/indexpalette3.vert"),
        Gdx.files.internal("shaders/indexpalette3.frag"));
    if (!shader.isCompiled()) throw new IllegalStateException(shader.getLog());
    batch = Riiablo.batch = new PaletteIndexedBatch(2048, shader);
    resourcesLoaded = true;
    resourceError = null;
  }

  /** Builds the smallest real client/server ECS world used by the viewer. */
  private void createCombatArena() {
    disposeCombatArena();
    Riiablo.gameSeed = (int) seed;
    arenaMap = new Map((int) seed, Riiablo.NORMAL);
    arenaZone = arenaMap.createSyntheticArena(1, 4, 4);
    entityFactory = new ClientEntityFactory();
    iso = new IsometricCamera();
    iso.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    iso.set(0, 0);
    iso.update();
    WorldConfigurationBuilder config = new WorldConfigurationBuilder()
        .with(new EventSystem(), new TagManager())
        .with(new CofManager())
        .with(new ObjectInteractor(), new WarpInteractor(), new ItemInteractor())
        .with(new MenuManager(), new DialogManager(), new OverlayManager())
        .with(new ItemManager(), new ItemGenerator())
        .with(new StateUpdater())
        .with(new Pathfinder(), new Actioneer())
        .with(new com.riiablo.engine.server.DynamicUnitCollisionSystem(true))
        .with(new com.riiablo.engine.server.VelocityAdder())
        .with(new AnimDataResolver(), new AnimStepper())
        .with(new com.riiablo.engine.server.SequenceHandler())
        .with(new SkillCastHandler())
        .with(new CofUnloader(), new CofResolver(), new CofLoader())
        .with(new CofLayerUnloader(), new CofLayerLoader(), new CofLayerCacher())
        .with(new CofAlphaHandler(), new CofTransformHandler())
        .with(new MissileLoader())
        .with(new AnimationStepper())
        .with(new ServerMonsterCorpseSystem())
        .with(new MissileCollisionSystem())
        .with(new ServerSkillSystem(true))
        .with(new com.riiablo.engine.server.VelocityModeChanger())
        .with(new AngularVelocity())
        .with(new com.riiablo.engine.client.DirectionResolver())
        .with(entityFactory);
    com.artemis.WorldConfiguration worldConfig = config.build()
        .register("map", arenaMap)
        .register("factory", entityFactory)
        .register("batch", batch)
        .register("iso", iso)
        .register("stage", stage)
        .register("scaledStage", null)
        .register("partyManager", new PartyManager())
        .register("combatPositionHistory", new CombatPositionHistory(arenaMap));
    engine = Riiablo.engine = new World(worldConfig);
    engine.inject(arenaMap);
    arenaMap.setEntityFactory(entityFactory);
    positions = engine.getMapper(Position.class);
    velocities = engine.getMapper(Velocity.class);
    angles = engine.getMapper(Angle.class);
    animations = engine.getMapper(AnimationWrapper.class);

    playerData = CharData.obtain(Riiablo.NORMAL, false, "SkillTester", PRESETS[selectedClass].classId);
    prepareDebugCharacter();
    prepareDebugEquipment();
    playerEntity = entityFactory.createPlayer(playerData, new com.badlogic.gdx.math.Vector2(0, 0));
    arenaZone.attachEntity(playerEntity);
    initializePlayerComposite();
    renderReadyLogged = false;
    refreshEntityPresentation(playerEntity);
    runtimeLog("event=player_created entity=" + playerEntity
        + " class=" + PRESETS[selectedClass].name + " position=(0,0)");
  }

  private void prepareDebugCharacter() {
    playerData.level = 99;
    playerData.getStats().base().put(com.riiablo.attributes.Stat.level, 99);
    playerData.getStats().aggregate().put(com.riiablo.attributes.Stat.level, 99);
    if (Riiablo.files != null && Riiablo.files.skills != null) {
      for (Skills.Entry skill : Riiablo.files.skills) {
        if (skill != null && skill.Id >= 0) playerData.setSkillLevel(skill.Id, 20);
      }
    }
    runtimeLog("event=debug_character_prepared class=" + PRESETS[selectedClass].name + " level=99 skills=20");
  }

  /** Gives weapon-dependent skills the minimum real D2 equipment they need. */
  private void prepareDebugEquipment() {
    if (playerData == null || Riiablo.files == null) return;
    if (selectedClass != Riiablo.AMAZON) return;
    try {
      ItemGenerator generator = new ItemGenerator();
      Item bow = generator.generate("sbw");
      Item arrows = generator.generate("aqv");
      playerData.getItems().add(bow);
      playerData.getItems().add(arrows);
      playerData.getItems().equipItem(BodyLoc.RARM, bow);
      playerData.getItems().equipItem(BodyLoc.LARM, arrows);
      runtimeLog("event=debug_equipment_equipped class=Amazon weapon=sbw ammo=aqv quantity="
          + arrows.attrs.base().get(com.riiablo.attributes.Stat.quantity).asInt());
    } catch (Throwable t) {
      runtimeLog("event=debug_equipment_failed class=Amazon error="
          + (t.getMessage() == null ? t.toString() : t.getMessage()).replace('\n', ' '));
    }
  }

  private void initializePlayerComposite() {
    if (engine == null || playerEntity == Engine.INVALID_ENTITY) return;
    com.artemis.ComponentMapper<com.riiablo.engine.server.component.CofComponents> components =
        engine.getMapper(com.riiablo.engine.server.component.CofComponents.class);
    if (!components.has(playerEntity)) return;
    int[] values = components.get(playerEntity).component;
    java.util.Arrays.fill(values, com.riiablo.engine.server.component.CofComponents.COMPONENT_LIT);
    engine.getMapper(com.riiablo.engine.client.component.CofDirtyComponents.class)
        .create(playerEntity).flags |= com.riiablo.engine.Dirty.ALL;
    runtimeLog("event=player_composite_initialized entity=" + playerEntity + " components=LIT");
  }

  private com.riiablo.codec.excel.MonStats.Entry resolveFallen() {
    String[] candidates = { "fallen1", "Fallen1", "fallen", "Fallen" };
    for (String candidate : candidates) {
      com.riiablo.codec.excel.MonStats.Entry entry = Riiablo.files.monstats.get(candidate);
      if (entry != null) return entry;
    }
    for (com.riiablo.codec.excel.MonStats.Entry entry : Riiablo.files.monstats) {
      if ((entry.Id != null && entry.Id.toLowerCase(Locale.ROOT).contains("fallen"))
          || (entry.Code != null && entry.Code.toLowerCase(Locale.ROOT).equals("fa"))) return entry;
    }
    return null;
  }

  private void disposeCombatArena() {
    if (engine != null) {
      engine.dispose();
      engine = null;
    }
    monsterEntities.clear();
    corpseEntities.clear();
    queuedCofs.clear();
    cofRefreshSent.clear();
    playerEntity = Engine.INVALID_ENTITY;
    playerData = null;
    arenaZone = null;
    arenaMap = null;
  }

  private void disposeResources() {
    resourcesLoaded = false;
    if (assets != null) {
      assets.dispose();
      assets = null;
    }
  }

  private void runtimeLog(String line) {
    if (runtimeLog != null) runtimeLog.writeString(line + "\n", true, "UTF-8");
  }

  private void buildUi() {
    VisUI.load(Gdx.files.internal("skin/x1/uiskin.json"));
    Skin skin = VisUI.getSkin();
    VisTable root = new VisTable();
    root.setFillParent(true);
    root.pad(8);
    root.top().left();
    stage.addActor(root);

    VisTable menu = new VisTable();
    VisTextButton reload = new VisTextButton("Reload Resources");
    VisTextButton reset = new VisTextButton("Reset Scene");
    menu.add(reload).height(28).padRight(6);
    menu.add(reset).height(28).padRight(12);
    menu.add(new VisLabel("Character")).padRight(4);
    classSelect = new VisSelectBox<>();
    classSelect.setItems(CLASS_NAMES);
    menu.add(classSelect).height(28).width(140).padRight(8);
    menu.add(new VisLabel("Skill")).padRight(4);
    skillSelect = new VisSelectBox<>();
    menu.add(skillSelect).height(28).width(230).padRight(8);
    menu.add(new VisLabel("Level")).padRight(4);
    VisTextButton level = new VisTextButton("1");
    menu.add(level).height(28).width(40).padRight(8);
    root.add(menu).growX().left().row();

    VisTable menu2 = new VisTable();
    monsterSelect = new VisSelectBox<>();
    monsterSelect.setItems("fallen");
    VisTextButton addMonster = new VisTextButton("Load Monster");
    VisTextButton addCorpse = new VisTextButton("Load Corpse");
    menu2.add(new VisLabel("Monster")).padRight(4);
    menu2.add(monsterSelect).height(28).width(120).padRight(4);
    menu2.add(addMonster).height(28).padRight(4);
    menu2.add(addCorpse).height(28).padRight(8);
    ai = new VisCheckBox("Enable AI");
    menu2.add(ai).height(28).padRight(8);
    showGrid = new VisCheckBox("Show Grid");
    showGrid.setChecked(false);
    menu2.add(showGrid).height(28);
    root.add(menu2).growX().left().row();

    VisTable info = new VisTable();
    status = new VisLabel();
    targetMode = new VisLabel("Target: select a skill");
    info.add(status).left().expandX();
    info.add(targetMode).right();
    root.add(info).growX().padTop(5).row();
    root.add(new VisLabel("Left click: move character | Right click: cast skill")).left().padTop(3).row();

    notesPanel = new VisTable();
    final VisTextButton notesToggle = new VisTextButton("Test Notes ▼");
    notesPanel.add(notesToggle).left().row();
    notes = new VisTextArea("");
    notes.setVisible(false);
    notesPanel.add(notes).grow().minHeight(70);
    root.add(notesPanel).growX().expandY().bottom().padTop(6).row();
    notesToggle.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
        boolean visible = notes.isVisible();
        notes.setVisible(!visible);
        notesToggle.setText(visible ? "Test Notes ▼" : "Test Notes ▲");
    notesPanel.invalidateHierarchy();
      }
    });

    reload.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
        try { loadResources(); rebuildSkills(); resetScene(); } catch (Throwable t) {
          resourceError = t.getMessage() == null ? t.toString() : t.getMessage();
        }
        updateStatus();
      }
    });
    reset.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { resetScene(); }
    });
    classSelect.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
        selectedClass = classSelect.getSelectedIndex();
        finishSkillLog();
        rebuildSkills();
        resetScene();
        skillSelect.setSelectedIndex(0);
        updateStatus();
      }
    });
    skillSelect.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
        beginSkillLog();
      }
    });
    level.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
        skillLevel = skillLevel >= 20 ? 1 : skillLevel + 1;
        level.setText(Integer.toString(skillLevel));
        beginSkillLog();
      }
    });
    addMonster.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
        spawnMonster();
        updateStatus();
      }
    });
    addCorpse.addListener(new ChangeListener() {
      @Override public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
        spawnCorpse();
        updateStatus();
      }
    });
    rebuildSkills();
    updateStatus();
  }

  private void rebuildSkills() {
    if (skillSelect == null) return;
    Array<String> names = new Array<>();
    names.add("请选择技能");
    if (resourcesLoaded && Riiablo.files != null && Riiablo.files.skills != null) {
      String wanted = CLASS_CODES[selectedClass];
      for (Skills.Entry skill : Riiablo.files.skills) {
        if (skill.charclass != null && skill.charclass.toLowerCase(Locale.ROOT).startsWith(wanted)
            && !skill.passive && skill.skill != null && !skill.skill.isEmpty()) names.add(skill.skill);
      }
    }
    skillSelect.setItems(names);
    skillSelect.setSelectedIndex(0);
  }

  private void resetScene() {
    finishSkillLog();
    moving = false;
    moveTarget.setZero();
    runtimeLog("event=scene_reset class=" + PRESETS[selectedClass].name);
    if (resourcesLoaded) {
      try { createCombatArena(); }
      catch (Throwable t) {
        resourceError = t.getMessage() == null ? t.toString() : t.getMessage();
        log.error("Unable to reset skill ECS arena", t);
        runtimeLog("event=player_create_failed error=" + resourceError.replace('\n', ' '));
      }
    }
    player.x = 0; player.y = 0;
    monsters.clear();
    corpses.clear();
    ai.setChecked(false);
    updateStatus();
  }

  private void spawnMonster() {
    if (entityFactory == null || arenaZone == null || monsterEntities.size >= 10) return;
    com.riiablo.codec.excel.MonStats.Entry fallen = resolveFallen();
    if (fallen == null) return;
    int index = monsterEntities.size;
    int entity = entityFactory.createMonster(fallen.hcIdx, 20 + index * 12, 10 + index * 8);
    if (entity == Engine.INVALID_ENTITY) return;
    arenaZone.attachEntity(entity);
    refreshEntityPresentation(entity);
    monsterEntities.add(entity);
    runtimeLog("event=monster_created entity=" + entity + " monster=" + fallen.Id
        + " position=(" + (20 + index * 12) + "," + (10 + index * 8) + ")");
  }

  private void spawnCorpse() {
    if (entityFactory == null || arenaZone == null || corpseEntities.size >= 10) return;
    com.riiablo.codec.excel.MonStats.Entry fallen = resolveFallen();
    if (fallen == null) return;
    int index = corpseEntities.size;
    int entity = entityFactory.createMonster(fallen.hcIdx, 20 + index * 12, 10 + index * 8);
    if (entity == Engine.INVALID_ENTITY) return;
    arenaZone.attachEntity(entity);
    refreshEntityPresentation(entity);
    corpseEntities.add(entity);
    runtimeLog("event=corpse_created entity=" + entity + " monster=" + fallen.Id
        + " position=(" + (20 + index * 12) + "," + (10 + index * 8) + ")");
    net.mostlyoriginal.api.event.common.EventSystem events =
        engine.getSystem(net.mostlyoriginal.api.event.common.EventSystem.class);
    if (events != null) events.dispatch(ModeChangeEvent.obtain(entity, Engine.Monster.MODE_DD));
  }

  /**
   * Entity creation happens after the Artemis world has been built.  The
   * factory's initial mode event can be observed by CofResolver before the
   * CofDescriptor exists, so send one follow-up change to kick the normal
   * COF/DCC presentation pipeline and make the real sprite visible.
   */
  private void refreshEntityPresentation(int entity) {
    if (engine == null || entity == Engine.INVALID_ENTITY) return;
    com.riiablo.engine.client.component.CofDescriptor descriptor =
        engine.getMapper(com.riiablo.engine.client.component.CofDescriptor.class).get(entity);
    if (descriptor != null && descriptor.descriptor != null && !assets.isLoaded(descriptor.descriptor)) {
      assets.load(descriptor.descriptor);
      runtimeLog("event=cof_queued entity=" + entity + " path=" + descriptor.descriptor.fileName);
    }
    net.mostlyoriginal.api.event.common.EventSystem events =
        engine.getSystem(net.mostlyoriginal.api.event.common.EventSystem.class);
    if (events != null) {
      CofChangeEvent event = new CofChangeEvent();
      event.entityId = entity;
      events.dispatch(event);
      runtimeLog("event=presentation_refresh entity=" + entity);
    }
  }

  private void beginSkillLog() {
    if (skillSelect == null || skillSelect.getSelectedIndex() <= 0) {
      targetMode.setText("Target: select a skill");
      return;
    }
    finishSkillLog();
    String skill = skillSelect.getSelected();
    if (playerData != null) playerData.setSkillLevel(Riiablo.files.skills.index(skill), skillLevel);
    sessionLog.begin(CLASS_NAMES[selectedClass], skill, skillLevel, seed);
    sessionLog.append("weapon=" + PRESETS[selectedClass].weapon + " weaponRequired="
        + PRESETS[selectedClass].weaponRequired + "\n");
    targetModeValue = determineTargetMode(skill);
    sessionLog.append("event=skill_selected targetMode=" + targetModeValue + "\n");
    targetMode.setText("Target: " + targetModeValue);
    updateStatus();
  }

  private void finishSkillLog() {
    if (sessionLog != null && notes != null && notes.getText().trim().length() > 0) {
      sessionLog.appendNote(notes.getText());
      notes.setText("");
    }
  }

  private void updateStatus() {
    if (status == null) return;
    StringBuilder text = new StringBuilder();
    text.append(resourcesLoaded ? "Resources: loaded" : "Resources: unavailable");
    if (resourceError != null) text.append(" (").append(resourceError).append(")");
    text.append(" | Player: ").append(playerEntity != Engine.INVALID_ENTITY ? "loaded (entity=" + playerEntity + ")" : "NOT LOADED");
    text.append(" | ").append(CLASS_NAMES[selectedClass]);
    text.append(" | Weapon: ").append(PRESETS[selectedClass].weapon);
    text.append(" | Monsters: ").append(monsterEntities.size).append(" | Corpses: ").append(corpseEntities.size);
    status.setText(text);
  }

  @Override
  public void render() {
    Gdx.gl.glClearColor(0.48f, 0.48f, 0.48f, 1f);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    if (assets != null) assets.update();
    // This viewer owns a standalone Artemis world instead of the main
    // GameScreen's fixed-step loop.  Feed it the frame delta explicitly;
    // without this, VelocityAdder and AnimationStepper see world.delta == 0
    // and both movement and client animation remain frozen.
    float delta = Gdx.graphics.getDeltaTime();
    if (engine != null) engine.setDelta(delta);
    updateMovementIntent();
    if (engine != null) engine.process();
    ensurePresentationAssets();
    processPresentationPass();
    drawArena();
    drawEntities();
    stage.act(Gdx.graphics.getDeltaTime());
    stage.draw();
  }

  private void drawArena() {
    shapes.setProjectionMatrix(stage.getCamera().combined);
    shapes.begin(ShapeRenderer.ShapeType.Line);
    shapes.setColor(GRID);
    float centerX = stage.getViewport().getWorldWidth() * .5f;
    float centerY = stage.getViewport().getWorldHeight() * .52f;
    if (showGrid != null && showGrid.isChecked()) {
      for (int i = -10; i <= 10; i++) {
        shapes.line(centerX - 700 + i * 72, centerY - 350, centerX + 700 + i * 72, centerY + 350);
        shapes.line(centerX - 700 + i * 72, centerY + 350, centerX + 700 + i * 72, centerY - 350);
      }
    }
    // Circles are retained only as a diagnostic fallback while an asset is
    // loading; the normal path renders the actual ECS animations below.
    if (engine == null) {
      drawMarker(centerX + player.x, centerY + player.y, Color.CYAN);
      for (Marker marker : monsters) drawMarker(centerX + marker.x, centerY + marker.y, Color.RED);
      for (Marker marker : corpses) drawMarker(centerX + marker.x, centerY + marker.y, Color.DARK_GRAY);
    }
    shapes.end();
  }

  private void updateMovementIntent() {
    if (!moving || positions == null || velocities == null
        || !positions.has(playerEntity) || !velocities.has(playerEntity)) return;
    com.badlogic.gdx.math.Vector2 position = positions.get(playerEntity).position;
    com.badlogic.gdx.math.Vector2 delta = moveTarget.cpy().sub(position);
    Velocity velocity = velocities.get(playerEntity);
    if (delta.len2() <= 0.01f) {
      position.set(moveTarget);
      velocity.velocity.setZero();
      moving = false;
      runtimeLog("event=move_completed entity=" + playerEntity + " position=" + position);
      return;
    }
    float speed = velocity.walkSpeed > 0f ? velocity.walkSpeed : Engine.Player.SPEED_WALK;
    velocity.velocity.set(delta).nor().setLength(speed);
    if (angles != null && angles.has(playerEntity)) {
      angles.get(playerEntity).target.set(delta).nor();
    }
  }

  private void drawEntities() {
    if (engine == null || batch == null || animations == null || positions == null) return;
    if (!renderReadyLogged && playerEntity != Engine.INVALID_ENTITY && animations.has(playerEntity)
        && positions.has(playerEntity)
        && animations.get(playerEntity).animation.getNumFramesPerDir() > 0) {
      com.riiablo.codec.Animation animation = animations.get(playerEntity).animation;
      runtimeLog("event=player_render_ready entity=" + playerEntity
          + " frames=" + animation.getNumFramesPerDir()
          + " position=" + positions.get(playerEntity).position);
      renderReadyLogged = true;
    }
    // The arena uses tile-space conversion from IsometricCamera, but the
    // standalone viewport is screen-pixel based. Offset the converted origin
    // to the centre of the window and draw with the Stage's pixel projection.
    batch.setProjectionMatrix(stage.getCamera().combined);
    if (Riiablo.palettes != null) batch.setPalette(Riiablo.palettes.act1);
    batch.begin();
    drawEntityAnimation(playerEntity);
    for (int entity : monsterEntities) drawEntityAnimation(entity);
    for (int entity : corpseEntities) drawEntityAnimation(entity);
    IntBag missiles = engine.getAspectSubscriptionManager()
        .get(Aspect.all(com.riiablo.engine.server.component.Missile.class,
            AnimationWrapper.class, Position.class)).getEntities();
    for (int i = 0; i < missiles.size(); i++) drawEntityAnimation(missiles.get(i));
    batch.end();
  }

  private void ensurePresentationAssets() {
    if (engine == null || assets == null) return;
    queuePresentationAsset(playerEntity);
    for (int entity : monsterEntities) queuePresentationAsset(entity);
    for (int entity : corpseEntities) queuePresentationAsset(entity);
  }

  /** Runs the presentation-only systems explicitly, matching the main
   * client's initial pass. Entities are created after the world starts, so
   * waiting for a full gameplay tick can otherwise leave their DCC layers
   * unbound in a standalone viewer.
   */
  private void processPresentationPass() {
    if (engine == null) return;
    engine.getSystem(CofManager.class).process();
    engine.getSystem(AnimDataResolver.class).process();
    engine.getSystem(CofResolver.class).process();
    engine.getSystem(com.riiablo.engine.client.CofLoader.class).process();
    engine.getSystem(CofLayerLoader.class).process();
    engine.getSystem(CofLayerCacher.class).process();
  }

  private void queuePresentationAsset(int entity) {
    if (entity == Engine.INVALID_ENTITY) return;
    com.riiablo.engine.client.component.CofDescriptor descriptor =
        engine.getMapper(com.riiablo.engine.client.component.CofDescriptor.class).get(entity);
    if (descriptor == null || descriptor.descriptor == null) return;
    if (assets.isLoaded(descriptor.descriptor)) {
      com.riiablo.engine.client.component.CofWrapper wrapper =
          engine.getMapper(com.riiablo.engine.client.component.CofWrapper.class).get(entity);
      if (wrapper == null) {
        com.riiablo.codec.COF cof = assets.get(descriptor.descriptor);
        if (cof != null) {
          engine.getMapper(com.riiablo.engine.client.component.CofWrapper.class).create(entity).cof = cof;
          runtimeLog("event=cof_ready entity=" + entity + " path=" + descriptor.descriptor.fileName);
        }
      }
      if (engine.getMapper(com.riiablo.engine.client.component.CofWrapper.class).has(entity)
          && animations != null && animations.has(entity)
          && animations.get(entity).animation.getNumFramesPerDir() == 0) {
        engine.getMapper(com.riiablo.engine.client.component.CofDirtyComponents.class)
            .create(entity).flags |= com.riiablo.engine.Dirty.ALL;
      }
      if (engine.getMapper(com.riiablo.engine.client.component.CofWrapper.class).has(entity)
          && !cofRefreshSent.contains(entity, false)) {
        CofChangeEvent refresh = new CofChangeEvent();
        refresh.entityId = entity;
        engine.getSystem(net.mostlyoriginal.api.event.common.EventSystem.class).dispatch(refresh);
        cofRefreshSent.add(entity);
        runtimeLog("event=cof_refresh entity=" + entity);
      }
      return;
    }
    String path = descriptor.descriptor.fileName;
    if (queuedCofs.contains(path, false)) return;
    assets.load(descriptor.descriptor);
    queuedCofs.add(path);
    runtimeLog("event=cof_queued entity=" + entity + " path=" + path);
    CofChangeEvent event = new CofChangeEvent();
    event.entityId = entity;
    engine.getSystem(net.mostlyoriginal.api.event.common.EventSystem.class).dispatch(event);
  }

  private void drawEntityAnimation(int entity) {
    if (entity == Engine.INVALID_ENTITY || !animations.has(entity) || !positions.has(entity)) return;
    com.riiablo.codec.Animation animation = animations.get(entity).animation;
    if (animation == null) return;
    com.badlogic.gdx.math.Vector2 screen = iso.toScreen(positions.get(entity).position.cpy());
    screen.add(Gdx.graphics.getWidth() * .5f, Gdx.graphics.getHeight() * .5f);
    animation.draw(batch, screen.x, screen.y);
  }

  private void drawMarker(float x, float y, Color color) {
    shapes.setColor(color);
    shapes.circle(x, y, 8, 12);
  }

  @Override public void resize(int width, int height) {
    if (stage != null) stage.getViewport().update(width, height, true);
    if (iso != null) {
      iso.setToOrtho(false, width, height);
      iso.set(0, 0);
      iso.update();
    }
  }
  @Override public void dispose() {
    finishSkillLog();
    disposeCombatArena();
    if (stage != null) stage.dispose();
    if (shapes != null) shapes.dispose();
    if (batch != null) batch.dispose();
    if (shader != null) shader.dispose();
    disposeResources();
    if (VisUI.isLoaded()) VisUI.dispose();
  }

  private final class ArenaInput extends InputAdapter {
    @Override public boolean touchDown(int screenX, int screenY, int pointer, int button) {
      com.badlogic.gdx.math.Vector2 inputWorld = pointerToWorld(screenX, screenY);
      runtimeLog("event=input button=" + button + " screen=(" + screenX + "," + screenY
          + ") world=" + inputWorld + " player=" + playerEntity
          + " skillIndex=" + (skillSelect == null ? -1 : skillSelect.getSelectedIndex()));
      if (button == Input.Buttons.LEFT) {
        com.badlogic.gdx.math.Vector2 world = inputWorld;
        moveTarget.set(world);
        moving = playerEntity != Engine.INVALID_ENTITY && positions != null
            && positions.has(playerEntity) && velocities != null && velocities.has(playerEntity);
        runtimeLog("event=move_requested entity=" + playerEntity + " target=" + world
            + " started=" + moving + " mode=open_plane");
        return true;
      }
      if (button == Input.Buttons.RIGHT && sessionLog != null && sessionLog.file() != null
          && skillSelect.getSelectedIndex() > 0) {
        com.badlogic.gdx.math.Vector2 targetPoint = inputWorld;
        int targetId = targetModeValue == TargetMode.HOSTILE_UNIT ? nearestMonsterEntity()
            : targetModeValue == TargetMode.CORPSE ? nearestCorpseEntity() : Engine.INVALID_ENTITY;
        String targetName = targetId == Engine.INVALID_ENTITY ? "none" : "monster-" + targetId;
        sessionLog.append("event=cast skill=" + skillSelect.getSelected()
            + " targetMode=" + targetModeValue + " target=" + targetName
            + " x=" + targetPoint.x + " y=" + targetPoint.y);
        if (engine != null && playerEntity != Engine.INVALID_ENTITY) {
          int skillId = Riiablo.files.skills.index(skillSelect.getSelected());
          net.mostlyoriginal.api.event.common.EventSystem events =
              engine.getSystem(net.mostlyoriginal.api.event.common.EventSystem.class);
          if (skillId >= 0 && events != null) {
            SkillCastEvent cast = SkillCastEvent.obtain(playerEntity, skillId, targetId, targetPoint);
            events.dispatch(cast);
            String result = "event=cast_result skillId=" + skillId + " target=" + targetName
                + " accepted=" + cast.accepted + " resultCode=" + cast.resultCode
                + " manaCost=" + cast.manaCost;
            sessionLog.append(result);
            runtimeLog(result);
          } else {
            String result = "event=cast_skipped skillId=" + skillId + " events=" + (events != null);
            sessionLog.append(result);
            runtimeLog(result);
          }
        }
        return true;
      }
      runtimeLog("event=input_ignored button=" + button + " reason=skill_not_selected_or_log_unavailable");
      return false;
    }
  }

  private com.badlogic.gdx.math.Vector2 pointerToWorld(int screenX, int screenY) {
    com.badlogic.gdx.math.Vector2 point = new com.badlogic.gdx.math.Vector2(screenX, screenY);
    if (stage != null) stage.getViewport().unproject(point);
    if (iso == null) return point;
    // Rendering converts tile coordinates to pixels around (0,0), then adds
    // the arena centre. Undo that exact transform; do not call
    // IsometricCamera.screenToWorld here because the point has already been
    // unprojected by ScreenViewport.
    point.sub(stage.getViewport().getWorldWidth() * .5f,
        stage.getViewport().getWorldHeight() * .5f);
    return iso.toWorld(point);
  }

  private int nearestMonsterEntity() {
    if (positions == null || !positions.has(playerEntity)) return Engine.INVALID_ENTITY;
    com.badlogic.gdx.math.Vector2 origin = positions.get(playerEntity).position;
    int nearest = Engine.INVALID_ENTITY;
    float best = Float.MAX_VALUE;
    for (int entity : monsterEntities) {
      if (!positions.has(entity)) continue;
      float distance = origin.dst2(positions.get(entity).position);
      if (distance < best) { best = distance; nearest = entity; }
    }
    return nearest;
  }

  private int nearestCorpseEntity() {
    if (positions == null || !positions.has(playerEntity)) return Engine.INVALID_ENTITY;
    com.badlogic.gdx.math.Vector2 origin = positions.get(playerEntity).position;
    int nearest = Engine.INVALID_ENTITY;
    float best = Float.MAX_VALUE;
    for (int entity : corpseEntities) {
      if (!positions.has(entity)) continue;
      float distance = origin.dst2(positions.get(entity).position);
      if (distance < best) { best = distance; nearest = entity; }
    }
    return nearest;
  }

  private TargetMode determineTargetMode(String skillName) {
    if (!resourcesLoaded || Riiablo.files == null || Riiablo.files.NativeSkills == null) {
      return TargetMode.HOSTILE_UNIT;
    }
    for (com.riiablo.codec.excel.NativeSkills.Entry entry : Riiablo.files.NativeSkills) {
      if (skillName.equalsIgnoreCase(entry.skill)) {
        if (entry.bool("TargetCorpse")) return TargetMode.CORPSE;
        if (entry.bool("TargetAlly") || entry.bool("TargetPet")) return TargetMode.ALLY_PET;
        if (entry.bool("TargetItem")) return TargetMode.ITEM;
        if (entry.bool("Warp") || entry.bool("TgtPlaceCheck")
            || entry.bool("SearchOpenXY") || entry.bool("SearchEnemyXY")) {
          return TargetMode.GROUND_POINT;
        }
        if (entry.bool("Passive") || entry.bool("Aura")) return TargetMode.SELF;
        return TargetMode.HOSTILE_UNIT;
      }
    }
    return TargetMode.HOSTILE_UNIT;
  }

  private Marker selectTarget(int screenX, int screenY) {
    switch (targetModeValue) {
      case CORPSE: return nearest(corpses);
      case HOSTILE_UNIT: return nearest(monsters);
      case ALLY_PET:
      case SELF: return player;
      case GROUND_POINT:
        player.x = screenX - Gdx.graphics.getWidth() * .5f;
        player.y = Gdx.graphics.getHeight() * .52f - screenY;
        return new Marker(player.x, player.y, "ground");
      case ITEM:
      case NONE:
      default: return null;
    }
  }

  private Marker nearest(Array<Marker> candidates) {
    Marker nearest = null;
    float best = Float.MAX_VALUE;
    for (Marker marker : candidates) {
      float dx = marker.x - player.x;
      float dy = marker.y - player.y;
      float distance = dx * dx + dy * dy;
      if (distance < best) { best = distance; nearest = marker; }
    }
    return nearest;
  }

  private static final class Marker {
    float x, y; final String type;
    Marker(float x, float y, String type) { this.x = x; this.y = y; this.type = type; }
  }

  private enum TargetMode {
    NONE, HOSTILE_UNIT, GROUND_POINT, CORPSE, ALLY_PET, SELF, ITEM
  }
}
