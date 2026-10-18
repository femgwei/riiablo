package com.riiablo.save;

import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.logger.Appender;
import com.riiablo.logger.Level;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.logger.OutputStreamAppender;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CharDataResetTest extends RiiabloTest {
  @Test
  void resistanceInitializationUsesCharacterModeForPlayerAndMercenary() {
    CharData character = new CharData();
    short[] resistances = {Stat.fireresist, Stat.lightresist, Stat.coldresist, Stat.poisonresist};
    for (boolean expansion : new boolean[] {false, true}) {
      character.flags = expansion ? D2S.FLAG_EXPANSION : 0;
      for (int difficulty = 0; difficulty < 3; difficulty++) {
        character.diff = difficulty;
        character.softReset();
        int expected = expansion ? com.riiablo.Riiablo.files.DifficultyLevels.get(difficulty).ResistPenalty
            : new int[] {0, -20, -50}[difficulty];
        for (Attributes stats : new Attributes[] {character.statData, character.mercData.statData}) {
          assertTrue(stats.difficultyResistanceIncluded());
          for (short stat : resistances) assertEquals(expected, stats.base().getValue(stat, 999));
        }
      }
    }
  }

  @Test
  void clearingFreshCharacterDoesNotRebuildEmptyAttributes() throws Exception {
    Logger logger = LogManager.getLogger(Attributes.class);
    Appender original = logger.getAppender();
    Runnable restoreLevel = logger.temporaryLevel(Level.WARN);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try {
      logger.addAppender(new OutputStreamAppender(output));
      new CharData().clear();
      assertEquals("", output.toString("UTF-8"));
    } finally {
      logger.addAppender(original);
      restoreLevel.run();
    }
  }

  @Test
  void softResetClearsAllPlayerAndMercenaryAttributeLists() {
    CharData character = new CharData();
    Attributes[] attributes = {character.statData, character.mercData.statData};
    for (Attributes stats : attributes) {
      stats.base().put(Stat.level, 80);
      stats.reset();
      stats.buildList().put(Stat.firemindam, 123);
      stats.remaining().put(Stat.firemindam, 456);
    }

    character.softReset();

    for (Attributes stats : attributes) {
      assertEquals(0, stats.list().numLists(), "no old serialized property lists");
      assertTrue(stats.aggregate().isEmpty(), "no old runtime aggregate");
      assertTrue(stats.remaining().isEmpty(), "no pending old modifiers");
      assertEquals(0, stats.base().getValue(Stat.level, -1));
      assertEquals(75, stats.base().getValue(Stat.maxfireresist, -1));
    }
    // Reusing the record must not resurrect old modifiers either.
    character.softReset();
    assertEquals(0, character.statData.list().numLists());
    assertEquals(0, character.mercData.statData.list().numLists());
  }
}
