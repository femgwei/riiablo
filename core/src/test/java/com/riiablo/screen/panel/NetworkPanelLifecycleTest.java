package com.riiablo.screen.panel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.I18NBundle;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.Colors;
import com.riiablo.Fonts;
import com.riiablo.Riiablo;
import com.riiablo.codec.FontTBL;
import com.riiablo.engine.client.*;
import com.riiablo.engine.server.party.Party;
import com.riiablo.engine.server.party.PartyRelation;
import com.riiablo.engine.server.trade.TradeState;
import com.riiablo.graphics.PaletteIndexedBatch;
import com.riiablo.net.packet.d2gs.*;
import com.riiablo.save.CharData;
import com.riiablo.screen.GameScreen;
import com.riiablo.widget.Label;
import com.riiablo.widget.LabelButton;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Production text panels/actors and detached wire snapshots, without MPQ, GL, or a socket. */
class NetworkPanelLifecycleTest {
  private final Map<Field, Object> aliases = new HashMap<>();
  private final Array<SessionNetworkPanel> panels = new Array<>();
  private Application previousApp;
  private com.badlogic.gdx.Files previousFiles;
  private FontTBL.BitmapFont font;
  private GameScreen owner;
  private World world;
  private ClientNetworkSynchronizer sender;
  private ClientNetworkReceiver receiver;
  private ClientPartyState party;
  private ClientTradeState trade;

  @BeforeEach void fixture() throws Exception {
    GdxNativesLoader.load(); previousApp = Gdx.app; Gdx.app = mock(Application.class);
    previousFiles = Gdx.files; Gdx.files = mock(com.badlogic.gdx.Files.class);
    for (Field field : Riiablo.class.getFields()) if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) aliases.put(field, field.get(null));
    Riiablo.assets = mock(AssetManager.class); Riiablo.charData = mock(CharData.class);
    Riiablo.colors = new Colors(); Riiablo.fonts = mock(Fonts.class);
    font = mock(FontTBL.BitmapFont.class); BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData();
    data.lineHeight = 16; data.capHeight = 12;
    for (int c = 32; c < 127; c++) { BitmapFont.Glyph g = new BitmapFont.Glyph(); g.id = c; g.width = 8; g.height = 12; g.xadvance = 8; data.setGlyph(c, g); }
    Texture texture = mock(Texture.class); when(texture.getWidth()).thenReturn(128); when(texture.getHeight()).thenReturn(128);
    Riiablo.textures = mock(com.riiablo.Textures.class); set(com.riiablo.Textures.class, Riiablo.textures, "white", texture);
    Array<TextureRegion> regions = Array.with(new TextureRegion(texture));
    set(BitmapFont.class, font, "data", data); set(BitmapFont.class, font, "regions", regions);
    when(font.getData()).thenReturn(data); when(font.getRegions()).thenReturn(regions); when(font.getColor()).thenReturn(Color.WHITE);
    when(font.getLineHeight()).thenReturn(16f); when(font.newFontCache()).thenAnswer(call -> new BitmapFontCache(font));
    Riiablo.fonts.font16 = font; Riiablo.fonts.fontformal10 = font; Riiablo.fonts.fontformal11 = font;
    Riiablo.bundle = mock(I18NBundle.class, call -> call.getArgument(0));
    owner = mock(GameScreen.class); Riiablo.game = owner; world = mock(World.class); Riiablo.engine = world;
    sender = mock(ClientNetworkSynchronizer.class); receiver = mock(ClientNetworkReceiver.class);
    when(world.getSystem(ClientNetworkSynchronizer.class)).thenReturn(sender); when(world.getSystem(ClientNetworkReceiver.class)).thenReturn(receiver);
    party = new ClientPartyState(); trade = new ClientTradeState();
    when(receiver.partyState()).thenReturn(party); when(receiver.tradeState()).thenReturn(trade);
    when(sender.serverPlayerId()).thenReturn(10); when(sender.requestParty(anyByte(), anyInt())).thenReturn(1L);
    when(sender.requestTrade(anyByte(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyLong())).thenReturn(2L);
  }

  @AfterEach void restore() throws Exception {
    try { for (SessionNetworkPanel panel : panels) panel.dispose(); }
    finally { Gdx.app = previousApp; Gdx.files = previousFiles; for (Map.Entry<Field, Object> alias : aliases.entrySet()) alias.getKey().set(null, alias.getValue()); }
  }

