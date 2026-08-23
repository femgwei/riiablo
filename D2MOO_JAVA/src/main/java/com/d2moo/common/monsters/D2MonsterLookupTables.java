package com.d2moo.common.monsters;

/** Exact immutable lookup tables used by D2Common monster helpers 11051-11055. */
public final class D2MonsterLookupTables {
    private static final int[] HIRELING_DESC_STRING_IDS = {
            0, 3371, 3372, 11090, 11088, 11089, 3377, 3376, 3375
    };

    private static final int[] TABLE_11052 = {
            0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4,
            4, 4, 5, 5, 5, 5, 6, 6, 6, 6, 7, 7, 7, 7, 8, 8,
            8, 8, 9, 9, 9, 9, 10, 10, 10, 10, 11, 11, 11, 11, 12, 12,
            12, 12, 13, 13, 13, 13, 14, 14, 14, 14, 15, 15, 15, 15, 0, 0
    };

    private static final int[] TABLE_11053 = {
            0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2,
            2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4,
            4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 6, 6, 6, 6,
            6, 6, 6, 6, 7, 7, 7, 7, 7, 7, 7, 7, 0, 0, 0, 0
    };

    private static final int[] TABLE_11054 = {4, 12, 20, 28, 36, 44, 52, 60};

    private static final int[] OFFSET_X_11055 = {
            0, -1, -1, -1, 0, 1, 1, 1,
            0, -1, -2, -2, -2, -2, -2, -1,
            0, 1, 2, 2, 2, 2, 2, 1,
            0, -3, -3, -3, 0, 3, 3, 3
    };

    private static final int[] OFFSET_Y_11055 = {
            -1, -1, 0, 1, 1, 1, 0, -1,
            -2, -2, -2, -1, 0, 1, 2, 2,
            2, 2, 2, 1, 0, -1, -2, -2,
            -3, -3, 0, 3, 3, 3, 0, -3
    };

    private D2MonsterLookupTables() {}

    public static int hirelingDescriptionStringId(int id) {
        return id >= 0 && id < HIRELING_DESC_STRING_IDS.length
                ? HIRELING_DESC_STRING_IDS[id]
                : 0;
    }

    public static int value11052(int index) {
        return TABLE_11052[index];
    }

    public static int value11053(int index) {
        return TABLE_11053[index];
    }

    public static int value11054(int index) {
        return TABLE_11054[index];
    }

    public static int offsetX11055(int index) {
        return OFFSET_X_11055[index];
    }

    public static int offsetY11055(int index) {
        return OFFSET_Y_11055[index];
    }
}
