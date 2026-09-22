package com.riiablo.screen.panel;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.Input;

import java.util.ArrayList;
import java.util.List;

import com.riiablo.Riiablo;
import com.riiablo.Cvars;
import com.riiablo.Keys;
import com.riiablo.audio.SoundOptions;
import com.riiablo.codec.Animation;
import com.riiablo.codec.DC6;
import com.riiablo.engine.client.automap.AutomapOptions;
import com.riiablo.graphics.PaletteIndexedColorDrawable;
import com.riiablo.graphics.DisplayOptions;
import com.riiablo.graphics.VideoOptions;
import com.riiablo.loader.DC6Loader;
import com.riiablo.map.RenderSystem;
import com.riiablo.key.MappedKey;
import com.riiablo.screen.MenuScreen;
import com.riiablo.widget.Label;
import com.riiablo.widget.LabelButton;

public class EscapePanel extends WidgetGroup implements Disposable {

  public enum Page { MAIN, OPTIONS, SOUND, VIDEO, AUTOMAP, CONTROLS }

  final AssetDescriptor<DC6> optionsDescriptor = localUi("options.dc6");
  final AssetDescriptor<DC6> exitDescriptor = localUi("exit.dc6");
  final AssetDescriptor<DC6> returntogameDescriptor = localUi("returntogame.dc6");
  EscapeButton options;
  EscapeButton exit;
  EscapeButton returntogame;

  final AssetDescriptor<DC6> pentspinDescriptor = new AssetDescriptor<>("data\\global\\ui\\CURSOR\\pentspin.DC6", DC6.class);
  Animation pentspinL, pentspin;
  FocusActor[] focusActor;
  Table mainPage;
  Table optionsPage;
  Table soundPage;
  Table videoPage;
  Table automapPage;
  Table controlsPage;
  Page currentPage = Page.MAIN;

  OptionRow automapMode;
  OptionRow automapFade;
  OptionRow automapCenter;
  OptionRow automapParty;
  OptionRow automapNames;
  OptionRow soundEnabled;
  OptionRow effectsEnabled;
  OptionRow effectsVolume;
  OptionRow musicEnabled;
  OptionRow musicVolume;
  OptionRow gamma;
  OptionRow vsync;
  OptionRow resolution;
  OptionRow showFps;
  OptionRow statusBar;
  OptionRow vibration;
  Label controlsStatus;
  final ControlsOptionsState controlsState = new ControlsOptionsState();
  final List<ControlBindingRow> controlRows = new ArrayList<>();
  ControlBindingRow capturingRow;
  int capturingAssignment;

  private static AssetDescriptor<DC6> localUi(String file) {
    String language = Riiablo.language == null ? "eng" : Riiablo.language.resourceCode;
    return new AssetDescriptor<>("data\\local\\ui\\" + language + "\\" + file,
        DC6.class, DC6Loader.DC6Parameters.COMBINE);
  }

  private static final MappedKey[] CONFIGURABLE_KEYS = {
      Keys.Inventory, Keys.Character, Keys.Spells, Keys.Hireling, Keys.Quests,
      Keys.Party, Keys.Stash, Keys.Vendor, Keys.SwapWeapons, Keys.Enter, Keys.Automap,
      Keys.Help,
      Keys.Skill1, Keys.Skill2, Keys.Skill3, Keys.Skill4, Keys.Skill5, Keys.Skill6,
      Keys.Skill7, Keys.Skill8, Keys.Belt1, Keys.Belt2, Keys.Belt3, Keys.Belt4,
      Keys.MoveUp, Keys.MoveDown, Keys.MoveLeft, Keys.MoveRight, Keys.Run,
      Keys.AutomapZoomIn, Keys.AutomapZoomOut, Keys.AutomapReset
  };

