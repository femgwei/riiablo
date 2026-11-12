package com.riiablo.engine.server.ai;

/** Assassin Shadow Master AI entry point. */
public final class ShadowMaster extends GenericMonster {
  private int selectedSlot = -1;
  private int selectedSkillId = -1;
  public ShadowMaster(int entityId) {
    super(entityId, "ShadowMaster");
  }

  @Override
  public void initialize() {
    super.initialize();
    delayFirstThink(20);
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

  @Override
  protected boolean isValidAiTarget(int targetId) {
    if (!mSummonedPet.has(entityId) || !mPosition.has(targetId)) return true;
    com.riiablo.engine.server.component.SummonedPet pet = mSummonedPet.get(entityId);
    if (pet == null || pet.ownerId < 0 || !mPosition.has(pet.ownerId)) return true;
    // D2MOO uses a 144-square boss leash for the Master.  Once outside it,
    // the normal GenericMonster loop follows the owner instead of attacking.
    return mPosition.get(entityId).position.dst2(mPosition.get(pet.ownerId).position) <= 144f * 144f;
  }

  @Override
  protected int resolveProjectileSkillSlot(int targetId) {
    if (monster == null || monster.monstats == null) return -1;
    int bestScore = Integer.MIN_VALUE;
    int[] best = new int[8];
    int count = 0;
    int slot = 0;
    for (com.riiablo.codec.excel.Skills.Entry skill : files().skills) {
      if (!isMasterSkill(skill) || !isTargetCompatible(skill, targetId)) continue;
      int score = masterSkillScore(skill, targetId);
      if (score > bestScore) { bestScore = score; count = 0; }
      if (score == bestScore && count < best.length) best[count++] = skill.Id;
      slot++;
    }
    if (count == 0) return resolveProjectileSkillSlot();
    selectedSkillId = best[aiRandom(count)];
    selectedSlot = 0;
    return 0;
  }

  @Override
  protected String resolveMonsterSkillName(int index) {
    if (index == 0 && selectedSkillId >= 0) {
      com.riiablo.codec.excel.Skills.Entry skill = files().skills.get(selectedSkillId);
      if (skill != null) return skill.skill;
    }
    return super.resolveMonsterSkillName(index);
  }

  @Override
  protected int resolveMonsterSkillLevel(int index) {
    if (index == 0 && selectedSkillId >= 0) {
      int summonLevel = mSummonedPet.has(entityId) ? mSummonedPet.get(entityId).skillLevel : 1;
      int ownerLevel = 0;
      if (mSummonedPet.has(entityId) && mPlayer.has(mSummonedPet.get(entityId).ownerId)
          && mPlayer.get(mSummonedPet.get(entityId).ownerId).data != null) {
        ownerLevel = mPlayer.get(mSummonedPet.get(entityId).ownerId).data.getSkill(selectedSkillId);
      }
      return Math.max(1, Math.min(24, summonLevel / 2 + ownerLevel / 2));
    }
    return super.resolveMonsterSkillLevel(index);
  }

  private boolean isTargetCompatible(com.riiablo.codec.excel.Skills.Entry skill, int targetId) {
    if (skill == null || targetId < 0 || !mAttributesWrapper.has(targetId)) return true;
    // Summon skills must not re-summon the same class as the current target.
    if (skill.summon != null && !skill.summon.isEmpty() && mMonster.has(targetId)
        && mMonster.get(targetId).monstats != null
        && skill.summon.equalsIgnoreCase(mMonster.get(targetId).monstats.Id)) return false;
    return true;
  }

  private int masterSkillScore(com.riiablo.codec.excel.Skills.Entry skill, int targetId) {
    int score = masterSkillScore(skill);
    if (mAttributesWrapper.has(targetId) && mAttributesWrapper.get(targetId).attrs != null) {
      short resist = resistanceStat(skill, targetId);
      score += Math.max(-20, Math.min(20, -resist / 5));
    }
    return score;
  }

  private short resistanceStat(com.riiablo.codec.excel.Skills.Entry skill, int targetId) {
    String type = skill.EType == null ? "" : skill.EType.toLowerCase(java.util.Locale.ROOT);
    short stat = com.riiablo.attributes.Stat.damageresist;
    if (type.contains("fire")) stat = com.riiablo.attributes.Stat.fireresist;
    else if (type.contains("ltng") || type.contains("light")) stat = com.riiablo.attributes.Stat.lightresist;
    else if (type.contains("cold") || type.contains("freeze")) stat = com.riiablo.attributes.Stat.coldresist;
    else if (type.contains("pois")) stat = com.riiablo.attributes.Stat.poisonresist;
    else if (type.contains("magic")) stat = com.riiablo.attributes.Stat.magicresist;
    return (short) mAttributesWrapper.get(targetId).attrs.getValue(stat, 0);
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
