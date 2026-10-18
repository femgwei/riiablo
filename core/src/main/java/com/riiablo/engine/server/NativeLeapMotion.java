package com.riiablo.engine.server;

import com.artemis.World;
import com.d2moo.common.path.*;
import com.riiablo.engine.SimulationClock;
import com.riiablo.engine.server.component.*;
import com.riiablo.map.Map;

/** SrvDo077 / SetVelocityForLeap: type9, Run<<8, no movement/footprint mask.
 * Takeoff animation and landing selection remain the skill caller's responsibility. */
final class NativeLeapMotion {
  static boolean supports(World world, int id, Leap leap) {
    Position position=world.getMapper(Position.class).get(id);
    Monster monster=world.getMapper(Monster.class).get(id);
    return position.hasNativePrecision() && monster!=null && monster.monstats!=null
        && monster.monstats.Run>0 && monster.monstats.Run<=127
        && leap.destination.x==(int)leap.destination.x && leap.destination.y==(int)leap.destination.y
        && leap.destination.x>0 && leap.destination.x<=65535
        && leap.destination.y>0 && leap.destination.y<=65535
        && Math.abs((position.nativePrecisionX()>>>16)-leap.destination.x)<=100
        && Math.abs((position.nativePrecisionY()>>>16)-leap.destination.y)<=100
        && Math.abs(world.delta-SimulationClock.STEP_SECONDS)<0.000001f;
  }

  static boolean advance(World world,int id,Leap leap) {
    Position position=world.getMapper(Position.class).get(id);
    if(!position.hasNativePrecision() || com.artemis.PendingEntityDeletion.isDeleted(world,id)) return false;
    if(leap.nativeTick!=null && (position.nativePrecisionX()!=leap.nativeTick.result.precisionX
        || position.nativePrecisionY()!=leap.nativeTick.result.precisionY)) return false;
    if(leap.nativeTick==null) {
      D2PathContinuation.State path=new D2PathContinuation.State(1);
      path.currentPoint=new D2Path.Point(position.nativePrecisionX()>>>16,position.nativePrecisionY()>>>16);
      path.targetPoint=path.finalTargetPoint=new D2Path.Point((int)leap.destination.x,(int)leap.destination.y);
      path.setNewDistance(77); path.setDistance(20);
      D2PathResult.State result=new D2PathResult.State(path);
      result.precisionX=position.nativePrecisionX(); result.precisionY=position.nativePrecisionY();
      result.velocity=world.getMapper(Monster.class).get(id).monstats.Run<<8;
      D2PathTypes.setType(result,9);
      result.storePoints(java.util.Collections.singletonList(path.targetPoint));
      D2PathResult.finishSearch(result);
      leap.nativeTick=new D2PathTick.State(result);
    }
    Velocity velocity=world.getMapper(Velocity.class).get(id);
    DynamicUnitCollisionSystem collision=world.getSystem(DynamicUnitCollisionSystem.class);
    if(collision!=null) collision.synchronizeNativeCollision(id);
    if(velocity!=null) {
      velocity.nativeMovementOwned=true;
      velocity.velocity.setZero();
      if(velocity.stateMovementLocked) return true;
    }
    if(Math.abs(world.delta-SimulationClock.STEP_SECONDS)>=0.000001f) return true;
    D2PathResult.State result=leap.nativeTick.result;
    boolean active=D2PathTick.advance(leap.nativeTick,1024,new D2PathTick.Effects() {
      public boolean tryMove(int x,int y,int nx,int ny,boolean unchecked) { return true; }
      public void refresh(D2PathContinuation.State path) { recache(result); }
      public int search(D2PathContinuation.State path) { return 0; }
      public void recache(D2PathResult.State state) {
        MapWrapper wrapper=world.getMapper(MapWrapper.class).get(id);
        if(wrapper==null || wrapper.zone==null) return;
        Map.Zone zone=wrapper.zone.nativeCollisionZone(state.path.currentPoint.x,state.path.currentPoint.y);
        if(zone==null) return;
        wrapper.zone=zone;
        Map.RoomEx room=zone.findRoomEx(state.path.currentPoint.x,state.path.currentPoint.y);
        if(!wrapper.roomIdPinned) wrapper.roomId=room==null?-1:room.id;
      }
    });
    position.setNativePrecision(result.precisionX,result.precisionY);
    return active;
  }
}
