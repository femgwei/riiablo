package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.annotations.All;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.NativeRng;
import com.riiablo.engine.server.Pathfinder;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.NativeAiTargetOverride;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.Size;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;

/** Authoritative AI target behavior for D2Game's Dim Vision/Attract/Confuse modes. */
class NecromancerCurseAiIntegrationTest extends RiiabloTest {
  @Test
  void playerSummonTargetsHostileMonsterAndKeepsValidTarget() {
    Fixture fixture = new Fixture();
    try {
      int owner = fixture.player(1, 0);
      int summon = fixture.monster(0, 0);
      fixture.world.getMapper(SummonedPet.class).create(summon)
          .set(owner, "skeleton", 70, 1, false, 0);

      int first = fixture.monster(6, 0);
      int friendlyPet = fixture.monster(2, 0);
      fixture.world.getMapper(SummonedPet.class).create(friendlyPet)
          .set(owner, "skeleton", 70, 1, false, 0);
      int corpse = fixture.monster(0.5f, 0);
      fixture.world.getMapper(Corpse.class).create(corpse);
      fixture.npc(0.25f, 0);

      ProbeAI ai = fixture.ai(summon);
      assertEquals(first, ai.nearest(),
          "a player summon must ignore its owner, friendly pets, corpses and NPCs");

      int closer = fixture.monster(3, 0);
      fixture.world.process();
      assertEquals(first, ai.continuing(first),
          "D2MOO target-node selection must keep a valid current target");

      fixture.world.getMapper(AttributesWrapper.class).get(first).attrs
          .base().put(Stat.hitpoints, 0);
      fixture.world.getMapper(AttributesWrapper.class).get(first).attrs.reset();
      assertEquals(closer, ai.continuing(first),
          "a dead current target must trigger a deterministic replacement scan");
    } finally {
      fixture.close();
    }
  }

  @Test
  void attractForcesMonsterTargetAndResetsWhenItDies() {
    Fixture fixture = new Fixture();
    try {
      int actor = fixture.monster(0, 0);
      int attracted = fixture.monster(8, 0);
      int player = fixture.player(1, 0);
      NativeAiTargetOverride override = fixture.world.getMapper(NativeAiTargetOverride.class)
          .create(actor).setAttract(attracted, player, 123, 10);
      ProbeAI ai = fixture.ai(actor);

      assertFalse(ai.tickSpecial());
      assertEquals(attracted, ai.nearest(), "fixed target must override the nearer player");
      fixture.world.getMapper(AttributesWrapper.class).get(attracted).attrs
          .base().put(Stat.hitpoints, 0);
      fixture.world.getMapper(AttributesWrapper.class).get(attracted).attrs.reset();

      assertFalse(ai.tickSpecial());
      assertFalse(fixture.world.getMapper(NativeAiTargetOverride.class).has(actor));
      assertEquals(player, ai.nearest(), "normal allegiance search must resume after AI reset");
      assertTrue(override.targetId == attracted || override.targetId == Engine.INVALID_ENTITY);
      fixture.world.process();

      int expiringTarget = fixture.monster(7, 0);
      NativeAiTargetOverride expiring = fixture.world.getMapper(NativeAiTargetOverride.class)
          .create(actor).setAttract(expiringTarget, player, 123, 1);
      assertTrue(fixture.world.getMapper(NativeAiTargetOverride.class).has(actor));
      assertEquals(expiringTarget, expiring.targetId);
      assertTrue(ai.validOverride(expiringTarget));
      assertFalse(ai.tickSpecial());
      assertEquals(0, expiring.remainingFrames);
      assertTrue(ai.validOverride(expiringTarget));
      assertEquals(expiringTarget, ai.nearest());
      assertFalse(ai.tickSpecial());
      assertFalse(fixture.world.getMapper(NativeAiTargetOverride.class).has(actor),
          "native AI reset must run after the requested duration");

      com.riiablo.map.Map sourceMap = new com.riiablo.map.Map(0, 1);
      com.riiablo.map.Map targetMap = new com.riiablo.map.Map(0, 2);
      fixture.world.getMapper(MapWrapper.class).create(actor).set(sourceMap, null);
      fixture.world.getMapper(MapWrapper.class).create(expiringTarget).set(targetMap, null);
      fixture.world.getMapper(NativeAiTargetOverride.class).create(actor)
          .setAttract(expiringTarget, player, 123, 10);
      assertFalse(ai.tickSpecial());
      assertFalse(fixture.world.getMapper(NativeAiTargetOverride.class).has(actor),
          "cross-map targets must immediately reset the temporary command");
    } finally {
      fixture.close();
    }
  }

