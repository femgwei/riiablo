package com.riiablo.engine.server.ai;

/**
 * Marker AI for the native {@code Hireable} MonStats rows.
 *
 * <p>Player-owned hirelings are driven by MercenaryFollowSystem and
 * MercenarySkillSystem after their Mercenary component is installed. Keeping
 * this AI deliberately passive prevents the entity factory's short creation
 * window from substituting hostile GenericMonster behavior.</p>
 */
public final class Hireable extends AI {
  public Hireable(int entityId) {
    super(entityId);
  }

  @Override
  public void update(float delta) {
    // Authoritative owner-follow and combat decisions live in the dedicated
    // mercenary systems so they execute once per fixed simulation tick.
  }

  @Override
  public String getState() {
    return "HIREABLE";
  }
}
