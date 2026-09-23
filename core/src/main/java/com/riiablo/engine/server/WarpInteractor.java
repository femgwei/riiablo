package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import net.mostlyoriginal.api.system.core.PassiveSystem;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;

import com.riiablo.codec.excel.LvlWarp;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Box2DBody;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Size;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Warp;
import com.riiablo.engine.server.event.ZoneChangeEvent;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.map.Map;
import com.riiablo.engine.server.quest.QuestWarp;
import com.riiablo.engine.server.quest.Act5BaalQuest;
import com.riiablo.engine.server.quest.QuestWarpPolicy;
import com.riiablo.Riiablo;
import com.riiablo.save.D2SWriter;
import com.riiablo.net.packet.d2gs.QuestOperation;
import net.mostlyoriginal.api.event.common.EventSystem;

@Wire(failOnNull = false)
public class WarpInteractor extends PassiveSystem implements Interactable.Interactor {
  private static final String TAG = "WarpInteractor";

  protected ComponentMapper<Warp> mWarp;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Box2DBody> mBox2DBody;
  protected ComponentMapper<Size> mSize;
  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<Player> mPlayer;

  /** Dynamic unit footprints used by movement/pathing.  A warp bypasses the
   * normal movement step, so it must consult the same grid before committing
   * the destination position. */
  @Wire(failOnNull = false)
  protected DynamicUnitCollisionSystem dynamicCollision;
  @Wire(failOnNull = false)
  protected RoomActivationSystem roomActivation;

  protected Pathfinder pathfinder;
  protected Actioneer actioneer;
  protected EventSystem events;
  @Wire(failOnNull = false)
  protected com.riiablo.engine.client.ClientNetworkSynchronizer clientNetwork;

  @Wire(name = "map")
  protected Map map;

  private final Vector2 tmpVec2 = new Vector2();

  @Override
  public void interact(int src, int entity) {
    if (clientNetwork != null) {
      long requestId = clientNetwork.requestQuest(
          QuestOperation.WARP_INTERACTION, entity, -1);
      if (requestId != 0L) {
        Gdx.app.log(TAG, "Warp interaction requested: player=" + src
            + " entity=" + entity + " request=" + requestId);
      }
      return;
    }
    warp(src, entity);
  }