  @Test
  void confuseDeterministicallyChoosesAnotherLiveMonster() {
    Fixture fixture = new Fixture();
    try {
      int actor = fixture.monster(0, 0);
      int first = fixture.monster(4, 0);
      int second = fixture.monster(5, 0);
      int corpse = fixture.monster(2, 0);
      fixture.world.getMapper(Corpse.class).create(corpse).reset(100, true);
      fixture.player(1, 0);
      fixture.world.getMapper(UnitStates.class).get(actor).stateList
          .addState(StateId.CONFUSE, 20, 1, 99);
      NativeAiTargetOverride override = fixture.world.getMapper(NativeAiTargetOverride.class)
          .create(actor).setConfuse(99, 61, 20, 0x110F);
      ProbeAI ai = fixture.ai(actor);

      assertFalse(ai.tickSpecial());
      assertTrue(override.targetId == first || override.targetId == second);
      assertEquals(override.targetId, ai.nearest());

      NativeRng expected = new NativeRng(0x110F);
      int[] sorted = { first, second };
      java.util.Arrays.sort(sorted);
      assertEquals(sorted[expected.nextInt(sorted.length)], override.targetId,
          "same entity order and seed must select the same confused target");

      fixture.world.getMapper(UnitStates.class).get(actor).stateList
          .removeState(StateId.CONFUSE);
      assertFalse(ai.tickSpecial());
      assertFalse(fixture.world.getMapper(NativeAiTargetOverride.class).has(actor),
          "removing Confuse must restore ordinary target selection");
    } finally {
      fixture.close();
    }
  }

  @Test
  void dimVisionCancelsRangedSequenceButSnapshotClientDoesNotRunAuthority() {
    Fixture fixture = new Fixture();
    try {
      int actor = fixture.monster(0, 0);
      UnitStates states = fixture.world.getMapper(UnitStates.class).get(actor);
      states.stateList.addState(StateId.DIMVISION, 20, 1, 99);
      fixture.world.getMapper(Casting.class).create(actor).set(10, Engine.INVALID_ENTITY,
          new Vector2(10, 0));
      fixture.world.getMapper(Sequence.class).create(actor)
          .sequence(Engine.Monster.MODE_S1, Engine.Monster.MODE_NU);
      ProbeAI ai = fixture.ai(actor);

      assertTrue(ai.tickSpecial());
      assertFalse(fixture.world.getMapper(Casting.class).has(actor));
      assertFalse(fixture.world.getMapper(Sequence.class).has(actor));

      states.snapshotOnly = true;
      NativeAiTargetOverride override = fixture.world.getMapper(NativeAiTargetOverride.class)
          .create(actor).setAttract(Engine.INVALID_ENTITY, 99, 59, 7);
      assertFalse(ai.tickSpecial());
      assertTrue(fixture.world.getMapper(NativeAiTargetOverride.class).has(actor));
      assertEquals(7, override.remainingFrames,
          "snapshot-only clients must not advance or repair authoritative AI state");
    } finally {
      fixture.close();
    }
  }

  private static final class Fixture {
    final World previous = Riiablo.engine;
    final World world = new World(new WorldConfigurationBuilder().build());

    Fixture() {
      Riiablo.engine = world;
    }

