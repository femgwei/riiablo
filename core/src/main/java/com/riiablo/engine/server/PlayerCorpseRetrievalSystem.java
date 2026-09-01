package com.riiablo.engine.server;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;

import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.PlayerCorpse;
import com.riiablo.engine.server.component.Position;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
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
      if (!corpse.canRetrieve(playerId)) continue;
      
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
    
    // Restore all equipped items from corpse. Item identity is stable even if
    // inventory operations shifted ItemData indices after the player revived.
    int itemsRestored = 0;
    com.badlogic.gdx.utils.Array<BodyLoc> restoredSlots = new com.badlogic.gdx.utils.Array<>();
    for (com.badlogic.gdx.utils.ObjectMap.Entry<BodyLoc, Item> entry : corpse.equippedItems) {
      BodyLoc bodyLoc = entry.key;
      Item item = entry.value;
      if (bodyLoc == null || bodyLoc == BodyLoc.NONE || item == null) continue;

      if (!itemData.contains(item)) {
        log.error("[PLAYER_CORPSE_ITEM] action=missing player={} corpse={} bodyLoc={} code={}",
            playerId, corpseEntityId, bodyLoc, item.code);
        continue;
      }

      boolean restored = false;
      try {
        if (itemData.getSlot(bodyLoc) == null) {
          itemData.equipItem(bodyLoc, item);
          restored = true;
          log.info("[PLAYER_CORPSE_ITEM] action=equip player={} corpse={} bodyLoc={} code={}",
              playerId, corpseEntityId, bodyLoc, item.code);
        } else if (itemData.moveOwnedToInventory(item)) {
          restored = true;
          log.info("[PLAYER_CORPSE_ITEM] action=inventory player={} corpse={} bodyLoc={} code={}",
              playerId, corpseEntityId, bodyLoc, item.code);
        }
      } catch (Exception e) {
        log.warn("[PLAYER_CORPSE_ITEM] action=restore_failed player={} corpse={} bodyLoc={} reason={}",
            playerId, corpseEntityId, bodyLoc, e.getMessage());
      }
      if (restored) {
        restoredSlots.add(bodyLoc);
        itemsRestored++;
      }
    }

    for (BodyLoc bodyLoc : restoredSlots) corpse.equippedItems.remove(bodyLoc);

    if (corpse.cursorItem != null) {
      Item cursorItem = corpse.cursorItem;
      if (itemData.restoreCursorItem(cursorItem) || itemData.moveOwnedToInventory(cursorItem)) {
        corpse.cursorItem = null;
        itemsRestored++;
        log.info("[PLAYER_CORPSE_ITEM] action=restore_cursor player={} corpse={} code={}",
            playerId, corpseEntityId, cursorItem.code);
      }
    }
    
    log.info("Player {} retrieved {} items from corpse", playerId, itemsRestored);
    
    if (corpse.equippedItems.size == 0 && corpse.cursorItem == null) {
      corpse.retrieved = true;
      log.info("[PLAYER_CORPSE] action=retrieved player={} corpse={} items={}",
          playerId, corpseEntityId, itemsRestored);
      world.delete(corpseEntityId);
    } else {
      log.info("[PLAYER_CORPSE] action=partial player={} corpse={} restored={} remaining={}",
          playerId, corpseEntityId, itemsRestored, corpse.equippedItems.size);
    }
  }
}
