package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.*;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.event.*;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.map.MapGraph;
import com.riiablo.map.pfa.GraphPath;
import com.riiablo.map.pfa.Point2;
import com.riiablo.save.CharData;
import java.util.ArrayList;
import java.util.List;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/** AI -> production action/movement/damage systems. Only terrain and allocation are fixtures.
 * Action markers are injected deliberately; animation-marker production has a separate runtime gate.
 * Melee probabilities remain native; special branch probabilities/timers are controlled explicitly.
 * Bounded fixed-seed trials admit misses and idle decisions.
 */
class MonsterBehaviorScenarioIntegrationTest extends RiiabloTest {
  @ParameterizedTest(name = "resistance={0}, flat={1}, strength={2}, ED={3}: actual HP loss={4}")
  @CsvSource({"50,10,0,0,45", "75,0,0,0,50", "100,0,0,0,50",
      "-50,10,0,0,135", "0,100,0,0,0", "0,0,50,50,200",
      "50,10,50,50,95", "0,0,0,-100,10", "0,0,50,-100,50"})
  void monsterMeleeNativeMitigationReachesActualPlayerLife(int resistance, int flat,
      int strength, int enhanced, int expected) {
    try (Scene s = new Scene("Zombie", 1)) {
      Attributes source = s.world.getMapper(AttributesWrapper.class).get(s.actor).attrs;
      source.base().put(Stat.mindamage, 100);
      source.base().put(Stat.maxdamage, 100);
      source.base().put(Stat.strength, strength);
      source.base().put(Stat.damagepercent, enhanced);
      source.reset();
      Attributes target = s.world.getMapper(AttributesWrapper.class).get(s.player).attrs;
      target.base().put(Stat.damageresist, resistance);
      target.base().put(Stat.normal_damage_reduction, flat);
      target.reset();
      // Stop at the first successful, unblocked melee attempt. A zero-damage
      // result must prove it landed, not accidentally pass because AI missed.
      for (int i = 0; i < 500 && s.probe.successfulMelee == 0; i++) s.tick(true);
      assertEquals(1, s.probe.successfulMelee, s.diagnostic());
      assertEquals(Scene.LIFE - expected, s.life(s.player), s.diagnostic());
      assertEquals(expected == 0 ? 0 : 1, s.probe.hits);
    }
  }

  @ParameterizedTest(name = "{0}: native animation marker delivers damage")
  @ValueSource(strings = {"Zombie", "Skeleton", "Fallen", "CorruptRogue", "Goatman", "Brute"})
  void nativeAnimationMustDeliverTheAiAttack(String name) {
    try (Scene s = new Scene(name, 1, true)) {
      for (int i = 0; i < 500 && s.life(s.player) == Scene.LIFE; i++) s.tick(false);
      assertTrue(s.probe.markers > 0, "no runtime marker " + s.diagnostic());
      assertTrue(s.probe.hits > 0, "no runtime damage " + s.diagnostic());
      assertTrue(s.life(s.player) < Scene.LIFE, s.diagnostic());
    }
  }

  @ParameterizedTest(name = "{0}: adjacent attack causes real damage")
  @ValueSource(strings = {"Zombie", "Skeleton", "Fallen", "CorruptRogue", "Goatman", "Brute"})
  void meleeAiMustActuallyHurtPlayer(String name) {
    try (Scene s = new Scene(name, 1)) {
      for (int i = 0; i < 500 && s.life(s.player) == Scene.LIFE; i++) s.tick(true);
      assertTrue(s.probe.hits > 0, s.diagnostic());
      assertTrue(s.life(s.player) < Scene.LIFE, s.diagnostic());
      assertEquals(s.actor, s.probe.lastAttacker);
      assertEquals(s.player, s.probe.lastVictim);
      assertTrue(s.life(s.actor) > 0);
    }
  }

