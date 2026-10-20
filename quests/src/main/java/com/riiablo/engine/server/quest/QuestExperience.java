package com.riiablo.engine.server.quest;

/** Native experience-table values needed by the Ancients reward rule. */
public interface QuestExperience {
  int getMaxLevel(int classId);
  long getExperienceForCurrentLevel(int level, int classId);
  long getExperienceForNextLevel(int level, int classId);
}
