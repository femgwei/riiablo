package com.riiablo.engine.client;

import com.google.flatbuffers.Table;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IntervalSystem;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.net.Socket;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.IntSet;

import com.riiablo.Riiablo;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Dirty;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.ItemManager;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Box2DBody;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofAlphas;
import com.riiablo.engine.server.component.CofComponents;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.CofTransforms;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.io.ByteInput;
import com.riiablo.item.Item;
import com.riiablo.item.ItemReader;
import com.riiablo.map.Map;
import com.riiablo.net.packet.d2gs.AngleP;
import com.riiablo.net.packet.d2gs.BeltToCursor;
import com.riiablo.net.packet.d2gs.BodyToCursor;
import com.riiablo.net.packet.d2gs.ClassP;
import com.riiablo.net.packet.d2gs.CofAlphasP;
import com.riiablo.net.packet.d2gs.CofComponentsP;
import com.riiablo.net.packet.d2gs.CofTransformsP;
import com.riiablo.net.packet.d2gs.CofReferenceP;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.Connection;
import com.riiablo.net.packet.d2gs.CursorToBelt;
import com.riiablo.net.packet.d2gs.CursorToBody;
import com.riiablo.net.packet.d2gs.CursorToGround;
import com.riiablo.net.packet.d2gs.CursorToStore;
import com.riiablo.net.packet.d2gs.D2GS;
import com.riiablo.net.packet.d2gs.D2GSData;
import com.riiablo.net.packet.d2gs.DS1ObjectWrapperP;
import com.riiablo.net.packet.d2gs.Disconnect;
import com.riiablo.net.packet.d2gs.EntityFlags;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.net.packet.d2gs.GroundToCursor;
import com.riiablo.net.packet.d2gs.ItemP;
import com.riiablo.net.packet.d2gs.MonsterP;
import com.riiablo.net.packet.d2gs.Ping;
import com.riiablo.net.packet.d2gs.NpcServiceResult;
import com.riiablo.net.packet.d2gs.PartyResult;
import com.riiablo.net.packet.d2gs.PlayerP;
import com.riiablo.net.packet.d2gs.PlayerLifecycleResult;
import com.riiablo.net.packet.d2gs.QuestResult;
import com.riiablo.net.packet.d2gs.SpendSkillPointResult;
import com.riiablo.net.packet.d2gs.PositionP;
import com.riiablo.net.packet.d2gs.StoreToCursor;
import com.riiablo.net.packet.d2gs.SwapBeltItem;
import com.riiablo.net.packet.d2gs.SwapBodyItem;
import com.riiablo.net.packet.d2gs.SwapStoreItem;
import com.riiablo.net.packet.d2gs.ItemMoveResult;
import com.riiablo.net.packet.d2gs.VelocityP;
import com.riiablo.net.packet.d2gs.VitalsP;
import com.riiablo.net.packet.d2gs.MissileP;
import com.riiablo.net.packet.d2gs.WarpP;
import com.riiablo.net.packet.d2gs.StateP;
import com.riiablo.net.SizePrefixedPacketAccumulator;
import com.riiablo.save.CharData;
import com.riiablo.util.ArrayUtils;
import com.riiablo.util.BufferUtils;
import com.riiablo.util.DebugUtils;
import com.riiablo.widget.TextArea;
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
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<com.riiablo.engine.server.component.Item> mItem;
  protected ComponentMapper<Box2DBody> mBox2DBody;
  protected ComponentMapper<MapWrapper> mMapWrapper;

  protected CofManager cofs;
  protected NetworkIdManager syncIds;
  protected ItemManager items;
  protected Pinger pinger;
  protected EventSystem events;

  @Wire(name="client.socket")
  protected Socket socket;

  @Wire(name = "factory")
  protected EntityFactory factory;

  @Wire(name = "map")
  protected Map map;

  @Wire(name = "output")
  protected TextArea output;

  protected static final ItemReader itemReader = new ItemReader(); // TODO: inject

  private static final int NETWORK_READ_BUFFER_SIZE = 1 << 16;
  private static final int MAX_NETWORK_PACKET_SIZE = 1 << 22;
  private final byte[] networkReadBuffer = new byte[NETWORK_READ_BUFFER_SIZE];
  private final SizePrefixedPacketAccumulator packets =
      new SizePrefixedPacketAccumulator(
          NETWORK_READ_BUFFER_SIZE, MAX_NETWORK_PACKET_SIZE,
          MAX_NETWORK_PACKET_SIZE + NETWORK_READ_BUFFER_SIZE + Integer.BYTES);
  private final EntitySync sync = new EntitySync();
  private final IntSet deferredServerEntities = new IntSet();
  private final ClientPartyState partyState = new ClientPartyState();

  public ClientNetworkReceiver() {
    super(null, 1 / 60f);
  }

  @Override
  protected void processSystem() {
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
      case D2GSData.GroundToCursor:
        GroundToCursor(packet);
        break;
      case D2GSData.CursorToGround:
        CursorToGround(packet);
        break;
      case D2GSData.StoreToCursor:
        StoreToCursor(packet);
        break;
      case D2GSData.CursorToStore:
        CursorToStore(packet);
        break;
      case D2GSData.SwapStoreItem:
        SwapStoreItem(packet);
        break;
      case D2GSData.BodyToCursor:
        BodyToCursor(packet);
        break;
      case D2GSData.CursorToBody:
        CursorToBody(packet);
        break;
      case D2GSData.SwapBodyItem:
        SwapBodyItem(packet);
        break;
      case D2GSData.BeltToCursor:
        BeltToCursor(packet);
        break;
      case D2GSData.CursorToBelt:
        CursorToBelt(packet);
        break;
      case D2GSData.SwapBeltItem:
        SwapBeltItem(packet);
        break;
      case D2GSData.NpcServiceResult:
        NpcServiceResult(packet);
        break;
      case D2GSData.PartyResult:
        PartyResult(packet);
        break;
      case D2GSData.ItemMoveResult:
        ItemMoveResult(packet);
        break;
      case D2GSData.SpendSkillPointResult:
        SpendSkillPointResult(packet);
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

    output.appendText(Riiablo.string.format(3641, charName));
    output.appendText("\n");

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
    Disconnect disconnect = (Disconnect) packet.data(new Disconnect());
    int serverEntityId = disconnect.entityId();
    int entityId = syncIds.get(serverEntityId);

    CharData data = mPlayer.get(entityId).data;

    output.appendText(Riiablo.string.format(3642, data.name));
    output.appendText("\n");

    world.delete(entityId);
    Body body = mBox2DBody.get(entityId).body;
    if (body != null) ;
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
          return factory.createStaticObjectByClassId(
              object.objectId(), position.x(), position.y());
        }

        return Engine.INVALID_ENTITY;
      }
      case MON: {
        DS1ObjectWrapperP ds1ObjectWrapper = findTable(sync, ComponentP.DS1ObjectWrapperP, new DS1ObjectWrapperP());
        if (ds1ObjectWrapper != null) {
          PositionP position = findTable(sync, ComponentP.PositionP, new PositionP());
          String objectType = Riiablo.files.MonPreset.getPlace(ds1ObjectWrapper.act(), ds1ObjectWrapper.id());
          MonStats.Entry monstats = Riiablo.files.monstats.get(objectType);
          return factory.createMonster(monstats, position.x(), position.y());
        } else {
          PositionP position = findTable(sync, ComponentP.PositionP, new PositionP());
          MonsterP monster = findTable(sync, ComponentP.MonsterP, new MonsterP());
          return factory.createMonster(monster.monsterId(), position.x(), position.y());
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
        byte[] bytes = BufferUtils.readRemaining(item.dataAsByteBuffer());
        ByteInput byteInput = ByteInput.wrap(bytes);
        Item itemObj = itemReader.readItem(byteInput);
        return factory.createItem(itemObj, position.x(), position.y());
      }
      case WRP: {
        WarpP warp = findTable(sync, ComponentP.WarpP, new WarpP());
        PositionP position = findTable(sync, ComponentP.PositionP, new PositionP());
        int entityId = factory.createWarp(warp.index(), position.x(), position.y());
        if (entityId == Engine.INVALID_ENTITY || !mMapWrapper.has(entityId)) {
          return Engine.INVALID_ENTITY;
        }
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
          local.authoritative = false;
          local.range = missile.range();
        }
        Gdx.app.log(TAG, String.format(
            "[MISSILE_SYNC] phase=create serverEntity=%d missileId=%d owner=%d position=(%.2f,%.2f)",
            sync.entityId(), missile.missileId(), missile.ownerId(), position.x(), position.y()));
        return entityId;
      }
      default:
        return Engine.INVALID_ENTITY;
    }
  }



  /**
   * Applies the server's progression snapshot to the local character data.
   * PlayerP is also sent for remote players, but their progression must never
   * overwrite this client's character file.
   */
  private void applyPlayerSnapshot(int entityId, PlayerP data) {
    if (Riiablo.charData == null || Riiablo.game == null
        || entityId != Riiablo.game.player) {
      return;
    }

    long wireExperience = data.experience();
    // Stat values are encoded as unsigned 32-bit values. Clamp malformed or
    // future packets before writing to avoid assertion failures in StatList.
    long experience = wireExperience < 0 ? 0L : Math.min(wireExperience, 0xFFFFFFFFL);
    int level = Math.max(1, data.level());
    int skillPoints = data.skillPoints();
    int statPoints = data.statPoints();
    if (data.walletPresent()) {
      com.riiablo.item.VendorPricing.setGoldSnapshot(Riiablo.charData,
          (int) Math.min(Integer.MAX_VALUE, data.gold()),
          (int) Math.min(Integer.MAX_VALUE, data.goldBank()));
    }
    if (data.ammoPresent()) {
      com.riiablo.item.Item ammo = Riiablo.charData.getItems().findItemById(data.ammoItemId());
      if (ammo == null) ammo = Riiablo.charData.getItems().getEquippedRangedAmmo();
      if (ammo != null && ammo.attrs != null) {
        int quantity = data.ammoQuantity();
        ammo.attrs.base().put(Stat.quantity, quantity);
        ammo.attrs.aggregate().put(Stat.quantity, quantity);
        Gdx.app.log(TAG, "[RANGED_AMMO_SYNC] itemId=" + data.ammoItemId()
            + " code=" + ammo.code + " quantity=" + quantity);
      }
    }

    long oldExperience = Riiablo.charData.getStats().aggregate()
        .getValue(Stat.experience, 0L);
    int oldLevel = Riiablo.charData.getStats().aggregate()
        .getValue(Stat.level, Riiablo.charData.level & 0xFF);
    int oldSkillPoints = Riiablo.charData.getStats().aggregate()
        .getValue(Stat.newskills, 0);
    int oldStatPoints = Riiablo.charData.getStats().aggregate()
        .getValue(Stat.statpts, 0);

    // The HUD reads aggregate stats, while save/load and server award code use
    // the base list. Keep both in lockstep so a later refresh cannot erase the
    // value received from the server.
    Riiablo.charData.getStats().base().put(Stat.experience, experience);
    Riiablo.charData.getStats().aggregate().put(Stat.experience, experience);
    Riiablo.charData.getStats().base().put(Stat.level, level);
    Riiablo.charData.getStats().aggregate().put(Stat.level, level);
    Riiablo.charData.getStats().base().put(Stat.newskills, skillPoints);
    Riiablo.charData.getStats().aggregate().put(Stat.newskills, skillPoints);
    Riiablo.charData.getStats().base().put(Stat.statpts, statPoints);
    Riiablo.charData.getStats().aggregate().put(Stat.statpts, statPoints);
    Riiablo.charData.level = (byte) level;

    int questCount = Math.min(data.questRecordsLength(), com.riiablo.Riiablo.NUM_ACTS * 8);
    if (questCount > 0) {
      int currentDifficulty = Riiablo.charData.diff;
      // The server serializes all acts for the character's current
      // difficulty; diff is not an act index.
      for (int act = 0; act < com.riiablo.Riiablo.NUM_ACTS; act++) {
        short[] records = Riiablo.charData.getQuests(act);
        int actOffset = act * 8;
        for (int i = 0; i < records.length && actOffset + i < questCount; i++) {
          records[i] = (short) data.questRecords(actOffset + i);
        }
      }
      Gdx.app.log(TAG, "[QUEST_SYNC] entity=" + entityId + " revision="
          + data.questRevision() + " records=" + questCount + " difficulty=" + currentDifficulty);
    }

    int wireSkills = Math.min(data.skillIdsLength(), data.skillLevelsLength());
    if (Riiablo.charData.classId != null) {
      boolean[] present = new boolean[Math.max(0,
          Riiablo.charData.classId.lastSpell - Riiablo.charData.classId.firstSpell)];
      for (int i = 0; i < wireSkills; i++) {
        int skillId = data.skillIds(i);
        int skillLevel = data.skillLevels(i);
        if (skillId < Riiablo.charData.classId.firstSpell
            || skillId >= Riiablo.charData.classId.lastSpell) continue;
        present[skillId - Riiablo.charData.classId.firstSpell] = true;
        if (Riiablo.charData.getBaseSkillLevel(skillId) != skillLevel) {
          Riiablo.charData.setSkillLevel(skillId, skillLevel);
        }
      }
      for (int skillId = Riiablo.charData.classId.firstSpell;
          skillId < Riiablo.charData.classId.lastSpell; skillId++) {
        if (!present[skillId - Riiablo.charData.classId.firstSpell]
            && Riiablo.charData.getBaseSkillLevel(skillId) != 0) {
          Riiablo.charData.setSkillLevel(skillId, 0);
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
  }

  private void NpcServiceResult(D2GS packet) {
    NpcServiceResult result = (NpcServiceResult) packet.data(new NpcServiceResult());
    if (Riiablo.game != null && Riiablo.game.vendorPanel != null) {
      Riiablo.game.vendorPanel.applyNetworkResult(result);
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

  private void SpendSkillPointResult(D2GS packet) {
    SpendSkillPointResult result = (SpendSkillPointResult) packet.data(
        new SpendSkillPointResult());
    if (Riiablo.charData != null && result.success()) {
      Riiablo.charData.setSkillLevel(result.skillId(), result.skillLevel());
      Riiablo.charData.getStats().base().put(Stat.newskills, result.skillPoints());
      Riiablo.charData.getStats().aggregate().put(Stat.newskills, result.skillPoints());
    }
    Gdx.app.log(TAG, "[SKILL_POINT_NET] phase=result request=" + result.requestId()
        + " success=" + result.success() + " reason=" + result.reason()
        + " skill=" + result.skillId() + " level=" + result.skillLevel()
        + " points=" + result.skillPoints());
  }

  private void QuestResult(D2GS packet) {
    QuestResult result = (QuestResult) packet.data(new QuestResult());
    if (Riiablo.charData != null && result.questRecordsLength() > 0) {
      int count = Math.min(result.questRecordsLength(), Riiablo.NUM_ACTS * 8);
      for (int act = 0; act < Riiablo.NUM_ACTS; act++) {
        short[] records = Riiablo.charData.getQuests(act);
        int offset = act * 8;
        for (int i = 0; i < records.length && offset + i < count; i++) {
          records[i] = (short) result.questRecords(offset + i);
        }
      }
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
    int entityId = syncIds.get(entityData.entityId());
    if ((entityData.flags() & EntityFlags.deleted) == EntityFlags.deleted) {
      deferredServerEntities.remove(entityData.entityId());
      if (entityId != Engine.INVALID_ENTITY) {
        world.delete(entityId);
      }

      return;
    }

    if (deferredServerEntities.contains(entityData.entityId())) return;

    if (entityId == Engine.INVALID_ENTITY) {
      entityId = createEntity(entityData);
      if (entityId == Engine.INVALID_ENTITY) {
        deferredServerEntities.add(entityData.entityId());
        Gdx.app.log(TAG, "[ENTITY_SYNC] phase=skip serverEntity=" + entityData.entityId()
            + " type=" + entityData.type() + " reason=client_factory_rejected");
        return;
      }
      syncIds.put(entityData.entityId(), entityId);
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
          applyPlayerSnapshot(entityId, data);
          break;
        }
        case ComponentP.ObjectP:
        case ComponentP.DS1ObjectWrapperP:
        case ComponentP.WarpP:
        case ComponentP.MonsterP:
        case ComponentP.ItemP:
          break;
        case ComponentP.StateP: {
          StateP data = (StateP) entityData.component(new StateP(), i);
          applyStateSnapshot(entityId, data);
          break;
        }
        case ComponentP.VitalsP: {
          VitalsP data = (VitalsP) entityData.component(new VitalsP(), i);
          applyVitalsSnapshot(entityId, data);
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
          Vector2 position = mPosition.get(entityId).position;
          PositionP data = (PositionP) entityData.component(new PositionP(), i);
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
  }

  private void applyStateSnapshot(int entityId, StateP data) {
    if (!mUnitStates.has(entityId)) {
      Gdx.app.debug(TAG, "Ignoring state snapshot for entity without UnitStates: " + entityId);
      return;
    }
    UnitStates unitStates = mUnitStates.get(entityId);
    if (unitStates.stateList == null) unitStates.init(entityId);
    int count = data.stateIdLength();
    int[] stateIds = new int[count];
    int[] durations = new int[count];
    int[] levels = new int[count];
    for (int i = 0; i < count; i++) {
      stateIds[i] = data.stateId(i);
      durations[i] = i < data.durationLength() ? data.duration(i) : 0;
      levels[i] = i < data.levelLength() ? data.level(i) : 1;
    }
    if (count > 0) {
      StringBuilder snapshot = new StringBuilder();
      for (int i = 0; i < count; i++) {
        if (i > 0) snapshot.append(',');
        snapshot.append(stateIds[i]).append(':').append(durations[i])
            .append('@').append(levels[i]);
      }
      Gdx.app.log(TAG, String.format(
          "[STATE_SYNC] entity=%d count=%d states=%s source=server",
          entityId, count, snapshot));
    }
    unitStates.stateList.replaceFromSnapshot(stateIds, durations, levels);
    unitStates.snapshotOnly = true;
  }

  private void applyVitalsSnapshot(int entityId, VitalsP data) {
    if (!mAttributesWrapper.has(entityId)) {
      Gdx.app.debug(TAG, "Ignoring vitals snapshot for entity without attributes: " + entityId);
      return;
    }
    AttributesWrapper wrapper = mAttributesWrapper.get(entityId);
    float oldHitpoints = wrapper.attrs.aggregate().getValue(Stat.hitpoints, 0f);
    com.riiablo.engine.server.component.serializer.VitalsSerializer.apply(wrapper, data);
    float hitpoints = wrapper.attrs.aggregate().getValue(Stat.hitpoints, 0f);
    if (oldHitpoints != hitpoints || data.dead()) {
      Gdx.app.log(TAG, String.format(
          "[VITALS_SYNC] entity=%d hp=%.3f oldHp=%.3f maxHp=%.3f mana=%.3f maxMana=%.3f dead=%s",
          entityId, hitpoints, oldHitpoints, data.maxHitpoints(), data.mana(),
          data.maxMana(), data.dead()));
    }
    if (oldHitpoints > 0f && data.dead()) {
      Gdx.app.log(TAG, String.format(
          "[PLAYER_DEATH_SYNC] phase=client entity=%d hp=%.3f dispatch=DeathEvent",
          entityId, hitpoints));
      // VitalsP currently has no killer id. Use the victim as a safe local
      // sentinel; presentation handlers only need the victim and this avoids
      // negative entity-id mapper lookups in legacy subscribers.
      events.dispatch(DeathEvent.obtain(entityId, entityId));
    }
  }

  private void PlayerLifecycleResult(D2GS packet) {
    PlayerLifecycleResult result = (PlayerLifecycleResult) packet.data(
        new PlayerLifecycleResult());
    if (!result.success()) {
      Gdx.app.log(TAG, "[PLAYER_RESPAWN] phase=reject request=" + result.requestId()
          + " reason=" + result.reason());
      return;
    }
    if (Riiablo.game == null || Riiablo.game.player < 0) return;
    int localPlayerId = Riiablo.game.player;
    DeathHandler death = world.getSystem(DeathHandler.class);
    if (death != null && death.isPlayerDead(localPlayerId)) {
      death.respawnPlayerAtTown(localPlayerId);
    }
    if (mPosition.has(localPlayerId)) {
      mPosition.get(localPlayerId).position.set(result.x(), result.y());
    }
    if (mMapWrapper.has(localPlayerId)) {
      Vector2 position = mPosition.get(localPlayerId).position;
      mMapWrapper.get(localPlayerId).set(map, map.getZone(position));
    }
    Gdx.app.log(TAG, String.format(
        "[PLAYER_RESPAWN] phase=confirmed request=%d serverPlayer=%d position=(%.2f,%.2f)",
        result.requestId(), result.playerEntityId(), result.x(), result.y()));
  }

  private void applyCofReferenceSnapshot(int entityId, CofReferenceP data) {
    if (!mCofReference.has(entityId)) return;
    CofReference old = mCofReference.get(entityId);
    byte oldMode = old.mode;
    byte oldWClass = old.wclass;
    cofs.setMode(entityId, (byte) data.mode());
    cofs.setWClass(entityId, (byte) data.weaponClass());
    if (oldMode != (byte) data.mode() || oldWClass != (byte) data.weaponClass()) {
      Gdx.app.log(TAG, String.format(
          "[COF_SYNC] entity=%d mode=%d oldMode=%d weaponClass=%d oldWeaponClass=%d",
          entityId, data.mode(), oldMode & 0xFF, data.weaponClass(), oldWClass & 0xFF));
    }
  }

  private void GroundToCursor(D2GS packet) {
    GroundToCursor groundToCursor = (GroundToCursor) packet.data(new GroundToCursor());
    int entityId = syncIds.get(groundToCursor.itemId());
    items.groundToCursor(Riiablo.game.player, entityId);
  }

  private void CursorToGround(D2GS packet) {
    CursorToGround cursorToGround = (CursorToGround) packet.data(new CursorToGround());
    items.cursorToGround(Riiablo.game.player);
  }

  private void StoreToCursor(D2GS packet) {
    StoreToCursor storeToCursor = (StoreToCursor) packet.data(new StoreToCursor());
    items.storeToCursor(Riiablo.game.player, storeToCursor.itemId());
  }

  private void CursorToStore(D2GS packet) {
    CursorToStore cursorToStore = (CursorToStore) packet.data(new CursorToStore());
    items.cursorToStore(Riiablo.game.player, cursorToStore.storeLoc(), cursorToStore.x(), cursorToStore.y());
  }

  private void SwapStoreItem(D2GS packet) {
    SwapStoreItem swapStoreItem = (SwapStoreItem) packet.data(new SwapStoreItem());
    items.swapStoreItem(Riiablo.game.player, swapStoreItem.itemId(), swapStoreItem.storeLoc(), swapStoreItem.x(), swapStoreItem.y());
  }

  private void BodyToCursor(D2GS packet) {
    BodyToCursor bodyToCursor = (BodyToCursor) packet.data(new BodyToCursor());
    items.bodyToCursor(Riiablo.game.player, bodyToCursor.bodyLoc(), bodyToCursor.merc());
  }

  private void CursorToBody(D2GS packet) {
    CursorToBody cursorToBody = (CursorToBody) packet.data(new CursorToBody());
    items.cursorToBody(Riiablo.game.player, cursorToBody.bodyLoc(), cursorToBody.merc());
  }

  private void SwapBodyItem(D2GS packet) {
    SwapBodyItem swapBodyItem = (SwapBodyItem) packet.data(new SwapBodyItem());
    items.swapBodyItem(Riiablo.game.player, swapBodyItem.bodyLoc(), swapBodyItem.merc());
  }

  private void BeltToCursor(D2GS packet) {
    BeltToCursor beltToCursor = (BeltToCursor) packet.data(new BeltToCursor());
    items.beltToCursor(Riiablo.game.player, beltToCursor.itemId());
  }

  private void CursorToBelt(D2GS packet) {
    CursorToBelt cursorToBelt = (CursorToBelt) packet.data(new CursorToBelt());
    items.cursorToBelt(Riiablo.game.player, cursorToBelt.x(), cursorToBelt.y());
  }

  private void SwapBeltItem(D2GS packet) {
    SwapBeltItem swapBeltItem = (SwapBeltItem) packet.data(new SwapBeltItem());
    items.swapBeltItem(Riiablo.game.player, swapBeltItem.itemId());
  }

  private void ItemMoveResult(D2GS packet) {
    ItemMoveResult result = (ItemMoveResult) packet.data(new ItemMoveResult());
    if (world.getSystem(NetworkedClientItemManager.class) != null) {
      world.getSystem(NetworkedClientItemManager.class).onAuthoritativeResult(result);
    }
    if (Riiablo.charData != null) {
      com.badlogic.gdx.utils.Array<Item> snapshot = new com.badlogic.gdx.utils.Array<>(false,
          result.snapshotLength(), Item.class);
      for (int i = 0; i < result.snapshotLength(); i++) {
        com.riiablo.net.packet.d2gs.ItemMoveSnapshotEntry entry = result.snapshot(i);
        try {
          byte[] encoded = new byte[entry.itemDataLength()];
          for (int j = 0; j < encoded.length; j++) encoded[j] = (byte) entry.itemData(j);
          Item item = itemReader.readItem(ByteInput.wrap(encoded));
          item.id = entry.itemId();
          item.location = com.riiablo.item.Location.valueOf(entry.location());
          item.storeLoc = com.riiablo.item.StoreLoc.valueOf(entry.storeLoc());
          item.bodyLoc = com.riiablo.item.BodyLoc.valueOf(entry.bodyLoc());
          item.gridX = (byte) entry.x();
          item.gridY = (byte) entry.y();
          snapshot.add(item);
        } catch (Throwable t) {
          Gdx.app.error(TAG, "[ITEM_MOVE_SNAPSHOT] failed to decode item " + entry.itemId(), t);
        }
      }
      Riiablo.charData.getItems().replaceFromAuthoritativeSnapshot(snapshot);
    }
    if (result.groundEntityId() >= 0 && result.groundItemDataLength() > 0) {
      int localEntityId = syncIds.get(result.groundEntityId());
      if (localEntityId != Engine.INVALID_ENTITY && mItem.has(localEntityId)) {
        try {
          byte[] encoded = new byte[result.groundItemDataLength()];
          for (int i = 0; i < encoded.length; i++) encoded[i] = (byte) result.groundItemData(i);
          mItem.get(localEntityId).item = itemReader.readItem(ByteInput.wrap(encoded));
          if (mPosition.has(localEntityId)) {
            mPosition.get(localEntityId).position.set(result.groundX(), result.groundY());
          }
        } catch (Throwable t) {
          Gdx.app.error(TAG, "[ITEM_MOVE_GROUND] failed to apply ground correction "
              + result.groundEntityId(), t);
        }
      }
    }
    if (!result.success()) {
      Gdx.app.log(TAG, "[ITEM_MOVE_REJECTED] request=" + result.requestId()
          + " failure=" + result.failure() + " revision=" + result.revision());
    }
  }
}
