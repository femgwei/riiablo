package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.riiablo.Files;
import com.riiablo.Riiablo;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;

class ServerGameplayContextTest {
  @Test
  void legacyFactoriesShareTheCurrentBindingAndKeepMissingTableFallback() {
    Files previousFiles = Riiablo.files;
    CharData previousCharacter = Riiablo.charData;
    try {
      Riiablo.files = null;
      Riiablo.charData = null;
      assertEquals(null, ServerGameplayContext.legacyCombatContext(2).difficultyRow());
      assertEquals(true, ServerGameplayContext.legacyCombatContext(2).expansion());
      Files replacement = mock(Files.class);
      Riiablo.files = replacement;
      assertSame(replacement, ServerGameplayContext.fromGlobals().files());
      assertEquals(null, ServerGameplayContext.legacyCombatContext(1).difficultyRow());
    } finally {
      Riiablo.files = previousFiles;
      Riiablo.charData = previousCharacter;
    }
  }

  @Test
  void combatModeBelongsToWorldAndDedicatedDefaultsToExpansion() {
    Files files = mock(Files.class);
    CharData character = mock(CharData.class);
    ServerGameplayContext classic = new ServerGameplayContext(files, 1, character);
    assertEquals(-50, com.riiablo.engine.server.combat.NativeDifficultyRules
        .playerResistancePenalty(classic.combatContext(2)));
    assertEquals(true, classic.combatContext(2).stateDurationsDeferred());
    when(character.isExpansion()).thenReturn(true);
    assertEquals(-100, com.riiablo.engine.server.combat.NativeDifficultyRules
        .playerResistancePenalty(classic.combatContext(2)));
    assertEquals(true, new ServerGameplayContext(files, 2).combatContext(2).expansion());
  }

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
