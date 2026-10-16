package com.riiablo.screen.panel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.assets.*;
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.*;
import com.riiablo.*;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.DC6;
import com.riiablo.codec.FontTBL;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.UnitLifecycle;
import com.riiablo.item.Item;
import com.riiablo.mpq.MPQFileHandleResolver;
import com.riiablo.save.CharData;
import com.riiablo.save.ItemController;
import com.riiablo.screen.GameScreen;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.mockito.MockedConstruction;

/** Real HUD/World subscriptions and manager counts; intercept only private GL uploads. */
class MercenaryHudLifecycleTest extends RiiabloTest {
  private static final String ROOT = "data/global/ui/HIREABLES/";
  private static final String[] ICONS = {"rogueicon.dc6", "act2hireableicon.dc6", "act3hireableicon.dc6",
      "barbhirable_icon.dc6", "Raven.dc6", "Wolf.dc6", "OakSage.dc6", "Vines.dc6", "Bear.dc6",
      "valkarieicon.dc6", "amazonicon.dc6", "skeletonicon.dc6", "skeletonmageicon.dc6", "golemicon.dc6",
      "revivedicon.dc6", "ShadowAssassin.dc6", "sorceressicon.dc6"};
  private final Map<Field, Object> aliases = new HashMap<>();
  private final Array<Disposable> resources = new Array<>();
  private final Array<World> worlds = new Array<>();
  private AssetManager assets;
  private ItemController controller;
  private FontTBL.BitmapFont font;
  private MockedConstruction<Texture> uploads;
  private Object previousNames;

