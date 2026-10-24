package com.riiablo.presentation;

import java.util.Objects;

import com.riiablo.Cursor;
import com.riiablo.Files;
import com.riiablo.engine.client.ClientNetworkPresentationPort;
import com.riiablo.engine.client.ClientNetworkReceiver;
import com.riiablo.net.packet.d2gs.NpcServiceResult;
import com.riiablo.save.CharData;

/** Bridges network replica presentation facts and result delivery into the host context. */
public final class ClientNetworkPresentationAdapter implements ClientNetworkPresentationPort {
  private final ClientGameplayContext context;

  public ClientNetworkPresentationAdapter(ClientGameplayContext context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  @Override public String format(int stringId, Object... args) {
    return context.strings() == null ? null : context.strings().format(stringId, args);
  }

  @Override public Files files() { return context.files(); }
  @Override public CharData charData() { return context.charData(); }
  @Override public int playerId() { return context.playerId(); }
  @Override public Cursor cursor() { return context.cursor(); }
  @Override public void presentNpcServiceResult(NpcServiceResult result) {
    context.presentNpcServiceResult(result);
  }
  @Override public void presentTradeState(ClientNetworkReceiver receiver) {
    context.presentTradeState(receiver);
  }
}
