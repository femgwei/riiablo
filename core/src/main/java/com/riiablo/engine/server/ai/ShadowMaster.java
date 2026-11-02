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
    int count = 0;
    if (monster != null && monster.monstats != null) {
      for (int i = 0; i < 8; i++) {
        String name = monsterSkillName(i);
        if (name == null || name.isEmpty()) continue;
        com.riiablo.codec.excel.Skills.Entry skill = files().skills.get(name);
        if (skill != null && (skill.srvdofunc != 97 || skill.srvmissilea != null))
          candidates[count++] = i;
      }
    }
    if (count == 0) return super.resolveProjectileSkillSlot();
    selectedSlot = candidates[aiRandom(count)];
    return selectedSlot;
  }
}
