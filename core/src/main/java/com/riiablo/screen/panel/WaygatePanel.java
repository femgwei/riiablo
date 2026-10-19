package com.riiablo.screen.panel;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.riiablo.Riiablo;
import com.riiablo.codec.DC;
import com.riiablo.codec.DC6;
import com.riiablo.codec.excel.Levels;
import com.riiablo.graphics.BlendMode;
import com.riiablo.loader.DC6Loader;
import com.riiablo.save.CharData;
import com.riiablo.widget.Button;
import com.riiablo.widget.IconTextButton;
import com.riiablo.util.Disposal;
import com.riiablo.util.AssetReferenceScope;

import java.util.Comparator;

public class WaygatePanel extends WidgetGroup implements Disposable {
  private static final String TAG = "WaygatePanel";
  private final AssetManager assets = java.util.Objects.requireNonNull(Riiablo.assets, "assets");
  private boolean disposed;
  private final AssetReferenceScope references = new AssetReferenceScope(assets);
  private final PanelSession session;
  public void bindWorld(com.artemis.World world) { session.bindWorld(world); }

  final AssetDescriptor<DC6> waygatebackgroundDescriptor = new AssetDescriptor<>("data\\global\\ui\\MENU\\waygatebackground.dc6", DC6.class, DC6Loader.DC6Parameters.COMBINE);
  TextureRegion waygatebackground;

  final AssetDescriptor<DC6> expwaygatetabsDescriptor = new AssetDescriptor<>("data\\global\\ui\\MENU\\expwaygatetabs.dc6", DC6.class, DC6Loader.DC6Parameters.COMBINE);
  DC expwaygatetabs;

  final AssetDescriptor<DC6> waygateiconsDescriptor = new AssetDescriptor<>("data\\global\\ui\\MENU\\waygateicons.dc6", DC6.class, DC6Loader.DC6Parameters.COMBINE);
  DC waygateicons;
  Button.ButtonStyle waygateButtonStyle;

  final AssetDescriptor<DC6> buysellbtnDescriptor = new AssetDescriptor<>("data\\global\\ui\\PANEL\\buysellbtn.DC6", DC6.class, DC6Loader.DC6Parameters.COMBINE);
  Button btnExit;

  ClickListener clickListener;
  final CharData charData;
  final Array<WaypointControl> waypointControls = new Array<>(42);
  private final Tab[] tabs = new Tab[5];
  private final Button[] actButtons = new Button[5];

  public WaygatePanel(CharData charData) {
    this(Riiablo.game, charData);
  }

  public WaygatePanel(com.riiablo.screen.GameScreen owner, CharData charData) {
    this.charData = charData;
    session = new PanelSession(owner);
    try { initialize(); session.finish(this); }
    catch (RuntimeException | Error failure) {
      try { dispose(); }
      catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
      throw failure;
    }
  }

