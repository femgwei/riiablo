package com.riiablo.engine.server.combat;

/**
 * Mutable native damage record shared by attacks, missiles and state effects.
 *
 * <p>This is the Java boundary corresponding to the observable parts of
 * D2MOO's {@code D2DamageStrc}.  It carries the packet and result flags; ECS
 * systems remain responsible for applying the resulting life change.
 */
public class NativeDamagePacket {
  public boolean hit;
  public boolean blocked;
  public boolean blockAnimationAllowed;
  public int defenseType = NativeCombatConstants.DEFENSE_NONE;
  public boolean critical;
  public boolean monsterCritical;
  public boolean deadlyStrike;
  public boolean crushingBlow;
  public int physicalDamage;
  /** Physical damage and physical leech use this scale; consumers must use HP accessors. */
  public int physicalScale = 1;
  public int[] elementalDamage = new int[NativeCombatConstants.DAMAGE_TYPE_COUNT];
  /** Legacy whole-HP summary, not suitable for applying fixed-point packets to life. */
  public int totalDamage;
  public int elementalScale = 1;
  /** Native independent rate/length settlement, including fully MDR-reduced cold hits. */
  public boolean nativeElementalSettlement;
  public int lifeStolen;
  public int manaStolen;
  /** Concrete-hand ordinary attack percentages; -1 keeps legacy event policy. */
  public int snapshotLifeLeechPercent = -1, snapshotManaLeechPercent = -1;
  /** Monster drain channels are raw 8.8, not player percentages. */
  public boolean fixedMonsterDrain;
  public boolean hasResourceDrain() {
    return fixedMonsterDrain && (manaDrainFixed > 0 || staminaDrainFixed > 0);
  }
  public int lifeDrainFixed, manaDrainFixed, staminaDrainFixed;
  public int absorbedLife;
  public int hitChance;
  public int attackRating;
  public int targetDefense;
  public int hitRoll = -1;
  public int coldDuration;
  public int poisonDuration;
  /** Resisted HP/frame. Native table poison keeps its rate out of elementalDamage. */
  public float poisonDamagePerFrame;
  public int poisonRawDamageFixed;
  public int poisonPiercePercent;
  public int poisonBaseDuration;

  public float elementalDamageHp(int type) {
    return elementalDamage[type] / (float) elementalScale;
  }

  public float physicalDamageHp() { return physicalDamage / (float) physicalScale; }
  public float lifeStolenHp() { return lifeStolen / (float) physicalScale; }
  public float manaStolenHp() { return manaStolen / (float) physicalScale; }

  public float absorbedLifeHp() {
    return absorbedLife / (float) elementalScale;
  }

  public float totalDamageHp() {
    if (elementalScale == 1 && physicalScale == 1 && !fixedMonsterDrain) return totalDamage;
    float total = physicalDamageHp();
    if (fixedMonsterDrain) total += lifeDrainFixed / 256f;
    for (int i = 1; i < NativeCombatConstants.DAMAGE_TYPE_COUNT; i++) {
      if (i != NativeCombatConstants.DAMAGE_POISON || poisonDuration <= 0) {
        total += elementalDamageHp(i);
      }
    }
    return total;
  }

  public boolean isAmazonPassiveDefense() {
    return defenseType == NativeCombatConstants.DEFENSE_DODGE
        || defenseType == NativeCombatConstants.DEFENSE_AVOID
        || defenseType == NativeCombatConstants.DEFENSE_EVADE;
  }

  public void reset() {
    hit = false;
    blocked = false;
    blockAnimationAllowed = false;
    defenseType = NativeCombatConstants.DEFENSE_NONE;
    critical = false;
    monsterCritical = false;
    deadlyStrike = false;
    crushingBlow = false;
    physicalDamage = 0;
    physicalScale = 1;
    for (int i = 0; i < NativeCombatConstants.DAMAGE_TYPE_COUNT; i++) elementalDamage[i] = 0;
    totalDamage = 0;
    elementalScale = 1;
    nativeElementalSettlement = false;
    lifeStolen = 0;
    manaStolen = 0;
    snapshotLifeLeechPercent = snapshotManaLeechPercent = -1;
    fixedMonsterDrain = false;
    lifeDrainFixed = manaDrainFixed = staminaDrainFixed = 0;
    absorbedLife = 0;
    hitChance = 0;
    attackRating = 0;
    targetDefense = 0;
    hitRoll = -1;
    coldDuration = 0;
    poisonDuration = 0;
    poisonDamagePerFrame = 0f;
    poisonRawDamageFixed = 0;
    poisonPiercePercent = 0;
    poisonBaseDuration = 0;
  }
}
