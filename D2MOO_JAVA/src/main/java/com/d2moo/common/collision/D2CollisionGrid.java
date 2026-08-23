package com.d2moo.common.collision;

import java.util.Arrays;

/** Standalone unsigned 16-bit collision grid using D2Common footprint rules. */
public final class D2CollisionGrid {
    private static final int[][] CROSS = {{-1, 0}, {0, 0}, {1, 0}, {0, -1}, {0, 1}};

    private final int originX;
    private final int originY;
    private final int width;
    private final int height;
    private final int[] flags;

    public D2CollisionGrid(int originX, int originY, int width, int height) {
        long area = (long) width * height;
        if (width <= 0 || height <= 0 || area > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid collision grid dimensions");
        }
        this.originX = originX;
        this.originY = originY;
        this.width = width;
        this.height = height;
        this.flags = new int[(int) area];
    }

    public int originX() {
        return originX;
    }

    public int originY() {
        return originY;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean contains(int x, int y) {
        return x >= originX && y >= originY
                && (long) x < (long) originX + width
                && (long) y < (long) originY + height;
    }

    /** Native invalid-coordinate result is COLLIDE_MASK_INVALID, not the requested mask. */
    public int checkMask(int x, int y, int mask) {
        int index = indexOf(x, y);
        return index < 0
                ? D2Collision.COLLIDE_MASK_INVALID
                : D2Collision.checkMask(flags[index], mask);
    }

    public int flagsAt(int x, int y) {
        int index = indexOf(x, y);
        return index < 0 ? D2Collision.COLLIDE_MASK_INVALID : flags[index];
    }

    public void setMask(int x, int y, int mask) {
        int index = indexOf(x, y);
        if (index >= 0) flags[index] = D2Collision.setMask(flags[index], mask);
    }

    public void resetMask(int x, int y, int mask) {
        int index = indexOf(x, y);
        if (index >= 0) flags[index] = D2Collision.resetMask(flags[index], mask);
    }

    public void clear() {
        Arrays.fill(flags, 0);
    }

    public int checkMaskWithSizeXY(int x, int y, int sizeX, int sizeY, int mask) {
        if (sizeX <= 1 && sizeY <= 1) return checkMask(x, y, mask);
        if (sizeX <= 0 || sizeY <= 0) return D2Collision.COLLIDE_MASK_INVALID;
        return checkBox(D2Collision.createBoundingBox(x, y, sizeX, sizeY), mask);
    }

    public void setMaskWithSizeXY(int x, int y, int sizeX, int sizeY, int mask) {
        if (sizeX == 1 && sizeY == 1) {
            setMask(x, y, mask);
        } else if (sizeX > 0 && sizeY > 0) {
            alterBox(D2Collision.createBoundingBox(x, y, sizeX, sizeY), mask, true);
        }
    }

    public void resetMaskWithSizeXY(int x, int y, int sizeX, int sizeY, int mask) {
        if (sizeX == 1 && sizeY == 1) {
            resetMask(x, y, mask);
        } else if (sizeX > 0 && sizeY > 0) {
            alterBox(D2Collision.createBoundingBox(x, y, sizeX, sizeY), mask, false);
        }
    }

    public int checkMaskWithSize(int x, int y, int unitSize, int mask) {
        switch (unitSize) {
            case D2Collision.UNIT_SIZE_NONE:
            case D2Collision.UNIT_SIZE_POINT:
                return checkMask(x, y, mask);
            case D2Collision.UNIT_SIZE_SMALL:
                return checkCross(x, y, mask);
            case D2Collision.UNIT_SIZE_BIG:
                return checkBox(D2Collision.createBoundingBox(x, y, 3, 3), mask);
            default:
                return D2Collision.COLLIDE_ALL_MASK;
        }
    }

    public void setMaskWithSize(int x, int y, int unitSize, int mask) {
        alterMaskWithSize(x, y, unitSize, mask, true);
    }

    public void resetMaskWithSize(int x, int y, int unitSize, int mask) {
        alterMaskWithSize(x, y, unitSize, mask, false);
    }

    public int checkMaskWithPattern(int x, int y, int pattern, int mask) {
        switch (pattern) {
            case D2Collision.PATTERN_NONE:
                return checkMask(x, y, mask);
            case D2Collision.PATTERN_SMALL_UNIT_PRESENCE:
            case D2Collision.PATTERN_SMALL_PET_PRESENCE:
            case D2Collision.PATTERN_SMALL_NO_PRESENCE:
                return checkCross(x, y, mask);
            case D2Collision.PATTERN_BIG_UNIT_PRESENCE:
            case D2Collision.PATTERN_BIG_PET_PRESENCE:
                return checkBox(D2Collision.createBoundingBox(x, y, 3, 3), mask);
            default:
                return D2Collision.COLLIDE_ALL_MASK;
        }
    }

    public void setMaskWithPattern(int x, int y, int pattern, int mask) {
        alterMaskWithPattern(x, y, pattern, mask, true);
    }

    public void resetMaskWithPattern(int x, int y, int pattern, int mask) {
        alterMaskWithPattern(x, y, pattern, mask, false);
    }

    /** Matches COLLISION_TryMoveUnitCollisionMask. */
    public int tryMoveUnit(
            int oldX, int oldY, int newX, int newY,
            int unitSize, int footprintMask, int moveConditionMask) {
        resetMaskWithSize(oldX, oldY, unitSize, footprintMask);
        int collided = checkMaskWithSize(newX, newY, unitSize, moveConditionMask);
        if ((collided & (D2Collision.COLLIDE_WALL | D2Collision.COLLIDE_MISSILE_BARRIER)) != 0) {
            setMaskWithSize(oldX, oldY, unitSize, footprintMask);
        } else {
            setMaskWithSize(newX, newY, unitSize, footprintMask);
        }
        return collided;
    }

    /** Matches COLLISION_TryTeleportUnitCollisionMask. */
    public int tryTeleportUnit(
            int oldX, int oldY, int newX, int newY,
            int pattern, int footprintMask, int moveConditionMask) {
        resetMaskWithPattern(oldX, oldY, pattern, footprintMask);
        int collided = checkMaskWithPattern(newX, newY, pattern, moveConditionMask);
        if (collided != 0) {
            setMaskWithPattern(oldX, oldY, pattern, footprintMask);
        } else {
            setMaskWithPattern(newX, newY, pattern, footprintMask);
        }
        return collided;
    }

    /** Matches the force-teleport form: report collision but always move the footprint. */
    public int forceTeleportUnit(
            int oldX, int oldY, int newX, int newY,
            int unitSize, int footprintMask, int moveConditionMask) {
        resetMaskWithSize(oldX, oldY, unitSize, footprintMask);
        int collided = checkMaskWithSize(newX, newY, unitSize, moveConditionMask);
        setMaskWithSize(newX, newY, unitSize, footprintMask);
        return collided;
    }

    private int checkCross(int centerX, int centerY, int mask) {
        int result = 0;
        for (int[] offset : CROSS) {
            result |= checkMask(centerX + offset[0], centerY + offset[1], mask);
        }
        return result;
    }

    private int checkBox(D2Collision.BoundingBox box, int mask) {
        int result = 0;
        for (int y = box.bottom; y <= box.top; y++) {
            for (int x = box.left; x <= box.right; x++) {
                result |= checkMask(x, y, mask);
            }
        }
        return result & D2Collision.COLLIDE_ALL_MASK;
    }

    private void alterMaskWithSize(int x, int y, int unitSize, int mask, boolean set) {
        switch (unitSize) {
            case D2Collision.UNIT_SIZE_POINT:
                alterPoint(x, y, mask, set);
                break;
            case D2Collision.UNIT_SIZE_SMALL:
                alterCross(x, y, mask, set);
                break;
            case D2Collision.UNIT_SIZE_BIG:
                alterBox(D2Collision.createBoundingBox(x, y, 3, 3), mask, set);
                break;
            default:
                break;
        }
    }

    private void alterMaskWithPattern(int x, int y, int pattern, int mask, boolean set) {
        switch (pattern) {
            case D2Collision.PATTERN_SMALL_UNIT_PRESENCE:
                alterCross(x, y, mask, set);
                if (mask != 0) alterPoint(x, y, D2Collision.COLLIDE_NO_PATH, set);
                break;
            case D2Collision.PATTERN_BIG_UNIT_PRESENCE:
                alterBox(D2Collision.createBoundingBox(x, y, 3, 3), mask, set);
                if (mask != 0) alterCross(x, y, D2Collision.COLLIDE_NO_PATH, set);
                break;
            case D2Collision.PATTERN_SMALL_PET_PRESENCE:
                alterCross(x, y, mask, set);
                if (mask != 0) alterPoint(x, y, D2Collision.COLLIDE_PET, set);
                break;
            case D2Collision.PATTERN_BIG_PET_PRESENCE:
                alterBox(D2Collision.createBoundingBox(x, y, 3, 3), mask, set);
                if (mask != 0) alterCross(x, y, D2Collision.COLLIDE_PET, set);
                break;
            case D2Collision.PATTERN_SMALL_NO_PRESENCE:
                alterCross(x, y, mask, set);
                break;
            default:
                break;
        }
    }

    private void alterCross(int centerX, int centerY, int mask, boolean set) {
        for (int[] offset : CROSS) {
            alterPoint(centerX + offset[0], centerY + offset[1], mask, set);
        }
    }

    private void alterBox(D2Collision.BoundingBox box, int mask, boolean set) {
        for (int y = box.bottom; y <= box.top; y++) {
            for (int x = box.left; x <= box.right; x++) {
                alterPoint(x, y, mask, set);
            }
        }
    }

    private void alterPoint(int x, int y, int mask, boolean set) {
        if (set) setMask(x, y, mask);
        else resetMask(x, y, mask);
    }

    private int indexOf(int x, int y) {
        if (!contains(x, y)) return -1;
        return (x - originX) + (y - originY) * width;
    }
}
