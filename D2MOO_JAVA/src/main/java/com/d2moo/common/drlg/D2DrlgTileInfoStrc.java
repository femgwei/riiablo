package com.d2moo.common.drlg;

/** Native {@code D2DrlgTileInfoStrc}; a scanned preset spawn/warp marker. */
public final class D2DrlgTileInfoStrc {
    private int nPosX;
    private int nPosY;
    private int nTileIndex;

    public D2DrlgTileInfoStrc() {}
    public D2DrlgTileInfoStrc(int x, int y, int tileIndex) {
        nPosX = x; nPosY = y; nTileIndex = tileIndex;
    }
    public int getNPosX() { return nPosX; }
    public void setNPosX(int value) { nPosX = value; }
    public int getNPosY() { return nPosY; }
    public void setNPosY(int value) { nPosY = value; }
    public int getNTileIndex() { return nTileIndex; }
    public void setNTileIndex(int value) { nTileIndex = value; }
}
