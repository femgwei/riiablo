package com.riiablo.screen.panel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFontCache;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.input.GestureDetector;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.riiablo.Colors;
import com.riiablo.Fonts;
import com.riiablo.Riiablo;
import com.riiablo.Textures;
import com.riiablo.codec.DC6;
import com.riiablo.codec.FontTBL;
import com.riiablo.widget.Button;
import com.riiablo.widget.HotkeyButton;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Real mobile widgets and reference counts; no Android, GL or MPQ required. */
class MobilePanelLifecycleTest {
  private static final String SOUND = "data/global/sfx/cursor/button.wav";
  private static final String MENU = "data/global/ui/PANEL/minipanelbtn.DC6";
  private static final String ICON = "data/global/ui/SPELLS/Skillicon.DC6";
  private static final String SORCERESS = "data/global/ui/SPELLS/SoSkillicon.DC6";
  private final Map<Field, Object> aliases = new HashMap<>();
  private final Array<Disposable> resources = new Array<>();
  private Application previousApp;
  private com.badlogic.gdx.Files previousFiles;
  private AssetManager assets;

  @BeforeEach void setup() throws Exception {
    previousApp = Gdx.app; previousFiles = Gdx.files;
    Gdx.app = mock(Application.class); Gdx.files = mock(com.badlogic.gdx.Files.class);
    for (Field field : Riiablo.class.getFields()) {
      if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
        aliases.put(field, field.get(null));
      }
    }
    Riiablo.colors = new Colors(); Riiablo.charData = null; Riiablo.game = null;
    Texture texture = mock(Texture.class);
    when(texture.getWidth()).thenReturn(32); when(texture.getHeight()).thenReturn(32);
    TextureRegion region = new TextureRegion(texture);
    Riiablo.textures = mock(Textures.class);
    set(Textures.class, Riiablo.textures, "white", texture);
    Riiablo.fonts = mock(Fonts.class);
    FontTBL.BitmapFont font = mock(FontTBL.BitmapFont.class);
    BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData(); data.lineHeight = 16;
    set(BitmapFont.class, font, "data", data); set(BitmapFont.class, font, "regions", Array.with(region));
    when(font.getData()).thenReturn(data); when(font.getColor()).thenReturn(Color.WHITE);
    when(font.getRegions()).thenReturn(Array.with(region)); when(font.getLineHeight()).thenReturn(16f);
    when(font.newFontCache()).thenAnswer(call -> new BitmapFontCache(font));
    Riiablo.fonts.font16 = font;
    assets = spy(new AssetManager(FileHandle::new, false)); Riiablo.assets = assets;
    assets.setLoader(DC6.class, new SynchronousAssetLoader<DC6, AssetLoaderParameters<DC6>>(FileHandle::new) {
      @Override public DC6 load(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<DC6> params) {
        DC6 sheet = mock(DC6.class);
        when(sheet.getTexture(anyInt())).thenReturn(region); return sheet;
      }
      @Override public Array<AssetDescriptor> getDependencies(String name, FileHandle file, AssetLoaderParameters<DC6> params) { return null; }
    });
    assets.setLoader(Sound.class, new SynchronousAssetLoader<Sound, AssetLoaderParameters<Sound>>(FileHandle::new) {
      @Override public Sound load(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<Sound> params) { return mock(Sound.class); }
      @Override public Array<AssetDescriptor> getDependencies(String name, FileHandle file, AssetLoaderParameters<Sound> params) { return null; }
    });
  }

  @AfterEach void restore() throws Exception {
    try {
      for (Disposable resource : resources) resource.dispose();
      assets.dispose();
    } finally {
      for (Map.Entry<Field, Object> alias : aliases.entrySet()) alias.getKey().set(null, alias.getValue());
      Gdx.app = previousApp; Gdx.files = previousFiles;
    }
  }

  @Test void menuOwnsAllNineButtonsIncludingUnattachedMapAndReparentedEscape() {
    MobilePanel panel = own(new MobilePanel());
    Button[] buttons = menuButtons(panel);
    Group root = new Group(); root.addActor(panel); root.addActor(panel.btnEscapeMenu);
    assertNull(panel.btnMap.getParent());
    assets.finishLoading(); assertEquals(9, assets.getReferenceCount(SOUND));
    panel.dispose(); panel.dispose();
    assertEquals(0, assets.getLoadedAssets()); assertEquals(0, root.getChildren().size);
    for (Button button : buttons) {
      assertNull(button.getParent()); assertEquals(0, button.getListeners().size);
    }
    assertNull(panel.getEscapeButton()); assertEquals(Touchable.disabled, panel.getTouchable());
  }

  @Test void controlsOwnSixButtonsAndRemoveSharedGestureFromEverySkillSlot() throws Exception {
    MobileControls controls = own(new MobileControls());
    HotkeyButton[] slots = controls.skills.clone(); Button interact = controls.interact;
    ActorGestureListener gesture = (ActorGestureListener) get(controls, "gestureListener");
    GestureDetector detector = spy(gesture.getGestureDetector());
    set(ActorGestureListener.class, gesture, "detector", detector);
    Group root = new Group(); root.addActor(controls);
    assets.finishLoading(); assertEquals(6, assets.getReferenceCount(SOUND));
    controls.dispose(); controls.dispose();
    verify(detector).cancel();
    assertEquals(0, assets.getLoadedAssets()); assertEquals(0, root.getChildren().size);
    assertEquals(0, interact.getListeners().size);
    for (HotkeyButton slot : slots) assertEquals(0, slot.getListeners().size);
    assertNull(controls.skills); assertNull(controls.Skillicon); assertNull(controls.SoSkillicon);
  }

  @ParameterizedTest @ValueSource(booleans = {false, true})
  void pendingSoundLoadsAndSharedSheetsReturnOnlyOwnedReferencesToOriginalHost(boolean controls) {
    String[] sheets = controls ? new String[] {ICON, SORCERESS} : new String[] {MENU};
    for (String path : sheets) assets.load(path, DC6.class);
    assets.load(SOUND, Sound.class); assets.finishLoading();
    Disposable widget = own(controls ? new MobileControls() : new MobilePanel());
    // Button loads remain queued. Teardown must neither cancel an external
    // reference nor follow a replacement global AssetManager.
    AssetManager replacement = mock(AssetManager.class); Riiablo.assets = replacement;
    widget.dispose(); widget.dispose(); assets.finishLoading();
    for (String path : sheets) assertEquals(1, assets.getReferenceCount(path));
    assertEquals(1, assets.getReferenceCount(SOUND)); verifyNoInteractions(replacement);
  }

  @Test void lateMenuClickAndControlGestureCannotReachNewSessionOrDrawReleasedAssets() throws Exception {
    MobilePanel panel = own(new MobilePanel()); MobileControls controls = own(new MobileControls());
    ClickListener menuClick = lastClick(panel.btnCharacter);
    ClickListener interactClick = lastClick(controls.interact);
    ActorGestureListener gesture = (ActorGestureListener) get(controls, "gestureListener");
    panel.dispose(); controls.dispose();
    // A null game is deliberate: an unguarded old callback would dereference it.
    menuClick.clicked(null, 0, 0); interactClick.clicked(null, 0, 0);
    assertFalse(gesture.longPress(null, 0, 0)); gesture.tap(null, 0, 0, 1, 0);
    Batch batch = mock(Batch.class);
    panel.act(1); controls.act(1); panel.draw(batch, 1); controls.draw(batch, 1);
    verifyNoInteractions(batch);
  }

  @ParameterizedTest @ValueSource(booleans = {false, true})
  void borrowedQuickChooserIsUnboundOnlyWhenItStillTargetsThisControlsSlot(boolean newerObserver) throws Exception {
    MobileControls controls = own(new MobileControls());
    SpellsQuickPanel chooser = mock(SpellsQuickPanel.class);
    chooser.observer = newerObserver ? mock(HotkeyButton.class) : controls.skills[0];
    set(MobileControls.class, controls, "observedPanel", chooser);
    controls.dispose();
    if (newerObserver) verifyNoInteractions(chooser);
    else { verify(chooser).setObserver(null); verify(chooser).setVisible(false); }
  }

  @ParameterizedTest @ValueSource(booleans = {false, true})
  void buttonFailureDoesNotSkipOtherButtonsSheetsOrDetachAndIsNotRetried(boolean controlsWidget) {
    Actor widget = controlsWidget ? own(new MobileControls()) : own(new MobilePanel());
    Button button;
    if (controlsWidget) {
      MobileControls controls = (MobileControls) widget;
      button = spy(controls.interact); controls.interact.remove(); controls.interact = button; controls.addActor(button);
    } else {
      MobilePanel panel = (MobilePanel) widget;
      button = spy(panel.btnCharacter); panel.btnCharacter.remove(); panel.btnCharacter = button; panel.addActor(button);
    }
    Error failure = new AssertionError("button cleanup");
    doAnswer(call -> { call.callRealMethod(); throw failure; }).when(button).dispose();
    Group root = new Group(); root.addActor(widget);
    assertSame(failure, assertThrows(AssertionError.class, () -> ((Disposable) widget).dispose()));
    assertEquals(0, assets.getLoadedAssets()); assertEquals(0, root.getChildren().size);
    ((Disposable) widget).dispose(); verify(button).dispose();
  }

  @ParameterizedTest @ValueSource(booleans = {false, true})
  void constructorFailureRollsBackAlreadyAcquiredSheetsAndButtons(boolean controls) {
    Error failure = new AssertionError("button texture");
    if (controls) {
      assets.load(ICON, DC6.class); assets.finishLoading();
      DC6 icons = assets.get(ICON, DC6.class);
      when(icons.getTexture(0)).thenThrow(failure);
      assertSame(failure, assertThrows(AssertionError.class, MobileControls::new));
      assertEquals(1, assets.getReferenceCount(ICON)); assertFalse(assets.contains(SORCERESS));
      assertFalse(assets.contains(SOUND)); assets.unload(ICON);
    } else {
      assets.load(MENU, DC6.class); assets.finishLoading();
      DC6 menu = assets.get(MENU, DC6.class);
      when(menu.getTexture(2)).thenThrow(failure);
      assertSame(failure, assertThrows(AssertionError.class, MobilePanel::new));
      assertEquals(1, assets.getReferenceCount(MENU)); assertFalse(assets.contains(SOUND)); assets.unload(MENU);
    }
    assertEquals(0, assets.getLoadedAssets());
  }

  private Button[] menuButtons(MobilePanel panel) {
    return new Button[] {panel.btnCharacter, panel.btnInventory, panel.btnSkillTree, panel.btnParty,
        panel.btnMap, panel.btnMessages, panel.btnQuests, panel.btnEscapeMenu, panel.btnSwapWeapons};
  }
  private static ClickListener lastClick(Actor actor) {
    ClickListener result = null;
    for (com.badlogic.gdx.scenes.scene2d.EventListener listener : actor.getListeners()) {
      if (listener instanceof ClickListener) result = (ClickListener) listener;
    }
    return result;
  }
  private <T extends Disposable> T own(T resource) { resources.add(resource); return resource; }
  private static Object get(Object object, String name) throws Exception {
    Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
  }
  private static void set(Class<?> type, Object object, String name, Object value) throws Exception {
    Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(object, value);
  }
}
