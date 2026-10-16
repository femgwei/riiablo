package com.d2moo.common.path;

import com.d2moo.common.drlg.D2ActiveRoom;
import com.d2moo.common.drlg.D2DrlgAct;
import com.d2moo.common.drlg.D2DrlgGridStrc;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.d2moo.common.dungeon.Dungeon;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;

/** Request-local projection of one Act's allocated rooms and #10035 populated lists.
 * Capture/use on the serialized game thread; this is not a synchronization primitive.
 * Rebuild after topology/lifetime or grid-storage/layout changes. Collision bits and unit RNG remain live.
 * Does not allocate/activate rooms or install a production movement backend. */
public final class D2PathActiveRooms {
    private final D2DrlgAct act;
    private final ArrayList<Entry> ordered = new ArrayList<>();
    private final IdentityHashMap<D2ActiveRoom, Entry> entries = new IdentityHashMap<>();

    public D2PathActiveRooms(D2DrlgAct act) {
        this.act = Objects.requireNonNull(act);
        for (D2ActiveRoom room = act.getRoom(); room != null; room = room.getRoomNext()) {
            if (entries.containsKey(room)) throw new IllegalArgumentException("cyclic Act room chain");
            if (room.getAct() != act || room.getPDrlgRoom() == null
                    || room.getPDrlgRoom().getRoom() != room) {
                throw new IllegalArgumentException("Act room ownership/backlink mismatch");
            }
            Entry entry = new Entry(room);
            entries.put(room, entry);
            ordered.add(entry);
        }
        for (Entry entry : ordered) {
            ArrayList<D2PathRequest.Room> near = new ArrayList<>();
            for (D2ActiveRoom nativeNear : entry.near) {
                if (nativeNear != null && !entries.containsKey(nativeNear)) {
                    throw new IllegalArgumentException("active near room is outside the captured Act");
                }
                near.add(nativeNear == null ? null : entries.get(nativeNear).projected);
            }
            entry.nearby = Collections.unmodifiableList(near);
            entry.projected.nearby = entry.nearby;
        }
    }

    /** Identity lookup, never a coordinate/roomId lookup; null preserves native null rooms. */
    public D2PathRequest.Room room(D2ActiveRoom room) {
        if (room == null) return null;
        Entry entry = entries.get(room);
        if (entry == null) throw new IllegalArgumentException("room was not captured");
        return entry.projected;
    }

    /** Detects current-state invalidation, not every historical mutation or concurrent access.
     * Collision bit changes and unused array capacity do not invalidate the topology. */
    public void requireCurrent() {
        D2ActiveRoom current = act.getRoom();
        for (Entry entry : ordered) {
            if (current != entry.source || !entry.current(act)) {
                throw new IllegalStateException("active-room snapshot is stale; rebuild before requesting a path");
            }
            current = current.getRoomNext();
        }
        if (current != null) throw new IllegalStateException("Act room chain changed");
    }

    private static final class Entry {
        final D2ActiveRoom source;
        final D2DrlgRoom drlg;
        final D2DrlgGridStrc grid;
        final int[] cells, rowOffsets, rowValues;
        final int gridWidth, gridHeight;
        final int levelId;
        final D2ActiveRoom[] near;
        final D2PathRequest.Room projected;
        List<D2PathRequest.Room> nearby;

        Entry(D2ActiveRoom source) {
            this.source = source;
            drlg = source.getPDrlgRoom();
            grid = source.getPCollisionGrid();
            cells = grid == null ? null : grid.getPCellsFlags();
            rowOffsets = grid == null ? null : grid.getPCellsRowOffsets();
            gridWidth = grid == null ? 0 : grid.getNWidth();
            gridHeight = grid == null ? 0 : grid.getNHeight();
            rowValues = rowOffsets == null ? null : Arrays.copyOf(rowOffsets,
                    Math.max(0, Math.min(gridHeight, rowOffsets.length)));
            levelId = Dungeon.getLevelIdFromRoom(source);
            near = Dungeon.getAdjacentRoomsListFromRoom(source);
            projected = new D2PathRequest.Room(new D2PathResult.Room(
                    source.getCoords().getNSubtileX(), source.getCoords().getNSubtileY(),
                    source.getCoords().getNSubtileWidth(), source.getCoords().getNSubtileHeight()),
                    Dungeon.isRoomInTown(source));
        }

        boolean current(D2DrlgAct act) {
            D2PathResult.Room b = projected.bounds;
            if (source.getAct() != act || source.getPDrlgRoom() != drlg || drlg.getRoom() != source
                    || source.getPCollisionGrid() != grid || !gridCurrent()
                    || Dungeon.getLevelIdFromRoom(source) != levelId
                    || source.getCoords().getNSubtileX() != b.x || source.getCoords().getNSubtileY() != b.y
                    || source.getCoords().getNSubtileWidth() != b.width
                    || source.getCoords().getNSubtileHeight() != b.height
                    || source.getNNumRooms() != near.length || projected.nearby != nearby
                    || projected.town != Dungeon.isRoomInTown(source)) {
                return false;
            }
            D2ActiveRoom[] live = source.getPpRoomList();
            for (int i = 0; i < near.length; i++) {
                if (live[i] != near[i]) return false;
            }
            return true;
        }

        /** Storage/layout stability only; cells bits stay live and malformed initial
         * layouts are not validated here. Unused row-array capacity is irrelevant. */
        boolean gridCurrent() {
            if (grid == null) return true;
            if (grid.getPCellsFlags() != cells || grid.getPCellsRowOffsets() != rowOffsets
                    || grid.getNWidth() != gridWidth || grid.getNHeight() != gridHeight) return false;
            if (rowValues != null) {
                for (int i = 0; i < rowValues.length; i++) {
                    if (rowOffsets[i] != rowValues[i]) return false;
                }
            }
            return true;
        }
    }
}
