package com.riiablo.engine.server.ai;

import org.apache.commons.lang3.ArrayUtils;
import com.artemis.Aspect;
import java.util.Locale;

import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;

import com.artemis.ComponentMapper;
import net.mostlyoriginal.api.event.common.EventSystem;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntSet;

import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.quest.NativeMercenaryRewardSystem;
import com.riiablo.engine.server.pet.MercenaryManager;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.PathWrapper;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.event.NpcInteractionEvent;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.item.Item;
import com.riiablo.item.VendorGenerator;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.DS1;
import com.riiablo.save.CharData;

public class Npc extends AI {
  private static final Logger log = LogManager.getLogger(Npc.class);
  private static final float PATH_STALL_REPATH_TIME = 0.4f;
  private static final float PATH_STALL_RETRY_DELAY = 0.5f;

  protected static final IntSet TALKERS    = new IntSet();
  protected static final IntSet REPAIRERS  = new IntSet();
  protected static final IntSet TRADERS    = new IntSet();
  protected static final IntSet GAMBLERS   = new IntSet();
  protected static final IntSet HIRERERS   = new IntSet();
  protected static final IntSet RESURRECTERS = new IntSet();
  protected static final IntSet HEALERS    = new IntSet();
  protected EntitySubscription mercenaries;
  protected com.artemis.World world;
  @com.artemis.annotations.Wire(name = "serverContext", failOnNull = false)
  protected com.riiablo.engine.server.ServerGameplayContext serverContext;
  static {
    // Act 1
    TALKERS.addAll(MonsterType.DECKARDCAIN, MonsterType.DECKARDCAIN_TOWN, MonsterType.CAIN4,
        MonsterType.TYRAEL2,
        MonsterType.GHEED, MonsterType.AKARA, MonsterType.KASHYA,
        MonsterType.CHARSI, MonsterType.WARRIV);
    REPAIRERS.addAll(154);
    TRADERS.addAll(147, 148, 154);
    GAMBLERS.addAll(147);
    // Native hireling vendors: Kashya (A1), Greiz (A2), Asheara (A3),
    // and Qual-Kehk (A5). Each vendor resolves its act-specific pool in the
    // authoritative mercenary service via the NPC's native hcIdx.
    HIRERERS.addAll(MercenaryManager.NPC_KASHYA, MercenaryManager.NPC_GREIZ,
        MercenaryManager.NPC_ASHEARA, MercenaryManager.NPC_QUAL_KEHK);
    // SUNITNPC ResurrectMerc_6FCC9350: Act IV Tyrael revives but does not hire.
    RESURRECTERS.addAll(MercenaryManager.NPC_KASHYA, MercenaryManager.NPC_GREIZ,
        MercenaryManager.NPC_ASHEARA, MercenaryManager.NPC_QUAL_KEHK,
        MonsterType.TYRAEL2);
    // SUNITNPC Heal_6FCCB220: healing is independent of trade/repair services.
    HEALERS.addAll(MonsterType.AKARA, MonsterType.ATMA, MonsterType.FARA, MonsterType.ORMUS,
        MonsterType.JAMELLA,
        MonsterType.MALAH);

    // Act 2
    TALKERS.addAll();
    REPAIRERS.addAll(MonsterType.FARA);
    TRADERS.addAll(MonsterType.DROGNAN, MonsterType.FARA, MonsterType.LYSANDER,
        MonsterType.ELZIX);
    GAMBLERS.addAll(199);
    HIRERERS.addAll();

    // Act 3
    TALKERS.addAll();
    // SUNITNPC Repair_6FCC95B0 and NPC_HandleDialogMessage use native NPC ids.
    REPAIRERS.addAll(MonsterType.HRATLI);
    TRADERS.addAll(MonsterType.ASHEARA, MonsterType.HRATLI, MonsterType.ALKOR,
        MonsterType.ORMUS);
    GAMBLERS.addAll(254);
    HIRERERS.addAll();

    // Act 4
    TALKERS.addAll();
    REPAIRERS.addAll(MonsterType.HALBU);
    TRADERS.addAll(MonsterType.HALBU, MonsterType.JAMELLA);
    GAMBLERS.addAll(MonsterType.JAMELLA);
    HIRERERS.addAll();

    // Act 5
    TALKERS.addAll();
    REPAIRERS.addAll(MonsterType.LARZUK);
    TRADERS.addAll(MonsterType.LARZUK, MonsterType.DREHYA, MonsterType.MALAH);
    GAMBLERS.addAll(MonsterType.DREHYA, MonsterType.NIHLATHAK);
    HIRERERS.addAll();
  }


  protected EventSystem event;

