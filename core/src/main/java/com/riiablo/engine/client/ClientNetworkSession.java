package com.riiablo.engine.client;

import com.riiablo.Riiablo;
import com.riiablo.save.CharData;
import com.riiablo.net.packet.d2gs.NpcServiceResult;
import java.util.Objects;
import java.util.function.IntSupplier;

/** Explicit identity for one networked client session. */
public final class ClientNetworkSession {
  private final CharData character;
  private final IntSupplier player;
  private final ResultRouter results;

  public ClientNetworkSession(CharData character, IntSupplier player) {
    this(character, player, null);
  }

  public ClientNetworkSession(CharData character, IntSupplier player, ResultRouter results) {
    this.character = Objects.requireNonNull(character, "character");
    this.player = Objects.requireNonNull(player, "player");
    this.results = results;
  }

  public CharData character() { return character; }
  public int player() { return player.getAsInt(); }
  public ResultRouter results() { return results; }

  public interface ResultRouter {
    void onNpcService(NpcServiceResult result);
    void onTradeState(ClientNetworkReceiver receiver);
  }

  /** Compatibility only for direct unit fixtures that bypass Artemis injection. */
  static ClientNetworkSession fromGlobals() {
    return Riiablo.charData == null ? null
        : new ClientNetworkSession(Riiablo.charData,
            () -> Riiablo.game == null ? com.riiablo.engine.Engine.INVALID_ENTITY : Riiablo.game.player);
  }
}
