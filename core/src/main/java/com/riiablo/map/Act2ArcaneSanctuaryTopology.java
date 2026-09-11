package com.riiablo.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Deterministic, render-independent model of D2MOO's Arcane Sanctuary maze.
 *
 * <p>The native generator creates one center room and four branches of fifteen
 * rooms.  At branch indices 8 and 12 the next room intentionally continues
 * from the previous parent; those two rooms are therefore siblings rather than
 * a linear chain.  Keeping this graph separate from DS1 materialization avoids
 * treating the Summoner room as the complete Sanctuary map.</p>
 */
public final class Act2ArcaneSanctuaryTopology {
  public static final int BRANCH_COUNT = 4;
  public static final int ROOMS_PER_BRANCH = 15;
  public static final int CENTER_ROOM_ID = 0;

  public static final class Room {
    private final int id;
    private final int branch;
    private final int branchIndex;
    private final int direction;
    private final int branchPresetDef;

    Room(int id, int branch, int branchIndex, int direction, int branchPresetDef) {
      this.id = id;
      this.branch = branch;
      this.branchIndex = branchIndex;
      this.direction = direction;
      this.branchPresetDef = branchPresetDef;
    }

    public int id() { return id; }
    public int branch() { return branch; }
    public int branchIndex() { return branchIndex; }
    public int direction() { return direction; }
    public int branchPresetDef() { return branchPresetDef; }
  }

  public static final class Edge {
    private final int from;
    private final int to;

    Edge(int from, int to) {
      this.from = from;
      this.to = to;
    }

    public int from() { return from; }
    public int to() { return to; }
  }

  private final int levelSeed;
  private final Act2ArcaneSanctuaryLayout.Direction summonerDirection;
  private final List<Room> rooms;
  private final List<Edge> edges;

  private Act2ArcaneSanctuaryTopology(int levelSeed,
      Act2ArcaneSanctuaryLayout.Direction summonerDirection,
      List<Room> rooms, List<Edge> edges) {
    this.levelSeed = levelSeed;
    this.summonerDirection = summonerDirection;
    this.rooms = Collections.unmodifiableList(rooms);
    this.edges = Collections.unmodifiableList(edges);
  }

  public static Act2ArcaneSanctuaryTopology generate(int levelSeed) {
    Act2ArcaneSanctuaryLayout.Direction selected =
        Act2ArcaneSanctuaryLayout.fromLevelSeed(levelSeed);
    List<Room> rooms = new ArrayList<>(1 + BRANCH_COUNT * ROOMS_PER_BRANCH);
    List<Edge> edges = new ArrayList<>(BRANCH_COUNT * ROOMS_PER_BRANCH);
    rooms.add(new Room(CENTER_ROOM_ID, -1, -1, -1, 0));

    for (int branch = 0; branch < BRANCH_COUNT; branch++) {
      Act2ArcaneSanctuaryLayout.Direction branchDirection =
          Act2ArcaneSanctuaryLayout.Direction.values()[branch];
      int parent = CENTER_ROOM_ID;
      for (int index = 0; index < ROOMS_PER_BRANCH; index++) {
        int roomId = 1 + branch * ROOMS_PER_BRANCH + index;
        int direction = directionFromRoomIndex(branch, index);
        rooms.add(new Room(roomId, branch, index, direction,
            branchDirection.branchPresetDef()));
        edges.add(new Edge(parent, roomId));
        // Matches DRLGMAZE_PlaceArcaneSanctuary's intentional branch forks.
        if (index != 8 && index != 12) parent = roomId;
      }
    }
    return new Act2ArcaneSanctuaryTopology(levelSeed, selected, rooms, edges);
  }

  /** D2MOO DRLGMAZE_ArcaneSanctuaryDirectionFromRoomIdx. */
  public static int directionFromRoomIndex(int branch, int index) {
    switch (index) {
      case 2:
      case 12:
        return (branch + 3) & 3;
      case 7:
      case 9:
        return (branch + 1) & 3;
      case 10:
      case 11:
      case 13:
      case 14:
        return (branch + 2) & 3;
      default:
        return branch & 3;
    }
  }

  public int levelSeed() { return levelSeed; }
  public Act2ArcaneSanctuaryLayout.Direction summonerDirection() {
    return summonerDirection;
  }
  public int summonerBranch() { return summonerDirection.ordinal(); }
  public List<Room> rooms() { return rooms; }
  public List<Edge> edges() { return edges; }
}
