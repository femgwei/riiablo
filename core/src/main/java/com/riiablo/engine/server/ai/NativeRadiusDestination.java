package com.riiablo.engine.server.ai;

import com.badlogic.gdx.math.Vector2;

/** Coordinate request for AITACTICS_MoveInRadiusToTarget; path collision stays authoritative. */
final class NativeRadiusDestination {
  private NativeRadiusDestination() {}

  static Vector2 calculate(Vector2 source, Vector2 target, int size, int maxStep, int desiredDistance) {
    // Match the host cell projection used by the live melee predicate.
    int sx=Math.round(source.x), sy=Math.round(source.y);
    int tx=Math.round(target.x), ty=Math.round(target.y);
    int dx=Math.abs(tx-sx), dy=Math.abs(ty-sy);
    int xd=Math.abs(dx-size), yd=Math.abs(dy-size);
    int distance=(Math.min(xd,yd)+2*Math.max(xd,yd))/2;
    int sign=Integer.compare(distance,desiredDistance);
    int diff=Math.min(Math.abs(distance-desiredDistance),maxStep);
    int sum=Math.max(dx+dy,diff), x=0,y=0;
    if(sum>0) {
      x=diff*dx/sum;y=diff*dy/sum;
      while(x+y<diff){x++;y++;}
    }
    return new Vector2(sx+sign*x*Integer.compare(tx,sx),sy+sign*y*Integer.compare(ty,sy));
  }
}
