package com.d2moo.common.collision;

/** Pure constants and geometry from D2Common's {@code D2Collision.h}. */
public final class D2Collision {
    private D2Collision() {}

    public static final int UNIT_SIZE_NONE = 0;
    public static final int UNIT_SIZE_POINT = 1;
    public static final int UNIT_SIZE_SMALL = 2;
    public static final int UNIT_SIZE_BIG = 3;

    public static final int PATTERN_NONE = 0;
    public static final int PATTERN_SMALL_UNIT_PRESENCE = 1;
    public static final int PATTERN_BIG_UNIT_PRESENCE = 2;
    public static final int PATTERN_SMALL_PET_PRESENCE = 3;
    public static final int PATTERN_BIG_PET_PRESENCE = 4;
    public static final int PATTERN_SMALL_NO_PRESENCE = 5;

    public static final int COLLIDE_NONE = 0x0000;
    public static final int COLLIDE_WALL = 0x0001;
    public static final int COLLIDE_VISIBLE = 0x0002;
    public static final int COLLIDE_MISSILE_BARRIER = 0x0004;
    public static final int COLLIDE_NOPLAYER = 0x0008;
    public static final int COLLIDE_PRESET = 0x0010;
    public static final int COLLIDE_BLANK = 0x0020;
    public static final int COLLIDE_MISSILE = 0x0040;
    public static final int COLLIDE_PLAYER = 0x0080;
    public static final int COLLIDE_WATER = 0x00C0;
    public static final int COLLIDE_MONSTER = 0x0100;
    public static final int COLLIDE_ITEM = 0x0200;
    public static final int COLLIDE_OBJECT = 0x0400;
    public static final int COLLIDE_DOOR = 0x0800;
    public static final int COLLIDE_NO_PATH = 0x1000;
    public static final int COLLIDE_PET = 0x2000;
    public static final int COLLIDE_4000 = 0x4000;
    public static final int COLLIDE_CORPSE = 0x8000;
    public static final int COLLIDE_ALL_MASK = 0xFFFF;

    public static final int COLLIDE_MASK_INVALID =
            COLLIDE_BLANK | COLLIDE_MISSILE_BARRIER | COLLIDE_VISIBLE | COLLIDE_WALL;
    public static final int COLLIDE_MASK_PLAYER_PATH =
            COLLIDE_WALL | COLLIDE_NOPLAYER | COLLIDE_OBJECT | COLLIDE_DOOR | COLLIDE_NO_PATH;
    public static final int COLLIDE_MASK_PLAYER_FLYING = COLLIDE_DOOR | COLLIDE_MISSILE_BARRIER;
    public static final int COLLIDE_MASK_PLAYER_WW = COLLIDE_WALL | COLLIDE_OBJECT | COLLIDE_DOOR;
    public static final int COLLIDE_MASK_RADIAL_BARRIER =
            COLLIDE_DOOR | COLLIDE_MISSILE_BARRIER | COLLIDE_WALL;
    public static final int COLLIDE_MASK_FLYING_UNIT =
            COLLIDE_MISSILE_BARRIER | COLLIDE_DOOR | COLLIDE_NO_PATH;
    public static final int COLLIDE_MASK_MONSTER_THAT_CAN_OPEN_DOORS =
            COLLIDE_WALL | COLLIDE_OBJECT | COLLIDE_NO_PATH | COLLIDE_PET;
    public static final int COLLIDE_MASK_MONSTER_MISSILE = COLLIDE_MONSTER | COLLIDE_WALL;
    public static final int COLLIDE_MASK_MONSTER_PATH =
            COLLIDE_MASK_MONSTER_THAT_CAN_OPEN_DOORS | COLLIDE_DOOR;
    public static final int COLLIDE_MASK_DOOR_BLOCK_VIS =
            COLLIDE_DOOR | COLLIDE_MISSILE_BARRIER | COLLIDE_VISIBLE;
    public static final int COLLIDE_MASK_BLOCKS_DOOR =
            COLLIDE_PLAYER | COLLIDE_MONSTER | COLLIDE_CORPSE;
    public static final int COLLIDE_MASK_SPAWN =
            COLLIDE_WALL | COLLIDE_ITEM | COLLIDE_OBJECT | COLLIDE_DOOR | COLLIDE_NO_PATH | COLLIDE_PET;
    public static final int COLLIDE_MASK_PLACEMENT =
            COLLIDE_MASK_SPAWN | COLLIDE_PRESET | COLLIDE_MONSTER;

    /** Matches {@code COLLISION_CreateBoundingBox}, including its even-size bias. */
    public static BoundingBox createBoundingBox(int centerX, int centerY, int sizeX, int sizeY) {
        if (sizeX <= 0 || sizeY <= 0) {
            throw new IllegalArgumentException("Collision box dimensions must be positive");
        }
        int left = centerX - sizeX / 2;
        int bottom = centerY - sizeY / 2;
        return new BoundingBox(left, bottom, left + sizeX - 1, bottom + sizeY - 1);
    }

    /** Applies the native unsigned 16-bit mask check. */
    public static int checkMask(int collisionFlags, int requestedMask) {
        return (collisionFlags & requestedMask) & COLLIDE_ALL_MASK;
    }

    public static int setMask(int collisionFlags, int mask) {
        return (collisionFlags | mask) & COLLIDE_ALL_MASK;
    }

    public static int resetMask(int collisionFlags, int mask) {
        return (collisionFlags & ~mask) & COLLIDE_ALL_MASK;
    }

    public static final class BoundingBox {
        public final int left;
        public final int bottom;
        public final int right;
        public final int top;

        BoundingBox(int left, int bottom, int right, int top) {
            this.left = left;
            this.bottom = bottom;
            this.right = right;
            this.top = top;
        }

        public int width() {
            return right - left + 1;
        }

        public int height() {
            return top - bottom + 1;
        }

        public boolean contains(int x, int y) {
            return x >= left && x <= right && y >= bottom && y <= top;
        }
    }
}
