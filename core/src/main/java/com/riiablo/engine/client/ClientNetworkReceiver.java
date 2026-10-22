package com.riiablo.engine.client;

import com.riiablo.presentation.ClientEntityFactory;
import com.riiablo.presentation.ItemEffectManager;

import com.riiablo.presentation.PlayerHitSound;
import com.riiablo.presentation.ClientGameplayContext;

import com.google.flatbuffers.Table;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IntervalSystem;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.net.Socket;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.TimeUtils;

import com.riiablo.Riiablo;
import com.riiablo.engine.SimulationClock;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Dirty;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.Actioneer;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Box2DBody;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofAlphas;
import com.riiablo.engine.server.component.CofComponents;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.CofTransforms;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.shared.component.Selectable;
import com.riiablo.presentation.component.Label;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.event.MissileImpactEvent;
import com.riiablo.engine.server.event.SoundEvent;
import com.riiablo.item.Item;
import com.riiablo.item.ItemReader;
import com.riiablo.map.EnvironmentCycle;
import com.riiablo.map.Map;
import com.riiablo.net.packet.d2gs.AngleP;
import com.riiablo.net.packet.d2gs.ClassP;
import com.riiablo.net.packet.d2gs.CofAlphasP;
import com.riiablo.net.packet.d2gs.CofComponentsP;
import com.riiablo.net.packet.d2gs.CofTransformsP;
import com.riiablo.net.packet.d2gs.CofReferenceP;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.Connection;
import com.riiablo.net.packet.d2gs.D2GS;
import com.riiablo.net.packet.d2gs.D2GSData;
import com.riiablo.net.packet.d2gs.DS1ObjectWrapperP;
import com.riiablo.net.packet.d2gs.Disconnect;
import com.riiablo.net.packet.d2gs.EntityFlags;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.net.packet.d2gs.GoldResult;
import com.riiablo.net.packet.d2gs.ItemP;
import com.riiablo.net.packet.d2gs.MonsterP;
import com.riiablo.net.packet.d2gs.SummonedPetP;
import com.riiablo.net.packet.d2gs.Ping;
import com.riiablo.net.packet.d2gs.NpcServiceResult;
import com.riiablo.net.packet.d2gs.PartyResult;
import com.riiablo.net.packet.d2gs.TradeResult;
import com.riiablo.net.packet.d2gs.PlayerP;
import com.riiablo.net.packet.d2gs.PlayerLifecycleResult;
import com.riiablo.net.packet.d2gs.QuestResult;
import com.riiablo.net.packet.d2gs.CastSkillResult;
import com.riiablo.net.packet.d2gs.SpendSkillPointResult;
import com.riiablo.net.packet.d2gs.SpendStatPointResult;
import com.riiablo.net.packet.d2gs.PositionP;
import com.riiablo.net.packet.d2gs.ItemMoveResult;
import com.riiablo.net.packet.d2gs.VelocityP;
import com.riiablo.net.packet.d2gs.VitalsP;
import com.riiablo.net.packet.d2gs.MissileP;
import com.riiablo.net.packet.d2gs.MissileImpactP;
import com.riiablo.net.packet.d2gs.SoundP;
import com.riiablo.net.packet.d2gs.WarpP;
import com.riiablo.net.packet.d2gs.StateP;
import com.riiablo.net.packet.d2gs.SnapshotBaseline;
import com.riiablo.net.packet.d2gs.SnapshotBaselinePhase;
import com.riiablo.net.SizePrefixedPacketAccumulator;
import com.riiablo.save.CharData;
import com.riiablo.engine.server.quest.QuestSnapshot;
import com.riiablo.util.ArrayUtils;
import com.riiablo.util.DebugUtils;
import java.util.function.Consumer;
import net.mostlyoriginal.api.event.common.EventSystem;

@All
public class ClientNetworkReceiver extends IntervalSystem {
  private static final String TAG = "ClientNetworkReceiver";
  private static final boolean DEBUG         = true;
  private static final boolean DEBUG_PACKET  = DEBUG && !true;
  private static final boolean DEBUG_SYNC    = DEBUG && !true;

  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<CofComponents> mCofComponents;
  protected ComponentMapper<CofTransforms> mCofTransforms;
  protected ComponentMapper<CofAlphas> mCofAlphas;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<Angle> mAngle;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<Missile> mMissile;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<com.riiablo.engine.server.component.Item> mItem;
  protected ComponentMapper<com.riiablo.engine.server.component.Object> mObject;
  protected ComponentMapper<Box2DBody> mBox2DBody;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<Selectable> mSelectable;
  protected ComponentMapper<Label> mLabel;
  protected ComponentMapper<SummonedPet> mSummonedPet;
  protected ComponentMapper<com.riiablo.engine.server.component.Mercenary> mMercenary;
  private com.riiablo.engine.server.NativeHirelingExperienceTable clientHirelingTable;

  protected CofManager cofs;
  protected NetworkIdManager syncIds;
  protected Pinger pinger;
  protected EventSystem events;
  protected AuthoritativeInterpolationSystem interpolation;
  protected ClientNetworkSynchronizer networkSynchronizer;
  @Wire(name="clientSession") protected ClientNetworkSession clientSession;
  @Wire(name="clientContext") protected ClientGameplayContext clientContext = ClientGameplayContext.fromGlobals();

  @Wire(name = "environment")
  protected EnvironmentCycle environment;

  @Wire(name="client.socket")
  protected Socket socket;

  @Wire(name = "factory")
  protected EntityFactory factory;

  @Wire(name = "map")
  protected Map map;

  @Wire(name = "clientMessages", failOnNull = false)
  protected Consumer<String> output;

  protected static final ItemReader itemReader = new ItemReader(); // TODO: inject

  private static final int NETWORK_READ_BUFFER_SIZE = 1 << 16;
  private static final int MAX_NETWORK_PACKET_SIZE = 1 << 22;
  private final byte[] networkReadBuffer = new byte[NETWORK_READ_BUFFER_SIZE];
  private final SizePrefixedPacketAccumulator packets =
      new SizePrefixedPacketAccumulator(
          NETWORK_READ_BUFFER_SIZE, MAX_NETWORK_PACKET_SIZE,
          MAX_NETWORK_PACKET_SIZE + NETWORK_READ_BUFFER_SIZE + Integer.BYTES);
  private final EntitySync sync = new EntitySync();
  private final AuthoritativeSnapshotTimeline snapshotTimeline =
      new AuthoritativeSnapshotTimeline();
  private final AuthoritativeTransformState authoritativeTransforms =
      new AuthoritativeTransformState();
  private final AuthoritativeUnitLifecycleState authoritativeLifecycles =
      new AuthoritativeUnitLifecycleState();
  private final AuthoritativeInventoryState authoritativeInventory =
      new AuthoritativeInventoryState();
  private final AuthoritativeQuestState authoritativeQuests =
      new AuthoritativeQuestState();
  private final AuthoritativePlayerItemState authoritativePlayerItems = new AuthoritativePlayerItemState();
  private final AuthoritativeGroundItemState authoritativeGroundItems = new AuthoritativeGroundItemState();
  private final AuthoritativeMercenaryState authoritativeMercenaries =
      new AuthoritativeMercenaryState();
  private final AuthoritativeMapState authoritativeMaps = new AuthoritativeMapState();
  /** Last authoritative deletion tick per server entity.  Deletion packets
   * can be duplicated or arrive after a RoomEx re-entry; retaining this
   * watermark prevents an old delete from removing a newly rebuilt entity
   * that reused the same server id. */
  private final HashMap<Integer, Long> lastDeletedServerTicks = new HashMap<>();
  private final IntSet deferredServerEntities = new IntSet();
  /** Last authoritative level observed for each server entity. */
  private final IntIntMap serverEntityLevels = new IntIntMap();
  private final IntIntMap pendingMercenaryOwners = new IntIntMap();
  private final ClientPartyState partyState = new ClientPartyState();
  private final ClientTradeState tradeState = new ClientTradeState();
  private long latestServerTick;
  private long latestServerTickReceiptMillis;
  private long lastCombatSequence;
  private boolean lastCombatAccepted;
  private boolean snapshotResyncInProgress;
  private boolean snapshotResyncPending;
  private long lastResyncRequestMillis;
  private long lastResyncObservedTick;
  private long baselineBeginTick;
  private final SnapshotBaselineTransaction baselineTransaction =
      new SnapshotBaselineTransaction();
  /** Current level of the local player; -1 until the first authoritative snapshot. */
  private int localLevelId = -1;

  public ClientNetworkReceiver() {
    super(null, SimulationClock.STEP_SECONDS);
  }

  /** Last committed wire transform; prediction and rendering cannot edit it. */
  public AuthoritativeTransformState.Snapshot authoritativeTransform(int entityId) {
    return authoritativeTransforms.get(entityId);
  }

  public AuthoritativeUnitLifecycleState.Snapshot authoritativeLifecycle(int entityId) {
    return authoritativeLifecycles.get(entityId);
  }

  public AuthoritativeInventoryState.Snapshot authoritativeInventory() { return authoritativeInventory.snapshot(); }
  public AuthoritativeQuestState.Snapshot authoritativeQuests() { return authoritativeQuests.snapshot(); }
  public AuthoritativePlayerItemState.Snapshot authoritativePlayerItems() { return authoritativePlayerItems.snapshot(); }
  public AuthoritativeGroundItemState.Snapshot authoritativeGroundItem(int entity) { return authoritativeGroundItems.get(entity); }
  public AuthoritativeMercenaryState.Snapshot authoritativeMercenary(int entity) {
    return authoritativeMercenaries.get(entity);
  }
  public AuthoritativeMapState.Snapshot authoritativeMap(int entity) {
    return authoritativeMaps.get(entity);
  }

  private void removeTransform(int entityId) {
    authoritativeGroundItems.remove(entityId);
    authoritativeMercenaries.remove(entityId);
    authoritativeMaps.remove(entityId);
    authoritativeLifecycles.remove(entityId);
    authoritativeTransforms.remove(entityId);
    if (interpolation != null) interpolation.remove(entityId);
  }

