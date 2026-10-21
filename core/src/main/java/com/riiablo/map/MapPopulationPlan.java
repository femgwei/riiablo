package com.riiablo.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Generation output. Only the assembly adapter allocates entities from these placements. */
public final class MapPopulationPlan {
  public static final class Monster {
    public final Map.Zone zone;
    public final int monsterId;
    public final float x;
    public final float y;

    private Monster(Map.Zone zone, int monsterId, float x, float y) {
      this.zone = zone;
      this.monsterId = monsterId;
      this.x = x;
      this.y = y;
    }
  }

  private final List<Monster> monsters = new ArrayList<>();
  private final List<Monster> view = Collections.unmodifiableList(monsters);

  void addMonster(Map.Zone zone, int monsterId, float x, float y) {
    monsters.add(new Monster(zone, monsterId, x, y));
  }

  public List<Monster> monsters() { return view; }
}
