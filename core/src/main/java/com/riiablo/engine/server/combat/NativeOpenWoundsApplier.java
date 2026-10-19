package com.riiablo.engine.server.combat;

import com.artemis.World;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.NativeUnitRandom;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.monster.MonsterRank;
import com.riiablo.engine.server.state.*;

/** Successful ordinary monster item event, once per hit, never per DOT tick. */
public final class NativeOpenWoundsApplier {
  private NativeOpenWoundsApplier() {}
  public static void apply(World world, int source, int target, boolean ranged, int gameSeed) {
    if (source < 0 || target < 0 || !world.getMapper(Monster.class).has(source)) return;
    AttributesWrapper aw = world.getMapper(AttributesWrapper.class).get(source);
    if (aw == null || aw.attrs == null) return;
    Attributes attrs = aw.attrs;
    UnitStates sourceStates = world.getMapper(UnitStates.class).get(source);
    UnitStates targetStates = world.getMapper(UnitStates.class).get(target);
    if (sourceStates != null && sourceStates.snapshotOnly
        || targetStates != null && targetStates.snapshotOnly) return;
    int chance = attrs.getValue(Stat.item_openwounds,0)
        + (sourceStates == null || sourceStates.stateList == null ? 0
            : sourceStates.stateList.getTotalStatContribution(Stat.item_openwounds));
    if (chance <= 0 || NativeUnitRandom.monster(world.getMapper(Monster.class).get(source),gameSeed,source)
        .nextInt(100) >= chance) return;
    Monster victim = world.getMapper(Monster.class).get(target);
    if (victim != null && (victim.monstats != null && victim.monstats.npc
        || victim.monstats2 != null && !victim.monstats2.isAtt)) return;
    UnitStates component = world.getMapper(UnitStates.class).get(target);
    if (component == null) component = world.getMapper(UnitStates.class).create(target).init(world,target);
    if (component.stateList == null) component.init(world,target);
    boolean elite = victim != null && (victim.rank == MonsterRank.CHAMPION
        || victim.rank == MonsterRank.UNIQUE || victim.rank == MonsterRank.SUPER_UNIQUE);
    int rate = NativeOpenWounds.rate(attrs.getValue(Stat.level,1),
        world.getMapper(Player.class).has(target),ranged,elite);
    // Skills.cpp::sub_6FD10EC0 returns an existing same-skill/same-level
    // list after refreshing expiry ONLY, retaining its first rate and owner.
    UnitState state = component.stateList.getState(StateId.OPENWOUNDS);
    if (state == null) {
      state = component.stateList.addState(StateId.OPENWOUNDS,
          NativeOpenWounds.DURATION,1,source);
      state.setNativeModifier(Stat.hpregen,-rate);
    }
    state.duration = state.initialDuration = NativeOpenWounds.DURATION;
    state.expired = false;
    state.needsSync = true;
  }
}