  /** Performs one validated server/local authoritative warp transaction. */
  public boolean warp(int src, int entity) {
    Warp warp = mWarp.get(entity);
    MapWrapper sourceWrapper = mMapWrapper.get(entity);
    Map.Zone source = sourceWrapper == null ? null : sourceWrapper.zone;
    Map.Zone dst = warp == null ? null : map.findZone(warp.dstLevel);
    if (warp == null || source == null || dst == null) {
      Gdx.app.error(TAG, "Warp interaction missing map data: player=" + src
          + " entity=" + entity + " warp=" + warp + " source=" + source + " dst=" + dst);
      return false;
    }
    if (warp.linkedTownPortal != Engine.INVALID_ENTITY && mWarp.has(warp.linkedTownPortal)) {
      return warpToLinkedTownPortal(src, entity, source, warp.linkedTownPortal);
    }
    if (QuestWarp.isQuestWarp(warp.index)) {
      Player player = mPlayer == null ? null : mPlayer.get(src);
      String rejection = QuestWarpPolicy.rejectionReason(
          player == null ? null : player.data,
          source.level == null ? -1 : source.level.Id,
          warp.dstLevel.Id);
      if (rejection != null) {
        Gdx.app.log(TAG, "Quest warp rejected: player=" + src + " source="
            + (source.level == null ? -1 : source.level.Id) + " destination="
            + warp.dstLevel.Id + " reason=" + rejection);
        return false;
      }
      int unitSize = mSize != null && mSize.has(src) ? mSize.get(src).size : Size.MEDIUM;
      prewarmDestination(dst, new Vector2(
          dst.x() + Math.max(1, dst.width() / 2f),
          dst.y() + Math.max(1, dst.height() / 2f)));
      Vector2 arrival = findQuestArrival(src, dst, unitSize);
      if (arrival == null) {
        Gdx.app.error(TAG, "Quest warp destination has no free coordinates: player=" + src
            + " destination=" + dst.level.LevelName + "(" + dst.level.Id + ")"
            + " unitSize=" + unitSize);
        return false;
      }
      commitTransition(src, dst, arrival);
      markA5Q6LastPortalUsed(src, source, warp);
      Gdx.app.log(TAG, "Quest warp interaction: player=" + src
          + " source=" + source.level.LevelName + "(" + source.level.Id + ")"
          + " destination=" + dst.level.LevelName + "(" + dst.level.Id + ")"
          + " arrival=" + arrival);
      return true;
    }
    int dstIndex = source.getWarp(warp.index);
    if (dstIndex < 0) {
      // Reduced A5 DS1 exports may omit the reverse special-id mapping even
      // though the destination Warp entity carries the authoritative level.
      // Resolve an unambiguous reverse marker from that entity metadata.
      for (int i = 0; i < dst.getWarpEntities().size; i++) {
        int candidateId = dst.getWarpEntities().get(i);
        Warp candidate = mWarp.get(candidateId);
        if (candidate != null && candidate.dstLevel != null
            && source.level != null && candidate.dstLevel.Id == source.level.Id) {
          dstIndex = candidate.index;
          break;
        }
      }
    }
    int dstWarpEntity = dst.findWarp(dstIndex);
    if (dstWarpEntity == Engine.INVALID_ENTITY) {
      Gdx.app.error(TAG, "Warp destination entity missing: source=" + source.level.LevelName
          + "(" + source.level.Id + ") special=0x" + Integer.toHexString(warp.index)
          + " destination=" + dst.level.LevelName + "(" + dst.level.Id + ")"
          + " reverseSpecial=0x" + Integer.toHexString(dstIndex));
      return false;
    }
    Vector2 dstWarpPos = mPosition.get(dstWarpEntity).position;
    int unitSize = mSize != null && mSize.has(src) ? mSize.get(src).size : Size.MEDIUM;
    prewarmDestination(dst, dstWarpPos);
    if (!findArrivalAvoidingUnits(src, dst, dstWarpPos, unitSize, 50, tmpVec2)) {
      // Synthetic A5 markers may inherit a LvlWarp offset that places the
      // entity just outside a zero-padded/reduced Zone export. Retry from the
      // Zone interior so the authoritative transition remains usable.
      int centerX = dst.x() + Math.max(1, dst.width() / 2);
      int centerY = dst.y() + Math.max(1, dst.height() / 2);
      Vector2 interior = new Vector2(centerX, centerY);
      if (dst.contains(centerX, centerY)
          && findArrivalAvoidingUnits(src, dst, interior, unitSize, 0, tmpVec2)) {
        dstWarpPos = tmpVec2;
      } else if (dst.level != null && dst.level.Id >= 109 && dst.level.Id <= 132) {
        // Native A5 barricade exports can be metadata-only (zero-sized
        // collision grid). Keep the level transition authoritative and place
        // the player at its origin rather than rejecting the Warp outright.
        dstWarpPos = tmpVec2.set(dst.x(), dst.y());
      } else {
      Gdx.app.error(TAG, "Warp destination has no free coordinates: source="
          + source.level.LevelName + "(" + source.level.Id + ")"
          + " destination=" + dst.level.LevelName + "(" + dst.level.Id + ")"
          + " reverseSpecial=0x" + Integer.toHexString(dstIndex)
          + " destinationPosition=" + dstWarpPos + " unitSize=" + unitSize);
      return false;
      }
    }
    float arrivalX = tmpVec2.x;
    float arrivalY = tmpVec2.y;
    Vector2 position = mPosition.get(src).position;
    Gdx.app.log(TAG, "Warp interaction: player=" + src
        + " source=" + source.level.LevelName + "(" + source.level.Id + ")"
        + " special=0x" + Integer.toHexString(warp.index)
        + " sourcePosition=" + position
        + " destination=" + dst.level.LevelName + "(" + dst.level.Id + ")"
        + " reverseSpecial=0x" + Integer.toHexString(dstIndex)
        + " destinationPosition=" + dstWarpPos
        + " freeArrival=(" + arrivalX + "," + arrivalY + ")");
    commitTransition(src, dst, tmpVec2.set(arrivalX, arrivalY));

    Warp dstWarp = mWarp.get(dstWarpEntity);
    LvlWarp.Entry dstWarpEntry = dstWarp.warp;
    tmpVec2.set(arrivalX, arrivalY).add(dstWarpEntry.ExitWalkX, dstWarpEntry.ExitWalkY);
    actioneer.moveTo(src, tmpVec2);
    return true;
  }