  final Vector2 tmpVec2 = new Vector2();

  static final int NULL_TARGET = ArrayUtils.INDEX_NOT_FOUND;
  int targetId = NULL_TARGET;
  protected float actionTimer = 0;
  boolean actionPerformed = false;
  String state = "";

  protected String name;
  protected MonStats.Entry monstats;

  public Npc(int entityId) {
    super(entityId);
  }

  @Override
  public void initialize() {
    super.initialize();
    monstats = monster.monstats;
    name = monstats.Id;
    mSize.get(entityId).size = 1; // fixes pathfinding issues
    if (world != null) {
      mercenaries = world.getAspectSubscriptionManager()
          .get(Aspect.all(Mercenary.class, AttributesWrapper.class));
    }
  }

  /** Client subclasses own presentation and local vendor stock. */
  protected void presentInteraction() {}
  protected void faceNearbyPlayer() {}
  public void clearVendorStock() {}

  protected static boolean isHealer(int monsterId) {
    return HEALERS.contains(monsterId);
  }

  /** Native town healer service: restore all three resources and cure harmful states. */
  private void healPlayer(int playerId) {
    if (!isHealer(monstats == null ? -1 : monstats.hcIdx)
        || !mAttributesWrapper.has(playerId)) return;
    Attributes attrs = mAttributesWrapper.get(playerId).attrs;
    if (attrs == null || attrs.aggregate() == null || attrs.base() == null) return;
    restoreResource(attrs, Stat.hitpoints, Stat.maxhp);
    restoreResource(attrs, Stat.mana, Stat.maxmana);
    restoreResource(attrs, Stat.stamina, Stat.maxstamina);
    int restoredMercenaries = healMercenaries(playerId);
    if (mUnitStates.has(playerId)) {
      com.riiablo.engine.server.component.UnitStates states = mUnitStates.get(playerId);
      if (states != null && states.stateList != null) {
        states.stateList.removeNegativeEffects(serverContext != null ? serverContext.files().States : Riiablo.files == null ? null : Riiablo.files.States);
      }
    }
    log.info("[NPC_HEAL] npc={} player={} restored=life,mana,stamina mercenaries={}",
        monstats.hcIdx, playerId, restoredMercenaries);
  }

  /**
   * Restores the owner's living hireling using the same runtime attributes as
   * combat, HUD and potion use. Dead hirelings are intentionally left alone;
   * D2 requires a paid hireling resurrection instead of a free healer revive.
   */
  private int healMercenaries(int playerId) {
    if (mercenaries == null || mMercenary == null || mAttributesWrapper == null) return 0;
    IntBag entities = mercenaries.getEntities();
    if (entities == null) return 0;

    int restored = 0;
    int[] ids = entities.getData();
    for (int i = 0; i < entities.size(); i++) {
      int mercenaryId = ids[i];
      if (!mMercenary.has(mercenaryId)) continue;
      Mercenary mercenary = mMercenary.get(mercenaryId);
      if (mercenary == null || mercenary.ownerId != playerId
          || !mAttributesWrapper.has(mercenaryId)) continue;

      AttributesWrapper wrapper = mAttributesWrapper.get(mercenaryId);
      Attributes mercenaryAttrs = wrapper == null ? null : wrapper.attrs;
      if (mercenaryAttrs == null || mercenaryAttrs.aggregate() == null
          || mercenaryAttrs.base() == null) continue;

      // A dead hireling must be revived through the hireling resurrection
      // service, not by talking to a town healer.
      boolean changed = needsRestore(mercenaryAttrs, Stat.hitpoints, Stat.maxhp)
          || needsRestore(mercenaryAttrs, Stat.mana, Stat.maxmana)
          || needsRestore(mercenaryAttrs, Stat.stamina, Stat.maxstamina);
      if (!restoreLivingMercenary(mercenaryAttrs)) continue;
      if (mUnitStates.has(mercenaryId)) {
        com.riiablo.engine.server.component.UnitStates states = mUnitStates.get(mercenaryId);
        if (states != null && states.stateList != null) {
          changed |= states.stateList.removeNegativeEffects(
              serverContext != null ? serverContext.files().States : Riiablo.files == null ? null : Riiablo.files.States) > 0;
        }
      }
      if (changed) restored++;
    }
    return restored;
  }

  /** Returns false for missing or dead hireling attributes. */
  static boolean restoreLivingMercenary(Attributes attrs) {
    if (attrs == null || attrs.aggregate() == null || attrs.base() == null) return false;
    StatRef hitpoints = attrs.aggregate().get(Stat.hitpoints, StatRef.obtain());
    if (hitpoints == null || hitpoints.asFixed() <= 0f) return false;
    restoreResource(attrs, Stat.hitpoints, Stat.maxhp);
    restoreResource(attrs, Stat.mana, Stat.maxmana);
    restoreResource(attrs, Stat.stamina, Stat.maxstamina);
    return true;
  }