  @ParameterizedTest(name = "{0}: stationary and continuously moving player pursuit")
  @ValueSource(strings = {"Zombie", "Skeleton", "Fallen", "CorruptRogue", "Goatman", "Brute"})
  void pursuingAiMustMakeProgressAndFollowMovingTarget(String name) {
    for (boolean moving : new boolean[] {false, true}) {
      try (Scene s = new Scene(name, 16)) {
        Vector2 start = s.position(s.actor).cpy();
        int movingTicks = 0;
        for (int tick = 0; tick < 180; tick++) {
          if (moving) s.position(s.player).y = 10 + (tick % 20 < 10 ? 1 : -1);
          s.tick(false);
          if (!s.world.getMapper(Velocity.class).get(s.actor).velocity.isZero()) movingTicks++;
        }
        assertTrue(s.position(s.actor).dst(start) > 1, "no displacement: " + s.diagnostic());
        assertTrue(s.position(s.actor).dst(s.position(s.player)) < 15,
            "failed to close distance moving=" + moving + " " + s.diagnostic());
        assertTrue(movingTicks > 5, "no sustained pursuit: " + s.diagnostic());
        assertEquals(Scene.LIFE, s.life(s.player), "no injected hit marker, no damage");
      }
    }
  }

  @ParameterizedTest(name = "{0}: stale melee target cannot be hit after fleeing")
  @ValueSource(strings = {"Zombie", "Skeleton", "Fallen", "CorruptRogue", "Goatman", "Brute"})
  void targetEscapingBeforeAttackMarkerTakesNoMeleeDamage(String name) {
    try (Scene s = new Scene(name, 1)) {
      for (int i = 0; i < 500 && !s.world.getMapper(Casting.class).has(s.actor); i++) s.tick(false);
      assertTrue(s.world.getMapper(Casting.class).has(s.actor), s.diagnostic());
      s.position(s.player).set(1000, 1000);
      s.marker();
      assertEquals(Scene.LIFE, s.life(s.player));
      assertEquals(0, s.probe.hits);
    }
  }

  @ParameterizedTest(name = "{0}: AI cast creates live child entity")
  @ValueSource(strings = {"FoulCrowNest", "Sarcophagus", "MaggotEgg", "SandMaggotQueen", "VileMother"})
  void spawningAiCreatesConfiguredLivingChildren(String name) {
    try (Scene s = new Scene(name, 8)) {
      if (name.equals("MaggotEgg")) s.ai.params[1] = 100; // isolate hatch branch
      if (name.equals("VileMother")) {
        s.installZone();
        s.ai.params[0] = 2;
        s.ai.params[1] = 3;
        s.ai.params[2] = 100;
      }
      for (int tick = 0; tick < 1500 && s.factory.children.isEmpty(); tick++) s.tick(true);
      assertFalse(s.factory.children.isEmpty(), s.diagnostic());
      int child = s.factory.children.get(0);
      Monster monster = s.world.getMapper(Monster.class).get(child);
      assertNotNull(monster);
      assertNotNull(monster.monstats);
      assertTrue(s.life(child) > 0);
      assertNotEquals(s.actor, child);
      assertTrue(Float.isFinite(s.position(child).x));
      assertTrue(s.position(child).dst(s.position(s.actor)) < 20);
      assertEquals(Scene.LIFE, s.life(s.player), "birth must not deal incidental melee damage");
      if (name.equals("FoulCrowNest") || name.equals("Sarcophagus")) {
        assertEquals(s.actor, monster.minionOwnerId, "Nest must retain its spawned child's owner");
        NativeUnitFlags flags = s.world.getMapper(NativeUnitFlags.class).get(child);
        assertNotNull(flags);
        assertTrue(flags.has(NativeUnitFlags.NEST_SUMMON));
      }
      if (name.equals("MaggotEgg")) {
        assertEquals(s.row.spawn, monster.monstats.Id);
        assertEquals(0, s.life(s.actor), "hatching consumes the egg");
      }
      if (name.equals("SandMaggotQueen")) assertTrue(monster.monstats.Id.startsWith("sandmaggot"));
      if (name.equals("VileMother")) assertTrue(monster.monstats.Id.startsWith("vilechild"));
    }
  }

  @Test void exhaustedNestCannotProduceUnlimitedChildren() {
    try (Scene s = new Scene("FoulCrowNest", 8)) {
      s.ai.params[0] = 1;
      s.ai.params[2] = 2;
      for (int i = 0; i < 300; i++) s.tick(true);
      assertEquals(2, s.factory.children.size(), s.diagnostic());
      for (int i = 0; i < 200; i++) s.tick(true);
      assertEquals(2, s.factory.children.size());
    }
  }

  @Test void blockedNestPlacementCannotCreateAChild() {
    try (Scene s = new Scene("FoulCrowNest", 8)) {
      when(s.map.flags(anyInt(), anyInt())).thenReturn(com.riiablo.map.DT1.Tile.FLAG_BLOCK_WALK);
      s.ai.params[0] = 1;
      for (int i = 0; i < 100; i++) s.tick(true);
      assertTrue(s.factory.children.isEmpty(), s.diagnostic());
      assertEquals(Scene.LIFE, s.life(s.player));
    }
  }

