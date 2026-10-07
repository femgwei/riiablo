package com.riiablo.map;

import com.badlogic.gdx.math.MathUtils;

/**
 * D2Common's authoritative 360-degree day/night clock.
 *
 * <p>The constants and transition rules mirror {@code D2Environment.cpp}.
 * The server only needs the cycle, tick and eclipse fields on the wire; light
 * intensity and colour are deterministic derivatives of that state.
 */
public final class EnvironmentCycle {
  public static final int PERIOD_DAY = 0;
  public static final int PERIOD_DUSK = 1;
  public static final int PERIOD_NIGHT = 2;
  public static final int PERIOD_DAWN = 3;

  public static final int CYCLE_SUNRISE = 0;
  public static final int CYCLE_MORNING = 1;
  public static final int CYCLE_NOON = 2;
  public static final int CYCLE_AFTERNOON = 3;
  public static final int CYCLE_SUNSET = 4;
  public static final int CYCLE_NIGHT = 5;
  public static final int NUM_CYCLES = 6;

  public static final int FULL_CIRCLE = 360;
  public static final int NORMAL_TIME_RATE = 128;
  public static final int ECLIPSE_TIME_RATE = 4;

  private static final int ROCKY_SUMMIT_LEVEL_ID = 120;

  private static final Cycle[] NORMAL = {
      new Cycle(320, PERIOD_DAWN, 125, 144, 243),
      new Cycle(340, PERIOD_DAWN, 208, 184, 131),
      new Cycle(0, PERIOD_DAY, 255, 255, 255),
      new Cycle(160, PERIOD_DUSK, 255, 255, 255),
      new Cycle(180, PERIOD_DUSK, 194, 152, 193),
      new Cycle(200, PERIOD_NIGHT, 125, 144, 243),
  };

  private static final Cycle[] ACT4 = {
      new Cycle(340, PERIOD_DAWN, 243, 70, 243),
      new Cycle(350, PERIOD_DAWN, 208, 184, 131),
      new Cycle(0, PERIOD_DAY, 255, 20, 20),
      new Cycle(180, PERIOD_DUSK, 255, 255, 30),
      new Cycle(190, PERIOD_DUSK, 20, 152, 193),
      new Cycle(200, PERIOD_NIGHT, 125, 144, 243),
  };

  private static final Cycle[] ECLIPSE = {
      new Cycle(300, PERIOD_DAWN, 0, 30, 243),
      new Cycle(0, PERIOD_DAY, 0, 30, 244),
      new Cycle(60, PERIOD_DUSK, 0, 30, 243),
      new Cycle(120, PERIOD_NIGHT, 0, 30, 243),
      new Cycle(180, PERIOD_NIGHT, 0, 30, 244),
      new Cycle(240, PERIOD_NIGHT, 0, 30, 243),
  };

  private int cycleIndex = CYCLE_NOON;
  private int ticks;
  private boolean eclipse;

  public int cycleIndex() {
    return cycleIndex;
  }

  public int ticks() {
    return ticks;
  }

  public boolean eclipse() {
    return eclipse;
  }

  public int timeRate() {
    return eclipse ? ECLIPSE_TIME_RATE : NORMAL_TIME_RATE;
  }

  public int periodOfDay() {
    return cycles(0)[cycleIndex].period;
  }

  public boolean isDay() {
    return periodOfDay() == PERIOD_DAY;
  }

  /** Sets a representative point in a normal cycle for visual verification. */
  public void setPeriodOfDay(int period) {
    switch (period) {
      case PERIOD_DAY:
        initialize(CYCLE_NOON, 90 * NORMAL_TIME_RATE, false);
        break;
      case PERIOD_DUSK:
        initialize(CYCLE_SUNSET, 190 * NORMAL_TIME_RATE, false);
        break;
      case PERIOD_NIGHT:
        initialize(CYCLE_NIGHT, 270 * NORMAL_TIME_RATE, false);
        break;
      case PERIOD_DAWN:
        initialize(CYCLE_SUNRISE, 330 * NORMAL_TIME_RATE, false);
        break;
      default:
        throw new IllegalArgumentException("Unknown environment period: " + period);
    }
  }

