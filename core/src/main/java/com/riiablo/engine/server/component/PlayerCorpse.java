package com.riiablo.engine.server.component;

import com.artemis.PooledComponent;
import com.artemis.annotations.PooledWeaver;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.ObjectMap;

import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;

/**
 * Component that stores player corpse information.
 * When a player dies, their corpse is created at the death location with all equipped items.
 * The player can return to the corpse location to retrieve their items.
 */
@PooledWeaver
public class PlayerCorpse extends PooledComponent {
  /**
   * The player entity ID that owns this corpse
   */
  public int playerId;
  
  /**
   * Death location (world coordinates)
   */
  public final Vector2 deathLocation = new Vector2();
  
  /** Player equipment owned by this corpse, keyed by its original body location. */
  public final ObjectMap<BodyLoc, Item> equippedItems = new ObjectMap<>();

  /** Mouse item preserved by the corpse, matching D2Inventory corpse creation. */
  public Item cursorItem;

  /** Explicit PlayerList corpse-loot grants. Hostility alone never grants access. */
  public final IntSet authorizedLooters = new IntSet();
  
  /**
   * Whether the corpse has been retrieved (items returned to player)
   */
  public boolean retrieved = false;
  
  /**
   * Corpse duration in seconds (how long the corpse remains before items are lost)
   * In D2, corpses persist indefinitely until retrieved or the game is closed
   */
  public static final float CORPSE_DURATION = Float.MAX_VALUE; // Corpses persist until retrieved
  
  /**
   * Time remaining before corpse expires (in seconds)
   */
  public float timeRemaining = CORPSE_DURATION;

  public boolean canRetrieve(int candidatePlayerId) {
    return candidatePlayerId == playerId || authorizedLooters.contains(candidatePlayerId);
  }

  public void grantLootPermission(int candidatePlayerId) {
    if (candidatePlayerId >= 0 && candidatePlayerId != playerId) {
      authorizedLooters.add(candidatePlayerId);
    }
  }

  public void revokeLootPermission(int candidatePlayerId) {
    authorizedLooters.remove(candidatePlayerId);
  }

  @Override
  protected void reset() {
    playerId = -1;
    deathLocation.setZero();
    equippedItems.clear();
    cursorItem = null;
    authorizedLooters.clear();
    retrieved = false;
    timeRemaining = CORPSE_DURATION;
  }
}
