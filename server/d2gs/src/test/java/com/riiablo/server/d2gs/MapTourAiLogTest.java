package com.riiablo.server.d2gs;

import com.riiablo.logger.Appender;
import com.riiablo.logger.Level;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class MapTourAiLogTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();

  @Test public void scopesLevelsCapturesFutureLoggersAndRestoresOriginalSinks() throws Exception {
    Logger ai = LogManager.getLogger("com.riiablo.engine.server.ai.AI");
    Appender original = ai.getAppender();
    Level level = ai.level();
    Logger missiles = LogManager.getLogger("com.riiablo.engine.server.MissileCollisionSystem");
    Appender missileAppender = missiles.getAppender();
    Level missileLevel = missiles.level();
    Path dir = temporary.newFolder().toPath();
    Logger future;
    try (MapTourAiLog capture = new MapTourAiLog(dir, 1, 0)) {
      assertEquals(Level.INFO, ai.level());
      assertEquals(Level.INFO, missiles.level());
      missiles.info("[MISSILE_HIT] phase=impact missileId=9 owner=7 target=8 appliedDamage=3.0");
      ai.info("[MONSTER_AI_INIT] entity=7 monster=skeleton1 ai=Skeleton");
      future = LogManager.getLogger("tour-test.future." + System.nanoTime());
      future.warn("future_logger_warning");
      capture.completed();
    }
    assertSame(original, ai.getAppender());
    assertEquals(level, ai.level());
    assertSame(missileAppender, missiles.getAppender());
    assertEquals(missileLevel, missiles.level());
    assertSame(LogManager.getRootLogger().getAppender(), future.getAppender());
    String events = read(dir.resolve("ai-events.log"));
    assertTrue(events.contains("MONSTER_AI_INIT"));
    assertTrue(events.contains("[MISSILE_HIT] phase=impact"));
    assertTrue(events.contains("future_logger_warning"));
    assertTrue(read(dir.resolve("ai-summary.txt")).contains("run=COMPLETED"));
  }

  @Test public void separatesInitializationFromBehaviorAndReassignsReusedIds() throws Exception {
    Path dir = temporary.newFolder().toPath();
    try (MapTourAiLog capture = new MapTourAiLog(dir, 1, 0)) {
      capture.record(Level.INFO, "test", "[MONSTER_AI_INIT] entity=7 monster=old ai=Skeleton");
      capture.record(Level.INFO, "test", "[MONSTER_AI_INIT] entity=7 monster=new ai=Zombie");
      capture.record(Level.INFO, "test", "[MONSTER_MOVE] entity=7 monster=new ai=Zombie");
      capture.record(Level.INFO, "test", "[MONSTER_MELEE] phase=decision entity=7 monster=new");
      capture.record(Level.INFO, "test", "[MONSTER_MELEE] phase=damage entity=7 monster=new");
      capture.record(Level.INFO, "test", "[MONSTER_SKILL] phase=cast entity=7 monster=new");
      String summary = capture.summaryText();
      assertTrue(summary.contains("monster=old ai=Skeleton initRecords=1 moveRecords=0 decisionRecords=0"
          + " skillCastRecords=0 meleeDamageRecords=0 thinkSamples=0 movementSamples=0 nativeCombatant=false evidence=INIT_ONLY_NO_BEHAVIOR_EVIDENCE"));
      assertTrue(summary.contains("monster=new ai=Zombie initRecords=1 moveRecords=1 decisionRecords=1"
          + " skillCastRecords=1 meleeDamageRecords=1 thinkSamples=0 movementSamples=0 nativeCombatant=false evidence=BEHAVIOR_OBSERVED"));
      assertTrue(summary.contains("correctness=NOT_ASSERTED"));
    }
    assertTrue(read(dir.resolve("ai-summary.txt")).contains("run=FAILED_OR_INTERRUPTED"));
  }

  @Test public void rejectsAndFailuresCannotMasqueradeAsDamageOrCompletedBehavior() throws Exception {
    try (MapTourAiLog capture = new MapTourAiLog(temporary.newFolder().toPath(), 1, 0)) {
      capture.record(Level.INFO, "test", "[MONSTER_AI_INIT] entity=8 monster=test ai=GenericMonster");
      capture.record(Level.INFO, "test", "[MONSTER_MELEE] phase=reject entity=8 reason=range");
      capture.record(Level.INFO, "test", "[MONSTER_SKILL] phase=reject entity=8 reason=unsupported");
      capture.record(Level.WARN, "test", "[AI_FALLBACK] entityId=8 ai=Missing");
      capture.record(Level.ERROR, "test", "runtime_failed");
      assertTrue(capture.summaryText().contains("fallbackRecords=1"));
      assertTrue(capture.summaryText().contains("skillCastRecords=0 meleeDamageRecords=0"));
      try { capture.requireNoRuntimeFailures(); fail("fallback/error must fail the runtime gate"); }
      catch (IOException expected) { assertTrue(expected.getMessage().contains("fallbackRecords=1 errors=1")); }
    }
  }

  @Test public void refusesExistingEvidenceWithoutTruncation() throws Exception {
    Path dir = temporary.newFolder().toPath();
    Files.write(dir.resolve("ai-events.log"), "keep".getBytes(StandardCharsets.UTF_8));
    try { new MapTourAiLog(dir, 1, 0); fail("must not overwrite evidence"); }
    catch (IOException expected) { assertEquals("keep", read(dir.resolve("ai-events.log"))); }
  }

  @Test public void npcMovementAndCombatantInitializationCannotPassRuntimeGate() throws Exception {
    try (MapTourAiLog capture = new MapTourAiLog(temporary.newFolder().toPath(), 1, 0)) {
      capture.record(Level.INFO, "test", "[MONSTER_AI_INIT] entity=1 monster=akara ai=Npc nativeCombatant=false");
      capture.record(Level.INFO, "test", "[MONSTER_MOVE] entity=1");
      capture.record(Level.INFO, "test", "[MONSTER_AI_INIT] entity=2 monster=zombie1 ai=Zombie nativeCombatant=true");
      capture.record(Level.DEBUG, "test", "[MONSTER_AI_TICK] entity=2 sample=AFTER_AI_UPDATE");
      assertTrue(capture.summaryText().contains("thinkSamples=1 movementSamples=0 nativeCombatant=true evidence=AI_TICK_ONLY_NO_ACTION"));
      try { capture.requireNoRuntimeFailures(); fail("no combatant behavior"); }
      catch (IOException expected) { assertTrue(expected.getMessage().contains("No native combatant behavior")); }
      capture.record(Level.INFO, "test", "[MONSTER_MOVE] entity=2");
      capture.requireNoRuntimeFailures();
    }
  }

  @Test public void temporaryLevelRestoresInheritedContextBinding() {
    String name = "tour-test.inherited." + System.nanoTime();
    Logger logger = LogManager.getLogger(name);
    Level before = logger.level();
    Runnable restore = logger.temporaryLevel(Level.INFO);
    assertEquals(Level.INFO, logger.level());
    restore.run();
    assertEquals(before, logger.level());
    LogManager.setLevel(name, Level.DEBUG);
    assertEquals("restored logger still inherits later context updates", Level.DEBUG, logger.level());
  }

  @Test public void legacyPathfinderMovementUsesPositionEvidenceAndNotJustPathOrState() throws Exception {
    try (MapTourAiLog capture = new MapTourAiLog(temporary.newFolder().toPath(), 1, 0)) {
      capture.record(Level.INFO, "test", "[MONSTER_AI_INIT] entity=2 monster=zombie1 ai=Zombie nativeCombatant=true");
      capture.record(Level.DEBUG, "test", "[MONSTER_AI_TICK] entity=2 state=APPROACH pos=(1.0, 2.0) hasPathfind=true");
      capture.record(Level.DEBUG, "test", "[MONSTER_AI_TICK] entity=2 state=APPROACH pos=(1.0, 2.0) hasPathfind=true");
      assertTrue(capture.summaryText().contains("movementSamples=0 nativeCombatant=true evidence=AI_TICK_ONLY_NO_ACTION"));
      capture.record(Level.DEBUG, "test", "[MONSTER_AI_TICK] entity=2 pos=(2.0, 2.0)");
      capture.requireNoRuntimeFailures();
      assertTrue(capture.summaryText().contains("movementSamples=1 nativeCombatant=true evidence=BEHAVIOR_OBSERVED"));
      capture.record(Level.INFO, "test", "[MONSTER_AI_INIT] entity=2 monster=brute1 ai=Brute nativeCombatant=true");
      capture.record(Level.DEBUG, "test", "[MONSTER_AI_TICK] entity=2 pos=(200.0, 200.0)");
      assertTrue(capture.summaryText().contains("monster=brute1 ai=Brute initRecords=1 moveRecords=0 decisionRecords=0"
          + " skillCastRecords=0 meleeDamageRecords=0 thinkSamples=1 movementSamples=0"));
    }
  }

  @Test public void defaultTourDoesNotEnableObservation() throws Exception {
    D2GSHeadlessClient.Config config = config("--map-tour");
    assertFalse(config.tourAiObserve);
    assertEquals(8000, config.tourAiObserveMillis);
  }

  @Test public void observationAcceptsBoundedWindowAndFocusedLevels() throws Exception {
    D2GSHeadlessClient.Config config = config("--map-tour", "--tour-ai-observe",
        "--tour-ai-observe-millis", "400", "--tour-levels", "2", "--difficulty", "2");
    assertTrue(config.tourAiObserve);
    assertEquals(400, config.tourAiObserveMillis);
    assertEquals("2", config.tourLevels);
  }

  @Test public void observationRejectsAcceleratedRunsAndIncompatibleModes() throws Exception {
    for (String[] args : new String[][] {
        {"--tour-ai-observe"},
        {"--map-tour", "--tour-ai-observe", "--tour-rate", "100"},
        {"--map-tour", "--tour-ai-observe", "--tour-object-loads"},
        {"--map-tour", "--tour-ai-observe", "--tour-all-seams"},
        {"--map-tour", "--tour-ai-observe", "--tour-town-round-trip"},
        {"--map-tour", "--tour-ai-observe", "--tour-ai-observe-millis", "0"},
        {"--map-tour", "--tour-ai-observe", "--tour-ai-observe-millis", "30001"},
        {"--map-tour", "--tour-ai-observe", "--difficulty", "3"}}) {
      try { config(args); fail("invalid AI observation accepted"); }
      catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("AI observation")); }
    }
  }

  private static D2GSHeadlessClient.Config config(String... args) throws IOException {
    String[] all = new String[args.length + 1];
    all[0] = "--generated-amazon";
    System.arraycopy(args, 0, all, 1, args.length);
    return D2GSHeadlessClient.Config.parse(all);
  }

  private static String read(Path path) throws IOException {
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
  }
}
