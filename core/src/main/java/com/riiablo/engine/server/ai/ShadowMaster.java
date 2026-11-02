package com.riiablo.engine.server.ai;

/** Assassin Shadow Master AI entry point. */
public final class ShadowMaster extends GenericMonster {
  private int selectedSlot = -1;
  public ShadowMaster(int entityId) {
    super(entityId, "ShadowMaster");
  }

  @Override
  protected int resolveProjectileSkillSlot() {
    int[] candidates = new int[8];
    int[] scores = new int[8];
    int count = 0;
    if (monster != null && monster.monstats != null) {
      for (int i = 0; i < 8; i++) {
        String name = monsterSkillName(i);
        if (name == null || name.isEmpty()) continue;
        com.riiablo.codec.excel.Skills.Entry skill = files().skills.get(name);
        if (skill != null && isMasterSkill(skill)) {
          candidates[count] = i;
          scores[count++] = masterSkillScore(skill);
        }
      }
    }
    if (count == 0) return super.resolveProjectileSkillSlot();
    int best = scores[0];
    for (int i = 1; i < count; i++) best = Math.max(best, scores[i]);
    int bestCount = 0;
    for (int i = 0; i < count; i++) if (scores[i] == best) candidates[bestCount++] = candidates[i];
    selectedSlot = candidates[aiRandom(bestCount)];
    return selectedSlot;
  }

  private int masterSkillScore(com.riiablo.codec.excel.Skills.Entry skill) {
    int score = hasProjectileMissile(skill) ? 20 : 0;
    if (skill.aura) score += 35;
    if (skill.immediate) score += 10;
    score += Math.min(20, Math.max(0, skill.reqlevel));
    return score;
  }

  /** Shadow Master receives the assassin skill pool, but native AI skips
   * passive/invalid rows and prefers skills that can actually emit a missile. */
  private boolean isMasterSkill(com.riiablo.codec.excel.Skills.Entry skill) {
    if (skill.charclass == null
        || com.riiablo.codec.excel.Skills.getClassId(skill.charclass)
            != com.riiablo.CharacterClass.ASSASSIN.id) return false;
    if (skill.passive || skill.srvdofunc == 97) return false;
    return hasProjectileMissile(skill) || skill.aura || skill.immediate;
  }
}
