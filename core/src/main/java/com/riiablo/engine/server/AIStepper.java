package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.Aspect;
import com.artemis.EntitySubscription;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntSet;
import com.riiablo.engine.server.component.AIWrapper;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Size;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.RenderSystem;
import com.riiablo.skill.SkillCodes;

@Wire(failOnNull = false)
@All({AIWrapper.class, Position.class, Monster.class})
public class AIStepper extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(AIStepper.class);

  protected ComponentMapper<AIWrapper> mAIWrapper;
  protected ComponentMapper<Casting> mCasting;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<Sequence> mSequence;
  protected ComponentMapper<AnimData> mAnimData;
  protected ComponentMapper<Size> mSize;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Pathfind> mPathfind;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<Running> mRunning;

  private EntitySubscription players;
  private final IntSet dormant = new IntSet();

//  protected ComponentMapper<Interactable> mInteractable;
//  protected ComponentMapper<Size> mSize;

  protected RenderSystem renderer;

  @Override
  protected void initialize() {
    // D2MOO DRLGACTIVATE tracks clients, not potential combat targets. A dead
    // player therefore still keeps its current RoomEx and pRoomsNear in sight.
    players = world.getAspectSubscriptionManager().get(
        Aspect.all(Player.class, Position.class, MapWrapper.class));
  }

