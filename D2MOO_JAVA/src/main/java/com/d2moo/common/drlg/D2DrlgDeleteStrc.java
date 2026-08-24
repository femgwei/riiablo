package com.d2moo.common.drlg;

/** Native per-room unit deletion record ({@code D2DrlgDeleteStrc}). */
public class D2DrlgDeleteStrc {
    private int unitType;
    private int unitGuid;
    private D2DrlgDeleteStrc next;

    public int getUnitType() { return unitType; }
    public void setUnitType(int unitType) { this.unitType = unitType; }

    public int getUnitGuid() { return unitGuid; }
    public void setUnitGuid(int unitGuid) { this.unitGuid = unitGuid; }

    public D2DrlgDeleteStrc getNext() { return next; }
    public void setNext(D2DrlgDeleteStrc next) { this.next = next; }
}
