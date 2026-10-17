package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import com.riiablo.Files;
import com.riiablo.Riiablo;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;

class ServerGameplayContextTest {
  @Test
  void keepsWorldDataAndSeedWhenGlobalHostChanges() {
    Files worldFiles = mock(Files.class);
    Files replacementFiles = mock(Files.class);
    Files previousFiles = Riiablo.files;
    int previousSeed = Riiablo.gameSeed;
    try {
      CharData character = mock(CharData.class);
      ServerGameplayContext context = new ServerGameplayContext(
          worldFiles, 0x13579BDF, character);
      Riiablo.files = replacementFiles;
      Riiablo.gameSeed = 0x2468ACE0;

      assertSame(worldFiles, context.files());
      assertEquals(0x13579BDF, context.gameSeed());
      assertSame(character, context.character());
      assertEquals(0x2468ACE0, Riiablo.gameSeed);
      assertSame(replacementFiles, Riiablo.files);
    } finally {
      Riiablo.files = previousFiles;
      Riiablo.gameSeed = previousSeed;
    }
  }
}
