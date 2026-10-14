package com.riiablo.engine.server.ai;

import com.badlogic.gdx.Input;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.NativePlayerTargetSlots;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.NativeAiTargetOverride;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.Size;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** AiBaal.cpp target-score/owner-group projection; terrain is a test double. */
class BaalCrabTargetingIntegrationTest extends RiiabloTest {
  @Test
  void filledEarlierSlotBeatsLowerEntityIdOnEqualPlayerScores() {
    for (String ai : new String[] {"BaalCrab", "BaalCrabClone"}) {
      try (Act1NativeAiIntegrationTest.Fixture f = fixture(ai)) {
        int earlierId = player(f, 30, 10);
        int laterId = player(f, 30, 10);
        // Re-register the newest player in slot zero without freeing its ID.
        f.world.getMapper(Player.class).remove(f.target);
        f.world.getMapper(Player.class).remove(laterId);
        f.world.process();
        f.world.getMapper(Player.class).create(laterId);
        f.world.process();
        NativePlayerTargetSlots slots = f.world.getSystem(NativePlayerTargetSlots.class);
        assertEquals(laterId, slots.playerAtSlot(0));
        assertEquals(earlierId, slots.playerAtSlot(1));
        assertEquals(laterId, select(f), "native slot order, not ECS entity order, breaks the tie");
        assertEquals(2, baal(f).lastTargetCount());
      }
    }
  }

