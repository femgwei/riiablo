package com.riiablo.screen.panel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.*;
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.*;
import com.riiablo.*;
import com.riiablo.attributes.Attributes;
import com.riiablo.audio.Audio;
import com.riiablo.codec.DC6;
import com.riiablo.codec.FontTBL;
import com.riiablo.codec.StringTBLs;
import com.riiablo.codec.excel.Quests;
import com.riiablo.codec.excel.Speech;
import com.riiablo.codec.util.BBox;
import com.riiablo.save.CharData;
import com.riiablo.widget.Button;
import com.riiablo.widget.DialogScroller;
import com.riiablo.widget.TextArea;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.*;

/** Production quest widgets/scrollers and real reference counts; no MPQ or GL. */
class QuestsPanelLifecycleTest {
  private static final String SOUND = "data/global/sfx/cursor/button.wav";
  private static final String MENU = "data/global/ui/MENU/";
  private final Map<Field, Object> aliases = new HashMap<>();
  private final Array<Disposable> resources = new Array<>();
  private final Array<Audio.Instance> playback = new Array<>();
  private final Array<Quests.Entry> rows = new Array<>();
  private Application previousApp;
  private com.badlogic.gdx.Files previousFiles;
  private Input previousInput;
  private AssetManager assets;
  private CharData character;
  private Audio audioHost;
  private FontTBL.BitmapFont font;

