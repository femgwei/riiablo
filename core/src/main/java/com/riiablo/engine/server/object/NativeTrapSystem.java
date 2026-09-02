package com.riiablo.engine.server.object;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector2;
import com.d2moo.common.drlg.D2ObjectIds;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.NativeTrapFire;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.event.NativeTrapInteractionEvent;
import com.riiablo.engine.server.monster.MonsterType;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.Map;

import net.mostlyoriginal.api.event.common.Subscribe;

/**
 * Authoritative consumer for D2Game's object-trap callback.
 *
 * <p>D2MOO dispatches trap types 1..4 to a concrete trap monster, types 6/8/9
 * to firebolt or a region-selected monster, and types 5/7 to a fire-object
 * handler. It also applies level-dependent compatibility routing before the
 * callback (notably Act I's firebolt fallback). The Java server has no
 * object-region trap-monster table yet, so types 8/9 use firebolt while
 * retaining the native one-or-two-unit roll. Every spawned unit receives the
 * native no-XP/no-TC policy so trapped containers cannot be farmed.</p>
 */
@Wire(failOnNull = false)
public class NativeTrapSystem extends BaseSystem {
  private static final Logger log = LogManager.getLogger(NativeTrapSystem.class);

  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<NativeTrapFire> mFire;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<Interactable> mInteractable;

  @Wire(name = "factory", failOnNull = false)
  protected EntityFactory factory;

  private final Vector2 origin = new Vector2();

  @Override
  protected void processSystem() {}

  @Subscribe
  public void onTrap(NativeTrapInteractionEvent event) {
    if (event == null || !event.firstActivation || event.trapType <= 0) return;
    if (factory == null || !mPosition.has(event.entityId)) {
      log.warn("[OBJECT_TRAP] skipped: entity={} trapType={} factory={} position={}",
          event.entityId, event.trapType, factory != null, mPosition.has(event.entityId));
      return;
    }

    Position position = mPosition.get(event.entityId);
    origin.set(position.position);
    MapWrapper wrapper = mMapWrapper.get(event.entityId);
    Map.Zone zone = wrapper == null ? null : wrapper.zone;
    int levelId = zone == null || zone.level == null ? -1 : zone.level.Id;
    int handlerType = nativeHandlerType(event.trapType, levelId);
    int monsterId = monsterForTrapType(handlerType);
    int count = monsterCount(handlerType, trapSeed(event, position));
    if (monsterId < 0 || count <= 0) {
      if (handlerType == 5 || handlerType == 7) {
        spawnFireObjects(event, position, handlerType);
      } else {
        log.info("[OBJECT_TRAP] no persistent spawn mapping: entity={} trapType={} ",
            event.entityId, event.trapType);
      }
      return;
    }

    int spawned = 0;
    for (int i = 0; i < count; i++) {
      int dx = i == 0 ? 0 : (i & 1) == 0 ? 2 : -2;
      int dy = i == 0 ? 0 : (i & 1) == 0 ? -2 : 2;
      int id = factory.createMonster(monsterId, origin.x + dx, origin.y + dy);
      if (id == Engine.INVALID_ENTITY) continue;
      factory.applyNativeUnitFlags(id, NativeUnitFlags.NEST_SUMMON);
      spawned++;
    }
    log.info("[OBJECT_TRAP] triggered: entity={} player={} trapType={} handlerType={} "
            + "monster={} requested={} spawned={} zone={}",
        event.entityId, event.playerId, event.trapType, handlerType, monsterId,
        count, spawned, levelId);
  }

  private void spawnFireObjects(NativeTrapInteractionEvent event, Position position,
      int handlerType) {
    MapWrapper sourceWrapper = mMapWrapper.get(event.entityId);
    Map.Zone sourceZone = sourceWrapper == null ? null : sourceWrapper.zone;
    int large = factory.createStaticObjectByClassId(
        D2ObjectIds.OBJECT_FIRE_LARGE, position.position.x, position.position.y);
    attachFireLifetime(large, 3f, event.entityId, sourceZone);
    int small = factory.createStaticObjectByClassId(D2ObjectIds.OBJECT_FIRE_SMALL,
        position.position.x + 1f, position.position.y);
    attachFireLifetime(small, 1.5f, event.entityId, sourceZone);
    log.info("[OBJECT_TRAP_FIRE] triggered entity={} trapType={} large={} small={}",
        event.entityId, handlerType, large, small);
  }

  private void attachFireLifetime(int fireId, float radius, int sourceEntityId,
      Map.Zone sourceZone) {
    if (fireId == Engine.INVALID_ENTITY || !world.getEntityManager().isActive(fireId)) {
      log.warn("[OBJECT_TRAP_FIRE] creation failed source={} fire={}", sourceEntityId, fireId);
      return;
    }
    Position firePosition = mPosition.get(fireId);
    MapWrapper fireWrapper = mMapWrapper.get(fireId);
    if (firePosition == null || sourceZone != null
        && (fireWrapper == null || fireWrapper.zone != sourceZone)) {
      world.delete(fireId);
      return;
    }
    CofReference cof = mCofReference.get(fireId);
    if (cof != null) cof.mode = Engine.Object.MODE_OP;
    mInteractable.remove(fireId);
    com.riiablo.engine.server.component.Object object =
        world.getMapper(com.riiablo.engine.server.component.Object.class).get(fireId);
    int damage = object == null || object.base == null ? 0 : object.base.Damage;
    float duration = NativeTrapFire.DEFAULT_DURATION;
    if (object != null && object.base != null && object.base.FrameCnt != null
        && Engine.Object.MODE_OP < object.base.FrameCnt.length
        && object.base.FrameCnt[Engine.Object.MODE_OP] > 0) {
      duration = Math.max(1f,
          (object.base.FrameCnt[Engine.Object.MODE_OP] >> 8) / 25f);
    }
    int seed = 31 * sourceEntityId + fireId;
    seed = 31 * seed + Float.floatToIntBits(firePosition.position.x);
    seed = 31 * seed + Float.floatToIntBits(firePosition.position.y);
    mFire.create(fireId).reset(duration, radius, damage, seed);
  }

  /** D2Game's level compatibility branches in {@code sub_6FC74DF0}. */
  static int nativeHandlerType(int trapType, int levelId) {
    if (levelId < 0) return trapType;
    if (trapType == 8 && levelId >= 75) return 2;
    if (trapType == 3 && levelId < 40 && levelId != 25) return 2;
    if ((trapType == 1 || trapType == 4) && levelId < 40) return 2;
    return trapType;
  }

  /** Native trap callback table, including the Act I firebolt compatibility branches. */
  static int monsterForTrapType(int trapType) {
    switch (trapType) {
      case 1: return MonsterType.TRAP_LIGHTNING;
      case 2: case 6: case 8: case 9: return MonsterType.TRAP_FIREBOLT;
      case 3: return MonsterType.TRAP_POISONCLOUD;
      case 4: return MonsterType.TRAP_NOVA;
      default: return -1;
    }
  }

  static int monsterCount(int handlerType, long seed) {
    if (handlerType == 8 || handlerType == 9) {
      return new RandomXS128(seed).nextInt(2) + 1;
    }
    return monsterForTrapType(handlerType) < 0 ? 0 : 1;
  }

  static long trapSeed(NativeTrapInteractionEvent event, Position position) {
    long seed = event.entityId;
    seed = 31L * seed + event.objectClassId;
    seed = 31L * seed + event.trapType;
    seed = 31L * seed + Float.floatToIntBits(position.position.x);
    seed = 31L * seed + Float.floatToIntBits(position.position.y);
    return seed;
  }
}
