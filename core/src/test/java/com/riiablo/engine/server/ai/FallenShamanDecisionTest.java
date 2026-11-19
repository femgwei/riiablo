package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static com.riiablo.engine.server.ai.LegacyFsmCheckpointIntegrationTest.fixture;

import com.riiablo.RiiabloTest;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.*;
import com.d2moo.common.drlg.D2Seed;
import org.junit.jupiter.api.Test;

class FallenShamanDecisionTest extends RiiabloTest {
  @Test void failedFirstShotCanRetryTheSameVisiblePlayerWithAnIndependentRoll() {
    try(Fixture f=fixture("FallenShaman",8)) {
      f.ai.params=new int[]{0,20,0,15,20,0,0,0};
      Monster monster=seed(f);
      f.ai.update(.04f); // command 51, first shot 31 fails, backup shot 12 succeeds.
      assertNotNull(f.casting());assertEquals(f.target,f.casting().targetId);
      assertEquals(671516612,monster.nativeSeed.getNLowSeed());
      assertEquals(330169957,monster.nativeSeed.getNHighSeed());
    }
  }

  @Test void backupScanSkipsBlockedPlayerAndShootsAnotherVisiblePlayer() {
    try(Fixture f=fixture("FallenShaman",8)) {
      f.ai.params=new int[]{0,20,0,15,20,0,0,0};
      int other=f.world.create();
      f.world.getMapper(Player.class).create(other);
      f.world.getMapper(Position.class).create(other).position.set(10,20);
      f.world.getMapper(Size.class).create(other).size=1;
      com.riiablo.map.Map map=org.mockito.Mockito.mock(com.riiablo.map.Map.class);
      com.riiablo.map.Map.Zone zone=org.mockito.Mockito.mock(com.riiablo.map.Map.Zone.class);
      f.world.getMapper(MapWrapper.class).get(f.actor).set(map,zone);
      org.mockito.Mockito.when(map.getZone(org.mockito.ArgumentMatchers.any(com.badlogic.gdx.math.Vector2.class)))
          .thenReturn(zone);
      org.mockito.Mockito.when(map.castRay(org.mockito.ArgumentMatchers.any(),
          org.mockito.ArgumentMatchers.eq(com.riiablo.map.DT1.Tile.FLAG_BLOCK_JUMP),
          org.mockito.ArgumentMatchers.eq(0),org.mockito.ArgumentMatchers.any()))
          .thenAnswer(call -> {
            com.badlogic.gdx.ai.utils.Ray<com.badlogic.gdx.math.Vector2> ray=call.getArgument(0);
            return ray.end.x==18f;
          });
      f.world.process();seed(f);f.ai.update(.04f);
      assertNotNull(f.casting());assertEquals(other,f.casting().targetId);
    }
  }

  private Monster seed(Fixture f) {
    Monster monster=f.world.getMapper(Monster.class).get(f.actor);
    monster.nativeSeed=new D2Seed(1,666);monster.rngState=1;
    return monster;
  }

  @Test void absentCorpseStillConsumesCommandRollAndCircleRollAtZeroChance() {
    try(Fixture f=fixture("FallenShaman",8)) {
      f.ai.params=new int[]{0,0,0,15,0,0,0,0};
      Monster monster=seed(f);
      f.ai.update(.04f);
      assertNull(f.casting());
      assertEquals(791599131,monster.nativeSeed.getNLowSeed());
      assertEquals(747178749,monster.nativeSeed.getNHighSeed());
    }
  }

  @Test void failedCommandRollDoesNotPreventIndependentSuccessfulResurrectionRoll() {
    try(Fixture f=fixture("FallenShaman",8)) {
      f.ai.params=new int[]{50,0,0,15,0,0,0,0};
      int corpse=f.ally("fallen1",0,12,10);
      f.world.getMapper(Corpse.class).create(corpse).usable=true;
      f.world.getMapper(CofReference.class).create(corpse).mode=Engine.Monster.MODE_DD;
      f.world.getMapper(Monster.class).get(corpse).setMinionOwner(f.actor);
      f.world.process();
      Monster monster=seed(f);
      f.ai.update(.04f); // 51 fails command, 31 succeeds resurrection.
      assertNotNull(f.casting());assertEquals(corpse,f.casting().targetId);
      assertFalse(f.world.getMapper(NativeAiCommand.class).has(corpse));
      assertEquals(791599131,monster.nativeSeed.getNLowSeed(),"no random cooldown draw");
      assertTrue(f.world.getMapper(Corpse.class).get(corpse).usable,"wait for animation keyframe");
    }
  }

  @Test void commandReachesDistantOwnedMinionsWithoutRecruitingOtherPacks() {
    try(Fixture f=fixture("FallenShaman",8)) {
      f.ai.params=new int[]{100,0,0,15,0,0,0,0};
      int owned=f.ally("fallen1",100,70,10);
      int foreign=f.ally("fallen1",100,12,10);
      f.world.getMapper(Monster.class).get(owned).setMinionOwner(f.actor);
      f.world.getMapper(Monster.class).get(foreign).setMinionOwner(9999);
      f.world.process();seed(f);f.ai.update(.04f);
      NativeAiCommand command=f.world.getMapper(NativeAiCommand.class).get(owned);
      assertNotNull(command);assertEquals(NativeAiCommand.ATTACK,command.command);
      assertFalse(f.world.getMapper(NativeAiCommand.class).has(foreign));
    }
  }

  @Test void circleUsesThreeStepNativeOrbitAndThirdDrawForDirection() {
    try(Fixture f=fixture("FallenShaman",8)) {
      f.ai.params=new int[]{0,0,100,15,0,0,0,0};
      Monster monster=seed(f);
      f.ai.update(.04f);
      org.mockito.Mockito.verify(f.paths).findNativeOrbit(f.actor,f.target,3,false);
      assertEquals(671516612,monster.nativeSeed.getNLowSeed());
      assertEquals(330169957,monster.nativeSeed.getNHighSeed());
    }
  }
}
