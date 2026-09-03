package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.combat.StatusEffectApplier;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.serializer.StateSerializer;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import org.junit.jupiter.api.Test;

/** Real ECS status lifecycle and authoritative multiplayer snapshot coverage. */
class StatusEffectEcsScenarioTest extends RiiabloTest {
  @Test
  void purePoisonMissileAppliesDotRespectsResistanceAndExpires() {
    MathUtils.random.setSeed(0x5707EFL);
    Probe probe = new Probe();
    NoopFactory factory = new NoopFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), probe, new StateUpdater(), new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
    try {
      int owner = createPlayer(world, 0, 0);
      int target = createMonster(world, 10, 10, 20, 50);
      int missile = createPurePoisonMissile(world, owner, 10, 10, 2, 3);

      world.setDelta(1f / 25f);
      world.process();
      UnitState poison = world.getMapper(UnitStates.class).get(target)
          .stateList.getState(StateId.POISON);
      assertTrue(poison != null, "a pure-poison hit must apply a state even with zero direct damage");
      assertEquals(1, poison.damagePerFrame, "50% poison resistance must halve DOT damage");
      assertEquals(3, poison.duration);
      assertFalse(world.getMapper(Missile.class).has(missile));
      // The generic combat pipeline still applies its minimum one-point
      // physical packet; poison itself is deferred to StateUpdater ticks.
      assertEquals(19f, hitpoints(world, target), 0.001f,
          "poison payload must be deferred while the base packet is applied");

      world.process();
      world.process();
      world.process();
      assertEquals(16f, hitpoints(world, target), 0.001f);
      assertEquals(4, probe.damageEvents, "one base packet plus three DOT ticks");
      assertEquals(0, probe.deathEvents);
      assertFalse(world.getMapper(UnitStates.class).get(target)
          .stateList.hasState(StateId.POISON));
      world.process();
      assertEquals(16f, hitpoints(world, target), 0.001f,
          "expired poison must not deal an extra tick");
      System.out.println("[STATUS_ECS_CHAIN] state=POISON resist=50 damagePerFrame=1"
          + " duration=3 hp=20->17 expired=true status=PASS");
    } finally {
      world.dispose();
      StatusEffectApplier.INSTANCE.setStateSink(null);
    }
  }

  @Test
  void poisonDeathFiresOnceAndColdMovementRecoversOnExpiry() {
    Probe probe = new Probe();
    NoopFactory factory = new NoopFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), probe, new StateUpdater(), factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
    try {
      int source = createPlayer(world, 0, 0);
      int target = createMonster(world, 10, 10, 5, 0);
      Velocity velocity = world.getMapper(Velocity.class).create(target).set(1f, 2f);

      StatusEffectApplier.INSTANCE.applyPoison(target, 2, 3, source);
      world.process();
      world.process();
      world.process();
      assertEquals(0f, hitpoints(world, target), 0.001f);
      assertEquals(3, probe.damageEvents);
      assertEquals(1, probe.deathEvents);
      world.process();
      assertEquals(1, probe.deathEvents, "a lethal DOT must not repeat DeathEvent");

      setHitpoints(world, target, 20);
      StatusEffectApplier.INSTANCE.applyCold(target, 2, source);
      world.process();
      assertTrue(velocity.stateSpeedMultiplier < 1f);
      assertFalse(velocity.stateMovementLocked);
      world.process();
      assertEquals(1f, velocity.stateSpeedMultiplier, 0.001f);
      assertFalse(world.getMapper(UnitStates.class).get(target)
          .stateList.hasState(StateId.COLD));
      System.out.println("[STATUS_ECS_CHAIN] state=POISON lethalDeathEvents=1 state=COLD"
          + " slowed=true recovered=true status=PASS");
    } finally {
      world.dispose();
      StatusEffectApplier.INSTANCE.setStateSink(null);
    }
  }

  @Test
  void strongerDotReplacesWeakerButWeakerDotCannotReplaceStronger() {
    NoopFactory factory = new NoopFactory();
    StateUpdater updater = new StateUpdater();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), updater, factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
    try {
      int source = createPlayer(world, 0, 0);
      int target = createMonster(world, 10, 10, 100, 0);

      updater.applyState(target, StateId.POISON, 100, 1, source,
          4, CombatSystem.DAMAGE_POISON);
      updater.applyState(target, StateId.POISON, 10, 2, source,
          12, CombatSystem.DAMAGE_POISON);
      UnitState poison = world.getMapper(UnitStates.class).get(target)
          .stateList.getState(StateId.POISON);
      assertEquals(12, poison.damagePerFrame,
          "a stronger poison must replace the old per-frame rate");
      assertEquals(10, poison.duration,
          "native replacement assigns the new expiry even when it is shorter");
      assertEquals(10, poison.initialDuration);
      assertEquals(2, poison.level);

      updater.applyState(target, StateId.POISON, 200, 3, source,
          6, CombatSystem.DAMAGE_POISON);
      assertEquals(12, poison.damagePerFrame,
          "a weaker poison must not replace the stronger active poison");
      assertEquals(10, poison.duration,
          "a rejected weaker poison must not extend the active duration");
      assertEquals(2, poison.level);

      updater.applyState(target, StateId.BURNING, 80, 1, source,
          3, CombatSystem.DAMAGE_FIRE);
      updater.applyState(target, StateId.BURNING, 20, 2, source,
          3, CombatSystem.DAMAGE_FIRE);
      UnitState burning = world.getMapper(UnitStates.class).get(target)
          .stateList.getState(StateId.BURNING);
      assertEquals(3, burning.damagePerFrame);
      assertEquals(20, burning.duration,
          "an equal-rate burn follows the same native replacement rule");
      assertEquals(2, burning.level);
    } finally {
      world.dispose();
      StatusEffectApplier.INSTANCE.setStateSink(null);
    }
  }

  @Test
  void stateSerializerRoundTripsAuthoritativeSnapshot() {
    UnitStates source = new UnitStates().init(42);
    source.stateList.addState(StateId.POISON, 90, 2, 7);
    UnitState coldSource = source.stateList.addState(StateId.COLD, 20, 3, 8);
    coldSource.velocityModifier = -35;
    UnitState chargeSource = source.stateList.addState(
        StateId.PROGRESSIVE_FIRE, 80, 4, 42);
    chargeSource.velocityModifier = 3;
    StateSerializer serializer = new StateSerializer();
    FlatBufferBuilder builder = new FlatBufferBuilder(256);
    int stateOffset = serializer.putData(builder, source);
    int typeOffset = EntitySync.createComponentTypeVector(
        builder, new byte[] {ComponentP.StateP});
    int componentOffset = EntitySync.createComponentVector(builder, new int[] {stateOffset});
    int root = EntitySync.createEntitySync(builder, 42, 0, 0, typeOffset, componentOffset);
    builder.finish(root);

    EntitySync packet = EntitySync.getRootAsEntitySync(builder.dataBuffer());
    UnitStates client = new UnitStates().init(42);
    client.stateList.addState(StateId.FREEZE, 999, 1, -1);
    serializer.getData(packet, 0, client);

    assertEquals(ComponentP.StateP, serializer.getDataType());
    assertEquals(3, client.stateList.size());
    assertFalse(client.stateList.hasState(StateId.FREEZE));
    assertEquals(90, client.stateList.getStateDuration(StateId.POISON));
    assertEquals(2, client.stateList.getStateLevel(StateId.POISON));
    assertEquals(20, client.stateList.getStateDuration(StateId.COLD));
    assertEquals(3, client.stateList.getStateLevel(StateId.COLD));
    assertEquals(-35, client.stateList.getState(StateId.COLD).velocityModifier);
    assertEquals(3, client.stateList.getState(StateId.PROGRESSIVE_FIRE).velocityModifier);
    assertEquals(-35, client.stateList.getTotalVelocityModifier(),
        "progressive charges must not be interpreted as movement speed");
    System.out.println("[STATE_SYNC_CHAIN] entity=42 states=3 poison=90/2 cold=20/3"
        + " progressiveFireCharges=3 staleRemoved=true status=PASS");
  }

  private static int createPlayer(World world, float x, float y) {
    int id = world.create();
    world.getMapper(Player.class).create(id);
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(100, 0);
    return id;
  }

  private static int createMonster(World world, float x, float y, float hp, int poisonResist) {
    int id = world.create();
    world.getMapper(Monster.class).create(id);
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(hp, poisonResist);
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static int createPurePoisonMissile(World world, int owner,
      float x, float y, int damagePerFrame, int duration) {
    Missiles.Entry row = Riiablo.files.Missiles.get("shafire3");
    int id = world.create();
    Missile missile = world.getMapper(Missile.class).create(id)
        .set(row, new Vector2(x, y), 100).setOwner(owner);
    missile.damage.base().put(Stat.poisonmindam, damagePerFrame);
    missile.damage.base().put(Stat.poisonmaxdam, damagePerFrame);
    missile.damage.base().put(Stat.poisonlength, duration);
    missile.damage.base().put(Stat.level, 1);
    missile.damage.reset();
    missile.damageSnapshot = true;
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(Velocity.class).create(id).velocity.setZero();
    return id;
  }

  private static Attributes attributes(float hp, int poisonResist) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.base().put(Stat.level, 1);
    attrs.base().put(Stat.armorclass, 0);
    attrs.base().put(Stat.mindamage, 0);
    attrs.base().put(Stat.maxdamage, 0);
    attrs.base().put(Stat.tohit, 10_000);
    attrs.base().put(Stat.poisonresist, poisonResist);
    attrs.reset();
    return attrs;
  }

  private static float hitpoints(World world, int entityId) {
    Attributes attrs = world.getMapper(AttributesWrapper.class).get(entityId).attrs;
    StatRef hp = attrs.get(Stat.hitpoints, StatRef.obtain());
    return hp != null ? hp.asFixed() : 0f;
  }

  private static void setHitpoints(World world, int entityId, float hp) {
    world.getMapper(AttributesWrapper.class).get(entityId).attrs.get(Stat.hitpoints).set(hp);
  }

  private static final class Probe extends BaseSystem {
    int damageEvents;
    int deathEvents;
    @Subscribe public void onDamage(DamageEvent event) { damageEvents++; }
    @Subscribe public void onDeath(DeathEvent event) { deathEvents++; }
    @Override protected void processSystem() {}
  }

  private static final class NoopFactory extends EntityFactory {
    @Override public int createPlayer(CharData data, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMonster(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(Item item, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMissile(int id, Vector2 angle, Vector2 position) { return Engine.INVALID_ENTITY; }
  }
}