  @ParameterizedTest(name = "suicide element={0}: delayed damage, radius and exactly-once")
  @ValueSource(ints = {0, 1, 25})
  void suicideAiDamagesNearbyPlayerOnlyAfterNativeFourFrameDelay(int element) {
    try (Scene s = new Scene("SuicideMinion", 1)) {
      s.ai.params[0] = element;
      s.ai.params[1] = 1;
      s.ai.params[3] = 5;
      s.ai.params[4] = 2;
      s.ai.SLEEP = Animation.FRAME_DURATION;
      int far = s.playerAt(30, 10);
      int ally = s.factory.createMonster(s.row.hcIdx, 11, 11);
      s.factory.children.clear();
      for (int i = 0; i < 20 && !"DEAD".equals(s.ai.getState()); i++) s.tick(false);
      assertEquals("DEAD", s.ai.getState(), s.diagnostic());
      assertEquals(0, s.life(s.actor));
      assertEquals(Scene.LIFE, s.life(s.player));
      for (int i = 0; i < 3; i++) s.tick(false);
      assertEquals(Scene.LIFE, s.life(s.player), "native explosion callback is death+4");
      s.tick(false);
      assertTrue(s.life(s.player) < Scene.LIFE, s.diagnostic());
      assertEquals(Scene.LIFE, s.life(far), "outside radius");
      assertEquals(Scene.LIFE, s.life(ally), "friendly monster");
      assertEquals(1, s.probe.hits);
      assertEquals(1, s.factory.missiles.size());
      Missile visual = s.world.getMapper(Missile.class).get(s.factory.missiles.get(0));
      assertTrue(visual.presentationOnly, "visual must not hit twice");
      float after = s.life(s.player);
      for (int i = 0; i < 30; i++) s.tick(false);
      assertEquals(after, s.life(s.player));
      assertEquals(1, s.probe.hits);
    }
  }

  @Test void shamanRangedAiMissileMustReachAndDamagePlayer() {
    try (Scene s = new Scene("FallenShaman", 8)) {
      s.ai.params[0] = 0; // no corpse/command branch
      for (int i = 0; i < 1000 && s.life(s.player) == Scene.LIFE; i++) s.tick(true);
      assertFalse(s.factory.missiles.isEmpty(), s.diagnostic());
      assertTrue(s.life(s.player) < Scene.LIFE, s.diagnostic());
      assertEquals(s.actor, s.probe.lastAttacker);
    }
  }

  @Test void succubusCurseMustInstallStateOnPlayer() {
    try (Scene s = new Scene("Succubus", 8)) {
      s.ai.params[2] = 100;
      s.ai.params[3] = 30;
      s.ai.params[6] = 0;
      s.tick(true);
      UnitStates states = s.world.getMapper(UnitStates.class).get(s.player);
      assertNotNull(states, s.diagnostic());
      assertTrue(states.stateList.hasCurse(), s.diagnostic());
      assertEquals(Scene.LIFE, s.life(s.player), "a curse is not direct damage");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"FoulCrowNest", "Sarcophagus", "MaggotEgg"})
  void generatorsWithoutAnEnemyDoNotSpawn(String name) {
    try (Scene s = new Scene(name, 1000)) {
      for (int i = 0; i < 100; i++) s.tick(true);
      assertTrue(s.factory.children.isEmpty(), s.diagnostic());
      assertTrue(s.factory.missiles.isEmpty());
      assertEquals(Scene.LIFE, s.life(s.player));
    }
  }

  static final class Probe extends BaseSystem {
    int hits, markers, lastAttacker = -1, lastVictim = -1;
    int successfulMelee;
    @Subscribe public void melee(MeleeAttackEvent e) { if (e.hit && !e.blocked) successfulMelee++; }
    @Subscribe public void marker(AnimDataKeyframeEvent e) {
      if (e.keyframe == Engine.KEYFRAME_ATK || e.keyframe == Engine.KEYFRAME_MIS) markers++;
    }
    @Subscribe public void hit(DamageEvent e) {
      if (e.damage > 0) { hits++; lastAttacker = e.attacker; lastVictim = e.victim; }
    }
    @Override protected void processSystem() {}
  }

