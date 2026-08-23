package com.riiablo.engine.server;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;

import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.PlayerCorpse;
import com.riiablo.engine.server.component.Position;
import com.riiablo.item.BodyLoc;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.save.ItemData;

/**
 * System that handles player corpse retrieval.
 * When a player approaches their corpse, they can retrieve their items.
 * Reference: D2MOD - players can interact with their corpse to get items back
 */
@All({Player.class, Position.class})
public class PlayerCorpseRetrievalSystem extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(PlayerCorpseRetrievalSystem.class);
  
  /**
   * Maximum distance for corpse interaction (in world units)
   * In D2, players need to be close to their corpse to retrieve items
   */
  private static final float CORPSE_INTERACTION_DISTANCE = 2.0f;
  
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<PlayerCorpse> mPlayerCorpse;
  
  @Override
  protected void process(int playerId) {
    // DeathHandler attaches PlayerCorpse to the player as the authoritative
    // dead-state marker. Do not let a dead player retrieve the independent
    // corpse that occupies the same death location; retrieval becomes valid
    // only after ESC has revived the player and removed this marker.
    if (mPlayerCorpse.has(playerId)) return;

    // Check if this player has a corpse
    // We need to find the corpse entity for this player
    // For now, we'll check all PlayerCorpse components to find the one matching this player
    
    // TODO: Optimize this - we could maintain a map of playerId -> corpseEntityId
    // For now, iterate through all entities with PlayerCorpse component
    com.artemis.AspectSubscriptionManager subscriptionManager = world.getAspectSubscriptionManager();
    com.artemis.EntitySubscription subscription = subscriptionManager.get(com.artemis.Aspect.all(PlayerCorpse.class));
    com.artemis.utils.IntBag entities = subscription.getEntities();
    
    Position playerPos = mPosition.get(playerId);
    
    for (int i = 0; i < entities.size(); i++) {
      int corpseEntityId = entities.get(i);
      // The player-side marker is not the corpse entity containing loot.
      if (corpseEntityId == playerId) continue;
      if (!mPlayerCorpse.has(corpseEntityId)) continue;
      
      PlayerCorpse corpse = mPlayerCorpse.get(corpseEntityId);
      
      // Check if this corpse belongs to this player
      if (corpse.playerId != playerId) continue;
      
      // Check if corpse has already been retrieved
      if (corpse.retrieved) continue;
      
      // Check distance between player and corpse
      // Use death location stored in PlayerCorpse component
      float distance = playerPos.position.dst(corpse.deathLocation);
      
      if (distance <= CORPSE_INTERACTION_DISTANCE) {
        // Player is close enough to retrieve corpse
        retrieveCorpse(playerId, corpseEntityId, corpse);
        break; // Only process one corpse per frame
      }
    }
  }
  
  /**
   * Retrieve items from corpse and restore them to player
   */
  private void retrieveCorpse(int playerId, int corpseEntityId, PlayerCorpse corpse) {
    log.info("Player {} retrieving corpse at ({}, {})", 
        playerId, corpse.deathLocation.x, corpse.deathLocation.y);
    
    if (!mPlayer.has(playerId)) {
      log.warn("Player {} not found for corpse retrieval", playerId);
      return;
    }
    
    Player player = mPlayer.get(playerId);
    ItemData itemData = player.data.getItems();
    
    // Restore all equipped items from corpse
    int itemsRestored = 0;
    com.badlogic.gdx.utils.IntIntMap.Keys keys = corpse.equippedItemIndices.keys();
    while (keys.hasNext) {
      int bodyLocOrdinal = keys.next();
      BodyLoc bodyLoc = BodyLoc.valueOf(bodyLocOrdinal);
      if (bodyLoc == null || bodyLoc == BodyLoc.NONE) continue;
      
      int itemIndex = corpse.equippedItemIndices.get(bodyLocOrdinal, ItemData.INVALID_ITEM);
      if (itemIndex == ItemData.INVALID_ITEM) continue;
      
      // Check if item still exists in itemData
      // Use getItemCount() or check bounds differently
      // For now, we'll try to equip and catch exceptions
      
      // Re-equip the item using public method
      try {
        itemData.equipItem(bodyLoc, itemIndex);
        itemsRestored++;
        log.debug("Restored item to {}: index={}", bodyLoc, itemIndex);
      } catch (Exception e) {
        log.warn("Failed to restore item to {}: {}", bodyLoc, e.getMessage());
      }
    }
    
    log.info("Player {} retrieved {} items from corpse", playerId, itemsRestored);
    
    // Mark corpse as retrieved
    corpse.retrieved = true;
    
    // Remove corpse entity (or mark it for removal)
    // For now, we'll just mark it as retrieved and let CorpseManager handle removal
    // TODO: Remove corpse entity immediately or after a delay
  }
}
