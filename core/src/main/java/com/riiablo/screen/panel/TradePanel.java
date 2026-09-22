package com.riiablo.screen.panel;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;

import com.riiablo.Riiablo;
import com.riiablo.engine.client.ClientNetworkReceiver;
import com.riiablo.engine.client.ClientNetworkSynchronizer;
import com.riiablo.engine.client.ClientTradeState;
import com.riiablo.engine.server.trade.TradeState;
import com.riiablo.graphics.PaletteIndexedColorDrawable;
import com.riiablo.net.packet.d2gs.TradeOperation;
import com.riiablo.widget.Label;
import com.riiablo.widget.LabelButton;

/** Basic network-backed player trade window. */
public class TradePanel extends Table {
  private static final float WIDTH = 360;
  private static final float HEIGHT = 420;

  private final Table sourceOffer = new Table();
  private final Table targetOffer = new Table();
  private final Label status = new Label("", Riiablo.fonts.fontformal10, Riiablo.colors.grey);
  private ClientNetworkSynchronizer synchronizer;
  private ClientNetworkReceiver receiver;
  private long displayedRevision = Long.MIN_VALUE;
  private long displayedRequestId;

  public TradePanel() {
    setSize(WIDTH, HEIGHT);
    setTouchable(Touchable.enabled);
    setBackground(new PaletteIndexedColorDrawable(Riiablo.colors.modal75));
    pad(12);
    defaults().growX();

    Label title = new Label(text("trade_title"), Riiablo.fonts.font16, Riiablo.colors.gold);
    title.setAlignment(Align.center);
    add(title).height(28).row();

    Table offers = new Table();
    offers.defaults().top().pad(4);
    offers.add(offerColumn(text("party_you"), sourceOffer)).width(160).top();
    offers.add(offerColumn(text("trade_other_player"), targetOffer)).width(160).top();
    add(offers).grow().row();

    status.setAlignment(Align.center);
    status.setWrap(true);
    add(status).height(28).padTop(4).row();

    Table actions = new Table();
    actions.add(actionButton(text("accept"), () -> request(TradeOperation.ACCEPT))).pad(2);
    actions.add(actionButton(text("confirm"), () -> request(TradeOperation.CONFIRM))).pad(2);
    actions.add(actionButton(text("cancel"), () -> request(TradeOperation.CANCEL))).pad(2);
    add(actions).height(24).row();
    add(actionButton(text("close"), () -> close())).height(20).center();
    setVisible(false);
  }

  private Table offerColumn(String title, Table content) {
    Table column = new Table();
    Label header = new Label(title, Riiablo.fonts.fontformal11, Riiablo.colors.gold);
    header.setAlignment(Align.center);
    column.add(header).height(22).row();
    column.add(content).grow().top().row();
    return column;
  }

  public void setNetworkSystems(ClientNetworkSynchronizer synchronizer,
                                ClientNetworkReceiver receiver) {
    this.synchronizer = synchronizer;
    this.receiver = receiver;
    displayedRevision = Long.MIN_VALUE;
  }

  @Override
  public void setVisible(boolean visible) {
    super.setVisible(visible);
    if (visible) displayedRevision = Long.MIN_VALUE;
  }

  @Override
  public void act(float delta) {
    super.act(delta);
    if (!isVisible() || receiver == null) return;
    ClientTradeState state = receiver.tradeState();
    if (state.revision() != displayedRevision) rebuild(state);
    if (state.lastRequestId() != 0 && state.lastRequestId() != displayedRequestId) {
      displayedRequestId = state.lastRequestId();
      status.setColor(state.lastSuccess() ? Riiablo.colors.green : Riiablo.colors.red);
      status.setText(state.lastSuccess() ? localizedStateLabel(state.state())
          : localizedFailure(state.lastReason()));
    }
  }

  private void rebuild(ClientTradeState state) {
    displayedRevision = state.revision();
    sourceOffer.clearChildren();
    targetOffer.clearChildren();
    addItems(sourceOffer, state.sourceItems());
    addItems(targetOffer, state.targetItems());
    sourceOffer.add(new Label(format("trade_gold", state.sourceGold()),
        Riiablo.fonts.fontformal10, Riiablo.colors.gold)).left().row();
    targetOffer.add(new Label(format("trade_gold", state.targetGold()),
        Riiablo.fonts.fontformal10, Riiablo.colors.gold)).left().row();
    status.setText(localizedStateLabel(state.state()));
  }

  private static void addItems(Table target, com.badlogic.gdx.utils.Array<ClientTradeState.Item> items) {
    if (items.size == 0) {
      target.add(new Label(text("empty"), Riiablo.fonts.fontformal10, Riiablo.colors.grey)).left().row();
      return;
    }
    for (ClientTradeState.Item item : items) {
      target.add(new Label("#" + item.itemId + "  " + item.width + "x" + item.height
          + " @" + item.x + "," + item.y,
          Riiablo.fonts.fontformal10, Riiablo.colors.white)).left().row();
    }
  }

  private void request(byte operation) {
    if (synchronizer == null || receiver == null) return;
    ClientTradeState state = receiver.tradeState();
    long requestId = synchronizer.requestTrade(operation, -1, state.sessionId(),
        -1, 0, 0, 0);
    status.setColor(requestId == 0 ? Riiablo.colors.red : Riiablo.colors.gold);
    status.setText(requestId == 0 ? text("trade_request_send_failed") : text("request_sent"));
    if (operation == TradeOperation.CANCEL) close();
  }

  private void close() {
    if (Riiablo.game != null) Riiablo.game.setLeftPanel(null);
    else setVisible(false);
  }

  private LabelButton actionButton(String text, final Runnable action) {
    LabelButton button = new LabelButton(text, Riiablo.fonts.fontformal10, Riiablo.colors.white);
    button.addListener(new ClickListener() {
      @Override public void clicked(InputEvent event, float x, float y) { action.run(); }
    });
    return button;
  }

  private static String localizedStateLabel(int state) {
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

  private static String localizedFailure(String reason) {
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

  private static String text(String key) {
    return Riiablo.bundle.get(key);
  }

  private static String format(String key, Object... args) {
    return Riiablo.bundle.format(key, args);
  }
}
