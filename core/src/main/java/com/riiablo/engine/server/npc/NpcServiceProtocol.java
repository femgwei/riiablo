package com.riiablo.engine.server.npc;

import com.badlogic.gdx.math.Vector2;

/** Shared validation rules for the multiplayer NPC service boundary. */
public final class NpcServiceProtocol {
  public static final float MAX_INTERACTION_DISTANCE_SQ = 64f;

  public enum Service { TRADE, GAMBLE, REPAIR, HIRE, RESURRECT }
  public enum Operation { OPEN, BUY, SELL, REPAIR_ITEM, REPAIR_ALL, HIRE, RESURRECT }

  private NpcServiceProtocol() {}

  public static boolean inRange(Vector2 player, Vector2 npc) {
    return player != null && npc != null && player.dst2(npc) <= MAX_INTERACTION_DISTANCE_SQ;
  }

  public static boolean supports(Service service, Operation operation) {
    if (service == null || operation == null) return false;
    switch (service) {
      case TRADE: return operation == Operation.OPEN || operation == Operation.BUY || operation == Operation.SELL;
      case GAMBLE: return operation == Operation.OPEN || operation == Operation.BUY;
      case REPAIR: return operation == Operation.OPEN || operation == Operation.REPAIR_ITEM || operation == Operation.REPAIR_ALL;
      case HIRE: return operation == Operation.OPEN || operation == Operation.HIRE;
      case RESURRECT: return operation == Operation.OPEN || operation == Operation.RESURRECT;
      default: return false;
    }
  }

  public static String rejectReason(boolean connected, boolean npcExists, boolean near,
                                    boolean allowed, boolean revisionMatches) {
    if (!connected) return "NOT_CONNECTED";
    if (!npcExists) return "UNKNOWN_NPC";
    if (!near) return "OUT_OF_RANGE";
    if (!allowed) return "SERVICE_NOT_AVAILABLE";
    if (!revisionMatches) return "STALE_STOCK";
    return null;
  }
}
