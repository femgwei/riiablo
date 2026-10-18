package com.riiablo.map;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.d2moo.common.drlg.D2DrlgLevel;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.d2moo.common.path.*;
import com.riiablo.codec.excel.*;
import com.riiablo.engine.SimulationClock;
import com.riiablo.engine.server.*;
import com.riiablo.engine.server.component.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativePathRuntimeTest {
  static final class Fixture implements AutoCloseable {
    final com.badlogic.gdx.Application previous = com.badlogic.gdx.Gdx.app;
    final Map map = new Map(1,0);
    final Map.Zone zone = new Map.Zone();
    final Pathfinder finder = new Pathfinder();
    final DynamicUnitCollisionSystem collision = new DynamicUnitCollisionSystem();
    final D2DrlgRoom source=new D2DrlgRoom();
    final World world;
    Fixture() {
      com.badlogic.gdx.Gdx.app = org.mockito.Mockito.mock(com.badlogic.gdx.Application.class);
      zone.map=map; zone.x=zone.y=10; zone.width=zone.height=90;
      zone.flags=new byte[8100]; zone.level=new Levels.Entry(); zone.level.Id=109;
      map.zones.add(zone);
      Map.RoomEx room=zone.addRoomEx(10,10,90,90);
      D2DrlgLevel level=new D2DrlgLevel(); level.setLevelId(109);
      source.setLevel(level); level.setFirstRoomEx(source);
      room.setNativePopulationMetadata(source);
      room.setAdjacentRoomIds(new int[0]);
      world=new World(new WorldConfigurationBuilder().with(collision,finder,new LeapSystem(),new VelocityAdder())
          .build().register("map",map));
      world.setDelta(SimulationClock.STEP_SECONDS);
    }
    int monster(int x,int y) {
      int id=world.create();
      world.getMapper(com.riiablo.engine.server.component.Class.class).create(id).type=
          com.riiablo.engine.server.component.Class.Type.MON;
      MonStats.Entry stats=new MonStats.Entry(); stats.Id=stats.BaseId="fallen1"; stats.Velocity=6; stats.Run=10;
      MonStats2.Entry sizes=new MonStats2.Entry(); sizes.SizeX=sizes.SizeY=2;
      world.getMapper(Monster.class).create(id).set(stats,sizes);
      world.getMapper(NativeUnitFlags.class).create(id);
      world.getMapper(Position.class).create(id).initializeNativeCell(x,y);
      world.getMapper(Velocity.class).create(id).setMonsterFromNativeTable(6);
      world.getMapper(Size.class).create(id).size=2;
      world.getMapper(Angle.class).create(id);
      world.getMapper(MapWrapper.class).create(id).set(map,zone);
      world.getMapper(NativePathIdentity.class).create(id).allocated(world,id);
      world.getMapper(NativeAiPathCache.class).create(id).allocated();
      collision.reserveNativeCollision(id);
      return id;
    }
    Pathfind path(int id) { return world.getMapper(Pathfind.class).get(id); }
    Position position(int id) { return world.getMapper(Position.class).get(id); }
    public void close() { world.dispose(); map.zones.clear(); com.badlogic.gdx.Gdx.app=previous; }
  }

  @Test void productionRequestAndTwentyFiveTicksUseNativeCursorAndRawPosition() {
    try(Fixture f=new Fixture()) {
      int id=f.monster(20,20);
      assertTrue(f.finder.findPath(id,new Vector2(32,20)));
      NativePathRuntime runtime=f.path(id).nativeRuntime;
      assertNotNull(runtime);
      assertEquals(13,runtime.result.path.pathType);
      for(int i=0;i<25;i++) f.world.process();
      assertEquals(D2PathStep.toFp16Center(20)+25*18432,f.position(id).nativePrecisionX());
      assertSame(runtime,f.path(id).nativeRuntime);
      assertEquals(0,runtime.result.path.nDist());
      assertTrue(f.world.getMapper(Velocity.class).get(id).nativeMovementOwned);
      assertTrue(f.world.getMapper(NativeAiPathCache.class).get(id).pointsKnown);
    }
  }

  @Test void failedNativeRequestNeverFallsBackToHostPlanner() {
    try(Fixture f=new Fixture()) {
      int id=f.monster(20,20);
      for(int y=19;y<=21;y++)for(int x=19;x<=21;x++)
        if(x!=20||y!=20) f.zone.setNativeCollisionMask(x,y,1);
      assertFalse(f.finder.findPath(id,new Vector2(30,20)));
      assertNull(f.path(id));
      assertEquals(0x1100,f.zone.nativeCollisionFlagsWorld(20,20));
    }
  }

  @Test void movedTargetUsesNativeFiveCellThresholdAndRetainsAllocationGuard() {
    try(Fixture f=new Fixture()) {
      int id=f.monster(20,20), target=f.monster(40,20);
      assertTrue(f.finder.findPath(id,f.position(target).position,false,target));
      NativePathRuntime runtime=f.path(id).nativeRuntime;
      f.position(target).initializeNativeCell(47,20);
      f.world.process();
      assertEquals(13,runtime.result.path.pathType);
      assertEquals(47,runtime.result.path.previousTargetPoint.x);
      f.world.delete(target);
      f.world.process();
      assertNull(f.path(id));
    }
  }

  @Test void circleUsesNativeTypeFlagsAndEndsWithoutHostRepath() {
    try(Fixture f=new Fixture()) {
      int id=f.monster(30,30), target=f.monster(40,30);
      assertTrue(f.finder.findNativeOrbit(id,target,8,true));
      NativePathRuntime runtime=f.path(id).nativeRuntime;
      assertEquals(5,runtime.result.path.pathType);
      assertEquals(2,runtime.result.path.directionOffset);
      for(int i=0;i<180 && f.path(id)!=null;i++) f.world.process();
      assertNull(f.path(id));
      assertEquals(0,runtime.result.path.pathPointCount);
    }
  }

  @Test void freezeRetainsRawPrecisionAndContinuationBudget() {
    try(Fixture f=new Fixture()) {
      int id=f.monster(20,20);
      assertTrue(f.finder.findPath(id,new Vector2(32,20)));
      f.world.process();
      NativePathRuntime runtime=f.path(id).nativeRuntime;
      int raw=f.position(id).nativePrecisionX(), budget=runtime.result.path.nDistance();
      f.world.getMapper(Velocity.class).get(id).stateMovementLocked=true;
      for(int i=0;i<6;i++) f.world.process();
      assertEquals(raw,f.position(id).nativePrecisionX());
      assertEquals(budget,runtime.result.path.nDistance());
    }
  }

  @Test void whirlwindMovementTestsNoPathButIgnoresOrdinaryMonsterMarker() {
    try(Fixture f=new Fixture()) {
      int id=f.monster(20,20), blocker=f.monster(24,20);
      f.world.getMapper(WhirlwindRuntime.class).create(id);
      assertTrue(f.collision.tryMove(id,20,20,2));
      assertFalse(f.collision.tryMove(id,23,20,2));
      assertEquals(0x1100,f.zone.nativeCollisionFlagsWorld(20,20));
      f.zone.resetNativeCollisionMask(24,20,0x1000);
      f.zone.setNativeCollisionMask(24,20,0x2000); // PET is absent from the WW movement mask.
      assertTrue(f.collision.tryMove(id,23,20,2));
      assertEquals(0x2100,f.zone.nativeCollisionFlagsWorld(24,20));
    }
  }

  @Test void replacingRoomGridRecapturesRoomsWithoutClearingNewTerrain() {
    try(Fixture f=new Fixture()) {
      int id=f.monster(20,20);
      assertTrue(f.finder.findPath(id,new Vector2(32,20)));
      NativePathRuntime runtime=f.path(id).nativeRuntime;
      f.zone.setNativeTileGrid(new com.riiablo.drlg.TileGrid(18,18),0);
      f.zone.setNativeCollisionMask(35,35,1);
      f.world.process();
      assertSame(runtime,f.path(id).nativeRuntime);
      assertEquals(1,f.zone.nativeCollisionFlagsWorld(35,35));
      assertTrue(f.position(id).hasNativePrecision());
    }
  }

  @Test void nativeTickRecachesRoomAndZoneAcrossExportedLevelSeam() {
    try(Fixture f=new Fixture()) {
      Map.Zone next=new Map.Zone(); next.map=f.map; next.x=100; next.y=10;
      next.width=next.height=90; next.flags=new byte[8100];
      next.level=new Levels.Entry(); next.level.Id=110; f.map.zones.add(next);
      Map.RoomEx nr=next.addRoomEx(100,10,90,90);
      nr.setAdjacentRoomIds(new int[0]);
      D2DrlgLevel level=new D2DrlgLevel(); level.setLevelId(110);
      D2DrlgRoom nativeNext=new D2DrlgRoom(); nativeNext.setLevel(level); level.setFirstRoomEx(nativeNext);
      nativeNext.setPpRoomsNear(new D2DrlgRoom[]{f.source}); nativeNext.setNRoomsNear(1);
      f.source.setPpRoomsNear(new D2DrlgRoom[]{nativeNext}); f.source.setNRoomsNear(1);
      f.zone.getRoomsEx().first().setNativePopulationMetadata(f.source);
      nr.setNativePopulationMetadata(nativeNext);
      f.map.addWarpDestinationOverride(109,0,110); f.zone.setNativeSeamAnchor(0,99,20);
      int id=f.monster(98,20);
      assertTrue(f.finder.findPath(id,new Vector2(103,20)));
      for(int i=0;i<12;i++) f.world.process();
      assertTrue(f.position(id).nativePrecisionX()>>>16 >= 100);
      assertSame(next,f.world.getMapper(MapWrapper.class).get(id).zone);
      assertEquals(0,f.zone.nativeCollisionFlagsWorld(98,20));
      assertTrue(f.position(id).hasNativePrecision());
    }
  }

  @Test void nativeLeapUsesRunVelocityAndSuppressesFootprintUntilLanding() {
    try(Fixture f=new Fixture()) {
      int id=f.monster(20,20);
      f.zone.setNativeCollisionMask(24,20,1); // Leap flight has no movement mask.
      Leap leap=f.world.getMapper(Leap.class).create(id).set(new Vector2(20,20),new Vector2(30,20),0.65f,-1);
      f.world.process();
      assertNotNull(leap.nativeTick);
      assertEquals(9,leap.nativeTick.result.path.pathType);
      assertEquals(D2PathStep.toFp16Center(20)+40960,f.position(id).nativePrecisionX());
      assertEquals(0,f.zone.nativeCollisionFlagsWorld(20,20));
      assertTrue(f.world.getMapper(Velocity.class).get(id).nativeMovementOwned);
      for(int i=1;i<16;i++) f.world.process();
      assertFalse(f.world.getMapper(Leap.class).has(id));
      assertEquals(D2PathStep.toFp16Center(30),f.position(id).nativePrecisionX());
      assertEquals(0x1100,f.zone.nativeCollisionFlagsWorld(30,20));
      assertEquals(1,f.zone.nativeCollisionFlagsWorld(24,20));
    }
  }

  @Test void nativeLeapFreezePreservesRawPositionAndResumesWithoutInterpolation() {
    try(Fixture f=new Fixture()) {
      int id=f.monster(20,20);
      f.world.getMapper(Leap.class).create(id).set(new Vector2(20,20),new Vector2(30,20),0.65f,-1);
      f.world.process();
      int raw=f.position(id).nativePrecisionX();
      f.world.getMapper(Velocity.class).get(id).stateMovementLocked=true;
      for(int i=0;i<4;i++)f.world.process();
      assertEquals(raw,f.position(id).nativePrecisionX());
      f.world.getMapper(Velocity.class).get(id).stateMovementLocked=false;
      f.world.process();
      assertEquals(raw+40960,f.position(id).nativePrecisionX());
    }
  }

  @Test void externalTeleportCancelsOldNativeCursorWithoutRewindingPosition() {
    try(Fixture f=new Fixture()) {
      int id=f.monster(20,20);
      assertTrue(f.finder.findPath(id,new Vector2(32,20)));
      f.world.process();
      f.position(id).initializeNativeCell(45,45);
      f.world.process();
      assertNull(f.path(id));
      assertEquals(D2PathStep.toFp16Center(45),f.position(id).nativePrecisionX());
      assertEquals(D2PathStep.toFp16Center(45),f.position(id).nativePrecisionY());
    }
  }

  @Test void externalTeleportCancelsLeapWithoutWritingItsOldPrecision() {
    try(Fixture f=new Fixture()) {
      int id=f.monster(20,20);
      f.world.getMapper(Leap.class).create(id).set(new Vector2(20,20),new Vector2(30,20),0.65f,-1);
      f.world.process();
      f.position(id).initializeNativeCell(45,45);
      f.world.process();
      assertFalse(f.world.getMapper(Leap.class).has(id));
      assertEquals(D2PathStep.toFp16Center(45),f.position(id).nativePrecisionX());
      assertEquals(0x1100,f.zone.nativeCollisionFlagsWorld(45,45));
    }
  }
}