  static final class Scene implements AutoCloseable {
    static final float LIFE = 10000;
    final World previous = Riiablo.engine;
    final com.riiablo.codec.D2 previousAnimation = Riiablo.anim;
    final int previousSeed = Riiablo.gameSeed;
    final java.util.Random previousRandom = MathUtils.random;
    final Probe probe = new Probe();
    final Allocator factory = new Allocator();
    final World world;
    final MonStats.Entry row;
    final AI ai;
    final int actor, player;
    final Map map = mock(Map.class);

    Scene(String name, float distance) { this(name, distance, false); }
    Scene(String name, float distance, boolean nativeAnimation) {
      MonStats.Entry found = null;
      for (MonStats.Entry candidate : Riiablo.files.monstats)
        if (name.equals(candidate.AI) && candidate.killable && !candidate.npc) { found = candidate; break; }
      assertNotNull(found, "native MonStats AI=" + name);
      row = found;
      // Search shape is controlled; real Pathfinder and VelocityAdder consume it.
      when(map.findPath(nullable(Map.Zone.class), any(Vector2.class), any(Vector2.class),
          anyInt(), anyInt(), any(GraphPath.class), nullable(MapGraph.Obstacle.class),
          anyInt(), anyInt(), anyBoolean())).thenAnswer(call -> {
        Vector2 from = call.getArgument(1), to = call.getArgument(2);
        GraphPath out = call.getArgument(5);
        out.clear();
        out.add(new Point2(Map.round(from.x), Map.round(from.y)));
        out.add(new Point2(Map.round(to.x), Map.round(to.y)));
        return true;
      });
      WorldConfigurationBuilder builder = new WorldConfigurationBuilder().with(new EventSystem(), probe,
          new Actioneer(), new DynamicUnitCollisionSystem(false), new Pathfinder(), new VelocityAdder(), new CofManager(),
          new ServerSkillSystem(true), new MissileCollisionSystem(), new NativePlayerTargetSlots(), factory);
      if (nativeAnimation) {
        Riiablo.anim = com.riiablo.codec.D2.loadFromFile(Riiablo.mpqs.resolve("data\\global\\eanimdata.d2"));
        builder.with(new AnimDataResolver(), new SequenceHandler(), new AnimStepper());
      }
      world = new World(builder.build().register("map", map).register("factory", factory));
      Riiablo.engine = world;
      Riiablo.gameSeed = 0x1977;
      MathUtils.random = new com.badlogic.gdx.math.RandomXS128(0x1977);
      actor = factory.createMonster(row.hcIdx, 10, 10);
      factory.children.clear();
      player = playerAt(10 + distance, 10);
      world.setDelta(Animation.FRAME_DURATION);
      world.process();
      ai = AI.findAI(actor, name);
      assertFalse(ai instanceof GenericMonster, "dedicated AI required");
      world.getInjector().inject(ai);
      ai.initialize();
    }

    int playerAt(float x, float y) {
      int id = world.create();
      world.getMapper(Player.class).create(id);
      world.getMapper(Class.class).create(id).type = Class.Type.PLR;
      world.getMapper(Position.class).create(id).position.set(x, y);
      world.getMapper(Size.class).create(id).size = 1;
      world.getMapper(Velocity.class).create(id).setPlayer(6, 9);
      world.getMapper(AttributesWrapper.class).create(id).attrs = attrs();
      return id;
    }

    void tick(boolean resolveMarker) {
      ai.update(Animation.FRAME_DURATION);
      if (resolveMarker && (world.getMapper(Casting.class).has(actor)
          || world.getMapper(Sequence.class).has(actor))) {
        if (world.getMapper(Casting.class).has(actor)) marker();
        world.getMapper(Casting.class).remove(actor);
        world.getMapper(Sequence.class).remove(actor);
      }
      world.process();
    }
    void installZone() {
      Map.Zone zone = mock(Map.Zone.class);
      when(zone.areRoomsAdjacent(anyFloat(), anyFloat(), anyFloat(), anyFloat())).thenReturn(true);
      when(map.getZone(any(Vector2.class))).thenReturn(zone);
      world.getMapper(MapWrapper.class).create(actor).set(map, zone);
      world.getMapper(MapWrapper.class).create(player).set(map, zone);
    }
    void marker() { world.getSystem(EventSystem.class).dispatch(AnimDataKeyframeEvent.obtain(actor, Engine.KEYFRAME_ATK)); }
    Vector2 position(int id) { return world.getMapper(Position.class).get(id).position; }
    float life(int id) { return world.getMapper(AttributesWrapper.class).get(id).attrs.get(Stat.hitpoints).asFixed(); }
    String diagnostic() { return row.Id + " state=" + ai.getState() + " " + ai.getDecisionDiagnostics()
        + " pos=" + position(actor) + " target=" + position(player) + " hp=" + life(player); }
    @Override public void close() {
      world.dispose(); Riiablo.engine = previous; Riiablo.gameSeed = previousSeed;
      Riiablo.anim = previousAnimation; MathUtils.random = previousRandom;
    }
  }

