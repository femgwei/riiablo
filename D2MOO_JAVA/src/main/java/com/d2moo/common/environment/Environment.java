package com.d2moo.common.environment;

import com.d2moo.common.util.D2Pool;

/** Allocation portion of native D2Common's environment module. */
public final class Environment {
    public static final int ENVPERIOD_DAY = 0;
    public static final int ENVPERIOD_DUSK = 1;
    public static final int ENVPERIOD_NIGHT = 2;
    public static final int ENVPERIOD_DAWN = 3;

    public static final int ENVCYCLE_SUNRISE = 0;
    public static final int ENVCYCLE_MORNING = 1;
    public static final int ENVCYCLE_NOON = 2;
    public static final int ENVCYCLE_AFTERNOON = 3;
    public static final int ENVCYCLE_SUNSET = 4;
    public static final int ENVCYCLE_NIGHT = 5;

    private static final int DEFAULT_TIME_RATE = 128;

    private Environment() {}

    /** D2Common {@code ENVIRONMENT_AllocDrlgEnvironment}. */
    public static D2DrlgEnvironment allocDrlgEnvironment(Object memPool) {
        D2DrlgEnvironment environment =
                D2Pool.callocStrcPool(memPool, D2DrlgEnvironment.class);
        if (environment == null) return null;

        environment.setCycleIndex(ENVCYCLE_NOON);
        environment.setTimeRateIndex(0);
        environment.setTimeRate(DEFAULT_TIME_RATE);
        environment.setPeriodOfDay(ENVPERIOD_DAY);
        environment.setTicks(0);
        environment.setIntensity(128);
        environment.setRed(255);
        environment.setGreen(255);
        environment.setBlue(255);
        environment.setInitTick((int) (System.nanoTime() / 1_000_000L));
        environment.setEclipse(false);
        return environment;
    }

    /** D2Common {@code ENVIRONMENT_FreeDrlgEnvironment}. */
    public static void freeDrlgEnvironment(
            Object memPool, D2DrlgEnvironment environment) {
        if (environment != null) D2Pool.freePool(memPool, environment);
    }
}
