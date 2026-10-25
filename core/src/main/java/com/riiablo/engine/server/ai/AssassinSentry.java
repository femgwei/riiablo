package com.riiablo.engine.server.ai;

/**
 * Rooted AI marker for the Assassin trap summon rows.
 *
 * <p>The native AssassinSentry/DeathSentry routines are split in riiablo:
 * {@code AssassinTrapSystem} owns target selection, shot cadence, helper
 * missiles, corpse consumption, and lifetime.  The monster AI slot still
 * needs a concrete implementation so {@link AI#findAI(int, String)} does not
 * fall back to {@link GenericMonster}, which would both log a false fallback
 * and risk competing with the trap system.  This class therefore only keeps
 * the rooted controller inert; all attack behavior remains authoritative in
 * {@code AssassinTrapSystem}.
 */
public class AssassinSentry extends AI {
  private String state = "TRAP";

  @Override void writeRuntime(java.io.DataOutputStream out) throws java.io.IOException {
    super.writeRuntime(out);
    com.riiablo.engine.replay.CheckpointIO.writeString(out, state);
  }
  @Override void readRuntime(java.io.DataInputStream in, boolean apply) throws java.io.IOException {
    super.readRuntime(in, apply);
    String savedState = com.riiablo.engine.replay.CheckpointIO.readString(in);
    if (apply) {
      state = savedState;
    }
  }

  public AssassinSentry(int entityId) {
    super(entityId);
  }

  @Override
  public void update(float delta) {
    if (monster == null || !mPosition.has(entityId)) return;
    // A trap never chases or emits a generic melee attack.  Clear any stale
    // path left by a live reload and let AssassinTrapSystem drive the native
    // SrvDo044/SrvDo045 attack transaction.
    stopMovement();
    state = "TRAP";
  }

  @Override
  public String getState() {
    return state;
  }
}
