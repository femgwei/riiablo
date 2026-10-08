package com.riiablo.engine.server.monster;

import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.MonLvl;
import com.riiablo.engine.server.NativeRng;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.combat.CombatSystem;

/** Runtime portions of D2Game MonsterUnique.cpp that are evaluated per attack. */
public final class MonsterUniqueModifiers {
  private MonsterUniqueModifiers() {}

  public static final class ElementalAttack {
    public final int[] min = new int[CombatSystem.DAMAGE_TYPE_COUNT];
    public final int[] max = new int[CombatSystem.DAMAGE_TYPE_COUNT];
    public int coldLength;
    public int poisonLength;
    public int selectedType;
  }

  /** Mirrors MONSTERUNIQUE_ApplyElementalDamage for Spectral Hit (UMod 27). */
  public static ElementalAttack rollSpectralHit(
      Monster monster, Attributes attrs, int difficulty) {
    if (monster == null || attrs == null
        || !MonsterAffix.hasAffix(monster.affixes, MonsterAffix.SPECTRAL_HIT)
        || Riiablo.files == null || Riiablo.files.MonLvl == null) return null;
    int rowCount = Riiablo.files.MonLvl.size();
    if (rowCount <= 1) return null;
    difficulty = Math.max(0, Math.min(2, difficulty));
    int level = Math.max(1, value(attrs, Stat.level));
    MonLvl.Entry row = Riiablo.files.MonLvl.get(
        Math.min(level, rowCount - 1));
    if (row == null || row.LDM == null || difficulty >= row.LDM.length) return null;

    ElementalAttack result = new ElementalAttack();
    short[] minStats = {0, Stat.firemindam, Stat.lightmindam, Stat.coldmindam,
        Stat.poisonmindam, Stat.magicmindam};
    short[] maxStats = {0, Stat.firemaxdam, Stat.lightmaxdam, Stat.coldmaxdam,
        Stat.poisonmaxdam, Stat.magicmaxdam};
    for (int type = CombatSystem.DAMAGE_FIRE; type < CombatSystem.DAMAGE_TYPE_COUNT; type++) {
      result.min[type] = value(attrs, minStats[type]);
      result.max[type] = value(attrs, maxStats[type]);
    }
    result.coldLength = value(attrs, Stat.coldlength);
    result.poisonLength = value(attrs, Stat.poisonlength);

    NativeRng random = new NativeRng(monster.rngState);
    int[] nativeTypes = {CombatSystem.DAMAGE_FIRE, CombatSystem.DAMAGE_LIGHTNING,
        CombatSystem.DAMAGE_MAGIC, CombatSystem.DAMAGE_COLD, CombatSystem.DAMAGE_POISON};
    int type = nativeTypes[random.nextInt(nativeTypes.length)];
    monster.rngState = random.state();
    int levelDamage = Math.max(0, row.LDM[difficulty]);
    result.min[type] += levelDamage * 66 / 100;
    result.max[type] += levelDamage;
    if (type == CombatSystem.DAMAGE_COLD) result.coldLength += 40;
    if (type == CombatSystem.DAMAGE_POISON) result.poisonLength += 40;
    result.selectedType = type;
    return result;
  }

  private static int value(Attributes attrs, short stat) {
    StatRef ref = attrs.get(stat, StatRef.obtain());
    return ref == null ? 0 : ref.asInt();
  }
}