  @Override protected void dispose() {
    authoritativeTransforms.clear();
    authoritativeLifecycles.clear();
    authoritativeInventory.clear();
    authoritativeQuests.clear();
    authoritativePlayerItems.clear();
    authoritativeGroundItems.clear();
    authoritativeMercenaries.clear();
    authoritativeMaps.clear();
    if (interpolation != null) interpolation.clear();
    super.dispose();
  }

  /** Latest accepted authoritative tick, or zero before the first snapshot. */
  public long latestServerTick() {
    if (latestServerTick == 0L) return 0L;
    long elapsed = Math.max(0L, TimeUtils.millis() - latestServerTickReceiptMillis);
    return latestServerTick + elapsed / SimulationClock.STEP_MILLIS;
  }

  @Override
  protected void processSystem() {
    if (snapshotResyncPending && !snapshotResyncInProgress) {
      requestSnapshotResync(lastResyncObservedTick, "snapshot_retry");
    }
    if (!snapshotResyncInProgress && latestServerTick > 0L
        && TimeUtils.millis() - latestServerTickReceiptMillis > 3000L) {
      requestSnapshotResync(snapshotTimeline.tick(), "snapshot_silence");
    }
    InputStream in = socket.getInputStream();
    try {
      if (in.available() > 0) {
        int packetsProcessed = 0;
        int bytesRead = 0;
        int available;
        do {
          available = in.available();
          if (available <= 0) break;
          int read = in.read(networkReadBuffer, 0,
              Math.min(available, networkReadBuffer.length));
          if (read < 0) {
            Gdx.app.log(TAG, "[NET_FRAME] phase=closed reason=end_of_stream");
            setEnabled(false);
            return;
          }
          if (read == 0) break;
          bytesRead += read;
          packets.append(networkReadBuffer, 0, read);
          packetsProcessed += packets.drain(frame -> {
            try {
              D2GS d2gs = D2GS.getRootAsD2GS(frame);
              if (DEBUG_PACKET) Gdx.app.debug(TAG,
                  "[NET_FRAME] phase=packet size=" + frame.remaining()
                      + " type=" + D2GSData.name(d2gs.dataType()));
              process(d2gs);
            } catch (Throwable t) {
              // One unsupported entity must not block every valid packet
              // already following it in the same TCP read.
              Gdx.app.error(TAG, "[NET_FRAME] phase=packet_error size="
                  + frame.remaining() + " action=skip", t);
            }
          });
        } while (available > 0);

        if (packets.pendingBytes() > 0) {
          Gdx.app.debug(TAG, "[NET_FRAME] phase=partial bytesRead=" + bytesRead
              + " buffered=" + packets.pendingBytes()
              + " expected=" + packets.expectedFrameSize()
              + " packets=" + packetsProcessed);
        }
      }
    } catch (SizePrefixedPacketAccumulator.InvalidFrameException t) {
      Gdx.app.error(TAG, "[NET_FRAME] phase=reject size=" + t.frameSize
          + " action=disconnect", t);
      setEnabled(false);
    } catch (Throwable t) {
      Gdx.app.error(TAG, t.getMessage(), t);
    }
    resolveMercenaryOwners();
  }

  private void process(D2GS packet) {
    switch (packet.dataType()) {
      case D2GSData.Connection:
        Connection(packet);
        break;
      case D2GSData.Disconnect:
        Disconnect(packet);
        break;
      case D2GSData.Ping:
        pinger.Ping((Ping) packet.data(new Ping()));
        break;
      case D2GSData.EntitySync:
        Synchronize(packet);
        break;
      case D2GSData.MissileImpactP:
        MissileImpactP impact = (MissileImpactP) packet.data(new MissileImpactP());
        if (events != null) {
          events.dispatch(MissileImpactEvent.obtain(impact.missileEntityId(),
              impact.missileId(), impact.ownerId(), impact.targetEntityId(),
              new Vector2(impact.x(), impact.y()),
              new Vector2(impact.dx(), impact.dy())));
        }
        break;
      case D2GSData.SoundP:
        SoundP sound = (SoundP) packet.data(new SoundP());
        if (events != null) {
          SoundEvent event = SoundEvent.obtain(
              sound.entityId(), sound.sound(), sound.global());
          if (sound.positionPresent()) event.withPosition(sound.x(), sound.y());
          events.dispatch(event);
        }
        break;
      case D2GSData.SnapshotBaseline:
        SnapshotBaseline(packet);
        break;
      case D2GSData.CastSkillResult:
        CastSkillResult(packet);
        break;
      case D2GSData.GroundToCursor:
      case D2GSData.CursorToGround:
      case D2GSData.StoreToCursor:
      case D2GSData.CursorToStore:
      case D2GSData.SwapStoreItem:
      case D2GSData.BodyToCursor:
      case D2GSData.CursorToBody:
      case D2GSData.SwapBodyItem:
      case D2GSData.BeltToCursor:
      case D2GSData.CursorToBelt:
      case D2GSData.SwapBeltItem:
        // Legacy echoes describe an action, not a complete ownership result.
        requestSnapshotResync(snapshotTimeline.tick(), "legacy_inventory_echo");
        break;
      case D2GSData.NpcServiceResult:
        NpcServiceResult(packet);
        break;
      case D2GSData.PartyResult:
        PartyResult(packet);
        break;
      case D2GSData.TradeResult:
        TradeResult(packet);
        break;
      case D2GSData.ItemMoveResult:
        ItemMoveResult(packet);
        break;
      case D2GSData.GoldResult:
        GoldResult(packet);
        break;
      case D2GSData.SpendSkillPointResult:
        SpendSkillPointResult(packet);
        break;
      case D2GSData.SpendStatPointResult:
        SpendStatPointResult(packet);
        break;
      case D2GSData.PlayerLifecycleResult:
        PlayerLifecycleResult(packet);
        break;
      case D2GSData.QuestResult:
        QuestResult(packet);
        break;
      default:
        Gdx.app.error(TAG, "Unknown packet type: " + packet.dataType());
    }
  }

  private void Connection(D2GS packet) {
    Connection connection = (Connection) packet.data(new Connection());
    String charName = connection.charName();
    int charClass = connection.charClass();

    if (output != null && clientContext.strings() != null) {
      output.accept(clientContext.strings().format(3641, charName));
    }
    if (output != null) output.accept("\n");

    Vector2 origin = map.find(Map.ID.TOWN_ENTRY_1);
    if (origin == null) origin = map.find(Map.ID.TOWN_ENTRY_2);
    if (origin == null) origin = map.find(Map.ID.TP_LOCATION);
    int entityId = factory.createPlayer(charName, charClass, origin.x, origin.y);
    syncIds.put(connection.entityId(), entityId);
    int[] component = mCofComponents.get(entityId).component;
//    for (int i = 0; i < 16; i++) component[i] = connection.cofComponents(i);
    float[] alpha = mCofAlphas.get(entityId).alpha;
//    for (int i = 0; i < 16; i++) alpha[i] = connection.cofAlphas(i) / 255f;
    byte[] transform = mCofTransforms.get(entityId).transform;
//    for (int i = 0; i < 16; i++) transform[i] = (byte) connection.cofTransforms(i);

    int alphaFlags = Dirty.NONE;
    int transformFlags = Dirty.NONE;
    for (int i = 0; i < 16; i++) {
      cofs.setComponent(entityId, i, connection.cofComponents(i));
    }
    for (int i = 0; i < 16; i++) {
      alphaFlags |= cofs.setAlpha(entityId, i, connection.cofAlphas(i) / 255f);
      transformFlags |= cofs.setTransform(entityId, i, (byte) connection.cofTransforms(i));
    }

    cofs.updateAlpha(entityId, alphaFlags);
    cofs.updateTransform(entityId, transformFlags);
    cofs.setMode(entityId, Engine.Player.MODE_TN, true);
    cofs.setWClass(entityId, Engine.WEAPON_1HS); // TODO...

    System.out.println("  " + DebugUtils.toByteArray(ArrayUtils.toByteArray(component)));
    System.out.println("  " + Arrays.toString(alpha));
    System.out.println("  " + DebugUtils.toByteArray(transform));
  }

  private void Disconnect(D2GS packet) {
    snapshotResyncPending = false;
    snapshotResyncInProgress = false;
    localLevelId = -1;
    serverEntityLevels.clear();
    lastDeletedServerTicks.clear();
    Disconnect disconnect = (Disconnect) packet.data(new Disconnect());
    int serverEntityId = disconnect.entityId();
    int entityId = syncIds.get(serverEntityId);

    CharData data = mPlayer.get(entityId).data;

    if (output != null && clientContext.strings() != null) {
      output.accept(clientContext.strings().format(3642, data.name));
    }
    if (output != null) output.accept("\n");

    removeTransform(entityId);
    world.delete(entityId);
  }

  @Deprecated
  private int findType(EntitySync s) {
    for (int i = 0, len = s.componentLength(); i < len; i++) {
      if (s.componentType(i) == ComponentP.ClassP) {
        return ((ClassP) s.component(new ClassP(), i)).type();
      }
    }

    return -1;
  }

  private <T extends Table> T findTable(EntitySync s, byte dataType, T table) {
    ByteBuffer dataTypes = s.componentTypeAsByteBuffer();
    for (int i = 0; dataTypes.hasRemaining(); i++) {
      if (dataTypes.get() == dataType) {
        s.component(table, i);
        return table;
      }
    }

    return null;
  }

