package com.riiablo.engine.server.combat;

import com.artemis.World;
import com.riiablo.Files;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.NativeUnitRandom;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.monster.MonsterRank;
import com.riiablo.engine.server.skill.NecromancerSkills;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import java.util.function.IntUnaryOperator;

/** SkillItem EventFunc09 for successful monster melee hits, including Raven.
 * Raven's item_stupidity=-1 is a valid nonzero event stat, not a disabled chance.
 */
public final class NativeBlindTargetApplier {
  private NativeBlindTargetApplier() {}

  static int rollSkillLevel(int attackerLevel, int defenderLevel, int value,
      boolean ranged, IntUnaryOperator random) {
    if (value == 0) return 0;
    int chance = 5 * (attackerLevel + 4 * value - defenderLevel + 6);
    if (ranged && chance > 0) chance /= 3;
    chance = Math.max(1, Math.min(99, chance));
    int margin = chance - random.applyAsInt(100);
    return margin > 0 ? Math.min(20, margin / 5 + 1) : 0;
  }

  public static void apply(World world, Files files, int source, int target,
      int difficulty, int gameSeed) {
    Monster attacker = world.getMapper(Monster.class).get(source);
    Monster victim = world.getMapper(Monster.class).get(target);
    if (attacker == null || victim == null) return;
    AttributesWrapper a = world.getMapper(AttributesWrapper.class).get(source);
    AttributesWrapper b = world.getMapper(AttributesWrapper.class).get(target);
    if (a == null || b == null || a.attrs == null || b.attrs == null) return;
    UnitStates sourceStates = world.getMapper(UnitStates.class).get(source);
    UnitStates states = world.getMapper(UnitStates.class).get(target);
    if (sourceStates != null && sourceStates.snapshotOnly || states != null && states.snapshotOnly) return;
    Attributes attrs = a.attrs;
    int value = attrs.getValue(Stat.item_stupidity, 0)
        + (sourceStates == null || sourceStates.stateList == null ? 0
            : sourceStates.stateList.getTotalStatContribution(Stat.item_stupidity));
    if (value == 0) return;
    // The event rolls before SKILLS_ApplyDimVisionToTarget checks curse/AI eligibility.
    int level = rollSkillLevel(attrs.getValue(Stat.level, 1), b.attrs.getValue(Stat.level, 1),
        value, false, NativeUnitRandom.monster(attacker, gameSeed, source)::nextInt);
    if (level == 0 || victim.monstats == null || !victim.monstats.switchai
        || victim.monstats.npc || victim.monstats.Align == 2
        || victim.rank == MonsterRank.UNIQUE || victim.rank == MonsterRank.SUPER_UNIQUE
        || world.getMapper(Corpse.class).has(target)
        || b.attrs.get(Stat.hitpoints) == null || b.attrs.get(Stat.hitpoints).asFixed() <= 0f) return;
    if (victim.monstats2 != null && (victim.monstats2.mMode == null
        || victim.monstats2.mMode.length <= 2 || !victim.monstats2.mMode[2])) return;
    NativeUnitFlags flags = world.getMapper(NativeUnitFlags.class).get(target);
    if (flags != null && !flags.has(NativeUnitFlags.MONSTER_TARGET)) return;
    if (states != null && states.stateList != null
        && states.stateList.hasState(StateId.UNINTERRUPTABLE)) return;
    Skills.Entry skill = files.skills.get(SkillId.DIM_VISION);
    if (skill == null) return;
    if (states == null) states = world.getMapper(UnitStates.class).create(target).init(world, target);
    if (states.stateList == null) states.init(world, target);
    NecromancerSkills.applyCurse(states.stateList, files.States, skill, level, source,
        files.DifficultyLevels.get(Math.max(0, Math.min(2, difficulty))), b.attrs, false);
  }
}
