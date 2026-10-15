package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.Transient;
import com.riiablo.engine.server.NativePlayerTargetSlots;
import com.riiablo.engine.server.NativeTargetOrder;

/** Per-unit runtime membership (Targets.cpp dwNodeIndex), never save/network data.
 * Not pooled: state-list callbacks may retain this allocation's node reference. */
@Transient
public class NativeTargetNode extends Component {
  public static final int NONE = 0, SUMMON = 1, MERCENARY = 2, CONVERSION = 3,
      CURSE = 4, WALL = 5;

  private int kind;
  private int ownerId = -1;
  private long order;
  private NativePlayerTargetSlots slots;

  public void usePlayerSlots(NativePlayerTargetSlots slots) { this.slots = slots; }
  public long order() { invalidateReleasedGroup(); return order; }
  public long order(int kind) { return order() != 0 && this.kind == kind ? order : 0; }
  public int ownerId() { return order() == 0 || isNode9() ? -1 : ownerId; }
  public boolean isNode9() { return order() != 0 && (kind == CURSE || kind == WALL); }

  private void invalidateReleasedGroup() {
    // Covers children allocated/released before subscription insertion, too.
    // A missing/recycled head frees the old group; it never queues a rejoin.
    if (order != 0 && kind != CURSE && kind != WALL && slots != null
        && !slots.acceptsChildInsertion(ownerId, order)) detach();
  }

  /** UpdateSummonAI/sub_6FC40280 require an unregistered unit; no implicit move. */
  public boolean insert(int kind, int ownerId) {
    invalidateReleasedGroup();
    if (kind < SUMMON || kind > WALL || order != 0) return false;
    if (kind != CURSE && kind != WALL && slots != null
        && slots.slotOf(ownerId) >= NativePlayerTargetSlots.SLOT_COUNT) return false;
    this.kind = kind;
    this.ownerId = ownerId;
    order = NativeTargetOrder.inserted();
    return true;
  }

  /** TARGETS_Last removes whichever membership is current, not its caller's bucket. */
  public void detach() {
    kind = NONE;
    ownerId = -1;
    order = 0;
  }
}
