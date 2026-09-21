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

    Label title = new Label("Player Trade", Riiablo.fonts.font16, Riiablo.colors.gold);
    title.setAlignment(Align.center);
    add(title).height(28).row();

    Table offers = new Table();
    offers.defaults().top().pad(4);
    offers.add(offerColumn("You", sourceOffer)).width(160).top();
    offers.add(offerColumn("Other Player", targetOffer)).width(160).top();
    add(offers).grow().row();

    status.setAlignment(Align.center);
    status.setWrap(true);
    add(status).height(28).padTop(4).row();

    Table actions = new Table();
    actions.add(actionButton("Accept", () -> request(TradeOperation.ACCEPT))).pad(2);
    actions.add(actionButton("Confirm", () -> request(TradeOperation.CONFIRM))).pad(2);
    actions.add(actionButton("Cancel", () -> request(TradeOperation.CANCEL))).pad(2);
    add(actions).height(24).row();
    add(actionButton("Close", () -> close())).height(20).center();
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
      status.setText(state.lastSuccess() ? stateLabel(state.state()) : humanize(state.lastReason()));
    }
  }

  private void rebuild(ClientTradeState state) {
    displayedRevision = state.revision();
    sourceOffer.clearChildren();
    targetOffer.clearChildren();
    addItems(sourceOffer, state.sourceItems());
    addItems(targetOffer, state.targetItems());
    sourceOffer.add(new Label("Gold: " + state.sourceGold(),
        Riiablo.fonts.fontformal10, Riiablo.colors.gold)).left().row();
    targetOffer.add(new Label("Gold: " + state.targetGold(),
        Riiablo.fonts.fontformal10, Riiablo.colors.gold)).left().row();
    status.setText(stateLabel(state.state()));
  }

  private static void addItems(Table target, com.badlogic.gdx.utils.Array<ClientTradeState.Item> items) {
    if (items.size == 0) {
      target.add(new Label("Empty", Riiablo.fonts.fontformal10, Riiablo.colors.grey)).left().row();
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
    status.setText(requestId == 0 ? "Unable to send trade request" : "Request sent...");
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

  private static String stateLabel(int state) {
    switch (state) {
      case TradeState.PENDING: return "Waiting for acceptance";
      case TradeState.INVITED: return "Trade invitation";
      case TradeState.TRADING: return "Trading";
      case TradeState.CONFIRMED: return "Both players confirmed";
      case TradeState.COMPLETED: return "Trade completed";
      case TradeState.CANCELLED: return "Trade cancelled";
      default: return "No active trade";
    }
  }

  private static String humanize(String reason) {
    if (reason == null || reason.isEmpty()) return "Trade request rejected";
    String text = reason.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
    return Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }
}
