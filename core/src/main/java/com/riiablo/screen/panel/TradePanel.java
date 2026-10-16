package com.riiablo.screen.panel;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;

import com.riiablo.Riiablo;
import com.riiablo.engine.client.ClientNetworkReceiver;
import com.riiablo.engine.client.ClientTradeState;
import com.riiablo.engine.server.trade.TradeState;
import com.riiablo.graphics.PaletteIndexedColorDrawable;
import com.riiablo.net.packet.d2gs.TradeOperation;
import com.riiablo.widget.Label;
import com.riiablo.screen.GameScreen;
import com.riiablo.util.Disposal;

/** Basic network-backed player trade window. */
public class TradePanel extends SessionNetworkPanel {
  private static final float WIDTH = 360;
  private static final float HEIGHT = 420;

  private Table sourceOffer;
  private Table targetOffer;
  private Label status;
  private final Array<Actor> offerActors = new Array<>();
  private long displayedRevision = Long.MIN_VALUE;
  private long displayedRequestId;

  public TradePanel() {
    this(Riiablo.game);
  }

  public TradePanel(GameScreen owner) {
    super(owner);
    try { initialize(); }
    catch (RuntimeException | Error failure) { constructionFailed(failure); throw failure; }
  }

  private void initialize() {
    sourceOffer = table();
    targetOffer = table();
    status = label("", fonts.fontformal10, colors.grey);
    setSize(WIDTH, HEIGHT);
    setTouchable(Touchable.enabled);
    setBackground(new PaletteIndexedColorDrawable(colors.modal75));
    pad(12);
    defaults().growX();

    Label title = label(text("trade_title"), fonts.font16, colors.gold);
    title.setAlignment(Align.center);
    add(title).height(28).row();

    Table offers = table();
    offers.defaults().top().pad(4);
    offers.add(offerColumn(text("party_you"), sourceOffer)).width(160).top();
    offers.add(offerColumn(text("trade_other_player"), targetOffer)).width(160).top();
    add(offers).grow().row();

    status.setAlignment(Align.center);
    status.setWrap(true);
    add(status).height(28).padTop(4).row();

    Table actions = table();
    actions.add(actionButton(text("accept"), () -> request(TradeOperation.ACCEPT))).pad(2);
    actions.add(actionButton(text("confirm"), () -> request(TradeOperation.CONFIRM))).pad(2);
    actions.add(actionButton(text("cancel"), () -> request(TradeOperation.CANCEL))).pad(2);
    add(actions).height(24).row();
    add(actionButton(text("close"), () -> close())).height(20).center();
    setVisible(false);
  }

  private Table offerColumn(String title, Table content) {
    Table column = table();
    Label header = label(title, fonts.fontformal11, colors.gold);
    header.setAlignment(Align.center);
    column.add(header).height(22).row();
    column.add(content).grow().top().row();
    return column;
  }

  @Override
  public void setVisible(boolean visible) {
    if (visible && !isCurrentSession()) return;
    super.setVisible(visible);
    if (visible) displayedRevision = Long.MIN_VALUE;
  }

  @Override
  public void act(float delta) {
    if (!isCurrentSession()) return;
    super.act(delta);
    if (!isInteractive() || receiver == null) return;
    ClientTradeState state = receiver.tradeState();
    if (state.revision() != displayedRevision) rebuild(state);
    if (state.lastRequestId() != 0 && state.lastRequestId() != displayedRequestId) {
      displayedRequestId = state.lastRequestId();
      status.setColor(state.lastSuccess() ? colors.green : colors.red);
      status.setText(state.lastSuccess() ? localizedStateLabel(state.state())
          : localizedFailure(state.lastReason()));
    }
  }

  private void rebuild(ClientTradeState state) {
    retireOffers();
    try { buildOffers(state); displayedRevision = state.revision(); }
    catch (RuntimeException | Error failure) {
      try { retireOffers(); }
      catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
      throw failure;
    }
  }

  private void buildOffers(ClientTradeState state) {
    addItems(sourceOffer, state.sourceItems());
    addItems(targetOffer, state.targetItems());
    sourceOffer.add(content(label(format("trade_gold", state.sourceGold()),
        fonts.fontformal10, colors.gold))).left().row();
    targetOffer.add(content(label(format("trade_gold", state.targetGold()),
        fonts.fontformal10, colors.gold))).left().row();
    status.setText(localizedStateLabel(state.state()));
  }

