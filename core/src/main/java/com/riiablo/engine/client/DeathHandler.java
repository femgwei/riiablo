package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.Gdx;

import com.riiablo.engine.Engine;
import com.riiablo.engine.client.component.Selectable;
import com.riiablo.engine.client.component.AnimationWrapper;
import com.riiablo.engine.client.component.BBoxWrapper;
import com.riiablo.engine.client.component.CofComponentDescriptors;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.component.AIWrapper;
import com.riiablo.engine.server.component.Box2DBody;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.MovementModes;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.PlayerCorpse;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.Target;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.CofAlphas;
import com.riiablo.engine.server.component.CofComponents;
import com.riiablo.engine.server.component.CofTransforms;
import com.riiablo.engine.server.component.Size;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.event.ModeChangeEvent;
import com.riiablo.item.BodyLoc;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.Box2DPhysics;
import com.riiablo.map.Map;
import com.riiablo.attributes.Stat;
import com.artemis.annotations.Wire;

@Wire(failOnNull = false)
public class DeathHandler extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(DeathHandler.class);

  protected ComponentMapper<AIWrapper> mAIWrapper;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Target> mTarget;
  protected ComponentMapper<Corpse> mCorpse;
  protected ComponentMapper<PlayerCorpse> mPlayerCorpse;
  protected ComponentMapper<Selectable> mSelectable;
  protected ComponentMapper<Sequence> mSequence;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<MovementModes> mMovementModes;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<Casting> mCasting;
  protected ComponentMapper<Pathfind> mPathfind;
  protected ComponentMapper<Running> mRunning;
  protected ComponentMapper<UnitStates> mUnitStates;

  protected CofManager cofs;

  protected ComponentMapper<Box2DBody> mBox2DBody;
  protected ComponentMapper<Class> mClass;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<CofAlphas> mCofAlphas;
  protected ComponentMapper<CofComponents> mCofComponents;
  protected ComponentMapper<CofTransforms> mCofTransforms;
  protected ComponentMapper<Size> mSize;
  protected ComponentMapper<CofComponentDescriptors> mCofComponentDescriptors;
  protected ComponentMapper<AnimationWrapper> mAnimationWrapper;
  protected ComponentMapper<BBoxWrapper> mBBoxWrapper;
  
  protected Box2DPhysics box2d;
  
  @Wire(name = "map")
  protected Map map;
  
  @Subscribe
  public void onDeathEvent(DeathEvent event) {
    log.traceEntry("onDeathEvent(killer: {}, victim: {})", event.killer, event.victim);
    final int victimId = event.victim;
    
    // Handle player death
    if (mPlayer.has(victimId)) {
      handlePlayerDeath(victimId);
    }
    
    // Handle monster/NPC death
    if (mAIWrapper.has(victimId)) {
      mAIWrapper.get(victimId).ai.kill();
    }
    
    // Clear target if killer was targeting victim
    if (mTarget.has(event.killer) && mTarget.get(event.killer).target == victimId) {
      mTarget.remove(event.killer);
    }
  }
  
  /**
   * Handle player death: create corpse, save equipment, trigger death sequence
   * Reference: D2MOD - players create corpse at death location, respawn at town
   */
  private void handlePlayerDeath(int playerId) {
    if (mPlayerCorpse.has(playerId)) {
      log.debug("[PLAYER_DEATH] duplicate death ignored entity={}", playerId);
      return;
    }
    log.info("[PLAYER_DEATH] begin entity={}", playerId);
    
    // Get player position (death location)
    if (!mPosition.has(playerId)) {
      log.warn("Player {} has no position component, cannot create corpse", playerId);
      return;
    }
    
    if (!mPlayer.has(playerId)) {
      log.warn("Player {} has no Player component", playerId);
      return;
    }
    
    // D2MOD: Immediately remove Velocity component to prevent movement after death
    // This should be done before creating death sequence to prevent player from running
    if (mVelocity.has(playerId)) {
      mVelocity.remove(playerId);
      log.debug("Player {} Velocity component removed on death", playerId);
    }
    
    // Remove target component to stop any ongoing movement/attacks
    if (mTarget.has(playerId)) {
      mTarget.remove(playerId);
      log.debug("Player {} Target component removed on death", playerId);
    }
    
    Position pos = mPosition.get(playerId);
    Vector2 deathLocation = new Vector2(pos.position);
    
    // Save equipped items before removing them
    Player player = mPlayer.get(playerId);
    com.riiablo.save.ItemData itemData = player.data.getItems();
    com.badlogic.gdx.utils.ObjectMap<BodyLoc, com.riiablo.item.Item> equippedItems =
        new com.badlogic.gdx.utils.ObjectMap<>();

    // The live player's COF components are cleared by unequip notifications.
    // Preserve the death-time appearance for the independent corpse entity.
    int[] corpseComponents = mCofComponents.has(playerId)
        ? mCofComponents.get(playerId).component.clone() : null;
    float[] corpseAlphas = mCofAlphas.has(playerId)
        ? mCofAlphas.get(playerId).alpha.clone() : null;
    byte[] corpseTransforms = mCofTransforms.has(playerId)
        ? mCofTransforms.get(playerId).transform.clone() : null;
    
    // D2MOD: Ensure player HP stays at 0 or below after death
    // Save current HP state before unequipping items (which may trigger updateStats)
    float currentHp = 0f;
    if (mAttributesWrapper.has(playerId)) {
      com.riiablo.attributes.Attributes attrs = mAttributesWrapper.get(playerId).attrs;
      com.riiablo.attributes.StatRef hpRef = attrs.get(Stat.hitpoints);
      if (hpRef != null) {
        currentHp = hpRef.asFixed();
        // Ensure HP is 0 or negative (dead)
        if (currentHp > 0f) {
          hpRef.set(0f);
          log.debug("Player {} HP set to 0 on death (was {})", playerId, currentHp);
        }
      }
    }
    
    // Save all equipped item indices by BodyLoc
    // Reference: D2MOD - player corpse stores equipped items
    for (BodyLoc bodyLoc : BodyLoc.values()) {
      if (bodyLoc == BodyLoc.NONE) continue;
      com.riiablo.item.Item item = itemData.getSlot(bodyLoc);
      if (item != null) {
        // Get item index from equipped map by unequipping temporarily
        // We'll unequip it properly after saving the index
        int itemIndex = itemData.unequipItem(bodyLoc);
        if (itemIndex != com.riiablo.save.ItemData.INVALID_ITEM) {
          equippedItems.put(bodyLoc, item);
          log.info("[PLAYER_CORPSE_ITEM] action=detach player={} bodyLoc={} itemIndex={} code={}",
              playerId, bodyLoc, itemIndex, item.code);
        }
      }
    }
    
    // D2MOD: After unequipping items, ensure HP remains at 0 or below
    // updateStats() may have recalculated maxhp, but HP should stay at 0
    if (mAttributesWrapper.has(playerId)) {
      com.riiablo.attributes.Attributes attrs = mAttributesWrapper.get(playerId).attrs;
      com.riiablo.attributes.StatRef hpRef = attrs.get(Stat.hitpoints);
      if (hpRef != null && hpRef.asFixed() > 0f) {
        hpRef.set(0f);
        log.debug("Player {} HP reset to 0 after unequipping items", playerId);
      }
    }
    
    // Create PlayerCorpse component to store death information (attached to player entity)
    PlayerCorpse playerCorpse = mPlayerCorpse.create(playerId);
    playerCorpse.playerId = playerId;
    playerCorpse.deathLocation.set(deathLocation);
    playerCorpse.equippedItems.clear();
    playerCorpse.equippedItems.putAll(equippedItems);
    
    log.info("Player {} items saved to corpse: {} items unequipped", 
        playerId, equippedItems.size);
    
    // Create death sequence: MODE_DT (death animation) -> MODE_DD (corpse)
    // Reference: D2MOD - players use MODE_DT -> MODE_DD sequence like monsters
    // Replace an in-flight attack/cast sequence.  Keeping it would let the
    // attack's completion event switch the player back to standing mode.
    if (mCasting.has(playerId)) mCasting.remove(playerId);
    if (mPathfind.has(playerId)) mPathfind.remove(playerId);
    if (mSequence.has(playerId)) mSequence.remove(playerId);
    // Player DT/DD COFs only exist for HTH. Keeping the equipped weapon class
    // here attempts to load e.g. AMDT1HT, stalls the death sequence, and lets
    // ESC revive the player while the old physics body is still active.
    cofs.setWClass(playerId, Engine.WEAPON_HTH);
    mSequence.create(playerId).sequence(Engine.Player.MODE_DT, Engine.Player.MODE_DD);
    log.info("[PLAYER_DEATH] sequence entity={} mode=DT->DD", playerId);
    
    // Create independent corpse entity at death location (like d2mod)
    // The corpse entity will remain at death location while player respawns at town
    createCorpseEntity(
        playerId, deathLocation, playerCorpse,
        corpseComponents, corpseAlphas, corpseTransforms);
    
    log.info("Player {} died at location: ({}, {}), {} items saved to corpse", 
        playerId, deathLocation.x, deathLocation.y, equippedItems.size);
  }

  @Subscribe
  public void onModeChanged(ModeChangeEvent event) {
    final int entityId = event.entityId;
    log.trace("onModeChanged(entityId: {}, mode: {})", entityId, event.mode);
    
    // Handle player corpse mode
    if (mPlayer.has(entityId) && event.mode == Engine.Player.MODE_DD) {
      log.debug("Player {} entered corpse mode (MODE_DD)", entityId);
      handlePlayerCorpseMode(entityId);
      return;
    }
    
    // Handle monster corpse mode
    if (mMonster.has(entityId) && event.mode == Engine.Monster.MODE_DD) {
      log.debug("Monster {} entered corpse mode (MODE_DD)", entityId);
      // Destroy physics body so corpse doesn't block movement
      if (mBox2DBody.has(entityId)) {
        Body body = mBox2DBody.get(entityId).body;
        if (body != null) {
          box2d.getPhysics().destroyBody(body);
        }
        mBox2DBody.remove(entityId);
      }

      // Remove velocity and movement modes to prevent VelocityModeChanger from changing mode
      // Corpses should remain in MODE_DD and not switch to MODE_NU
      if (mVelocity.has(entityId)) {
        mVelocity.remove(entityId);
      }
      if (mMovementModes.has(entityId)) {
        mMovementModes.remove(entityId);
      }

      // Remove AI so corpse has no behavior
      if (mAIWrapper.has(entityId)) {
        mAIWrapper.remove(entityId);
      }

      // Remove target component if present
      if (mTarget.has(entityId)) {
        mTarget.remove(entityId);
      }

      // Remove selectable component so corpse can't be highlighted
      if (mSelectable.has(entityId)) {
        mSelectable.remove(entityId);
      }

      // Add Corpse component to track lifetime and enable corpse removal after timeout
      // The CorpseManager system will handle the countdown and eventual deletion
      // This also prevents VelocityModeChanger from processing the corpse
      if (!mCorpse.has(entityId)) {
        mCorpse.create(entityId);
        log.debug("Monster {} died, corpse will remain for {} seconds", entityId, Corpse.DEFAULT_DURATION);
      }
    }
  }
  
  /**
   * Handle player entering corpse mode (MODE_DD)
   * In D2, player stays dead until ESC is pressed, then respawns at town
   * Reference: d2mod - player remains in MODE_DD until ESC key pressed
   */
  private void handlePlayerCorpseMode(int playerId) {
    // Destroy physics body so corpse doesn't block movement
    if (mBox2DBody.has(playerId)) {
      Body body = mBox2DBody.get(playerId).body;
      if (body != null) {
        box2d.getPhysics().destroyBody(body);
      }
      mBox2DBody.remove(playerId);
    }

    // Remove velocity and movement modes to prevent VelocityModeChanger from changing mode
    if (mVelocity.has(playerId)) {
      mVelocity.remove(playerId);
    }
    if (mMovementModes.has(playerId)) {
      mMovementModes.remove(playerId);
    }

    // Remove target component if present (monsters should stop attacking dead player)
    if (mTarget.has(playerId)) {
      mTarget.remove(playerId);
    }

    // Remove selectable component
    if (mSelectable.has(playerId)) {
      mSelectable.remove(playerId);
    }

    // PlayerCorpse should already be created in handlePlayerDeath
    if (!mPlayerCorpse.has(playerId)) {
      log.warn("Player {} entered corpse mode but PlayerCorpse component not found", playerId);
      return;
    }
    
    // Player stays in MODE_DD (corpse mode) until ESC is pressed
    // ESC key handling will be done in GameScreen or a separate system
    // Do NOT respawn immediately - wait for ESC key
    log.info("Player {} is now dead (MODE_DD). Press ESC to respawn at town.", playerId);
  }
  
  /**
   * Create independent corpse entity at death location
   * Reference: d2mod - corpse is a separate entity that remains at death location
   */
  private void createCorpseEntity(
      int playerId,
      Vector2 deathLocation,
      PlayerCorpse playerCorpse,
      int[] equippedComponents,
      float[] equippedAlphas,
      byte[] equippedTransforms) {
    if (!mPlayer.has(playerId) || !mClass.has(playerId)) {
      log.warn("Player {} missing required components for corpse creation", playerId);
      return;
    }
    
    if (!mCofReference.has(playerId)) {
      log.warn("Player {} missing CofReference component", playerId);
      return;
    }
    
    // Create corpse entity using world.create() and manually add components
    int corpseEntityId = world.create();
    
    // Copy player's class type
    Class playerClass = mClass.get(playerId);
    Class corpseClass = mClass.create(corpseEntityId);
    corpseClass.type = playerClass.type;
    
    // Copy player's appearance to corpse (MODE_DD = corpse mode)
    com.riiablo.engine.server.component.CofReference playerCof = mCofReference.get(playerId);
    com.riiablo.engine.server.component.CofReference corpseCof = mCofReference.create(corpseEntityId);
    corpseCof.set(playerCof.token, Engine.Player.MODE_DD); // Corpse mode
    corpseCof.wclass = Engine.WEAPON_HTH;

    CofComponents corpseComponents = mCofComponents.create(corpseEntityId);
    if (equippedComponents != null) {
      System.arraycopy(
          equippedComponents, 0, corpseComponents.component, 0, corpseComponents.component.length);
    }
    CofAlphas corpseAlphas = mCofAlphas.create(corpseEntityId);
    if (equippedAlphas != null) {
      System.arraycopy(equippedAlphas, 0, corpseAlphas.alpha, 0, corpseAlphas.alpha.length);
    }
    CofTransforms corpseTransforms = mCofTransforms.create(corpseEntityId);
    if (equippedTransforms != null) {
      System.arraycopy(
          equippedTransforms, 0, corpseTransforms.transform, 0, corpseTransforms.transform.length);
    }

    // This entity is created after the normal client factory path, so attach
    // the client-side COF/animation components explicitly to make it visible.
    mCofComponentDescriptors.create(corpseEntityId);
    AnimationWrapper animation = mAnimationWrapper.create(corpseEntityId);
    mBBoxWrapper.create(corpseEntityId).box = animation.animation.getBox();
    
    // Set corpse position at death location
    Position corpsePos = mPosition.create(corpseEntityId);
    corpsePos.position.set(deathLocation);
    
    // Set corpse size
    if (mSize.has(playerId)) {
      Size playerSize = mSize.get(playerId);
      Size corpseSize = mSize.create(corpseEntityId);
      corpseSize.size = playerSize.size;
    } else {
      mSize.create(corpseEntityId).size = com.riiablo.engine.server.component.Size.MEDIUM;
    }
    
    // Attach PlayerCorpse component to corpse entity
    PlayerCorpse corpseComponent = mPlayerCorpse.create(corpseEntityId);
    corpseComponent.playerId = playerId;
    corpseComponent.deathLocation.set(deathLocation);
    corpseComponent.equippedItems.clear();
    corpseComponent.equippedItems.putAll(playerCorpse.equippedItems);
    
    // Add Corpse component to mark it as a corpse
    Corpse corpse = mCorpse.create(corpseEntityId);
    corpse.timeRemaining = PlayerCorpse.CORPSE_DURATION;
    corpse.usable = false;
    
    log.info("Created corpse entity {} at death location ({}, {}) for player {}", 
        corpseEntityId, deathLocation.x, deathLocation.y, playerId);
  }
  
  /**
   * Check if player is dead (has PlayerCorpse component)
   * D2MOD: Player is considered dead once PlayerCorpse component is created
   * (even if still in MODE_DT death animation, before MODE_DD)
   */
  public boolean isPlayerDead(int playerId) {
    if (!mPlayer.has(playerId)) {
      return false;
    }
    // D2MOD: Player is dead if PlayerCorpse component exists
    // This component is created immediately when player dies, even before MODE_DD
    return mPlayerCorpse.has(playerId);
  }

  /**
   * Returns whether the death animation has completed and ESC may revive the
   * player. Movement remains blocked for the whole DT/DD interval via
   * {@link #isPlayerDead(int)}.
   */
  public boolean canRespawnPlayer(int playerId) {
    return isPlayerDead(playerId)
        && mCofReference.has(playerId)
        && mCofReference.get(playerId).mode == Engine.Player.MODE_DD;
  }
  
  /**
   * Respawn player at town location (called when ESC is pressed after death)
   * Reference: D2MOD - players respawn at town waypoint after death
   */
  public void respawnPlayerAtTown(int playerId) {
    if (map == null) {
      log.warn("Map not available, cannot respawn player at town");
      return;
    }
    
    // Find town entry point (similar to GameScreen spawn logic)
    Vector2 townLocation = map.find(Map.ID.TOWN_ENTRY_1);
    if (townLocation == null) {
      townLocation = map.find(Map.ID.TOWN_ENTRY_2);
    }
    
    if (townLocation == null) {
      log.warn("Could not find town entry point, respawning at origin");
      townLocation = new Vector2(0, 0);
    }
    
    Vector2 oldLocation = mPosition.has(playerId)
        ? new Vector2(mPosition.get(playerId).position)
        : new Vector2(Float.NaN, Float.NaN);

    // Move player to town location
    if (mPosition.has(playerId)) {
      mPosition.get(playerId).position.set(townLocation);
      log.debug("Player {} respawned at town location: ({}, {})", 
          playerId, townLocation.x, townLocation.y);
    }

    if (mPathfind.has(playerId)) mPathfind.remove(playerId);
    if (mCasting.has(playerId)) mCasting.remove(playerId);
    if (mTarget.has(playerId)) mTarget.remove(playerId);
    if (mRunning.has(playerId)) mRunning.remove(playerId);
    if (mUnitStates.has(playerId) && mUnitStates.get(playerId).stateList != null) {
      mUnitStates.get(playerId).stateList.clearAll();
    }
    
    // Restore HP/MP to full
    if (mAttributesWrapper.has(playerId)) {
      com.riiablo.attributes.Attributes attrs = mAttributesWrapper.get(playerId).attrs;
      com.riiablo.attributes.StatRef maxHpRef = com.riiablo.attributes.StatRef.obtain();
      com.riiablo.attributes.StatRef maxMpRef = com.riiablo.attributes.StatRef.obtain();
      
      float maxHp = attrs.get(Stat.maxhp, maxHpRef).asFixed();
      float maxMp = attrs.get(Stat.maxmana, maxMpRef).asFixed();
      
      attrs.get(Stat.hitpoints).set(maxHp);
      attrs.get(Stat.mana).set(maxMp);
      
      // StatRef doesn't have a release method, just let GC handle it
      
      log.debug("Player {} HP/MP restored to full: HP={}, MP={}", playerId, maxHp, maxMp);
    }
    
    // Reset player mode to standing (MODE_NU)
    // Player should be alive and standing at town
    cofs.setMode(playerId, Engine.Player.MODE_NU);
    
    // Remove corpse mode sequence if present
    if (mSequence.has(playerId)) {
      mSequence.remove(playerId);
    }
    
    // D2MOD: Remove PlayerCorpse component to mark player as alive
    // This is critical - without removing this, isPlayerDead() will always return true
    if (mPlayerCorpse.has(playerId)) {
      mPlayerCorpse.remove(playerId);
      log.debug("Player {} PlayerCorpse component removed on respawn", playerId);
    }
    
    // Restore Velocity component so player can move again
    // D2MOD: Velocity component was removed on death, need to recreate it with correct speeds
    if (!mVelocity.has(playerId) && mPlayer.has(playerId)) {
      Player player = mPlayer.get(playerId);
      com.riiablo.save.CharData charData = player.data;
      if (charData != null) {
        // Get walk/run speeds from CharStats (same as ServerEntityFactory.createPlayer)
        com.riiablo.codec.excel.CharStats.Entry charStats = charData.classId != null ? charData.classId.entry() : null;
        float walkSpeed = charStats != null && charStats.WalkVelocity > 0
            ? charStats.WalkVelocity : Engine.Player.SPEED_WALK;
        float runSpeed = charStats != null && charStats.RunVelocity > 0
            ? charStats.RunVelocity : Engine.Player.SPEED_RUN;
        mVelocity.create(playerId).set(walkSpeed, runSpeed);
        log.debug("Player {} Velocity component restored: walkSpeed={}, runSpeed={}", playerId, walkSpeed, runSpeed);
      }
    }

    // ESC can be delivered close to a mode transition. If a body still
    // exists, teleport it as well as Position; otherwise
    // Box2DSynchronizerPost copies the old death location back over the town
    // position on the next frame, which looks like an in-place resurrection.
    if (mBox2DBody.has(playerId)) {
      Body body = mBox2DBody.get(playerId).body;
      if (body != null) {
        body.setLinearVelocity(0f, 0f);
        body.setAngularVelocity(0f);
        body.setTransform(townLocation, body.getAngle());
        log.info("[PLAYER_REVIVE] physics body teleported entity={}", playerId);
      } else {
        mBox2DBody.remove(playerId);
        mBox2DBody.create(playerId);
        log.info("[PLAYER_REVIVE] null physics body recreated entity={}", playerId);
      }
    } else {
      mBox2DBody.create(playerId);
      log.info("[PLAYER_REVIVE] physics body recreated entity={}", playerId);
    }
    
    // Restore MovementModes component if it was removed
    if (!mMovementModes.has(playerId)) {
      mMovementModes.create(playerId).set(Engine.Player.MODE_TN, Engine.Player.MODE_TW, Engine.Player.MODE_RN);
      log.debug("Player {} MovementModes component restored", playerId);
    }
    
    log.info("[PLAYER_REVIVE] entity={} position=({}, {}) hpRestored=true movementRestored=true",
        playerId, townLocation.x, townLocation.y);
    Map.Zone townZone = map.getZone(townLocation);
    Gdx.app.log("DeathHandler", String.format(
        "[PLAYER_REVIVE] entity=%d from=(%.3f,%.3f) to=(%.3f,%.3f) zone=%s",
        playerId, oldLocation.x, oldLocation.y, townLocation.x, townLocation.y,
        townZone == null || townZone.level == null ? "null" : townZone.level.LevelName));
  }
}
