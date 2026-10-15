package com.riiablo.engine.server;

import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.skill.SkillFormula;

/** Per-cast SrvSt53/SrvDo095/152 clock, including Horror's verified A2 MonSeq. */
public final class NativeMonInferno {
  public final int deadline;
  public final int interval;
  public final int tailFrames;
  public int frame;
  public int nextUpdate = -1;
  public int endFrame = -1;
  public int missiles;
  public int lastMissileId = -1;
  public boolean started;
  public final boolean horrorSequence;
  public int sequenceCursor;

  public NativeMonInferno(Skills.Entry skill, int level, int tailFrames) {
    horrorSequence = skill.Id == 323;
    deadline = Math.max(1, SkillFormula.evaluate(skill.calc2, skill, level));
    interval = Math.max(1, SkillFormula.evaluate(skill.calc3, skill, level));
    this.tailFrames = Math.max(1, tailFrames);
  }

  public void updated(boolean created, boolean infernoState) {
    started = true;
    if (created) missiles++;
    if (created && frame < deadline && infernoState) {
      nextUpdate = frame + interval;
      endFrame = -1;
    } else {
      nextUpdate = -1;
      endFrame = frame + tailFrames;
    }
  }

  public static int missileFrames(Skills.Entry skill, Missiles.Entry missile, int level) {
    int frames = SkillFormula.evaluate(skill.calc1, skill, level);
    if (frames <= 0) frames = missile.Param[1] + level - 1;
    return Math.max(1, Math.min(255, frames));
  }

  public static int velocity(Missiles.Entry missile, int level) {
    return missile.Vel + level * missile.VelLev / 8;
  }

  public static boolean supports(Skills.Entry skill, Monster monster, Sequence sequence) {
    if (skill == null || skill.srvstfunc != 53
        || (skill.srvdofunc != 95 && skill.srvdofunc != 152)
        || monster == null || monster.monstats2 == null || sequence == null) return false;
    // Explicit consumers only: verified ordinary modes plus Horror's A2 MonSeq.
    // Baal's Skills.monanim=xx is overridden by AiBaal action 12 (SKILL1).
    return (skill.srvdofunc == 95 && ((skill.Id == 178 && sequence.mode1 == Engine.Monster.MODE_A1)
        || (skill.Id == 319 && sequence.mode1 == Engine.Monster.MODE_S1)
        || (skill.Id == 317 && sequence.mode1 == Engine.Monster.MODE_S1)
        || (skill.Id == 323 && sequence.mode1 == Engine.Monster.MODE_A2
            && monster.monstats != null && "FrozenHorror".equals(monster.monstats.AI)
            && "seq_horrorarcticblast".equals(monster.monstats.Sk1mode))))
        || (skill.srvdofunc == 152 && skill.Id == 193 && sequence.mode1 == Engine.Monster.MODE_SC);
  }
}
