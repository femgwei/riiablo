package com.d2moo.common.path;

import java.util.Objects;

/** 1.10f #10185/#10186 and the three native 18-entry path type tables.
 * This describes ALL table entries, not an implementation of every search entry.
 * Out-of-table indices are rejected instead of reproducing arbitrary native memory reads. */
public final class D2PathTypes {
    private D2PathTypes() {}

    public enum Entry {
        NONE(0), IDA_STAR(0x6fda7970), A_STAR(0x6fda69e0), TOWARD(0x6fdaa9f0),
        TOWARD_ALIAS(0x6fdab0b0), STRAIGHT(0x6fdab130), KNOCKBACK_SERVER(0x6fdab270),
        LEAP(0x6fdab1e0), KNOCKBACK_CLIENT(0x6fdab240), BACKUP_TURN(0x6fdab0c0),
        WALL_FOLLOW(0x6fdac270);
        public final int address;
        Entry(int address) { this.address = address; }
    }

    private static final int[] FLAGS = {
        0x21900, 0x1900, 0, 0, 0x60000, 0, 0, 0x21900, 0x1e600,
        0x1e800, 0x60000, 0x1e604, 0, 0x100, 0x60000, 0x800, 0, 0
    };
    private static final int[] OFFSETS = {0, 0, 0, 0, 0, 2, -2, 0, 0, 0, 0, 0, -4, 0, 0, 0, 0, 0};
    private static final Entry[] ENTRIES = {
        Entry.IDA_STAR, Entry.A_STAR, Entry.TOWARD, Entry.TOWARD_ALIAS, Entry.NONE,
        Entry.TOWARD, Entry.TOWARD, Entry.STRAIGHT, Entry.KNOCKBACK_SERVER, Entry.LEAP,
        Entry.NONE, Entry.KNOCKBACK_CLIENT, Entry.BACKUP_TURN, Entry.TOWARD, Entry.NONE,
        Entry.WALL_FOLLOW, Entry.IDA_STAR, Entry.NONE
    };

    public static int flags(int type) { return FLAGS[index(type)]; }
    public static int directionOffset(int type) { return OFFSETS[index(type)]; }
    public static Entry entry(int type) { return ENTRIES[index(type)]; }

    /** 0x6FDAA0E0: save gates inspect OLD flags; assertions after assignment do not roll back. */
    public static void setType(D2PathResult.State state, int type) {
        Objects.requireNonNull(state);
        int flags = flags(type);
        D2PathContinuation.State path = state.path;
        if (path.ownerType == 0 && type == 2) {
            throw new IllegalStateException("native player PATH_SetType(TOWARD) assertion");
        }
        if ((flags & 0x2000) != 0 && (path.pathFlags & 0x4000) == 0) {
            path.previousPathType = path.pathType;
        }
        if ((flags & 0x8000) != 0 && (path.pathFlags & 0x10000) == 0) {
            state.previousVelocity = state.velocity;
        }
        path.pathType = type;
        path.pathFlags = flags | (path.pathFlags & 0xfff800ff);
        path.directionOffset = directionOffset(type);
        if (path.previousPathType == 11 || path.previousPathType == 8) {
            throw new IllegalStateException("native saved knockback type assertion (writes retained)");
        }
        if (type == 4 && path.nDistMax() >= D2Path.MAX_PATH_LENGTH) {
            throw new IllegalStateException("native missile distance assertion (writes retained)");
        }
    }

    /** 0x6FDAA1E0: restore velocity first; player always resets to 7, others need flag0x2000. */
    public static void resetToPreviousType(D2PathResult.State state) {
        Objects.requireNonNull(state);
        D2PathContinuation.State path = state.path;
        if (path.ownerType < 0 || path.ownerType > 5) throw new IllegalStateException("resolved native owner required");
        if ((path.pathFlags & 0x8000) != 0) state.velocity = state.previousVelocity;
        if (path.ownerType == 0) setType(state, 7);
        else if ((path.pathFlags & 0x2000) != 0) setType(state, path.previousPathType);
    }

    private static int index(int type) {
        if (type < 0 || type >= ENTRIES.length) throw new IllegalArgumentException("native path type 0..17 required");
        return type;
    }
}