  @BeforeEach void setup() throws Exception {
    previousApp = Gdx.app; previousFiles = Gdx.files; previousInput = Gdx.input;
    Gdx.app = mock(Application.class); Gdx.files = mock(com.badlogic.gdx.Files.class); Gdx.input = mock(Input.class);
    when(Gdx.app.getClipboard()).thenReturn(mock(Clipboard.class));
    for (Field field : Riiablo.class.getFields()) {
      if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) aliases.put(field, field.get(null));
    }
    Riiablo.colors = new Colors(); Riiablo.game = null; Riiablo.engine = null;
    Texture texture = mock(Texture.class);
    when(texture.getWidth()).thenReturn(32); when(texture.getHeight()).thenReturn(32);
    TextureRegion region = new TextureRegion(texture);
    Riiablo.textures = mock(Textures.class); set(Textures.class, Riiablo.textures, "white", texture);
    Riiablo.fonts = mock(Fonts.class); font = mock(FontTBL.BitmapFont.class);
    BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData(); data.lineHeight = 16;
    set(BitmapFont.class, font, "data", data); set(BitmapFont.class, font, "regions", Array.with(region));
    when(font.getData()).thenReturn(data); when(font.getColor()).thenReturn(Color.WHITE);
    when(font.getRegions()).thenReturn(Array.with(region)); when(font.getLineHeight()).thenReturn(16f);
    when(font.newFontCache()).thenAnswer(call -> new BitmapFontCache(font));
    Riiablo.fonts.font16 = font; Riiablo.fonts.fontformal11 = font;
    Riiablo.string = mock(StringTBLs.class); when(Riiablo.string.lookup(anyString())).thenReturn("text");
    when(Riiablo.string.lookup("speech")).thenReturn("60\nQuest speech");
    Riiablo.bundle = mock(I18NBundle.class); when(Riiablo.bundle.get(anyString())).thenReturn("unknown");
    Riiablo.files = mock(com.riiablo.Files.class);
    Quests quests = mock(Quests.class);
    for (int act = 0; act < 5; act++) {
      Quests.Entry row = new Quests.Entry(); row.act = act; row.id = 1;
      row.visible = true; row.icon = "fixtureQuest" + act; row.qstr = "quest" + act;
      row.qsts = new String[] {"questText"}; rows.add(row);
    }
    when(quests.iterator()).thenAnswer(call -> rows.iterator());
    set(com.riiablo.Files.class, Riiablo.files, "quests", quests);
    Speech speeches = mock(Speech.class); Speech.Entry speech = new Speech.Entry(); speech.soundstr = "speech";
    when(speeches.get(anyString())).thenReturn(speech);
    set(com.riiablo.Files.class, Riiablo.files, "speech", speeches);
    character = mock(CharData.class); Riiablo.charData = character;
    when(character.getStats()).thenReturn(mock(Attributes.class, RETURNS_DEEP_STUBS));
    when(character.getQuests(Riiablo.ACT1)).thenReturn(new short[8]);
    audioHost = mock(Audio.class); Riiablo.audio = audioHost;
    when(audioHost.play(anyString(), eq(false), eq(Audio.Channel.SPEECH))).thenAnswer(call -> {
      Audio.Instance instance = mock(Audio.Instance.class); playback.add(instance); return instance;
    });
    assets = spy(new AssetManager(FileHandle::new, false)); Riiablo.assets = assets;
    assets.setLoader(DC6.class, new SynchronousAssetLoader<DC6, AssetLoaderParameters<DC6>>(FileHandle::new) {
      @Override public DC6 load(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<DC6> params) {
        DC6 dc = mock(DC6.class);
        when(dc.getTexture()).thenReturn(region); when(dc.getTexture(anyInt())).thenReturn(region);
        when(dc.getTexture(anyInt(), anyInt())).thenReturn(region);
        when(dc.getNumDirections()).thenReturn(1); when(dc.getNumFramesPerDir()).thenReturn(27);
        TextureRegion[] frames = new TextureRegion[27]; java.util.Arrays.fill(frames, region);
        when(dc.getRegions()).thenReturn(new TextureRegion[][] {frames});
        when(dc.getBox(anyInt())).thenReturn(new BBox(0, 0, 32, 32)); return dc;
      }
      @Override public Array<AssetDescriptor> getDependencies(String name, FileHandle file, AssetLoaderParameters<DC6> params) { return null; }
    });
    assets.setLoader(Sound.class, new SynchronousAssetLoader<Sound, AssetLoaderParameters<Sound>>(FileHandle::new) {
      @Override public Sound load(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<Sound> params) { return mock(Sound.class); }
      @Override public Array<AssetDescriptor> getDependencies(String name, FileHandle file, AssetLoaderParameters<Sound> params) { return null; }
    });
  }

  @AfterEach void restore() throws Exception {
    try { for (Disposable resource : resources) resource.dispose(); assets.dispose(); }
    finally {
      for (Map.Entry<Field, Object> alias : aliases.entrySet()) alias.getKey().set(null, alias.getValue());
      Gdx.app = previousApp; Gdx.files = previousFiles; Gdx.input = previousInput;
    }
  }

  @Test void independentExitBalancesAllAssetsOnOriginalManagerAndClearsWholeTree() throws Exception {
    Array<String> paths = assetPaths();
    for (String path : paths) assets.load(path, DC6.class);
    assets.load(SOUND, Sound.class); assets.finishLoading();
    QuestsPanel panel = own(new QuestsPanel());
    Array<Actor> actors = new Array<>(); collect(panel, actors);
    assertEquals(7, countButtons(actors));
    Group root = new Group(); root.addActor(panel);
    AssetManager replacement = mock(AssetManager.class); Riiablo.assets = replacement;
    Riiablo.charData = mock(CharData.class);
    panel.dispose(); panel.dispose(); assets.finishLoading();
    assertEquals(paths.size + 1, assets.getLoadedAssets());
    for (String path : paths) assertEquals(1, assets.getReferenceCount(path), path);
    assertEquals(1, assets.getReferenceCount(SOUND)); verifyNoInteractions(replacement);
    assertTrue(root.getChildren().isEmpty()); assertNull(panel.activeTab);
    for (Actor actor : actors) {
      assertNull(actor.getParent()); assertTrue(actor.getListeners().isEmpty());
      assertTrue(actor.getCaptureListeners().isEmpty()); assertTrue(actor.getActions().isEmpty());
    }
  }

  @Test void closeAndReplayStopOnlyPreviousSpeechAndKeepTheScrollerReusable() throws Exception {
    QuestsPanel panel = own(new QuestsPanel()); select(panel, 0); replay(panel);
    Audio.Instance first = playback.peek(); replay(panel); Audio.Instance second = playback.peek();
    verify(first).stop(); verify(second, never()).stop();
    panel.setVisible(false); verify(second).stop();
    panel.setVisible(true); replay(panel); Audio.Instance third = playback.peek();
    assertEquals(3, playback.size); panel.dispose(); verify(third).stop();
    verify(first).stop(); verify(second).stop();
  }

  @Test void switchingTabsStopsSpeechWithoutRetiringEitherScroller() throws Exception {
    QuestsPanel panel = own(new QuestsPanel()); select(panel, 0); replay(panel);
    Audio.Instance first = playback.peek();
    Actor secondTab = ((Actor[]) get(panel, "tabs"))[1];
    for (Actor actor : panel.getChildren()) if (actor.getUserObject() == secondTab) lastClick(actor).clicked(null, 0, 0);
    verify(first).stop();
    Actor firstTab = ((Actor[]) get(panel, "tabs"))[0];
    for (Actor actor : panel.getChildren()) if (actor.getUserObject() == firstTab) lastClick(actor).clicked(null, 0, 0);
    select(panel, 0); replay(panel); assertEquals(2, playback.size);
    panel.dispose(); verify(playback.peek()).stop();
  }

  @Test void allFiveIncludingHiddenScrollersStopOnExitAndLateCallbacksDoNothing() throws Exception {
    QuestsPanel panel = own(new QuestsPanel()); select(panel, 0);
    Array<ClickListener> clicks = new Array<>(); Array<Actor> actors = new Array<>(); collect(panel, actors);
    for (Actor actor : actors) for (EventListener listener : actor.getListeners()) if (listener instanceof ClickListener) clicks.add((ClickListener) listener);
    Array<DialogScroller.DialogCompletionListener> completions = new Array<>();
    Array<DialogScroller> dialogs = dialogs(panel);
    for (DialogScroller dialog : dialogs) {
      completions.add((DialogScroller.DialogCompletionListener) get(dialog, "listener")); dialog.play("replay");
    }
    assertEquals(5, playback.size); panel.dispose(); panel.dispose();
    Riiablo.charData = mock(CharData.class); Riiablo.string = mock(StringTBLs.class);
    Audio replacement = mock(Audio.class); Riiablo.audio = replacement;
    for (ClickListener click : clicks) click.clicked(null, 0, 0);
    for (int i = 0; i < dialogs.size; i++) {
      DialogScroller dialog = dialogs.get(i); completions.get(i).onCompleted(dialog);
      dialog.act(10000); dialog.play("late"); dialog.setTextId("late"); dialog.setText("late");
      assertNull(get(dialog, "listener")); assertNull(get(dialog, "audio"));
    }
    panel.setVisible(true); assertFalse(panel.isVisible());
    Batch batch = mock(Batch.class); panel.act(1); panel.draw(batch, 1);
    verifyNoInteractions(batch, replacement, Riiablo.charData, Riiablo.string);
    for (Audio.Instance instance : playback) verify(instance).stop();
    assertEquals(0, assets.getLoadedAssets());
  }

  @Test void oldLivePanelIgnoresNewCharactersClicksAndRendering() throws Exception {
    QuestsPanel panel = own(new QuestsPanel()); select(panel, 0);
    ClickListener replay = lastClick(panel.btnPlayQuest);
    Riiablo.charData = mock(CharData.class);
    replay.clicked(null, 0, 0);
    Batch batch = mock(Batch.class); panel.act(1); panel.draw(batch, 1);
    verifyNoInteractions(Riiablo.charData, batch, audioHost);
  }

  @Test void repeatedIconFilenameOwnsOneReferencePerSubmittedQuest() {
    rows.get(1).icon = rows.get(0).icon;
    QuestsPanel panel = own(new QuestsPanel());
    assertEquals(2, assets.getReferenceCount(MENU + rows.get(0).icon + ".dc6"));
    panel.dispose(); assertEquals(0, assets.getLoadedAssets());
  }

  @Test void failedEarlyLoadRollsBackOnlySuccessfullySubmittedRequests() {
    Error failure = new AssertionError("questlast load");
    doThrow(failure).when(assets).load(argThat((AssetDescriptor<?> asset) -> asset.fileName.equals(MENU + "questlast.dc6")));
    assertSame(failure, assertThrows(AssertionError.class, QuestsPanel::new));
    assertEquals(0, assets.getLoadedAssets()); assertFalse(assets.contains(SOUND));
  }

  @Test void failedLateConstructionRollsBackReturnedButtonsTabsAndSharedSheets() {
    String path = MENU + "expquesttabs.dc6"; assets.load(path, DC6.class); assets.finishLoading();
    DC6 tabs = assets.get(path, DC6.class); Error failure = new AssertionError("second tab texture");
    when(tabs.getTexture(2)).thenThrow(failure);
    assertSame(failure, assertThrows(AssertionError.class, QuestsPanel::new));
    assets.finishLoading(); assertEquals(1, assets.getLoadedAssets()); assertEquals(1, assets.getReferenceCount(path));
  }

  @Test void failedSpeechStopStillCleansOtherScrollersButtonsAndTexturesWithoutRetry() throws Exception {
    QuestsPanel panel = own(new QuestsPanel());
    for (DialogScroller dialog : dialogs(panel)) dialog.play("replay");
    Error failure = new AssertionError("speech stop"); doThrow(failure).when(playback.first()).stop();
    assertSame(failure, assertThrows(AssertionError.class, panel::dispose));
    assertEquals(0, assets.getLoadedAssets()); panel.dispose();
    for (Audio.Instance instance : playback) verify(instance).stop();
  }

  @Test void failedButtonReleaseStillReleasesEveryReferenceAndDetachesPanel() {
    QuestsPanel panel = own(new QuestsPanel()); assets.finishLoading();
    Group root = new Group(); root.addActor(panel);
    Error failure = new AssertionError("button sound");
    doAnswer(call -> { call.callRealMethod(); throw failure; }).doCallRealMethod().when(assets).unload(SOUND);
    assertSame(failure, assertThrows(AssertionError.class, panel::dispose));
    assertEquals(0, assets.getLoadedAssets()); assertTrue(root.getChildren().isEmpty()); panel.dispose();
  }

  @Test void scrollerCompletesOnlyOnceAndStaticTextNeverCompletes() throws Exception {
    DialogScroller.DialogCompletionListener listener = mock(DialogScroller.DialogCompletionListener.class);
    DialogScroller dialog = own(new DialogScroller(listener)); dialog.setText("static"); dialog.act(10000);
    verifyNoInteractions(listener);
    dialog.play("replay"); dialog.act(10000); dialog.act(10000);
    verify(listener).onCompleted(dialog); verify(playback.first()).stop();
    dialog.play("again"); dialog.act(10000); verify(listener, times(2)).onCompleted(dialog);
  }

  @Test void scrollerReplacementAndMissingOrMalformedSpeechNeverOrphanPreviousAudio() throws Exception {
    DialogScroller dialog = own(new DialogScroller(null)); dialog.play("first");
    Audio.Instance first = playback.peek(); dialog.setText("static"); verify(first).stop();
    dialog.play("second"); Audio.Instance second = playback.peek();
    when(Riiablo.files.speech.get("missing")).thenReturn(null); dialog.play("missing"); verify(second).stop();
    assertNull(get(dialog, "audio"));
    when(Riiablo.string.lookup("speech")).thenReturn("no duration prefix");
    dialog.play("malformed"); assertEquals(3, playback.size); dialog.dispose(); verify(playback.peek()).stop();
  }

  @Test void scrollerUsesOriginalAudioHostAndInstanceFontRatherThanStaticFont() throws Exception {
    DialogScroller first = own(new DialogScroller(null));
    Audio replacement = mock(Audio.class); Riiablo.audio = replacement;
    FontTBL.BitmapFont replacementFont = mock(FontTBL.BitmapFont.class);
    BitmapFont.BitmapFontData data = font.getData();
    Array<TextureRegion> regions = font.getRegions();
    set(BitmapFont.class, replacementFont, "data", data);
    set(BitmapFont.class, replacementFont, "regions", font.getRegions());
    when(replacementFont.getData()).thenReturn(data); when(replacementFont.getColor()).thenReturn(Color.WHITE);
    when(replacementFont.getRegions()).thenReturn(regions); when(replacementFont.getLineHeight()).thenReturn(16f);
    when(replacementFont.newFontCache()).thenAnswer(call -> new BitmapFontCache(replacementFont));
    Riiablo.fonts.fontformal11 = replacementFont;
    DialogScroller second = own(new DialogScroller(null));
    assertSame(font, ((TextArea) get(first, "textArea")).getStyle().font);
    assertSame(Riiablo.fonts.fontformal11, ((TextArea) get(second, "textArea")).getStyle().font);
    first.play("replay"); first.dispose(); verify(playback.first()).stop(); verifyNoInteractions(replacement);
  }

  private void replay(QuestsPanel panel) { lastClick(panel.btnPlayQuest).clicked(null, 0, 0); }
  private void select(QuestsPanel panel, int tab) throws Exception {
    Object selected = ((Object[]) get(panel, "tabs"))[tab];
    Actor icons = (Actor) get(selected, "questIcons"); lastClick(((Group) icons).getChildren().first()).clicked(null, 0, 0);
  }
  private Array<DialogScroller> dialogs(QuestsPanel panel) throws Exception {
    Array<DialogScroller> result = new Array<>();
    for (Object tab : (Object[]) get(panel, "tabs")) result.add((DialogScroller) get(tab, "questDialog")); return result;
  }
  private Array<String> assetPaths() {
    Array<String> paths = Array.with(MENU + "questbackground.dc6", MENU + "questlast.dc6", MENU + "expquesttabs.dc6",
        MENU + "questsockets.dc6", MENU + "questdone.dc6", "data/global/ui/PANEL/buysellbtn.DC6");
    for (Quests.Entry row : rows) paths.add(MENU + row.icon + ".dc6"); return paths;
  }
  private int countButtons(Array<Actor> actors) { int count = 0; for (Actor actor : actors) if (actor instanceof Button) count++; return count; }
  private <T extends Disposable> T own(T resource) { resources.add(resource); return resource; }
  private static void collect(Actor actor, Array<Actor> result) {
    result.add(actor); if (actor instanceof Group) for (Actor child : ((Group) actor).getChildren()) collect(child, result);
  }
  private static ClickListener lastClick(Actor actor) {
    ClickListener result = null; for (EventListener listener : actor.getListeners()) if (listener instanceof ClickListener) result = (ClickListener) listener;
    return result;
  }
  private static Object get(Object object, String name) throws Exception {
    Class<?> type = object.getClass();
    while (type != null) {
      try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
      catch (NoSuchFieldException missing) { type = type.getSuperclass(); }
    }
    throw new NoSuchFieldException(name);
  }
  private static void set(Class<?> type, Object object, String name, Object value) throws Exception {
    Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(object, value);
  }
}