  public EscapePanel() {
    Riiablo.assets.load(optionsDescriptor);
    Riiablo.assets.finishLoadingAsset(optionsDescriptor);
    options = new EscapeButton(Riiablo.assets.get(optionsDescriptor).getTexture(0));
    options.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        Riiablo.audio.play(2, true); // select.wav
        showPage(Page.OPTIONS);
      }
    });

    Riiablo.assets.load(exitDescriptor);
    Riiablo.assets.finishLoadingAsset(exitDescriptor);
    exit = new EscapeButton(Riiablo.assets.get(exitDescriptor).getTexture(0));
    exit.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        Riiablo.audio.play(2, true); // select.wav
        if (Riiablo.game != null && !Riiablo.game.saveCharacter()) return;
        Riiablo.client.clearAndSet(new MenuScreen());
      }
    });

    Riiablo.assets.load(returntogameDescriptor);
    Riiablo.assets.finishLoadingAsset(returntogameDescriptor);
    returntogame = new EscapeButton(Riiablo.assets.get(returntogameDescriptor).getTexture(0));
    returntogame.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        Riiablo.audio.play(2, true); // select.wav
        close();
      }
    });

    Riiablo.assets.load(pentspinDescriptor);
    Riiablo.assets.finishLoadingAsset(pentspinDescriptor);
    pentspinL = Animation.newAnimation(Riiablo.assets.get(pentspinDescriptor));
    pentspinL.setReversed(true);
    pentspin = Animation.newAnimation(Riiablo.assets.get(pentspinDescriptor));
    focusActor = new FocusActor[6];
    for (int i = 0; i < focusActor.length; i += 2) {
      focusActor[i    ] = new FocusActor(pentspinL);
      focusActor[i + 1] = new FocusActor(pentspin);
    }
    ClickListener focusListener = new ClickListener() {
      @Override
      public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
        setVisible(event.getListenerActor(), true);
      }

      @Override
      public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
        setVisible(event.getListenerActor(), false);
      }

      void setVisible(Actor actor, boolean b) {
        if (actor == options) {
          focusActor[0].setVisible(b);
          focusActor[1].setVisible(b);
        } else if (actor == exit) {
          focusActor[2].setVisible(b);
          focusActor[3].setVisible(b);
        } else if (actor == returntogame) {
          focusActor[4].setVisible(b);
          focusActor[5].setVisible(b);
        }
      }
    };
    options.addListener(focusListener);
    exit.addListener(focusListener);
    returntogame.addListener(focusListener);

    final int spacing = 24;
    mainPage = new Table();
    mainPage.align(Align.center);
    mainPage.add(focusActor[0]);
    mainPage.add(options).space(0, spacing, 0, spacing).fillX();
    mainPage.add(focusActor[1]).row();
    mainPage.add(focusActor[2]);
    mainPage.add(exit).space(0, spacing, 0, spacing).fillX();
    mainPage.add(focusActor[3]).row();
    mainPage.add(focusActor[4]);
    mainPage.add(returntogame).space(0, spacing, 0, spacing).fillX();
    mainPage.add(focusActor[5]).row();

    mainPage.setFillParent(true);
    mainPage.setBackground(new PaletteIndexedColorDrawable(Riiablo.colors.modal50));
    addActor(mainPage);

    optionsPage = createOptionsPage();
    soundPage = createSoundPage();
    videoPage = createVideoPage();
    automapPage = createAutomapPage();
    controlsPage = createControlsPage();
    addActor(optionsPage);
    addActor(soundPage);
    addActor(videoPage);
    addActor(automapPage);
    addActor(controlsPage);
    showPage(Page.MAIN);

    setFillParent(true);
    setVisible(false);
    //setTouchable(Touchable.childrenOnly);
    setTouchable(Touchable.enabled);
    addListener(new InputListener() {
      @Override
      public boolean keyDown(InputEvent event, int keycode) {
        return captureKey(keycode);
      }
    });
    //setDebug(true, true);
  }

  private Table createOptionsPage() {
    Table page = createPage(null);
    page.add(largeMenuButton(ui("options_sound"), () -> showPage(Page.SOUND))).height(46).row();
    page.add(largeMenuButton(ui("options_video"), () -> showPage(Page.VIDEO))).height(46).row();
    page.add(largeMenuButton(ui("options_automap"), () -> showPage(Page.AUTOMAP))).height(46).row();
    page.add(largeMenuButton(ui("options_controls"), () -> showPage(Page.CONTROLS))).height(46).row();
    page.add(largeMenuButton(ui("previous_menu"), () -> showPage(Page.MAIN))).height(46).padTop(12).row();
    return page;
  }

  private Table createAutomapPage() {
    Table page = createPage(ui("automap_title"));
    automapMode = new OptionRow(ui("automap_size"), () -> {
      int current = Cvars.Client.Automap.Mode.get() == null
          ? RenderSystem.AUTOMAP_MODE_CENTER : Cvars.Client.Automap.Mode.get();
      int next = AutomapOptions.nextMode(current);
      Cvars.Client.Automap.Mode.set((byte) next);
      RenderSystem.setAutomapPreferredMode(next);
      refreshAutomapRows();
    });
    automapFade = booleanOption(ui("automap_fade"), Cvars.Client.Automap.Fade);
    automapCenter = booleanOption(ui("automap_center_when_cleared"),
        Cvars.Client.Automap.CenterWhenCleared);
    automapParty = booleanOption(ui("automap_show_party"), Cvars.Client.Automap.ShowParty);
    automapNames = booleanOption(ui("automap_show_npc_names"), Cvars.Client.Automap.ShowNames);
    page.add(automapMode).width(520).height(24).row();
    page.add(automapFade).width(520).height(24).row();
    page.add(automapCenter).width(520).height(24).row();
    page.add(automapParty).width(520).height(24).row();
    page.add(automapNames).width(520).height(24).row();
    page.add(menuButton(ui("previous_menu"), () -> showPage(Page.OPTIONS)))
        .height(24).padTop(12).row();
    refreshAutomapRows();
    return page;
  }

  private Table createSoundPage() {
    Table page = createPage(ui("sound_title"));
    soundEnabled = new OptionRow(ui("sound_enabled"), () -> {
      Cvars.Client.Sound.Enabled.set(!Boolean.TRUE.equals(Cvars.Client.Sound.Enabled.get()));
      refreshSoundRows();
    });
    effectsEnabled = new OptionRow(ui("sound_effects"), () -> {
      Cvars.Client.Sound.Effects.Enabled.set(
          !Boolean.TRUE.equals(Cvars.Client.Sound.Effects.Enabled.get()));
      refreshSoundRows();
    });
    effectsVolume = new OptionRow(ui("sound_volume"), () -> {
      Cvars.Client.Sound.Effects.Volume.set(
          SoundOptions.nextVolume(volume(Cvars.Client.Sound.Effects.Volume.get())));
      refreshSoundRows();
    });
    musicEnabled = new OptionRow(ui("music_enabled"), () -> {
      Cvars.Client.Sound.Music.Enabled.set(
          !Boolean.TRUE.equals(Cvars.Client.Sound.Music.Enabled.get()));
      refreshSoundRows();
    });
    musicVolume = new OptionRow(ui("music_volume"), () -> {
      Cvars.Client.Sound.Music.Volume.set(
          SoundOptions.nextVolume(volume(Cvars.Client.Sound.Music.Volume.get())));
      refreshSoundRows();
    });
    page.add(soundEnabled).width(520).height(24).row();
    page.add(effectsEnabled).width(520).height(24).row();
    page.add(effectsVolume).width(520).height(24).row();
    page.add(musicEnabled).width(520).height(24).row();
    page.add(musicVolume).width(520).height(24).row();
    page.add(menuButton(ui("previous_menu"), () -> showPage(Page.OPTIONS)))
        .height(24).padTop(12).row();
    refreshSoundRows();
    return page;
  }

  private Table createVideoPage() {
    Table page = createPage(ui("video_title"));
    gamma = new OptionRow(ui("video_gamma"), () -> {
      Cvars.Client.Display.Gamma.set(
          VideoOptions.nextGamma(value(Cvars.Client.Display.Gamma.get())));
      refreshVideoRows();
    });
    vsync = new OptionRow(ui("video_vsync"), () -> {
      Cvars.Client.Display.VSync.set(!Boolean.TRUE.equals(Cvars.Client.Display.VSync.get()));
      refreshVideoRows();
    });
    showFps = new OptionRow(ui("video_show_fps"), () -> {
      byte current = Cvars.Client.Display.ShowFPS.get() == null
          ? com.riiablo.Client.FPS_NONE : Cvars.Client.Display.ShowFPS.get();
      Cvars.Client.Display.ShowFPS.set(DisplayOptions.nextFpsMode(current));
      refreshVideoRows();
    });
    resolution = new OptionRow(ui("video_resolution"), null, false);
    page.add(gamma).width(520).height(24).row();
    page.add(vsync).width(520).height(24).row();
    page.add(showFps).width(520).height(24).row();
    page.add(resolution).width(520).height(24).row();
    statusBar = new OptionRow(ui("video_status_bar"), null, false);
    page.add(statusBar).width(520).height(24).row();
    page.add(menuButton(ui("previous_menu"), () -> showPage(Page.OPTIONS)))
        .height(24).padTop(12).row();
    refreshVideoRows();
    return page;
  }

  private Table createControlsPage() {
    Table page = createPage(ui("controls_title"));
    controlsStatus = new Label(ui("controls_select_binding"), Riiablo.fonts.font16,
        Riiablo.colors.grey);
    controlsStatus.setAlignment(Align.center);
    page.add(controlsStatus).width(750).height(24).row();

    Table grid = new Table();
    grid.defaults().pad(1);
    controlRows.clear();
    for (int i = 0; i < CONFIGURABLE_KEYS.length; i++) {
      ControlBindingRow row = new ControlBindingRow(CONFIGURABLE_KEYS[i]);
      controlRows.add(row);
      grid.add(row).width(246).height(27);
      if ((i + 1) % 3 == 0) grid.row();
    }
    if (CONFIGURABLE_KEYS.length % 3 != 0) grid.row();
    page.add(grid).width(750).row();
    vibration = new OptionRow(ui("controls_vibration"), () -> {
      Cvars.Client.Input.Vibration.set(!Boolean.TRUE.equals(Cvars.Client.Input.Vibration.get()));
      refreshControlsRows();
    });
    page.add(vibration).width(520).height(24).row();
    page.add(menuButton(ui("controls_reset_defaults"), this::resetControlDefaults))
        .height(24).padTop(8).row();
    page.add(menuButton(ui("previous_menu"), () -> showPage(Page.OPTIONS)))
        .height(24).padTop(4).row();
    return page;
  }

  private void beginCapture(ControlBindingRow row, int assignment) {
    capturingRow = row;
    capturingAssignment = assignment;
    Riiablo.keys.setCaptureMode(true);
    controlsState.beginCapture(mappingLabel(row.mapping), assignment != MappedKey.PRIMARY_MAPPING);
    controlsStatus.setText(controlsStatusLabel());
    if (getStage() != null) getStage().setKeyboardFocus(this);
  }

  private boolean captureKey(int keycode) {
    if (currentPage != Page.CONTROLS || capturingRow == null) return false;
    if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACK) {
      controlsState.cancel();
      cancelCapture(ui("controls_change_cancelled"));
      return true;
    }
    if (keycode == Input.Keys.BACKSPACE) {
      capturingRow.mapping.unassign(capturingAssignment);
      Riiablo.keys.save(capturingRow.mapping);
      refreshControlRows();
      controlsState.cleared();
      cancelCapture(ui("controls_binding_cleared"));
      return true;
    }

    for (MappedKey existing : Riiablo.keys.get(keycode)) {
      if (existing != capturingRow.mapping) {
        controlsState.conflict(mappingLabel(existing));
        controlsStatus.setText(controlsStatusLabel());
        return true;
      }
    }
    if (capturingRow.mapping.isAssigned(keycode)
        && capturingRow.mapping.getMapping(capturingAssignment) != keycode) {
      controlsState.conflict(mappingLabel(capturingRow.mapping));
      controlsStatus.setText(ui("controls_already_used_by", mappingLabel(capturingRow.mapping)));
      return true;
    }

    try {
      capturingRow.mapping.assign(capturingAssignment, keycode);
      Riiablo.keys.save(capturingRow.mapping);
      refreshControlRows();
      controlsState.saved();
      cancelCapture(controlsStatusLabel());
    } catch (IllegalArgumentException e) {
      controlsStatus.setText(ui("controls_key_cannot_assign"));
    }
    return true;
  }

  private void cancelCapture(String message) {
    capturingRow = null;
    Riiablo.keys.setCaptureMode(false);
    controlsStatus.setText(message);
    if (getStage() != null) getStage().setKeyboardFocus(null);
  }

  private void resetControlDefaults() {
    Riiablo.keys.resetAll();
    refreshControlRows();
    controlsState.defaultsRestored();
    cancelCapture(controlsStatusLabel());
  }

  private void refreshControlRows() {
    for (ControlBindingRow row : controlRows) row.refresh();
    refreshControlsRows();
  }

  private void refreshControlsRows() {
    if (vibration != null) vibration.setValue(yesNo(Cvars.Client.Input.Vibration.get()));
  }

  private OptionRow booleanOption(String label, final com.riiablo.cvar.Cvar<Boolean> cvar) {
    return new OptionRow(label, () -> {
      cvar.set(!Boolean.TRUE.equals(cvar.get()));
      refreshAutomapRows();
    });
  }

  private Table createPlaceholderPage(String title) {
    Table page = createPage(title);
    Label unavailable = new Label(ui("options_unavailable"),
        Riiablo.fonts.font16, Riiablo.colors.grey);
    unavailable.setAlignment(Align.center);
    page.add(unavailable).height(30).row();
    page.add(menuButton(ui("previous_menu"), () -> showPage(Page.OPTIONS)))
        .height(24).padTop(12).row();
    return page;
  }

  private Table createPage(String title) {
    Table page = new Table();
    page.setFillParent(true);
    page.setBackground(new PaletteIndexedColorDrawable(Riiablo.colors.modal50));
    page.align(Align.center);
    page.defaults().center();
    if (title != null) {
      Label heading = new Label(title, Riiablo.fonts.font42, Riiablo.colors.gold);
      heading.setAlignment(Align.center);
      page.add(heading).height(50).padBottom(12).row();
    }
    return page;
  }

  private LabelButton menuButton(String text, final Runnable action) {
    LabelButton button = new LabelButton(text, Riiablo.fonts.font16);
    button.setAlignment(Align.center);
    button.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        Riiablo.audio.play(2, true);
        action.run();
      }
    });
    return button;
  }

  private LabelButton largeMenuButton(String text, final Runnable action) {
    LabelButton button = new LabelButton(text, Riiablo.fonts.font42);
    button.setAlignment(Align.center);
    button.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        Riiablo.audio.play(2, true);
        action.run();
      }
    });
    return button;
  }

  private void refreshAutomapRows() {
    if (automapMode == null) return;
    int mode = Cvars.Client.Automap.Mode.get() == null
        ? RenderSystem.AUTOMAP_MODE_CENTER : Cvars.Client.Automap.Mode.get();
    automapMode.setValue(automapModeLabel(mode));
    automapFade.setValue(yesNo(Cvars.Client.Automap.Fade.get()));
    automapCenter.setValue(yesNo(Cvars.Client.Automap.CenterWhenCleared.get()));
    automapParty.setValue(yesNo(Cvars.Client.Automap.ShowParty.get()));
    automapNames.setValue(yesNo(Cvars.Client.Automap.ShowNames.get()));
  }

  private void refreshSoundRows() {
    if (soundEnabled == null) return;
    soundEnabled.setValue(yesNo(Cvars.Client.Sound.Enabled.get()));
    effectsEnabled.setValue(yesNo(Cvars.Client.Sound.Effects.Enabled.get()));
    effectsVolume.setValue(SoundOptions.percentageLabel(
        volume(Cvars.Client.Sound.Effects.Volume.get())));
    musicEnabled.setValue(yesNo(Cvars.Client.Sound.Music.Enabled.get()));
    musicVolume.setValue(SoundOptions.percentageLabel(
        volume(Cvars.Client.Sound.Music.Volume.get())));
  }

  private void refreshVideoRows() {
    if (gamma == null) return;
    gamma.setValue(VideoOptions.gammaLabel(value(Cvars.Client.Display.Gamma.get())));
    vsync.setValue(yesNo(Cvars.Client.Display.VSync.get()));
    showFps.setValue(fpsModeLabel(Cvars.Client.Display.ShowFPS.get() == null
        ? com.riiablo.Client.FPS_NONE : Cvars.Client.Display.ShowFPS.get()));
    resolution.setValue(ui("not_available"));
    statusBar.setValue(ui("not_available"));
  }

  private static float volume(Float value) {
    return value == null ? 0f : value;
  }

  private static float value(Float value) {
    return value == null ? 1.0f : value;
  }

  private static String yesNo(Boolean value) {
    return Boolean.TRUE.equals(value) ? ui("yes") : ui("no");
  }

  private static String automapModeLabel(int mode) {
    switch (AutomapOptions.normalizeMode(mode)) {
      case RenderSystem.AUTOMAP_MODE_TOP_LEFT: return ui("automap_mode_top_left");
      case RenderSystem.AUTOMAP_MODE_TOP_RIGHT: return ui("automap_mode_top_right");
      default: return ui("automap_mode_full_screen");
    }
  }

  private static String fpsModeLabel(byte mode) {
    switch (DisplayOptions.normalizeFpsMode(mode)) {
      case com.riiablo.Client.FPS_TOPLEFT: return ui("position_top_left");
      case com.riiablo.Client.FPS_TOPRIGHT: return ui("position_top_right");
      case com.riiablo.Client.FPS_BOTTOMLEFT: return ui("position_bottom_left");
      case com.riiablo.Client.FPS_BOTTOMRIGHT: return ui("position_bottom_right");
      default: return ui("off");
    }
  }

  private String controlsStatusLabel() {
    switch (controlsState.getStatus()) {
      case CAPTURING:
        return ui("controls_press_key", controlsState.getBinding(),
            "SECONDARY".equals(controlsState.getDetail())
                ? ui("controls_secondary") : ui("controls_primary"));
      case CONFLICT: return ui("controls_conflict", controlsState.getDetail());
      case SAVED: return ui("controls_binding_saved");
      case CLEARED: return ui("controls_binding_cleared");
      case DEFAULTS_RESTORED: return ui("controls_defaults_restored");
      default: return ui("controls_select_binding");
    }
  }

  private static String mappingLabel(MappedKey mapping) {
    return Riiablo.language == com.riiablo.D2Language.CHINESE
        ? ui("key_" + mapping.getAlias())
        : mapping.getName();
  }

  private static String ui(String key, Object... args) {
    return args.length == 0 ? Riiablo.bundle.get(key) : Riiablo.bundle.format(key, args);
  }

  public void open() {
    showPage(Page.MAIN);
    setVisible(true);
  }

  public void close() {
    setVisible(false);
    showPage(Page.MAIN);
  }

  /** Returns true when Escape navigated to a parent page instead of closing the menu. */
  public boolean navigateBack() {
    switch (currentPage) {
      case MAIN:
        return false;
      case OPTIONS:
        showPage(Page.MAIN);
        return true;
      default:
        showPage(Page.OPTIONS);
        return true;
    }
  }

  public Page getCurrentPage() {
    return currentPage;
  }

  private void showPage(Page page) {
    currentPage = page;
    if (page == Page.SOUND) refreshSoundRows();
    if (page == Page.VIDEO) refreshVideoRows();
    if (page == Page.CONTROLS) refreshControlsRows();
    if (page == Page.AUTOMAP) refreshAutomapRows();
    if (mainPage != null) mainPage.setVisible(page == Page.MAIN);
    if (optionsPage != null) optionsPage.setVisible(page == Page.OPTIONS);
    if (soundPage != null) soundPage.setVisible(page == Page.SOUND);
    if (videoPage != null) videoPage.setVisible(page == Page.VIDEO);
    if (automapPage != null) automapPage.setVisible(page == Page.AUTOMAP);
    if (controlsPage != null) controlsPage.setVisible(page == Page.CONTROLS);
  }

  @Override
  public void dispose() {
    Riiablo.assets.unload(optionsDescriptor.fileName);
    Riiablo.assets.unload(exitDescriptor.fileName);
    Riiablo.assets.unload(returntogameDescriptor.fileName);
    Riiablo.assets.unload(pentspinDescriptor.fileName);
  }

  @Override
  public void draw(Batch batch, float a) {
    pentspinL.act();
    pentspin.act();
    super.draw(batch, a);
  }

  private static class EscapeButton extends Button {
    EscapeButton(TextureRegion region) {
      super(new TextureRegionDrawable(region));
    }

    @Override
    protected void drawBackground(Batch batch, float a, float x, float y) {
      Drawable background = getBackground();
      background.draw(batch,
          x + (getWidth() / 2) - (background.getMinWidth() / 2),
          getY(),
          background.getMinWidth(),
          background.getMinHeight());
    }
  }

  private static class FocusActor extends Actor {
    Animation pentspin;
    FocusActor(Animation pentspin) {
      this.pentspin = pentspin;
      setSize(pentspin.getMinWidth(), pentspin.getMinHeight());
      setVisible(false);
    }

    @Override
    public void draw(Batch batch, float a) {
      pentspin.draw(batch, getX(), getY());
    }
  }

  private static class OptionRow extends Table {
    final Label name;
    final Label value;

    OptionRow(String name, final Runnable action) {
      this(name, action, true);
    }

    OptionRow(String name, final Runnable action, boolean enabled) {
      // Match the parent Escape menu typography and row rhythm.  The old
      // formal10 rows made the Options pages visibly smaller and tighter than
      // the parent menu buttons.
      this.name = new Label(name, Riiablo.fonts.font16);
      this.value = enabled
          ? new LabelButton("", Riiablo.fonts.font16, Riiablo.colors.gold)
          : new Label("", Riiablo.fonts.font16, Riiablo.colors.grey);
      this.name.setAlignment(Align.left);
      this.value.setAlignment(Align.right);
      add(this.name).width(230).left();
      add(this.value).width(280).right();
      if (enabled && action != null) {
        ClickListener listener = new ClickListener() {
          @Override
          public void clicked(InputEvent event, float x, float y) {
            Riiablo.audio.play(2, true);
            action.run();
          }
        };
        this.name.addListener(listener);
        this.value.addListener(listener);
      } else {
        this.name.setColor(Riiablo.colors.grey);
        this.name.setTouchable(Touchable.disabled);
        this.value.setTouchable(Touchable.disabled);
      }
    }

    void setValue(String text) {
      value.setText(text);
    }
  }

  private final class ControlBindingRow extends Table {
    final MappedKey mapping;
    final Label name;
    final LabelButton primary;
    final LabelButton secondary;

    ControlBindingRow(MappedKey mapping) {
      this.mapping = mapping;
      name = new Label(mappingLabel(mapping), Riiablo.fonts.font16);
      primary = new LabelButton("", Riiablo.fonts.font16, Riiablo.colors.gold);
      secondary = new LabelButton("", Riiablo.fonts.font16, Riiablo.colors.gold);
      name.setAlignment(Align.left);
      primary.setAlignment(Align.center);
      secondary.setAlignment(Align.center);
      add(name).width(126).left();
      add(primary).width(58).center();
      add(secondary).width(58).center();
      primary.addListener(new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) {
          beginCapture(ControlBindingRow.this, MappedKey.PRIMARY_MAPPING);
        }
      });
      secondary.addListener(new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) {
          beginCapture(ControlBindingRow.this, MappedKey.SECONDARY_MAPPING);
        }
      });
      refresh();
    }

    void refresh() {
      primary.setText(keyLabel(mapping.getPrimaryAssignment()));
      secondary.setText(keyLabel(mapping.getSecondaryMapping()));
    }
  }

  private static String keyLabel(int keycode) {
    return keycode == MappedKey.NOT_MAPPED ? "--" : Input.Keys.toString(keycode);
  }
}