  @Test
  void culledSlotStillOccupiesItsPlaceAndAdmitsChildrenWhenHeadReturns() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture()) {
      int pet = pet(f, false, 30, 10, 2);
      stats(f, f.target, 0);
      f.targetPosition().set(65, 10);
      f.world.process();
      assertEquals(Engine.INVALID_ENTITY, select(f));
      assertEquals(0, baal(f).lastTargetCount());
      assertEquals(0, f.world.getSystem(NativePlayerTargetSlots.class).slotOf(f.target));
      f.targetPosition().set(20, 10);
      assertEquals(pet, select(f));
      assertEquals(2, baal(f).lastTargetCount());
    }
  }

  @Test
  void unregisteredNinthPlayerAndItsChildrenDoNotEnterPrimaryGroups() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture()) {
      for (int i = 1; i < NativePlayerTargetSlots.SLOT_COUNT; i++) player(f, 30, 10);
      int ninth = player(f, 30, 10);
      int child = pet(f, false, 30, 10, 2);
      f.world.getMapper(SummonedPet.class).get(child).ownerId = ninth;
      for (int id : new int[] {ninth, child}) {
        stats(f, id, 100).base().put(Stat.maxdamage, 1000);
        attrs(f, id).reset();
      }
      f.world.process();
      assertEquals(f.target, select(f));
      assertEquals(8, baal(f).lastTargetCount());
      assertEquals(NativePlayerTargetSlots.UNREGISTERED,
          f.world.getSystem(NativePlayerTargetSlots.class).slotOf(ninth));
    }
  }

  @Test
  void removedPlayerGroupIsNotCountedWhileOtherSlotKeepsItsIndex() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture()) {
      pet(f, false, 30, 10, 2);
      int other = player(f, 30, 10);
      f.world.process();
      f.world.getMapper(Player.class).remove(f.target);
      f.world.process();
      assertEquals(other, select(f));
      assertEquals(1, baal(f).lastTargetCount());
      assertEquals(1, f.world.getSystem(NativePlayerTargetSlots.class).slotOf(other));
    }
  }

  @Test
  void highestScoreWinsRatherThanNearestAndEqualScoreKeepsFirstHead() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture()) {
      int far = player(f, 50, 10);
      stats(f, far, 100).base().put(Stat.maxdamage, 1000);
      attrs(f, far).reset();
      assertEquals(far, select(f));
      assertEquals(2, baal(f).lastTargetCount());
      assertEquals(76, baal(f).lastMaxTargetScore());
      attrs(f, far).base().put(Stat.maxdamage, 0);
      attrs(f, far).reset();
      f.targetPosition().set(40, 10);
      f.world.getMapper(Position.class).get(far).position.set(20, 10);
      assertEquals(f.target, select(f), "ties do not replace the first head with a closer one");
    }
  }

  @Test
  void strict55GateUsesNoSizeIntegerGridDistance() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture()) {
      f.world.getMapper(Size.class).get(f.target).size = 10;
      for (int[] point : new int[][] {{64, 10, 1}, {65, 10, 0}, {46, 46, 1}, {47, 47, 0}}) {
        f.targetPosition().set(point[0], point[1]);
        assertEquals(point[2] == 1 ? f.target : Engine.INVALID_ENTITY, select(f));
        assertEquals(point[2], baal(f).lastTargetCount());
      }
    }
  }

  @Test
  void bossScopeBypassesOrdinaryAidistSpawnAndRoomGatesOnly() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture()) {
      MonStats.Entry scope = new MonStats.Entry();
      scope.aidist = new int[] {1, 1, 1};
      baal(f).monster.monstats = scope;
      baal(f).monster.hasSpawnAnchor = true;
      baal(f).monster.spawnX = -1000;
      baal(f).monster.spawnY = -1000;
      Map map = mock(Map.class);
      Map.Zone source = zone(4);
      Map.Zone target = zone(4);
      baal(f).monster.spawnZone = source;
      f.world.getMapper(MapWrapper.class).get(f.actor).set(map, source);
      f.world.getMapper(MapWrapper.class).create(f.target).set(map, target);
      f.targetPosition().set(64, 10);
      assertFalse(f.ai.isValidEnemyTarget(f.target), "ordinary shared filter still enforces scope");
      assertEquals(f.target, select(f));
      f.world.getMapper(MapWrapper.class).get(f.target).zone = source;
      assertEquals(f.target, select(f), "non-adjacent rooms do not add a second boss range gate");
    }
  }

  @Test
  void differentMapActAndTownHeadsAreCulled() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture()) {
      Map map = mock(Map.class);
      Map.Zone source = zone(4);
      Map.Zone target = zone(4);
      f.world.getMapper(MapWrapper.class).get(f.actor).set(map, source);
      MapWrapper wrapper = f.world.getMapper(MapWrapper.class).create(f.target).set(map, target);
      assertEquals(f.target, select(f));
      wrapper.map = mock(Map.class);
      assertEquals(Engine.INVALID_ENTITY, select(f));
      wrapper.map = map;
      target.level.Act = 3;
      assertEquals(Engine.INVALID_ENTITY, select(f));
      target.level.Act = 4;
      when(target.isTown()).thenReturn(true);
      assertEquals(Engine.INVALID_ENTITY, select(f));
      assertEquals(0, baal(f).lastTargetCount());
    }
  }

  @Test
  void deadOrUntargetableHeadStillCountsButCannotBeSelected() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture()) {
      for (int mode : new int[] {Engine.Player.MODE_DT, Engine.Player.MODE_DD}) {
        f.world.getMapper(Sequence.class).create(f.target).sequence((byte) mode, (byte) mode);
        assertEquals(Engine.INVALID_ENTITY, select(f));
        assertEquals(1, baal(f).lastTargetCount());
      }
      f.world.getMapper(Sequence.class).remove(f.target);
      stats(f, f.target, 0);
      assertEquals(Engine.INVALID_ENTITY, select(f));
      stats(f, f.target, 100);
      NativeUnitFlags flags = f.world.getMapper(NativeUnitFlags.class).create(f.target);
      flags.set(NativeUnitFlags.TARGETABLE);
      assertEquals(Engine.INVALID_ENTITY, select(f));
      flags.set(NativeUnitFlags.CAN_BE_ATTACKED);
      assertEquals(f.target, select(f));
      f.world.getMapper(Corpse.class).create(f.target);
      assertEquals(Engine.INVALID_ENTITY, select(f));
      assertEquals(1, baal(f).lastTargetCount());
    }
  }

  @Test
  void deadPlayerHeadStillAllowsLivingMercenaryAndSummon() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture()) {
      stats(f, f.target, 0);
      int pet = pet(f, false, 30, 10, 2);
      int merc = pet(f, true, 40, 10, 2);
      attrs(f, merc).base().put(Stat.maxdamage, 1000);
      attrs(f, merc).reset();
      f.world.process();
      assertEquals(merc, select(f));
      assertEquals(3, baal(f).lastTargetCount());
      f.world.getMapper(Sequence.class).create(merc)
          .sequence(Engine.Monster.MODE_DT, Engine.Monster.MODE_DD);
      assertEquals(pet, select(f));
      assertEquals(2, baal(f).lastTargetCount(), "dead monster child is unlinked; dead player head stays");
    }
  }

  @Test
  void equalScoreChildrenUseJoinEventsAcrossMercenariesAndSummonsNotEntityIds() {
    for (String ai : new String[] {"BaalCrab", "BaalCrabClone"}) {
      try (Act1NativeAiIntegrationTest.Fixture f = fixture(ai)) {
        int merc = pet(f, true, 30, 10, 2);
        f.world.getMapper(Mercenary.class).get(merc).ownerId = -1;
        int summon = pet(f, false, 30, 10, 2);
        for (int id : new int[] {merc, summon}) {
          attrs(f, id).base().put(Stat.maxdamage, 1000);
          attrs(f, id).reset();
        }
        f.world.getMapper(Mercenary.class).get(merc).set(f.target, 0, 1, 1, 0);
        f.world.process();
        assertEquals(merc, select(f), "lower-ID hireling joins after the higher-ID summon");
        assertEquals(3, baal(f).lastTargetCount());
        f.world.getMapper(SummonedPet.class).remove(summon);
        f.world.process();
        f.world.getMapper(SummonedPet.class).create(summon).set(f.target, "skeleton", 70, 1, false, 0);
        assertEquals(summon, select(f), "a later summon insertion shares the same order domain");
      }
    }
  }

  @Test
  void latestChildCannotJumpAnEqualScorePlayerHead() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture()) {
      int child = pet(f, false, 20, 10, 2);
      f.world.process();
      assertEquals(baal(f).targetScore(f.target), baal(f).targetScore(child));
      assertEquals(f.target, select(f));
    }
  }

  @Test
  void latestChildInLaterPlayerGroupCannotJumpEarlierEqualScoreChild() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture()) {
      int first = pet(f, false, 30, 10, 2);
      int otherPlayer = player(f, 30, 10);
      int later = pet(f, false, 30, 10, 2);
      f.world.getMapper(SummonedPet.class).get(later).ownerId = otherPlayer;
      for (int id : new int[] {first, later}) {
        attrs(f, id).base().put(Stat.maxdamage, 1000);
        attrs(f, id).reset();
      }
      f.world.process();
      assertEquals(first, select(f));
      assertEquals(4, baal(f).lastTargetCount());
    }
  }

  @Test
  void deathModesAndCorpseDetachMonsterChildrenButHpZeroAloneDoesNot() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture()) {
      int child = pet(f, false, 30, 10, 2);
      stats(f, child, 0);
      f.world.process();
      assertEquals(f.target, select(f));
      assertEquals(2, baal(f).lastTargetCount());
      for (int mode : new int[] {Engine.Monster.MODE_DT, Engine.Monster.MODE_DD}) {
        f.world.getMapper(Sequence.class).create(child).sequence((byte) mode, (byte) mode);
        assertEquals(f.target, select(f));
        assertEquals(1, baal(f).lastTargetCount());
      }
      f.world.getMapper(Sequence.class).remove(child);
      f.world.getMapper(Corpse.class).create(child);
      assertEquals(f.target, select(f));
      assertEquals(1, baal(f).lastTargetCount());
    }
  }

  @Test
  void rangeGateIsOnOwnerHeadNotEachChild() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture()) {
      int pet = pet(f, false, 110, 10, 2);
      stats(f, f.target, 0);
      f.world.process();
      assertEquals(pet, select(f), "in-range head includes its out-of-range child");
      assertEquals(2, baal(f).lastTargetCount());
      f.targetPosition().set(65, 10);
      f.world.getMapper(Position.class).get(pet).position.set(11, 10);
      assertEquals(Engine.INVALID_ENTITY, select(f), "nearby child cannot bypass rejected head");
      assertEquals(0, baal(f).lastTargetCount());
    }
  }

  @Test
  void lowThreatCountsWithoutWinningAndBoneWallIsNotPrimaryGroup() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture()) {
      stats(f, f.target, 0);
      int low = pet(f, false, 11, 10, 1);
      int wall = pet(f, false, 11, 10, 2);
      f.world.getMapper(SummonedPet.class).get(wall).boneWall = true;
      f.world.process();
      assertEquals(0, baal(f).targetScore(low));
      assertEquals(Engine.INVALID_ENTITY, select(f));
      assertEquals(2, baal(f).lastTargetCount(), "node 9 wall excluded, low-threat child still counted");
    }
  }

  @Test
  void scoreMatchesNativeResistAndElementalDamageArithmetic() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture()) {
      Attributes stats = stats(f, f.target, 100);
      assertEquals(30, baal(f).targetScore(f.target));
      stats.base().put(Stat.fireresist, 15);
      stats.base().put(Stat.coldresist, 30);
      stats.base().put(Stat.lightresist, 45);
      stats.base().put(Stat.damageresist, 60);
      stats.base().put(Stat.magicresist, 75);
      stats.reset();
      assertEquals(33, baal(f).targetScore(f.target)); // (62 + 675) / 22
      stats = stats(f, f.target, 100);
      stats.base().put(Stat.maxdamage, 100);
      stats.base().put(Stat.firemaxdam, 20);
      stats.base().put(Stat.lightmaxdam, 40);
      stats.base().put(Stat.magicmaxdam, 60);
      stats.base().put(Stat.coldmaxdam, 80);
      stats.base().put(Stat.poisonmaxdam, 10 * 256 + 255);
      stats.reset();
      assertEquals(44, baal(f).targetScore(f.target)); // (675 + 2 * (310 / 2)) / 22
    }
  }

  @Test
  void negativeScoreIsNotClampedAndCannotWinAgainstInitialZero() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture()) {
      Attributes stats = stats(f, f.target, 100);
      stats.base().put(Stat.damageresist, -33000);
      stats.reset();
      assertEquals(-369, baal(f).targetScore(f.target));
      assertEquals(Engine.INVALID_ENTITY, select(f));
      assertEquals(0, baal(f).lastMaxTargetScore());
    }
  }

  @Test
  void damageSumNarrowsToSigned32BeforeHalving() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture()) {
      Attributes stats = stats(f, f.target, 100);
      stats.base().put(Stat.maxdamage, Integer.MAX_VALUE);
      stats.base().put(Stat.firemaxdam, 101);
      stats.reset();
      assertEquals(-97612857, baal(f).targetScore(f.target));
    }
  }

  @Test
  void actionWeightsCountTraversedNodesNotJustLivingSelectableTargets() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture()) {
      stats(f, f.target, 0);
      pet(f, false, 20, 10, 1);
      int dead = pet(f, true, 20, 10, 2);
      stats(f, dead, 0);
      int living = pet(f, false, 20, 10, 2);
      f.world.process();
      assertEquals(living, select(f));
      assertEquals(4, baal(f).lastTargetCount());
      assertEquals(65, baal(f).decisionWeights(living, false)[13]);
    }
  }

  @Test
  void cloneInheritsHighestScoreOwnerGroupSelection() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrabClone")) {
      assertTrue(f.ai instanceof BaalCrabClone);
      int far = player(f, 50, 10);
      stats(f, far, 100).base().put(Stat.maxdamage, 1000);
      attrs(f, far).reset();
      assertEquals(far, select(f));
      assertEquals(76, baal(f).lastMaxTargetScore());
      assertEquals(2, baal(f).lastTargetCount());
    }
  }

  @Test
  void lifeColdMeleeBarrierAndZeroScoreBoundaries() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture()) {
      stats(f, f.target, 19.9f);
      assertEquals(17, baal(f).targetScore(f.target));
      stats(f, f.target, 20f);
      assertEquals(30, baal(f).targetScore(f.target));
      UnitStates states = f.world.getMapper(UnitStates.class).create(f.target).init(f.target);
      states.stateList.addState(StateId.COLD, 100, 1, f.actor);
      assertEquals(48, baal(f).targetScore(f.target));
      states.stateList.clearAll();
      f.targetPosition().set(11, 10);
      assertEquals(36, baal(f).targetScore(f.target));
      Map map = barrierMap(f);
      assertEquals(36, baal(f).targetScore(f.target), "melee score takes precedence over barrier");
      f.targetPosition().set(20, 10);
      assertEquals(13, baal(f).targetScore(f.target));
      stats(f, f.target, 19f);
      assertEquals(1, baal(f).targetScore(f.target), "only exactly zero becomes one");
      verify(map, atLeastOnce()).castRay(any(), any(), eq(DT1.Tile.FLAG_BLOCK_JUMP), eq(0), any(), eq(false));
    }
  }

  @Test
  void leftRightRanksUseRealSkillsEffectiveLevelsAndActiveWeaponSet() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture()) {
      stats(f, f.target, 100);
      CharData data = CharData.createRemote("BaalTarget", (byte) 1);
      f.world.getMapper(Player.class).get(f.target).data = data;
      int left = 36; // Fire Bolt
      int right = 64; // Frozen Orb
      int leftRank = Riiablo.files.NativeSkills.get(left).nativeInteger("attackrank");
      int rightRank = Riiablo.files.NativeSkills.get(right).nativeInteger("attackrank");
      assertTrue(leftRank > 0 && rightRank > 0);
      data.setSkillLevel(left, 12);
      data.setSkillLevel(right, 20);
      data.setAction(0, Input.Buttons.LEFT, left);
      data.setAction(0, Input.Buttons.RIGHT, right);
      int rank = (12 * leftRank + 20 * rightRank) / 4;
      assertEquals((675 + 2 * rank) / 22, baal(f).targetScore(f.target));
      f.world.getMapper(UnitStates.class).create(f.target).init(f.target).stateList
          .addState(StateId.BATTLECOMMAND, 100, 1, f.target).skillModifier = 3;
      rank = (15 * leftRank + 23 * rightRank) / 4;
      assertEquals((675 + 2 * rank) / 22, baal(f).targetScore(f.target));
      data.getItems().setAlternate(1);
      assertEquals(30, baal(f).targetScore(f.target), "alternate set has no learned hand skills");
    }
  }

  @Test
  void maxScoreFeedbackWithMultipleTargetsIsStrictlyOver60AndOnlyOpenRanged() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture()) {
      player(f, 25, 10); // Single-target Normal/Nightmare curse gate is tested separately.
      Attributes stats = stats(f, f.target, 100);
      stats.base().put(Stat.maxdamage, 646);
      stats.reset();
      assertEquals(f.target, select(f));
      assertEquals(60, baal(f).lastMaxTargetScore());
      assertEquals(40, baal(f).decisionWeights(f.target, false)[8]);
      stats.base().put(Stat.maxdamage, 668);
      stats.reset();
      select(f);
      assertEquals(61, baal(f).lastMaxTargetScore());
      assertEquals(70, baal(f).decisionWeights(f.target, false)[8]);
      assertEquals(20, baal(f).decisionWeights(f.target, true)[8]);
      barrierMap(f);
      assertEquals(80, baal(f).decisionWeights(f.target, false)[8]);
    }
  }

  @Test
  void normalScoreDoesNotGainOptionalAnchorBonusOrConsumeRandomDraws() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture()) {
      baal(f).beforeDecision();
      f.world.getMapper(Position.class).get(f.actor).position.set(110, 10);
      f.targetPosition().set(70, 10);
      Riiablo.gameSeed = 123;
      D2Seed expected = new D2Seed(123 ^ (f.actor * 0x45D9F3B), 666);
      assertEquals(f.target, select(f));
      assertEquals(30, baal(f).lastMaxTargetScore(), "Fn135 passes null optional score args");
      assertEquals(Seed.rollRandomNumber(expected) & 0xFFFFFFFFL,
          f.ai.rollAiRandomNumber());
    }
  }

  @Test
  void explicitAttractOverrideRetainsAuthorityOverBossPlayerScan() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture()) {
      int monster = f.ally("skeleton1", 100, 15, 10);
      f.world.getMapper(NativeAiTargetOverride.class).create(f.actor)
          .setAttract(monster, f.target, 86, 100);
      f.world.process();
      assertEquals(monster, select(f));
    }
  }

  private static Act1NativeAiIntegrationTest.Fixture fixture() {
    return fixture("BaalCrab");
  }
  private static Act1NativeAiIntegrationTest.Fixture fixture(String aiName) {
    for (MonStats.Entry row : Riiablo.files.monstats) {
      if (aiName.equalsIgnoreCase(row.AI)) {
        return new Act1NativeAiIntegrationTest.Fixture(row.Id, 0, 10);
      }
    }
    throw new AssertionError("No 1.10f " + aiName + " row");
  }

  private static BaalCrab baal(Act1NativeAiIntegrationTest.Fixture f) { return (BaalCrab) f.ai; }
  private static int select(Act1NativeAiIntegrationTest.Fixture f) {
    return baal(f).findNearestTargetWithAidist(new float[] {Float.MAX_VALUE});
  }
  private static Attributes attrs(Act1NativeAiIntegrationTest.Fixture f, int id) {
    return f.world.getMapper(AttributesWrapper.class).get(id).attrs;
  }
  private static Attributes stats(Act1NativeAiIntegrationTest.Fixture f, int id, float hp) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, 100f);
    attrs.reset();
    f.world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
    return attrs;
  }
  private static int player(Act1NativeAiIntegrationTest.Fixture f, float x, float y) {
    int id = f.world.create();
    f.world.getMapper(Player.class).create(id);
    f.world.getMapper(Position.class).create(id).position.set(x, y);
    f.world.getMapper(Size.class).create(id).size = 1;
    f.world.process();
    return id;
  }
  private static int pet(Act1NativeAiIntegrationTest.Fixture f, boolean merc, float x, float y, int threat) {
    int id = f.ally("skeleton1", 100, x, y);
    // Never mutate the shared real MonStats table.
    MonStats.Entry row = new MonStats.Entry();
    row.threat = threat;
    f.world.getMapper(com.riiablo.engine.server.component.Monster.class).get(id).monstats = row;
    f.world.getMapper(Size.class).create(id).size = 1;
    if (merc) f.world.getMapper(Mercenary.class).create(id).ownerId = f.target;
    else f.world.getMapper(SummonedPet.class).create(id).set(f.target, "skeleton", 70, 1, false, 0);
    return id;
  }
  private static Map.Zone zone(int act) {
    Map.Zone zone = mock(Map.Zone.class);
    zone.level = new Levels.Entry();
    zone.level.Act = act;
    return zone;
  }
  private static Map barrierMap(Act1NativeAiIntegrationTest.Fixture f) {
    Map map = mock(Map.class);
    f.world.getMapper(MapWrapper.class).get(f.actor).set(map, zone(4));
    when(map.castRay(any(), any(), eq(DT1.Tile.FLAG_BLOCK_JUMP), eq(0), any(), eq(false))).thenReturn(true);
    return map;
  }
}
