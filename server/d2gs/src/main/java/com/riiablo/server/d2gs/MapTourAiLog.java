package com.riiablo.server.d2gs;

import com.riiablo.logger.Appender;
import com.riiablo.logger.Encoder;
import com.riiablo.logger.Level;
import com.riiablo.logger.LogEvent;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scoped production-log capture. Record counts are evidence, not an AI correctness oracle. */
final class MapTourAiLog implements AutoCloseable {
  private static final String SERVER = "com.riiablo.engine.server.";
  private static final Pattern FIELD = Pattern.compile("(?:^|\\s)([A-Za-z][A-Za-z0-9]*)=([^\\s]+)");
  private static final Pattern POSITION = Pattern.compile("\\bpos=\\(([^,]+), ([^)]+)\\)");
  private final BufferedWriter output;
  private final Path summary;
  private final long started = System.nanoTime();
  private final Map<Logger, Runnable> levels = new LinkedHashMap<>();
  private final Map<String, String> entities = new LinkedHashMap<>();
  private final Map<String, double[]> positions = new LinkedHashMap<>();
  private final Map<String, Counts> counts = new TreeMap<>();
  private long warnings, errors, fallbacks;
  private boolean closed, completed;

  MapTourAiLog(Path directory, int seed, int difficulty) throws IOException {
    Files.createDirectories(directory);
    summary = directory.resolve("ai-summary.txt");
    if (Files.exists(directory.resolve("map-tour.log")) || Files.exists(summary)) {
      throw new IOException("Refusing to reuse AI tour output: " + directory);
    }
    output = Files.newBufferedWriter(directory.resolve("ai-events.log"), StandardCharsets.UTF_8,
        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    output.write("[AI_TOUR] BEGIN seed=" + seed + " difficulty=" + difficulty
        + " coverage=OBSERVED_RUNTIME_ONLY oracle=NONE\n");
    output.flush();
    // Do not enable global DEBUG. Explicitly enable the production AI/action sources;
    // --verbose controls a different (LibGDX) logger and is insufficient by itself.
    for (String name : new String[] {"ai.AI", "ai.Fallen", "ai.FallenShaman", "ai.FetishShaman",
        "ai.FoulCrowNest", "ai.GreaterMummy", "ai.Npc", "ai.PinHead", "ai.Navi", "ai.Vines",
        "ai.Zombie", "AIStepper", "Actioneer", "ServerSkillSystem"}) {
      Logger logger = LogManager.getLogger(SERVER + name);
      levels.put(logger, logger.temporaryLevel(name.equals("AIStepper") ? Level.DEBUG : Level.INFO));
    }
    // Future loggers inherit the wrapped root sink; existing ones retain their own sinks.
    for (Logger logger : new ArrayList<>(LogManager.getRegistry().getLoggers().values())) {
      logger.addAppender(new Capture(logger.getAppender()));
    }
  }

  synchronized void completed() { completed = true; }

  synchronized void requireNoRuntimeFailures() throws IOException {
    if (fallbacks != 0 || errors != 0) throw new IOException("AI tour has fallbackRecords="
        + fallbacks + " errors=" + errors + "; inspect ai-events.log");
    for (Counts row : counts.values()) {
      if (row.nativeCombatant && row.behaviorRecords() > 0) return;
    }
    throw new IOException("No native combatant behavior observed; initialization/NPC logs are insufficient");
  }

  private final class Capture implements Appender {
    final Appender previous;
    Capture(Appender previous) { this.previous = previous; }
    @Override public Encoder encoder() { return previous.encoder(); }
    @Override public void append(LogEvent event) {
      try {
        record(event.level(), event.source().getClassName(), event.message().format());
      } finally {
        // Exactly one release, owned by the original sink, including async sinks.
        previous.append(event);
      }
    }
  }

  synchronized void record(Level level, String source, String message) {
    if (closed) return;
    try {
      com.riiablo.engine.server.AuthoritativeSimulation simulation =
          com.riiablo.engine.server.AuthoritativeSimulation.current();
      output.write("tick=" + (simulation == null ? -1 : simulation.tickNumber())
          + " elapsedMs=" + (System.nanoTime() - started) / 1_000_000L
          + " " + level + " [" + source + "] " + message);
      output.newLine();
      output.flush();
    } catch (IOException e) { throw new UncheckedIOException(e); }
    if (level == Level.WARN) warnings++;
    if (level.isMoreSpecificThan(Level.ERROR)) errors++;
    if (message.contains("[AI_FALLBACK]")) fallbacks++;
    Map<String, String> fields = fields(message);
    String entity = fields.get("entity");
    if (message.startsWith("[MONSTER_AI_INIT]")) {
      String key = fields.get("monster") + " ai=" + fields.get("ai");
      entities.put(entity, key); // A fresh init replaces recycled entity-ID attribution.
      positions.remove(entity);
      Counts row = counts.computeIfAbsent(key, ignored -> new Counts());
      row.initRecords++;
      row.nativeCombatant |= "true".equals(fields.get("nativeCombatant"));
    }
    String key = entities.get(entity);
    if (key == null) return;
    Counts row = counts.get(key);
    if (message.startsWith("[MONSTER_MOVE]")) row.moveRecords++;
    if (message.startsWith("[MONSTER_AI_TICK]")) {
      row.thinkSamples++;
      Matcher position = POSITION.matcher(message);
      if (position.find()) {
        double x = Double.parseDouble(position.group(1)), y = Double.parseDouble(position.group(2));
        if (Double.isFinite(x) && Double.isFinite(y)) {
          double[] previous = positions.put(entity, new double[] {x, y});
          if (previous != null && Math.hypot(x - previous[0], y - previous[1]) > 0.01) row.movementSamples++;
        }
      }
    }
    if ("decision".equals(fields.get("phase"))) row.decisionRecords++;
    if (message.startsWith("[MONSTER_SKILL]") && "cast".equals(fields.get("phase"))) row.castRecords++;
    if (message.startsWith("[MONSTER_MELEE]") && "damage".equals(fields.get("phase"))) row.damageRecords++;
  }

  static Map<String, String> fields(String message) {
    Map<String, String> result = new LinkedHashMap<>();
    Matcher matcher = FIELD.matcher(message);
    while (matcher.find()) result.put(matcher.group(1), matcher.group(2));
    return result;
  }

  private static final class Counts {
    int initRecords, moveRecords, decisionRecords, castRecords, damageRecords, thinkSamples, movementSamples;
    boolean nativeCombatant;
    long behaviorRecords() { return (long) moveRecords + decisionRecords + castRecords + damageRecords + movementSamples; }
  }

  synchronized String summaryText() {
    StringBuilder text = new StringBuilder("[AI_TOUR] SUMMARY run=")
        .append(completed ? "COMPLETED" : "FAILED_OR_INTERRUPTED")
        .append(" coverage=OBSERVED_RUNTIME_ONLY oracle=NONE correctness=NOT_ASSERTED")
        .append(" warnings=").append(warnings).append(" errors=").append(errors)
        .append(" fallbackRecords=").append(fallbacks).append('\n');
    text.append("Counts are log records, not unique actions, ticks, kills or coverage percentages.\n")
        .append("Initialization does not prove activation. Unlisted monsters/branches are NOT_TESTED.\n")
        .append("Positive damageRecords are melee damage-path records, not a full skill-effect audit.\n");
    for (Map.Entry<String, Counts> entry : counts.entrySet()) {
      Counts row = entry.getValue();
      text.append("[AI_TOUR] MONSTER monster=").append(entry.getKey())
          .append(" initRecords=").append(row.initRecords)
          .append(" moveRecords=").append(row.moveRecords)
          .append(" decisionRecords=").append(row.decisionRecords)
          .append(" skillCastRecords=").append(row.castRecords)
          .append(" meleeDamageRecords=").append(row.damageRecords)
          .append(" thinkSamples=").append(row.thinkSamples)
          .append(" movementSamples=").append(row.movementSamples)
          .append(" nativeCombatant=").append(row.nativeCombatant)
          .append(" evidence=").append(row.behaviorRecords() > 0 ? "BEHAVIOR_OBSERVED"
              : row.thinkSamples > 0 ? "AI_TICK_ONLY_NO_ACTION" : "INIT_ONLY_NO_BEHAVIOR_EVIDENCE").append('\n');
    }
    return text.toString();
  }

  @Override public synchronized void close() throws IOException {
    if (closed) return;
    closed = true;
    for (Logger logger : new ArrayList<>(LogManager.getRegistry().getLoggers().values())) {
      Appender appender = logger.getAppender();
      if (appender instanceof MapTourAiLog.Capture) {
        Capture capture = (Capture) appender;
        logger.addAppender(capture.previous);
      }
    }
    for (Runnable restore : levels.values()) restore.run();
    try {
      Files.write(summary, summaryText().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
    } finally { output.close(); }
  }
}
