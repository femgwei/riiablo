package com.riiablo.engine.server;

import java.util.Arrays;

import com.artemis.BaseEntitySystem;
import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.annotations.All;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntSet;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.NativeTargetNode;
import com.riiablo.engine.server.component.Sequence;

/** Targets.cpp player-head slots only, not the complete native child/node registry. */
@All(Player.class)
public class NativePlayerTargetSlots extends BaseEntitySystem {
  public static final int SLOT_COUNT = 8;
  public static final int UNREGISTERED = 11;

  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<Sequence> mSequence;

  private final int[] heads = new int[SLOT_COUNT];
  private final long[] headOrders = new long[SLOT_COUNT];
  private final IntIntMap slots = new IntIntMap();
  private final IntSet allocationAttempts = new IntSet();
  private EntitySubscription targetNodes;
  /** Only this world's target insertion domain. Detached compatibility lists
   * keep their legacy counter, but cannot enter this registry implicitly. */
  private long nextOrder;
  // PLAYERPETS quota insertion is a different order domain from TARGETS.
  // Kept on the World owner so another game/client cannot perturb eviction.
  private long petSpawnSequence;

  public long nextPetSpawnOrder() {
    if (petSpawnSequence == Long.MAX_VALUE) throw new IllegalStateException("pet spawn order exhausted");
    return ++petSpawnSequence;
  }
  public long checkpointPetSpawnSequence() { return petSpawnSequence; }
  public void restorePetSpawnSequence(long sequence) {
    if (sequence < 0) throw new IllegalArgumentException("pet spawn sequence");
    petSpawnSequence = sequence;
  }

  public long nextOrder() {
    if (nextOrder == Long.MAX_VALUE) throw new IllegalStateException("target order exhausted");
    return ++nextOrder;
  }

  public static final class Checkpoint {
    public final long sequence;
    private final int[] heads, attempts;
    private final long[] orders;
    public Checkpoint(long sequence, int[] heads, long[] orders, int[] attempts) {
      if (sequence < 0 || heads.length != SLOT_COUNT || orders.length != SLOT_COUNT
          || attempts.length > 1000000) throw new IllegalArgumentException("target slots");
      this.sequence = sequence; this.heads = heads.clone();
      this.orders = orders.clone(); this.attempts = attempts.clone();
      java.util.Set<Integer> owners = new java.util.HashSet<>();
      java.util.Set<Long> events = new java.util.HashSet<>();
      int previous = -1;
      for (int id : attempts) {
        if (id < 0 || id <= previous) throw new IllegalArgumentException("target allocation attempts");
        previous = id;
      }
      for (int i = 0; i < SLOT_COUNT; i++) {
        if (heads[i] < -1 || (heads[i] == -1) != (orders[i] == 0)
            || orders[i] < 0 || orders[i] > sequence
            || heads[i] >= 0 && (!owners.add(heads[i]) || !events.add(orders[i])
                || Arrays.binarySearch(attempts, heads[i]) < 0)) {
          throw new IllegalArgumentException("target head identity/order");
        }
      }
    }
    public int[] heads() { return heads.clone(); }
    public long[] orders() { return orders.clone(); }
    public int[] attempts() { return attempts.clone(); }
  }

  public Checkpoint captureCheckpoint() {
    int[] attempts = new int[allocationAttempts.size]; int index = 0;
    IntSet.IntSetIterator iterator = allocationAttempts.iterator();
    while (iterator.hasNext) attempts[index++] = iterator.next();
    Arrays.sort(attempts);
    Checkpoint result = new Checkpoint(nextOrder, heads, headOrders, attempts);
    validateCheckpoint(result);
    return result;
  }

  public void validateCheckpoint(Checkpoint state) {
    for (int id : state.attempts) if (!mPlayer.has(id)) {
      throw new IllegalStateException("target slot player missing: " + id);
    }
  }

  /** Call after structural subscriptions have settled, never registerPlayer. */
  public void restoreCheckpoint(Checkpoint state) {
    validateCheckpoint(state);
    System.arraycopy(state.heads, 0, heads, 0, SLOT_COUNT);
    System.arraycopy(state.orders, 0, headOrders, 0, SLOT_COUNT);
    slots.clear(); allocationAttempts.clear();
    for (int i = 0; i < SLOT_COUNT; i++) if (heads[i] >= 0) slots.put(heads[i], i);
    for (int id : state.attempts) allocationAttempts.add(id);
    nextOrder = state.sequence;
  }

