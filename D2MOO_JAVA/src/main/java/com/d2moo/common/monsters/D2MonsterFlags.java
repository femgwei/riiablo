package com.d2moo.common.monsters;

/** MonStats flag bits from D2Common {@code MonsterTbls.h}. */
public final class D2MonsterFlags {
    private D2MonsterFlags() {}

    public static final int IS_SPAWN = 1 << 0;
    public static final int IS_MELEE = 1 << 1;
    public static final int NO_RATIO = 1 << 2;
    public static final int OPEN_DOORS = 1 << 3;
    public static final int SET_BOSS = 1 << 4;
    public static final int BOSS_XFER = 1 << 5;
    public static final int BOSS = 1 << 6;
    public static final int PRIME_EVIL = 1 << 7;
    public static final int NPC = 1 << 8;
    public static final int INTERACT = 1 << 9;
    public static final int IN_TOWN = 1 << 10;
    public static final int LOW_UNDEAD = 1 << 11;
    public static final int HIGH_UNDEAD = 1 << 12;
    public static final int DEMON = 1 << 13;
    public static final int FLYING = 1 << 14;
    public static final int KILLABLE = 1 << 15;
    public static final int SWITCH_AI = 1 << 16;
    public static final int NO_MULTISHOT = 1 << 17;
    public static final int NEVER_COUNT = 1 << 18;
    public static final int PET_IGNORE = 1 << 19;
    public static final int DEATH_DAMAGE = 1 << 20;
    public static final int GENERIC_SPAWN = 1 << 21;
    public static final int ZOO = 1 << 22;
    public static final int PLACE_SPAWN = 1 << 23;
    public static final int INVENTORY = 1 << 24;
    public static final int ENABLED = 1 << 25;
    public static final int NO_SHIELD_BLOCK = 1 << 26;
    public static final int NO_AURA = 1 << 27;
    public static final int RANGED_TYPE = 1 << 28;
}