  @ParameterizedTest @ValueSource(booleans = {true, false})
  void independentExitRetiresCompleteTreeAndDetachedControlsWithoutDisposingBorrowedHosts(boolean isParty) throws Exception {
    SessionNetworkPanel panel = panel(isParty); panel.setVisible(true); panel.act(0);
    Array<Actor> tree = actors(panel); Array<ClickListener> clicks = clicks(tree);
    Actor detached = button(panel, "close"); detached.remove();
    for (Actor actor : tree) { actor.addCaptureListener(new InputListener()); actor.addAction(new Action() { public boolean act(float dt) { fail("retired action"); return false; } }); }
    Group parent = new Group(); parent.addActor(panel); clearInvocations(sender, receiver, owner, Riiablo.assets);
    panel.dispose(); panel.dispose(); retired(tree); invoke(clicks); inactive(panel);
    assertTrue(parent.getChildren().isEmpty()); assertNull(get(panel, "owner")); assertNull(get(panel, "world"));
    assertNull(get(panel, "synchronizer")); assertNull(get(panel, "receiver")); assertNull(panel.getBackground());
    assertEquals(0, ((Array<?>) get(panel, "ownedActors")).size);
    verifyNoInteractions(sender, receiver, owner, Riiablo.assets); verify(font, never()).dispose();
  }

