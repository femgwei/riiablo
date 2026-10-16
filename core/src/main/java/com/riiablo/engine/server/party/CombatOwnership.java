package com.riiablo.engine.server.party;

import com.artemis.World;
import com.badlogic.gdx.utils.IntSet;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.NativeTargetNode;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.SummonedPet;

/** ECS owner projection for D2MOO sub_6FCBD900 (5be5503).
 * Source ownership is one hop; only the target follows the monster owner chain. */
public final class CombatOwnership {
  private CombatOwnership() {}

  private static boolean active(World world, int id) {
    if (id < 0) return false;
    // Artemis isActive uses BitVector.unsafeGet and exposes no capacity check.
    try { return world.getEntityManager().isActive(id); }
    catch (IndexOutOfBoundsException invalidOwner) { return false; }
  }

  private static boolean monster(World world, int id) {
    return world.getMapper(Monster.class).has(id)
        || world.getMapper(Mercenary.class).has(id)
        || world.getMapper(SummonedPet.class).has(id);
  }

  private static int owner(World world, int id) {
    if (world.getMapper(Mercenary.class).has(id)) return world.getMapper(Mercenary.class).get(id).ownerId;
    if (world.getMapper(SummonedPet.class).has(id)) return world.getMapper(SummonedPet.class).get(id).ownerId;
    Monster monster = world.getMapper(Monster.class).get(id);
    return monster != null ? monster.minionOwnerId : -1;
  }

  public static int source(World world, int id) {
    if (!active(world, id)) return -1;
    if (world.getMapper(Player.class).has(id)) return id;
    int owner = monster(world, id) ? owner(world, id)
        : world.getMapper(Missile.class).has(id) ? world.getMapper(Missile.class).get(id).ownerId : -1;
    return active(world, owner) ? owner : id;
  }

  public static int target(World world, int id) {
    IntSet visited = null;
    while (active(world, id)) {
      if (world.getMapper(Player.class).has(id) || !monster(world, id)) return id;
      int owner = owner(world, id);
      if (owner == id || !active(world, owner)) return id;
      if (visited == null) visited = new IntSet();
      if (!visited.add(id)) return -1; // malformed multi-node cycle: fail closed
      id = owner;
    }
    return -1;
  }

  private static boolean aligned(World world, int resolved, int original, boolean fallback) {
    NativeTargetNode node = world.getMapper(NativeTargetNode.class).get(resolved);
    if (resolved == original) return fallback || node != null && node.isNode8();
    Monster monster = world.getMapper(Monster.class).get(resolved);
    return world.getMapper(Player.class).has(resolved)
        || node != null && node.isNode8()
        || world.getMapper(Mercenary.class).has(resolved)
        || world.getMapper(SummonedPet.class).has(resolved)
        || monster != null && monster.converted;
  }

  /** Existing caller-specific Conversion checks run before this relation gate. */
  public static boolean canDamage(World world, PartyManager parties, int attacker, int target,
      boolean attackerAligned, boolean targetAligned) {
    int source = source(world, attacker), destination = target(world, target);
    if (source < 0 || destination < 0) return false;
    return PvpCombatRules.canDamage(parties, source, destination,
        aligned(world, source, attacker, attackerAligned),
        aligned(world, destination, target, targetAligned));
  }
}