  private int createEntity(EntitySync sync) {
    assert syncIds.get(sync.entityId()) == Engine.INVALID_ENTITY;
    Class.Type type = Class.Type.valueOf(sync.type());
    switch (type) {
      case OBJ: {
        DS1ObjectWrapperP ds1ObjectWrapper = findTable(sync, ComponentP.DS1ObjectWrapperP, new DS1ObjectWrapperP());
        if (ds1ObjectWrapper != null) {
          PositionP position = findTable(sync, ComponentP.PositionP, new PositionP());
          return factory.createObject(ds1ObjectWrapper.act(), ds1ObjectWrapper.type(), ds1ObjectWrapper.id(), position.x(), position.y());
        }

        com.riiablo.net.packet.d2gs.ObjectP object = findTable(
            sync, ComponentP.ObjectP, new com.riiablo.net.packet.d2gs.ObjectP());
        PositionP position = findTable(sync, ComponentP.PositionP, new PositionP());
        if (object != null && position != null) {
          int created = factory.createStaticObjectByClassId(
              object.objectId(), position.x(), position.y());
          // Quest-created objects (including town portals) can be placed on a
          // room boundary or on a coordinate that is not yet indexed by the
          // client map.  The authoritative level in EntitySync is more
          // reliable than a coordinate-only lookup; attach that zone before
          // RenderSystem builds its spatial index.
          attachNetworkZone(created, sync.levelId());
          applyObjectSnapshot(created, object);
          return created;
        }

        return Engine.INVALID_ENTITY;
      }
      case MON: {
        DS1ObjectWrapperP ds1ObjectWrapper = findTable(sync, ComponentP.DS1ObjectWrapperP, new DS1ObjectWrapperP());
        if (ds1ObjectWrapper != null) {
          PositionP position = findTable(sync, ComponentP.PositionP, new PositionP());
          if (position == null) {
            Gdx.app.error(TAG, "[ENTITY_SYNC] phase=monster_reject serverEntity="
                + sync.entityId() + " reason=missing_position");
            return Engine.INVALID_ENTITY;
          }
          String objectType = clientContext.files().MonPreset.getPlace(ds1ObjectWrapper.act(), ds1ObjectWrapper.id());
          MonStats.Entry monstats = clientContext.files().monstats.get(objectType);
          if (monstats == null) {
            Gdx.app.error(TAG, "[ENTITY_SYNC] phase=monster_reject serverEntity="
                + sync.entityId() + " reason=missing_monstats placement=" + objectType);
            return Engine.INVALID_ENTITY;
          }
          int created = factory.createMonster(monstats, position.x(), position.y());
          Gdx.app.log(TAG, String.format(
              "[ENTITY_SYNC] phase=monster_create serverEntity=%d localEntity=%d "
                  + "monster=%s position=(%.2f,%.2f) source=ds1",
              sync.entityId(), created, monstats.Id, position.x(), position.y()));
          return created;
        } else {
          PositionP position = findTable(sync, ComponentP.PositionP, new PositionP());
          MonsterP monster = findTable(sync, ComponentP.MonsterP, new MonsterP());
          if (position == null || monster == null) {
            Gdx.app.error(TAG, "[ENTITY_SYNC] phase=monster_reject serverEntity="
                + sync.entityId() + " reason="
                + (position == null ? "missing_position" : "missing_monster"));
            return Engine.INVALID_ENTITY;
          }
          int created = factory.createMonster(monster.monsterId(), position.x(), position.y(),
              monster.rank(), monster.affixes(), monster.championType(), monster.uniqueId());
          Gdx.app.log(TAG, String.format(
              "[ENTITY_SYNC] phase=monster_create serverEntity=%d localEntity=%d "
                  + "monsterId=%d rank=%d uniqueId=%d position=(%.2f,%.2f) source=monster_p",
              sync.entityId(), created, monster.monsterId(), monster.rank(), monster.uniqueId(),
              position.x(), position.y()));
          return created;
        }
      }
      case PLR: {
        PlayerP player = findTable(sync, ComponentP.PlayerP, new PlayerP());
        PositionP position = findTable(sync, ComponentP.PositionP, new PositionP());
        int entityId = factory.createPlayer(player.charName(), player.charClass(), position.x(), position.y());
        cofs.setMode(entityId, Engine.Player.MODE_TN);
        cofs.setWClass(entityId, Engine.WEAPON_1HS); // TODO...
        return entityId;
      }
      case ITM: {
        ItemP item = findTable(sync, ComponentP.ItemP, new ItemP());
        PositionP position = findTable(sync, ComponentP.PositionP, new PositionP());
        if (item == null || position == null) return Engine.INVALID_ENTITY;
        AuthoritativeGroundItemState.Snapshot snapshot = AuthoritativeGroundItemState.capture(
            sync.entityId(), sync.tick(), sync.serverTimeMillis(), item);
        final Item itemObj;
        try {
          itemObj = ClientGroundItemSnapshotWriter.decode(snapshot, itemReader, null);
        } catch (RuntimeException | AssertionError invalid) {
          requestSnapshotResync(sync.tick(), "ground_decode_failed");
          return Engine.INVALID_ENTITY;
        }
        int entityId = factory.createItem(itemObj, position.x(), position.y());
        if (entityId != Engine.INVALID_ENTITY && mItem.has(entityId)) {
          ClientGroundItemSnapshotWriter.apply(mItem.get(entityId), itemObj, snapshot);
          authoritativeGroundItems.commit(entityId, snapshot);
        }
        return entityId;
      }
      case WRP: {
        WarpP warp = findTable(sync, ComponentP.WarpP, new WarpP());
        PositionP position = findTable(sync, ComponentP.PositionP, new PositionP());
        Map.Zone sourceZoneHint = zoneForLevel(sync.levelId());
        int entityId = factory.createWarp(sourceZoneHint, warp.index(),
            position.x(), position.y());
        if (entityId == Engine.INVALID_ENTITY || !mMapWrapper.has(entityId)) {
          return Engine.INVALID_ENTITY;
        }
        attachNetworkZone(entityId, sync.levelId());
        Map.Zone zone = mMapWrapper.get(entityId).zone;
        if (zone == null) return Engine.INVALID_ENTITY;
        zone.addWarp(entityId);
        return entityId;
      }
      case MIS: {
        MissileP missile = findTable(sync, ComponentP.MissileP, new MissileP());
        PositionP position = findTable(sync, ComponentP.PositionP, new PositionP());
        AngleP angle = findTable(sync, ComponentP.AngleP, new AngleP());
        if (missile == null || position == null) return Engine.INVALID_ENTITY;
        Vector2 direction = angle == null ? new Vector2(1, 0) : new Vector2(angle.x(), angle.y());
        int localOwnerId = missile.ownerId() == Engine.INVALID_ENTITY
            ? Engine.INVALID_ENTITY : syncIds.get(missile.ownerId());
        int entityId = factory.createMissile(missile.missileId(), direction,
            new Vector2(position.x(), position.y()), localOwnerId);
        if (entityId != Engine.INVALID_ENTITY && mMissile.has(entityId)) {
          Missile local = mMissile.get(entityId);
          applyMissileSnapshot(local, missile);
        }
        Gdx.app.log(TAG, String.format(
            "[MISSILE_SYNC] phase=create serverEntity=%d missileId=%d owner=%d skill=%d "
                + "level=%d position=(%.2f,%.2f)",
            sync.entityId(), missile.missileId(), missile.ownerId(), missile.skillId(),
            missile.damageLevel(), position.x(), position.y()));
        return entityId;
      }
      default:
        return Engine.INVALID_ENTITY;
    }
  }

  private Map.Zone zoneForLevel(int levelId) {
    if (map == null || levelId < 0 || clientContext.files() == null || clientContext.files().Levels == null) {
      return null;
    }
    return map.findZone(clientContext.files().Levels.get(levelId));
  }

  private void attachNetworkZone(int entityId, int levelId) {
    applyMapSnapshot(entityId, levelId, 0L, 0L);
  }

  private void applyMapSnapshot(int entityId, int levelId, long tick, long serverTimeMillis) {
    if (entityId == Engine.INVALID_ENTITY || levelId < 0 || mMapWrapper == null || map == null) return;
    if (!authoritativeMaps.accepts(entityId, tick, serverTimeMillis)) return;
    AuthoritativeMapState.Snapshot snapshot =
        AuthoritativeMapState.capture(levelId, tick, serverTimeMillis);
    if (ClientMapSnapshotWriter.apply(mMapWrapper, map, entityId, snapshot,
        this::zoneForLevel)) authoritativeMaps.commit(entityId, snapshot);
  }



  /**
   * Applies the server's progression snapshot to the local character data.
   * PlayerP is also sent for remote players, but their progression must never
   * overwrite this client's character file.
   */
  private boolean isTownPortalSync(EntitySync sync) {
    if (sync == null) return false;
    Class.Type type = Class.Type.valueOf(sync.type());
    if (type == Class.Type.OBJ) {
      com.riiablo.net.packet.d2gs.ObjectP object = findTable(
          sync, ComponentP.ObjectP, new com.riiablo.net.packet.d2gs.ObjectP());
      return object != null && (object.objectId()
          == com.riiablo.engine.server.object.NativeQuestObjectResolver.TOWN_PORTAL
          || object.objectId() == 60);
    }
    if (type != Class.Type.WRP) return false;
    WarpP warp = findTable(sync, ComponentP.WarpP, new WarpP());
    if (warp == null || !com.riiablo.engine.server.quest.QuestWarp.isQuestWarp(warp.index())) {
      return false;
    }
    int destination = com.riiablo.engine.server.quest.QuestWarp.destinationLevelId(warp.index());
    return destination == 1 || destination == 40 || destination == 75
        || destination == 103 || destination == 109;
  }