  @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8})
  void eitherPanelRejectsReplacedGameWorldCharacterAndResourceHosts(int replacement) {
    SessionNetworkPanel a = panel(true), b = panel(false); a.setVisible(true); b.setVisible(true); a.act(0); b.act(0);
    Array<Actor> tree = actors(a); tree.addAll(actors(b)); Array<ClickListener> saved = clicks(tree);
    switch (replacement) {
      case 0: Riiablo.game = mock(GameScreen.class); break;
      case 1: Riiablo.engine = mock(World.class); break;
      case 2: Riiablo.charData = mock(CharData.class); break;
      case 3: Riiablo.assets = mock(AssetManager.class); break;
      case 4: Riiablo.fonts = mock(Fonts.class); break;
      case 5: Riiablo.colors = new Colors(); break;
      case 6: Riiablo.bundle = mock(I18NBundle.class); break;
      case 7: Riiablo.game = null; break;
      case 8: Riiablo.textures = mock(com.riiablo.Textures.class); break;
      default: fail();
    }
    clearInvocations(sender, receiver, owner); invoke(saved); inactive(a); inactive(b);
    ((TradePanel) b).applyNetworkState(receiver);
    for (Actor actor : tree) {
      actor.act(0); actor.draw((Batch) null, 1); assertNull(actor.hit(0, 0, true));
      if (actor instanceof Label) ((Label) actor).draw((PaletteIndexedBatch) null, 1);
    }
    verifyNoInteractions(sender, receiver, owner);
  }

  @ParameterizedTest @ValueSource(booleans = {true, false})
  void hiddenCallbacksAreIgnoredAndCurrentControlsWorkAgainAfterReopen(boolean isParty) {
    SessionNetworkPanel panel = panel(isParty); Actor close = button(panel, "close"); Array<ClickListener> saved = clicks(Array.with(close));
    invoke(saved); verifyNoInteractions(owner); panel.setVisible(true); invoke(saved); verify(owner).setLeftPanel(null);
    clearInvocations(owner); panel.setVisible(false); invoke(saved); verifyNoInteractions(owner);
    panel.setVisible(true); invoke(saved); verify(owner).setLeftPanel(null);
  }

  @ParameterizedTest @ValueSource(booleans = {true, false})
  void bindingIsOneWorldAndOneNetworkPairAndCannotResurrectAfterExit(boolean isParty) {
    SessionNetworkPanel panel = panel(isParty); panel.bindWorld(world); panel.setNetworkSystems(sender, receiver);
    assertThrows(IllegalStateException.class, () -> panel.bindWorld(mock(World.class)));
    ClientNetworkReceiver other = mock(ClientNetworkReceiver.class);
    assertThrows(IllegalArgumentException.class, () -> panel.setNetworkSystems(sender, other));
    when(world.getSystem(ClientNetworkReceiver.class)).thenReturn(other);
    assertThrows(IllegalStateException.class, () -> panel.setNetworkSystems(sender, other));
    panel.dispose(); clearInvocations(world); panel.bindWorld(world); panel.setNetworkSystems(sender, receiver);
    verifyNoInteractions(world); inactive(panel);
  }

  @ParameterizedTest @ValueSource(booleans = {true, false})
  void cleanupFailureStillRetiresOtherActorsAndClearsBorrowedSystems(boolean isParty) throws Exception {
    SessionNetworkPanel panel = panel(isParty); Actor failed = panel.own(spy(new Actor())); Actor last = panel.own(new Actor());
    failed.addListener(new InputListener()); last.addListener(new InputListener());
    doAnswer(call -> { call.callRealMethod(); throw new AssertionError("cleanup"); }).when(failed).clearListeners();
    assertEquals("cleanup", assertThrows(AssertionError.class, panel::dispose).getMessage());
    assertEquals(0, last.getListeners().size); assertNull(get(panel, "receiver")); assertNull(get(panel, "owner"));
    panel.dispose(); inactive(panel); verify(failed).clearListeners();
  }

  @ParameterizedTest @ValueSource(booleans = {true, false})
  void lateConstructorAttachmentFailureRetiresUnattachedCloseAndPreservesCleanupFailure(boolean isParty) {
    Error failure = new AssertionError("attach"); Array<Actor> saved = new Array<>(); AtomicReference<SessionNetworkPanel> partial = new AtomicReference<>();
    if (isParty) assertSame(failure, assertThrows(AssertionError.class, () -> new PartyPanel(owner) {
      @Override public <T extends Actor> Cell<T> add(T actor) {
        if (actor instanceof LabelButton) { partial.set(this); saved.addAll(actors(this)); saved.add(actor); throw failure; }
        return super.add(actor);
      }
      @Override public void clearListeners() { super.clearListeners(); throw new AssertionError("cleanup"); }
    }));
    else assertSame(failure, assertThrows(AssertionError.class, () -> new TradePanel(owner) {
      @Override public <T extends Actor> Cell<T> add(T actor) {
        if (actor instanceof LabelButton) { partial.set(this); saved.addAll(actors(this)); saved.add(actor); throw failure; }
        return super.add(actor);
      }
      @Override public void clearListeners() { super.clearListeners(); throw new AssertionError("cleanup"); }
    }));
    assertEquals(1, failure.getSuppressed().length); retired(saved); inactive(partial.get());
  }

  @Test void partyRebuildRetiresOldRowsAndKeepsCurrentInviteTradeAndSnapshotParameters() throws Exception {
    PartyPanel panel = (PartyPanel) panel(true); member(20, "Other", PartyRelation.NONE); panel.setVisible(true); panel.act(0);
    Actor oldInvite = button(panel, "party_action_invite"); Array<ClickListener> saved = clicks(Array.with(oldInvite));
    click(oldInvite); verify(sender).requestParty(PartyOperation.INVITE, 20);
    click(button(panel, "trade")); verify(sender).requestTrade(TradeOperation.REQUEST, 20, 0, -1, 0, 0, 0);
    Array<Actor> oldTree = actors((Table) get(panel, "roster")); oldTree.removeIndex(0);
    party.apply(mock(PartyResult.class)); member(21, "Replacement", PartyRelation.INVITED); panel.act(0);
    retired(oldTree); clearInvocations(sender); invoke(saved); verifyNoInteractions(sender);
    click(button(panel, "accept")); verify(sender).requestParty(PartyOperation.ACCEPT, 21);
    retired(oldTree);
    panel.setVisible(false); panel.setVisible(true); panel.act(0); panel.act(0);
    verify(sender).requestParty(PartyOperation.SNAPSHOT, -1);
  }

  @Test void partyRebuildCleansExternallyDetachedButtonsAndDoesNotAccumulateOwners() throws Exception {
    PartyPanel panel = (PartyPanel) panel(true); member(20, "Other", PartyRelation.NONE); panel.setVisible(true); panel.act(0);
    int baseline = ((Array<?>) get(panel, "ownedActors")).size; Actor detached = button(panel, "trade"); Array<ClickListener> saved = clicks(Array.with(detached)); detached.remove();
    clearInvocations(sender); invoke(saved); verifyNoInteractions(sender);
    for (int i = 0; i < 4; i++) { party.apply(mock(PartyResult.class)); member(20, "Other", PartyRelation.NONE); panel.act(0); assertEquals(baseline, ((Array<?>) get(panel, "ownedActors")).size); }
    retired(Array.with(detached)); invoke(saved); verify(sender, never()).requestTrade(anyByte(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyLong());
  }

  @Test void partyRosterAttachmentFailureRollsBackEveryReturnedRowAndCanRetry() throws Exception {
    PartyPanel panel = (PartyPanel) panel(true); Table roster = spy((Table) get(panel, "roster")); set(PartyPanel.class, panel, "roster", roster);
    member(20, "Other", PartyRelation.NONE); panel.setVisible(true); Array<Actor> failed = new Array<>(); Error failure = new AssertionError("row attach");
    doAnswer(call -> { failed.addAll(actors(call.getArgument(0))); throw failure; }).when(roster).add(any(Actor.class));
    assertSame(failure, assertThrows(AssertionError.class, () -> panel.act(0))); retired(failed);
    assertEquals(0, ((Array<?>) get(panel, "rosterActors")).size);
    doCallRealMethod().when(roster).add(any(Actor.class)); panel.act(0); assertFalse(roster.getChildren().isEmpty());
  }

  @Test void partyOfflineRebuildAndLaterNetworkBindingRetireOldMessage() throws Exception {
    PartyPanel panel = new PartyPanel(owner); panels.add(panel); panel.bindWorld(world); panel.setVisible(true); panel.act(0);
    Table roster = (Table) get(panel, "roster"); Actor old = roster.getChild(0); assertEquals("party_multiplayer_only", ((Label) old).getText().toString());
    panel.setNetworkSystems(sender, receiver); panel.act(0); retired(Array.with(old));
    assertEquals("party_no_players", ((Label) roster.getChild(0)).getText().toString());
  }

  @Test void tradeRebuildRetiresBothOfferTreesWithoutMutatingDetachedState() throws Exception {
    TradePanel panel = (TradePanel) panel(false); trade.apply(tradeResult(41, TradeState.TRADING, 9));
    ClientTradeState.Item item = new ClientTradeState.Item(); item.itemId = 77; item.width = 2; item.height = 1; trade.sourceItems().add(item);
    panel.setVisible(true); panel.act(0); Array<Actor> old = new Array<>();
    old.addAll(((Table) get(panel, "sourceOffer")).getChildren()); old.addAll(((Table) get(panel, "targetOffer")).getChildren());
    assertTrue(((Label) old.first()).getText().toString().contains("#77"));
    trade.apply(tradeResult(42, TradeState.CONFIRMED, 9)); panel.act(0); retired(old);
    click(button(panel, "accept")); click(button(panel, "confirm")); click(button(panel, "cancel"));
    for (byte operation : new byte[] {TradeOperation.ACCEPT, TradeOperation.CONFIRM, TradeOperation.CANCEL}) verify(sender).requestTrade(operation, -1, 9, -1, 0, 0, 0);
    verify(owner).setLeftPanel(null); assertEquals(TradeState.CONFIRMED, trade.state()); assertEquals(9, trade.sessionId());
  }

  @Test void tradeOfferAttachmentFailureRollsBackAndCanRetryWithoutNewRevision() throws Exception {
    TradePanel panel = (TradePanel) panel(false); Table target = spy((Table) get(panel, "targetOffer")); set(TradePanel.class, panel, "targetOffer", target);
    panel.setVisible(true); Array<Actor> failed = new Array<>(); Error failure = new AssertionError("offer attach");
    doAnswer(call -> { failed.add(call.getArgument(0)); throw failure; }).when(target).add(any(Actor.class));
    assertSame(failure, assertThrows(AssertionError.class, () -> panel.act(0))); retired(failed);
    assertEquals(0, ((Array<?>) get(panel, "offerActors")).size); assertEquals(0, ((Table) get(panel, "sourceOffer")).getChildren().size);
    doCallRealMethod().when(target).add(any(Actor.class)); panel.act(0); assertEquals(2, target.getChildren().size);
  }

  @Test void tradeResultsOnlyRouteFromTheBorrowedReceiverAndDoNotRequireAlreadyVisiblePanel() {
    TradePanel panel = (TradePanel) panel(false); trade.apply(tradeResult(1, TradeState.INVITED, 9));
    panel.applyNetworkState(mock(ClientNetworkReceiver.class)); verifyNoInteractions(owner);
    panel.applyNetworkState(receiver); verify(owner).openTradePanel(); clearInvocations(owner);
    panel.setVisible(true); trade.apply(tradeResult(2, TradeState.COMPLETED, 9)); panel.applyNetworkState(receiver); verify(owner).setLeftPanel(null);
    clearInvocations(owner); panel.dispose(); panel.applyNetworkState(receiver); verifyNoInteractions(owner);
  }

  @Test void actualOldReceiverPacketCannotOpenOrCloseNewGamePanel() throws Exception {
    TradePanel panel = (TradePanel) panel(false); owner.tradePanel = panel;
    ClientNetworkReceiver oldReceiver = new ClientNetworkReceiver(); Method dispatch = ClientNetworkReceiver.class.getDeclaredMethod("TradeResult", D2GS.class); dispatch.setAccessible(true);
    dispatch.invoke(oldReceiver, tradePacket(1, TradeState.INVITED, 7)); panel.setVisible(true);
    dispatch.invoke(oldReceiver, tradePacket(2, TradeState.COMPLETED, 7));
    verifyNoInteractions(owner); assertEquals(2, oldReceiver.tradeState().revision()); assertEquals(0, trade.revision());
    when(world.getSystem(ClientNetworkReceiver.class)).thenReturn(oldReceiver);
    GameScreen oldOwner = owner; owner = mock(GameScreen.class); Riiablo.game = owner;
    TradePanel current = new TradePanel(owner); panels.add(current); current.bindWorld(world); current.setNetworkSystems(sender, oldReceiver); owner.tradePanel = current;
    dispatch.invoke(oldReceiver, tradePacket(3, TradeState.INVITED, 8)); verify(owner).openTradePanel(); verifyNoInteractions(oldOwner);
  }

  @ParameterizedTest @ValueSource(booleans = {true, false})
  void synchronousSendThatDisposesPanelDoesNotWriteRetiredStatus(boolean isParty) {
    SessionNetworkPanel panel = panel(isParty); panel.setVisible(true);
    if (isParty) { when(sender.requestParty(anyByte(), anyInt())).thenAnswer(call -> { panel.dispose(); return 1L; }); panel.act(0); }
    else { when(sender.requestTrade(anyByte(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyLong())).thenAnswer(call -> { panel.dispose(); return 1L; }); click(button(panel, "confirm")); }
    inactive(panel);
  }

  @ParameterizedTest @ValueSource(booleans = {true, false})
  void assemblyMayBindBeforePublicationButCannotDispatchUntilItsOwnerIsCurrent(boolean isParty) {
    Riiablo.game = null;
    SessionNetworkPanel panel = isParty ? new PartyPanel(owner) : new TradePanel(owner); panels.add(panel);
    panel.bindWorld(world); panel.setNetworkSystems(sender, receiver); panel.setVisible(true);
    assertFalse(panel.isVisible()); panel.act(0); verify(receiver, never()).partyState(); verify(receiver, never()).tradeState();
    Riiablo.game = owner; panel.setVisible(true); assertTrue(panel.isVisible()); panel.act(0);
    if (isParty) verify(sender).requestParty(PartyOperation.SNAPSHOT, -1); else verify(receiver).tradeState();
  }

  @ParameterizedTest @ValueSource(booleans = {true, false})
  void standaloneNoArgumentPanelCapturesItsInitialWorldAndClosesLocally(boolean isParty) {
    Riiablo.game = null;
    SessionNetworkPanel panel = isParty ? new PartyPanel() : new TradePanel(); panels.add(panel);
    panel.setNetworkSystems(sender, receiver); panel.setVisible(true); click(button(panel, "close"));
    assertFalse(panel.isVisible()); verifyNoInteractions(owner);
    if (!isParty) { trade.apply(tradeResult(1, TradeState.INVITED, 9)); ((TradePanel) panel).applyNetworkState(receiver); assertTrue(panel.isVisible()); }
  }

  @SuppressWarnings("unchecked")
  @ParameterizedTest @ValueSource(booleans = {true, false})
  void dynamicCleanupFailureContinuesOtherControlsAndRebuildRetriesSameRevision(boolean isParty) throws Exception {
    SessionNetworkPanel panel = panel(isParty); if (isParty) member(20, "Other", PartyRelation.NONE);
    panel.setVisible(true); panel.act(0);
    Array<Actor> content = (Array<Actor>) get(panel, isParty ? "rosterActors" : "offerActors"); Array<Actor> old = new Array<>(content);
    Actor failure = panel.own(spy(new Actor())); content.add(failure); old.add(failure);
    failure.addListener(new InputListener()); doAnswer(call -> { call.callRealMethod(); throw new AssertionError("dynamic cleanup"); }).when(failure).clearListeners();
    if (isParty) { party.apply(mock(PartyResult.class)); member(21, "New", PartyRelation.NONE); }
    else trade.apply(tradeResult(1, TradeState.TRADING, 9));
    assertEquals("dynamic cleanup", assertThrows(AssertionError.class, () -> panel.act(0)).getMessage());
    retired(old); assertEquals(0, content.size); panel.act(0); assertTrue(content.size > 0); verify(failure).clearListeners();
  }

  @Test void partyFailureReasonAndZeroSendFeedbackRetainExistingLocalization() throws Exception {
    PartyPanel panel = (PartyPanel) panel(true); member(20, "Other", PartyRelation.NONE); panel.setVisible(true); panel.act(0);
    when(sender.requestParty(PartyOperation.INVITE, 20)).thenReturn(0L); click(button(panel, "party_action_invite"));
    Label status = (Label) get(panel, "status"); assertEquals("request_send_failed", status.getText().toString());
    PartyResult result = mock(PartyResult.class); when(result.requestId()).thenReturn(8L); when(result.reason()).thenReturn("HOSTILE_COOLDOWN"); when(result.retryAfterMillis()).thenReturn(1500L);
    party.apply(result); panel.act(0); assertEquals("party_hostile_cooldown", status.getText().toString()); assertEquals(Riiablo.colors.red, status.getColor());
  }

  @Test void tradeFailureAndZeroSendFeedbackRetainExistingLocalization() throws Exception {
    TradePanel panel = (TradePanel) panel(false); panel.setVisible(true); panel.act(0);
    when(sender.requestTrade(anyByte(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyLong())).thenReturn(0L);
    click(button(panel, "confirm")); Label status = (Label) get(panel, "status"); assertEquals("trade_request_send_failed", status.getText().toString());
    TradeResult result = mock(TradeResult.class); when(result.requestId()).thenReturn(8L); when(result.reason()).thenReturn("SESSION_MISMATCH");
    trade.apply(result); panel.act(0); assertEquals("trade_error_session_mismatch", status.getText().toString()); assertEquals(Riiablo.colors.red, status.getColor());
  }

  private SessionNetworkPanel panel(boolean isParty) {
    SessionNetworkPanel panel = isParty ? new PartyPanel(owner) : new TradePanel(owner); panels.add(panel);
    panel.bindWorld(world); panel.setNetworkSystems(sender, receiver); return panel;
  }
  private void member(int id, String name, int relation) {
    ClientPartyState.Member member = new ClientPartyState.Member(); member.entityId = id; member.name = name; member.relation = relation;
    member.partyId = Party.INVALID_ID; member.online = true; member.level = 10; party.members().put(id, member);
  }
  private static D2GS tradePacket(long request, int state, int session) {
    FlatBufferBuilder builder = new FlatBufferBuilder(256); int reason = builder.createString("OK");
    int result = TradeResult.createTradeResult(builder, request, true, reason, TradeOperation.CONFIRM, session, 10, 20, state, 12, 4, 0, 0);
    builder.finish(D2GS.createD2GS(builder, D2GSData.TradeResult, result)); return D2GS.getRootAsD2GS(builder.dataBuffer());
  }
  private static TradeResult tradeResult(long request, int state, int session) { return (TradeResult) tradePacket(request, state, session).data(new TradeResult()); }
  private static Array<Actor> actors(Actor root) { Array<Actor> result = new Array<>(); result.add(root); if (root instanceof Group) for (Actor child : ((Group) root).getChildren()) result.addAll(actors(child)); return result; }
  private static Actor button(Actor root, String text) { for (Actor actor : actors(root)) if (actor instanceof LabelButton && ((Label) actor).getText().toString().equals(text)) return actor; throw new AssertionError("missing " + text); }
  private static Array<ClickListener> clicks(Array<Actor> tree) { Array<ClickListener> result = new Array<>(); for (Actor actor : tree) for (EventListener listener : actor.getListeners()) if (listener instanceof ClickListener) result.add((ClickListener) listener); return result; }
  private static void invoke(Array<ClickListener> clicks) { for (ClickListener click : clicks) click.clicked(null, 0, 0); }
  private static void click(Actor actor) { invoke(clicks(Array.with(actor))); }
  private static void inactive(SessionNetworkPanel panel) { panel.setVisible(true); panel.act(1); panel.draw(null, 1); assertNull(panel.hit(0, 0, true)); }
  private static void retired(Array<Actor> actors) { for (Actor actor : actors) { assertNull(actor.getParent()); assertEquals(0, actor.getListeners().size); assertEquals(0, actor.getCaptureListeners().size); assertEquals(0, actor.getActions().size); } }
  private static Object get(Object target, String name) throws Exception { for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) { try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target); } catch (NoSuchFieldException ignored) {} } throw new NoSuchFieldException(name); }
  private static void set(Class<?> type, Object target, String name, Object value) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value); }
}
