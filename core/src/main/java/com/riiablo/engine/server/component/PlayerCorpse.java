package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntIntMap;

/**
 * Component that stores player corpse information.
 * When a player dies, their corpse is created at the death location with all equipped items.
 * The player can return to the corpse location to retrieve their items.
 */
@PooledWeaver
public class PlayerCorpse extends Component {
  /**
   * The player entity ID that owns this corpse
   */
  public int playerId;
  
  /**
   * Death location (world coordinates)
   */
  public final Vector2 deathLocation = new Vector2();
  
  /**
   * Player's equipped items at time of death (stored for retrieval)
   * This stores a snapshot of equipped item indices by BodyLoc
   * Key: BodyLoc ordinal, Value: Item index in ItemData.itemData array
   */
  public IntIntMap equippedItemIndices;
  
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
}