  private void applyPlayerSnapshot(int entityId, PlayerP data, long tick, long serverTimeMillis) {
    if (character() == null || player() < 0 || entityId != player()) {
      return;
    }

    long wireExperience = data.experience();
    // Stat values are encoded as unsigned 32-bit values. Clamp malformed or
    // future packets before writing to avoid assertion failures in StatList.
    long experience = wireExperience < 0 ? 0L : Math.min(wireExperience, 0xFFFFFFFFL);
    int level = Math.max(1, data.level());
    int skillPoints = data.skillPoints();
    int statPoints = data.statPoints();
    if (authoritativePlayerItems.commit(tick, serverTimeMillis, data)) {
      ClientPlayerItemSnapshotWriter.apply(character(), authoritativePlayerItems.snapshot());
    }

    long oldExperience = character().getStats().aggregate()
        .getValue(Stat.experience, 0L);
    int oldLevel = character().getStats().aggregate()
        .getValue(Stat.level, character().level & 0xFF);
    int oldSkillPoints = character().getStats().aggregate()
        .getValue(Stat.newskills, 0);
    int oldStatPoints = character().getStats().aggregate()
        .getValue(Stat.statpts, 0);

    // The HUD reads aggregate stats, while save/load and server award code use
    // the base list. Keep both in lockstep so a later refresh cannot erase the
    // value received from the server.
    character().getStats().base().put(Stat.experience, experience);
    character().getStats().aggregate().put(Stat.experience, experience);
    character().getStats().base().put(Stat.level, level);
    character().getStats().aggregate().put(Stat.level, level);
    character().setAvailablePoints(Stat.newskills, skillPoints);
    character().setAvailablePoints(Stat.statpts, statPoints);
    character().level = (byte) level;

    // PlayerP and VitalsP are independent components in the same snapshot.
    // Keep progression insertion from exposing a stale base resource between
    // component applications (or after a packet that omits unchanged vitals).
    character().synchronizeCurrentResources();

    int questCount = data.questRecordsLength();
    if (questCount > 0 && authoritativeQuests.commit(
        tick, serverTimeMillis, character().diff, data.questRevision(),
        questCount, data::questRecords)
        && ClientQuestSnapshotWriter.apply(character(), authoritativeQuests.snapshot())) {
      int currentDifficulty = character().diff;
      // The server serializes all acts for the character's current
      // difficulty; diff is not an act index.
      Gdx.app.log(TAG, "[QUEST_SYNC] entity=" + entityId + " revision="
          + data.questRevision() + " records=" + questCount + " difficulty=" + currentDifficulty);
    }

    int wireSkills = Math.min(data.skillIdsLength(), data.skillLevelsLength());
    if (character().classId != null) {
      boolean[] present = new boolean[Math.max(0,
          character().classId.lastSpell - character().classId.firstSpell)];
      for (int i = 0; i < wireSkills; i++) {
        int skillId = data.skillIds(i);
        int skillLevel = data.skillLevels(i);
        if (skillId < character().classId.firstSpell
            || skillId >= character().classId.lastSpell) continue;
        present[skillId - character().classId.firstSpell] = true;
        if (character().getBaseSkillLevel(skillId) != skillLevel) {
          character().setSkillLevel(skillId, skillLevel);
        }
      }
      for (int skillId = character().classId.firstSpell;
          skillId < character().classId.lastSpell; skillId++) {
        if (!present[skillId - character().classId.firstSpell]
            && character().getBaseSkillLevel(skillId) != 0) {
          character().setSkillLevel(skillId, 0);
        }
      }
    }

    if (oldExperience != experience || oldLevel != level || oldSkillPoints != skillPoints
        || oldStatPoints != statPoints) {
      Gdx.app.log(TAG, String.format(
          "[XP_SYNC] entity=%d experience=%d oldExperience=%d level=%d oldLevel=%d skillPoints=%d oldSkillPoints=%d statPoints=%d oldStatPoints=%d learnedSkills=%d",
          entityId, experience, oldExperience, level, oldLevel, skillPoints, oldSkillPoints,
          statPoints, oldStatPoints, wireSkills));
    }
    if (oldSkillPoints != skillPoints) character().notifySkillChanged();
  }

  private void applyObjectSnapshot(int entityId, com.riiablo.net.packet.d2gs.ObjectP snapshot) {
    if (entityId == Engine.INVALID_ENTITY || snapshot == null) return;
    boolean hasState = hasObjectStateSnapshot(snapshot);
    if (hasState && factory instanceof ClientEntityFactory) {
      ((ClientEntityFactory) factory).applyAuthoritativeObjectState(
          entityId, snapshot.mode(), snapshot.stateFlags());
    } else if (hasState) {
      // Test and compatibility factories may not own the client presentation
      // components. Still retain the complete authoritative state locally.
      com.riiablo.engine.server.component.Object object =
          mObject == null ? null : mObject.get(entityId);
      if (object != null) {
        object.mode = (byte) snapshot.mode();
        object.stateFlags = (byte) snapshot.stateFlags();
      }
      if (mSelectable != null) {
        if ((snapshot.stateFlags()
            & com.riiablo.engine.server.component.Object.STATE_INTERACTABLE) != 0) {
          mSelectable.create(entityId);
        } else {
          mSelectable.remove(entityId);
        }
      }
    }
    if (hasState && mCofReference.has(entityId)
        && snapshot.mode() >= Engine.Object.MODE_NU
        && snapshot.mode() <= Engine.Object.MODE_S5) {
      cofs.setMode(entityId, (byte) snapshot.mode());
    }
  }

  static boolean hasObjectStateSnapshot(com.riiablo.net.packet.d2gs.ObjectP snapshot) {
    return snapshot != null && (snapshot.mode() != Engine.Object.MODE_NU
        || snapshot.stateFlags() != 0);
  }

  private void NpcServiceResult(D2GS packet) {
    NpcServiceResult result = (NpcServiceResult) packet.data(new NpcServiceResult());
    if (result.mercenary() != null && character() != null) {
      com.riiablo.net.packet.d2gs.NpcMercenarySnapshot merc = result.mercenary();
      applyMercenaryHeader(merc.seed(), merc.nameId(), merc.hirelingId(), merc.experience(),
          (int) merc.flags());
    }
    if (clientSession != null && clientSession.results() != null) {
      clientSession.results().onNpcService(result);
    } else {
      clientContext.presentNpcServiceResult(result);
    }
    Gdx.app.log(TAG, "[NPC_SERVICE] request=" + result.requestId()
        + " success=" + result.success() + " reason=" + result.reason()
        + " gold=" + result.gold() + " stockRevision=" + result.stockRevision()
        + " stockCount=" + result.stockLength());
  }
  private void PartyResult(D2GS packet) {
    PartyResult result = (PartyResult) packet.data(new PartyResult());
    partyState.apply(result);
    Gdx.app.log(TAG, "[PARTY] request=" + result.requestId()
        + " operation=" + result.operation() + " success=" + result.success()
        + " reason=" + result.reason() + " source=" + result.sourceEntityId()
        + " target=" + result.targetEntityId() + " party=" + result.partyId()
        + " players=" + result.membersLength());
  }

  public ClientPartyState partyState() {
    return partyState;
  }

  private void applyMonsterSnapshot(int entityId, MonsterP data) {
    if (data == null || !mMonster.has(entityId)) return;
    mMonster.get(entityId).setRank(
        data.rank(), data.affixes(), data.championType(), data.uniqueId());
  }

  private void TradeResult(D2GS packet) {
    TradeResult result = (TradeResult) packet.data(new TradeResult());
    tradeState.apply(result);
    if (clientSession != null && clientSession.results() != null) {
      clientSession.results().onTradeState(this);
    } else {
      clientContext.presentTradeState(this);
    }
    Gdx.app.log(TAG, "[TRADE] request=" + result.requestId()
        + " operation=" + result.operation() + " success=" + result.success()
        + " reason=" + result.reason() + " session=" + result.sessionId()
        + " state=" + result.state() + " sourceItems=" + result.sourceItemsLength()
        + " targetItems=" + result.targetItemsLength());
  }

  public ClientTradeState tradeState() {
    return tradeState;
  }

  private void SpendSkillPointResult(D2GS packet) {
    SpendSkillPointResult result = (SpendSkillPointResult) packet.data(
        new SpendSkillPointResult());
    if (character() != null && result.success()) {
      character().setSkillLevel(result.skillId(), result.skillLevel());
      character().setAvailablePoints(Stat.newskills, result.skillPoints());
      character().notifySkillChanged();
    }
    Gdx.app.log(TAG, "[SKILL_POINT_NET] phase=result request=" + result.requestId()
        + " success=" + result.success() + " reason=" + result.reason()
        + " skill=" + result.skillId() + " level=" + result.skillLevel()
        + " points=" + result.skillPoints());
  }

  private void SpendStatPointResult(D2GS packet) {
    SpendStatPointResult result = (SpendStatPointResult) packet.data(
        new SpendStatPointResult());
    if (character() != null && result.success()) {
      short stat;
      switch (result.statType()) {
        case com.riiablo.engine.server.player.PlayerStatsManager.STAT_TYPE_STRENGTH:
          stat = Stat.strength; break;
        case com.riiablo.engine.server.player.PlayerStatsManager.STAT_TYPE_ENERGY:
          stat = Stat.energy; break;
        case com.riiablo.engine.server.player.PlayerStatsManager.STAT_TYPE_DEXTERITY:
          stat = Stat.dexterity; break;
        case com.riiablo.engine.server.player.PlayerStatsManager.STAT_TYPE_VITALITY:
          stat = Stat.vitality; break;
        default: stat = -1;
      }
      if (stat >= 0) {
        character().getStats().base().put(stat, result.statValue());
        character().getStats().aggregate().put(stat, result.statValue());
      }
      character().setAvailablePoints(Stat.statpts, result.statPoints());
    }
    Gdx.app.log(TAG, "[STAT_POINT_NET] phase=result request=" + result.requestId()
        + " success=" + result.success() + " reason=" + result.reason()
        + " stat=" + result.statType() + " value=" + result.statValue()
        + " points=" + result.statPoints());
  }

  private void CastSkillResult(D2GS packet) {
    CastSkillResult result = (CastSkillResult) packet.data(new CastSkillResult());
    if (result.sequence() <= lastCombatSequence && result.sequence() != 0L) {
      Gdx.app.debug(TAG, "[NET_CAST] phase=result_stale sequence=" + result.sequence()
          + " last=" + lastCombatSequence);
      return;
    }
    if (result.sequence() != 0L) lastCombatSequence = result.sequence();
    lastCombatAccepted = result.success();
    if (!result.success() && result.finalResult()) {
      int localSource = syncIds.get(result.sourceEntityId());
      if (localSource != Engine.INVALID_ENTITY) {
        Actioneer actioneer = world.getSystem(Actioneer.class);
        if (actioneer != null) actioneer.cancelCasting(localSource, result.reason());
      }
    }
    Gdx.app.log(TAG, "[NET_CAST] phase=result sequence=" + result.sequence()
        + " success=" + result.success() + " reason=" + result.reason()
        + " source=" + result.sourceEntityId() + " target=" + result.targetEntityId()
        + " appliedTick=" + result.appliedTick()
        + " authoritativeTick=" + result.authoritativeTick()
        + " final=" + result.finalResult());
  }

