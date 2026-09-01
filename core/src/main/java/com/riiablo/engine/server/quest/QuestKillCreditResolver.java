package com.riiablo.engine.server.quest;

import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.badlogic.gdx.utils.IntArray;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.party.PartyManager;

/**
 * @deprecated Use {@link com.riiablo.engine.server.KillCreditResolver}. Kept
 * as a source-compatible adapter for older map/quest integrations.
 */
@Deprecated
public final class QuestKillCreditResolver {
  private final com.riiablo.engine.server.KillCreditResolver delegate;

  public QuestKillCreditResolver(ComponentMapper<Player> players,
      ComponentMapper<Mercenary> mercenaries, ComponentMapper<MapWrapper> maps,
      PartyManager parties) {
    delegate = new com.riiablo.engine.server.KillCreditResolver(
        players, mercenaries, maps, null, parties);
  }

  public int ownerOf(int killerId) {
    return delegate.ownerOf(killerId);
  }

  public IntArray eligiblePlayers(int killerId, int levelId,
      EntitySubscription playerSubscription) {
    return delegate.eligiblePlayers(killerId, levelId, playerSubscription);
  }
}