  public NativePlayerTargetSlots() {
    Arrays.fill(heads, Engine.INVALID_ENTITY);
  }

  @Override
  protected void initialize() {
    targetNodes = world.getAspectSubscriptionManager().get(Aspect.all(NativeTargetNode.class));
    // The all-entity subscription reports even an allocation destroyed before
    // its first Player subscription insertion. Otherwise immediate factory
    // registration would leak that head (and block an eventual recycled ID).
    world.getAspectSubscriptionManager().get(Aspect.all())
        .addSubscriptionListener(new EntitySubscription.SubscriptionListener() {
          @Override public void inserted(IntBag entities) {}
          @Override public void removed(IntBag entities) {
            for (int i = 0; i < entities.size(); i++) release(entities.get(i));
          }
        });
  }

  /** SUnit allocation calls TARGETS_First before returning the assembled player.
   * The subscription callback also supports minimal worlds without that factory. */
  public void registerPlayer(int entityId) {
    if (entityId < 0 || !mPlayer.has(entityId) || slots.containsKey(entityId)) return;
    allocationAttempts.add(entityId);
    int mode = mSequence.has(entityId) ? mSequence.get(entityId).mode1
        : mCofReference.has(entityId) ? mCofReference.get(entityId).mode : Engine.INVALID_MODE;
    // SUnit does not register a newly allocated PLRMODE_DEAD unit. A player
    // already registered before death must keep its slot, including at DD.
    if (mode == Engine.Player.MODE_DD) return;
    for (int slot = 0; slot < SLOT_COUNT; slot++) {
      if (heads[slot] != Engine.INVALID_ENTITY) continue;
      long order = nextOrder();
      heads[slot] = entityId;
      headOrders[slot] = order;
      slots.put(entityId, slot);
      return;
    }
    // Failed allocation is not a waiting queue; no automatic later promotion.
  }

  public int slotOf(int entityId) {
    return slots.get(entityId, UNREGISTERED);
  }

  public int playerAtSlot(int slot) {
    return slot >= 0 && slot < SLOT_COUNT ? heads[slot] : Engine.INVALID_ENTITY;
  }

  /** Runtime registration boundary, in the same event domain as child insertion. */
  public long registrationOrder(int entityId) {
    int slot = slotOf(entityId);
    return slot < SLOT_COUNT ? headOrders[slot] : 0;
  }

  /** FreeUnitNode releases the old group: a recycled owner ID is not a rejoin.
   * This temporal projection requires an explicit child insertion event after
   * the current head allocation, even for children never observed by an AI. */
  public boolean acceptsChildInsertion(int player, long childOrder) {
    long headOrder = registrationOrder(player);
    return headOrder != 0 && childOrder > headOrder;
  }

  @Override
  protected void inserted(int entityId) {
    if (!allocationAttempts.contains(entityId)) registerPlayer(entityId);
  }

  @Override
  protected void removed(int entityId) {
    release(entityId);
  }

  private void release(int entityId) {
    allocationAttempts.remove(entityId);
    releaseSlot(entityId);
  }

  private void releaseSlot(int entityId) {
    int slot = slots.remove(entityId, UNREGISTERED);
    if (slot < SLOT_COUNT) {
      // FreeUnitNode sets every child to 11, even children never scanned by AI.
      IntBag nodes = targetNodes.getEntities();
      ComponentMapper<NativeTargetNode> mapper = world.getMapper(NativeTargetNode.class);
      for (int i = 0; i < nodes.size(); i++) {
        NativeTargetNode node = mapper.get(nodes.get(i));
        if (node != null && node.ownerId() == entityId) node.detach();
      }
      heads[slot] = Engine.INVALID_ENTITY;
      headOrders[slot] = 0;
    }
  }

  @Override
  protected void processSystem() {
    // Registration/destruction, not position, HP, map or room activity, owns slots.
    // Also handle a Player component withdrawn before its first subscription
    // insertion, where there is no normal Player removal notification either.
    IntSet.IntSetIterator attempts = allocationAttempts.iterator();
    while (attempts.hasNext) {
      int entityId = attempts.next();
      if (mPlayer.has(entityId)) continue;
      attempts.remove();
      releaseSlot(entityId);
    }
  }
}
