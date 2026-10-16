package com.riiablo.screen.panel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.*;
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.net.Socket;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.*;
import com.riiablo.*;
import com.riiablo.attributes.Stat;
import com.riiablo.audio.Audio;
import com.riiablo.codec.DC6;
import com.riiablo.codec.FontTBL;
import com.riiablo.engine.client.ClientNetworkSynchronizer;
import com.riiablo.item.*;
import com.riiablo.net.packet.d2gs.*;
import com.riiablo.save.CharData;
import com.riiablo.screen.GameScreen;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.mockito.MockedConstruction;

/** Real trade widgets, tables, wallets and reference counts; intercept only private GL uploads. */
class VendorPanelLifecycleTest extends RiiabloTest {
  private static final String SOUND = "data/global/sfx/cursor/button.wav";
  private final Map<Field, Object> aliases = new HashMap<>();
  private final Array<Disposable> resources = new Array<>();
  private AssetManager assets;
  private CharData character;
  private FontTBL.BitmapFont font;
  private MockedConstruction<Texture> uploads;
  private Input previousInput;

  @BeforeEach void fixture() throws Exception {
    previousInput = Gdx.input; Gdx.input = mock(Input.class);
    for (Field field : Riiablo.class.getFields())
      if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) aliases.put(field, field.get(null));
    Riiablo.colors = new Colors(); Riiablo.game = null;
    character = CharData.obtain().clear().set(Riiablo.NORMAL, false, "VendorOwner", Riiablo.AMAZON);
    Riiablo.charData = character; wallet(1000);
    Riiablo.cursor = mock(Cursor.class); Riiablo.audio = mock(Audio.class);
    Texture texture = mock(Texture.class); when(texture.getWidth()).thenReturn(32); when(texture.getHeight()).thenReturn(32);
    TextureRegion region = new TextureRegion(texture);
    Riiablo.textures = mock(Textures.class); set(Riiablo.textures, "white", texture);
    Riiablo.fonts = mock(Fonts.class); font = mock(FontTBL.BitmapFont.class);
    BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData(); data.lineHeight = 16; data.capHeight = 12;
    for (int c = 32; c < 127; c++) {
      BitmapFont.Glyph glyph = new BitmapFont.Glyph(); glyph.id = c; glyph.width = 8; glyph.height = 12; glyph.xadvance = 8;
      data.setGlyph(c, glyph);
    }
    set(BitmapFont.class, font, "data", data); set(BitmapFont.class, font, "regions", Array.with(region));
    when(font.getData()).thenReturn(data); when(font.getColor()).thenReturn(Color.WHITE);
    when(font.getRegions()).thenReturn(Array.with(region)); when(font.getLineHeight()).thenReturn(16f);
    when(font.newFontCache()).thenAnswer(call -> new BitmapFontCache(font));
    Riiablo.fonts.font16 = font; Riiablo.fonts.fontformal11 = font;
    Riiablo.bundle = mock(I18NBundle.class); when(Riiablo.bundle.get(anyString())).thenReturn("fixture");
    when(Riiablo.bundle.format(anyString(), any())).thenReturn("price");
    assets = spy(new AssetManager(FileHandle::new, false)); Riiablo.assets = assets;
    assets.setLoader(DC6.class, new SynchronousAssetLoader<DC6, AssetLoaderParameters<DC6>>(FileHandle::new) {
      @Override public DC6 load(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<DC6> params) {
        DC6 dc = mock(DC6.class); when(dc.getTexture()).thenReturn(region); when(dc.getTexture(anyInt())).thenReturn(region); return dc;
      }
      @Override public Array<AssetDescriptor> getDependencies(String name, FileHandle file, AssetLoaderParameters<DC6> params) { return null; }
    });
    assets.setLoader(Sound.class, new SynchronousAssetLoader<Sound, AssetLoaderParameters<Sound>>(FileHandle::new) {
      @Override public Sound load(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<Sound> params) { return mock(Sound.class); }
      @Override public Array<AssetDescriptor> getDependencies(String name, FileHandle file, AssetLoaderParameters<Sound> params) { return null; }
    });
    uploads = mockConstruction(Texture.class);
  }

  @AfterEach void restore() throws Exception {
    try { for (Disposable resource : resources) resource.dispose(); assets.dispose(); }
    finally { uploads.close(); for (Map.Entry<Field, Object> alias : aliases.entrySet()) alias.getKey().set(null, alias.getValue()); Gdx.input = previousInput; }
  }

  @Test void sameCharacterVendorControlsRejectReplacementWorld() {
    Riiablo.engine = null;
    GameScreen game = mock(GameScreen.class); Riiablo.game = game;
    VendorPanel panel = own(new VendorPanel(game));
    Array<Actor> actors = PanelSessionAssertions.snapshot(panel);
    Riiablo.engine = mock(com.artemis.World.class);
    PanelSessionAssertions.assertInactive(panel, actors); verifyNoInteractions(Riiablo.engine);
    Riiablo.engine = null; Riiablo.game = mock(GameScreen.class);
    PanelSessionAssertions.assertInactive(panel, actors); verifyNoInteractions(Riiablo.game);
  }

  @Test void independentExitReturnsNineButtonSoundsFourSheetsAndFourPrivateFillsOnlyOnce() throws Exception {
    for (String path : paths()) assets.load(path, DC6.class); assets.load(SOUND, Sound.class); assets.finishLoading();
    VendorPanel panel = vendor(); assets.finishLoading(); assertEquals(10, assets.getReferenceCount(SOUND));
    Array<Actor> actors = actors(panel); Group root = new Group(); root.addActor(panel);
    for (Actor actor : actors) { actor.addCaptureListener(new InputListener()); actor.addAction(new Action() { public boolean act(float dt) { return false; } }); }
    AssetManager replacement = mock(AssetManager.class); Riiablo.assets = replacement; Riiablo.charData = mock(CharData.class);
    panel.dispose(); panel.dispose();
    assertEquals(5, assets.getLoadedAssets()); for (String path : paths()) assertEquals(1, assets.getReferenceCount(path), path);
    assertEquals(1, assets.getReferenceCount(SOUND)); assertEquals(4, uploads.constructed().size());
    for (Texture texture : uploads.constructed()) verify(texture).dispose();
    retired(actors); assertTrue(root.getChildren().isEmpty()); verifyNoInteractions(replacement); verify(font, never()).dispose();
    assertNull(get(panel, "tabs")); assertNull(get(panel, "localStock")); assertNull(get(panel, "networkSynchronizer"));
  }

  @Test void promptReparentReleasesOriginalCaptureOnReopenAndAfterDetach() throws Exception {
    VendorPanel panel = vendor(); Item item = stock(panel); Stage first = stage(panel); prompt(panel, item);
    InputListener dismiss = (InputListener) get(panel, "purchasePromptDismissListener");
    assertTrue(first.getRoot().getCaptureListeners().contains(dismiss, true));
    Stage second = stage(panel); prompt(panel, item);
    assertFalse(first.getRoot().getCaptureListeners().contains(dismiss, true));
    assertTrue(second.getRoot().getCaptureListeners().contains(dismiss, true));
    set(Actor.class, panel, "stage", null); panel.dispose();
    assertFalse(second.getRoot().getCaptureListeners().contains(dismiss, true)); verify(first).removeCaptureListener(dismiss);
    verify(second).removeCaptureListener(dismiss);
  }

  @Test void hideAndCancelReleaseCaptureAndAllowPromptReuse() throws Exception {
    VendorPanel panel = vendor(); Item item = stock(panel); Stage stage = stage(panel); panel.setVisible(true); prompt(panel, item);
    assertTrue(prompt(panel).isVisible()); panel.setVisible(false); assertFalse(prompt(panel).isVisible());
    assertTrue(stage.getRoot().getCaptureListeners().isEmpty()); panel.setVisible(true); prompt(panel, item);
    action(panel, "purchaseCancel").clicked(null, 0, 0); assertFalse(prompt(panel).isVisible());
    prompt(panel, item); assertTrue(prompt(panel).isVisible()); assertSame(item, get(panel, "pendingPurchaseItem"));
  }

  @Test void outsideClickDismissesPromptWithoutChangingWalletOrStock() throws Exception {
    VendorPanel panel = vendor(); Item item = stock(panel); stage(panel); prompt(panel, item);
    InputListener dismiss = (InputListener) get(panel, "purchasePromptDismissListener"); InputEvent event = new InputEvent();
    event.setStageX(-1000); event.setStageY(-1000);
    assertTrue(dismiss.touchDown(event, 0, 0, 0, Input.Buttons.LEFT)); assertFalse(prompt(panel).isVisible());
    assertEquals(1000, gold()); assertTrue(item.hasFlag2(Item.ITEMFLAG2_INSTORE));
  }

  @Test void captureAcquisitionFailureRollsBackOriginalStageAndPrompt() throws Exception {
    VendorPanel panel = vendor(); Item item = stock(panel); Stage stage = stage(panel);
    Error failure = new AssertionError("capture add");
    doAnswer(call -> { stage.getRoot().addCaptureListener(call.getArgument(0)); throw failure; }).when(stage).addCaptureListener(any());
    assertSame(failure, assertThrows(AssertionError.class, () -> prompt(panel, item)));
    assertFalse(prompt(panel).isVisible()); assertNull(get(panel, "pendingPurchaseItem")); assertNull(get(panel, "purchasePromptStage"));
    assertTrue(stage.getRoot().getCaptureListeners().isEmpty());
  }

  @Test void captureReleaseFailureStillReleasesAssetsFillsCallbacksAndBorrowedHandles() throws Exception {
    VendorPanel panel = vendor(); Item item = stock(panel); Stage stage = stage(panel); prompt(panel, item);
    Socket socket = mock(Socket.class); ClientNetworkSynchronizer sync = mock(ClientNetworkSynchronizer.class);
    set(panel, "clientSocket", socket); panel.setNetworkSynchronizer(sync); Array<Actor> actors = actors(panel);
    Error failure = new AssertionError("capture remove"); doThrow(failure).when(stage).removeCaptureListener(any());
    assertSame(failure, assertThrows(AssertionError.class, panel::dispose)); retired(actors);
    assertEquals(0, assets.getLoadedAssets()); for (Texture texture : uploads.constructed()) verify(texture).dispose();
    panel.dispose(); verify(stage).removeCaptureListener(any()); verifyNoInteractions(socket, sync);
    assertNull(get(panel, "networkSynchronizer")); assertNull(get(panel, "clientSocket"));
  }

  @Test void confirmedPurchaseUsesCursorAndIsSingleShot() throws Exception {
    VendorPanel panel = vendor(); Item item = stock(panel); prompt(panel, item);
    int expectedGold = 1000 - VendorPricing.buyPrice(item, null, character);
    ClickListener yes = action(panel, "purchaseConfirm"); yes.clicked(null, 0, 0);
    assertSame(item, character.getItems().getCursor()); assertEquals(expectedGold, gold());
    assertFalse(item.hasFlag2(Item.ITEMFLAG2_INSTORE)); assertFalse(prompt(panel).isVisible());
    yes.clicked(null, 0, 0); assertEquals(expectedGold, gold());
  }

  @Test void directPurchaseLeavesCursorEmptyAndCancelsSellMode() throws Exception {
    VendorPanel panel = vendor(); Item item = stock(panel); panel.setVisible(true); panel.btnSell.setChecked(true);
    int expectedGold = 1000 - VendorPricing.buyPrice(item, null, character);
    action(panel, "btnSell").clicked(null, 0, 0); assertTrue(panel.isSelling());
    assertTrue(purchase(panel).onPurchase(item, true)); assertNull(character.getItems().getCursor());
    assertTrue(character.getItems().contains(item)); assertEquals(expectedGold, gold()); assertFalse(panel.isSelling());
  }

  @Test void currentTradeModesAndExitStillUseTheirOriginalCursorAndGame() throws Exception {
    VendorPanel panel = vendor(); panel.config(VendorPanel.SMITHY, new Array<>()); panel.setVisible(true);
    panel.btnBuy.setChecked(true); action(panel, "btnBuy").clicked(null, 0, 0); verify(Riiablo.cursor).setCursor(Riiablo.cursor.buysell, 3);
    panel.btnRepair.setChecked(true); action(panel, "btnRepair").clicked(null, 0, 0); assertTrue(panel.isRepairing());
    panel.cancelTradeMode(); assertFalse(panel.isRepairing()); verify(Riiablo.cursor).resetCursor();
    GameScreen game = mock(GameScreen.class); game.vendorPanel = panel; Riiablo.game = game;
    action(panel, "btnExit").clicked(null, 0, 0); verify(game).closeVendorTradePanels();
  }

  @Test void networkOpenAndBuyKeepRequestRoutingAndWalletSnapshots() throws Exception {
    VendorPanel panel = vendor(); ClientNetworkSynchronizer sync = network(panel);
    when(sync.requestNpcService(anyInt(), anyByte(), anyByte(), anyInt(), anyInt(), anyLong())).thenReturn(42L, 43L);
    panel.configNetwork(VendorPanel.TRADER, 7, NpcServiceType.TRADE);
    verify(sync).requestNpcService(7, NpcServiceType.TRADE, NpcServiceOperation.OPEN, 0, -1, 0);
    NpcServiceResult result = mock(NpcServiceResult.class); when(result.requestId()).thenReturn(42L); when(result.gold()).thenReturn(900L);
    panel.applyNetworkResult(result); assertEquals(900, gold()); assertEquals(0L, get(panel, "pendingRequestId"));
    Item item = item(); item.id = 99; purchase(panel).onPurchase(item, true);
    verify(sync).requestNpcService(7, NpcServiceType.TRADE, NpcServiceOperation.BUY, 99, -1, 0);
    assertEquals(43L, get(panel, "pendingRequestId")); assertEquals(900, gold());
  }

  @Test void hiddenSameSessionResultStillUpdatesWalletAsBefore() throws Exception {
    VendorPanel panel = vendor(); NpcServiceResult result = mock(NpcServiceResult.class); when(result.gold()).thenReturn(750L);
    panel.setVisible(false); panel.applyNetworkResult(result); assertEquals(750, gold());
  }

  @Test void oldCallbacksAndNetworkResultsCannotTouchReplacementSession() throws Exception {
    VendorPanel panel = vendor(); Item item = stock(panel); stage(panel); prompt(panel, item);
    Array<Actor> actors = actors(panel); VendorGrid grid = panel.activeGrid; VendorGrid.PurchaseListener purchase = purchase(panel);
    InputListener dismiss = (InputListener) get(panel, "purchasePromptDismissListener"); NpcServiceResult result = mock(NpcServiceResult.class);
    Riiablo.charData = mock(CharData.class); Riiablo.game = mock(GameScreen.class); Riiablo.input = mock(Client.InputProcessor.class);
    ClientNetworkSynchronizer sync = network(panel); clearInvocations(Riiablo.cursor, Riiablo.audio, item.wrapper);
    lateClicks(actors); assertFalse(dismiss.touchDown(null, 0, 0, 0, 0));
    assertFalse(purchase.onPurchase(item, true)); assertFalse(purchase.onSell(item));
    assertFalse(grid.onStoredItemClicked(item, false)); assertFalse(grid.onCursorDrop(item));
    Array<Item> stock = Array.with(item); assertEquals(0, grid.drain(stock)); assertEquals(1, stock.size);
    assertFalse(panel.sellItem(0)); assertFalse(panel.repairItem(0)); assertFalse(panel.isSelling()); assertFalse(panel.isRepairing());
    assertFalse(panel.canSellItems()); assertEquals(0, panel.sellPrice(item)); panel.cancelTradeMode(); panel.setTab(0);
    panel.config(VendorPanel.TRADER, stock); panel.configNetwork(VendorPanel.TRADER, 7, NpcServiceType.TRADE); panel.applyNetworkResult(result);
    Batch batch = mock(Batch.class); panel.draw(batch, 1); panel.act(1); grid.draw(batch, 1);
    verifyNoInteractions(Riiablo.charData, Riiablo.game, Riiablo.input, Riiablo.cursor, Riiablo.audio, item.wrapper, result, sync, batch);
    assertEquals(1000, gold());
  }

  @Test void sameCharacterAndManagerDoNotAllowOldGameOrReplacementAudioCursor() {
    VendorPanel panel = vendor(); Riiablo.game = mock(GameScreen.class); assertFalse(panel.repairItem(0)); assertFalse(panel.canSellItems());
    panel.configNetwork(VendorPanel.TRADER, 7, NpcServiceType.TRADE); verifyNoInteractions(Riiablo.game);
    Riiablo.game = null; Audio original = Riiablo.audio; Riiablo.audio = mock(Audio.class); panel.setVisible(true); assertFalse(panel.isVisible());
    Riiablo.audio = original; Riiablo.cursor = mock(Cursor.class); panel.setVisible(true); assertFalse(panel.isVisible());
  }

  @Test void explicitGameOwnerAllowsConstructionBeforePublicationButGatesLaterDispatch() {
    GameScreen game = mock(GameScreen.class), other = mock(GameScreen.class); Riiablo.game = other;
    VendorPanel panel = own(new VendorPanel(game)); panel.config(VendorPanel.TRADER, new Array<>()); assertEquals(0, getInt(panel, "configuredFlags"));
    Riiablo.game = game; panel.config(VendorPanel.TRADER, new Array<>()); assertEquals(VendorPanel.TRADER, getInt(panel, "configuredFlags"));
    Riiablo.game = other; panel.setVisible(true); assertFalse(panel.isVisible()); panel.dispose(); assertNull(getUnchecked(panel, "owner"));
  }

  @Test void disposedSavedCallbacksCannotReopenReconfigureOrAdoptNewNetworkHost() throws Exception {
    VendorPanel panel = vendor(); Item item = stock(panel); Array<Actor> actors = actors(panel); VendorGrid.PurchaseListener purchase = purchase(panel);
    Array<ClickListener> saved = clicks(actors);
    ClientNetworkSynchronizer sync = mock(ClientNetworkSynchronizer.class); panel.dispose();
    invokeClicks(saved); panel.setVisible(true); panel.config(VendorPanel.TRADER, Array.with(item)); panel.setNetworkSynchronizer(sync);
    assertFalse(panel.isVisible()); assertFalse(purchase.onPurchase(item, true)); assertFalse(panel.isNetworked());
    assertNull(get(panel, "networkSynchronizer")); assertEquals(0, assets.getLoadedAssets()); retired(actors); verifyNoInteractions(sync);
  }

  @Test void staleDisposalDoesNotResetTheNewSessionsCursor() throws Exception {
    VendorPanel panel = vendor(); panel.setVisible(true); panel.btnSell.setChecked(true); action(panel, "btnSell").clicked(null, 0, 0);
    Cursor replacement = mock(Cursor.class); Riiablo.cursor = replacement; panel.dispose(); verifyNoInteractions(replacement);
  }

  @Test void earlyLoadFailurePreservesExternalReference() {
    assets.load(paths()[0], DC6.class); assets.finishLoading(); Error failure = new AssertionError("tabs load");
    doThrow(failure).when(assets).load(argThat((AssetDescriptor<?> a) -> a.fileName.endsWith("/buyselltabs.DC6")));
    assertSame(failure, assertThrows(AssertionError.class, VendorPanel::new)); assertEquals(1, assets.getLoadedAssets());
    assertEquals(1, assets.getReferenceCount(paths()[0]));
  }

  @Test void partialGridConstructionFailureReleasesEarlierReturnedGridsAndNineButtons() {
    Error failure = new AssertionError("third grid upload");
    Texture first = mock(Texture.class), second = mock(Texture.class);
    try (org.mockito.MockedStatic<PanelTextures> pixels = mockStatic(PanelTextures.class)) {
      pixels.when(() -> PanelTextures.solid(Color.BLACK)).thenReturn(first, second).thenThrow(failure);
      assertSame(failure, assertThrows(AssertionError.class, VendorPanel::new)); assertEquals(0, assets.getLoadedAssets());
      verify(first).dispose(); verify(second).dispose();
    }
  }

  @Test void latePromptFailureRollsBackAllFourGridsSoundsAndBorder() {
    Error failure = new AssertionError("title"); when(Riiablo.bundle.get("vendor_buy_title")).thenThrow(failure);
    assertSame(failure, assertThrows(AssertionError.class, VendorPanel::new)); assertEquals(0, assets.getLoadedAssets());
    assertEquals(4, uploads.constructed().size()); for (Texture texture : uploads.constructed()) verify(texture).dispose();
  }

  @Test void soundUnloadFailureStillRetiresTreeAllSheetsAndAllPrivateFills() {
    VendorPanel panel = vendor(); assets.finishLoading(); Array<Actor> actors = actors(panel); Error failure = new AssertionError("sound unload");
    doAnswer(call -> { call.callRealMethod(); throw failure; }).doCallRealMethod().when(assets).unload(SOUND);
    assertSame(failure, assertThrows(AssertionError.class, panel::dispose)); panel.dispose(); assertEquals(0, assets.getLoadedAssets()); retired(actors);
    for (Texture texture : uploads.constructed()) verify(texture).dispose();
  }

  @Test void hideCaptureFailureStillClearsPendingRequestAndTradeMode() throws Exception {
    VendorPanel panel = vendor(); Item item = stock(panel); Stage stage = stage(panel); panel.setVisible(true); prompt(panel, item);
    set(panel, "pendingRequestId", 42L); set(panel, "networkFlags", VendorPanel.TRADER); set(panel, "selling", true);
    Error failure = new AssertionError("hide capture"); doThrow(failure).when(stage).removeCaptureListener(any());
    assertSame(failure, assertThrows(AssertionError.class, () -> panel.setVisible(false)));
    assertEquals(0L, get(panel, "pendingRequestId")); assertEquals(0, get(panel, "networkFlags"));
    assertEquals(false, get(panel, "selling")); assertNull(get(panel, "pendingPurchaseItem"));
    assertFalse(prompt(panel).isVisible()); panel.dispose(); assertEquals(0, assets.getLoadedAssets());
    verify(stage).removeCaptureListener(any());
  }

  @Test void replacingOnlyItemDataOrAssetManagerStopsNetworkResultBeforeReadingPacket() throws Exception {
    character = spy(character); Riiablo.charData = character; VendorPanel panel = vendor();
    NpcServiceResult result = mock(NpcServiceResult.class); com.riiablo.save.ItemData original = character.getItems();
    when(character.getItems()).thenReturn(mock(com.riiablo.save.ItemData.class)); panel.applyNetworkResult(result);
    when(character.getItems()).thenReturn(original); Riiablo.assets = mock(AssetManager.class); panel.applyNetworkResult(result);
    verifyNoInteractions(result, Riiablo.assets);
  }

  @Test void finalDisposalDropsStockReferenceWithoutDestroyingBorrowedItemsOrSharedStockArray() throws Exception {
    VendorPanel panel = vendor(); Item item = stock(panel); Array<?> stock = (Array<?>) get(panel, "localStock");
    panel.dispose(); assertEquals(1, stock.size); assertSame(item, stock.first()); verify(item.wrapper, never()).dispose();
    assertNull(get(panel, "localStock")); assertNull(get(panel, "localPricing")); assertNull(get(panel, "localVendorGenerator"));
  }

  private VendorPanel vendor() { return own(new VendorPanel()); }
  private <T extends Disposable> T own(T value) { resources.add(value); return value; }
  private void wallet(int gold) { character.getStats().base().put(Stat.gold, gold); character.getStats().aggregate().put(Stat.gold, gold); }
  private int gold() { return character.getStats().get(Stat.gold).asInt(); }
  private Item item() {
    Item item = spy(new Item()); item.reset(); item.setBase(Riiablo.files.misc.get("hp1")); item.quality = Quality.NORMAL;
    item.wrapper = mock(ItemWrapper.class); doReturn("Fixture potion").when(item).getNameString();
    item.flags2 |= Item.ITEMFLAG2_INSTORE; return item;
  }
  private Item stock(VendorPanel panel) { Item item = item(); panel.config(VendorPanel.TRADER, Array.with(item)); return item; }
  private ClientNetworkSynchronizer network(VendorPanel panel) throws Exception {
    ClientNetworkSynchronizer sync = mock(ClientNetworkSynchronizer.class); panel.setNetworkSynchronizer(sync); set(panel, "clientSocket", mock(Socket.class)); return sync;
  }
  private VendorGrid.PurchaseListener purchase(VendorPanel panel) throws Exception { return (VendorGrid.PurchaseListener) get(panel.activeGrid, "purchaseListener"); }
  private void prompt(VendorPanel panel, Item item) throws Exception { purchase(panel).onPurchase(item, false); }
  private WidgetGroup prompt(VendorPanel panel) throws Exception { return (WidgetGroup) get(panel, "purchasePrompt"); }
  private Stage stage(VendorPanel panel) throws Exception {
    Stage stage = mock(Stage.class); Group root = new Group(); when(stage.getRoot()).thenReturn(root);
    when(stage.addCaptureListener(any())).thenAnswer(call -> root.addCaptureListener(call.getArgument(0)));
    when(stage.removeCaptureListener(any())).thenAnswer(call -> root.removeCaptureListener(call.getArgument(0)));
    when(stage.screenToStageCoordinates(any())).thenAnswer(call -> call.getArgument(0)); set(Actor.class, panel, "stage", stage); return stage;
  }
  private ClickListener action(VendorPanel panel, String field) throws Exception {
    ClickListener action = null;
    for (EventListener listener : ((Actor) get(panel, field)).getListeners()) if (listener instanceof ClickListener) {
      try { listener.getClass().getDeclaredMethod("clicked", InputEvent.class, float.class, float.class); }
      catch (NoSuchMethodException inherited) { continue; } action = (ClickListener) listener;
    }
    assertNotNull(action, field); return action;
  }
  private Array<Actor> actors(Actor actor) { Array<Actor> values = new Array<>(); collect(actor, values); return values; }
  private void collect(Actor actor, Array<Actor> values) { values.add(actor); if (actor instanceof Group) for (Actor child : ((Group) actor).getChildren()) collect(child, values); }
  private void lateClicks(Array<Actor> actors) {
    invokeClicks(clicks(actors));
  }
  private Array<ClickListener> clicks(Array<Actor> actors) {
    Array<ClickListener> clicks = new Array<>();
    for (Actor actor : actors) for (EventListener listener : actor.getListeners())
      if (listener instanceof ClickListener) clicks.add((ClickListener) listener);
    return clicks;
  }
  private void invokeClicks(Array<ClickListener> clicks) {
    InputEvent event = new InputEvent(); event.setButton(Input.Buttons.LEFT);
    for (ClickListener listener : clicks) {
      listener.clicked(null, 0, 0);
      listener.touchDown(event, 0, 0, 0, Input.Buttons.LEFT);
    }
  }
  private void retired(Array<Actor> actors) { for (Actor actor : actors) { assertNull(actor.getParent()); assertTrue(actor.getListeners().isEmpty()); assertTrue(actor.getCaptureListeners().isEmpty()); assertTrue(actor.getActions().isEmpty()); } }
  private String[] paths() { return new String[] {"data/global/ui/PANEL/buysell.dc6", "data/global/ui/PANEL/buyselltabs.DC6", "data/global/ui/PANEL/buysellbtn.DC6", "data/global/ui/MENU/boxpieces.DC6"}; }
  private int getInt(Object value, String field) { return (Integer) getUnchecked(value, field); }
  private Object getUnchecked(Object value, String field) { try { return get(value, field); } catch (Exception e) { throw new AssertionError(e); } }
  private Object get(Object value, String name) throws Exception {
    for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
      try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(value); } catch (NoSuchFieldException missing) { }
    } throw new NoSuchFieldException(name);
  }
  private void set(Object value, String name, Object data) throws Exception {
    for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
      try { set(type, value, name, data); return; } catch (NoSuchFieldException missing) { }
    } throw new NoSuchFieldException(name);
  }
  private void set(Class<?> type, Object value, String name, Object data) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(value, data); }
}
