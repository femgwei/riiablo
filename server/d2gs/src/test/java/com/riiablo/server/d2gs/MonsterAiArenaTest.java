package com.riiablo.server.d2gs;

import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.component.NativeUnitFlags;
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

  @Test public void diabRunRequiresNaturalCastRealPathHitAndTailCleanup() {
    MonsterAiArena.DiabRunCycle cycle = new MonsterAiArena.DiabRunCycle();
    cycle.move(true); cycle.hit(true); cycle.end(true); assertEquals(0, cycle.phase);
    cycle.start(false); assertEquals(0, cycle.phase);
    cycle.start(true); cycle.hit(true); cycle.end(true); assertEquals(1, cycle.phase);
    cycle.move(false); assertEquals(1, cycle.phase);
    cycle.move(true); cycle.hit(false); assertEquals(2, cycle.phase);
    cycle.hit(true); cycle.end(false); assertEquals(3, cycle.phase);
    cycle.end(true); assertEquals(4, cycle.phase);
  }

  @Test public void diabWallNeedsCastTrailActualHitAndCleanupInOrder() {
    MonsterAiArena.DiabWallCycle cycle = new MonsterAiArena.DiabWallCycle();
    cycle.trail(true); cycle.damage(true); cycle.cleared(true); assertEquals(0, cycle.phase);
    cycle.cast(false); assertEquals(0, cycle.phase);
    cycle.cast(true); assertEquals(1, cycle.phase);
    cycle.damage(true); cycle.cleared(true); assertEquals(1, cycle.phase);
    cycle.trail(true); cycle.damage(false); assertEquals(2, cycle.phase);
    cycle.damage(true); cycle.cleared(false); assertEquals(3, cycle.phase);
    cycle.cleared(true); assertEquals(4, cycle.phase);
  }

  @Test public void diabColdRequiresActualDirectHitAndTheFollowingS2End() {
    MonsterAiArena.DiabColdCycle cycle = new MonsterAiArena.DiabColdCycle();
    cycle.finished(true); cycle.hit(false);
    assertEquals(0, cycle.phase);
    cycle.hit(true); assertEquals(1, cycle.phase);
    cycle.finished(false); assertEquals(1, cycle.phase);
    cycle.finished(true); assertEquals(2, cycle.phase);
  }

  @Test public void diabloInfernoOracleAlsoRequiresActualStateResetBetweenCasts() {
    for (boolean reset : new boolean[] {false, true}) {
      com.riiablo.codec.excel.Skills.Entry skill = new com.riiablo.codec.excel.Skills.Entry();
      skill.calc2 = "4"; skill.calc3 = "2";
      com.riiablo.engine.server.NativeMonInferno cast = new com.riiablo.engine.server.NativeMonInferno(skill, 3, 6);
      MonsterAiArena.InfernoCycle oracle = new MonsterAiArena.InfernoCycle(true);
      oracle.observeState(false); // A pre-cast false state cannot satisfy reset.
      cast.frame = 2; cast.updated(true, true); oracle.observe(cast, true);
      cast.frame = 4; cast.updated(true, true); oracle.observe(cast, true);
      cast.frame = 10; oracle.finished();
      assertEquals(4, oracle.phase);
      oracle.observeState(!reset);
      oracle.observe(new com.riiablo.engine.server.NativeMonInferno(skill, 3, 6), true);
      assertEquals(reset ? 5 : 1, oracle.phase);
    }
  }

  @Test public void infernoOracleRequiresRealPulsesIntervalTailAndNewCast() {
    com.riiablo.codec.excel.Skills.Entry skill = new com.riiablo.codec.excel.Skills.Entry();
    skill.calc2 = "7"; skill.calc3 = "3";
    com.riiablo.engine.server.NativeMonInferno cast = new com.riiablo.engine.server.NativeMonInferno(skill, 1, 4);
    MonsterAiArena.InfernoCycle oracle = new MonsterAiArena.InfernoCycle();
    oracle.observe(cast, true);
    cast.frame = 1; cast.updated(true, true); oracle.observe(cast, true);
    cast.frame = 4; cast.updated(true, true); oracle.observe(cast, true);
    assertEquals(2, oracle.phase);
    cast.frame = 7; cast.updated(true, true); oracle.observe(cast, true);
    assertEquals(3, oracle.phase);
    oracle.finished(); assertEquals("early finish must fail", 3, oracle.phase);
    cast.frame = 11; oracle.finished(); assertEquals(4, oracle.phase);
    oracle.observe(new com.riiablo.engine.server.NativeMonInferno(skill, 1, 4), true);
    assertEquals(5, oracle.phase);
  }

  @Test public void infernoOracleRejectsFakeMissileWrongIntervalAndSinglePulse() {
    for (int failure = 0; failure < 3; failure++) {
      com.riiablo.codec.excel.Skills.Entry skill = new com.riiablo.codec.excel.Skills.Entry();
      skill.calc2 = "7"; skill.calc3 = "3";
      com.riiablo.engine.server.NativeMonInferno cast = new com.riiablo.engine.server.NativeMonInferno(skill, 1, 4);
      MonsterAiArena.InfernoCycle oracle = new MonsterAiArena.InfernoCycle();
      oracle.observe(cast, true);
      cast.frame = 1; cast.updated(true, true); oracle.observe(cast, failure != 0);
      cast.frame = failure == 1 ? 5 : 7;
      if (failure != 2) { cast.updated(true, true); oracle.observe(cast, true); }
      cast.frame = 11; oracle.finished();
      assertNotEquals(4, oracle.phase);
      oracle.observe(new com.riiablo.engine.server.NativeMonInferno(skill, 1, 4), true);
      assertNotEquals(5, oracle.phase);
    }
  }

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
      probe.cycle = new MonsterAiArena.SubmergeCycle();
      probe.cycle.phase = 3;
      probe.action(SkillDoEvent.obtain(8, 0, 9, null, 1, 0));
      probe.finished(com.riiablo.engine.server.event.AnimDataFinishedEvent.obtain(8));
      assertEquals("helper events cannot finish the subject's cycle", 3, probe.cycle.phase);
      assertFalse(result.specialPassed);
      probe.frog = new MonsterAiArena.FrogCycle();
      probe.frog.phase = 3;
      probe.action(SkillDoEvent.obtain(8, 0, 9, null, 1, 0));
      probe.finished(com.riiablo.engine.server.event.AnimDataFinishedEvent.obtain(8));
      assertEquals("helper events cannot satisfy frog emergence", 3, probe.frog.phase);
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
    row.AI = "Tentacle";
    assertEquals(4, MonsterAiArena.scenarios(row).size());
    row.AI = "TentacleHead";
    assertEquals(3, MonsterAiArena.scenarios(row).size());
    row.AI = "FrogDemon";
    assertTrue(MonsterAiArena.scenarios(row).contains("frog-emerge"));
    row.AI = "Diablo";
    assertTrue(MonsterAiArena.scenarios(row).contains("inferno-cycle"));
    assertTrue(MonsterAiArena.scenarios(row).contains("diab-cold"));
    assertTrue(MonsterAiArena.scenarios(row).contains("diab-wall"));
    assertTrue(MonsterAiArena.scenarios(row).contains("diab-run"));
    assertEquals("&lt;&amp;&quot;", MonsterAiArena.escape("<&\""));
  }

  @Test public void submergeCycleRequiresHeldZeroFrameFlagsEmergenceEndAndSubsequentAttack() {
    MonsterAiArena.SubmergeCycle cycle = new MonsterAiArena.SubmergeCycle();
    int targetable = NativeUnitFlags.MONSTER_TARGET;
    cycle.attack(true, targetable);
    cycle.finished(true, targetable);
    cycle.observe(false, false, 0, targetable, true);
    cycle.observe(true, false, 0, 0, false);
    cycle.observe(true, true, 256, 0, false);
    cycle.observe(true, true, 0, targetable, false);
    cycle.observe(true, true, 0, -1, false);
    assertEquals(0, cycle.phase);
    cycle.observe(true, true, 0, 0, false);
    assertEquals(1, cycle.phase);
    cycle.observe(false, false, 0, 0, true);
    cycle.finished(true, targetable);
    cycle.attack(true, targetable);
    assertEquals(1, cycle.phase);
    cycle.observe(false, false, 0, targetable, true);
    assertEquals(2, cycle.phase);
    cycle.finished(false, targetable);
    cycle.finished(true, 0);
    cycle.attack(true, targetable);
    assertEquals(2, cycle.phase);
    cycle.finished(true, targetable);
    assertEquals(3, cycle.phase);
    cycle.attack(false, targetable);
    cycle.attack(true, 0);
    assertEquals(3, cycle.phase);
    cycle.attack(true, targetable);
    assertEquals(4, cycle.phase);
  }

  @Test public void frogCycleRequiresNoPresenceThenRestoredPresenceAndAnActualAttack() {
    MonsterAiArena.FrogCycle frog = new MonsterAiArena.FrogCycle();
    int flags = NativeUnitFlags.MONSTER_TARGET;
    frog.observe(true, true, 0, 0, false, true, false);
    frog.observe(true, false, 0, 0, true, false, false);
    frog.observe(true, true, 256, 0, true, false, false);
    frog.observe(true, true, 0, flags, true, false, false);
    assertEquals(0, frog.phase);
    frog.observe(true, true, 0, 0, true, false, false);
    assertEquals(1, frog.phase);
    frog.observe(false, false, 0, flags, true, false, true);
    frog.observe(false, false, 0, 0, false, true, true);
    frog.attack(true, flags, true);
    assertEquals(1, frog.phase);
    frog.observe(false, false, 0, flags, false, true, true);
    assertEquals(2, frog.phase);
    frog.finished(true, flags, false);
    frog.finished(false, flags, true);
    frog.attack(true, flags, true);
    assertEquals(2, frog.phase);
    frog.finished(true, flags, true);
    assertEquals(3, frog.phase);
    frog.attack(false, flags, true);
    frog.attack(true, flags, false);
    frog.attack(true, 0, true);
    assertEquals(3, frog.phase);
    frog.attack(true, flags, true);
    assertEquals(4, frog.phase);
  }
}
