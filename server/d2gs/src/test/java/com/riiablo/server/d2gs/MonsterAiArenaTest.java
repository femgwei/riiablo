package com.riiablo.server.d2gs;

import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.logger.Appender;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class MonsterAiArenaTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();

  @Test public void helpersNeverCountAsSubjectBehavior() throws Exception {
    MonsterAiArena.Result result = new MonsterAiArena.Result();
    Path path = temporary.newFolder().toPath().resolve("case.log");
    try (MonsterAiArena.CaseLog log = new MonsterAiArena.CaseLog(path, result)) {
      MonsterAiArena.Probe probe = new MonsterAiArena.Probe(log);
      probe.subject = 7;
      probe.action(SkillDoEvent.obtain(8, 181, 9, null, 97, 0));
      probe.damage(DamageEvent.obtain(8, 0, 10));
      assertEquals(0, result.actions);
      assertEquals(0, result.damageEvents);
      assertEquals("NO_ACTION", MonsterAiArena.classify(result));
      probe.action(SkillDoEvent.obtain(7, 181, 9, null, 97, 0));
      assertEquals(1, result.actions);
      assertEquals("ACTION_OBSERVED", MonsterAiArena.classify(result));
    }
  }

  @Test public void warningsAndFallbackCannotPassViaMovementOrCasts() {
    MonsterAiArena.Result result = new MonsterAiArena.Result();
    result.moves = 10;
    assertEquals("MOVEMENT_ONLY", MonsterAiArena.classify(result));
    result.actions = 2;
    result.specialAssert = true;
    assertEquals("SPECIAL_NOT_TRIGGERED", MonsterAiArena.classify(result));
    result.specialPassed = true;
    assertEquals("ACTION_OBSERVED", MonsterAiArena.classify(result));
    result.warnings = 1;
    assertEquals("WARNING", MonsterAiArena.classify(result));
    result.fallback = true;
    assertEquals("AI_FALLBACK", MonsterAiArena.classify(result));
    result.errors = 1;
    assertEquals("ERROR_LOG", MonsterAiArena.classify(result));
  }

  @Test public void futureLoggersCapturedAndSinksRestoredBetweenCases() throws Exception {
    Logger future;
    Appender original = LogManager.getRootLogger().getAppender();
    MonsterAiArena.Result result = new MonsterAiArena.Result();
    Path path = temporary.newFolder().toPath().resolve("case.log");
    try (MonsterAiArena.CaseLog log = new MonsterAiArena.CaseLog(path, result)) {
      future = LogManager.getLogger("arena.future." + System.nanoTime());
      future.warn("captured-warning");
      assertEquals(1, result.warnings);
    }
    assertSame(original, LogManager.getRootLogger().getAppender());
    assertSame(original, future.getAppender());
    future.warn("outside-case");
    assertEquals(1, result.warnings);
  }

  @Test public void rejectsInvalidOptionsAndUnknownFlags() {
    for (String[] args : new String[][] {{"--ticks", "0"}, {"--wat", "1"}, {"--output"},
        {"--home", "d2", "--output", "out", "--difficulty", "3"}}) {
      try { MonsterAiArena.Config.parse(args); fail("Must reject invalid options"); }
      catch (IllegalArgumentException expected) { }
    }
  }

  @Test public void catalogKeepsHostileControllersButExcludesDisabledAndNpcRows() {
    MonStats.Entry row = new MonStats.Entry();
    row.enabled = true;
    assertTrue(MonsterAiCatalog.eligible(row));
    row.npc = true;
    assertFalse(MonsterAiCatalog.eligible(row));
    row.npc = false;
    row.Align = 2;
    assertFalse(MonsterAiCatalog.eligible(row));
    row.Align = 0;
    row.enabled = false;
    assertFalse(MonsterAiCatalog.eligible(row));
  }

  @Test public void resurrectionScenariosAndHtmlEscapingAreExplicit() {
    MonStats.Entry row = new MonStats.Entry();
    row.AI = "FallenShaman";
    assertEquals(3, MonsterAiArena.scenarios(row).size());
    row.AI = "Zombie";
    assertEquals(2, MonsterAiArena.scenarios(row).size());
    assertEquals("&lt;&amp;&quot;", MonsterAiArena.escape("<&\""));
  }
}
