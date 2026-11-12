package com.riiablo.engine.server.ai;

/**
 * Assassin Shadow Warrior AI entry point.
 *
 * <p>The native AI copies the owner's active skills before selecting an
 * attack.  The skill-copy state is installed by the summon service; keeping a
 * dedicated class here is important because falling back to GenericMonster
 * loses the summon-specific policy and makes the pet use MonStats.Skill1.</p>
 */
public final class ShadowWarrior extends GenericMonster {
  private int selectedSkillId = -1;
  public ShadowWarrior(int entityId) {
    super(entityId, "ShadowWarrior");
  }

  @Override
  public void initialize() {
    super.initialize();
    delayFirstThink(20);
  }

  @Override
  protected int resolveProjectileSkillSlot() {
    if (!mSummonedPet.has(entityId)) return super.resolveProjectileSkillSlot();
    com.riiablo.engine.server.component.SummonedPet pet = mSummonedPet.get(entityId);
    if (pet == null || pet.ownerId < 0 || !mPlayer.has(pet.ownerId)
        || mPlayer.get(pet.ownerId).data == null) return super.resolveProjectileSkillSlot();
    int left = mPlayer.get(pet.ownerId).data.getAction(com.badlogic.gdx.Input.Buttons.LEFT);
    int right = mPlayer.get(pet.ownerId).data.getAction(com.badlogic.gdx.Input.Buttons.RIGHT);
    int selected = aiRandomBoolean() ? left : right;
    if (selected < 0 || files().skills.get(selected) == null) selected = selected == left ? right : left;
    if (!isUsableAssassinSkill(selected)) {
      int other = selected == left ? right : left;
      selected = isUsableAssassinSkill(other) ? other : -1;
    }
    if (selected < 0 || files().skills.get(selected) == null) return -1;
    selectedSkillId = selected;
    return 0;
  }

  /** Mirrors AITHINK_ShadowWarriorCheckUseSkill's class/target-state gates. */
  private boolean isUsableAssassinSkill(int id) {
    com.riiablo.codec.excel.Skills.Entry skill = id >= 0 ? files().skills.get(id) : null;
    if (skill == null || skill.charclass == null
        || com.riiablo.codec.excel.Skills.getClassId(skill.charclass)
            != com.riiablo.CharacterClass.ASSASSIN.id) return false;
    // Native AI does not cast an aura that is already active, and does not
    // build a progressive charge after the third stage.
    int progressiveState = com.riiablo.engine.server.skill.AssassinSkills.progressiveStateId(skill);
    if (skill.prgstack && progressiveState >= 0 && hasThreeCharges(progressiveState)) return false;
    if (mAttributesWrapper.has(entityId) && mAttributesWrapper.get(entityId).attrs != null) {
      int level = Math.max(1, resolveMonsterSkillLevel(0));
      float cost = com.riiablo.engine.server.skill.NativeSkillResolver.manaCost(skill, level);
      com.riiablo.attributes.StatRef mana = mAttributesWrapper.get(entityId).attrs.get(com.riiablo.attributes.Stat.mana);
      if (mana != null && mana.asFixed() < cost) return false;
    }
    return true;
  }

  private boolean hasThreeCharges(int stateId) {
    if (!mUnitStates.has(entityId)) return false;
    com.riiablo.engine.server.component.UnitStates states = mUnitStates.get(entityId);
    com.riiablo.engine.server.state.UnitState state = states.stateList == null
        ? null : states.stateList.getState(stateId);
    return state != null && com.riiablo.engine.server.skill.AssassinSkills.progressiveCharges(state) >= 3;
  }

  @Override
  protected String resolveMonsterSkillName(int index) {
    if (index == 0 && selectedSkillId >= 0 && files().skills.get(selectedSkillId) != null)
      return files().skills.get(selectedSkillId).skill;
    return super.resolveMonsterSkillName(index);
  }

  @Override
  protected int resolveMonsterSkillLevel(int index) {
    if (!mSummonedPet.has(entityId) || !mPlayer.has(mSummonedPet.get(entityId).ownerId)
        || mPlayer.get(mSummonedPet.get(entityId).ownerId).data == null)
      return super.resolveMonsterSkillLevel(index);
    int skillId = selectedSkillId >= 0 ? selectedSkillId
        : files().skills.index(monster.monstats.Skill1);
    int owner = mPlayer.get(mSummonedPet.get(entityId).ownerId).data.getSkill(skillId);
    return Math.max(1, owner / 2 + Math.max(1, mSummonedPet.get(entityId).skillLevel) / 3);
  }
}
