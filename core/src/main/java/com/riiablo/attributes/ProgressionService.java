package com.riiablo.attributes;

/** Pure player progression rules used by the M6 domain layer. */
public final class ProgressionService {
  private final ExperienceTable experience;

  public ProgressionService(ExperienceTable experience) {
    if (experience == null) throw new NullPointerException("experience");
    this.experience = experience;
  }

  public Result award(long currentExperience, int currentLevel, int classId, long award) {
    long current = Math.max(0L, currentExperience);
    int level = clampLevel(currentLevel);
    long applied = Math.max(0L, award);
    int maxLevel = Math.min(ExperienceTable.MAX_LEVEL, Math.max(1, experience.getMaxLevel(classId)));
    long cap = maxLevel >= ExperienceTable.MAX_LEVEL
        ? experience.getExperienceForCurrentLevel(maxLevel, classId)
        : experience.getExperienceForNextLevel(maxLevel, classId);
    long nextExperience = Math.min(saturatedAdd(current, applied), Math.max(current, cap));
    int nextLevel = level;
    while (nextLevel < maxLevel
        && nextExperience >= experience.getExperienceForNextLevel(nextLevel, classId)) {
      nextLevel++;
    }
    return new Result(current, nextExperience, level, nextLevel, nextExperience - current,
        nextLevel > level);
  }

  private static int clampLevel(int level) {
    return Math.max(1, Math.min(ExperienceTable.MAX_LEVEL, level));
  }

  private static long saturatedAdd(long left, long right) {
    if (right > Long.MAX_VALUE - left) return Long.MAX_VALUE;
    return left + right;
  }

  public static final class Result {
    public final long oldExperience, newExperience, appliedExperience;
    public final int oldLevel, newLevel;
    public final boolean leveledUp;

    private Result(long oldExperience, long newExperience, int oldLevel, int newLevel,
        long appliedExperience, boolean leveledUp) {
      this.oldExperience = oldExperience;
      this.newExperience = newExperience;
      this.oldLevel = oldLevel;
      this.newLevel = newLevel;
      this.appliedExperience = appliedExperience;
      this.leveledUp = leveledUp;
    }
  }
}