  public long lastCombatSequence() {
    return lastCombatSequence;
  }

  public boolean lastCombatAccepted() {
    return lastCombatAccepted;
  }

  private void QuestResult(D2GS packet) {
    QuestResult result = (QuestResult) packet.data(new QuestResult());
    // Quest revision is a content hash. Untimed replies cannot order records
    // against PlayerP (including newer rewards); refresh via a stamped baseline.
    AuthoritativeQuestState.Snapshot current = authoritativeQuests.snapshot();
    if (result.questRecordsLength() > 0
        && (current == null || current.revision != result.questRevision())) {
      requestSnapshotResync(snapshotTimeline.tick(), "quest_result");
    }
    Gdx.app.log(TAG, "[QUEST_NET] phase=result request=" + result.requestId()
        + " operation=" + result.operation() + " success=" + result.success()
        + " reason=" + result.reason() + " target=" + result.targetEntityId()
        + " message=" + result.messageIndex() + " revision=" + result.questRevision());
  }

  private void Synchronize(D2GS packet) {
    packet.data(sync);
    Synchronize(sync);
  }

  private void Synchronize(EntitySync entityData) {
    long previousTick = snapshotTimeline.tick();
    if (!snapshotResyncInProgress && previousTick > 0L && entityData.tick() > previousTick + 20L) {
      requestSnapshotResync(previousTick, "tick_gap");
    }
    if (snapshotResyncPending && !snapshotResyncInProgress) return;
    if (snapshotResyncInProgress && entityData.tick() != 0L
        && entityData.tick() < baselineBeginTick) {
      Gdx.app.log(TAG, "[SNAPSHOT_RESYNC] phase=entity_drop_before_begin serverEntity="
          + entityData.entityId() + " tick=" + entityData.tick()
          + " beginTick=" + baselineBeginTick);
      return;
    }

    // Reject packets from a previous level before they can advance the
    // snapshot timeline or delete an entity that happens to reuse an id in
    // the current map. The local player's packet is always allowed through so
    // it can establish the new level after a warp.
    final int packetLevelId = entityData.levelId();
    final int mappedEntityId = syncIds.get(entityData.entityId());
    final boolean packetForLocalPlayer = player() >= 0
        && mappedEntityId != Engine.INVALID_ENTITY
        && mappedEntityId == player();
    if (!packetForLocalPlayer && packetLevelId >= 0 && localLevelId >= 0
        && packetLevelId != localLevelId) {
      Gdx.app.log(TAG, "[ENTITY_SYNC] phase=drop_wrong_level serverEntity="
          + entityData.entityId() + " packetLevel=" + packetLevelId
          + " localLevel=" + localLevelId + " tick=" + entityData.tick());
      return;
    }
    if (!snapshotTimeline.accept(entityData.tick(), entityData.serverTimeMillis())) {
      Gdx.app.error(TAG, "[ENTITY_SYNC] phase=stale_drop serverEntity="
          + entityData.entityId() + " tick=" + entityData.tick()
          + " acceptedTick=" + snapshotTimeline.tick()
          + " serverTime=" + entityData.serverTimeMillis()
          + " acceptedServerTime=" + snapshotTimeline.serverTimeMillis());
      return;
    }
    if (entityData.tick() > lastResyncObservedTick) lastResyncObservedTick = entityData.tick();
    if (snapshotResyncInProgress) baselineTransaction.acceptEntity(entityData.entityId());
    if (entityData.tick() > latestServerTick) {
      latestServerTick = entityData.tick();
      latestServerTickReceiptMillis = TimeUtils.millis();
    }
    int entityId = syncIds.get(entityData.entityId());
    if (packetLevelId >= 0) {
      serverEntityLevels.put(entityData.entityId(), packetLevelId);
      if (packetForLocalPlayer && packetLevelId != localLevelId) {
        int previousLevelId = localLevelId;
        localLevelId = packetLevelId;
        pruneEntitiesForLevel(localLevelId);
        Gdx.app.log(TAG, "[ENTITY_SYNC] phase=level_change localLevel="
            + localLevelId + " previousLevel=" + previousLevelId
            + " tick=" + entityData.tick());
      }
    }
    if ((entityData.flags() & EntityFlags.deleted) == EntityFlags.deleted) {
      long deletionTick = entityData.tick();
      Long previousDeletion = lastDeletedServerTicks.get(entityData.entityId());
      if (previousDeletion != null
          && (deletionTick == 0L || previousDeletion >= deletionTick)) {
        Gdx.app.log(TAG, "[ENTITY_SYNC] phase=duplicate_delete_ignored serverEntity="
            + entityData.entityId() + " tick=" + deletionTick
            + " previousDeleteTick=" + previousDeletion);
        return;
      }
      lastDeletedServerTicks.put(entityData.entityId(), deletionTick);
      deferredServerEntities.remove(entityData.entityId());
      serverEntityLevels.remove(entityData.entityId(), -1);
      if (entityId != Engine.INVALID_ENTITY) {
        pendingMercenaryOwners.remove(entityId, Engine.INVALID_ENTITY);
        removeTransform(entityId);
        world.delete(entityId);
      }

      return;
    }

    Long previousDeletion = lastDeletedServerTicks.get(entityData.entityId());
    if (previousDeletion != null && (entityData.tick() == 0L
        || entityData.tick() > previousDeletion)) {
      // A newer baseline proves that this is a rebuilt incarnation of a
      // recycled server id.  Future deletes are allowed at the new tick.
      lastDeletedServerTicks.remove(entityData.entityId());
    }

    if (deferredServerEntities.contains(entityData.entityId())) return;

    if (entityId == Engine.INVALID_ENTITY) {
      entityId = createEntity(entityData);
      if (entityId == Engine.INVALID_ENTITY) {
        if (isTownPortalSync(entityData)) {
          // Portal endpoints are static for their whole lifetime. If their
          // first packet arrived while the destination zone was still being
          // built, suppressing this server id would lose the portal forever
          // because no incremental component update is guaranteed to follow.
          // A baseline retries creation once the map is ready.
          requestSnapshotResync(entityData.tick(), "town_portal_deferred");
        } else {
          deferredServerEntities.add(entityData.entityId());
        }
        Gdx.app.log(TAG, "[ENTITY_SYNC] phase=skip serverEntity=" + entityData.entityId()
            + " type=" + entityData.type() + " reason=client_factory_rejected");
        return;
      }
      syncIds.put(entityData.entityId(), entityId);
    }
    applyMapSnapshot(entityId, packetLevelId, entityData.tick(), entityData.serverTimeMillis());

    boolean localPlayer = player() >= 0 && entityId == player();
    if (localPlayer) {
      StateP markerStates = findTable(entityData, ComponentP.StateP, new StateP());
      VitalsP markerVitals = findTable(entityData, ComponentP.VitalsP, new VitalsP());
      if (containsState(markerStates, StateId.SYNC_WARPED)) {
        requestSnapshotResync(entityData.tick(), "warp");
      } else if (markerVitals != null && markerVitals.dead()) {
        requestSnapshotResync(entityData.tick(), "death");
      }
    }
    Class.Type entityType = Class.Type.valueOf(entityData.type());
    boolean movingEntity = entityType == Class.Type.PLR || entityType == Class.Type.MON
        || entityType == Class.Type.MIS;
    // Commit the wire value before prediction replays unacknowledged inputs.
    // Mutable client Position/Velocity/Angle are replicas, not server authority.
    if (movingEntity && !authoritativeTransforms.commit(entityId, entityData)) return;
    if (movingEntity && !authoritativeLifecycles.commit(entityId, entityData)) return;
    AuthoritativeUnitLifecycleState.Snapshot lifecycle = authoritativeLifecycles.get(entityId);
    if (lifecycle != null) {
      UnitStates states = mUnitStates.create(entityId);
      if (states.stateList == null) states.init(entityId);
      states.snapshotOnly = true;
    }
    if (!localPlayer && movingEntity && interpolation != null) {
      PositionP position = findTable(entityData, ComponentP.PositionP, new PositionP());
      AngleP angle = findTable(entityData, ComponentP.AngleP, new AngleP());
      StateP states = findTable(entityData, ComponentP.StateP, new StateP());
      if (position != null || angle != null) {
        interpolation.record(entityId, entityData.tick(), entityData.serverTimeMillis(),
            position != null, position == null ? 0f : position.x(),
            position == null ? 0f : position.y(),
            angle != null, angle == null ? 0f : angle.x(),
            angle == null ? 0f : angle.y(), containsState(states, StateId.SYNC_WARPED));
      }
    }

    boolean applyLocalPositionNormally = true;
    if (localPlayer) {
      boolean respawning = lifecycle != null
          && lifecycle.phase == AuthoritativeUnitLifecycleState.Phase.ALIVE
          && world.getMapper(com.riiablo.engine.server.component.PlayerCorpse.class).has(entityId);
      PositionP authoritative = findTable(entityData, ComponentP.PositionP, new PositionP());
      if (authoritative != null && networkSynchronizer != null) {
        long acknowledged = entityData.acknowledgedInputSequence();
        if (respawning || acknowledged == 0L) {
          // Compatible pre-prediction baseline.
          if (respawning) networkSynchronizer.resetPrediction(authoritative.x(), authoritative.y());
          else networkSynchronizer.rebaseLegacyPrediction(authoritative.x(), authoritative.y());
          if (interpolation != null) interpolation.correctLocal(entityId, 0f, 0f, true);
        } else {
          StateP states = findTable(entityData, ComponentP.StateP, new StateP());
          VitalsP vitals = findTable(entityData, ComponentP.VitalsP, new VitalsP());
          boolean rejected = entityData.rejectedInputSequence() == acknowledged;
          boolean hard = rejected || containsState(states, StateId.SYNC_WARPED)
              || (vitals != null && vitals.dead());
          Vector2 predicted = mPosition.get(entityId).position;
          ClientPredictionBuffer.Reconciliation correction =
              networkSynchronizer.reconcileMovement(acknowledged,
                  authoritative.x(), authoritative.y(), predicted.x, predicted.y, hard);
          applyLocalPositionNormally = false;
          if (!correction.stale) {
            mPosition.get(entityId).invalidateNativePrecision();
            predicted.set(correction.x, correction.y);
            if (mBox2DBody.has(entityId)) {
              Body body = mBox2DBody.get(entityId).body;
              if (body != null) body.setTransform(predicted, body.getAngle());
            }
            if (interpolation != null) {
              interpolation.correctLocal(entityId, correction.renderOffsetX,
                  correction.renderOffsetY, correction.hard);
            }
            Gdx.app.log(TAG, String.format(
                "[NET_PREDICTION] ack=%d rejected=%s hard=%s smooth=%s "
                    + "authority=(%.2f,%.2f) replay=(%.2f,%.2f)",
                acknowledged, rejected, correction.hard, correction.smooth,
                authoritative.x(), authoritative.y(), correction.x, correction.y));
          }
        }
      }
    }

    int tFlags = Dirty.NONE;
    int aFlags = Dirty.NONE;
    if (DEBUG_SYNC) Gdx.app.debug(TAG, "syncing " + entityId);
    for (int i = 0, len = entityData.componentLength(); i < len; i++) {
      switch (entityData.componentType(i)) {
        case ComponentP.ClassP:
          break;
        case ComponentP.PlayerP: {
          PlayerP data = (PlayerP) entityData.component(new PlayerP(), i);
          applyPlayerSnapshot(entityId, data, entityData.tick(), entityData.serverTimeMillis());
          break;
        }
        case ComponentP.ObjectP: {
          com.riiablo.net.packet.d2gs.ObjectP data = (com.riiablo.net.packet.d2gs.ObjectP)
              entityData.component(new com.riiablo.net.packet.d2gs.ObjectP(), i);
          applyObjectSnapshot(entityId, data);
          break;
        }
        case ComponentP.DS1ObjectWrapperP:
        case ComponentP.WarpP:
          break;
        case ComponentP.MonsterP: {
          MonsterP data = (MonsterP) entityData.component(new MonsterP(), i);
          applyMonsterSnapshot(entityId, data);
          break;
        }
        case ComponentP.ItemP: {
          if (entityType == Class.Type.ITM && mItem.has(entityId)) {
            ItemP data = (ItemP) entityData.component(new ItemP(), i);
            if (!authoritativeGroundItems.accepts(entityId, entityData.tick(), entityData.serverTimeMillis())) break;
            AuthoritativeGroundItemState.Snapshot snapshot = AuthoritativeGroundItemState.capture(
                entityData.entityId(), entityData.tick(), entityData.serverTimeMillis(), data,
                authoritativeGroundItems.get(entityId));
            try {
              Item decoded = ClientGroundItemSnapshotWriter.decode(snapshot, itemReader, mItem.get(entityId).item);
              ClientGroundItemSnapshotWriter.apply(mItem.get(entityId), decoded, snapshot);
              authoritativeGroundItems.commit(entityId, snapshot);
            } catch (RuntimeException | AssertionError invalid) {
              Gdx.app.error(TAG, "[ITEM_SYNC] failed to decode item " + entityData.entityId(), invalid);
              requestSnapshotResync(entityData.tick(), "ground_decode_failed");
            }
          }
          break;
        }
        case ComponentP.SummonedPetP: {
          SummonedPetP data = (SummonedPetP) entityData.component(
              new SummonedPetP(), i);
          applySummonedPetSnapshot(entityId, data);
          break;
        }
        case ComponentP.MercenaryP: {
          applyMercenarySnapshot(entityId, (com.riiablo.net.packet.d2gs.MercenaryP)
              entityData.component(new com.riiablo.net.packet.d2gs.MercenaryP(), i),
              entityData.tick(), entityData.serverTimeMillis());
          break;
        }
        case ComponentP.StateP: {
          if (lifecycle != null) {
            ClientUnitSnapshotWriter.states(entityId, mUnitStates.create(entityId), lifecycle);
          }
          break;
        }
        case ComponentP.VitalsP: {
          if (lifecycle != null) applyVitalsSnapshot(entityId, lifecycle.vitals);
          break;
        }
        case ComponentP.CofReferenceP: {
          CofReferenceP data = (CofReferenceP) entityData.component(new CofReferenceP(), i);
          applyCofReferenceSnapshot(entityId, data);
          break;
        }
        case ComponentP.CofComponentsP: {
          CofComponentsP data = (CofComponentsP) entityData.component(new CofComponentsP(), i);
          for (int j = 0, s0 = data.componentLength(); j < s0; j++) {
            cofs.setComponent(entityId, j, (byte) data.component(j));
          }
          break;
        }
        case ComponentP.CofTransformsP: {
          CofTransformsP data = (CofTransformsP) entityData.component(new CofTransformsP(), i);
          for (int j = 0, s0 = data.transformLength(); j < s0; j++) {
            tFlags |= cofs.setTransform(entityId, j, (byte) data.transform(j));
          }
          break;
        }
        case ComponentP.CofAlphasP: {
          CofAlphasP data = (CofAlphasP) entityData.component(new CofAlphasP(), i);
          for (int j = 0, s0 = data.alphaLength(); j < s0; j++) {
            aFlags |= cofs.setAlpha(entityId, j, data.alpha(j) / 255f);
          }
          break;
        }
        case ComponentP.PositionP: {
          if (localPlayer && !applyLocalPositionNormally) break;
          Vector2 position = mPosition.get(entityId).position;
          PositionP data = (PositionP) entityData.component(new PositionP(), i);
          mPosition.get(entityId).invalidateNativePrecision();
          position.x = data.x();
          position.y = data.y();
          if (mBox2DBody.has(entityId)) {
            Body body = mBox2DBody.get(entityId).body;
            if (body != null) body.setTransform(position, body.getAngle());
          }
          //Gdx.app.log(TAG, "  " + position);
          break;
        }
        case ComponentP.VelocityP: {
          Vector2 velocity = mVelocity.create(entityId).velocity;
          VelocityP data = (VelocityP) entityData.component(new VelocityP(), i);
          velocity.x = data.x();
          velocity.y = data.y();
          //Gdx.app.log(TAG, "  " + velocity);
          break;
        }
        case ComponentP.AngleP: {
          Vector2 angle = mAngle.get(entityId).target;
          AngleP data = (AngleP) entityData.component(new AngleP(), i);
          angle.x = data.x();
          angle.y = data.y();
          //Gdx.app.log(TAG, "  " + angle);
          break;
        }
        case ComponentP.MissileP:
          // Identity is consumed during createEntity; later packets only
          // update position, velocity and angle.
          break;
        default:
          Gdx.app.error(TAG, "Unknown packet type: " + ComponentP.name(entityData.componentType(i)));
      }
    }

    cofs.updateTransform(entityId, tFlags);
    cofs.updateAlpha(entityId, aFlags);
    ClientUnitLifecycleSystem lifecycleSystem = world.getSystem(ClientUnitLifecycleSystem.class);
    if (lifecycle != null && lifecycleSystem != null) {
      lifecycleSystem.applySnapshot(entityId, lifecycle);
    }
  }