  @BeforeEach void fixture() throws Exception {
    for (Field field : Riiablo.class.getFields())
      if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) aliases.put(field, field.get(null));
    previousNames = get(MercenaryHud.class, null, "petTypeNameKeys");
    Riiablo.colors = new Colors(); Riiablo.game = null; Riiablo.engine = null;
    Riiablo.charData = CharData.obtain().clear().set(Riiablo.NORMAL, false, "HudOwner", Riiablo.AMAZON);
    Riiablo.cursor = mock(Cursor.class); controller = mock(ItemController.class); Riiablo.mpqs = mock(MPQFileHandleResolver.class);
    Texture texture = mock(Texture.class); when(texture.getWidth()).thenReturn(46); when(texture.getHeight()).thenReturn(41);
    TextureRegion region = new TextureRegion(texture); Riiablo.textures = mock(Textures.class); set(Riiablo.textures, "white", texture);
    Riiablo.fonts = mock(Fonts.class); font = mock(FontTBL.BitmapFont.class);
    BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData(); data.lineHeight = 16; data.capHeight = 12;
    for (int c = 32; c < 127; c++) {
      BitmapFont.Glyph glyph = new BitmapFont.Glyph(); glyph.id = c; glyph.width = 8; glyph.height = 12; glyph.xadvance = 8;
      data.setGlyph(c, glyph);
    }
    set(BitmapFont.class, font, "data", data); set(BitmapFont.class, font, "regions", Array.with(region));
    when(font.getData()).thenReturn(data); when(font.getColor()).thenReturn(Color.WHITE);
    when(font.getRegions()).thenReturn(Array.with(region)); when(font.getLineHeight()).thenReturn(16f);
    when(font.newFontCache()).thenAnswer(call -> new BitmapFontCache(font)); Riiablo.fonts.font16 = font;
    Riiablo.bundle = mock(I18NBundle.class, call -> "fixture");
    assets = spy(new AssetManager(FileHandle::new, false)); Riiablo.assets = assets;
    assets.setLoader(DC6.class, new SynchronousAssetLoader<DC6, AssetLoaderParameters<DC6>>(FileHandle::new) {
      @Override public DC6 load(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<DC6> params) {
        DC6 dc = mock(DC6.class); when(dc.getTexture()).thenReturn(region); when(dc.getTexture(anyInt())).thenReturn(region); return dc;
      }
      @Override public Array<AssetDescriptor> getDependencies(String name, FileHandle file, AssetLoaderParameters<DC6> params) { return null; }
    });
    uploads = mockConstruction(Texture.class);
  }

  @AfterEach void restore() throws Exception {
    try {
      for (Disposable resource : resources) resource.dispose();
      for (World world : worlds) world.dispose();
      assets.dispose();
    } finally {
      uploads.close(); set(MercenaryHud.class, null, "petTypeNameKeys", previousNames);
      for (Map.Entry<Field, Object> alias : aliases.entrySet()) alias.getKey().set(null, alias.getValue());
    }
  }

  @Test void independentExitReturnsSeventeenIconsAndOnePrivateTextureExactlyOnce() throws Exception {
    for (String icon : ICONS) assets.load(ROOT + icon, DC6.class); assets.finishLoading();
    MercenaryHud hud = hud(); assets.finishLoading(); Array<Actor> actors = actors(hud); assertEquals(31, actors.size);
    Group parent = new Group(); parent.addActor(hud);
    for (Actor actor : actors) { actor.addCaptureListener(new InputListener()); actor.addAction(new Action() { public boolean act(float dt) { return false; } }); }
    AssetManager replacement = mock(AssetManager.class); Riiablo.assets = replacement; Riiablo.charData = mock(CharData.class);
    hud.dispose(); hud.dispose(); retired(actors); assertTrue(parent.getChildren().isEmpty()); assertFalse(hud.isVisible());
    assertEquals(17, assets.getLoadedAssets()); for (String icon : ICONS) assertEquals(1, assets.getReferenceCount(ROOT + icon));
    assertEquals(1, uploads.constructed().size()); verify(uploads.constructed().get(0)).dispose(); verify(font, never()).dispose(); verifyNoInteractions(replacement);
    assertNull(get(hud, "itemController")); assertNull(get(hud, "world")); assertNull(get(hud, "owner"));
    assertTrue(((List<?>) get(hud, "slots")).isEmpty()); assertTrue(((List<?>) get(hud, "slotNames")).isEmpty());
    assertTrue(((Map<?, ?>) get(hud, "summonIcons")).isEmpty()); for (Object icon : (Object[]) get(hud, "mercenaryIcons")) assertNull(icon);
  }

  @Test void queuedExitStillPreservesAnExternalOwnersSharedIcon() {
    assets.load(ROOT + ICONS[0], DC6.class); assets.finishLoading(); MercenaryHud hud = hud(); hud.dispose();
    assertEquals(1, assets.getLoadedAssets()); assertEquals(1, assets.getReferenceCount(ROOT + ICONS[0]));
  }

  @Test void earlyLoadFailureReturnsOnlySuccessfullySubmittedRequests() {
    assets.load(ROOT + ICONS[0], DC6.class); assets.finishLoading(); Error failure = new AssertionError("second icon");
    doThrow(failure).when(assets).load(argThat((AssetDescriptor<?> d) -> d.fileName.equals(ROOT + ICONS[1])));
    assertSame(failure, assertThrows(AssertionError.class, () -> new MercenaryHud(controller)));
    assertEquals(1, assets.getLoadedAssets()); assertEquals(1, assets.getReferenceCount(ROOT + ICONS[0])); assertTrue(uploads.constructed().isEmpty());
  }

  @Test void lateLabelFailureReleasesAllIconsAndPrivateFill() {
    AtomicInteger labels = new AtomicInteger(); Error failure = new AssertionError("feedback label");
    when(font.newFontCache()).thenAnswer(call -> { if (labels.incrementAndGet() == 30) throw failure; return new BitmapFontCache(font); });
    assertSame(failure, assertThrows(AssertionError.class, () -> new MercenaryHud(controller)));
    assertEquals(0, assets.getLoadedAssets()); verify(uploads.constructed().get(0)).dispose();
  }

  @Test void cleanupFailureStillRetiresTreeAndReleasesEveryOtherReference() throws Exception {
    MercenaryHud hud = hud(); assets.finishLoading(); Array<Actor> actors = actors(hud); Error failure = new AssertionError("first unload");
    doThrow(failure).when(assets).unload(ROOT + ICONS[0]); assertSame(failure, assertThrows(AssertionError.class, hud::dispose));
    retired(actors); assertEquals(1, assets.getLoadedAssets()); verify(uploads.constructed().get(0)).dispose();
    assertNull(get(hud, "feedback")); assertNull(get(hud, "world")); hud.dispose(); verify(assets).unload(ROOT + ICONS[0]);
    doCallRealMethod().when(assets).unload(ROOT + ICONS[0]);
  }

  @Test void cleanupErrorsAreSuppressedOnTheOriginalConstructionFailure() {
    Error original = new AssertionError("label"), release = new AssertionError("texture release");
    when(font.newFontCache()).thenAnswer(call -> { doThrow(release).when(uploads.constructed().get(0)).dispose(); throw original; });
    assertSame(original, assertThrows(AssertionError.class, () -> new MercenaryHud(controller)));
    assertArrayEquals(new Throwable[] {release}, original.getSuppressed()); assertEquals(0, assets.getLoadedAssets());
  }

  @Test void currentPotionChecksAndControllerResultArePreserved() throws Exception {
    MercenaryHud hud = hud(); portrait(hud); when(Riiablo.cursor.getItem()).thenReturn(mock(Item.class)); when(controller.useCursorPotionOnMercenary()).thenReturn(true);
    assertTrue(hud.useCursorPotion()); verify(controller).useCursorPotionOnMercenary();
    set(hud, "life", 50f); assertFalse(hud.useCursorPotion()); verifyNoMoreInteractions(controller);
    set(hud, "life", 25f); when(controller.useCursorPotionOnMercenary()).thenReturn(false); assertFalse(hud.useCursorPotion());
  }

  @Test void noCursorNoControllerAndNoMercenaryDoNotSubmitPotion() throws Exception {
    MercenaryHud hud = hud(); portrait(hud); assertFalse(hud.useCursorPotion());
    when(Riiablo.cursor.getItem()).thenReturn(mock(Item.class)); set(hud, "mercenaryId", -1); assertFalse(hud.useCursorPotion());
    portrait(hud); set(hud, "itemController", null); assertFalse(hud.useCursorPotion()); verifyNoInteractions(controller);
  }

  @Test void currentLeftClickFeedsAndRightClickOpensOriginalInventory() throws Exception {
    MercenaryHud hud = hud(); portrait(hud); GameScreen game = mock(GameScreen.class); game.mercenaryHud = hud;
    game.hirelingPanel = mock(HirelingPanel.class); Riiablo.game = game; when(Riiablo.cursor.getItem()).thenReturn(mock(Item.class));
    ClickListener left = clicks(hud).get(0), right = clicks(hud).get(1); left.clicked(new InputEvent(), 10, 30); verify(controller).useCursorPotionOnMercenary();
    InputEvent event = new InputEvent(); right.clicked(event, 10, 30); verify(game).setLeftPanel(game.hirelingPanel); assertTrue(event.isHandled());
    right.clicked(new InputEvent(), 57, 30); verifyNoMoreInteractions(game);
  }

  @Test void hiddenHudRejectsClicksHoverAndHitTestingWithoutReadingCursor() throws Exception {
    MercenaryHud hud = hud(); portrait(hud); hud.setVisible(false); Array<ClickListener> clicks = clicks(hud);
    invoke(clicks); assertFalse(clicks.first().mouseMoved(null, 10, 30)); assertFalse(hud.useCursorPotion()); assertFalse(hud.containsStagePoint(10, 30));
    verifyNoInteractions(Riiablo.cursor, controller);
  }

  @Test void hoverFeedbackTimeoutAndStageHitGeometryRemainUsable() throws Exception {
    MercenaryHud hud = hud(); portrait(hud); hud.setPosition(8, 16); assertTrue(hud.containsStagePoint(18, 46)); assertFalse(hud.containsStagePoint(65, 46));
    assertTrue(clicks(hud).first().mouseMoved(null, 10, 30)); assertTrue(((Actor) get(hud, "tooltip")).isVisible());
    clicks(hud).first().exit(null, 10, 30, 0, null); assertFalse(((Actor) get(hud, "tooltip")).isVisible());
    hud.showTemporaryMessage("test"); assertTrue(((Actor) get(hud, "feedback")).isVisible()); hud.act(1.6f); assertFalse(((Actor) get(hud, "feedback")).isVisible());
    hud.showTemporaryMessage(null); assertFalse(((Actor) get(hud, "feedback")).isVisible());
  }

  @Test void oldCharacterCallbacksCannotUseAnyReplacementHost() throws Exception {
    MercenaryHud hud = hud(); portrait(hud); Array<ClickListener> saved = clicks(hud);
    Riiablo.charData = mock(CharData.class); Riiablo.game = mock(GameScreen.class); Riiablo.engine = mock(World.class); Riiablo.cursor = mock(Cursor.class);
    Batch batch = mock(Batch.class); invoke(saved); hud.showTemporaryMessage("late"); hud.act(1); hud.draw(batch, 1); hud.drawCompanions(batch, 1);
    assertFalse(hud.useCursorPotion()); assertFalse(hud.containsStagePoint(10, 30)); assertFalse(saved.first().mouseMoved(null, 10, 30));
    verifyNoInteractions(Riiablo.charData, Riiablo.game, Riiablo.engine, Riiablo.cursor, controller, batch);
  }

  @Test void sameCharacterCannotAdoptAReplacementGame() throws Exception {
    MercenaryHud hud = hud(); portrait(hud); Riiablo.game = mock(GameScreen.class); invoke(clicks(hud)); assertFalse(hud.useCursorPotion());
    hud.setVisible(false); hud.setVisible(true); assertFalse(hud.isVisible()); verifyNoInteractions(Riiablo.game, Riiablo.cursor, controller);
  }

  @Test void sameGameCannotAdoptAReplacementCursor() throws Exception {
    MercenaryHud hud = hud(); portrait(hud); Riiablo.cursor = mock(Cursor.class); invoke(clicks(hud)); assertFalse(hud.useCursorPotion()); verifyNoInteractions(Riiablo.cursor, controller);
  }

  @Test void sameGameCannotAdoptAReplacementAssetManager() throws Exception {
    MercenaryHud hud = hud(); portrait(hud); Riiablo.assets = mock(AssetManager.class); Batch batch = mock(Batch.class);
    hud.drawCompanions(batch, 1); invoke(clicks(hud)); verifyNoInteractions(Riiablo.assets, batch, controller, Riiablo.cursor);
  }

  @Test void sameGameCannotAdoptAReplacementMpqResolver() throws Exception {
    MercenaryHud hud = hud(); portrait(hud); Riiablo.mpqs = mock(MPQFileHandleResolver.class); hud.act(1); invoke(clicks(hud));
    verifyNoInteractions(Riiablo.mpqs, controller, Riiablo.cursor);
  }

  @Test void disposedSavedListenersCannotReopenRenderOrTouchFeedback() throws Exception {
    MercenaryHud hud = hud(); portrait(hud); Array<Actor> actors = actors(hud); Array<ClickListener> saved = clicks(hud); hud.dispose();
    invoke(saved); hud.setVisible(true); hud.showTemporaryMessage("late"); hud.act(1); Batch batch = mock(Batch.class); hud.draw(batch, 1); hud.drawCompanions(batch, 1);
    assertFalse(hud.useCursorPotion()); assertFalse(hud.containsStagePoint(10, 30)); assertFalse(hud.isVisible()); retired(actors); verifyNoInteractions(controller, Riiablo.cursor, batch);
  }

  @Test void explicitOwnerCanConstructBeforePublicationAndBindsWorldOnlyOnce() throws Exception {
    GameScreen owner = mock(GameScreen.class); Riiablo.game = mock(GameScreen.class); MercenaryHud hud = own(new MercenaryHud(owner, controller));
    World world = world(); hud.bindWorld(world); hud.bindWorld(world); assertThrows(NullPointerException.class, () -> hud.bindWorld(null));
    World replacement = mock(World.class); assertThrows(IllegalStateException.class, () -> hud.bindWorld(replacement));
    Riiablo.game = owner; portrait(hud); when(Riiablo.cursor.getItem()).thenReturn(mock(Item.class)); hud.useCursorPotion(); verify(controller).useCursorPotionOnMercenary();
    Riiablo.game = mock(GameScreen.class); assertFalse(hud.useCursorPotion()); hud.dispose(); hud.bindWorld(replacement); assertNull(get(hud, "world")); verifyNoInteractions(replacement);
  }

  @Test void realSubscriptionsReadOnlyOwnedLivingEntitiesAndGroupCounts() throws Exception {
    World world = world(); GameScreen game = mock(GameScreen.class); game.player = world.create(); Riiablo.game = game;
    int merc = merc(world, game.player); pet(world, game.player, "raven"); pet(world, game.player, "raven");
    pet(world, game.player, "skeleton"); pet(world, game.player + 99, "valkyrie"); int dead = pet(world, game.player, "valkyrie");
    world.getMapper(SummonedPet.class).get(dead).deathPending = true;
    world.process(); MercenaryHud hud = hud(); game.mercenaryHud = hud; hud.act(0);
    assertEquals(merc, get(hud, "mercenaryId")); assertEquals(0, get(hud, "mercenarySlotIndex")); assertEquals(3, slots(hud).size()); assertEquals(168, hud.getWidth());
    assertEquals(2, get(slots(hud).get(1), "count")); assertEquals("RAVEN", get(slots(hud).get(1), "kind").toString());
    assertEquals(25f, get(hud, "life")); assertEquals(50f, get(hud, "maxLife"));
    world.delete(merc); world.process(); hud.act(0); assertEquals(-1, get(hud, "mercenaryId")); assertFalse(hud.useCursorPotion());
  }

  @Test void bearStillSuppressesWolfAndDeadMercenaryIsExcluded() throws Exception {
    World world = world(); GameScreen game = mock(GameScreen.class); game.player = world.create(); Riiablo.game = game;
    int merc = merc(world, game.player); world.getMapper(UnitLifecycle.class).create(merc).reset().transition(UnitLifecycle.Phase.DEATH);
    pet(world, game.player, "spiritwolf"); pet(world, game.player, "grizzly"); world.process(); MercenaryHud hud = hud(); game.mercenaryHud = hud; hud.act(0);
    assertEquals(-1, get(hud, "mercenaryId")); assertEquals(1, slots(hud).size()); assertEquals("BEAR", get(slots(hud).get(0), "kind").toString());
  }

  @Test void currentPortraitDrawUsesOriginalIconAndHalfWidthHealthBar() throws Exception {
    World world = world(); GameScreen game = mock(GameScreen.class); game.player = world.create(); Riiablo.game = game;
    merc(world, game.player); world.process(); MercenaryHud hud = hud(); game.mercenaryHud = hud; assets.finishLoading(); hud.act(0);
    Batch batch = mock(Batch.class); hud.drawCompanions(batch, 1);
    verify(batch).draw(same(assets.get(ROOT + ICONS[0], DC6.class).getTexture()), eq(5f), eq(19f));
    verify(batch).draw(same(uploads.constructed().get(0)), eq(5f), eq(63f), eq(23f), eq(5f));
  }

  @Test void cachedSubscriptionsNeverQueryAReplacementWorld() throws Exception {
    World world = world(); GameScreen game = mock(GameScreen.class); game.player = world.create(); Riiablo.game = game;
    merc(world, game.player); world.process(); MercenaryHud hud = hud(); game.mercenaryHud = hud; hud.act(0);
    assertNotNull(get(hud, "mercenaries")); assertNotNull(get(hud, "summonedPets"));
    World other = mock(World.class); Riiablo.engine = other; hud.act(1); invoke(clicks(hud)); verifyNoInteractions(other, Riiablo.cursor, controller);
    hud.dispose(); assertNull(get(hud, "mercenaries")); assertNull(get(hud, "summonedPets")); assertTrue(world.getAspectSubscriptionManager().get(com.artemis.Aspect.all(Mercenary.class, AttributesWrapper.class)).getEntities().size() > 0);
  }

  @Test void metadataCacheUsesResolverIdentityAndNeverStronglyRetainsIt() throws Exception {
    MPQFileHandleResolver first = table("nameA"); Riiablo.mpqs = first; assertEquals("nameA", MercenaryHud.petTypeNameKey("skeleton"));
    assertEquals("nameA", MercenaryHud.petTypeNameKey("skeleton")); verify(first).resolve(anyString());
    Object cached = get(MercenaryHud.class, null, "petTypeNameKeys"); assertSame(first, ((WeakReference<?>) get(cached, "source")).get());
    MPQFileHandleResolver second = table("nameB"); Riiablo.mpqs = second; assertEquals("nameB", MercenaryHud.petTypeNameKey("skeleton")); verify(second).resolve(anyString());
    assertThrows(UnsupportedOperationException.class, () -> ((Map<?, ?>) get(cached, "names")).clear());
  }

  @Test void missingResolverOrMissingTableDoesNotPoisonANewHostsMetadata() {
    Riiablo.mpqs = null; assertNull(MercenaryHud.petTypeNameKey("skeleton"));
    Riiablo.mpqs = mock(MPQFileHandleResolver.class); assertNull(MercenaryHud.petTypeNameKey("skeleton"));
    Riiablo.mpqs = table("newHost"); assertEquals("newHost", MercenaryHud.petTypeNameKey("skeleton"));
  }

  private MercenaryHud hud() { return own(new MercenaryHud(controller)); }
  private MercenaryHud own(MercenaryHud hud) { resources.add(hud); return hud; }
  private World world() { World world = new World(new WorldConfiguration()); worlds.add(world); Riiablo.engine = world; return world; }
  private int merc(World world, int owner) { int id = world.create(); world.getMapper(Mercenary.class).create(id).set(owner, 0, 3, 123, 0); vitals(world, id); return id; }
  private int pet(World world, int owner, String type) { int id = world.create(); world.getMapper(SummonedPet.class).create(id).set(owner, type, -1, 1, false, 0); vitals(world, id); return id; }
  private void vitals(World world, int id) { Attributes attrs = Attributes.obtainStandard(); attrs.aggregate().put(Stat.hitpoints, 25f); attrs.aggregate().put(Stat.maxhp, 50f); world.getMapper(AttributesWrapper.class).create(id).attrs = attrs; }
  private List<?> slots(MercenaryHud hud) throws Exception { return (List<?>) get(hud, "slots"); }
  private void portrait(MercenaryHud hud) throws Exception { set(hud, "mercenaryId", 12); set(hud, "mercenarySlotIndex", 0); set(hud, "life", 25f); set(hud, "maxLife", 50f); hud.setVisible(true); }
  private MPQFileHandleResolver table(String name) {
    MPQFileHandleResolver resolver = mock(MPQFileHandleResolver.class); FileHandle file = mock(FileHandle.class); when(file.exists()).thenReturn(true);
    when(file.readBytes()).thenReturn(("Pet Type\tName\nskeleton\t" + name + "\n").getBytes(StandardCharsets.UTF_8)); when(resolver.resolve(anyString())).thenReturn(file); return resolver;
  }
  private Array<Actor> actors(Actor actor) { Array<Actor> values = new Array<>(); collect(actor, values); return values; }
  private void collect(Actor actor, Array<Actor> values) { values.add(actor); if (actor instanceof Group) for (Actor child : ((Group) actor).getChildren()) collect(child, values); }
  private Array<ClickListener> clicks(MercenaryHud hud) { Array<ClickListener> values = new Array<>(); for (EventListener listener : hud.getListeners()) if (listener instanceof ClickListener) values.add((ClickListener) listener); return values; }
  private void invoke(Array<ClickListener> saved) { for (ClickListener click : saved) { click.clicked(new InputEvent(), 10, 30); click.enter(null, 10, 30, 0, null); click.exit(null, 10, 30, 0, null); click.mouseMoved(null, 10, 30); } }
  private void retired(Array<Actor> actors) { for (Actor actor : actors) { assertNull(actor.getParent()); assertTrue(actor.getListeners().isEmpty()); assertTrue(actor.getCaptureListeners().isEmpty()); assertTrue(actor.getActions().isEmpty()); } }
  private Object get(Object value, String name) throws Exception { return get(value.getClass(), value, name); }
  private Object get(Class<?> type, Object value, String name) throws Exception { Field field = field(type, name); return field.get(value); }
  private void set(Object value, String name, Object data) throws Exception { set(value.getClass(), value, name, data); }
  private void set(Class<?> type, Object value, String name, Object data) throws Exception { field(type, name).set(value, data); }
  private Field field(Class<?> type, String name) throws Exception { for (; type != null; type = type.getSuperclass()) { try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; } catch (NoSuchFieldException missing) { } } throw new NoSuchFieldException(name); }
}
