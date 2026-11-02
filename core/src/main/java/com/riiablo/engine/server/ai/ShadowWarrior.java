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
  protected int resolveProjectileSkillSlot() {
    if (!mSummonedPet.has(entityId)) return super.resolveProjectileSkillSlot();
    com.riiablo.engine.server.component.SummonedPet pet = mSummonedPet.get(entityId);
    if (pet == null || pet.ownerId < 0 || !mPlayer.has(pet.ownerId)
        || mPlayer.get(pet.ownerId).data == null) return super.resolveProjectileSkillSlot();
    int left = mPlayer.get(pet.ownerId).data.getAction(com.badlogic.gdx.Input.Buttons.LEFT);
    int right = mPlayer.get(pet.ownerId).data.getAction(com.badlogic.gdx.Input.Buttons.RIGHT);
    int selected = aiRandomBoolean() ? left : right;
    if (selected < 0 || files().skills.get(selected) == null) selected = selected == left ? right : left;
    if (selected < 0 || files().skills.get(selected) == null) return -1;
    selectedSkillId = selected;
    return 0;
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