  private void initialize() {
    references.load(waygatebackgroundDescriptor);
    assets.finishLoadingAsset(waygatebackgroundDescriptor);
    waygatebackground = assets.get(waygatebackgroundDescriptor).getTexture();
    setSize(waygatebackground.getRegionWidth(), waygatebackground.getRegionHeight());
    setTouchable(Touchable.enabled);
    setVisible(false);

    btnExit = new Button(new Button.ButtonStyle() {{
      references.load(buysellbtnDescriptor);
      assets.finishLoadingAsset(buysellbtnDescriptor);
      up   = new TextureRegionDrawable(assets.get(buysellbtnDescriptor).getTexture(10));
      down = new TextureRegionDrawable(assets.get(buysellbtnDescriptor).getTexture(11));
    }});
    session.own(btnExit);
    btnExit.setPosition(272, 14);
    btnExit.addListener(new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        if (!isCurrentSession()) return;
        if (session.owner() != null) session.owner().setLeftPanel(null);
        else setVisible(false);
      }
    });
    addActor(btnExit);

    references.load(expwaygatetabsDescriptor);
    assets.finishLoadingAsset(expwaygatetabsDescriptor);
    expwaygatetabs = assets.get(expwaygatetabsDescriptor);

    references.load(waygateiconsDescriptor);
    assets.finishLoadingAsset(waygateiconsDescriptor);
    waygateicons = assets.get(waygateiconsDescriptor);

    waygateButtonStyle = new Button.ButtonStyle() {{
      disabled = new TextureRegionDrawable(waygateicons.getTexture(0));
      up       = new TextureRegionDrawable(waygateicons.getTexture(3));
      down     = new TextureRegionDrawable(waygateicons.getTexture(4));
    }};

    clickListener = new ClickListener() {
      @Override
      public void clicked(InputEvent event, float x, float y) {
        if (!isCurrentSession()) return;
        IconTextButton button = (IconTextButton) event.getListenerActor();
        if (button.isDisabled()) return;
        Levels.Entry target = (Levels.Entry) button.getUserObject();
        if (session.owner() != null) session.owner().setLevel(target);
      }
    };

    @SuppressWarnings("unchecked")
    Array<Levels.Entry>[] waypoints = (Array<Levels.Entry>[]) new Array[5];
    for (int i = 0; i < waypoints.length; i++) waypoints[i] = new Array<>(9);
    for (Levels.Entry level : Riiablo.files.Levels) {
      if (level.Waypoint != 0xFF) {
        waypoints[level.Act].add(level);
      }
    }
    Comparator<Levels.Entry> comparator = new Comparator<Levels.Entry>() {
      @Override
      public int compare(Levels.Entry o1, Levels.Entry o2) {
        return o1.Waypoint - o2.Waypoint;
      }
    };
    for (Array<Levels.Entry> waypoint : waypoints) {
      waypoint.sort(comparator);
    }

    for (int i = 0; i < tabs.length; i++) {
      Tab tab = tabs[i] = new Tab();
      session.own(tab);
      for (Levels.Entry entry : waypoints[i]) {
        Actor controller = tab.addWaypoint(entry.LevelName);
        controller.setUserObject(entry);
        controller.addListener(clickListener);
        waypointControls.add(new WaypointControl(entry, (IconTextButton) controller));
      }

      tab.pack();
      tab.setWidth(290);
      tab.layout();
      //tab.setDebug(true, true);
      tab.setPosition(16, getHeight() - 58, Align.topLeft);
      tab.setVisible(false);
      addActor(tab);
    }

    float x = 2, y = getHeight() - 3;
    Button[] actors = actButtons;
    for (int i = 0; i < actors.length; i++) {
      final int j = i << 1;
      final Button actor = actors[i] = new Button(new Button.ButtonStyle() {{
        down = new TextureRegionDrawable(expwaygatetabs.getTexture(j));
        up   = new TextureRegionDrawable(expwaygatetabs.getTexture(j + 1));
        checked = down;
      }});
      session.own(actor);
      actor.setHighlightedBlendMode(BlendMode.ID, Color.WHITE);
      actor.setPosition(x, y, Align.topLeft);
      actor.setUserObject(tabs[i]);
      actor.addListener(new ClickListener() {
        @Override
        public void clicked(InputEvent event, float x, float y) {
          if (!isCurrentSession()) return;
          for (Tab tab : tabs) if (tab != null) tab.setVisible(false);
          Tab tab = (Tab) actor.getUserObject();
          tab.setVisible(true);
        }
      });
      addActor(actor);
      x += actor.getWidth();
    }

    ButtonGroup<Button> tabGroup = new ButtonGroup<>();
    tabGroup.add(actors);
    tabGroup.setMinCheckCount(1);
    tabGroup.setMaxCheckCount(1);
    tabs[0].setVisible(true);

    refresh();

    //setDebug(true, true);
  }

  /** Opens the current act's destinations and synchronizes the selected tab. */
  public void refresh(int act) {
    if (!isCurrentSession()) return;
    refresh();
    if (act < 0 || act >= tabs.length) return;
    actButtons[act].setChecked(true);
    for (int i = 0; i < tabs.length; i++) tabs[i].setVisible(i == act);
  }

  /** Refreshes destination availability from the current difficulty save flags. */
  public void refresh() {
    if (!isCurrentSession()) return;
    for (WaypointControl control : waypointControls) {
      Levels.Entry level = control.level;
      boolean active = charData.isWaypointActivated(level.Act, level.Waypoint);
      control.button.setDisabled(!active);
    }
  }

  @Override
  public void draw(Batch batch, float parentAlpha) {
    if (!isCurrentSession()) return;
    batch.draw(waygatebackground, getX(), getY());
    super.draw(batch, parentAlpha);
  }

  @Override
  public void dispose() {
    if (disposed) return;
    disposed = true;
    try (Disposal cleanup = new Disposal()) {
      cleanup.run(() -> super.setVisible(false));
      cleanup.run(() -> setTouchable(Touchable.disabled));
      cleanup.run(this::clearListeners);
      cleanup.run(() -> Button.disposeClickSounds(this));
      cleanup.dispose(btnExit);
      cleanup.run(() -> session.dispose(this));
      cleanup.dispose(references);
    } finally {
      waygatebackground = null;
      expwaygatetabs = null;
      waygateicons = null;
      waygateButtonStyle = null;
      clickListener = null;
      waypointControls.clear();
    }
  }

  private boolean isCurrentSession() { return !disposed && session.isCurrent() && charData == Riiablo.charData; }
  @Override public void act(float delta) { if (isCurrentSession()) super.act(delta); }
  @Override public Actor hit(float x, float y, boolean touchable) {
    return isCurrentSession() ? super.hit(x, y, touchable) : null;
  }
  @Override public void setVisible(boolean visible) {
    if (!disposed && (!visible || isCurrentSession())) super.setVisible(visible);
  }

  private class Tab extends Table {
    Tab() {
      columnDefaults(0).height(32).spaceBottom(4).growX();
    }

    Actor addWaypoint(String descId) {
      IconTextButton button = new IconTextButton(waygateButtonStyle, Riiablo.string.lookup(descId), Riiablo.fonts.font16);
      session.own(button);
      add(button).row();
      return button;
    }
  }

  private static class WaypointControl {
    final Levels.Entry level;
    final IconTextButton button;

    WaypointControl(Levels.Entry level, IconTextButton button) {
      this.level = level;
      this.button = button;
    }
  }
}