  private void applySummonedPetSnapshot(int entityId, SummonedPetP data) {
    if (data == null || mSummonedPet == null) return;
    if (mLabel != null && mLabel.has(entityId)) mLabel.remove(entityId);
    int owner = syncIds.get(data.ownerId());
    if (owner == Engine.INVALID_ENTITY) owner = data.ownerId();
    String type = data.petType();
    SummonedPet pet = mSummonedPet.create(entityId).set(owner, type,
        data.skillId(), 1, false, 0);
    pet.networkReplica = true;
    if (data.unsummonable()) mSelectable.create(entityId);
  }

  void applyMercenaryHeader(int seed, int nameId, int hirelingId, long xp, int flags) {
    CharData.MercData saved = character().getMerc();
    boolean changed = saved.seed != seed;
    int oldLevel = saved.getStats().get(Stat.level) == null ? 0
        : saved.getStats().get(Stat.level).asInt();
    if (changed) {
      saved.getItems().replaceFromAuthoritativeSnapshot(new com.badlogic.gdx.utils.Array<Item>());
      saved.getStats().clear();
    }
    saved.seed = seed; saved.name = (short) nameId; saved.type = (short) hirelingId;
    saved.xp = xp; saved.flags = flags;
    if (seed == 0) return;
    if (clientHirelingTable == null) clientHirelingTable =
        com.riiablo.engine.server.NativeHirelingExperienceTable.load();
    int level = clientHirelingTable.levelForStoredExperience(hirelingId, xp);
    if (changed || level != oldLevel) {
      com.riiablo.engine.server.NativeHirelingStatsUpdater.apply(saved.getStats(),
          clientHirelingTable.stats(hirelingId, level));
      saved.getItems().updateStats();
    }
    saved.getStats().base().put(Stat.experience, (int) Math.min(Integer.MAX_VALUE, xp));
    saved.getStats().aggregate().put(Stat.experience, (int) Math.min(Integer.MAX_VALUE, xp));
  }

  void applyMercenarySnapshot(int entityId, com.riiablo.net.packet.d2gs.MercenaryP data) {
    applyMercenarySnapshot(entityId, data, 0L, 0L);
  }

