package com.riiablo.engine.server.object;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Mercenary;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.ShrineInteractionEvent;
import com.riiablo.engine.server.event.WellInteractionEvent;
import com.riiablo.engine.server.monster.MonsterRank;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.engine.server.item.AuthoritativeItemMoveService;
import com.riiablo.engine.server.item.NativeGemShrineService;

/**
 * Applies the server-authoritative unit effects emitted by native shrines and
 * wells.
 *
 * <p>D2Game stores shrine bonuses in timed stat lists.  Keeping the bonuses on
 * {@link UnitState} has the same lifetime semantics and, importantly, avoids
 * permanently mutating the character's saved base stats.</p>
 */
@com.artemis.annotations.Wire(failOnNull = false)
public final class NativeShrineEffectSystem extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(NativeShrineEffectSystem.class);
  private static final int SKILL_SHRINE_BONUS = 2;

  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<Mercenary> mMercenary;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Player> mPlayer;

  @com.artemis.annotations.Wire(name = "factory", failOnNull = false)
  protected EntityFactory factory;
  @com.artemis.annotations.Wire(name = "itemGenerator", failOnNull = false)
  protected ItemGenerator itemGenerator;
  protected net.mostlyoriginal.api.event.common.EventSystem events;

  private final AuthoritativeItemMoveService authoritativeItems;

  public NativeShrineEffectSystem() {
    this(null);
  }

  /** Dedicated servers pass their item transaction service for revision sync. */
  public NativeShrineEffectSystem(AuthoritativeItemMoveService authoritativeItems) {
    this.authoritativeItems = authoritativeItems;
  }

  private EntitySubscription mercenaries;
  private EntitySubscription monsters;
  private EntitySubscription players;

  @Override
  protected void initialize() {
    mercenaries = world.getAspectSubscriptionManager().get(
        Aspect.all(Mercenary.class, AttributesWrapper.class));
    monsters = world.getAspectSubscriptionManager().get(
        Aspect.all(Monster.class, AttributesWrapper.class, Position.class));
    players = world.getAspectSubscriptionManager().get(
        Aspect.all(com.riiablo.engine.server.component.Player.class,
            AttributesWrapper.class, Position.class));
  }

  @Subscribe
  public void onWellInteraction(WellInteractionEvent event) {
    if (event == null) return;

    boolean chargeWorthy = cleanse(event.playerId, event.cleansePoison,
        event.cleanseFreeze, event.cleanseCurses);
    if (event.healAndCleansePets) {
      IntBag entities = mercenaries == null ? null : mercenaries.getEntities();
      int[] ids = entities == null ? null : entities.getData();
      int size = entities == null ? 0 : entities.size();
      for (int i = 0; i < size; i++) {
        int petId = ids[i];
        Mercenary mercenary = mMercenary.get(petId);
        if (mercenary == null || mercenary.ownerId != event.playerId) continue;

        // OBJMODE_PetIterate_Heal marks the well as used only when pet life is
        // restored. It still removes poison/freeze/curses unconditionally.
        chargeWorthy |= healToMaximum(petId);
        chargeWorthy |= cleanse(petId, event.cleansePoison, event.cleanseFreeze,
            event.cleanseCurses);
      }
    }

    if (chargeWorthy) event.appliedByConsumer = true;
    log.debug("[WELL_EFFECT] player={} chargeWorthy={} localMask={}",
        event.playerId, chargeWorthy, event.localRestorationMask);
  }

  @Subscribe
  public void onShrineInteraction(ShrineInteractionEvent event) {
    if (event == null) return;
    if (event.code >= 17 && event.code <= 22) {
      applySpecialShrine(event);
      return;
    }
    if (event.code < 6 || event.code > 15) return;
    UnitState state = applyTimedEffect(event.playerId, event.entityId,
        event.code, event.arg0, event.arg1, event.durationFrames);
    if (state == null) {
      log.warn("[SHRINE_EFFECT] rejected: player={}, shrine={}, code={}, reason=no_states",
          event.playerId, event.entityId, event.code);
      return;
    }

    if (event.code == 14) restoreStamina(event.playerId);
    log.info("[SHRINE_EFFECT] applied: player={}, shrine={}, code={}, state={}, "
            + "arg0={}, arg1={}, duration={}",
        event.playerId, event.entityId, event.code, state.stateId,
        event.arg0, event.arg1, state.duration);
  }

  private void applySpecialShrine(ShrineInteractionEvent event) {
    switch (event.code) {
      case 17:
        createTownPortal(event);
        break;
      case 18:
        gem(event);
        break;
      case 19:
        storm(event);
        break;
      case 20:
        upgradeNearestMonster(event);
        break;
      case 21:
        dropConsumables(event, "mpo");
        spawnRadialMissiles(event, 45, 6);
        break;
      case 22:
        dropConsumables(event, "mpg");
        break;
      default:
        break;
    }
  }

  private void gem(ShrineInteractionEvent event) {
    if (mPlayer == null || !mPlayer.has(event.playerId) || factory == null
        || itemGenerator == null || !mPosition.has(event.entityId)) {
      log.warn("[SHRINE_SPECIAL] gem rejected player={} shrine={} reason=missing_bridge",
          event.playerId, event.entityId);
      return;
    }
    Player player = mPlayer.get(event.playerId);
    if (player == null || player.data == null) return;
    Vector2 origin = mPosition.get(event.entityId).position;
    NativeGemShrineService.Result result = NativeGemShrineService.apply(
        player.data.getItems(), itemGenerator,
        item -> factory.createItem(item, origin.x, origin.y),
        created -> { if (created >= 0) world.delete(created); },
        Math.floorMod(event.playerId + event.shrineId, 7));
    if (result.mutated() && authoritativeItems != null) {
      authoritativeItems.markExternalMutation(event.playerId);
    }
    log.info("[SHRINE_SPECIAL] gem player={} shrine={} outcome={} source={} output={} entity={} revision={}",
        event.playerId, event.entityId, result.outcome, result.sourceCode,
        result.outputCode, result.groundEntityId,
        authoritativeItems == null ? -1L : authoritativeItems.revision(event.playerId));
  }

  private void createTownPortal(ShrineInteractionEvent event) {
    if (factory == null || !mPosition.has(event.playerId)
        || !mMapWrapper.has(event.playerId)) return;
    MapWrapper wrapper = mMapWrapper.get(event.playerId);
    if (wrapper == null || wrapper.zone == null || wrapper.zone.level == null) return;
    int act = Math.max(1, wrapper.zone.level.Act);
    int[] towns = com.d2moo.common.drlg.D2LevelIds.TOWN_LEVEL_IDS;
    int destination = towns[Math.min(towns.length - 1, act - 1)];
    Vector2 position = new Vector2(mPosition.get(event.playerId).position).add(2f, 2f);
    if (wrapper.map != null && wrapper.map.flags(position) != 0) {
      position.set(mPosition.get(event.playerId).position);
    }
    int visual = factory.createStaticObjectByClassId(60, position.x, position.y);
    int warp = factory.createQuestWarp(destination, position.x, position.y);
    if (warp != Engine.INVALID_ENTITY && wrapper.zone != null) wrapper.zone.addWarp(warp);
    if (warp == Engine.INVALID_ENTITY && visual != Engine.INVALID_ENTITY) world.delete(visual);
    log.info("[SHRINE_SPECIAL] portal player={} destination={} visual={} warp={} position=({}, {})",
        event.playerId, destination, visual, warp, position.x, position.y);
  }

  private void storm(ShrineInteractionEvent event) {
    if (!mPosition.has(event.entityId) || !mMapWrapper.has(event.entityId)) return;
    Position source = mPosition.get(event.entityId);
    MapWrapper sourceMap = mMapWrapper.get(event.entityId);
    float radius = Math.max(1f, event.arg1);
    int hit = 0;
    hit += damageUnits(players, sourceMap, source, radius, event.playerId, event.arg0);
    hit += damageUnits(monsters, sourceMap, source, radius, event.playerId, event.arg0);
    spawnRadialMissiles(event, 62, 16);
    log.info("[SHRINE_SPECIAL] storm player={} radius={} percent={} hitUnits={}",
        event.playerId, radius, event.arg0, hit);
  }

  private int damageUnits(EntitySubscription subscription, MapWrapper sourceMap,
      Position source, float radius, int attackerId, int percent) {
    if (subscription == null || sourceMap == null || sourceMap.zone == null) return 0;
    IntBag entities = subscription.getEntities();
    int[] ids = entities.getData();
    int hit = 0;
    for (int i = 0; i < entities.size(); i++) {
      int id = ids[i];
      MapWrapper targetMap = mMapWrapper.get(id);
      if (targetMap == null || targetMap.zone != sourceMap.zone
          || !mAttributesWrapper.has(id)
          || mPosition.get(id) == null
          || mPosition.get(id).position.dst2(source.position) > radius * radius) continue;
      Attributes attrs = mAttributesWrapper.get(id).attrs;
      if (attrs == null) continue;
      StatRef hp = attrs.get(Stat.hitpoints, StatRef.obtain());
      if (hp == null || hp.asFixed() <= 0f) continue;
      float damage = Math.max(1f, hp.asFixed() * Math.max(0, percent) / 100f);
      DamageEvent damageEvent = DamageEvent.obtain(attackerId, id, damage);
      if (events != null) events.dispatch(damageEvent);
      hp.sub(Math.max(0f, damageEvent.damage));
      if (hp.asFixed() <= 0f) {
        hp.set(0f);
        if (events != null) events.dispatch(DeathEvent.obtain(attackerId, id));
      }
      hit++;
    }
    return hit;
  }

  private void upgradeNearestMonster(ShrineInteractionEvent event) {
    if (monsters == null || !mPosition.has(event.playerId)
        || !mMapWrapper.has(event.playerId)) return;
    Position player = mPosition.get(event.playerId);
    MapWrapper playerMap = mMapWrapper.get(event.playerId);
    int nearest = Engine.INVALID_ENTITY;
    float nearestDistance = Float.MAX_VALUE;
    IntBag entities = monsters.getEntities();
    int[] ids = entities.getData();
    for (int i = 0; i < entities.size(); i++) {
      int id = ids[i];
      Monster monster = mMonster.get(id);
      MapWrapper map = mMapWrapper.get(id);
      Attributes attrs = mAttributesWrapper.get(id).attrs;
      if (monster == null || attrs == null || map == null || map.zone != playerMap.zone
          || monster.rank != MonsterRank.NORMAL || player.position.dst2(mPosition.get(id).position) >= nearestDistance)
        continue;
      nearest = id;
      nearestDistance = player.position.dst2(mPosition.get(id).position);
    }
    if (nearest == Engine.INVALID_ENTITY) return;
    Monster monster = mMonster.get(nearest);
    monster.setRank(MonsterRank.UNIQUE, monster.affixes, monster.championType, monster.uniqueId);
    Attributes attrs = mAttributesWrapper.get(nearest).attrs;
    scale(attrs, Stat.maxhp, MonsterRank.UNIQUE_HP_MULTIPLIER);
    scale(attrs, Stat.hitpoints, MonsterRank.UNIQUE_HP_MULTIPLIER);
    scale(attrs, Stat.experience, MonsterRank.UNIQUE_EXP_MULTIPLIER);
    log.info("[SHRINE_SPECIAL] monster upgraded player={} monster={} rank={}",
        event.playerId, nearest, MonsterRank.getName(monster.rank));
  }

  private static void scale(Attributes attrs, short stat, float multiplier) {
    if (attrs == null) return;
    float value = attrs.aggregate().getValue(stat, 0f);
    if (value <= 0f) return;
    float scaled = value * multiplier;
    attrs.base().put(stat, scaled);
    attrs.aggregate().put(stat, scaled);
  }

  private void dropConsumables(ShrineInteractionEvent event, String code) {
    if (factory == null || itemGenerator == null || !mPosition.has(event.playerId)) return;
    int count = Math.max(1, event.arg1 - event.arg0 + 1);
    Vector2 origin = mPosition.get(event.playerId).position;
    for (int i = 0; i < count; i++) {
      try {
        Item item = itemGenerator.generate(code);
        if (item != null) factory.createItem(item, origin.x + (i - count / 2) * 0.8f,
            origin.y + 1f);
      } catch (RuntimeException ex) {
        log.warn("[SHRINE_SPECIAL] drop rejected player={} code={} reason={}",
            event.playerId, code, ex.toString());
        break;
      }
    }
  }

  private void spawnRadialMissiles(ShrineInteractionEvent event, int missileId, int count) {
    if (factory == null || Riiablo.files == null || Riiablo.files.Missiles == null
        || !mPosition.has(event.entityId)) return;
    Missiles.Entry missile = Riiablo.files.Missiles.get(missileId);
    if (missile == null) return;
    Vector2 origin = mPosition.get(event.entityId).position;
    Vector2 direction = new Vector2();
    for (int i = 0; i < count; i++) {
      float angle = (float) (Math.PI * 2d * i / count);
      direction.set((float) Math.cos(angle), (float) Math.sin(angle));
      factory.createMissile(missile, direction, origin, event.playerId);
    }
  }

  UnitState applyTimedEffect(int playerId, int shrineEntityId, int code,
      int arg0, int arg1, int durationFrames) {
    if (!mUnitStates.has(playerId)) return null;
    UnitStates states = mUnitStates.get(playerId);
    if (states.stateList == null) states.init(playerId);
    return applyTimedEffect(states.stateList, shrineEntityId, code,
        arg0, arg1, durationFrames);
  }

  static UnitState applyTimedEffect(StateList states, int shrineEntityId,
      int code, int arg0, int arg1, int durationFrames) {
    if (states == null) return null;
    int stateId = stateIdForCode(code);
    if (stateId == StateId.NONE) return null;

    int duration = Math.max(1, durationFrames);
    UnitState state = states.addState(stateId, duration, 1, shrineEntityId);
    if (state == null) return null;

    // Refreshing a shrine state must replace its stat list values, not retain
    // modifiers left by the previous source.
    state.clearModifiers();
    switch (code) {
      case 6:
        state.defenseModifier = Math.max(0, arg0);
        break;
      case 7:
        state.attackModifier = Math.max(0, arg0);
        state.damageModifier = Math.max(0, arg1);
        break;
      case 8:
        state.fireResistModifier = Math.max(0, arg0);
        break;
      case 9:
        state.coldResistModifier = Math.max(0, arg0);
        break;
      case 10:
        state.lightResistModifier = Math.max(0, arg0);
        break;
      case 11:
        state.poisonResistModifier = Math.max(0, arg0);
        break;
      case 12:
        // Native state 0x86 supplies +2 to all learned skills; Shrines.txt
        // arguments are deliberately ignored by D2GAME_SHRINES_SkillBoost.
        state.skillModifier = SKILL_SHRINE_BONUS;
        break;
      case 13:
        state.manaRecoveryModifier = Math.max(0, arg0);
        break;
      case 14:
        state.maxStaminaModifier = Math.max(0, arg0);
        state.staminaRecoveryModifier = 1000;
        break;
      case 15:
        state.experienceModifier = Math.max(0, arg0);
        break;
      default:
        return null;
    }
    state.needsSync = true;
    return state;
  }

  static int stateIdForCode(int code) {
    switch (code) {
      case 6: return StateId.SHRINE_ARMOR;
      case 7: return StateId.SHRINE_COMBAT;
      case 8: return StateId.SHRINE_RESIST_FIRE;
      case 9: return StateId.SHRINE_RESIST_COLD;
      case 10: return StateId.SHRINE_RESIST_LIGHTNING;
      case 11: return StateId.SHRINE_RESIST_POISON;
      case 12: return StateId.SHRINE_SKILL;
      case 13: return StateId.SHRINE_MANA_REGEN;
      case 14: return StateId.SHRINE_STAMINA;
      case 15: return StateId.SHRINE_EXPERIENCE;
      default: return StateId.NONE;
    }
  }

  private boolean cleanse(int entityId, boolean poison, boolean freeze,
      boolean curses) {
    if (!mUnitStates.has(entityId)) return false;
    UnitStates component = mUnitStates.get(entityId);
    StateList states = component == null ? null : component.stateList;
    if (states == null) return false;
    boolean changed = false;
    if (poison) changed |= states.removeState(StateId.POISON);
    if (freeze) changed |= states.removeState(StateId.FREEZE);
    if (curses) changed |= states.removeCurses() > 0;
    return changed;
  }

  private boolean healToMaximum(int entityId) {
    if (!mAttributesWrapper.has(entityId)) return false;
    AttributesWrapper wrapper = mAttributesWrapper.get(entityId);
    Attributes attrs = wrapper == null ? null : wrapper.attrs;
    if (attrs == null) return false;
    float life = attrs.aggregate().getValue(Stat.hitpoints, 0f);
    float maximum = attrs.aggregate().getValue(Stat.maxhp, life);
    if (life >= maximum) return false;
    attrs.base().put(Stat.hitpoints, maximum);
    attrs.aggregate().put(Stat.hitpoints, maximum);
    return true;
  }

  private void restoreStamina(int entityId) {
    if (!mAttributesWrapper.has(entityId)) return;
    Attributes attrs = mAttributesWrapper.get(entityId).attrs;
    if (attrs == null) return;
    float maximum = attrs.aggregate().getValue(Stat.maxstamina, 0f);
    attrs.base().put(Stat.stamina, maximum);
    attrs.aggregate().put(Stat.stamina, maximum);
  }
}