  public static String periodName(int period) {
    switch (period) {
      case PERIOD_DAY: return "day";
      case PERIOD_DUSK: return "dusk";
      case PERIOD_NIGHT: return "night";
      case PERIOD_DAWN: return "dawn";
      default: return "unknown";
    }
  }

  /** Applies the state carried by D2GS packet 0x53 / the riiablo baseline. */
  public void initialize(int cycleIndex, int ticks, boolean eclipse) {
    if (cycleIndex < 0 || cycleIndex >= NUM_CYCLES) cycleIndex = CYCLE_NOON;
    this.eclipse = eclipse;
    this.cycleIndex = cycleIndex;
    int maximum = FULL_CIRCLE * timeRate();
    this.ticks = ticks < 0 || ticks > maximum ? 0 : ticks;
  }

  /** Advances one native 25 Hz game frame. */
  public boolean advance(int act) {
    int previousPeriod = periodOfDay();
    int previousCycle = cycleIndex;
    int rate = timeRate();

    ticks++;
    if (!eclipse) {
      if (act == 3) {
        ticks += 15;
      } else if (NORMAL[cycleIndex].period == PERIOD_NIGHT) {
        ticks++;
        if (act == 2) ticks += 9;
      }
    }

    if (ticks >= FULL_CIRCLE * rate) ticks = 0;

    int next = (cycleIndex + 1) % NUM_CYCLES;
    Cycle nextCycle = cycles(act)[next];
    if (ticks > rate * nextCycle.begin) cycleIndex = next;
    return previousPeriod != periodOfDay() || previousCycle != cycleIndex;
  }

  /** D2Common's outdoor light intensity for the current clock position. */
  public int intensity(int levelId, int act) {
    if (act == 3) {
      switch (levelId) {
        case 103: return 128; // Pandemonium Fortress
        case 104: return 64;  // Outer Steppes
        case 105: return 56;  // Plains of Despair
        case 106: return 48;  // City of the Damned
        default: return 16;
      }
    }
    if (eclipse) return 32;
    if (levelId == ROCKY_SUMMIT_LEVEL_ID) return 200;

    double angle = (double) ticks / timeRate() * Math.PI / 180.0;
    double sin = Math.sin(angle);
    if (ticks >= 180 * timeRate()) sin *= 0.5;
    int maximum = act == 4 ? 170 : 255;
    return MathUtils.clamp((int) (sin * 128.0 + 128.0 + 0.5), 0, maximum);
  }

  public int red(int act) {
    return color(act, 0);
  }

  public int green(int act) {
    return color(act, 1);
  }

  public int blue(int act) {
    return color(act, 2);
  }

  private int color(int act, int component) {
    Cycle[] cycles = cycles(act);
    Cycle current = cycles[cycleIndex];
    Cycle next = cycles[(cycleIndex + 1) % NUM_CYCLES];
    int denominator = timeRate() * (next.begin - current.begin);
    double ratio = denominator == 0 ? 0.0
        : (double) (ticks - timeRate() * current.begin) / denominator;
    int from = component == 0 ? current.red : component == 1 ? current.green : current.blue;
    int to = component == 0 ? next.red : component == 1 ? next.green : next.blue;
    return MathUtils.clamp((int) (from + (to - from) * ratio + 0.5), 0, 255);
  }

  public int red(int levelId, int act) {
    return levelId == ROCKY_SUMMIT_LEVEL_ID ? 245 : red(act);
  }

  public int green(int levelId, int act) {
    return levelId == ROCKY_SUMMIT_LEVEL_ID ? 240 : green(act);
  }

  public int blue(int levelId, int act) {
    return levelId == ROCKY_SUMMIT_LEVEL_ID ? 255 : blue(act);
  }

  private Cycle[] cycles(int act) {
    if (act == 3) return ACT4;
    return eclipse ? ECLIPSE : NORMAL;
  }

  private static final class Cycle {
    final int begin;
    final int period;
    final int red;
    final int green;
    final int blue;

    Cycle(int begin, int period, int red, int green, int blue) {
      this.begin = begin;
      this.period = period;
      this.red = red;
      this.green = green;
      this.blue = blue;
    }
  }
}
