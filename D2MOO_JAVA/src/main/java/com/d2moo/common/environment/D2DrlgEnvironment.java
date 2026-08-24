package com.d2moo.common.environment;

/** Java representation of native {@code D2DrlgEnvironmentStrc}. */
public class D2DrlgEnvironment {
    private int cycleIndex;
    private int periodOfDay;
    private int ticks;
    private int intensity;
    private int initTick;
    private int unused;
    private int red;
    private int green;
    private int blue;
    private float cos;
    private float last;
    private float sin;
    private int timeRate;
    private int timeRateIndex;
    private boolean eclipse;
    private int previous;

    public int getCycleIndex() { return cycleIndex; }
    public void setCycleIndex(int cycleIndex) { this.cycleIndex = cycleIndex; }
    public int getPeriodOfDay() { return periodOfDay; }
    public void setPeriodOfDay(int periodOfDay) { this.periodOfDay = periodOfDay; }
    public int getTicks() { return ticks; }
    public void setTicks(int ticks) { this.ticks = ticks; }
    public int getIntensity() { return intensity; }
    public void setIntensity(int intensity) { this.intensity = intensity; }
    public int getInitTick() { return initTick; }
    public void setInitTick(int initTick) { this.initTick = initTick; }
    public int getUnused() { return unused; }
    public void setUnused(int unused) { this.unused = unused; }
    public int getRed() { return red; }
    public void setRed(int red) { this.red = red & 0xFF; }
    public int getGreen() { return green; }
    public void setGreen(int green) { this.green = green & 0xFF; }
    public int getBlue() { return blue; }
    public void setBlue(int blue) { this.blue = blue & 0xFF; }
    public float getCos() { return cos; }
    public void setCos(float cos) { this.cos = cos; }
    public float getLast() { return last; }
    public void setLast(float last) { this.last = last; }
    public float getSin() { return sin; }
    public void setSin(float sin) { this.sin = sin; }
    public int getTimeRate() { return timeRate; }
    public void setTimeRate(int timeRate) { this.timeRate = timeRate; }
    public int getTimeRateIndex() { return timeRateIndex; }
    public void setTimeRateIndex(int timeRateIndex) { this.timeRateIndex = timeRateIndex; }
    public boolean isEclipse() { return eclipse; }
    public void setEclipse(boolean eclipse) { this.eclipse = eclipse; }
    public int getPrevious() { return previous; }
    public void setPrevious(int previous) { this.previous = previous; }
}