  /** Uses the exact paired endpoint registered for an ordinary player TP. */
  private boolean warpToLinkedTownPortal(int src, int sourceEntity, Map.Zone source,
      int destinationEntity) {
    MapWrapper destinationWrapper = mMapWrapper.get(destinationEntity);
    Warp destinationWarp = mWarp.get(destinationEntity);
    Map.Zone destination = destinationWrapper == null ? null : destinationWrapper.zone;
    Position destinationPosition = mPosition.get(destinationEntity);
    if (destination == null || destinationWarp == null || destinationPosition == null) {
      Gdx.app.error(TAG, "Town portal pair endpoint missing: source=" + sourceEntity
          + " destination=" + destinationEntity);
      return false;
    }
    int unitSize = mSize != null && mSize.has(src) ? mSize.get(src).size : Size.MEDIUM;
    Vector2 arrival = new Vector2(destinationPosition.position);
    prewarmDestination(destination, arrival);
    if (!destination.findFreeCoordinates(arrival, unitSize, 50, true, tmpVec2)) {
      Gdx.app.error(TAG, "Town portal destination has no free coordinates: player=" + src
          + " destination=" + destination.level.LevelName + "(" + destination.level.Id + ")"
          + " endpoint=" + destinationEntity);
      return false;
    }
    commitTransition(src, destination, tmpVec2);
    actioneer.moveTo(src, tmpVec2);
    Gdx.app.log(TAG, "Town portal interaction: player=" + src
        + " source=" + (source.level == null ? -1 : source.level.Id)
        + " destination=" + (destination.level == null ? -1 : destination.level.Id)
        + " endpoint=" + destinationEntity + " arrival=" + tmpVec2);
    return true;
  }

  /** Mirrors OBJECTS_OperateFunction72_LastPortal's CUSTOM6 transition. */
  private void markA5Q6LastPortalUsed(int playerId, Map.Zone source, Warp warp) {
    if (warp == null || source == null || source.level == null
        || warp.dstLevel == null || warp.dstLevel.Id != Act5BaalQuest.HARROGATH
        || source.level.Id != Act5BaalQuest.WORLDSTONE_CHAMBER || mPlayer == null) return;
    Player player = mPlayer.get(playerId);
    if (player == null || player.data == null) return;
    short[] records = player.data.getQuests(Riiablo.ACT5);
    if (records == null || records.length <= Act5BaalQuest.RECORD) return;
    short previous = records[Act5BaalQuest.RECORD];
    short next = com.riiablo.engine.server.quest.NativeQuestRecord.set(
        previous, com.riiablo.engine.server.quest.NativeQuestRecord.CUSTOM6);
    if (next == previous) return;
    records[Act5BaalQuest.RECORD] = next;
    if (player.data.managed && Riiablo.saves != null) D2SWriter.INSTANCE.save(player.data);
    Gdx.app.log(TAG, "A5Q6 last portal completed: player=" + playerId
        + " record=0x" + Integer.toHexString(Short.toUnsignedInt(next)));
  }

  /**
   * Commits every authoritative warp side effect before the next network
   * snapshot: movement intent, position/body, level/RoomEx and warp marker.
   */
  private void commitTransition(int entityId, Map.Zone destination, Vector2 arrival) {
    actioneer.moveTo(entityId, Engine.INVALID_ENTITY);

    Vector2 position = mPosition.get(entityId).position;
    position.set(arrival);

    MapWrapper wrapper = mMapWrapper.has(entityId) ? mMapWrapper.get(entityId) : null;
    if (wrapper == null) wrapper = mMapWrapper.create(entityId);
    wrapper.set(map, destination);
    Map.RoomEx room = destination.findRoomEx(arrival.x, arrival.y);
    wrapper.roomId = room == null ? -1 : room.id;

    Box2DBody box2dWrapper = mBox2DBody.has(entityId) ? mBox2DBody.get(entityId) : null;
    if (box2dWrapper != null && box2dWrapper.body != null) {
      box2dWrapper.body.setTransform(position, box2dWrapper.body.getAngle());
    }

    UnitStates states = mUnitStates.has(entityId) ? mUnitStates.get(entityId) : null;
    if (states != null) {
      if (states.stateList == null) states.init(entityId);
      states.stateList.addState(StateId.SYNC_WARPED, 2, 1, entityId);
    }
    if (events != null) events.dispatch(ZoneChangeEvent.obtain(entityId, destination));
  }