  void applyMercenarySnapshot(int entityId,
      com.riiablo.net.packet.d2gs.MercenaryP data, long tick, long serverTimeMillis) {
    if (data == null) return;
    int owner = syncIds.get(data.ownerId());
    if (!authoritativeMercenaries.accepts(entityId, tick, serverTimeMillis)) return;
    AuthoritativeMercenaryState.Snapshot snapshot =
        AuthoritativeMercenaryState.capture(data.ownerId(), tick, serverTimeMillis, data);
    if (!ClientMercenarySnapshotWriter.apply(mMercenary, entityId, owner, snapshot)) return;
    authoritativeMercenaries.commit(entityId, snapshot);
    com.riiablo.engine.server.component.Mercenary merc = mMercenary.get(entityId);
    if (mLabel.has(entityId)) mLabel.remove(entityId);
    if (mSelectable.has(entityId)) mSelectable.remove(entityId);
    world.getMapper(com.riiablo.engine.server.component.AIWrapper.class).remove(entityId);
    world.getMapper(com.riiablo.engine.server.component.Interactable.class).remove(entityId);
    if (owner == Engine.INVALID_ENTITY) {
      // Change-detected snapshots need not repeat this component. Retain a copy
      // in ECS, not a FlatBuffer view into the packet's reusable byte buffer.
      pendingMercenaryOwners.put(entityId, data.ownerId());
      return;
    }
    pendingMercenaryOwners.remove(entityId, Engine.INVALID_ENTITY);
    bindMercenaryOwner(entityId, merc, owner);
  }

  void resolveMercenaryOwners() {
    IntIntMap.Entries entries = pendingMercenaryOwners.entries();
    while (entries.hasNext) {
      IntIntMap.Entry entry = entries.next();
      if (!mMercenary.has(entry.key)) {
        entries.remove();
        continue;
      }
      int owner = syncIds.get(entry.value);
      if (owner == Engine.INVALID_ENTITY) continue;
      bindMercenaryOwner(entry.key, mMercenary.get(entry.key), owner);
      entries.remove();
    }
  }

  private void bindMercenaryOwner(int entityId,
      com.riiablo.engine.server.component.Mercenary merc, int owner) {
    merc.ownerId = owner;
    if (owner == player() && character() != null) {
      applyMercenaryHeader(merc.seed, merc.nameId, merc.hirelingId(), merc.experience, merc.flags);
      if (mAttributesWrapper.has(entityId)) {
        // The HUD/equipment panel and the replica share attributes. Later VitalsP
        // packets remain authoritative for current life and combat values.
        com.riiablo.attributes.Attributes previous = mAttributesWrapper.get(entityId).attrs;
        CharData.MercData saved = character().getMerc();
        if (previous != null && previous.get(Stat.hitpoints) != null) {
          saved.getStats().base().put(Stat.hitpoints, previous.get(Stat.hitpoints).asFixed());
          saved.getStats().aggregate().put(Stat.hitpoints, previous.get(Stat.hitpoints).asFixed());
        }
        mAttributesWrapper.get(entityId).attrs = saved.getStats();
      }
    }
  }

  /** Removes stale remote entities retained from the level we just left. */
  private void pruneEntitiesForLevel(int activeLevelId) {
    IntArray staleServerIds = new IntArray();
    for (IntIntMap.Entry entry : serverEntityLevels.entries()) {
      if (entry.value == activeLevelId) continue;
      int localEntityId = syncIds.get(entry.key);
      if (localEntityId != Engine.INVALID_ENTITY
          && (player() < 0 || localEntityId != player())) {
        staleServerIds.add(entry.key);
        pendingMercenaryOwners.remove(localEntityId, Engine.INVALID_ENTITY);
        removeTransform(localEntityId);
        world.delete(localEntityId);
      }
    }
    for (int i = 0; i < staleServerIds.size; i++) {
      serverEntityLevels.remove(staleServerIds.get(i), -1);
    }
    if (staleServerIds.size > 0) {
      Gdx.app.log(TAG, "[ENTITY_SYNC] phase=prune_old_level level="
          + activeLevelId + " entities=" + staleServerIds.size);
    }
  }

  private void SnapshotBaseline(D2GS packet) {
    SnapshotBaseline marker = (SnapshotBaseline) packet.data(new SnapshotBaseline());
    if (environment != null) {
      environment.initialize(marker.environmentCycle(), (int) marker.environmentTicks(),
          marker.environmentEclipse());
    }
    if (marker.phase() == SnapshotBaselinePhase.BEGIN) {
      if (!marker.success()) {
        Gdx.app.error(TAG, "[SNAPSHOT_RESYNC] phase=begin success=false request="
            + marker.requestId() + " baseline=" + marker.baselineId()
            + " reason=" + marker.reason());
        snapshotResyncPending = false;
        snapshotResyncInProgress = false;
        baselineTransaction.abort();
        return;
      }
      boolean started = baselineTransaction.begin(marker.requestId(), marker.baselineId(),
          marker.entityCount());
      if (!started) {
        Gdx.app.log(TAG, "[SNAPSHOT_RESYNC] phase=begin_ignored request="
            + marker.requestId() + " baseline=" + marker.baselineId()
            + " active=" + baselineTransaction.active());
        return;
      }
      snapshotResyncPending = false;
      snapshotResyncInProgress = true;
      baselineBeginTick = marker.serverTick();
      lastResyncObservedTick = marker.serverTick();
      lastDeletedServerTicks.clear();
      deferredServerEntities.clear();
      pendingMercenaryOwners.clear();
      if (interpolation != null) interpolation.clear();
      authoritativeTransforms.clear();
      authoritativeLifecycles.clear();
      authoritativeInventory.clear();
      authoritativeQuests.clear();
      authoritativePlayerItems.clear();
      authoritativeGroundItems.clear();
      authoritativeMercenaries.clear();
      authoritativeMaps.clear();
      authoritativeInventory.baseline(marker.inventoryRevision());
      NetworkedClientItemManager baselineItems = world.getSystem(NetworkedClientItemManager.class);
      if (baselineItems != null) baselineItems.resetInventoryRevision(marker.inventoryRevision());
      Gdx.app.log(TAG, "[SNAPSHOT_RESYNC] phase=begin request=" + marker.requestId()
          + " baseline=" + marker.baselineId() + " tick=" + marker.serverTick()
          + " entities=" + marker.entityCount());
    } else {
      AuthoritativeInventoryState.Snapshot inventory = authoritativeInventory.snapshot();
      boolean inventoryComplete = character() == null || inventory != null
          && inventory.revision == marker.inventoryRevision();
      long appliedQuestRevision = character() == null ? 0L : QuestSnapshot.revision(QuestSnapshot.records(character()));
      boolean questsComplete = character() == null || marker.questRevision() == 0L
          || authoritativeQuests.snapshot() != null
          && authoritativeQuests.snapshot().revision == marker.questRevision()
          && appliedQuestRevision == marker.questRevision();
      SnapshotBaselineTransaction.EndResult result = baselineTransaction.end(
          marker.requestId(), marker.baselineId(), marker.entityCount(), marker.success(),
          inventoryComplete && questsComplete);
      if (result == SnapshotBaselineTransaction.EndResult.IGNORED) {
        Gdx.app.log(TAG, "[SNAPSHOT_RESYNC] phase=end_ignored request="
            + marker.requestId() + " baseline=" + marker.baselineId());
        return;
      }
      if (result == SnapshotBaselineTransaction.EndResult.FAILED) {
        snapshotResyncPending = false;
        snapshotResyncInProgress = false;
        Gdx.app.error(TAG, "[SNAPSHOT_RESYNC] phase=end success=false request="
            + marker.requestId() + " reason=" + marker.reason());
        return;
      }
      if (result == SnapshotBaselineTransaction.EndResult.INCOMPLETE) {
        snapshotResyncInProgress = false;
        snapshotResyncPending = true;
        Gdx.app.error(TAG, "[SNAPSHOT_RESYNC] phase=end_incomplete request="
            + marker.requestId() + " baseline=" + marker.baselineId()
            + " expected=" + marker.entityCount()
            + " inventoryComplete=" + inventoryComplete + " questsComplete=" + questsComplete);
        requestSnapshotResync(lastResyncObservedTick, "baseline_incomplete");
        return;
      }
      snapshotTimeline.resetTo(marker.serverTick(), marker.serverTimeMillis());
      applyWaypointBaseline(marker);
      if (world.getSystem(NetworkedClientItemManager.class) != null) {
        world.getSystem(NetworkedClientItemManager.class)
            .resetInventoryRevision(marker.inventoryRevision());
      }
      authoritativeInventory.baseline(marker.inventoryRevision());
      latestServerTick = marker.serverTick();
      latestServerTickReceiptMillis = TimeUtils.millis();
      lastResyncObservedTick = marker.serverTick();
      snapshotResyncInProgress = false;
      snapshotResyncPending = false;
      Gdx.app.log(TAG, "[SNAPSHOT_RESYNC] phase=end request=" + marker.requestId()
          + " baseline=" + marker.baselineId() + " tick=" + marker.serverTick()
          + " entities=" + marker.entityCount());
    }
  }

  private void applyWaypointBaseline(SnapshotBaseline marker) {
    if (character() == null || marker.waypointMasksLength() == 0) return;
    int difficulty = Math.max(0, Math.min(com.riiablo.Riiablo.NUM_DIFFS - 1,
        marker.difficulty()));
    int count = Math.min(marker.waypointMasksLength(), com.riiablo.Riiablo.NUM_ACTS);
    for (int act = 0; act < count; act++) {
      character().setWaypointMask(difficulty, act, (int) marker.waypointMasks(act));
    }
    Gdx.app.log(TAG, "[SNAPSHOT_RESYNC] phase=waypoints difficulty=" + difficulty
        + " acts=" + count);
  }

  private void requestSnapshotResync(long tick, String reason) {
    long now = TimeUtils.millis();
    snapshotResyncPending = true;
    if (now - lastResyncRequestMillis < 2000L) return;
    lastResyncRequestMillis = now;
    if (networkSynchronizer != null) networkSynchronizer.requestSnapshotResync(tick, reason);
  }

  private static boolean containsState(StateP states, int stateId) {
    if (states == null) return false;
    for (int i = 0; i < states.stateIdLength(); i++) {
      if (states.stateId(i) == stateId) return true;
    }
    return false;
  }

