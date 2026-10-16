package com.riiablo.engine.server.ai;

/**
 * D2MOO Fn080 and Fn092 deliberately use the same TrapPoison_TrapNova function.
 * The native Skill1 and mode come from each trap's own MonStats row.
 */
public class TrapPoison extends TrapNova {
  public TrapPoison(int entityId) { super(entityId); }
}
