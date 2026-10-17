package com.riiablo.engine.server.party;

/**
 * Authoritative target policy shared by melee, skills and missiles.
 *
 * <p>D2MOO treats monsters as hostile to players by default, while player
 * units are friendly until one side opens hostility.  Party membership always
 * takes precedence over a stale relation entry.</p>
 */
public final class PvpCombatRules {
  private PvpCombatRules() {}

  public static boolean canDamage(PartyManager parties, int attackerId, int targetId,
                                  boolean attackerPlayer, boolean targetPlayer) {
    if (attackerId < 0 || targetId < 0 || attackerId == targetId) return false;
    if (attackerPlayer != targetPlayer) return true;
    if (!attackerPlayer) return false; // monster vs monster
    if (parties == null) return false;
    return parties.areHostile(attackerId, targetId);
  }

  public static boolean canTarget(PartyManager parties, int attackerId, int targetId,
                                  boolean attackerPlayer, boolean targetPlayer) {
    return canDamage(parties, attackerId, targetId, attackerPlayer, targetPlayer);
  }

  /** D2MOO PARTYSCREEN_ToggleHostile declaration gate. */
  public static boolean canDeclareHostility(int sourceLevel, int targetLevel,
                                            boolean sourceInTown) {
    return sourceLevel >= 9 && targetLevel >= 9 && sourceInTown;
  }
}