  private void addItems(Table target, Array<ClientTradeState.Item> items) {
    if (items.size == 0) {
      target.add(content(label(text("empty"), fonts.fontformal10, colors.grey))).left().row();
      return;
    }
    for (ClientTradeState.Item item : items) {
      target.add(content(label("#" + item.itemId + "  " + item.width + "x" + item.height
          + " @" + item.x + "," + item.y,
          fonts.fontformal10, colors.white))).left().row();
    }
  }

  private void request(byte operation) {
    if (!isInteractive() || synchronizer == null || receiver == null) return;
    ClientTradeState state = receiver.tradeState();
    long requestId = synchronizer.requestTrade(operation, -1, state.sessionId(),
        -1, 0, 0, 0);
    if (!isInteractive()) return;
    status.setColor(requestId == 0 ? colors.red : colors.gold);
    status.setText(requestId == 0 ? text("trade_request_send_failed") : text("request_sent"));
    if (operation == TradeOperation.CANCEL) close();
  }

  private void close() {
    if (!isCurrentSession()) return;
    if (owner != null) owner.setLeftPanel(null);
    else setVisible(false);
  }

  /** Only the receiver borrowed from this panel's world may open/close this session's window. */
  public void applyNetworkState(ClientNetworkReceiver source) {
    if (!isCurrentSession() || receiver == null || source != receiver) return;
    if (receiver.tradeState().active()) {
      if (owner != null) owner.openTradePanel();
      else setVisible(true);
    } else if (isVisible()) close();
  }

  private <T extends Actor> T content(T actor) { offerActors.add(actor); return actor; }

  private void retireOffers() {
    displayedRevision = Long.MIN_VALUE;
    try (Disposal cleanup = new Disposal()) {
      cleanup.run(() -> retireActors(offerActors));
      if (sourceOffer != null) cleanup.run(sourceOffer::clearChildren);
      if (targetOffer != null) cleanup.run(targetOffer::clearChildren);
    }
  }

  @Override protected void clearView() {
    try { retireOffers(); }
    finally { sourceOffer = null; targetOffer = null; status = null; displayedRequestId = 0; }
  }

  private String localizedStateLabel(int state) {
    switch (state) {
      case TradeState.PENDING: return text("trade_state_pending");
      case TradeState.INVITED: return text("trade_state_invited");
      case TradeState.TRADING: return text("trade_state_trading");
      case TradeState.CONFIRMED: return text("trade_state_confirmed");
      case TradeState.COMPLETED: return text("trade_state_completed");
      case TradeState.CANCELLED: return text("trade_state_cancelled");
      default: return text("trade_state_none");
    }
  }

  private String localizedFailure(String reason) {
    if (reason == null || reason.isEmpty()) return text("trade_request_rejected");
    switch (reason) {
      case "UNAUTHENTICATED_OR_INVALID_OPERATION": return text("trade_error_invalid_operation");
      case "REQUEST_ID_REUSED": return text("trade_error_request_reused");
      case "NO_ACTIVE_SESSION": return text("trade_error_no_session");
      case "SESSION_MISMATCH": return text("trade_error_session_mismatch");
      case "PLAYER_OFFLINE": return text("trade_error_player_offline");
      case "SELF_TRADE": return text("trade_error_self_trade");
      case "PLAYER_NOT_IN_AREA": return text("trade_error_not_in_area");
      case "DIFFERENT_AREA": return text("trade_error_different_area");
      case "TOO_FAR":
      case "Too Far": return text("trade_error_too_far");
      case "TRADE_INVALIDATED": return text("trade_error_invalidated");
      case "Target Busy": return text("trade_error_target_busy");
      case "No Space": return text("trade_error_no_space");
      case "No Gold": return text("trade_error_no_gold");
      case "Cancelled": return text("trade_state_cancelled");
      case "Item Not Tradable": return text("trade_error_item_not_tradable");
      case "Timeout": return text("trade_error_timeout");
      case "Error": return text("trade_error_generic");
      default: return format("trade_request_rejected_reason", reason);
    }
  }

}
