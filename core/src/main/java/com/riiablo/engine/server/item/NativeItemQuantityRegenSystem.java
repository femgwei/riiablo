package com.riiablo.engine.server.item;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.server.component.Player;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;

/** D2Game EVENTTYPE_STATREGEN handling for replenish-quantity items. */
@All(Player.class)
public final class NativeItemQuantityRegenSystem extends IteratingSystem {
  protected ComponentMapper<Player> mPlayer;

  private final IdentityHashMap<Item, Long> nextFrame = new IdentityHashMap<>();
  private final IdentityHashMap<Item, Boolean> seen = new IdentityHashMap<>();
  private long frame;

  @Override
  protected void begin() {
    frame++;
    seen.clear();
  }

  @Override
  protected void process(int entityId) {
    CharData data = mPlayer.get(entityId).data;
    if (data == null || data.getItems() == null) return;
    for (Item item : data.getItems().getItems()) {
      if (item == null || item.attrs == null) continue;
      seen.put(item, Boolean.TRUE);
      int rate = stat(item, Stat.item_replenish_quantity);
      int quantity = statBase(item, Stat.quantity);
      int maximum = item.base != null ? Math.max(0, item.base.maxstack) : 0;
      if (rate <= 0 || maximum <= 0 || quantity >= maximum) {
        nextFrame.remove(item);
        continue;
      }

      Long due = nextFrame.get(item);
      if (due == null) {
        nextFrame.put(item, frame + intervalFrames(rate));
      } else if (frame >= due) {
        replenishOne(item, maximum);
        if (statBase(item, Stat.quantity) < maximum) {
          nextFrame.put(item, frame + intervalFrames(rate));
        } else {
          nextFrame.remove(item);
        }
      }
    }
  }

  @Override
  protected void end() {
    Iterator<Map.Entry<Item, Long>> iterator = nextFrame.entrySet().iterator();
    while (iterator.hasNext()) {
      if (!seen.containsKey(iterator.next().getKey())) iterator.remove();
    }
  }

  /** Native sub_6FC4A350: max(125, 2500 / replenishRate + 1). */
  public static int intervalFrames(int replenishRate) {
    if (replenishRate <= 0) return Integer.MAX_VALUE;
    return Math.max(125, 2500 / replenishRate + 1);
  }

  static boolean replenishOne(Item item, int maximum) {
    if (item == null || item.attrs == null || maximum <= 0) return false;
    int current = statBase(item, Stat.quantity);
    if (current >= maximum) return false;
    int next = Math.min(maximum, current + 1);
    item.attrs.base().put(Stat.quantity, next);
    item.attrs.aggregate().put(Stat.quantity, next);
    return true;
  }

  private static int stat(Item item, short stat) {
    StatRef value = item.attrs.aggregate().get(stat, StatRef.obtain());
    return value != null ? value.asInt() : 0;
  }

  private static int statBase(Item item, short stat) {
    StatRef value = item.attrs.base().get(stat, StatRef.obtain());
    return value != null ? value.asInt() : 0;
  }
}