  private static boolean restoreResource(Attributes attrs, short resource, short maximum) {
    float value = attrs.aggregate().getValue(maximum, 0f);
    float current = attrs.aggregate().getValue(resource, 0f);
    attrs.base().put(resource, value);
    attrs.aggregate().put(resource, value);
    return current != value;
  }

  private static boolean needsRestore(Attributes attrs, short resource, short maximum) {
    return attrs.aggregate().getValue(resource, 0f)
        != attrs.aggregate().getValue(maximum, 0f);
  }

  @Override
  public void interact(int src, final int entityId) {
    pathfinder.findPath(entityId, null);
    lookAt(src);
    healPlayer(src);
    presentInteraction();
    actionTimer = Float.POSITIVE_INFINITY;
    actionPerformed = false;
    event.dispatch(NpcInteractionEvent.obatin(src, entityId));
    mInteractable.get(entityId).count++;
  }

  @Override
  public void update(float delta) {
    if (mInteractable.get(entityId).count > 0) {
      state = "INTERACTING";
      return;
    }

    Pathfind pathfind = mPathfind.get(entityId);
    if (pathfind != null && pathfind.stalledTime >= PATH_STALL_REPATH_TIME) {
      Vector2 position = mPosition.get(entityId).position;
      tmpVec2.set(pathfind.destination);
      float stalledTime = pathfind.stalledTime;
      boolean blockedByDynamic = pathfind.blockedByDynamic;
      boolean success = pathfinder.findPath(entityId, tmpVec2, true);
      log.info("[NPC_PATH_STALLED] entity={} monster={} position=({}, {}) "
              + "destination=({}, {}) stalledTime={} action={} blockedByDynamic={}",
          entityId, monstats != null ? monstats.Id : "unknown",
          position.x, position.y, tmpVec2.x, tmpVec2.y, stalledTime,
          success ? "repath" : "select_new_target", blockedByDynamic);
      if (!success) {
        pathfinder.findPath(entityId, null);
        actionTimer = PATH_STALL_RETRY_DELAY;
        actionPerformed = false;
        state = "BLOCKED";
      }
      return;
    }
    if (pathfind == null) {
      PathWrapper pathWrapper = mPathWrapper.get(entityId);
      if (pathWrapper == null || pathWrapper.path == null || pathWrapper.path.numPoints <= 0) {
        // Some map-spawned NPCs have no DS1 path component. They remain idle
        // until a valid path is attached instead of crashing the simulation.
        state = "IDLE";
        return;
      }
      DS1.Path path = pathWrapper.path;
      if (targetId == NULL_TARGET) {
        targetId = 0;
      } else if (actionTimer > 0) {
        actionTimer -= delta;
        actionPerformed = actionTimer < 0;
        if (!actionPerformed) faceNearbyPlayer();

        state = "IDLE";
        return;
      } else if (actionPerformed) {
        actionPerformed = false;
        targetId = (int) (rollAiRandomNumber() % path.numPoints);
      } else {
        int actionId = path.points[targetId].action;
        actionTimer = action(actionId);
        actionPerformed = actionTimer < 0;
        return;
      }

      state = "PATHING";
      DS1.Path.Point dst = path.points[targetId];
      pathfinder.findPath(entityId, tmpVec2.set(dst.x, dst.y), true);
    }
  }


  private float action(int actionId) {
    // path.actions == look at nearest player, chill, hold time, quest?
    // 1 = 4 second hold
    // 2 = 6 second hold
    // 3 = 4 second hold
    // 4 = special action at end of 10 second warriv jamella spell
    // 4 = special action at end of 8  second charsi jamella book
    // 4? fara = 5 seconds
    // 4 == S1
    // 5 == S2
    // etc

    switch (actionId) {
      case 1:
      case 3:
        state = "WAITING";
        return 4f;
      case 2:
        state = "WAITING";
        return 6f;
      // TODO: play anim only once, after timer, ending the action
      case 4: {
        cofs.setMode(entityId, Engine.Monster.MODE_S1);
      }
        state = "S1";
        return 10f;
      case 5: {
        cofs.setMode(entityId, Engine.Monster.MODE_S2);
      }
        state = "S2";
        return 10f;
      default:
        log.error("Unknown action index: entityId={}, actionId={}", entityId, actionId);
        state = "ERROR - WAITING";
        return 4f;
    }
  }

  @Override
  public String getState() {
    return state;
  }
}
