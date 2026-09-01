package com.riiablo.engine.server.quest;

import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntArray;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.party.Party;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.engine.server.party.PartyMember;

/** Resolves a native kill to the player that owns the acting unit. */
public final class QuestKillCreditResolver {
  private final ComponentMapper<Player> players;
  private final ComponentMapper<Mercenary> mercenaries;
  private final ComponentMapper<MapWrapper> maps;
  private final PartyManager parties;

  public QuestKillCreditResolver(ComponentMapper<Player> players,
      ComponentMapper<Mercenary> mercenaries, ComponentMapper<MapWrapper> maps,
      PartyManager parties) {
    this.players = players;
    this.mercenaries = mercenaries;
    this.maps = maps;
    this.parties = parties;
  }

  /** Returns the authoritative player owner, or -1 when the unit is unowned. */
  public int ownerOf(int killerId) {
    if (killerId < 0) return -1;
    if (players != null && players.has(killerId)
        && players.get(killerId) != null && players.get(killerId).data != null) return killerId;
    if (mercenaries != null && mercenaries.has(killerId)) {
      int owner = mercenaries.get(killerId).ownerId;
      if (players != null && owner >= 0 && players.has(owner)
          && players.get(owner) != null && players.get(owner).data != null) return owner;
    }
    return -1;
  }

  /**
   * Returns the owner and eligible same-level party members. The result is
   * deterministic and contains no duplicate entity ids.
   */
  public IntArray eligiblePlayers(int killerId, int levelId,
      EntitySubscription playerSubscription) {
    IntArray result = new IntArray();
    int owner = ownerOf(killerId);
    if (owner < 0 || playerSubscription == null) return result;
    // The authoritative killer receives credit even when a synthetic/test
    // entity has no MapWrapper yet; live entities are normally level-bound.
    if (players.has(owner) && players.get(owner) != null && players.get(owner).data != null) {
      result.add(owner);
    }
    if (parties == null) return result;
    short partyId = parties.getPartyId(owner);
    if (partyId == Party.INVALID_ID) return result;
    Party party = parties.getParty(partyId);
    if (party == null) return result;
    IntBag ids = playerSubscription.getEntities();
    int[] data = ids.getData();
    for (int i = 0; i < ids.size(); i++) {
      int id = data[i];
      if (id == owner || !party.hasMember(id)) continue;
      PartyMember member = party.getMember(id);
      if (member == null || !member.online || !member.alive) continue;
      addIfEligible(result, id, levelId);
    }
    return result;
  }

  private void addIfEligible(IntArray result, int id, int levelId) {
    if (players == null || !players.has(id) || players.get(id) == null
        || players.get(id).data == null || !sameLevel(id, levelId)) return;
    if (!result.contains(id)) result.add(id);
  }

  private boolean sameLevel(int id, int levelId) {
    if (maps == null || !maps.has(id)) return false;
    MapWrapper wrapper = maps.get(id);
    return wrapper != null && wrapper.zone != null && wrapper.zone.level != null
        && wrapper.zone.level.Id == levelId;
  }
}
