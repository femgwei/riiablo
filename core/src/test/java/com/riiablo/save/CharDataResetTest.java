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
