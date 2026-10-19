package com.riiablo.engine.server.combat;

import com.artemis.World;
import com.riiablo.Files;
import com.artemis.annotations.Wire;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.DifficultyLevels;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.ServerGameplayContext;
import com.riiablo.engine.server.NativeUnitRandom;

/** Authoritative player/hireling percentage leech shared by melee and owned missiles. */
final class LeechSettlement {
  private LeechSettlement() {}
  private static final class Binding {
    @Wire(name = "serverContext", failOnNull = false) ServerGameplayContext context;
  }

  static void apply(World world, DamageEvent hit, float lifeBefore) {
    if (!hit.isLeechableAttack() || hit.suppressLeech || hit.attacker < 0 || hit.damage <= 0f) return;
    boolean player = world.getMapper(Player.class).has(hit.attacker);
    boolean mercenary = world.getMapper(Mercenary.class).has(hit.attacker);
    // Ordinary monsters use fixed drain damage, NOT these percentage stats.
    if (!player && !mercenary) return;
    AttributesWrapper wrapper = world.getMapper(AttributesWrapper.class).get(hit.attacker);
    if (wrapper == null || wrapper.attrs == null) return;
    Attributes attrs = wrapper.attrs;
    StatRef life = attrs.get(Stat.hitpoints);
    UnitStates states = world.getMapper(UnitStates.class).get(hit.attacker);
    if (life == null || life.asFixed() <= 0f || (states != null && states.snapshotOnly)) return;
    int lifePercent = (hit.snapshotLifeLeechPercent >= 0 ? hit.snapshotLifeLeechPercent
        : stat(attrs, Stat.lifedrainmindam)) + hit.extraLifeLeechPercent;
    int manaPercent = (hit.snapshotManaLeechPercent >= 0 ? hit.snapshotManaLeechPercent
        : stat(attrs, Stat.manadrainmindam)) + hit.extraManaLeechPercent;
    if (hit.snapshotLifeLeechPercent < 0 && states != null && states.stateList != null) {
      lifePercent += states.stateList.getTotalLifeLeechModifier();
    }
    if (lifePercent <= 0 && manaPercent <= 0) return;
    int difficulty = difficulty(world, hit.victim, hit.attacker);
    Monster target = world.getMapper(Monster.class).get(hit.victim);
    int drain = 100;
    if (target != null) {
      drain = target.monstats != null && target.monstats.Drain != null
          && difficulty < target.monstats.Drain.length ? target.monstats.Drain[difficulty] : 0;
    }
    Binding binding = new Binding();
    world.inject(binding);
    Files files = ServerGameplayContext.files(binding.context);
    DifficultyLevels.Entry row = files != null ? files.DifficultyLevels.get(difficulty) : null;
    // Do not invent difficulty constants when the authoritative data is missing.
    if (player && row == null) return;
    int physical = raw(hit.physicalDamage);
    if (states == null || states.stateList == null
        || !states.stateList.hasState(StateId.DEATH_DELAY)) {
      hit.lifeLeeched = restore(attrs, Stat.hitpoints, Stat.maxhp,
          NativeLeechMath.percentage(physical, raw(lifeBefore), lifePercent,
              player ? row.LifeStealDivisor : 1, drain));
    }
    if (player) {
      hit.manaLeeched = restore(attrs, Stat.mana, Stat.maxmana,
          NativeLeechMath.percentage(physical, raw(lifeBefore), manaPercent,
              row.ManaStealDivisor, drain));
    }
    // Native cues depend on positive leech channels, even when the pool is full.
    if (physical > 0 && drain > 0) {
      boolean lifeCue = (long) lifePercent * 64 / Math.max(1, player ? row.LifeStealDivisor : 1) > 0;
      boolean manaCue = (long) manaPercent * 64 / Math.max(1, player ? row.ManaStealDivisor : 1) > 0;
      if (lifeCue && manaCue) {
        int seed = binding.context != null ? binding.context.gameSeed() : 0;
        hit.leechOverlay = NativeUnitRandom.unit(world, seed, hit.attacker).nextInt(2) == 0
            ? "steallife" : "stealmana";
      } else if (lifeCue || manaCue) hit.leechOverlay = lifeCue ? "steallife" : "stealmana";
    }
  }

  private static int stat(Attributes attrs, short id) {
    StatRef ref = attrs.get(id);
    return ref == null ? 0 : ref.asInt();
  }

  private static int raw(float hp) {
    return !Float.isFinite(hp) || hp <= 0f ? 0
        : (int) Math.min(Integer.MAX_VALUE, (double) hp * 256);
  }

  private static float restore(Attributes attrs, short currentId, short maximumId, int amount) {
    StatRef current = attrs.get(currentId, StatRef.obtain());
    StatRef maximum = attrs.get(maximumId, StatRef.obtain());
    if (current == null || maximum == null || amount <= 0) return 0f;
    int gain = Math.min(amount, Math.max(0, maximum.encodedValues() - current.encodedValues()));
    current.setEncoded(current.encodedValues() + gain);
    return gain / 256f;
  }

  private static int difficulty(World world, int victim, int attacker) {
    MapWrapper map = world.getMapper(MapWrapper.class).get(victim);
    if (map == null || map.map == null) map = world.getMapper(MapWrapper.class).get(attacker);
    return map == null || map.map == null ? 0 : Math.max(0, Math.min(2, map.map.getDifficulty()));
  }
}
