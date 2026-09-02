package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntArray;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.party.Party;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.engine.server.party.PartyMember;

/** Resolves reward ownership and native same-level party eligibility. */
public final class KillCreditResolver {
  /** D2Game {@code SUNITDMG_PartyCallback_ComputePartyExperience}: 80 squared. */
  public static final float EXPERIENCE_RANGE_SQUARED = 6400f;

  private final ComponentMapper<Player> players;
  private final ComponentMapper<Mercenary> mercenaries;
  private final ComponentMapper<SummonedPet> summonedPets;
  private final ComponentMapper<MapWrapper> maps;
  private final ComponentMapper<Position> positions;
  private final PartyManager parties;

  public KillCreditResolver(ComponentMapper<Player> players,
      ComponentMapper<Mercenary> mercenaries, ComponentMapper<MapWrapper> maps,
      ComponentMapper<Position> positions, PartyManager parties) {
    this(players, mercenaries, null, maps, positions, parties);
  }

  public KillCreditResolver(ComponentMapper<Player> players,
      ComponentMapper<Mercenary> mercenaries, ComponentMapper<SummonedPet> summonedPets,
      ComponentMapper<MapWrapper> maps, ComponentMapper<Position> positions,
      PartyManager parties) {
    this.players = players;
    this.mercenaries = mercenaries;
    this.summonedPets = summonedPets;
    this.maps = maps;
    this.positions = positions;
    this.parties = parties;
  }

  /** Returns the authoritative player owner, or -1 when the unit is unowned. */
  public int ownerOf(int killerId) {
    if (killerId < 0) return -1;
    if (validPlayer(killerId)) return killerId;
    if (mercenaries != null && mercenaries.has(killerId)) {
      int owner = mercenaries.get(killerId).ownerId;
      if (validPlayer(owner)) return owner;
    }
    if (summonedPets != null && summonedPets.has(killerId)) {
      int owner = summonedPets.get(killerId).ownerId;
      if (validPlayer(owner)) return owner;
    }
    return -1;
  }

  /**
   * Returns the owner and eligible same-level party members for quest and loot
   * credit. The result is deterministic and contains no duplicate ids.
   */
  public IntArray eligiblePlayers(int killerId, int levelId,
      EntitySubscription playerSubscription) {
    IntArray result = new IntArray();
    int owner = ownerOf(killerId);
    if (owner < 0 || playerSubscription == null) return result;
    // Synthetic tests and players entering a level may briefly have no map
    // component. Direct ownership must still be retained.
    result.add(owner);
    appendPartyMembers(result, owner, levelId, playerSubscription, -1, -1f);
    return result;
  }

  /**
   * Mirrors D2Game party XP eligibility: online, alive, in the victim's level,
   * and no farther than 80 subtiles from the victim.
   */
  public IntArray eligibleExperiencePlayers(int killerId, int victimId, int levelId,
      EntitySubscription playerSubscription) {
    IntArray result = new IntArray();
    int owner = ownerOf(killerId);
    if (owner < 0 || playerSubscription == null) return result;
    appendPartyMembers(result, owner, levelId, playerSubscription, victimId,
        EXPERIENCE_RANGE_SQUARED);
    return result;
  }

  private void appendPartyMembers(IntArray result, int owner, int levelId,
      EntitySubscription playerSubscription, int victimId, float rangeSquared) {
    if (parties == null) return;
    short partyId = parties.getPartyId(owner);
    if (partyId == Party.INVALID_ID) return;
    Party party = parties.getParty(partyId);
    if (party == null) return;
    IntBag ids = playerSubscription.getEntities();
    int[] data = ids.getData();
    for (int i = 0; i < ids.size(); i++) {
      int id = data[i];
      if (!party.hasMember(id)) continue;
      PartyMember member = party.getMember(id);
      if (member == null || !member.online || !member.alive
          || !validPlayer(id) || !sameLevel(id, levelId)) continue;
      if (rangeSquared >= 0f && !withinRange(id, victimId, rangeSquared)) continue;
      if (!result.contains(id)) result.add(id);
    }
  }

  private boolean validPlayer(int id) {
    return players != null && id >= 0 && players.has(id)
        && players.get(id) != null && players.get(id).data != null;
  }

  private boolean sameLevel(int id, int levelId) {
    if (maps == null || !maps.has(id)) return false;
    MapWrapper wrapper = maps.get(id);
    return wrapper != null && wrapper.zone != null && wrapper.zone.level != null
        && wrapper.zone.level.Id == levelId;
  }

  private boolean withinRange(int playerId, int victimId, float rangeSquared) {
    if (positions == null || !positions.has(playerId) || !positions.has(victimId)) return false;
    Vector2 player = positions.get(playerId).position;
    Vector2 victim = positions.get(victimId).position;
    return player != null && victim != null && player.dst2(victim) <= rangeSquared;
  }

  static boolean withinExperienceRange(float playerX, float playerY,
      float victimX, float victimY) {
    float dx = playerX - victimX;
    float dy = playerY - victimY;
    return dx * dx + dy * dy <= EXPERIENCE_RANGE_SQUARED;
  }
}
