package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.Pathfinder;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class CycleOfLifeAiIntegrationTest extends RiiabloTest {
  @Test void bothCyclersCastS1OnAUsableCorpseRegardlessOfItsPoisonResistance() {
    for (int summon : new int[] {SkillId.CARRION_VINE, SkillId.SOLAR_CREEPER}) {
      try (Fixture f = new Fixture(summon)) {
        int corpse = f.corpse(11, 10);
        f.world.getMapper(AttributesWrapper.class).get(corpse).attrs.base().put(Stat.poisonresist, 100);
        f.world.getMapper(AttributesWrapper.class).get(corpse).attrs.reset();
        f.world.getMapper(UnitStates.class).get(corpse).stateList
            .addState(StateId.POISON, 100, 1, corpse);
        f.flush();
        f.ai.update(Animation.FRAME_DURATION);
        assertNotNull(f.cast());
        assertEquals(f.skill.Id, f.cast().skillId);
        assertEquals(corpse, f.cast().targetId);
        assertEquals(Engine.Monster.MODE_S1, f.world.getMapper(Sequence.class).get(f.vine).mode1);
        assertEquals("CAST", f.ai.getState());
      }
    }
  }

  @Test void fullLifeOrManaBlocksSkillButDoesNotSkipApproachingACorpse() {
    for (int summon : new int[] {SkillId.CARRION_VINE, SkillId.SOLAR_CREEPER}) {
      try (Fixture f = new Fixture(summon)) {
        f.ownerAttributes().get(Stat.hitpoints).set(200f);
        f.ownerAttributes().get(Stat.mana).set(100f);
        int corpse = f.corpse(11, 10);
        f.flush();
        f.ai.update(Animation.FRAME_DURATION);
        assertNull(f.cast());
        assertEquals("APPROACH", f.ai.getState());
        assertEquals(corpse, f.paths.target);
      }
    }
  }

  @Test void ownerLeashWinsOverAnAdjacentCorpse() {
    try (Fixture f = new Fixture(SkillId.CARRION_VINE)) {
      f.corpse(11, 10);
      f.ai.params[4] = 10;
      f.world.getMapper(Position.class).get(f.owner).position.set(20, 10);
      f.flush();
      f.ai.update(Animation.FRAME_DURATION);
      assertNull(f.cast());
      assertEquals("REGROUP", f.ai.getState());
      assertEquals(f.owner, f.paths.target);
    }
  }

  @Test void skillSearchRadiusIsClampedToFiveThroughFifty() {
    try (Fixture f = new Fixture(SkillId.CARRION_VINE)) {
      String saved = f.skill.aurarangecalc;
      try {
        f.skill.aurarangecalc = "1";
        int corpse = f.corpse(14, 10);
        f.flush();
        f.ai.update(Animation.FRAME_DURATION);
        assertEquals(corpse, f.paths.target, "range 1 is raised to 5");
        f.world.getMapper(Position.class).get(corpse).position.set(16, 10);
        f.ai.update(2f);
        assertEquals("IDLE", f.ai.getState(), "range 5 rejects a corpse 6 cells away");
        f.skill.aurarangecalc = "1000";
        f.ai.params[1] = 60;
        f.world.getMapper(Position.class).get(corpse).position.set(61, 10);
        f.ai.update(2f);
        assertEquals("IDLE", f.ai.getState(), "range 1000 is capped at 50");
        f.world.getMapper(Position.class).get(corpse).position.set(59, 10);
        f.ai.update(2f);
        assertEquals(corpse, f.paths.target);
        assertEquals("APPROACH", f.ai.getState());
      } finally { f.skill.aurarangecalc = saved; }
    }
  }

  @Test void reservedConvertedAndOtherMapCorpsesAreNotConsumed() {
    try (Fixture f = new Fixture(SkillId.CARRION_VINE)) {
      int reserved = f.corpse(11, 10);
      f.world.getMapper(UnitStates.class).get(reserved).stateList
          .addState(StateId.CORPSE_NOSELECT, 100, 1, f.vine);
      int converted = f.corpse(11, 11);
      f.world.getMapper(Monster.class).get(converted).converted = true;
      int elsewhere = f.corpse(10, 11);
      f.world.getMapper(MapWrapper.class).create(f.vine).set(new Map(0, 0), null);
      f.world.getMapper(MapWrapper.class).create(elsewhere).set(new Map(0, 0), null);
      f.flush();
      f.ai.update(Animation.FRAME_DURATION);
      assertNull(f.cast());
      assertEquals("IDLE", f.ai.getState());
      assertEquals(f.ai.params[2] * Animation.FRAME_DURATION, f.ai.nextThink, .0001f);
    }
  }

  @Test void cooldownEqualityCannotStartAnotherSkillAndCheckpointPreservesTheDeadline() {
    try (Fixture f = new Fixture(SkillId.CARRION_VINE)) {
      f.corpse(11, 10);
      f.flush();
      f.ai.update(Animation.FRAME_DURATION);
      assertNotNull(f.cast());
      f.world.getMapper(Casting.class).remove(f.vine);
      f.world.getMapper(Sequence.class).remove(f.vine);
      f.flush();
      f.ai.skillCooldown = Animation.FRAME_DURATION;
      f.ai.nextThink = 0f;
      AiRuntimeState saved = AiRuntimeState.capture(f.ai, f.vine);
      f.ai.skillCooldown = -100f;
      saved.restore(f.ai, f.vine);
      f.ai.update(Animation.FRAME_DURATION);
      assertNull(f.cast(), "native skill deadline requires strictly later than equality");
      f.ai.nextThink = 0f;
      f.ai.update(Animation.FRAME_DURATION);
      assertNotNull(f.cast());
    }
  }

  private static final class Fixture implements AutoCloseable {
    final World previous = Riiablo.engine;
    final World world;
    final RecordingPaths paths = new RecordingPaths();
    final CycleOfLife ai;
    final Skills.Entry skill;
    final int owner, vine;
    Fixture(int summonId) {
      NoopFactory factory = new NoopFactory();
      world = new World(new WorldConfigurationBuilder()
          .with(new EventSystem(), new Pathfinder(), new CofManager(), factory)
          .build().register("factory", factory).register("map", new Map(0, 0)));
      Riiablo.engine = world;
      owner = world.create();
      world.getMapper(Player.class).create(owner);
      world.getMapper(Position.class).create(owner).position.set(10, 10);
      Attributes attrs = attributes(100f);
      attrs.base().put(Stat.maxhp, 200f);
      attrs.base().put(Stat.mana, 50f);
      attrs.base().put(Stat.maxmana, 100f);
      attrs.reset();
      world.getMapper(AttributesWrapper.class).create(owner).attrs = attrs;
      MonStats.Entry row = Riiablo.files.monstats.get(Riiablo.files.skills.get(summonId).summon);
      vine = world.create();
      world.getMapper(Monster.class).create(vine).set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
      world.getMapper(Position.class).create(vine).position.set(10, 10);
      world.getMapper(Angle.class).create(vine);
      world.getMapper(Size.class).create(vine).size = 1;
      world.getMapper(Velocity.class).create(vine).setMonsterFromNativeTable(row.Velocity);
      world.getMapper(SummonedPet.class).create(vine).set(owner, "vine", summonId, 1, false, 0);
      flush();
      ai = new CycleOfLife(vine);
      world.getInjector().inject(ai);
      ai.pathfinder = paths;
      ai.initialize();
      skill = ai.skillForSlot(0);
      assertNotNull(skill);
    }
    int corpse(float x, float y) {
      int id = world.create();
      MonStats.Entry row = Riiablo.files.monstats.get("fallen1");
      world.getMapper(Monster.class).create(id).set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
      world.getMapper(Position.class).create(id).position.set(x, y);
      world.getMapper(Size.class).create(id).size = 1;
      world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(0f);
      world.getMapper(Corpse.class).create(id).reset(Corpse.DEFAULT_DURATION, true);
      world.getMapper(UnitStates.class).create(id).init(id);
      return id;
    }
    Casting cast() { return world.getMapper(Casting.class).get(vine); }
    Attributes ownerAttributes() { return world.getMapper(AttributesWrapper.class).get(owner).attrs; }
    void flush() { world.process(); }
    @Override public void close() { world.dispose(); Riiablo.engine = previous; }
  }
  private static Attributes attributes(float hp) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, Math.max(1f, hp));
    attrs.reset();
    return attrs;
  }
  @com.artemis.annotations.All({Pathfind.class, Position.class, Velocity.class})
  private static final class RecordingPaths extends Pathfinder {
    int target = Engine.INVALID_ENTITY;
    @Override public boolean findPath(int src, Vector2 destination) { return true; }
    @Override public boolean findPath(int src, Vector2 destination, boolean raycast, int targetId) {
      target = targetId;
      return true;
    }
  }
  private static final class NoopFactory extends EntityFactory {
    @Override public int createPlayer(CharData data, Vector2 position) { return -1; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return -1; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return -1; }
    @Override public int createMonster(int id, float x, float y) { return -1; }
    @Override public int createWarp(int index, float x, float y) { return -1; }
    @Override public int createItem(Item item, float x, float y) { return -1; }
    @Override public int createMissile(int id, Vector2 angle, Vector2 position) { return -1; }
  }
}