    ProbeAI ai(int entityId) {
      ProbeAI ai = new ProbeAI(entityId);
      ai.wire(world);
      ai.initialize();
      return ai;
    }

    int monster(float x, float y) {
      MonStats.Entry row = Riiablo.files.monstats.get("fallen1");
      int id = world.create();
      world.getMapper(Monster.class).create(id)
          .set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
      world.getMapper(Position.class).create(id).position.set(x, y);
      world.getMapper(AttributesWrapper.class).create(id).attrs = attributes();
      world.getMapper(UnitStates.class).create(id).init(id);
      world.getMapper(Velocity.class).create(id).setMonster(row.Velocity);
      world.getMapper(Size.class).create(id).size = 1;
      world.getMapper(Angle.class).create(id);
      return id;
    }

    int player(float x, float y) {
      int id = world.create();
      world.getMapper(Player.class).create(id).data =
          CharData.createRemote("target", (byte) Riiablo.BARBARIAN);
      world.getMapper(Position.class).create(id).position.set(x, y);
      world.getMapper(AttributesWrapper.class).create(id).attrs = attributes();
      return id;
    }

    int npc(float x, float y) {
      MonStats.Entry row = new MonStats.Entry();
      row.Id = "testnpc";
      row.npc = true;
      row.aidist = new int[] {35, 35, 35};
      int id = world.create();
      world.getMapper(Monster.class).create(id)
          .set(row, new com.riiablo.codec.excel.MonStats2.Entry());
      world.getMapper(Position.class).create(id).position.set(x, y);
      world.getMapper(AttributesWrapper.class).create(id).attrs = attributes();
      return id;
    }

    void close() {
      world.dispose();
      Riiablo.engine = previous;
    }
  }

  private static Attributes attributes() {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.hitpoints, 100);
    attrs.base().put(Stat.maxhp, 100);
    attrs.reset();
    return attrs;
  }

  private static final class ProbeAI extends AI {
    ProbeAI(int entityId) { super(entityId); }
    void wire(World world) {
      mMonster = world.getMapper(Monster.class);
      mPlayer = world.getMapper(Player.class);
      mPosition = world.getMapper(Position.class);
      mAngle = world.getMapper(Angle.class);
      mMapWrapper = world.getMapper(
          com.riiablo.engine.server.component.MapWrapper.class);
      mSize = world.getMapper(Size.class);
      mPathfind = world.getMapper(
          com.riiablo.engine.server.component.Pathfind.class);
      mVelocity = world.getMapper(Velocity.class);
      mRunning = world.getMapper(
          com.riiablo.engine.server.component.Running.class);
      mSequence = world.getMapper(Sequence.class);
      mCasting = world.getMapper(Casting.class);
      mAttributesWrapper = world.getMapper(AttributesWrapper.class);
      mNativeUnitFlags = world.getMapper(
          com.riiablo.engine.server.component.NativeUnitFlags.class);
      mMercenary = world.getMapper(
          com.riiablo.engine.server.component.Mercenary.class);
      mSummonedPet = world.getMapper(
          com.riiablo.engine.server.component.SummonedPet.class);
      mUnitStates = world.getMapper(UnitStates.class);
      mNativeAiTargetOverride = world.getMapper(NativeAiTargetOverride.class);
      mCorpse = world.getMapper(Corpse.class);
      pathfinder = new NoopPathfinder();
    }
    boolean tickSpecial() { return updateSpecialAiControl(1f / 25f); }
    int nearest() { return findNearestTargetWithAidist(new float[1]); }
    int continuing(int targetId) {
      return findTargetWithContinuity(targetId, new float[] {Float.MAX_VALUE});
    }
    boolean validOverride(int targetId) { return isValidOverrideMonsterTarget(targetId); }
  }

  @All({Pathfind.class, Position.class, Velocity.class})
  private static final class NoopPathfinder extends Pathfinder {
    @Override
    public boolean findPath(
        int src, Vector2 target, boolean raycast, int targetEntityId) {
      return false;
    }
  }
}