// NOTE: Handled by EntityFactory
//  @Override
//  protected void inserted(int entityId) {
//    Monster monster = mMonster.get(entityId);
//    AIWrapper aiWrapper = mAIWrapper.get(entityId);
//    AI ai = aiWrapper.ai = AI.findAI(entityId, monster);
//    world.getInjector().inject(ai);
//    ai.initialize();
//    if (monster.monstats.interact) {
//      mInteractable.create(entityId).set(mSize.get(entityId).size, ai);
//    }
//  }

  @Override
  protected void process(int entityId) {
    Monster monster = mMonster.get(entityId);
    if (monster.spawnZone != null && mMapWrapper.has(entityId)
        && mMapWrapper.get(entityId).zone != null
        && mMapWrapper.get(entityId).zone != monster.spawnZone) {
      // Native AI is room/activation scoped. Once a path accidentally crosses
      // a generated level seam, stop the authoritative movement immediately;
      // do not allow the monster to continue toward town or another level.
      if (mPathfind.has(entityId)) mPathfind.remove(entityId);
      if (mRunning.has(entityId)) mRunning.remove(entityId);
      if (mVelocity.has(entityId)) mVelocity.get(entityId).velocity.setZero();
      log.debug("[MONSTER_BOUNDARY] entity={} monster={} spawnZone={} currentZone={} action=halt",
          entityId, monster.monstats != null ? monster.monstats.Id : "unknown",
          monster.spawnZone.level != null ? monster.spawnZone.level.Id : -1,
          mMapWrapper.get(entityId).zone.level != null
              ? mMapWrapper.get(entityId).zone.level.Id : -1);
      return;
    }
    if (!isInClientRoomOrSight(entityId)) {
      sleep(entityId, monster);
      return;
    }
    if (dormant.remove(entityId)) {
      log.debug("[MONSTER_ROOM_ACTIVE] entity={} monster={} action=wake",
          entityId, monster.monstats != null ? monster.monstats.Id : "unknown");
    }
    // Native RoomEx activation supersedes the old renderer-radius shortcut.
    // Keep that shortcut only for legacy maps which have no pRoomsNear data.
    if (!hasNativeRoomTopology(entityId)
        && renderer != null && !renderer.withinRadius(mPosition.get(entityId).position)) return;
    boolean hadCasting = mCasting.has(entityId);
    int previousSkill = hadCasting ? mCasting.get(entityId).skillId : -1;
    int previousTarget = hadCasting ? mCasting.get(entityId).targetId : -1;

    AIWrapper wrapper = mAIWrapper.get(entityId);
    if (!wrapper.ai.updateSpecialAiControl(world.delta)) wrapper.ai.update(world.delta);

    if (!mCasting.has(entityId)) return;
    Casting casting = mCasting.get(entityId);
    if (hadCasting
        && casting.skillId == previousSkill
        && casting.targetId == previousTarget) {
      return;
    }

    byte currentMode = mCofReference.has(entityId) ? mCofReference.get(entityId).mode : -1;
    byte requestedMode = mSequence.has(entityId) ? mSequence.get(entityId).mode1 : -1;
    boolean melee = casting.skillId == SkillCodes.attack
        && Monster.isMeleeMode(requestedMode);
    String marker = melee ? "[MONSTER_MELEE]"
        : casting.skillId == SkillCodes.attack ? "[MONSTER_ACTION]" : "[MONSTER_SKILL]";
    Position sourcePosition = mPosition.get(entityId);
    Position targetPosition = mPosition.has(casting.targetId) ? mPosition.get(casting.targetId) : null;
    float dx = targetPosition != null ? targetPosition.position.x - sourcePosition.position.x : Float.NaN;
    float dy = targetPosition != null ? targetPosition.position.y - sourcePosition.position.y : Float.NaN;
    float distance = targetPosition != null ? sourcePosition.position.dst(targetPosition.position) : Float.NaN;
    int sourceSize = mSize.has(entityId) ? mSize.get(entityId).size : -1;
    int targetSize = mSize.has(casting.targetId) ? mSize.get(casting.targetId).size : -1;
    int meleeRange = monster.monstats2 != null ? monster.monstats2.MeleeRng : -1;
    log.info("{} phase=decision entity={} monster={} ai={} skill={} currentMode={} "
            + "requestedMode={} target={} replaced={} resurrected={} resurrectedBy={} playerRevive={} "
            + "pos=({}, {}) targetPos=({}, {}) delta=({}, {}) distance={} meleeRng={} sourceSize={} "
            + "targetSize={} hasSequence={} sequenceStarted={} hasAnimData={} animFrame={} animFrames={} "
            + "keyframes={} hasPathfind={} velocity={}",
        marker,
        entityId,
        monster.monstats != null ? monster.monstats.Id : "unknown",
        wrapper.ai.getClass().getSimpleName(),
        casting.skillId,
        Monster.modeName(currentMode),
        Monster.modeName(requestedMode),
        casting.targetId,
        hadCasting,
        monster.resurrected,
        monster.resurrectedBy,
        monster.playerRevive,
        sourcePosition.position.x, sourcePosition.position.y,
        targetPosition != null ? targetPosition.position.x : Float.NaN,
        targetPosition != null ? targetPosition.position.y : Float.NaN,
        dx, dy, distance, meleeRange, sourceSize, targetSize,
        mSequence.has(entityId), mSequence.has(entityId) && mSequence.get(entityId).started,
        mAnimData.has(entityId), mAnimData.has(entityId) ? mAnimData.get(entityId).frame : -1,
        mAnimData.has(entityId) ? mAnimData.get(entityId).numFrames : -1,
        mAnimData.has(entityId) && mAnimData.get(entityId).keyframes != null
            ? mAnimData.get(entityId).keyframes.length : 0,
        mPathfind.has(entityId),
        mVelocity.has(entityId) ? mVelocity.get(entityId).velocity.len() : 0f);
  }

  /**
   * Mirrors the AI-relevant part of D2MOO DRLGACTIVATE_ChangeClientRoom:
   * CLIENT_IN_ROOM and the direct pRoomsNear CLIENT_IN_SIGHT ring are active.
   * Maps without exported native topology retain the legacy always-active path.
   */
  boolean isInClientRoomOrSight(int entityId) {
    if (!mMapWrapper.has(entityId)) return true;
    MapWrapper source = mMapWrapper.get(entityId);
    if (source.zone == null || !source.zone.hasNativeRoomTopology()) return true;
    if (source.zone.isRoomActivationTracking()) {
      return source.zone.isRoomActiveForAI(
          mPosition.get(entityId).position.x, mPosition.get(entityId).position.y);
    }

    IntBag entities = players.getEntities();
    int[] data = entities.getData();
    for (int i = 0, size = entities.size(); i < size; i++) {
      int playerId = data[i];
      MapWrapper target = mMapWrapper.get(playerId);
      if (source.map != null && target.map != null && source.map != target.map) continue;
      if (target.zone != source.zone) continue;
      if (source.zone.areRoomsAdjacent(
          mPosition.get(entityId).position.x, mPosition.get(entityId).position.y,
          mPosition.get(playerId).position.x, mPosition.get(playerId).position.y)) {
        return true;
      }
    }
    return false;
  }

  private boolean hasNativeRoomTopology(int entityId) {
    if (!mMapWrapper.has(entityId)) return false;
    MapWrapper wrapper = mMapWrapper.get(entityId);
    return wrapper.zone != null && wrapper.zone.hasNativeRoomTopology();
  }

  private void sleep(int entityId, Monster monster) {
    // Once a RoomEx leaves CLIENT_IN_SIGHT, its units no longer receive normal
    // game processing in D2MOO. Clear movement owned by the previous AI action
    // so other ECS movement systems cannot keep sliding a dormant monster.
    if (mPathfind.has(entityId)) mPathfind.remove(entityId);
    if (mRunning.has(entityId)) mRunning.remove(entityId);
    if (mVelocity.has(entityId)) mVelocity.get(entityId).velocity.setZero();
    if (dormant.add(entityId)) {
      MapWrapper wrapper = mMapWrapper.has(entityId) ? mMapWrapper.get(entityId) : null;
      com.riiablo.map.Map.RoomEx room = wrapper != null && wrapper.zone != null
          ? wrapper.zone.findRoomEx(
              mPosition.get(entityId).position.x, mPosition.get(entityId).position.y)
          : null;
      log.debug("[MONSTER_ROOM_DORMANT] entity={} monster={} level={} room={} clients={} action=sleep",
          entityId,
          monster.monstats != null ? monster.monstats.Id : "unknown",
          wrapper != null && wrapper.zone != null && wrapper.zone.level != null
              ? wrapper.zone.level.Id : -1,
          room != null ? room.id : -1,
          players != null ? players.getEntities().size() : 0);
    }
  }

  @Override
  protected void removed(int entityId) {
    dormant.remove(entityId);
  }
}
