package com.riiablo.server.d2gs;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.riiablo.COFs;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.audio.ServerAudio;
import com.riiablo.codec.Animation;
import com.riiablo.codec.D2;
import com.riiablo.codec.StringTBLs;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.*;
import com.riiablo.engine.server.ai.GenericMonster;
import com.riiablo.engine.server.component.AIWrapper;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.party.PartyManager;
import com.riiablo.engine.server.combat.CombatPositionHistory;
import com.riiablo.item.ItemGenerator;
import com.riiablo.logger.Appender;
import com.riiablo.logger.Encoder;
import com.riiablo.logger.Level;
import com.riiablo.logger.LogEvent;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.Map;
import com.riiablo.mpq.MPQFileHandleResolver;
import com.riiablo.save.CharData;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/**
 * Batch companion to skill-viewer. Reuses Map.createSyntheticArena, but runs
 * production server units/AI at 25 Hz, with a fresh world AND player per case.
 * No network, renderer, terrain, quest simulation, or correctness oracle is implied.
 */
public final class MonsterAiArena extends ApplicationAdapter {
  static final class Result {
    public String monster, ai, actualAi = "", scenario, status = "NOT_RUN", error = "";
    public String acts, sources, helpers = "", log;
    public int seed, difficulty, arenaLevel, ticks, actions, damageEvents, moves, warnings, errors;
    public boolean fallback, specialAssert, specialPassed;
    public String special = "NOT_IMPLEMENTED", coverage = "BASELINE_ONLY";
    public float damage;
  }

  static final class Config {
    String home = System.getenv("D2_HOME");
    Path output;
    int ticks = 500, seed = 1, difficulty;
    boolean catalogOnly;
    Set<String> monsters = new TreeSet<>();
    static Config parse(String[] args) {
      Config config = new Config();
      for (int i = 0; i < args.length; i++) {
        String arg = args[i];
        if (arg.equals("--catalog-only")) { config.catalogOnly = true; continue; }
        if (++i == args.length) throw new IllegalArgumentException("Missing value for " + arg);
        String value = args[i];
        switch (arg) {
          case "--home": config.home = value; break;
          case "--output": config.output = Paths.get(value); break;
          case "--ticks": config.ticks = Integer.parseInt(value); break;
          case "--seed": config.seed = Integer.parseInt(value); break;
          case "--difficulty": config.difficulty = Integer.parseInt(value); break;
          case "--monsters": config.monsters.addAll(Arrays.asList(value.split(","))); break;
          default: throw new IllegalArgumentException("Unknown option: " + arg);
        }
      }
      if (config.home == null || config.output == null || config.ticks < 1
          || config.ticks > 15000 || config.difficulty < 0 || config.difficulty > 2) {
        throw new IllegalArgumentException("Require --home, NEW --output, ticks=1..15000, difficulty=0..2");
      }
      return config;
    }
  }

  private final Config config;
  private final CountDownLatch done = new CountDownLatch(1);
  private Throwable failure;
  private MonsterAiArena(Config config) { this.config = config; }

  public static void main(String[] args) throws Exception {
    MonsterAiArena arena = new MonsterAiArena(Config.parse(args));
    HeadlessApplicationConfiguration options = new HeadlessApplicationConfiguration();
    options.updatesPerSecond = 25;
    new HeadlessApplication(arena, options);
    arena.done.await();
    if (arena.failure != null) throw new IllegalStateException("AI arena failed", arena.failure);
  }

