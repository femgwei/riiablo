package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;

import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.CharStats;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.UnitLifecycle;
import com.riiablo.engine.server.component.UnitStates;

/** Authoritative Diablo II 25 Hz player mana regeneration. */
@All({Player.class, AttributesWrapper.class})
public class ManaRecoverySystem extends IteratingSystem {
  static final int TICKS_PER_SECOND = 25;
  static final int DEFAULT_REGEN_SECONDS = 300;

  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<AttributesWrapper> mAttributes;
  protected ComponentMapper<UnitLifecycle> mUnitLifecycle;
  protected ComponentMapper<UnitStates> mUnitStates;

  @Override
  protected void process(int entityId) {
    AttributesWrapper wrapper = mAttributes.get(entityId);
    if (wrapper == null || wrapper.attrs == null || isDead(entityId)) return;

    Attributes attrs = wrapper.attrs;
    StatRef mana = attrs.get(Stat.mana, StatRef.obtain());
    StatRef maxMana = attrs.get(Stat.maxmana, StatRef.obtain());
    if (mana == null || maxMana == null) return;

    int currentEncoded = mana.encodedValues();
    int maximumEncoded = Math.max(0, maxMana.encodedValues());
    if (currentEncoded >= maximumEncoded) return;

    int recoveryBonus = encodedInt(attrs, Stat.manarecoverybonus);
    if (mUnitStates.has(entityId)) {
      UnitStates states = mUnitStates.get(entityId);
      if (states != null && states.stateList != null) {
        recoveryBonus += states.stateList.getTotalManaRecoveryModifier();
      }
    }
    int flatRecoveryEncoded = encodedValue(attrs, Stat.manarecovery);
    int recoveryEncoded = recoveryPerTickEncoded(
        maximumEncoded, manaRegenSeconds(mPlayer.get(entityId)), recoveryBonus,
        flatRecoveryEncoded);
    int nextEncoded = clampEncoded((long) currentEncoded + recoveryEncoded, 0, maximumEncoded);
    if (nextEncoded != currentEncoded) mana.setEncoded(nextEncoded);
  }

  private boolean isDead(int entityId) {
    if (mUnitLifecycle != null && mUnitLifecycle.has(entityId)) {
      UnitLifecycle lifecycle = mUnitLifecycle.get(entityId);
      if (lifecycle != null && lifecycle.isDead()) return true;
    }
    Attributes attrs = mAttributes.get(entityId).attrs;
    StatRef hitpoints = attrs.get(Stat.hitpoints, StatRef.obtain());
    return hitpoints != null && hitpoints.asFixed() <= 0f;
  }

  private static int manaRegenSeconds(Player player) {
    if (player == null || player.data == null || player.data.classId == null) {
      return DEFAULT_REGEN_SECONDS;
    }
    CharStats.Entry charStats = player.data.classId.entry();
    return charStats == null || charStats.ManaRegen <= 0
        ? DEFAULT_REGEN_SECONDS
        : charStats.ManaRegen;
  }

  private static int encodedInt(Attributes attrs, short stat) {
    StatRef ref = attrs.get(stat, StatRef.obtain());
    return ref == null ? 0 : ref.asInt();
  }

  private static int encodedValue(Attributes attrs, short stat) {
    StatRef ref = attrs.get(stat, StatRef.obtain());
    return ref == null ? 0 : ref.encodedValues();
  }

  /** Mirrors D2Game EVENTS_ManaRegen in the native 8.8 fixed-point domain. */
  static int recoveryPerTickEncoded(
      int maximumEncoded, int regenSeconds, int recoveryBonus, int flatRecoveryEncoded) {
    if (maximumEncoded <= 0) return flatRecoveryEncoded;
    int denominator = TICKS_PER_SECOND
        * (regenSeconds > 0 ? regenSeconds : DEFAULT_REGEN_SECONDS);
    int baseRecoveryEncoded = Math.max(1, maximumEncoded / denominator);
    long percent = (long) baseRecoveryEncoded * (recoveryBonus + 100L) / 100L;
    return clampEncoded(percent + flatRecoveryEncoded, Integer.MIN_VALUE, Integer.MAX_VALUE);
  }

  private static int clampEncoded(long value, int minimum, int maximum) {
    return (int) Math.max(minimum, Math.min((long) maximum, value));
  }
}
