package com.riiablo.engine.server;

import com.artemis.World;
import com.riiablo.Files;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.NativeAiState;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.event.MonsterSoftHitEvent;
import net.mostlyoriginal.api.event.common.EventSystem;

/** Server-owned action reaction. Damage, hit sounds and overlays are separate phases. */
public final class HitReactionResolver {
  public enum Source { MELEE, MISSILE }

  private HitReactionResolver() {}

  public static boolean blockMayInterrupt(Skills.Entry skill) {
    return skill == null || skill.interrupt;
  }

  public static void queue(World world, Files files, int victim,
      CombatSystem.CombatResult combat, Source source) {
    if (combat != null && ((combat.blocked && !combat.blockAnimationAllowed)
        || combat.isAmazonPassiveDefense())) return;
    queue(world, files, victim, combat != null && combat.blocked
        && combat.blockAnimationAllowed, source);
  }

  public static void softHit(World world, int victim) {
    if (!world.getMapper(Monster.class).has(victim)) return;
    CofReference reference = world.getMapper(CofReference.class).get(victim);
    if (reference == null || reference.mode == Engine.Monster.MODE_DT
        || reference.mode == Engine.Monster.MODE_DD) return;
    world.getMapper(NativeAiState.class).create(victim).previousMode = 19;
    EventSystem events = world.getSystem(EventSystem.class);
    if (events != null) events.dispatch(MonsterSoftHitEvent.obtain(victim));
  }

  /**
   * Preserve existing soft-GH policy during active actions. Native FHR thresholds
   * and Amazon dodge modes are not changed by this extraction. BL follows
   * PLRMODES_First's Skills.txt interrupt gate; both routes share the transition.
   */
  public static void queue(World world, Files files, int victim, boolean blocked, Source source) {
    CofManager cofs = world.getSystem(CofManager.class);
    Class unitClass = world.getMapper(Class.class).get(victim);
    CofReference reference = world.getMapper(CofReference.class).get(victim);
    if (cofs == null || unitClass == null || reference == null
        || !world.getMapper(AnimData.class).has(victim)) return;
    Class.Type type = unitClass.type;
    if (type != Class.Type.PLR && type != Class.Type.MON) return;
    if (type == Class.Type.PLR
        ? reference.mode == Engine.Player.MODE_DT || reference.mode == Engine.Player.MODE_DD
        : reference.mode == Engine.Monster.MODE_DT || reference.mode == Engine.Monster.MODE_DD) return;

    Casting casting = world.getMapper(Casting.class).get(victim);
    Sequence sequence = world.getMapper(Sequence.class).get(victim);
    if (blocked) {
      Skills.Entry skill = casting != null && files != null && files.skills != null
          ? files.skills.get(casting.skillId) : null;
      if (casting != null && !blockMayInterrupt(skill)) return;
      world.getMapper(Casting.class).remove(victim);
      world.getMapper(Sequence.class).remove(victim);
    } else if (source == Source.MELEE ? casting != null : sequence != null && sequence.started) {
      softHit(world, victim);
      return;
    }
    byte reaction = type == Class.Type.PLR
        ? (blocked ? Engine.Player.MODE_BL : Engine.Player.MODE_GH)
        : (blocked ? Engine.Monster.MODE_BL : Engine.Monster.MODE_GH);
    byte neutral = type == Class.Type.PLR ? Engine.Player.MODE_NU : Engine.Monster.MODE_NU;
    world.getMapper(Sequence.class).create(victim).sequence(reaction, neutral);
    cofs.setMode(victim, reaction, true);
  }
}
