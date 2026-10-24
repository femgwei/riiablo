package com.riiablo.engine.client;

import com.riiablo.Files;
import com.riiablo.Cursor;
import com.riiablo.net.packet.d2gs.NpcServiceResult;
import com.riiablo.save.CharData;

/** Presentation-owned facts and result sinks used by the network replica system. */
public interface ClientNetworkPresentationPort {
  String format(int stringId, Object... args);
  Files files();
  CharData charData();
  int playerId();
  Cursor cursor();
  void presentNpcServiceResult(NpcServiceResult result);
  void presentTradeState(ClientNetworkReceiver receiver);
}
