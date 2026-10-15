package com.riiablo.engine.server.npc;

/** Read-only authoritative candidate snapshot shared by local and network UI. */
public final class HirelingOffer {
  public int index, seed, mercType, hirelingId, nameId, level, life, defense, cost;
  public String description;
}