  static Attributes attrs() {
    Attributes a = Attributes.obtainStandard();
    a.base().put(Stat.hitpoints, Scene.LIFE);
    a.base().put(Stat.maxhp, Scene.LIFE);
    a.base().put(Stat.level, 20);
    a.base().put(Stat.mindamage, 20);
    a.base().put(Stat.maxdamage, 20);
    a.base().put(Stat.tohit, 10000);
    a.base().put(Stat.armorclass, 0);
    a.reset();
    return a;
  }

  /** Allocates observable ECS units; production ServerEntityFactory collision acceptance is separate. */
  static final class Allocator extends EntityFactory {
    final List<Integer> children = new ArrayList<>(), missiles = new ArrayList<>();
    @Override public int createMonster(int type, float x, float y) {
      int id = world.create();
      MonStats.Entry row = Riiablo.files.monstats.get(type);
      assertNotNull(row);
      world.getMapper(Monster.class).create(id).set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
      world.getMapper(Class.class).create(id).type = Class.Type.MON;
      world.getMapper(Position.class).create(id).position.set(x, y);
      world.getMapper(Angle.class).create(id);
      world.getMapper(Size.class).create(id).size = 1;
      world.getMapper(Velocity.class).create(id).setMonsterFromNativeTable(row.Velocity);
      world.getMapper(AttributesWrapper.class).create(id).attrs = attrs();
      CofReference reference = world.getMapper(CofReference.class).create(id)
          .set(row.Code, Engine.Monster.MODE_NU);
      int weaponClass = Riiablo.files.WeaponClass.index(Riiablo.files.monstats2.get(row.MonStatsEx).BaseW);
      reference.wclass = weaponClass >= Engine.WEAPON_NIL && weaponClass <= Engine.WEAPON_HT2
          ? (byte) weaponClass : Engine.WEAPON_HTH;
      world.getMapper(AnimData.class).create(id);
      world.getMapper(MovementModes.class).create(id).set(Engine.Monster.MODE_NU, Engine.Monster.MODE_WL, Engine.Monster.MODE_RN);
      world.getMapper(UnitStates.class).create(id).init(id);
      children.add(id);
      return id;
    }
    @Override public void applyNativeUnitFlags(int id, int flags) {
      world.getMapper(NativeUnitFlags.class).create(id).set(flags);
    }
    @Override public int createMonsterChild(int source, int type, int mode, float x, float y,
        int radius, boolean replace, int flags) {
      int id = createMonster(type, x, y);
      world.getMapper(Monster.class).get(id).minionOwnerId = source;
      world.getMapper(CofReference.class).get(id).mode = (byte) mode;
      applyNativeUnitFlags(id, flags);
      return id;
    }
    @Override public int createMissile(int type, Vector2 direction, Vector2 position) {
      return createMissile(type, direction, position, Engine.INVALID_ENTITY);
    }
    @Override public int createMissile(int type, Vector2 direction, Vector2 position, int owner) {
      int id = world.create();
      com.riiablo.codec.excel.Missiles.Entry row = Riiablo.files.Missiles.get(type);
      assertNotNull(row);
      world.getMapper(Missile.class).create(id).set(row, position, row.Range).setOwner(owner);
      world.getMapper(Position.class).create(id).position.set(position);
      world.getMapper(Velocity.class).create(id).velocity.set(direction).setLength(Math.max(1, row.Vel));
      missiles.add(id);
      return id;
    }
    @Override public int createPlayer(CharData data, Vector2 point) { return -1; }
    @Override public int createDynamicObject(int a, int b, float x, float y) { return -1; }
    @Override public int createStaticObject(int a, int b, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int a, float x, float y) { return -1; }
    @Override public int createWarp(int a, float x, float y) { return -1; }
    @Override public int createItem(Item item, float x, float y) { return -1; }
  }
}