  @Override public void create() {
    try {
      // An interrupted run remains distinguishable from a completed run. Never replace old evidence.
      Files.createDirectories(config.output.toAbsolutePath().getParent());
      Files.createDirectory(config.output);
      bootstrap();
      MonsterAiCatalog catalog = new MonsterAiCatalog(Riiablo.files);
      for (String requested : config.monsters) {
        if (!catalog.rows.containsKey(requested)) throw new IllegalArgumentException("Unknown hostile monster: " + requested);
      }
      Files.write(config.output.resolve("inventory.txt"), catalog.summary().getBytes(StandardCharsets.UTF_8));
      List<Result> results = new ArrayList<>();
      for (MonsterAiCatalog.Row row : catalog.rows.values()) {
        for (String scenario : scenarios(row.monster)) results.add(result(row, scenario));
      }
      report(catalog, results); // Persist the entire denominator before executing the first case.
      Json json = new Json();
      json.setOutputType(JsonWriter.OutputType.json);
      json.setUsePrototypes(false);
      try (BufferedWriter journal = Files.newBufferedWriter(config.output.resolve("results.jsonl"),
          StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
        for (Result result : results) {
          MonsterAiCatalog.Row row = catalog.rows.get(result.monster);
          if (!config.catalogOnly && (config.monsters.isEmpty() || config.monsters.contains(row.monster.Id))) {
            runCase(row, result);
            System.out.println("[AI_ARENA] " + result.monster + " " + result.scenario + " " + result.status
                + " actions=" + result.actions + " moves=" + result.moves + " special=" + result.special);
          }
          journal.write(json.toJson(result));
          journal.newLine();
          journal.flush();
        }
      }
      report(catalog, results);
      java.util.Map<String, Integer> counts = new TreeMap<>();
      for (Result result : results) counts.merge(result.status, 1, Integer::sum);
      System.out.println("[AI_ARENA] DIAGNOSTIC_COMPLETE " + counts + " correctnessPass=NOT_EVALUATED");
      Files.write(config.output.resolve("COMPLETED"), "All catalog rows recorded; not an AI correctness PASS.\n".getBytes(StandardCharsets.UTF_8));
    } catch (Throwable error) {
      failure = error;
      error.printStackTrace();
    } finally {
      try {
        if (Riiablo.assets != null) Riiablo.assets.dispose();
      } catch (Throwable cleanup) {
        if (failure == null) failure = cleanup;
        else failure.addSuppressed(cleanup);
      } finally {
        try { Gdx.app.exit(); }
        finally { done.countDown(); }
      }
    }
  }

  private void bootstrap() {
    Riiablo.home = Gdx.files.absolute(config.home);
    Riiablo.mpqs = new MPQFileHandleResolver(Riiablo.home);
    Riiablo.assets = new AssetManager();
    Riiablo.files = new com.riiablo.Files(Riiablo.assets);
    Riiablo.cofs = new COFs(Riiablo.assets);
    Riiablo.string = new StringTBLs(Riiablo.mpqs);
    Riiablo.anim = D2.loadFromFile(Riiablo.mpqs.resolve("data\\global\\eanimdata.d2"));
    Riiablo.audio = new ServerAudio(Riiablo.assets);
  }

  static List<String> scenarios(MonStats.Entry monster) {
    List<String> scenarios = new ArrayList<>(Arrays.asList("near", "far"));
    if ("FallenShaman".equals(monster.AI) || "GreaterMummy".equals(monster.AI)) scenarios.add("corpse");
    if ("Tentacle".equals(monster.AI) || "BaalCrabClone".equals(monster.AI)) scenarios.add("orphan");
    return scenarios;
  }

  private Result result(MonsterAiCatalog.Row row, String scenario) {
    Result result = new Result();
    result.monster = row.monster.Id;
    result.ai = row.monster.AI;
    result.acts = row.acts.toString();
    result.sources = row.sources.toString();
    result.scenario = scenario;
    result.seed = config.seed;
    result.difficulty = config.difficulty;
    result.arenaLevel = row.arenaLevel();
    result.log = row.monster.Id + "-" + scenario + ".log";
    return result;
  }

  private void runCase(MonsterAiCatalog.Row row, Result result) {
    World world = null;
    Map map = null;
    try (CaseLog capture = new CaseLog(config.output.resolve(result.log), result);
         ArenaScope scope = new ArenaScope()) {
      Riiablo.gameSeed = config.seed;
      MathUtils.random.setSeed(config.seed);
      scope.map = map = new Map(config.seed, config.difficulty);
      Map.Zone zone = map.createSyntheticArena(result.arenaLevel, 4, 4);
      ServerEntityFactory factory = new ServerEntityFactory();
      Probe probe = new Probe(capture);
      scope.world = world = Riiablo.engine = new World(new WorldConfigurationBuilder()
          .with(new EventSystem(), probe, new CofManager())
          .with(new com.riiablo.engine.client.MenuManager(), new com.riiablo.engine.client.DialogManager())
          .with(new ItemManager(), new ItemGenerator())
          .with(new ObjectInteractor(), new WarpInteractor(), new ItemInteractor())
          .with(new StateUpdater(), new MissileCollisionSystem(), new Actioneer())
          .with(new SummonedPetSystem(), new ServerMonsterCorpseSystem(), new UnitLifecycleSystem())
          .with(new NativePlayerTargetSlots(), new AuraEcsSystem(), new ServerPlayerDeathSystem())
          .with(new ServerSkillSystem(), new SequenceHandler(), new AnimStepper())
          .with(new AIStepper(), new DynamicUnitCollisionSystem(), new Pathfinder())
          .with(new VelocityAdder(), new VelocityModeChanger(false, true), new AnimDataResolver(), factory)
          .build().register("map", map).register("factory", factory)
          .register("partyManager", new PartyManager())
          .register("combatPositionHistory", new CombatPositionHistory(map)));
      world.inject(map);
      map.setEntityFactory(factory);
      world.setDelta(Animation.FRAME_DURATION);
      AuthoritativeSimulation simulation = new AuthoritativeSimulation(world);

      CharData player = CharData.obtain(config.difficulty, false, "AiTester", Riiablo.AMAZON);
      player.level = 30;
      Attributes stats = player.getStats();
      stats.base().put(Stat.level, 30);
      stats.base().put(Stat.maxhp, 1000000);
      stats.base().put(Stat.hitpoints, 1000000);
      stats.reset();
      float distance = result.scenario.equals("near") ? 3 : 18;
      // Keep full footprints inside the arena; (0,0) is also a legacy path sentinel.
      int playerId = factory.createPlayer(player, new Vector2(128, 128));
      zone.attachEntity(playerId);
      int subject = factory.createMonster(row.monster.hcIdx, 128 + distance, 128);
      if (subject == Engine.INVALID_ENTITY) throw new IllegalStateException("Factory rejected subject");
      zone.attachEntity(subject);
      probe.subject = subject;
      AIWrapper ai = world.getMapper(AIWrapper.class).get(subject);
      result.actualAi = ai.ai.getClass().getSimpleName();
      result.fallback = ai.ai instanceof GenericMonster;
      int corpse = helpers(world, factory, zone, row.monster, subject, 128 + distance, result, capture);
      Vector2 previous = world.getMapper(Position.class).get(subject).position.cpy();
      for (int tick = 0; tick < config.ticks; tick++) {
        capture.tick = tick + 1;
        simulation.tick(null, null);
        result.ticks++;
        if (!world.getEntityManager().isActive(subject)) {
          if (result.scenario.equals("orphan")) { result.specialPassed = true; break; }
          throw new IllegalStateException("Subject removed; terminal lifecycle requires a dedicated scenario");
        }
        Vector2 position = world.getMapper(Position.class).get(subject).position;
        if (!Float.isFinite(position.x) || !Float.isFinite(position.y)) throw new IllegalStateException("Nonfinite position");
        if (!position.epsilonEquals(previous, 0.01f)) result.moves++;
        previous.set(position);
        if (result.scenario.equals("orphan") && (world.getMapper(Corpse.class).has(subject)
            || world.getMapper(AttributesWrapper.class).get(subject).attrs.get(Stat.hitpoints).asFixed() <= 0f)) {
          result.specialPassed = true;
        }
        if (tick % 25 == 0) capture.line("sample subject=" + subject + " ai=" + result.actualAi
            + " state=" + ai.ai.getState() + " pos=" + position
            + " casting=" + world.getMapper(Casting.class).has(subject));
        if (corpse >= 0 && world.getMapper(Monster.class).has(corpse)) {
          Monster revived = world.getMapper(Monster.class).get(corpse);
          if (revived.resurrected && revived.resurrectedBy == subject) result.specialPassed = true;
        }
      }
      if (result.specialAssert) result.special = result.specialPassed
          ? (result.scenario.equals("orphan") ? "OWNERLESS_DEATH_OBSERVED" : "RESURRECTION_OBSERVED") : "NOT_TRIGGERED";
      result.status = classify(result);
      capture.line("END status=" + result.status + " ticks=" + result.ticks + " special=" + result.special);
    } catch (Exception | LinkageError | AssertionError error) {
      result.status = "RUNTIME_ERROR";
      Throwable cause = error;
      while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
      result.error = cause.toString();
      // The journal survives individual failures and the next case gets a new world.
      try (java.io.PrintWriter trace = new java.io.PrintWriter(Files.newBufferedWriter(
          config.output.resolve(result.log), StandardCharsets.UTF_8, StandardOpenOption.APPEND))) {
        error.printStackTrace(trace);
      } catch (IOException writeError) { throw new UncheckedIOException(writeError); }
    }
    // Include cleanup warnings/errors too. A cleanup failure is caught above per case.
    if (!result.status.equals("RUNTIME_ERROR")) result.status = classify(result);
  }

  /** Closed before CaseLog so disposal failures/warnings remain attributed to this case. */
  private static final class ArenaScope implements AutoCloseable {
    World world;
    Map map;
    @Override public void close() {
      try {
        if (world != null) world.dispose();
      } finally {
        try { if (map != null) map.dispose(); }
        finally { Riiablo.engine = null; }
      }
    }
  }

  static String classify(Result result) {
    if (result.errors > 0) return "ERROR_LOG";
    if (result.fallback) return "AI_FALLBACK";
    if (result.warnings > 0) return "WARNING";
    if (result.specialAssert && !result.specialPassed) return "SPECIAL_NOT_TRIGGERED";
    if (result.specialAssert && result.specialPassed && "orphan".equals(result.scenario)) return "TERMINAL_OBSERVED";
    if (result.actions > 0) return "ACTION_OBSERVED";
    return result.moves > 0 ? "MOVEMENT_ONLY" : "NO_ACTION";
  }

  private int helpers(World world, ServerEntityFactory factory, Map.Zone zone, MonStats.Entry row,
      int subject, float x, Result result, CaseLog capture) {
    if (result.scenario.equals("orphan")) {
      result.specialAssert = true;
      result.special = "NOT_TRIGGERED";
      result.coverage = "OWNERLESS_LIFECYCLE";
      return -1;
    }
    if ("Tentacle".equals(row.AI) || "BaalCrabClone".equals(row.AI)) {
      // D2MOO Fn056 / AiBaal Fn140: these units must have a living owner.
      String ownerId = "BaalCrabClone".equals(row.AI) ? "baalcrab"
          : row.Id.replace("tentacle", "tentaclehead");
      MonStats.Entry owner = Riiablo.files.monstats.get(ownerId);
      if (owner == null) throw new IllegalStateException("Missing native owner " + ownerId);
      int entity = factory.createMonster(owner.hcIdx, x + 12, 140);
      if (entity < 0) throw new IllegalStateException("Cannot create native owner " + ownerId);
      zone.attachEntity(entity);
      Monster subjectMonster = world.getMapper(Monster.class).get(subject);
      subjectMonster.setMinionOwner(entity);
      if ("BaalCrabClone".equals(row.AI)) {
        subjectMonster.baalLinkedOwnerId = entity;
        world.getMapper(Monster.class).get(entity).baalLinkedOwnerId = subject;
      }
      result.helpers += "owner:" + ownerId + ":" + entity + " ";
    }
    Set<String> ids = new TreeSet<>();
    for (String id : new String[] {row.minion1, row.minion2}) {
      if (id != null && !id.isEmpty()) ids.add(id);
    }
    String corpseId = null;
    if (result.scenario.equals("corpse")) {
      if ("FallenShaman".equals(row.AI)) {
        // D2MOO Fn013: resurrection callback needs a Fallen corpse (owned for expansion shamans).
        MonStats.Entry minion = Riiablo.files.monstats.get(row.minion1);
        corpseId = minion != null && "Fallen".equals(minion.AI) ? minion.Id : "fallen1";
      } else if ("GreaterMummy".equals(row.AI)) {
        // D2MOO Fn022 callback: a same-alignment lUndead terminal corpse.
        corpseId = "skeleton1";
      }
      result.specialAssert = true;
      result.special = "NOT_TRIGGERED";
      result.coverage = "BASELINE_PLUS_RESURRECTION";
    }
    int i = 0;
    for (String id : ids) {
      MonStats.Entry helper = Riiablo.files.monstats.get(id);
      if (helper == null) throw new IllegalStateException("Missing minion row " + id);
      int entity = factory.createMonster(helper.hcIdx, x + 6, 134 + i++ * 6);
      if (entity == Engine.INVALID_ENTITY) throw new IllegalStateException("Cannot create minion " + id);
      zone.attachEntity(entity);
      world.getMapper(Monster.class).get(entity).setMinionOwner(subject);
      result.helpers += id + ":" + entity + " ";
    }
    if (corpseId == null) return -1;
    MonStats.Entry helper = Riiablo.files.monstats.get(corpseId);
    if (helper == null) throw new IllegalStateException("Missing corpse row " + corpseId);
    int corpse = factory.createMonster(helper.hcIdx, x + 5, 123);
    zone.attachEntity(corpse);
    world.getMapper(Monster.class).get(corpse).setMinionOwner(subject);
    // Fixture supplies a terminal corpse, like skill-viewer. Never inject the resurrection skill.
    world.getMapper(AttributesWrapper.class).get(corpse).attrs.get(Stat.hitpoints, StatRef.obtain()).set(0f);
    world.getSystem(EventSystem.class).dispatch(DeathEvent.obtain(subject, corpse));
    world.getSystem(CofManager.class).setMode(corpse, Engine.Monster.MODE_DD);
    if (!world.getMapper(Corpse.class).has(corpse)) throw new IllegalStateException("Corpse lifecycle not created");
    result.helpers += "corpse:" + corpseId + ":" + corpse;
    capture.line("fixture terminalCorpse=" + corpse + " owner=" + subject);
    return corpse;
  }

  static final class Probe extends PassiveSystem {
    int subject = -1;
    final CaseLog capture;
    Probe(CaseLog capture) { this.capture = capture; }
    @Subscribe public void action(SkillDoEvent event) {
      if (event.entityId != subject) return;
      capture.result.actions++;
      capture.line("SkillDo subject=" + subject + " skill=" + event.skillId + " target=" + event.targetId
          + " srvdofunc=" + event.srvdofunc);
    }
    @Subscribe public void damage(DamageEvent event) {
      if (event.attacker != subject) return;
      capture.result.damageEvents++;
      capture.result.damage += event.damage;
      capture.line("Damage subject=" + subject + " victim=" + event.victim + " amount=" + event.damage);
    }
  }

  static final class CaseLog implements AutoCloseable {
    final Result result;
    final BufferedWriter output;
    int tick = -1;
    CaseLog(Path path, Result result) throws IOException {
      this.result = result;
      output = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
      for (Logger logger : new ArrayList<>(LogManager.getRegistry().getLoggers().values())) {
        logger.addAppender(new Capture(logger.getAppender()));
      }
      line("BEGIN monster=" + result.monster + " scenario=" + result.scenario + " seed=" + result.seed
          + " difficulty=" + result.difficulty + " level=" + result.arenaLevel
          + " fixture=synthetic_open_arena playerHP=1000000 oracle=NONE");
    }
    synchronized void line(String text) {
      try { output.write("tick=" + tick + " " + text + "\n"); }
      catch (IOException error) { throw new UncheckedIOException(error); }
    }
    final class Capture implements Appender {
      final Appender previous;
      Capture(Appender previous) { this.previous = previous; }
      @Override public Encoder encoder() { return previous.encoder(); }
      @Override public void append(LogEvent event) {
        try {
          if (event.level().isMoreSpecificThan(Level.WARN)) {
            if (event.level() == Level.WARN) result.warnings++; else result.errors++;
            line(event.level() + " " + event.message().format());
          }
        } finally { previous.append(event); }
      }
    }
    @Override public void close() throws IOException {
      for (Logger logger : new ArrayList<>(LogManager.getRegistry().getLoggers().values())) {
        if (logger.getAppender() instanceof CaseLog.Capture) {
          logger.addAppender(((Capture) logger.getAppender()).previous);
        }
      }
      output.close();
    }
  }

  private void report(MonsterAiCatalog catalog, List<Result> results) throws IOException {
    java.util.Map<String, Integer> statuses = new TreeMap<>();
    StringBuilder html = new StringBuilder("<!doctype html><meta charset=\"utf-8\"><title>Monster AI arena</title>"
        + "<style>body{font:14px system-ui;margin:24px}table{border-collapse:collapse}td,th{border:1px solid #bbb;padding:5px}"
        + "th{position:sticky;top:0;background:#eee}pre{white-space:pre-wrap}</style><h1>逐怪 AI 测试记录</h1>"
        + "<p>仅空地基线与已实现辅助场景；观察到动作 ≠ 原版逻辑正确。无网络、地形、任务或完整 Boss 场景验收。"
        + "每场重新创建 World/角色，主体独立归因；警告、fallback、未触发、未运行保留。玩家 HP=1000000，不主动攻击。</p><pre>");
    html.append(escape(catalog.summary())).append("</pre><p>seed=").append(config.seed)
        .append(" difficulty=").append(config.difficulty).append(" ticks/case=").append(config.ticks)
        .append(" (25Hz accelerated simulation)</p><label>筛选怪物 / AI / 状态 / Act： <input id=filter></label>"
            + "<table id=results><thead><tr>");
    for (String header : new String[] {"Monster", "Act", "AI / actual", "Scenario", "Status", "Ticks", "Actions",
        "Moves", "Damage events", "Warnings", "Special", "Helpers", "Sources", "Error / log"}) html.append("<th>").append(header).append("</th>");
    html.append("</tr></thead><tbody>");
    for (Result result : results) {
      statuses.merge(result.status, 1, Integer::sum);
      html.append("<tr>");
      for (Object value : new Object[] {result.monster, result.acts, result.ai + " / " + result.actualAi,
          result.scenario, result.status, result.ticks, result.actions, result.moves, result.damageEvents,
          result.warnings, result.special, result.helpers, result.sources}) html.append("<td>").append(escape(String.valueOf(value))).append("</td>");
      html.append("<td>").append(escape(result.error));
      if (!result.status.equals("NOT_RUN")) html.append(" <a href=\"").append(escape(result.log)).append("\">log</a>");
      html.append("</td></tr>");
    }
    html.append("</tbody></table><pre>").append(escape(statuses.toString())).append("</pre>"
        + "<script>document.getElementById('filter').addEventListener('input',function(){"
        + "var q=this.value.toLowerCase();document.querySelectorAll('#results tbody tr').forEach(function(r){"
        + "r.hidden=!r.textContent.toLowerCase().includes(q);});});</script>");
    Files.write(config.output.resolve("report.html"), html.toString().getBytes(StandardCharsets.UTF_8));
    Files.write(config.output.resolve("summary.txt"), (catalog.summary() + statuses + "\nseed=" + config.seed
        + " difficulty=" + config.difficulty + " ticksPerCase=" + config.ticks + "\n").getBytes(StandardCharsets.UTF_8));
  }

  static String escape(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }
}
