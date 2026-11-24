package com.riiablo.presentation;

import com.d2moo.common.path.D2PathStep;

/** 1.10f CltDo60: fixed birth centre, integer tangent targets and fp16 steps at 25Hz. */
public final class NativeHurricanePresentation {
  private static final int[] X={7,5,0,-5,-7,-5,0,5};
  private static final int[] Y={0,5,7,5,0,-5,-7,-5};
  private static final int[] DCC8={4,0,5,1,6,2,7,3};
  private NativeHurricanePresentation() {}

  public static int direction(int nativeDirection, int directions) {
    return directions==8 ? DCC8[((nativeDirection+4)&63)>>3] : 0;
  }

  public static Flight flight(int centerX,int centerY,int lane,int velocity) {
    return new Flight(centerX,centerY,X[Math.floorMod(lane,8)],Y[Math.floorMod(lane,8)],velocity);
  }

  public static final class Flight {
    public final int centerX,centerY;
    public int x,y,velocityX,velocityY,direction,age;
    private final int velocity;

    private Flight(int cx,int cy,int dx,int dy,int speed) {
      centerX=cx;centerY=cy;velocity=(Math.max(0,speed)<<8)*75/100;
      x=D2PathStep.toFp16Center(cx+dx);y=D2PathStep.toFp16Center(cy+dy);
      aim(D2PathStep.toFp16Center(cx+dx+dy),D2PathStep.toFp16Center(cy+dy-dx));
    }

    private void aim(int tx,int ty) {
      D2PathStep.DirectionVector vector=D2PathStep.directionVector(x,y,tx,ty);
      velocityX=(vector.x*velocity)>>8;velocityY=(vector.y*velocity)>>8;
      direction=vector.direction;
    }

    public void advanceTo(int targetAge) {
      // Resource arrival/refresh cannot rewind an already advanced flight.
      while(age<Math.max(0,targetAge)) {
        if((age&1)==0) {
          int ix=x>>>16,iy=y>>>16;
          aim(D2PathStep.toFp16Center(ix+iy-centerY),D2PathStep.toFp16Center(iy-ix+centerX));
        }
        x+=velocityX;y+=velocityY;age++;
      }
    }
  }
}
