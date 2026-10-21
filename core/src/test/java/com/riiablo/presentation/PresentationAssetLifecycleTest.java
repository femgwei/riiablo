package com.riiablo.presentation;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Array;
import com.riiablo.Colors;
import com.riiablo.Fonts;
import com.riiablo.Riiablo;
import com.riiablo.Textures;
import com.riiablo.codec.Animation;
import com.riiablo.codec.DCC;
import com.riiablo.codec.DC6;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.Overlay;
import com.riiablo.map.Map;
import com.riiablo.widget.DCWrapper;
import com.riiablo.widget.Label;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/** Real AssetManager and Artemis lifecycle; codecs, rendering and the Label are fixtures. */
class PresentationAssetLifecycleTest {
  private static final String GLEAM = "data/global/overlays/Gleam.dcc";
  private static final String MARKER = "data/global/overlays/NPCSpeechBalloon.dcc";
  private static final String SHRINE = "data/global/overlays/ShrineManaRegen.dcc";
  private final java.util.Map<Field, Object> aliases = new HashMap<>();
  private final List<World> worlds = new ArrayList<>();
  private Application oldApp;
  private AssetManager assets;
  private MockedStatic<Animation> animations;
  private MockedConstruction<Label> labels;

  @BeforeEach void setup() throws Exception {
    for (Field field : Riiablo.class.getFields()) {
      if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
        aliases.put(field, field.get(null));
      }
    }
    oldApp = Gdx.app;
    Gdx.app = mock(Application.class);
    Riiablo.colors = new Colors();
    Riiablo.fonts = mock(Fonts.class);
    Riiablo.textures = mock(Textures.class);
    Texture white = mock(Texture.class);
    when(white.getWidth()).thenReturn(16); when(white.getHeight()).thenReturn(16);
    set(Riiablo.textures, "white", white);
    labels = mockConstruction(Label.class);
    animations = mockStatic(Animation.class);
    animations.when(Animation::newAnimation).thenAnswer(call -> animation());
    animations.when(Animation::builder).thenAnswer(call -> builder(animation()));
    assets = new AssetManager(FileHandle::new, false);
    assets.setLoader(DCC.class, new SynchronousAssetLoader<DCC, AssetLoaderParameters<DCC>>(FileHandle::new) {
      @Override public DCC load(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<DCC> params) { return mock(DCC.class); }
      @Override public Array<AssetDescriptor> getDependencies(String name, FileHandle file, AssetLoaderParameters<DCC> params) { return null; }
    });
    Texture texture = mock(Texture.class);
    when(texture.getWidth()).thenReturn(16);
    when(texture.getHeight()).thenReturn(16);
    TextureRegion region = new TextureRegion(texture);
    assets.setLoader(DC6.class, new SynchronousAssetLoader<DC6, AssetLoaderParameters<DC6>>(FileHandle::new) {
      @Override public DC6 load(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<DC6> params) {
        DC6 dc = mock(DC6.class);
        when(dc.getTexture()).thenReturn(region);
        return dc;
      }
      @Override public Array<AssetDescriptor> getDependencies(String name, FileHandle file, AssetLoaderParameters<DC6> params) { return null; }
    });
    Riiablo.assets = assets;
    Riiablo.files = mock(com.riiablo.Files.class);
    Overlay table = mock(Overlay.class);
    Overlay.Entry row = new Overlay.Entry();
    row.overlay = "shrine_mana_regen";
    row.Filename = "ShrineManaRegen";
    row.AnimRate = 8;
    when(table.get(row.overlay)).thenReturn(row);
    when(table.iterator()).thenAnswer(call -> java.util.Collections.singleton(row).iterator());
    set(Riiablo.files, "Overlay", table);
  }

  @AfterEach void restore() throws Exception {
    try {
      for (World world : worlds) world.dispose();
      assets.dispose();
    } finally {
      animations.close(); labels.close(); Gdx.app = oldApp;
      for (java.util.Map.Entry<Field, Object> alias : aliases.entrySet()) alias.getKey().set(null, alias.getValue());
    }
  }

  @Test void gleamQueuedSharedReferenceReturnsOnRepeatedDispose() {
    assets.load(GLEAM, DCC.class);
    GroundItemGleamSystem system = new GroundItemGleamSystem();
    system.initialize(); system.begin(); system.begin();
    system.dispose(); system.dispose(); system.begin();
    assertEquals(1, assets.getReferenceCount(GLEAM));
    assets.unload(GLEAM);
    assertEquals(0, assets.getLoadedAssets());
  }

  @Test void questMarkerQueuedSharedReferenceReturnsOnRepeatedDispose() {
    assets.load(MARKER, DCC.class);
    MarkerHarness system = new MarkerHarness();
    system.initialize(); system.begin(); system.begin();
    system.dispose(); system.dispose(); system.begin(); system.end();
    assertEquals(1, assets.getReferenceCount(MARKER));
    assets.unload(MARKER);
    assertEquals(0, assets.getLoadedAssets());
  }

  @Test void renderSystemsReleaseOnlyTheirOriginalManager() {
    GroundItemGleamSystem gleam = new GroundItemGleamSystem(); gleam.initialize();
    MarkerHarness marker = new MarkerHarness(); marker.initialize();
    AssetManager replacement = mock(AssetManager.class);
    Riiablo.assets = replacement;
    gleam.begin(); marker.begin(); assets.finishLoading(); marker.begin();
    DCC g = assets.get(GLEAM, DCC.class), m = assets.get(MARKER, DCC.class);
    gleam.dispose(); marker.dispose();
    assertEquals(0, assets.getLoadedAssets());
    verify(g).dispose(); verify(m).dispose(); verifyNoInteractions(replacement);
  }

  @Test void neverStartedRenderSystemsDoNotAcquireAssets() {
    GroundItemGleamSystem gleam = new GroundItemGleamSystem(); gleam.initialize();
    MarkerHarness marker = new MarkerHarness(); marker.initialize();
    gleam.dispose(); marker.dispose(); gleam.begin(); marker.begin();
    assertEquals(0, assets.getQueuedAssets()); assertEquals(0, assets.getLoadedAssets());
  }

  @ParameterizedTest @ValueSource(strings = {"entity", "component", "earlyEntity", "earlyComponent"})
  void overlayRemovalReleasesEvenBeforeFirstInsertion(String kind) {
    OverlayManager manager = new OverlayManager(); World world = world(manager);
    int entity = world.create(); manager.setShrineIcon(entity, "shrine_mana_regen");
    if (!kind.startsWith("early")) { assets.finishLoading(); world.process(); }
    if (kind.toLowerCase().endsWith("entity")) world.delete(entity);
    else world.getMapper(com.riiablo.engine.client.component.Overlay.class).remove(entity);
    world.process();
    assertFalse(assets.contains(SHRINE));
  }

  @Test void overlayWorldExitBeforeProcessingAndRepeatedDisposeReleaseQueuedSharedRequest() {
    assets.load(SHRINE, DCC.class);
    OverlayManager manager = new OverlayManager(); World world = world(manager);
    manager.setShrineIcon(world.create(), "shrine_mana_regen");
    world.dispose(); manager.dispose();
    assertEquals(1, assets.getReferenceCount(SHRINE));
    assertThrows(IllegalStateException.class, () -> manager.set(0, "shrine_mana_regen"));
  }

  @Test void overlaySameStateIsIdempotentAndWrongClearDoesNotRelease() {
    OverlayManager manager = new OverlayManager(); World world = world(manager);
    int entity = world.create(); manager.setPersistent(entity, 7, "shrine_mana_regen");
    manager.setPersistent(entity, 7, "shrine_mana_regen"); assets.finishLoading();
    assertEquals(1, assets.getReferenceCount(SHRINE));
    manager.clearPersistent(entity, 8); assertEquals(1, assets.getReferenceCount(SHRINE));
    manager.clearPersistent(entity, 7); manager.clearPersistent(entity, 7);
    world.process(); assertFalse(assets.contains(SHRINE));
  }

  @Test void sameFrameReplacementAndDeferredRemovalKeepNewOwner() {
    OverlayManager manager = new OverlayManager(); World world = world(manager);
    int entity = world.create(); manager.setPersistent(entity, 1, "shrine_mana_regen");
    assets.finishLoading(); world.process();
    manager.clearPersistent(entity, 1);
    manager.setPersistent(entity, 2, "shrine_mana_regen");
    assets.finishLoading(); world.process(); world.process();
    assertEquals(1, assets.getReferenceCount(SHRINE));
    assertTrue(world.getMapper(com.riiablo.engine.client.component.Overlay.class).get(entity).isLoaded);
    manager.dispose(); assertFalse(assets.contains(SHRINE));
  }

  @Test void externalReplacementReleasesOldLedgerWithoutResettingNewComponent() {
    OverlayManager manager = new OverlayManager(); World world = world(manager);
    int entity = world.create(); manager.setShrineIcon(entity, "shrine_mana_regen");
    assets.finishLoading(); world.process();
    com.riiablo.engine.client.component.Overlay component = world.getMapper(com.riiablo.engine.client.component.Overlay.class).get(entity);
    Overlay.Entry replacement = new Overlay.Entry(); replacement.Filename = "Other";
    component.set(replacement);
    AssetDescriptor descriptor = component.assetDescriptor;
    world.process();
    assertFalse(assets.contains(SHRINE)); assertSame(descriptor, component.assetDescriptor);
  }

  @Test void twoEntitiesShareAssetAndDeletingOneCannotResetReusedComponent() {
    OverlayManager manager = new OverlayManager(); World world = world(manager);
    int first = world.create(), second = world.create();
    manager.setShrineIcon(first, "shrine_mana_regen"); manager.setShrineIcon(second, "shrine_mana_regen");
    assets.finishLoading(); world.process();
    DCC dcc = assets.get(SHRINE, DCC.class); assertEquals(2, assets.getReferenceCount(SHRINE));
    world.delete(first); world.process();
    assertEquals(1, assets.getReferenceCount(SHRINE)); verify(dcc, never()).dispose();
    int reused = world.create(); manager.setShrineIcon(reused, "shrine_mana_regen");
    assets.finishLoading(); world.process();
    assertEquals(2, assets.getReferenceCount(SHRINE));
    world.dispose(); assertFalse(assets.contains(SHRINE)); verify(dcc).dispose();
  }

  @Test void missingOverlayLeavesPreviousPresentationOwned() {
    OverlayManager manager = new OverlayManager(); World world = world(manager);
    int entity = world.create(); manager.setShrineIcon(entity, "shrine_mana_regen"); assets.finishLoading();
    manager.set(entity, "missing"); world.process();
    assertEquals(1, assets.getReferenceCount(SHRINE));
    assertTrue(world.getMapper(com.riiablo.engine.client.component.Overlay.class).get(entity).persistent);
  }

  @Test void transientKeepsExistingHoldFramesThenReleases() {
    OverlayManager manager = new OverlayManager(); World world = world(manager);
    int entity = world.create(); manager.set(entity, "shrine_mana_regen"); assets.finishLoading();
    world.process(); world.process(); world.process(); assertTrue(assets.contains(SHRINE));
    world.process(); assertFalse(assets.contains(SHRINE));
  }

  @Test void cursesBuffsAndImpactsOwnIndependentLayersAndReferences() {
    Overlay.Entry back = new Overlay.Entry();
    back.overlay = "back"; back.Filename = "Back"; back.PreDraw = true;
    when(Riiablo.files.Overlay.get("back")).thenReturn(back);
    OverlayManager manager = new OverlayManager(); World world = world(manager);
    int entity = world.create();
    manager.setPersistent(entity, 1, "shrine_mana_regen");
    manager.setPersistent(entity, 1, "back"); // overlay2 of the same state
    manager.setPersistent(entity, 2, "shrine_mana_regen");
    manager.set(entity, "shrine_mana_regen");
    assets.finishLoading(); world.process();
    com.riiablo.engine.client.component.Overlay root =
        world.getMapper(com.riiablo.engine.client.component.Overlay.class).get(entity);
    assertEquals(3, root.layers.size);
    assertEquals(3, assets.getReferenceCount(SHRINE));
    assertTrue(root.layers.first().entry.PreDraw);
    manager.clearPersistent(entity, 1);
    assertNull(root.entry, "removing the primary layer must keep its children alive");
    assertEquals(2, root.layers.size);
    assertEquals(2, assets.getReferenceCount(SHRINE));
    assertFalse(assets.contains("data/global/overlays/Back.dcc"));
    world.process(); world.process(); world.process();
    assertEquals(1, root.layers.size, "only the transient impact expires");
    assertEquals(1, assets.getReferenceCount(SHRINE));
    assertEquals(2, root.layers.first().stateId);
    manager.clearPersistent(entity, 2); world.process();
    assertFalse(assets.contains(SHRINE));
    assertFalse(world.getMapper(com.riiablo.engine.client.component.Overlay.class).has(entity));
  }

  @Test void deletingEntityReleasesEveryLoadedAndQueuedLayer() {
    OverlayManager manager = new OverlayManager(); World world = world(manager);
    int entity = world.create();
    manager.setPersistent(entity, 1, "shrine_mana_regen");
    assets.finishLoading(); world.process();
    manager.setPersistent(entity, 2, "shrine_mana_regen");
    manager.set(entity, "shrine_mana_regen");
    world.delete(entity); world.process();
    assertFalse(assets.contains(SHRINE));
  }

  @Test void overlayUsesOriginalManagerAndDisposalErrorStillReleasesOtherEntities() {
    OverlayManager manager = new OverlayManager(); World world = world(manager);
    int first = world.create(), second = world.create();
    manager.setShrineIcon(first, "shrine_mana_regen"); manager.setShrineIcon(second, "shrine_mana_regen");
    assets.finishLoading(); world.process();
    AssetManager replacement = mock(AssetManager.class); Riiablo.assets = replacement;
    Animation animation = world.getMapper(com.riiablo.engine.client.component.Overlay.class).get(first).animation;
    doThrow(new AssertionError("reset")).when(animation).reset();
    assertThrows(AssertionError.class, manager::dispose);
    assertFalse(assets.contains(SHRINE)); verifyNoInteractions(replacement);
    manager.dispose(); doNothing().when(animation).reset();
  }

  @Test void rejectedOverlaySubmissionRemovesComponentWithoutUnloadingUnownedAsset() {
    AssetManager rejecting = mock(AssetManager.class); Riiablo.assets = rejecting;
    Error failure = new AssertionError("submit");
    doThrow(failure).when(rejecting).load(any(AssetDescriptor.class));
    OverlayManager manager = new OverlayManager(); World world = world(manager);
    int entity = world.create();
    assertSame(failure, assertThrows(AssertionError.class,
        () -> manager.setShrineIcon(entity, "shrine_mana_regen")));
    assertFalse(world.getMapper(com.riiablo.engine.client.component.Overlay.class).has(entity));
    manager.dispose(); manager.dispose();
    verify(rejecting, never()).unload(anyString());
  }

  @Test void initiallyMissingHostBindsFirstAvailableManagerOnly() {
    Riiablo.assets = null;
    GroundItemGleamSystem gleam = new GroundItemGleamSystem(); gleam.initialize(); gleam.begin();
    MarkerHarness marker = new MarkerHarness(); marker.initialize(); marker.begin();
    assertEquals(0, assets.getQueuedAssets());
    Riiablo.assets = assets; gleam.begin(); marker.begin();
    Riiablo.assets = mock(AssetManager.class);
    gleam.dispose(); marker.dispose();
    assertEquals(0, assets.getLoadedAssets()); verifyNoInteractions(Riiablo.assets);
  }

  @Test void zoneReplacementClearsOldDrawableAndQueuedReferenceAndFinalActor() throws Exception {
    ZoneHarness system = zone();
    String old = "data/local/ui/eng/act1/A1L1.dc6", next = "data/local/ui/eng/act1/A1Q1.dc6";
    Riiablo.language = null;
    display(system, 1, "A1L1"); assets.finishLoading(); system.processSystem();
    DC6 dc = assets.get(old, DC6.class);
    DCWrapper image = (DCWrapper) system.stage().getRoot().getChildren().first();
    assertNotNull(((TextureRegionDrawable) image.getDrawable()).getRegion());
    display(system, 8, "ignored");
    assertFalse(assets.contains(old)); verify(dc).dispose();
    assertNull(((TextureRegionDrawable) image.getDrawable()).getRegion());
    system.dispose(); system.dispose(); system.processSystem();
    assertFalse(assets.contains(next)); assertTrue(system.stage().getRoot().getChildren().isEmpty());
  }

  @Test void zoneExitProtectsSharedConsumerAndOriginalManager() throws Exception {
    ZoneHarness system = zone(); Riiablo.language = null;
    String path = "data/local/ui/eng/act1/A1L1.dc6";
    assets.load(path, DC6.class); display(system, 1, "A1L1");
    AssetManager replacement = mock(AssetManager.class); Riiablo.assets = replacement;
    system.dispose();
    assertEquals(1, assets.getReferenceCount(path)); verifyNoInteractions(replacement);
  }

  @Test void zoneNeverDisplayedStillRemovesActor() {
    ZoneHarness system = zone(); system.dispose(); system.dispose();
    assertTrue(system.stage().getRoot().getChildren().isEmpty()); assertEquals(0, assets.getQueuedAssets());
  }

  private World world(OverlayManager manager) {
    World world = new World(new WorldConfigurationBuilder().with(manager).build()
        .register("clientContext", ClientGameplayContext.fromGlobals()));
    worlds.add(world); return world;
  }

  private ZoneHarness zone() {
    ZoneHarness system = new ZoneHarness(); Stage stage = mock(Stage.class); Group root = new Group();
    when(stage.getRoot()).thenReturn(root);
    doAnswer(call -> { root.addActor(call.getArgument(0)); return null; }).when(stage).addActor(any());
    system.bind(stage); system.initialize(); return system;
  }

  @com.artemis.annotations.All({com.riiablo.engine.server.component.Monster.class,
      com.riiablo.engine.server.component.Position.class})
  private static class MarkerHarness extends Act1QuestIndicatorSystem {
    @Override public void initialize() { super.initialize(); }
    @Override public void begin() { super.begin(); }
    @Override public void end() { super.end(); }
    @Override public void dispose() { super.dispose(); }
  }

  private static class ZoneHarness extends ZoneEntryDisplayer {
    void bind(Stage host) { stage = host; }
    Stage stage() { return stage; }
    @Override public void initialize() { super.initialize(); }
    @Override public void processSystem() { super.processSystem(); }
    @Override public void dispose() { super.dispose(); }
  }

  private static void display(ZoneEntryDisplayer system, int id, String filename) throws Exception {
    Map.Zone zone = mock(Map.Zone.class); Levels.Entry level = new Levels.Entry(); level.Id = id; level.EntryFile = filename;
    set(zone, "level", level); Map map = mock(Map.class); set(zone, "map", map); when(map.getAct()).thenReturn(0);
    java.lang.reflect.Method method = ZoneEntryDisplayer.class.getDeclaredMethod("displayEntry", Map.Zone.class);
    method.setAccessible(true); method.invoke(system, zone);
  }

  private static Animation animation() {
    Animation animation = mock(Animation.class);
    Animation.Builder builder = builder(animation);
    when(animation.edit()).thenReturn(builder);
    when(animation.getNumFramesPerDir()).thenReturn(1); return animation;
  }

  private static Animation.Builder builder(Animation animation) {
    Animation.Builder builder = mock(Animation.Builder.class, RETURNS_SELF); when(builder.build()).thenReturn(animation); return builder;
  }

  private static void set(Object target, String name, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
  }
}

