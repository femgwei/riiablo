package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static com.riiablo.engine.server.ai.LegacyFsmCheckpointIntegrationTest.fixture;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.*;
import org.junit.jupiter.api.Test;

class FallenShamanTargetPriorityTest extends RiiabloTest {
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
  void originalClayGolemFixtureSelectsTheAttackableHighThreatSummon(boolean barrier) {
    // Original run 20261119-060755-9bce64da: player (4521,5642),
    // Shaman (4529,5642), native Clay Golem (4525,5644).
    // Translate and mirror x into the detached test arena.
    try(Fixture f=fixture("FallenShaman",8)) {
      int golem=f.ally("claygolem",100,14,12);
      f.world.getMapper(SummonedPet.class).create(golem).ownerId=f.target;
      f.world.getMapper(Size.class).create(golem).size=2;
      f.world.getMapper(NativeUnitFlags.class).create(golem).set((int)2147614734L);
      assertEquals(11,f.world.getMapper(Monster.class).get(golem).monstats.threat);
      if(barrier) {
        com.riiablo.map.Map map=org.mockito.Mockito.mock(com.riiablo.map.Map.class);
        com.riiablo.map.Map.Zone zone=org.mockito.Mockito.mock(com.riiablo.map.Map.Zone.class);
        f.world.getMapper(MapWrapper.class).get(f.actor).set(map,zone);
        org.mockito.Mockito.when(map.getZone(org.mockito.ArgumentMatchers.any(com.badlogic.gdx.math.Vector2.class)))
            .thenReturn(zone);
        org.mockito.Mockito.when(map.castRay(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.eq(0),
            org.mockito.ArgumentMatchers.any())).thenAnswer(call -> {
              com.badlogic.gdx.ai.utils.Ray<com.badlogic.gdx.math.Vector2> ray=call.getArgument(0);
              return ray.end.x==18f;
            });
      }
      f.world.process();
      assertEquals(golem,((FallenShaman)f.ai).selectBackupTarget(new float[1]));
    }
  }

  @Test void originalHydraFlagsExcludeItFromTheBackupTargetScan() {
    try(Fixture f=fixture("FallenShaman",8)) {
      int hydra=f.ally("hydra1",100,13,10);
      f.world.getMapper(SummonedPet.class).create(hydra).ownerId=f.target;
      f.world.getMapper(NativeUnitFlags.class).create(hydra).set((int)2147614730L);
      f.world.process();
      assertFalse(f.world.getMapper(NativeUnitFlags.class).get(hydra).has(NativeUnitFlags.CAN_BE_ATTACKED));
      assertEquals(f.target,((FallenShaman)f.ai).selectBackupTarget(new float[1]));
    }
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
  void terrainProbeDoesNotReplaceAnExistingMovementPath(boolean blocked) {
    try(Fixture f=fixture("FallenShaman",12)) {
      com.riiablo.map.Map map=org.mockito.Mockito.mock(com.riiablo.map.Map.class);
      com.riiablo.map.Map.Zone zone=new com.riiablo.map.Map.Zone();
      f.world.getMapper(MapWrapper.class).get(f.actor).set(map,zone);
      org.mockito.Mockito.when(map.isBlockedForPath(org.mockito.ArgumentMatchers.eq(zone),
          org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt(),
          org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt(),
          org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(blocked);
      Pathfind path=f.world.getMapper(Pathfind.class).create(f.actor);
      assertEquals(!blocked,((FallenShaman)f.ai).backupApproachHasPoints(f.target));
      assertSame(path,f.world.getMapper(Pathfind.class).get(f.actor));
      org.mockito.Mockito.verifyNoInteractions(f.paths);
    }
  }

  private int pet(Fixture f,int threat,int x) {
    int id=f.ally("fallen1",100,x,10);
    com.riiablo.codec.excel.MonStats.Entry row=new com.riiablo.codec.excel.MonStats.Entry();
    row.Id="test-pet";row.threat=threat;row.Align=0;row.killable=true;
    f.world.getMapper(Monster.class).get(id).monstats=row;
    f.world.getMapper(SummonedPet.class).create(id).ownerId=f.target;
    f.world.getMapper(Size.class).create(id).size=1;
    f.world.process();return id;
  }
  @Test void knownApproachToPlayerKeepsPrimaryOverNearbyLowThreatPet() {
    try(Fixture f=fixture("FallenShaman",12)) {
      pet(f,1,13);
      FallenShaman ai=org.mockito.Mockito.spy((FallenShaman)f.ai);
      org.mockito.Mockito.doReturn(Boolean.TRUE).when(ai).backupApproachHasPoints(f.target);
      assertEquals(f.target,ai.selectBackupTarget(new float[1]));
    }
  }
  @Test void zeroPathPointsCanSelectNearbyLowThreatPet() {
    try(Fixture f=fixture("FallenShaman",12)) {
      int pet=pet(f,1,13);
      FallenShaman ai=org.mockito.Mockito.spy((FallenShaman)f.ai);
      org.mockito.Mockito.doReturn(Boolean.FALSE).when(ai).backupApproachHasPoints(f.target);
      assertEquals(pet,ai.selectBackupTarget(new float[1]));
    }
  }
  @Test void distantLowThreatPetCannotDisplacePrimaryEvenWithZeroPoints() {
    try(Fixture f=fixture("FallenShaman",12)) {
      pet(f,1,18);
      FallenShaman ai=org.mockito.Mockito.spy((FallenShaman)f.ai);
      org.mockito.Mockito.doReturn(Boolean.FALSE).when(ai).backupApproachHasPoints(f.target);
      assertEquals(f.target,ai.selectBackupTarget(new float[1]));
      org.mockito.Mockito.verify(ai,org.mockito.Mockito.never()).backupApproachHasPoints(f.target);
    }
  }
  @Test void anotherHighThreatTargetWithinTwentyTakesPrecedenceOverLowThreatFallback() {
    try(Fixture f=fixture("FallenShaman",8)) {
      pet(f,1,13);int high=pet(f,2,23);
      FallenShaman ai=org.mockito.Mockito.spy((FallenShaman)f.ai);
      org.mockito.Mockito.doReturn(Boolean.FALSE).when(ai).backupApproachHasPoints(f.target);
      assertEquals(high,ai.selectBackupTarget(new float[1]));
    }
  }
  @Test void unknownTerrainRetainsPrimaryWithoutInstallingAPath() {
    try(Fixture f=fixture("FallenShaman",12)) {
      pet(f,1,13);
      FallenShaman ai=(FallenShaman)f.ai;
      assertNull(ai.backupApproachHasPoints(f.target));
      assertEquals(f.target,ai.selectBackupTarget(new float[1]));
      org.mockito.Mockito.verifyNoInteractions(f.paths);
      assertFalse(f.world.getMapper(Pathfind.class).has(f.actor));
    }
  }
}