  private Vector2 findQuestArrival(int moverId, Map.Zone destination, int unitSize) {
    int centerX = destination.x() + destination.width() / 2;
    int centerY = destination.y() + destination.height() / 2;
    // Native quest rooms are often irregular and their geometric center can
    // land on a wall/void.  D2Common expands the search over the destination
    // room graph rather than failing the portal outright; mirror that by
    // trying a wider radius first and then each exported RoomEx center.
    return findArrivalAvoidingUnits(moverId, destination,
        tmpVec2.set(centerX, centerY), unitSize, 200, tmpVec2)
        ? tmpVec2 : findQuestRoomArrival(moverId, destination, unitSize);
  }

  private Vector2 findQuestRoomArrival(int moverId, Map.Zone destination, int unitSize) {
    com.badlogic.gdx.utils.Array<Map.RoomEx> rooms = destination.getRoomsEx();
    for (int i = 0; i < rooms.size; i++) {
      Map.RoomEx room = rooms.get(i);
      int x = room.x + room.width / 2;
      int y = room.y + room.height / 2;
      if (findArrivalAvoidingUnits(moverId, destination, tmpVec2.set(x, y),
          unitSize, 32, tmpVec2)) return tmpVec2;
    }
    return null;
  }

  /**
   * Finds a static-collision-free arrival which is also free in the dynamic
   * unit grid.  Warp transitions write the player's position directly, so
   * calling only Zone.findFreeCoordinates can land on a monster that was
   * already spawned in the destination room.  That leaves the player inside
   * a dynamic footprint and movement remains blocked until the monster dies.
   */
  private boolean findArrivalAvoidingUnits(int moverId, Map.Zone destination,
      Vector2 anchor, int unitSize, int maxDistance, Vector2 result) {
    if (destination == null || anchor == null || result == null) return false;
    int originX = Map.round(anchor.x);
    int originY = Map.round(anchor.y);
    int limit = Math.max(0, maxDistance);
    for (int radius = 0; radius <= limit; radius++) {
      if (radius == 0) {
        if (isArrivalFree(moverId, destination, originX, originY, unitSize, result)) {
          return true;
        }
        continue;
      }
      for (int dx = -radius; dx <= radius; dx++) {
        if (isArrivalFree(moverId, destination, originX + dx, originY - radius,
            unitSize, result)
            || isArrivalFree(moverId, destination, originX + dx, originY + radius,
                unitSize, result)) return true;
      }
      for (int dy = -radius + 1; dy < radius; dy++) {
        if (isArrivalFree(moverId, destination, originX - radius, originY + dy,
            unitSize, result)
            || isArrivalFree(moverId, destination, originX + radius, originY + dy,
                unitSize, result)) return true;
      }
    }
    return false;
  }

  /**
   * Materializes the destination entrance sight ring before selecting the
   * player's arrival coordinate.  Otherwise RoomActivationSystem can create
   * a deferred monster population one tick after the warp and place it on the
   * player, leaving the dynamic path grid blocked at its source cell.
   */
  private void prewarmDestination(Map.Zone destination, Vector2 anchor) {
    if (roomActivation != null && destination != null && anchor != null
        && destination.hasNativeRoomTopology()) {
      roomActivation.prewarmZoneAt(destination, anchor.x, anchor.y);
    }
    if (dynamicCollision != null) dynamicCollision.rebuildNow();
  }

  private boolean isArrivalFree(int moverId, Map.Zone destination, int x, int y,
      int unitSize, Vector2 result) {
    if (!destination.findFreeCoordinates(tmpVec2.set(x, y), unitSize, 0,
        true, result)) return false;
    if (dynamicCollision != null
        && !dynamicCollision.isFreeForPath(moverId, -1,
            Map.round(result.x), Map.round(result.y), unitSize)) return false;
    return true;
  }
}
