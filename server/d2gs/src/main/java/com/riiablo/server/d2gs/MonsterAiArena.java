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
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.event.AnimDataFinishedEvent;
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
 * No network, renderer, terrain or quest simulation; only named special scenarios
 * have narrow lifecycle assertions, not a general AI-correctness oracle.
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
    if ("Tentacle".equals(monster.AI) || "TentacleHead".equals(monster.AI)) scenarios.add("submerge-cycle");
    if ("FrogDemon".equals(monster.AI)) scenarios.add("frog-emerge");
    if ("FetishShaman".equals(monster.AI) || "Diablo".equals(monster.AI)
        || "FrozenHorror".equals(monster.AI)) scenarios.add("inferno-cycle");
    if ("Diablo".equals(monster.AI)) scenarios.add("diab-cold");
    if ("Diablo".equals(monster.AI)) scenarios.add("diab-wall");
    if ("Diablo".equals(monster.AI)) scenarios.add("diab-prison");
    if ("Diablo".equals(monster.AI)) scenarios.add("diab-run");
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
      if (result.scenario.equals("diab-prison") || result.scenario.equals("diab-wall")) {
        // A normal melee fixture can break an attackable prison, then walk out.
        stats.base().put(Stat.mindamage, 1000); stats.base().put(Stat.maxdamage, 1000);
        stats.base().put(Stat.tohit, 10000);
        stats.base().put(Stat.mana, 100); stats.base().put(Stat.maxmana, 100);
      }
      stats.reset();
      float distance = result.scenario.equals("near") || result.scenario.equals("submerge-cycle")
          || result.scenario.equals("inferno-cycle") || result.scenario.equals("diab-cold") ? 3 : 18;
      // Keep full footprints inside the arena; (0,0) is also a legacy path sentinel.
      int playerId = factory.createPlayer(player, new Vector2(128, 128));
      zone.attachEntity(playerId);
      int subject = factory.createMonster(row.monster.hcIdx, 128 + distance, 128);
      if (subject == Engine.INVALID_ENTITY) throw new IllegalStateException("Factory rejected subject");
      zone.attachEntity(subject);
      probe.subject = subject;
      probe.frogSubject = "FrogDemon".equals(row.monster.AI);
      if (result.scenario.equals("diab-prison")) {
        result.specialAssert = true; result.special = "NOT_TRIGGERED";
        result.coverage = "BASELINE_PLUS_DIAB_PRISON"; probe.prisonPhase = 0;
      }
      if (result.scenario.equals("diab-run")) {
        result.specialAssert = true;
        result.special = "NOT_TRIGGERED";
        result.coverage = "BASELINE_PLUS_DIAB_RUN";
        probe.diabRun = new DiabRunCycle();
      }
      if (result.scenario.equals("diab-wall")) {
        result.specialAssert = true;
        result.special = "NOT_TRIGGERED";
        result.coverage = "BASELINE_PLUS_DIAB_WALL";
        probe.diabWall = new DiabWallCycle();
      }
      if (result.scenario.equals("diab-cold")) {
        result.specialAssert = true;
        result.special = "NOT_TRIGGERED";
        result.coverage = "BASELINE_PLUS_DIAB_COLD";
        probe.diabCold = new DiabColdCycle();
      }
      if (result.scenario.equals("inferno-cycle")) {
        result.specialAssert = true;
        result.special = "NOT_TRIGGERED";
        result.coverage = "BASELINE_PLUS_INFERNO_CYCLE";
        probe.inferno = new InfernoCycle("Diablo".equals(row.monster.AI)
            || "FrozenHorror".equals(row.monster.AI));
      }
      if (result.scenario.equals("submerge-cycle")) {
        result.specialAssert = true;
        result.special = "NOT_TRIGGERED";
        result.coverage = "BASELINE_PLUS_SUBMERGE_CYCLE";
        probe.cycle = new SubmergeCycle();
        probe.emergeSkill = Riiablo.files.skills.get(row.monster.Skill2).Id;
      }
      if (result.scenario.equals("frog-emerge")) {
        result.specialAssert = true;
        result.special = "NOT_TRIGGERED";
        result.coverage = "BASELINE_PLUS_FROG_EMERGENCE";
        probe.frog = new FrogCycle();
        probe.emergeSkill = Riiablo.files.skills.get(row.monster.Skill2).Id;
      }
      AIWrapper ai = world.getMapper(AIWrapper.class).get(subject);
      result.actualAi = ai.ai.getClass().getSimpleName();
      result.fallback = ai.ai instanceof GenericMonster;
      int corpse = helpers(world, factory, zone, row.monster, subject, 128 + distance, result, capture);
      Vector2 previous = world.getMapper(Position.class).get(subject).position.cpy();
      boolean frogApproachRequested = false;
      boolean wallRunSeen = false;
      for (int tick = 0; tick < config.ticks; tick++) {
        capture.tick = tick + 1;
        if (result.scenario.equals("diab-wall") && probe.diabWall.phase == 0
            || result.scenario.equals("diab-prison") && probe.prisonPhase == 0) {
          Casting current = world.getMapper(Casting.class).get(subject);
          if (current != null && current.diabRun != null) wallRunSeen = true;
          Vector2 actor = world.getMapper(Position.class).get(subject).position;
          Vector2 playerPos = world.getMapper(Position.class).get(playerId).position;
          if (wallRunSeen && (current == null || current.diabRun == null)
              && playerPos.dst2(actor) < 100
              && !world.getMapper(Casting.class).has(playerId)
              && !world.getMapper(com.riiablo.engine.server.component.Pathfind.class).has(playerId)) {
            // DiabRun now closes distance legitimately. Reopen ranged space
            // through ordinary player movement, not a forced wall selection.
            Vector2 retreat = playerPos.cpy().sub(actor).nor().scl(16).add(playerPos);
            if (world.getSystem(Pathfinder.class).findPath(playerId, retreat, false, Engine.INVALID_ENTITY))
              capture.line("fixture wallRetreat=" + playerId + " destination=" + retreat);
            else if (!attackNearbyPrison(world, playerId, capture))
              throw new IllegalStateException("DiabWall retreat path rejected without attackable prison");
          }
        }
        simulation.tick(null, null);
        result.ticks++;
        if (!world.getEntityManager().isActive(subject)) {
          if (result.scenario.equals("orphan")) { result.specialPassed = true; break; }
          throw new IllegalStateException("Subject removed; terminal lifecycle requires a dedicated scenario");
        }
        Vector2 position = world.getMapper(Position.class).get(subject).position;
        probe.observeCycle();
        if (result.scenario.equals("diab-prison") && probe.prisonPhase >= 2 && probe.prisonPhase < 4) {
          if (probe.prisonPhase == 2) attackNearbyPrison(world, playerId, capture);
          if (probe.prisonPhase == 3 && !world.getMapper(com.riiablo.engine.server.component.Pathfind.class).has(playerId)
              && !world.getMapper(Casting.class).has(playerId)) {
            Vector2 exit = probe.prisonCenter.cpy().add(-8, 0);
            if (!world.getSystem(Pathfinder.class).findPath(playerId, exit, false, -1))
              throw new IllegalStateException("Broken prison still blocks exit");
            capture.line("fixture prisonExit=" + playerId + " destination=" + exit);
          }
        }
        if (probe.frog != null && probe.frog.phase == 1 && !frogApproachRequested) {
          // Stimulate the native distance gate with an ordinary player path,
          // not a warp, phase write, injected skill or changed AI parameter.
          Vector2 destination = new Vector2(position).add(-6, 0);
          if (!world.getSystem(Pathfinder.class).findPath(playerId, destination, false, Engine.INVALID_ENTITY)) {
            throw new IllegalStateException("Frog stimulus player path rejected");
          }
          frogApproachRequested = true;
          capture.line("fixture playerApproach=" + playerId + " destination=" + destination);
        }
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
        if (tick % 25 == 0 && "FrogDemon".equals(result.actualAi)) {
          Sequence seq = world.getMapper(Sequence.class).get(subject);
          AnimData anim = world.getMapper(AnimData.class).get(subject);
          Monster monster = world.getMapper(Monster.class).get(subject);
          capture.line("frogSample hidden=" + (seq != null && seq.nativeFrogHidden)
              + " held=" + (seq != null && seq.nativeSubmergeHeld)
              + " frame=" + (anim == null ? -1 : anim.frame)
              + " noPresence=" + (monster != null && monster.frogNoPresence)
              + " presence=" + probe.present() + " flags=" + probe.flags()
              + " playerPos=" + world.getMapper(Position.class).get(playerId).position);
        }
        if (corpse >= 0 && world.getMapper(Monster.class).has(corpse)) {
          Monster revived = world.getMapper(Monster.class).get(corpse);
          if (revived.resurrected && revived.resurrectedBy == subject) result.specialPassed = true;
        }
      }
      if (result.specialAssert) result.special = result.specialPassed
          ? (result.scenario.equals("orphan") ? "OWNERLESS_DEATH_OBSERVED"
              : result.scenario.equals("submerge-cycle") ? "SUBMERGE_CYCLE_OBSERVED"
              : result.scenario.equals("frog-emerge") ? "FROG_EMERGENCE_OBSERVED"
              : result.scenario.equals("inferno-cycle") ? "INFERNO_CYCLE_OBSERVED"
              : result.scenario.equals("diab-cold") ? "DIAB_COLD_OBSERVED"
              : result.scenario.equals("diab-wall") ? "DIAB_WALL_OBSERVED"
              : result.scenario.equals("diab-run") ? "DIAB_RUN_OBSERVED" : "RESURRECTION_OBSERVED") : "NOT_TRIGGERED";
      if (result.scenario.equals("diab-prison") && result.specialPassed) result.special = "DIAB_PRISON_OBSERVED";
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

  private static boolean attackNearbyPrison(World world, int player, CaseLog capture) {
    com.artemis.utils.IntBag ids = world.getAspectSubscriptionManager().get(Aspect.all(
        com.riiablo.engine.server.component.DiabPrison.class)).getEntities();
    Vector2 position = world.getMapper(Position.class).get(player).position;
    for (int i = 0; i < ids.size(); i++) {
      int id = ids.get(i);
      if (world.getMapper(AttributesWrapper.class).get(id).attrs.get(Stat.hitpoints).asFixed() <= 0) continue;
      if (position.dst2(world.getMapper(Position.class).get(id).position) > 9) continue;
      if (!world.getMapper(Casting.class).has(player)) {
        boolean accepted = world.getSystem(Actioneer.class).castAndReport(player, com.riiablo.skill.SkillCodes.attack, id,
            world.getMapper(Position.class).get(id).position.cpy());
        capture.line("fixture prisonAttack player=" + player + " target=" + id + " accepted=" + accepted);
      }
      return true;
    }
    return false;
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
    int emergeSkill = -1;
    boolean frogSubject;
    SubmergeCycle cycle;
    FrogCycle frog;
    InfernoCycle inferno;
    int horrorVictim = -1;
    float horrorLifeBefore, horrorDamage;
    boolean horrorDamageObserved;
    DiabColdCycle diabCold;
    DiabWallCycle diabWall;
    DiabRunCycle diabRun;
    int prisonPhase = -1, prisonTarget = -1;
    boolean prisonCastValid, prisonHit;
    Vector2 prisonCenter;
    com.badlogic.gdx.utils.IntArray prisonMembers;
    com.riiablo.engine.server.NativeDiabRun runCast;
    com.badlogic.gdx.math.Vector2 runStart;
    int runTarget = -1;
    boolean runHitPending, runFinished;
    float runHpBefore, runDamage;
    boolean pendingWall, wallCastValid, wallFinished, wallDamagePending;
    int wallCastId, wallTarget = -1, wallLevel;
    float wallLifeBefore, wallHitDamage;
    final Set<Integer> wallMakers = new TreeSet<>();
    SkillDoEvent pendingCold;
    float coldLifeBefore, coldDamage;
    int coldMissilesBefore, coldDamageEvents;
    boolean coldPacketValid, coldCastValid;
    com.riiablo.engine.server.NativeMonInferno lastInferno;
    int lastInfernoCount;
    final CaseLog capture;
    Probe(CaseLog capture) { this.capture = capture; }
    @Subscribe public void start(com.riiablo.engine.server.event.SkillStartEvent event) {
      if (event.entityId != subject || !frogSubject) return;
      capture.line("FrogSkillStart subject=" + subject + " skill=" + event.skillId
          + " target=" + event.targetId + " srvstfunc=" + event.srvstfunc);
    }
    @Subscribe public void action(SkillDoEvent event) {
      if (event.entityId != subject) return;
      if (prisonPhase == 0 && event.skillId == 199 && event.srvdofunc == 104) {
        Casting cast = world.getMapper(Casting.class).get(subject);
        Sequence seq = world.getMapper(Sequence.class).get(subject);
        prisonCastValid = cast != null && cast.skillId == 199 && seq != null
            && seq.started && seq.mode1 == Engine.Monster.MODE_S3;
        prisonTarget = event.targetId;
        prisonCenter = world.getMapper(Position.class).get(prisonTarget).position.cpy();
      }
      if (diabWall != null && diabWall.phase == 0 && !pendingWall && event.skillId == 197 && event.srvdofunc == 102) {
        pendingWall = true; wallTarget = event.targetId;
        wallLevel = world.getMapper(Monster.class).get(subject).monstats.Sk4lvl;
        Casting cast = world.getMapper(Casting.class).get(subject);
        Sequence seq = world.getMapper(Sequence.class).get(subject);
        wallCastValid = cast != null && cast.skillId == 197 && seq != null
            && seq.started && seq.mode1 == Engine.Monster.MODE_S3;
      }
      if (diabCold != null && event.skillId == 194 && event.srvdofunc == 100) {
        pendingCold = event;
        AttributesWrapper attrs = world.getMapper(AttributesWrapper.class).get(event.targetId);
        coldLifeBefore = attrs == null ? -1 : attrs.attrs.get(Stat.hitpoints).asFixed();
        coldMissilesBefore = missileCount(); coldDamage = 0; coldDamageEvents = 0; coldPacketValid = true;
        Casting cast = world.getMapper(Casting.class).get(subject);
        Sequence seq = world.getMapper(Sequence.class).get(subject);
        coldCastValid = cast != null && cast.skillId == 194 && seq != null
            && seq.started && seq.mode1 == Engine.Monster.MODE_S2;
      }
      capture.result.actions++;
      capture.line("SkillDo subject=" + subject + " skill=" + event.skillId + " target=" + event.targetId
          + " srvdofunc=" + event.srvdofunc);
      if (cycle != null) {
        CofReference cof = world.getMapper(CofReference.class).get(subject);
        int before = cycle.phase;
        cycle.attack(event.skillId == com.riiablo.skill.SkillCodes.attack
            && cof != null && cof.mode == Engine.Monster.MODE_A1, flags());
        cycleChanged(before);
      }
      if (frog != null) {
        CofReference cof = world.getMapper(CofReference.class).get(subject);
        int before = frog.phase;
        frog.attack(event.skillId == com.riiablo.skill.SkillCodes.attack && cof != null
            && (cof.mode == Engine.Monster.MODE_A1 || cof.mode == Engine.Monster.MODE_A2), flags(), present());
        frogChanged(before);
      }
    }
    void observeCycle() {
      if (horrorVictim >= 0) {
        float life = world.getMapper(AttributesWrapper.class).get(horrorVictim).attrs.get(Stat.hitpoints).asFixed();
        horrorDamageObserved |= horrorDamage > 0 && Math.abs(horrorLifeBefore - horrorDamage - life) < .01f;
        capture.line("HorrorColdHit victim=" + horrorVictim + " damage=" + horrorDamage
            + " hp=" + horrorLifeBefore + "->" + life + " valid=" + horrorDamageObserved);
        horrorVictim = -1;
      }
      if (prisonPhase >= 0) observePrison();
      if (diabRun != null) observeDiabRun();
      if (diabWall != null) observeDiabWall();
      if (diabCold != null && pendingCold != null) {
        int target = pendingCold.targetId;
        AttributesWrapper attrs = world.getMapper(AttributesWrapper.class).get(target);
        float life = attrs == null ? -1 : attrs.attrs.get(Stat.hitpoints).asFixed();
        com.riiablo.engine.server.component.UnitStates states = world.getMapper(
            com.riiablo.engine.server.component.UnitStates.class).get(target);
        com.riiablo.engine.server.state.UnitState cold = states == null || states.stateList == null ? null
            : states.stateList.getState(com.riiablo.engine.server.state.StateId.COLD);
        Monster monster = world.getMapper(Monster.class).get(subject);
        int expectedLength = com.riiablo.engine.server.NativeDiabCold.freezeLength(
            Riiablo.files.skills.get(194), monster.monstats.Sk2lvl);
        boolean valid = coldCastValid && coldDamageEvents == 1 && coldPacketValid && coldDamage > 0
            && Math.abs(coldLifeBefore - coldDamage - life) < .01f
            && cold != null && cold.sourceEntityId == subject && cold.initialDuration == expectedLength
            && !states.stateList.hasState(com.riiablo.engine.server.state.StateId.FREEZE)
            && coldMissilesBefore == missileCount();
        diabCold.hit(valid);
        capture.line("DiabColdHit subject=" + subject + " target=" + target + " valid=" + valid
            + " magic=" + coldDamage + " hp=" + coldLifeBefore + "->" + life
            + " coldLength=" + (cold == null ? -1 : cold.initialDuration) + " phase=" + diabCold.phase);
        pendingCold = null;
      }
      if (inferno != null) {
        com.riiablo.engine.server.component.UnitStates states = world.getMapper(
            com.riiablo.engine.server.component.UnitStates.class).get(subject);
        boolean enabled = states != null && states.stateList != null
            && states.stateList.hasState(com.riiablo.engine.server.state.StateId.INFERNO);
        boolean reset = inferno.resetObserved;
        inferno.observeState(enabled);
        if (!reset && inferno.resetObserved) capture.line("InfernoReset subject=" + subject + " enabled=false");
      }
      Casting activeCast = world.getMapper(Casting.class).get(subject);
      com.riiablo.engine.server.NativeMonInferno stream = activeCast == null ? null : activeCast.monInferno;
      if (stream != null) {
        if (stream != lastInferno) { lastInferno = stream; lastInfernoCount = 0; }
        boolean pulse = stream.missiles != lastInfernoCount;
        com.riiablo.engine.server.component.Missile missile = stream.lastMissileId < 0 ? null
            : world.getMapper(com.riiablo.engine.server.component.Missile.class).get(stream.lastMissileId);
        boolean valid = missile != null && missile.ownerId == subject
            && missile.skillId == activeCast.skillId && missile.nativeLifetimeFrames > 0;
        if (pulse) {
          capture.line("InfernoPulse subject=" + subject + " skill=" + activeCast.skillId
              + " frame=" + stream.frame + " missile=" + stream.lastMissileId + " valid=" + valid
              + " count=" + stream.missiles + " deadline=" + stream.deadline
              + " interval=" + stream.interval + " next=" + stream.nextUpdate + " end=" + stream.endFrame);
          lastInfernoCount = stream.missiles;
        }
        if (inferno != null) {
          int before = inferno.phase;
          inferno.observe(stream, !pulse || valid);
          if (before != inferno.phase) capture.line("InfernoCycle subject=" + subject + " phase=" + inferno.phase);
          if (inferno.phase == 5 && (!"FrozenHorror".equals(capture.result.ai) || horrorDamageObserved))
            capture.result.specialPassed = true;
        }
      }
      if (frog != null) {
        Sequence seq = world.getMapper(Sequence.class).get(subject);
        AnimData anim = world.getMapper(AnimData.class).get(subject);
        Casting cast = world.getMapper(Casting.class).get(subject);
        Monster monster = world.getMapper(Monster.class).get(subject);
        int before = frog.phase;
        frog.observe(seq != null && seq.nativeFrogHidden,
            seq != null && seq.started && seq.nativeSubmergeHeld,
            anim == null ? -1 : anim.frame, flags(), monster != null && monster.frogNoPresence,
            present(), cast != null && cast.skillId == emergeSkill && cast.targetId == subject
                && seq != null && seq.mode1 == Engine.Monster.MODE_S1);
        frogChanged(before);
      }
      if (cycle == null) return;
      Sequence seq = world.getMapper(Sequence.class).get(subject);
      AnimData anim = world.getMapper(AnimData.class).get(subject);
      Casting cast = world.getMapper(Casting.class).get(subject);
      int before = cycle.phase;
      cycle.observe(seq != null && seq.nativeSubmerge,
          seq != null && seq.started && seq.nativeSubmergeHeld,
          anim == null ? -1 : anim.frame, flags(),
          cast != null && cast.skillId == emergeSkill && cast.targetId == subject
              && seq != null && seq.mode1 == Engine.Monster.MODE_S1);
      cycleChanged(before);
    }
    @Subscribe public void finished(AnimDataFinishedEvent event) {
      if (diabRun != null && event.entityId == subject && runCast != null) {
        Casting cast = world.getMapper(Casting.class).get(subject);
        if (cast != null && cast.diabRun == runCast && runCast.tail && runCast.hitProcessed)
          runFinished = true;
      }
      if (diabWall != null && event.entityId == subject && pendingWall) {
        Casting cast = world.getMapper(Casting.class).get(subject);
        Sequence seq = world.getMapper(Sequence.class).get(subject);
        if (cast != null && cast.skillId == 197 && seq != null && seq.mode1 == Engine.Monster.MODE_S3)
          wallFinished = true;
      }
      if (diabCold != null && event.entityId == subject) {
        Casting cast = world.getMapper(Casting.class).get(subject);
        Sequence seq = world.getMapper(Sequence.class).get(subject);
        int before = diabCold.phase;
        diabCold.finished(cast != null && cast.skillId == 194 && seq != null && seq.mode1 == Engine.Monster.MODE_S2);
        if (before != diabCold.phase && diabCold.phase == 2) {
          capture.result.specialPassed = true;
          capture.line("DiabColdCycle subject=" + subject + " phase=2");
        }
      }
      if (inferno != null && event.entityId == subject) {
        int before = inferno.phase;
        inferno.finished();
        if (before != inferno.phase) capture.line("InfernoCycle subject=" + subject + " phase=" + inferno.phase);
      }
      if (frog != null && event.entityId == subject) {
        Casting cast = world.getMapper(Casting.class).get(subject);
        Sequence seq = world.getMapper(Sequence.class).get(subject);
        int before = frog.phase;
        frog.finished(cast != null && cast.skillId == emergeSkill && cast.targetId == subject
            && seq != null && !seq.nativeFrogHidden && seq.mode1 == Engine.Monster.MODE_S1, flags(), present());
        frogChanged(before);
      }
      if (cycle == null || event.entityId != subject) return;
      Casting cast = world.getMapper(Casting.class).get(subject);
      Sequence seq = world.getMapper(Sequence.class).get(subject);
      int before = cycle.phase;
      cycle.finished(cast != null && cast.skillId == emergeSkill && cast.targetId == subject
          && seq != null && !seq.nativeSubmerge && seq.mode1 == Engine.Monster.MODE_S1, flags());
      cycleChanged(before);
    }
    private int flags() {
      NativeUnitFlags flags = world.getMapper(NativeUnitFlags.class).get(subject);
      return flags == null ? -1 : flags.flags();
    }
    private boolean present() {
      DynamicUnitCollisionSystem collisions = world.getSystem(DynamicUnitCollisionSystem.class);
      return collisions != null && collisions.hasPresence(subject);
    }
    private void frogChanged(int before) {
      if (before == frog.phase) return;
      capture.line("FrogCycle subject=" + subject + " phase=" + frog.phase
          + " flags=" + flags() + " presence=" + present());
      if (frog.phase == 4) capture.result.specialPassed = true;
    }
    private void cycleChanged(int before) {
      if (before == cycle.phase) return;
      capture.line("SubmergeCycle subject=" + subject + " phase=" + cycle.phase + " flags=" + flags());
      if (cycle.phase == 4) capture.result.specialPassed = true;
    }
    @Subscribe public void damage(DamageEvent event) {
      if (prisonPhase == 2 && event.attacker == prisonTarget && prisonMembers != null
          && prisonMembers.contains(event.victim) && event.damage > 0 && event.physicalDamage > 0) {
        prisonHit = true;
        capture.line("PrisonPlayerHit victim=" + event.victim + " damage=" + event.damage);
      }
      if (event.attacker != subject) return;
      if (inferno != null && "FrozenHorror".equals(capture.result.ai) && event.missileId >= 0) {
        com.riiablo.engine.server.component.Missile missile = world.getMapper(
            com.riiablo.engine.server.component.Missile.class).get(event.missileId);
        if (missile != null && missile.skillId == 323 && missile.ownerId == subject
            && event.kind == DamageEvent.MISSILE && event.damage > 0 && event.damage == event.coldDamage
            && event.physicalDamage == 0 && event.fireDamage == 0 && event.lightningDamage == 0
            && event.magicDamage == 0 && event.poisonDamage == 0) {
          if (horrorVictim < 0) {
            horrorLifeBefore = world.getMapper(AttributesWrapper.class).get(event.victim).attrs.get(Stat.hitpoints).asFixed();
            horrorDamage = 0;
          }
          horrorVictim = event.victim;
          horrorDamage += event.damage;
        }
      }
      if (diabRun != null && runCast != null && event.victim == runTarget && diabRun.phase == 2) {
        Casting cast = world.getMapper(Casting.class).get(subject);
        AnimData anim = world.getMapper(AnimData.class).get(subject);
        if (cast != null && cast.diabRun == runCast && runCast.tail && runCast.hitProcessed
            && anim != null && anim.frame >>> 8 == 18 && event.kind == DamageEvent.MISSILE
            && event.damage >= 53 && event.damage < 69 && event.physicalDamage == event.damage
            && event.fireDamage == 0 && event.coldDamage == 0 && event.lightningDamage == 0
            && event.magicDamage == 0 && event.poisonDamage == 0) {
          runHpBefore = world.getMapper(AttributesWrapper.class).get(runTarget).attrs.get(Stat.hitpoints).asFixed();
          runDamage = event.damage; runHitPending = true;
          capture.line("DiabRunHit target=" + runTarget + " damage=" + runDamage + " hpBefore=" + runHpBefore);
        }
      }
      if (diabWall != null && event.missileId >= 0 && event.victim == wallTarget) {
        com.riiablo.engine.server.component.Missile fire = world.getMapper(
            com.riiablo.engine.server.component.Missile.class).get(event.missileId);
        if (fire != null && fire.diabWallFire && fire.diabWallCastId == wallCastId
            && fire.ownerId == subject && wallMakers.contains(fire.diabWallParentId)
            && event.kind == DamageEvent.MISSILE && event.fireDamage > 0 && event.damage == event.fireDamage
            && event.physicalDamage == 0 && event.coldDamage == 0 && event.lightningDamage == 0
            && event.magicDamage == 0 && event.poisonDamage == 0) {
          wallLifeBefore = world.getMapper(AttributesWrapper.class).get(event.victim).attrs.get(Stat.hitpoints).asFixed();
          wallHitDamage = event.damage; wallDamagePending = true;
          capture.line("DiabWallHit missile=" + event.missileId + " parent=" + fire.diabWallParentId
              + " cast=" + wallCastId + " damage=" + event.damage + " hpBefore=" + wallLifeBefore);
        }
      }
      if (pendingCold != null && event.victim == pendingCold.targetId) {
        coldDamageEvents++;
        coldDamage += event.damage;
        coldPacketValid &= event.kind == DamageEvent.MISSILE && event.magicDamage > 0
            && event.damage == event.magicDamage && event.physicalDamage == 0 && event.coldDamage == 0
            && event.fireDamage == 0 && event.lightningDamage == 0 && event.poisonDamage == 0;
      }
      capture.result.damageEvents++;
      capture.result.damage += event.damage;
      capture.line("Damage subject=" + subject + " victim=" + event.victim + " amount=" + event.damage);
    }
    private void observePrison() {
      int before = prisonPhase;
      DynamicUnitCollisionSystem collisions = world.getSystem(DynamicUnitCollisionSystem.class);
      if (prisonPhase == 0 && prisonCastValid) {
        com.artemis.utils.IntBag ids = world.getAspectSubscriptionManager().get(Aspect.all(
            com.riiablo.engine.server.component.DiabPrison.class)).getEntities();
        if (ids.size() == 4) {
          com.badlogic.gdx.utils.IntArray group = world.getMapper(
              com.riiablo.engine.server.component.DiabPrison.class).get(ids.get(0)).members;
          boolean valid = group != null && group.size == 4;
          int[][] offsets = {{1,1},{1,-1},{-1,-1},{-1,1}};
          for (int i = 0; valid && i < 4; i++) {
            int id = group.get(i); Monster m = world.getMapper(Monster.class).get(id);
            valid &= m.monstats.hcIdx == 340+i && m.minionOwnerId == group.first()
                && !world.getMapper(com.riiablo.engine.server.component.SummonedPet.class).has(id)
                && world.getMapper(AttributesWrapper.class).get(id).attrs.get(Stat.hitpoints).asFixed() > 0
                && world.getMapper(Position.class).get(id).position.epsilonEquals(
                    (int)prisonCenter.x+offsets[i][0], (int)prisonCenter.y+offsets[i][1], .001f)
                && collisions.hasPresence(id);
          }
          if (valid) { prisonMembers = group; prisonPhase = 1; }
        }
      }
      if (prisonPhase == 1) {
        Casting cast = world.getMapper(Casting.class).get(subject);
        boolean risen = true;
        for (int i=0;i<prisonMembers.size;i++) risen &= world.getMapper(CofReference.class)
            .get(prisonMembers.get(i)).mode == Engine.Monster.MODE_NU;
        if (risen && (cast == null || cast.skillId != 199)) {
          boolean blocked = !world.getSystem(Pathfinder.class).findPath(prisonTarget,
              prisonCenter.cpy().add(-8,0), false, -1);
          if (blocked) prisonPhase = 2;
        }
      }
      if (prisonPhase == 2 && prisonHit) {
        boolean dead = true;
        for (int i=0;i<prisonMembers.size;i++) {
          int id=prisonMembers.get(i);
          dead &= world.getMapper(AttributesWrapper.class).get(id).attrs.get(Stat.hitpoints).asFixed() <= 0
              && !collisions.hasPresence(id) && world.getMapper(
                  com.riiablo.engine.server.component.DiabPrison.class).get(id).members == null;
        }
        if (dead) prisonPhase = 3;
      }
      if (prisonPhase == 3 && world.getMapper(Position.class).get(prisonTarget).position.dst2(prisonCenter) > 9) {
        prisonPhase = 4; capture.result.specialPassed = true;
      }
      if (before != prisonPhase) capture.line("DiabPrisonCycle subject=" + subject + " phase=" + prisonPhase
          + " members=" + prisonMembers + " playerHit=" + prisonHit);
    }

    private void observeDiabRun() {
      Casting cast = world.getMapper(Casting.class).get(subject);
      Sequence seq = world.getMapper(Sequence.class).get(subject);
      int before = diabRun.phase;
      if (runCast == null && cast != null && cast.diabRun != null) {
        runCast = cast.diabRun; runTarget = runCast.targetId;
        runStart = world.getMapper(Position.class).get(subject).position.cpy();
        diabRun.start(cast.skillId == 198 && seq != null && seq.nativeDiabRun
            && seq.mode1 == Engine.Monster.MODE_RN && !runCast.pathStarted);
      }
      if (runCast == null) return;
      com.riiablo.engine.server.component.Pathfind path = world.getMapper(
          com.riiablo.engine.server.component.Pathfind.class).get(subject);
      if (runCast.moving && path != null)
        diabRun.move(path.targetEntityId == Engine.INVALID_ENTITY
            && path.destination.dst2(runCast.destination) < runStart.dst2(runCast.destination)
            && world.getMapper(Position.class).get(subject).position.dst2(runStart) > .25f);
      if (runHitPending) {
        float life = world.getMapper(AttributesWrapper.class).get(runTarget).attrs.get(Stat.hitpoints).asFixed();
        diabRun.hit(life <= runHpBefore - runDamage + .001f);
        runHitPending = false;
      }
      diabRun.end(runFinished && (cast == null || cast.diabRun != runCast) && path == null);
      if (before != diabRun.phase) capture.line("DiabRunCycle subject=" + subject + " phase=" + diabRun.phase
          + " cursor=" + (runCast.cursor >>> 8) + " loops=" + runCast.loops + " tail=" + runCast.tail);
      if (diabRun.phase == 4) capture.result.specialPassed = true;
    }

    private void observeDiabWall() {
      if (!pendingWall) return;
      com.artemis.utils.IntBag ids = world.getAspectSubscriptionManager().get(Aspect.all(
          com.riiablo.engine.server.component.Missile.class)).getEntities();
      int before = diabWall.phase, makers = 0, children = 0;
      boolean validMakers = wallCastValid;
      for (int i = 0; i < ids.size(); i++) {
        int id = ids.get(i);
        com.riiablo.engine.server.component.Missile m = world.getMapper(
            com.riiablo.engine.server.component.Missile.class).get(id);
        if (m.ownerId != subject) continue;
        if (m.diabWallMaker && (wallCastId == 0 || m.diabWallCastId == wallCastId)) {
          if (wallCastId == 0) wallCastId = m.diabWallCastId;
          makers++; wallMakers.add(id);
          validMakers &= m.nativeLifetimeFrames == 77 && m.damageLevel == wallLevel && m.skillId == 0;
        }
        if (m.diabWallFire && m.diabWallCastId == wallCastId && wallMakers.contains(m.diabWallParentId)) {
          children++;
          diabWall.trail(m.persistent && m.fixedElementalRate && m.skillId == 0 && m.damageLevel == wallLevel
              && m.nativeLifetimeFrames == 36 && m.elementalDamageRate == 41
              && m.elementalMinRateFixed == (wallLevel == 3 ? 176 : 136)
              && m.elementalMaxRateFixed == (wallLevel == 3 ? 280 : 240));
        }
      }
      if (diabWall.phase == 0 && makers > 0) diabWall.cast(validMakers && makers == wallLevel);
      if (wallDamagePending) {
        float life = world.getMapper(AttributesWrapper.class).get(wallTarget).attrs.get(Stat.hitpoints).asFixed();
        diabWall.damage(life <= wallLifeBefore - wallHitDamage + .001f);
        wallDamagePending = false;
      }
      diabWall.cleared(wallFinished && makers == 0 && children == 0);
      if (before != diabWall.phase) capture.line("DiabWallCycle subject=" + subject + " phase=" + diabWall.phase
          + " cast=" + wallCastId + " makers=" + makers + " children=" + children + " s3Finished=" + wallFinished);
      if (diabWall.phase == 4) capture.result.specialPassed = true;
    }

    private int missileCount() {
      return world.getAspectSubscriptionManager().get(com.artemis.Aspect.all(
          com.riiablo.engine.server.component.Missile.class)).getEntities().size();
    }
  }

  /** Natural St54 -> actual coordinate-path movement -> action18 HP loss -> tail cleanup. */
  static final class DiabRunCycle {
    int phase;
    void start(boolean valid) { if (phase == 0 && valid) phase = 1; }
    void move(boolean valid) { if (phase == 1 && valid) phase = 2; }
    void hit(boolean valid) { if (phase == 2 && valid) phase = 3; }
    void end(boolean valid) { if (phase == 3 && valid) phase = 4; }
  }

  /** Real cast -> table-owned trail -> actual HP loss -> S3/first-cast cleanup. */
  static final class DiabWallCycle {
    int phase;
    void cast(boolean valid) { if (phase == 0 && valid) phase = 1; }
    void trail(boolean valid) { if (phase == 1 && valid) phase = 2; }
    void damage(boolean valid) { if (phase == 2 && valid) phase = 3; }
    void cleared(boolean valid) { if (phase == 3 && valid) phase = 4; }
  }

  /** Direct hit must independently change HP/state, then finish the real S2. */
  static final class DiabColdCycle {
    int phase;
    void hit(boolean valid) { if (phase == 0 && valid) phase = 1; }
    void finished(boolean realS2) { if (phase == 1 && realS2) phase = 2; }
  }

  /** Ordered, subject-only lifecycle oracle; a cast or helper action cannot satisfy it. */
  /** One natural cast: actual pulses, native interval/deadline/tail, then a fresh cast. */
  static final class InfernoCycle {
    com.riiablo.engine.server.NativeMonInferno cast;
    int phase, count, lastFrame;
    boolean valid = true;
    final boolean requireReset;
    boolean resetObserved;
    InfernoCycle() { this(false); }
    InfernoCycle(boolean requireReset) { this.requireReset = requireReset; }
    void observeState(boolean enabled) {
      if (phase == 4 && !enabled) resetObserved = true;
    }
    void observe(com.riiablo.engine.server.NativeMonInferno next, boolean realMissile) {
      if (phase == 5 || next == null) return;
      if (next != cast) {
        if (phase == 4 && valid && (!requireReset || resetObserved)) { phase = 5; return; }
        cast = next; phase = 1; count = 0; lastFrame = 0; valid = true; resetObserved = false;
      }
      if (next.missiles != count) {
        valid &= realMissile && next.missiles == count + 1
            && (count == 0 || next.frame - lastFrame == next.interval);
        count = next.missiles; lastFrame = next.frame;
        if (valid && count >= 2) phase = 2;
        if (phase == 2 && next.frame >= next.deadline && next.nextUpdate == -1
            && next.endFrame == next.frame + next.tailFrames) phase = 3;
      }
    }
    void finished() {
      if (phase == 3 && valid && cast.frame == cast.endFrame) phase = 4;
    }
  }

  static final class SubmergeCycle {
    int phase;
    void observe(boolean submerge, boolean held, int frame, int flags, boolean emerging) {
      if (flags < 0) return;
      if (phase == 0 && submerge && held && frame == 0
          && (flags & NativeUnitFlags.MONSTER_TARGET) == 0) phase = 1;
      else if (phase == 1 && !submerge && emerging && targetable(flags)) phase = 2;
    }
    void finished(boolean emergence, int flags) {
      if (phase == 2 && emergence && targetable(flags)) phase = 3;
    }
    void attack(boolean ordinaryA1, int flags) {
      if (phase == 3 && ordinaryA1 && targetable(flags)) phase = 4;
    }
    private boolean targetable(int flags) {
      return flags >= 0 && (flags & NativeUnitFlags.MONSTER_TARGET) == NativeUnitFlags.MONSTER_TARGET;
    }
  }

  /** Frog-specific oracle also requires the production collision-presence boundary. */
  static final class FrogCycle {
    int phase;
    void observe(boolean hidden, boolean held, int frame, int flags,
        boolean noPresence, boolean present, boolean emerging) {
      if (phase == 0 && hidden && held && frame == 0 && noPresence && !present
          && flags >= 0 && (flags & NativeUnitFlags.MONSTER_TARGET) == 0) phase = 1;
      else if (phase == 1 && !hidden && emerging && !noPresence && present && targetable(flags)) phase = 2;
    }
    void finished(boolean emergence, int flags, boolean present) {
      if (phase == 2 && emergence && present && targetable(flags)) phase = 3;
    }
    void attack(boolean ordinaryAttack, int flags, boolean present) {
      if (phase == 3 && ordinaryAttack && present && targetable(flags)) phase = 4;
    }
    private boolean targetable(int flags) {
      return flags >= 0 && (flags & NativeUnitFlags.MONSTER_TARGET) == NativeUnitFlags.MONSTER_TARGET;
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
          + " fixture=synthetic_open_arena playerHP=1000000 oracle="
          + ("submerge-cycle".equals(result.scenario) ? "SUBMERGE_CYCLE"
              : "frog-emerge".equals(result.scenario) ? "FROG_EMERGENCE"
              : "inferno-cycle".equals(result.scenario) ? "INFERNO_CYCLE"
              : "diab-cold".equals(result.scenario) ? "DIAB_COLD"
              : "diab-wall".equals(result.scenario) ? "DIAB_WALL"
              : "diab-prison".equals(result.scenario) ? "DIAB_PRISON"
              : "diab-run".equals(result.scenario) ? "DIAB_RUN" : "NONE"));
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
        + "每场重新创建 World/角色，主体独立归因；警告、fallback、未触发、未运行保留。玩家 HP=1000000；骨牢/地火场角色可普通攻击破牢后步行。</p><pre>");
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
