package com.riiablo.screen.panel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
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
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.DC6;
import com.riiablo.codec.FontTBL;
import com.riiablo.codec.StringTBLs;
import com.riiablo.codec.excel.Skills;
import com.riiablo.codec.excel.SkillDesc;
import com.riiablo.save.CharData;
import com.riiablo.save.ItemData;
import com.riiablo.widget.Button;
import com.riiablo.widget.HotkeyButton;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Real widgets and shared reference counts, without MPQ or a GL context. */
class SpellsPanelResourceLifecycleTest {
  private static final String SOUND = "data/global/sfx/cursor/button.wav";
  private static final String ICON = "data/global/ui/SPELLS/Skillicon.DC6";
  private final Map<Field, Object> aliases = new HashMap<>();
  private final Array<Disposable> resources = new Array<>();
  private Application previousApp;
  private com.badlogic.gdx.Files previousFiles;
  private AssetManager assets;
  private CharData character;
  private ItemData items;
  private Skills skills;
  private SkillDesc descriptions;

  @BeforeEach void setup() throws Exception {
    previousApp = Gdx.app; previousFiles = Gdx.files;
    Gdx.app = mock(Application.class); Gdx.files = mock(com.badlogic.gdx.Files.class);
    for (Field field : Riiablo.class.getFields()) {
      if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
        aliases.put(field, field.get(null));
      }
    }
    Riiablo.colors = new Colors(); Riiablo.game = null; Riiablo.engine = null;
    Texture texture = mock(Texture.class);
    when(texture.getWidth()).thenReturn(32); when(texture.getHeight()).thenReturn(32);
    TextureRegion region = new TextureRegion(texture);
    Riiablo.textures = mock(Textures.class); set(Textures.class, Riiablo.textures, "white", texture);
    Riiablo.fonts = mock(Fonts.class);
    FontTBL.BitmapFont font = mock(FontTBL.BitmapFont.class);
    BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData(); data.lineHeight = 16;
    set(BitmapFont.class, font, "data", data); set(BitmapFont.class, font, "regions", Array.with(region));
    when(font.getData()).thenReturn(data); when(font.getColor()).thenReturn(Color.WHITE);
    when(font.getRegions()).thenReturn(Array.with(region)); when(font.getLineHeight()).thenReturn(16f);
    when(font.newFontCache()).thenAnswer(call -> new BitmapFontCache(font));
    Riiablo.fonts.font16 = font; Riiablo.fonts.fontformal10 = font;
    Riiablo.string = mock(StringTBLs.class);
    when(Riiablo.string.lookup(anyString())).thenReturn("skill");
    skills = mock(Skills.class); descriptions = mock(SkillDesc.class);
    Riiablo.files = mock(com.riiablo.Files.class);
    set(com.riiablo.Files.class, Riiablo.files, "skills", skills);
    set(com.riiablo.Files.class, Riiablo.files, "skilldesc", descriptions);
    for (int id = CharacterClass.SORCERESS.firstSpell; id < CharacterClass.SORCERESS.lastSpell; id++) {
      Skills.Entry skill = new Skills.Entry(); skill.Id = id; skill.charclass = "sor";
      skill.skilldesc = "skill" + id; skill.leftskill = true;
      SkillDesc.Entry desc = new SkillDesc.Entry(); desc.SkillPage = 1; desc.SkillRow = 1;
      desc.SkillColumn = 1; desc.str_long = "long"; desc.str_name = "name";
      desc.descline = desc.dsc2line = desc.dsc3line = new int[0];
      when(skills.get(id)).thenReturn(skill); when(descriptions.get(skill.skilldesc)).thenReturn(desc);
    }
    character = mock(CharData.class); items = mock(ItemData.class);
    set(CharData.class, character, "skillListeners", new Array<CharData.SkillListener>());
    set(ItemData.class, items, "alternateListeners", new Array<ItemData.AlternateListener>());
    doCallRealMethod().when(character).addSkillListener(any());
    doCallRealMethod().when(character).removeSkillListener(any());
    doCallRealMethod().when(items).addAlternateListener(any());
    doCallRealMethod().when(items).removeAlternateListener(any());
    character.classId = CharacterClass.SORCERESS; Riiablo.charData = character;
    when(character.getItems()).thenReturn(items); when(character.getStats()).thenReturn(mock(Attributes.class));
    when(character.getHotkey(anyInt(), anyInt())).thenReturn(-1);
    assets = spy(new AssetManager(FileHandle::new, false)); Riiablo.assets = assets;
    assets.setLoader(DC6.class, new SynchronousAssetLoader<DC6, AssetLoaderParameters<DC6>>(FileHandle::new) {
      @Override public DC6 load(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<DC6> params) {
        DC6 sheet = mock(DC6.class); when(sheet.getTexture(anyInt())).thenReturn(region);
        when(sheet.getTexture()).thenReturn(region);
        when(sheet.getNumPages()).thenReturn(100); return sheet;
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

  @ParameterizedTest @ValueSource(booleans = {false, true})
  void sameCharacterCannotRouteSavedControlsIntoAnotherGameOrWorld(boolean quick) {
    com.riiablo.screen.GameScreen game = mock(com.riiablo.screen.GameScreen.class);
    Riiablo.game = game;
    Actor panel = own(quick ? new SpellsQuickPanel(game, null, false, null) : new SpellsPanel(game));
    if (quick) rebuild((SpellsQuickPanel) panel);
    Array<Actor> actors = PanelSessionAssertions.snapshot(panel);
    Riiablo.game = mock(com.riiablo.screen.GameScreen.class);
    PanelSessionAssertions.assertInactive(panel, actors);
    verifyNoInteractions(Riiablo.game);
    Riiablo.game = game; Riiablo.engine = mock(com.artemis.World.class);
    PanelSessionAssertions.assertInactive(panel, actors);
    verifyNoInteractions(Riiablo.engine);
    ((Disposable) panel).dispose();
    PanelSessionAssertions.assertInactive(panel, actors);
  }

  private Actor overlay(String kind, com.riiablo.screen.GameScreen game) throws Exception {
    Riiablo.fonts.fontformal12 = Riiablo.fonts.font16;
    Riiablo.fonts.ReallyTheLastSucker = Riiablo.fonts.font16;
    if (kind.equals("help")) return own(new HelpPanel(game));
    if (kind.equals("character")) {
      when(character.getStats()).thenReturn(mock(Attributes.class, RETURNS_DEEP_STUBS));
      try (org.mockito.MockedConstruction<com.riiablo.graphics.BorderedPaletteIndexedDrawable> ignored =
          mockConstruction(com.riiablo.graphics.BorderedPaletteIndexedDrawable.class)) {
        return own(new CharacterPanel(game));
      }
    }
    com.riiablo.codec.excel.Levels table = mock(com.riiablo.codec.excel.Levels.class);
    com.riiablo.codec.excel.Levels.Entry row = new com.riiablo.codec.excel.Levels.Entry();
    row.Act = 0; row.Waypoint = 0; row.LevelName = "town";
    when(table.iterator()).thenAnswer(call -> Array.with(row).iterator());
    set(com.riiablo.Files.class, Riiablo.files, "Levels", table);
    return own(new WaygatePanel(game, character));
  }

  @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({
      "help,game", "help,world", "character,game", "character,world", "waygate,game", "waygate,world"})
  void remainingOverlaysRetireSavedControlsWithoutChangingTheCharacter(String kind, String changed) throws Exception {
    com.riiablo.screen.GameScreen game = mock(com.riiablo.screen.GameScreen.class); Riiablo.game = game;
    Actor panel = overlay(kind, game);
    Array<Actor> actors = PanelSessionAssertions.snapshot(panel);
    if (changed.equals("game")) Riiablo.game = mock(com.riiablo.screen.GameScreen.class);
    else Riiablo.engine = mock(com.artemis.World.class);
    PanelSessionAssertions.assertInactive(panel, actors);
    if (changed.equals("game")) verifyNoInteractions(Riiablo.game);
    else verifyNoInteractions(Riiablo.engine);
    ((Disposable) panel).dispose(); ((Disposable) panel).dispose();
    PanelSessionAssertions.assertInactive(panel, actors);
    assertEquals(0, assets.getLoadedAssets());
  }

  @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({
      "help,1", "help,2", "help,3", "help,4", "waygate,1", "waygate,2", "waygate,3", "waygate,4"})
  void overlayConstructionFailureReturnsEverySubmittedReference(String kind, int failAt) throws Exception {
    RuntimeException failure = new RuntimeException("injected overlay asset failure");
    int[] loaded = {0};
    doAnswer(call -> {
      if (++loaded[0] == failAt) throw failure;
      return call.callRealMethod();
    }).when(assets).finishLoadingAsset(any(AssetDescriptor.class));
    assertSame(failure, assertThrows(RuntimeException.class, () -> overlay(kind, null)));
    assertEquals(0, assets.getLoadedAssets());
    assertEquals(0, assets.getQueuedAssets());
  }

  @ParameterizedTest @ValueSource(strings = {"help", "character", "waygate"})
  void detachedOverlaySubtreesAndExternalAssetReferencesSurviveOnlyAsIntended(String kind) throws Exception {
    assets.load(SOUND, Sound.class); assets.finishLoading();
    Actor panel = overlay(kind, null);
    Array<Actor> actors = PanelSessionAssertions.snapshot(panel);
    for (Actor child : ((Group) panel).getChildren().toArray(Actor.class)) child.remove();
    ((Disposable) panel).dispose(); ((Disposable) panel).dispose();
    assets.finishLoading();
    assertEquals(1, assets.getLoadedAssets()); assertEquals(1, assets.getReferenceCount(SOUND));
    for (Actor actor : actors) {
      assertNull(actor.getParent()); assertTrue(actor.getListeners().isEmpty());
      assertTrue(actor.getCaptureListeners().isEmpty()); assertTrue(actor.getActions().isEmpty());
    }
  }

  @ParameterizedTest @ValueSource(booleans = {false, true})
  void originalManagerAndSharedReferencesSurviveGlobalReplacement(boolean quick) {
    assets.load(ICON, DC6.class); assets.load(SOUND, Sound.class); assets.finishLoading();
    Actor panel = own(quick ? new SpellsQuickPanel(null, false) : new SpellsPanel());
    if (quick) rebuild((SpellsQuickPanel) panel);
    Array<Actor> children = new Array<>(); collect(panel, children);
    Group root = new Group(); root.addActor(panel);
    AssetManager replacement = mock(AssetManager.class); Riiablo.assets = replacement;
    ((Disposable) panel).dispose(); ((Disposable) panel).dispose();
    assets.finishLoading();
    assertEquals(2, assets.getLoadedAssets());
    assertEquals(1, assets.getReferenceCount(ICON)); assertEquals(1, assets.getReferenceCount(SOUND));
    verifyNoInteractions(replacement); assertEquals(0, root.getChildren().size);
    for (Actor child : children) assertEquals(0, child.getListeners().size);
    verify(character).removeSkillListener((CharData.SkillListener) panel);
  }

  @Test void quickChooserUnregistersExactOwnerListenersAndRetiresSavedCallbacks() throws Exception {
    CharData.SkillListener otherSkill = mock(CharData.SkillListener.class);
    ItemData.AlternateListener otherAlternate = mock(ItemData.AlternateListener.class);
    character.addSkillListener(otherSkill); items.addAlternateListener(otherAlternate);
    HotkeyButton observer = mock(HotkeyButton.class);
    com.badlogic.gdx.net.Socket socket = mock(com.badlogic.gdx.net.Socket.class);
    SpellsQuickPanel panel = own(new SpellsQuickPanel(observer, false, socket)); rebuild(panel);
    ItemData.AlternateListener alternate = (ItemData.AlternateListener) get(panel, "alternateListener");
    ClickListener oldClick = lastClick(panel.buttons.values().next());
    com.riiablo.key.MappedKeyStateAdapter key = panel.mappedKeyListener;
    panel.dispose(); panel.dispose();
    Riiablo.charData = mock(CharData.class);
    oldClick.clicked(null, 0, 0); key.onPressed(Keys.Skill[0], 0);
    alternate.onAlternated(items, 0, null, null);
    panel.onChanged(character, new IntIntMap(), new Array<StatRef>());
    panel.setObserver(observer);
    panel.setVisible(true); assertFalse(panel.isVisible());
    assertNull(panel.observer); assertEquals(0, panel.buttons.size);
    verify(items).removeAlternateListener(alternate);
    verify(character).removeSkillListener(panel); verifyNoInteractions(observer, Riiablo.charData);
    assertEquals(Array.with(otherSkill), get(character, "skillListeners"));
    assertEquals(Array.with(otherAlternate), get(items, "alternateListeners"));
    verifyNoInteractions(socket);
    for (com.riiablo.key.MappedKey mapping : Keys.Skill) assertFalse(mapping.containsStateListener(key));
    Batch batch = mock(Batch.class); panel.act(1); panel.draw(batch, 1); verifyNoInteractions(batch);
  }

  @Test void activeOldChooserCannotSelectOrRebuildAgainstReplacedCharacter() throws Exception {
    HotkeyButton observer = mock(HotkeyButton.class);
    SpellsQuickPanel panel = own(new SpellsQuickPanel(observer, false)); rebuild(panel);
    ClickListener click = lastClick(panel.buttons.values().next());
    ItemData.AlternateListener alternate = (ItemData.AlternateListener) get(panel, "alternateListener");
    Riiablo.charData = mock(CharData.class);
    click.clicked(null, 0, 0); panel.mappedKeyListener.onPressed(Keys.Skill[0], 0);
    alternate.onAlternated(items, 0, null, null);
    panel.onChanged(Riiablo.charData, new IntIntMap(), new Array<StatRef>());
    assertEquals(1, panel.buttons.size);
    verifyNoInteractions(observer, Riiablo.charData);
    verify(character, never()).setAction(anyInt(), anyInt());
  }

  @Test void rebuildRetiresPreviousButtonCallbacksAndBalancesSoundReferences() {
    SpellsQuickPanel panel = own(new SpellsQuickPanel(null, false)); rebuild(panel);
    assets.finishLoading(); assertEquals(1, assets.getReferenceCount(SOUND));
    HotkeyButton old = panel.buttons.values().next(); ClickListener callback = lastClick(old);
    for (int i = 0; i < 3; i++) {
      rebuild(panel); assets.finishLoading(); assertEquals(1, assets.getReferenceCount(SOUND));
    }
    assertNull(old.getParent()); assertEquals(0, old.getListeners().size);
    callback.clicked(null, 0, 0); verify(character, never()).setAction(anyInt(), anyInt());
    lastClick(panel.buttons.values().next()).clicked(null, 0, 0);
    verify(character).setAction(com.badlogic.gdx.Input.Buttons.RIGHT, CharacterClass.SORCERESS.firstSpell);
  }

  @Test void failedRebuildCleansReturnedButtonsAndCanRebuildAgain() {
    SpellsQuickPanel panel = own(new SpellsQuickPanel(null, false));
    Error failure = new AssertionError("hotkey lookup");
    doThrow(failure).when(character).getHotkey(anyInt(), anyInt());
    assertSame(failure, assertThrows(AssertionError.class, () -> rebuild(panel)));
    assets.finishLoading(); assertFalse(assets.contains(SOUND));
    assertEquals(0, panel.buttons.size); for (com.badlogic.gdx.scenes.scene2d.ui.Table row : panel.tables) assertEquals(0, row.getChildren().size);
    doReturn(-1).when(character).getHotkey(anyInt(), anyInt()); rebuild(panel);
    assertEquals(1, panel.buttons.size);
  }

  @Test void duplicateChargedAndLearnedSkillsRetireBothActorsNotJustTheIndexEntry() {
    SpellsQuickPanel panel = own(new SpellsQuickPanel(null, false));
    int id = CharacterClass.SORCERESS.firstSpell;
    StatRef charge = mock(StatRef.class);
    when(charge.param0()).thenReturn(1); when(charge.param1()).thenReturn(id);
    IntIntMap learned = new IntIntMap(); learned.put(id, 1);
    panel.onChanged(character, learned, Array.with(charge));
    Array<Actor> actors = new Array<>(); collect(panel, actors);
    int count = 0; for (Actor actor : actors) if (actor instanceof HotkeyButton) count++;
    assertEquals(2, count); assertEquals(1, panel.buttons.size);
    assets.finishLoading(); assertEquals(2, assets.getReferenceCount(SOUND));
    panel.dispose(); assertEquals(0, assets.getLoadedAssets());
    for (Actor actor : actors) if (actor instanceof HotkeyButton) {
      assertNull(actor.getParent()); assertEquals(0, actor.getListeners().size);
    }
  }

  @Test void keyboardSelectionWorksWithoutAnAndroidObserver() {
    when(character.getHotkey(anyInt(), anyInt())).thenReturn(0);
    SpellsQuickPanel panel = own(new SpellsQuickPanel(null, true)); rebuild(panel);
    panel.mappedKeyListener.onPressed(Keys.Skill[0], 0);
    verify(character).setAction(com.badlogic.gdx.Input.Buttons.LEFT, CharacterClass.SORCERESS.firstSpell);
  }

  @Test void childClearFailureStillRetiresOtherChildrenRowsAndAssets() {
    SpellsQuickPanel panel = own(new SpellsQuickPanel(null, false)); rebuild(panel);
    Button failed = spy(new Button(new Button.ButtonStyle()));
    Button survivor = new Button(new Button.ButtonStyle());
    panel.tables[0].add(failed); panel.tables[0].add(survivor);
    Error failure = new AssertionError("child clear");
    doAnswer(call -> { call.callRealMethod(); throw failure; }).when(failed).clearListeners();
    assertSame(failure, assertThrows(AssertionError.class, panel::dispose));
    assertEquals(0, assets.getLoadedAssets()); assertEquals(0, survivor.getListeners().size);
    assertNull(survivor.getParent());
    for (com.badlogic.gdx.scenes.scene2d.ui.Table row : panel.tables) assertEquals(0, row.getChildren().size);
  }

  @ParameterizedTest @ValueSource(booleans = {false, true})
  void constructorFailureRollsBackSubmittedAssetsAndReturnedButtons(boolean quick) {
    Error failure = new AssertionError("constructor");
    if (quick) {
      doThrow(failure).when(items).addAlternateListener(any());
      assertSame(failure, assertThrows(AssertionError.class, () -> new SpellsQuickPanel(null, false)));
      verify(character).removeSkillListener(any()); verify(items).removeAlternateListener(any());
    } else {
      doThrow(failure).when(skills).get(CharacterClass.SORCERESS.firstSpell + 1);
      assertSame(failure, assertThrows(AssertionError.class, SpellsPanel::new));
    }
    assets.finishLoading(); assertEquals(0, assets.getLoadedAssets());
  }

  @ParameterizedTest @ValueSource(booleans = {false, true})
  void releaseFailureDoesNotSkipOtherResourcesAndIsNotRetried(boolean quick) {
    Actor panel = own(quick ? new SpellsQuickPanel(null, false) : new SpellsPanel());
    if (quick) rebuild((SpellsQuickPanel) panel);
    assets.finishLoading();
    Error failure = new AssertionError("sound release");
    doAnswer(call -> { call.callRealMethod(); throw failure; }).doCallRealMethod().when(assets).unload(SOUND);
    assertSame(failure, assertThrows(AssertionError.class, () -> ((Disposable) panel).dispose()));
    assertEquals(0, assets.getLoadedAssets()); ((Disposable) panel).dispose();
    assertEquals(0, panel.getListeners().size);
  }

  @Test void spellBookIgnoresForeignAndLateNotificationsClicksAndDraw() {
    SpellsPanel panel = own(new SpellsPanel());
    Array<Actor> actors = new Array<>(); collect(panel, actors);
    ClickListener callback = null;
    for (Actor actor : actors) if (actor instanceof Button) callback = lastClick(actor);
    panel.onChanged(mock(CharData.class), new IntIntMap(), new Array<StatRef>());
    panel.dispose(); Riiablo.charData = mock(CharData.class);
    panel.onChanged(character, null, null); callback.clicked(null, 0, 0);
    panel.setVisible(true); assertFalse(panel.isVisible());
    Batch batch = mock(Batch.class); panel.act(1); panel.draw(batch, 1); verifyNoInteractions(batch, Riiablo.charData);
  }

  private void rebuild(SpellsQuickPanel panel) {
    IntIntMap learned = new IntIntMap(); learned.put(CharacterClass.SORCERESS.firstSpell, 1);
    panel.onChanged(character, learned, new Array<StatRef>());
  }

  @Test void rebuiltSkillsRetireNestedTreesDetailsAndBothDrawOverloadsWhilePanelRemainsLive() throws Exception {
    SpellsQuickPanel panel = own(new SpellsQuickPanel(null, false)); rebuild(panel);
    HotkeyButton old = panel.buttons.values().next(); old.map(Keys.Skill[0]);
    Array<Actor> actors = new Array<>(); collect(old, actors);
    Actor details = (Actor) get(old, "details"); collect(details, actors);
    for (Actor actor : actors) { actor.addCaptureListener(new InputListener()); actor.addAction(new Action() { public boolean act(float dt) { fail("old action"); return false; } }); }
    Array<ClickListener> saved = new Array<>(); for (EventListener listener : old.getListeners()) if (listener instanceof ClickListener) saved.add((ClickListener) listener);
    rebuild(panel); assertTrue(panel.buttons.size > 0); retiredSkill(old, saved);
    for (Actor actor : actors) {
      assertNull(actor.getParent()); assertEquals(0, actor.getListeners().size); assertEquals(0, actor.getCaptureListeners().size); assertEquals(0, actor.getActions().size);
    }
    assertNull(old.getMapping()); assertNull(get(old, "chargedSkill")); assertNull(panel.keyMappings.get(Keys.Skill[0]));
    verify(character, never()).setAction(anyInt(), anyInt());
  }

  @Test void duplicateSkillIdsRetireBothChargedAndLearnedObjectsAndClearChargeBorrow() throws Exception {
    SpellsQuickPanel panel = own(new SpellsQuickPanel(null, false)); int id = CharacterClass.SORCERESS.firstSpell;
    StatRef charges = mock(StatRef.class); when(charges.param0()).thenReturn(1); when(charges.param1()).thenReturn(id);
    IntIntMap learned = new IntIntMap(); learned.put(id, 1); panel.onChanged(character, learned, Array.with(charges));
    Array<Actor> previous = new Array<>(); collect(panel, previous); clearInvocations(charges);
    panel.onChanged(character, new IntIntMap(), new Array<StatRef>());
    int count = 0; for (Actor actor : previous) if (actor instanceof HotkeyButton) {
      count++; retiredSkill((HotkeyButton) actor, new Array<ClickListener>()); assertNull(get(actor, "chargedSkill"));
    }
    assertEquals(2, count); verifyNoInteractions(charges); assertFalse(assets.contains(SOUND));
  }

  @Test void failedTableAttachmentStillReleasesTheReturnedUnattachedButtonAndCanRetry() {
    SpellsQuickPanel panel = own(new SpellsQuickPanel(null, false));
    com.badlogic.gdx.scenes.scene2d.ui.Table failed = spy(panel.tables[0]); panel.tables[0] = failed;
    Array<HotkeyButton> returned = new Array<>(); Error failure = new AssertionError("table attach");
    doAnswer(call -> { returned.add(call.getArgument(0)); throw failure; }).when(failed).add(any(Actor.class));
    assertSame(failure, assertThrows(AssertionError.class, () -> rebuild(panel))); assertEquals(1, returned.size);
    retiredSkill(returned.first(), new Array<ClickListener>()); assets.finishLoading(); assertFalse(assets.contains(SOUND));
    assertEquals(0, panel.buttons.size); assertEquals(0, panel.keyMappings.size);
    doCallRealMethod().when(failed).add(any(Actor.class)); rebuild(panel); assertEquals(1, panel.buttons.size);
  }

  @Test void removedButStillOwnedSkillButtonsAreRetiredAtTheNextRebuild() {
    SpellsQuickPanel panel = own(new SpellsQuickPanel(null, false)); rebuild(panel); HotkeyButton detached = panel.buttons.values().next();
    detached.remove(); rebuild(panel); retiredSkill(detached, new Array<ClickListener>());
    assets.finishLoading(); assertEquals(1, assets.getReferenceCount(SOUND));
  }

  @Test void sortingWithinOneGenerationKeepsAllReattachedButtonsUsableAndOrdered() {
    int first = CharacterClass.SORCERESS.firstSpell, second = first + 1;
    IntIntMap learned = new IntIntMap(); learned.put(first, 1); learned.put(second, 1);
    SpellsQuickPanel left = own(new SpellsQuickPanel(null, true)), right = own(new SpellsQuickPanel(null, false));
    left.onChanged(character, learned, new Array<StatRef>()); right.onChanged(character, learned, new Array<StatRef>());
    assertEquals(first, ((HotkeyButton) left.tables[0].getChild(0)).getSkill());
    assertEquals(second, ((HotkeyButton) right.tables[0].getChild(0)).getSkill());
    for (SpellsQuickPanel panel : new SpellsQuickPanel[] {left, right}) for (HotkeyButton button : panel.buttons.values()) {
      assertNotNull(button.getParent()); assertFalse(button.refreshDisabled()); assertFalse(button.isDisabled());
    }
    lastClick(left.buttons.get(first)).clicked(new InputEvent(), 0, 0);
    verify(character).setAction(com.badlogic.gdx.Input.Buttons.LEFT, first);
  }

  @Test void soundCleanupFailureStillRetiresOldGenerationAndAllowsLaterRetry() {
    SpellsQuickPanel panel = own(new SpellsQuickPanel(null, false)); rebuild(panel); HotkeyButton old = panel.buttons.values().next(); assets.finishLoading();
    Error failure = new AssertionError("sound release"); doAnswer(call -> { call.callRealMethod(); throw failure; }).when(assets).unload(SOUND);
    assertSame(failure, assertThrows(AssertionError.class, () -> rebuild(panel))); retiredSkill(old, new Array<ClickListener>());
    assertEquals(0, panel.buttons.size); assertEquals(0, panel.keyMappings.size);
    doCallRealMethod().when(assets).unload(SOUND); rebuild(panel); assertEquals(1, panel.buttons.size);
  }

  @Test void oldGenerationCannotBeCopiedIntoTheCurrentObserver() {
    SpellsQuickPanel panel = own(new SpellsQuickPanel(null, false)); rebuild(panel); HotkeyButton old = panel.buttons.values().next();
    rebuild(panel); HotkeyButton current = panel.buttons.values().next(); current.copy(old); assertFalse(current.isDisabled());
    assertNull(old.getMapping()); verify(character, never()).setAction(anyInt(), anyInt());
  }

  private void retiredSkill(HotkeyButton button, Array<ClickListener> saved) {
    Batch batch = mock(Batch.class); com.riiablo.graphics.PaletteIndexedBatch palette = mock(com.riiablo.graphics.PaletteIndexedBatch.class);
    for (ClickListener click : saved) {
      click.clicked(null, 0, 0);
      if (click.getClass().getName().startsWith("com.riiablo.widget.Button$")) {
        InputEvent event = new InputEvent(); event.setButton(com.badlogic.gdx.Input.Buttons.LEFT); click.touchDown(event, 0, 0, 0, com.badlogic.gdx.Input.Buttons.LEFT);
      }
    }
    assertTrue(button.refreshDisabled()); assertTrue(button.isDisabled()); assertEquals(Touchable.disabled, button.getTouchable());
    button.map(Keys.Skill[1]); button.copy(button); button.draw(batch, 1); button.draw(palette, 1); button.act(1);
    verifyNoInteractions(batch, palette);
  }
  private <T extends Actor & Disposable> T own(T resource) { resources.add(resource); return resource; }
  private static void collect(Actor actor, Array<Actor> actors) {
    actors.add(actor); if (actor instanceof Group) for (Actor child : ((Group) actor).getChildren()) collect(child, actors);
  }
  private static ClickListener lastClick(Actor actor) {
    ClickListener result = null;
    for (EventListener listener : actor.getListeners()) if (listener instanceof ClickListener) result = (ClickListener) listener;
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
