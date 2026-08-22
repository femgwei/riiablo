package com.d2moo.common.drlg;

/**
 * 坐标结构
 * 对应 C++ 结构：D2CoordStrc
 */
public class D2Coord {
    private int x;
    private int y;
    
    public D2Coord() {
        this(0, 0);
    }
    
    public D2Coord(int x, int y) {
        this.x = x;
        this.y = y;
    }
    
    public int getX() {
        return x;
    }
    
    public void setX(int x) {
        this.x = x;
    }
    
    public int getY() {
        return y;
    }
    
    public void setY(int y) {
        this.y = y;
    }
}