  /** Applies presentation metadata without enabling client simulation. */
  static void applyMissileSnapshot(Missile local, MissileP data) {
    if (local == null || data == null) return;
    local.authoritative = false;
    local.range = data.range();
    local.skillId = data.skillId();
    local.damageLevel = data.damageLevel();
    if (data.nativeFrame() >= 0) local.nativeFrame = data.nativeFrame();
    if (data.nativeLifetimeFrames() >= 0) {
      local.nativeLifetimeFrames = data.nativeLifetimeFrames();
    }
  }

  private void applyVitalsSnapshot(int entityId, AuthoritativeUnitLifecycleState.Vitals data) {
    if (!mAttributesWrapper.has(entityId)) {
      Gdx.app.debug(TAG, "Ignoring vitals snapshot for entity without attributes: " + entityId);
      return;
    }
    AttributesWrapper wrapper = mAttributesWrapper.get(entityId);
    float oldHitpoints = wrapper.attrs.aggregate().getValue(Stat.hitpoints, 0f);
    ClientUnitSnapshotWriter.vitals(wrapper, data);
    float hitpoints = wrapper.attrs.aggregate().getValue(Stat.hitpoints, 0f);
    // Vitals snapshots do not carry the attacker's hit sound.  A real decrease
    // in a player entity's hit points is therefore the reliable network-side
    // signal for the character's class-specific pain voice.  Do not trigger on
    // healing, max-hp changes, or an initial snapshot.
    if (hitpoints < oldHitpoints && mPlayer.has(entityId)) {
      PlayerHitSound.play(clientContext, mPlayer.get(entityId));
    }
    if (oldHitpoints != hitpoints || data.dead) {
          Gdx.app.log(TAG, String.format(
          "[VITALS_SYNC] entity=%d hp=%.3f oldHp=%.3f maxHp=%.3f mana=%.3f maxMana=%.3f stamina=%.3f maxStamina=%.3f dead=%s",
          entityId, hitpoints, oldHitpoints, data.maxHitpoints, data.mana,
          data.maxMana, data.stamina, data.maxStamina, data.dead));
    }
  }

  private void PlayerLifecycleResult(D2GS packet) {
    PlayerLifecycleResult result = (PlayerLifecycleResult) packet.data(new PlayerLifecycleResult());
    if (networkSynchronizer == null
        || !networkSynchronizer.acceptPlayerLifecycleResult(result.requestId())) return;
    Gdx.app.log(TAG, "[PLAYER_RESPAWN] phase=result request=" + result.requestId()
        + " success=" + result.success() + " reason=" + result.reason());
    // This legacy acknowledgement has no server tick. Applying its coordinates,
    // local town spawn, or full resources could overwrite a newer EntitySync.
    // Only the timestamped baseline may commit the resulting lifecycle.
    if (result.success()) requestSnapshotResync(snapshotTimeline.tick(), "respawn");
  }

  private void applyCofReferenceSnapshot(int entityId, CofReferenceP data) {
    if (!mCofReference.has(entityId)) return;
    CofReference old = mCofReference.get(entityId);
    byte oldMode = old.mode;
    byte oldWClass = old.wclass;
    int oldRestartId = old.animationRestartId;
    cofs.applyNetworkMode(entityId, (byte) data.mode(), (int) data.animationRestartId());
    cofs.setWClass(entityId, (byte) data.weaponClass());
    if (oldMode != (byte) data.mode() || oldWClass != (byte) data.weaponClass()
        || oldRestartId != (int) data.animationRestartId()) {
      Gdx.app.log(TAG, String.format(
          "[COF_SYNC] entity=%d mode=%d oldMode=%d weaponClass=%d oldWeaponClass=%d "
              + "restartId=%d oldRestartId=%d",
          entityId, data.mode(), oldMode & 0xFF, data.weaponClass(), oldWClass & 0xFF,
          (int) data.animationRestartId(), oldRestartId));
    }
  }

  private void ItemMoveResult(D2GS packet) {
    ItemMoveResult result = (ItemMoveResult) packet.data(new ItemMoveResult());
    NetworkedClientItemManager itemManager = world.getSystem(NetworkedClientItemManager.class);
    if (character() == null || !authoritativeInventory.accepts(result.revision(), result.requestId())
        || itemManager != null && result.revision() < itemManager.inventoryRevision()) return;
    AuthoritativeInventoryState.Snapshot snapshot = AuthoritativeInventoryState.capture(result);
    final ClientInventorySnapshotWriter.Prepared prepared;
    try {
      prepared = ClientInventorySnapshotWriter.prepare(snapshot, itemReader);
    } catch (RuntimeException | AssertionError invalid) {
      Gdx.app.error(TAG, "[ITEM_MOVE_SNAPSHOT] rejected complete transaction", invalid);
      requestSnapshotResync(snapshotTimeline.tick(), "inventory_decode_failed");
      return;
    }
    prepared.apply(character());
    authoritativeInventory.commit(snapshot);
    if (itemManager != null) itemManager.onAuthoritativeResult(result);
    {
      // Artwork is client-only state and must not decide whether an
      // authoritative item exists. Load each icon after ownership has been
      // committed so a missing/bad asset cannot make a picked item disappear.
      for (Item item : prepared.items) {
        try {
          item.load();
        } catch (Throwable t) {
          Gdx.app.error(TAG, "[ITEM_MOVE_SNAPSHOT] failed to load artwork for item "
              + item.id + " code=" + item.code, t);
        }
      }
      Item cursorItem = character().getItems().getCursor();
      if (clientContext.cursor() != null) clientContext.cursor().setItem(cursorItem);
      if (snapshot.success
          && snapshot.operation == com.riiablo.net.packet.d2gs.ItemMoveOperation.GROUND_TO_CURSOR
          && cursorItem == null) {
        ClientItemManager pickupManager = world.getSystem(ClientItemManager.class);
        if (pickupManager != null) pickupManager.playPickupSound();
      }
    }
    if (snapshot.groundEntityId >= 0 && !snapshot.hasGroundItem()) {
      int localEntityId = syncIds.get(snapshot.groundEntityId);
      serverEntityLevels.remove(snapshot.groundEntityId, -1);
      deferredServerEntities.remove(snapshot.groundEntityId);
      if (localEntityId != Engine.INVALID_ENTITY) {
        removeTransform(localEntityId);
        world.delete(localEntityId);
      }
      Gdx.app.log(TAG, "[ITEM_MOVE_GROUND] phase=authoritative_remove serverEntity="
          + snapshot.groundEntityId + " request=" + snapshot.requestId);
    } else if (snapshot.groundEntityId >= 0 && snapshot.hasGroundItem()) {
      int localEntityId = syncIds.get(snapshot.groundEntityId);
      if (localEntityId != Engine.INVALID_ENTITY && mItem.has(localEntityId)) {
        try {
          mItem.get(localEntityId).item = prepared.ground;
          // Ground corrections carry the complete ownership window as well as
          // the serialized item.  Keep the ECS metadata in lock-step so an
          // expired/changed owner cannot leave a stale client-side pickup
          // restriction after a rejected request.
          com.riiablo.engine.server.component.Item local = mItem.get(localEntityId);
          local.dropOwnerId = snapshot.groundOwnerId;
          local.dropOwnerUntilMillis = snapshot.groundOwnerUntilMillis;
          local.dropPartyId = snapshot.groundPartyId;
          local.dropPartyUntilMillis = snapshot.groundPartyUntilMillis;
          local.partyShareGold = snapshot.groundPartyShareGold;
          authoritativeGroundItems.commit(localEntityId, AuthoritativeGroundItemState.correction(
              snapshot, authoritativeGroundItems.get(localEntityId)));
          if (mPosition.has(localEntityId)) {
            mPosition.get(localEntityId).invalidateNativePrecision();
            mPosition.get(localEntityId).position.set(snapshot.groundX, snapshot.groundY);
          }
        } catch (Throwable t) {
          Gdx.app.error(TAG, "[ITEM_MOVE_GROUND] failed to apply ground correction "
              + snapshot.groundEntityId, t);
        }
      } else {
        // Recreating an item from this compact correction would omit its
        // Level/RoomEx subscription context. Request a complete baseline so
        // the normal EntitySync path restores it without producing a
        // cross-level or ownerless ghost.
        requestSnapshotResync(lastResyncObservedTick, "item_ground_missing");
      }
    }
    if (!snapshot.success
        && snapshot.operation == com.riiablo.net.packet.d2gs.ItemMoveOperation.GROUND_TO_CURSOR
        && snapshot.failure == com.riiablo.net.packet.d2gs.ItemMoveFailure.INVENTORY_OCCUPIED
        && snapshot.groundEntityId >= 0) {
      int localEntityId = syncIds.get(snapshot.groundEntityId);
      ItemEffectManager effects = world.getSystem(ItemEffectManager.class);
      if (localEntityId != Engine.INVALID_ENTITY && effects != null) {
        effects.replayDrop(localEntityId);
      }
    }
    if (!snapshot.success) {
      Gdx.app.log(TAG, "[ITEM_MOVE_REJECTED] request=" + snapshot.requestId
          + " failure=" + snapshot.failure + " revision=" + snapshot.revision);
    }
  }

  private void GoldResult(D2GS packet) {
    GoldResult result = (GoldResult) packet.data(new GoldResult());
    // Untimed acknowledgements are feedback. PlayerP carries the stamped wallet.
    if (result.success() && result.groundEntityId() >= 0) {
      Gdx.app.log(TAG, "[GOLD_DROP] entity=" + result.groundEntityId()
          + " amount=" + result.amount());
    }
    if (!result.success()) {
      Gdx.app.log(TAG, "[GOLD_OPERATION_REJECTED] request=" + result.requestId()
          + " operation=" + result.operation() + " reason=" + result.reason());
    }
  }

  private CharData character() {
    return clientSession != null ? clientSession.character() : clientContext.charData();
  }

  private int player() {
    return clientSession != null ? clientSession.player()
        : clientContext.playerId();
  }
}

