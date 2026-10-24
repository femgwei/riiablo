package com.riiablo.server.d2gs;

import com.artemis.World;
import com.badlogic.gdx.math.Vector2;
import com.google.flatbuffers.ByteBufferUtil;
import com.riiablo.Files;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.ServerEntityFactory;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Item;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Object;
import com.riiablo.io.ByteInput;
import com.riiablo.item.ItemReader;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.D2GS;
import com.riiablo.net.packet.d2gs.D2GSData;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.net.packet.d2gs.ItemP;
import com.riiablo.net.packet.d2gs.MissileP;
import com.riiablo.net.packet.d2gs.MonsterP;
import com.riiablo.net.packet.d2gs.ObjectP;
import com.riiablo.net.packet.d2gs.PlayerP;
import com.riiablo.net.packet.d2gs.PositionP;
import com.riiablo.net.packet.d2gs.VelocityP;
import com.riiablo.net.packet.d2gs.AngleP;
import com.riiablo.net.packet.d2gs.WarpP;
import com.riiablo.save.CharData;
import com.riiablo.attributes.Stat;
import java.nio.ByteBuffer;

/**
 * Production checkpoint lifecycle bridge backed by the authoritative
 * {@link ServerEntityFactory}.  EntitySync contains enough native identity to
 * rebuild a networked entity, while the normal serializer applies the
 * mutable component state immediately afterwards.
 *
 * <p>Artemis does not expose arbitrary entity-id allocation.  The bridge
 * therefore accepts a create only when the factory returns the requested id;
 * a different allocation is rejected instead of silently remapping a network
 * identity.</p>
 */
public final class ServerEntityLifecycle implements D2gsCheckpointSnapshot.EntityLifecycle {
  private final World world;
  private final ServerEntityFactory factory;
  private final ItemReader itemReader;

  public ServerEntityLifecycle(World world, EntityFactory factory, Files files) {
    if (world == null) throw new NullPointerException("world");
    if (factory == null) throw new NullPointerException("factory");
    if (!(factory instanceof ServerEntityFactory)) {
      throw new IllegalArgumentException("factory must be ServerEntityFactory");
    }
    this.world = world;
    this.factory = (ServerEntityFactory) factory;
    this.itemReader = new ItemReader(files);
  }

  @Override
  public int createEntity(World target, int expectedEntityId, ByteBuffer payload) {
    if (target != world) throw new IllegalArgumentException("world");
    if (expectedEntityId < 0 || payload == null
        || world.getEntityManager().isActive(expectedEntityId)) return Engine.INVALID_ENTITY;
    try {
      D2GS envelope = D2GS.getRootAsD2GS(
          ByteBufferUtil.removeSizePrefix(payload.duplicate()));
      if (envelope.dataType() != D2GSData.EntitySync) return Engine.INVALID_ENTITY;
      EntitySync sync = (EntitySync) envelope.data(new EntitySync());
      if (sync == null || sync.entityId() != expectedEntityId) return Engine.INVALID_ENTITY;
      PositionP position = table(sync, ComponentP.PositionP, new PositionP());
      if (position == null || !Float.isFinite(position.x()) || !Float.isFinite(position.y())) {
        return Engine.INVALID_ENTITY;
      }
      int entityId;
      switch (Class.Type.valueOf(sync.type())) {
        case PLR:
          entityId = createPlayer(sync, position);
          break;
        case MON:
          entityId = createMonster(sync, position);
          break;
        case OBJ:
          entityId = createObject(sync, position);
          break;
        case ITM:
          entityId = createItem(sync, position);
          break;
        case WRP:
          entityId = createWarp(sync, position);
          break;
        case MIS:
          entityId = createMissile(sync, position);
          break;
        default:
          return Engine.INVALID_ENTITY;
      }
      if (entityId != expectedEntityId) {
        if (entityId >= 0 && world.getEntityManager().isActive(entityId)) world.delete(entityId);
        return entityId;
      }
      markCheckpointInsertion(entityId);
      return entityId;
    } catch (RuntimeException ignored) {
      return Engine.INVALID_ENTITY;
    }
  }

  private void markCheckpointInsertion(int entityId) {
    com.riiablo.engine.server.CofManager cofs = world.getSystem(CofManager.class);
    if (cofs != null && world.getMapper(com.riiablo.engine.server.component.CofReference.class)
        .has(entityId)) {
      cofs.markCheckpointInsertion(entityId);
    }
  }

  @Override
  public boolean deleteEntity(World target, int entityId) {
    if (target != world || entityId < 0 || !world.getEntityManager().isActive(entityId)) {
      return false;
    }
    CofManager cofs = world.getSystem(CofManager.class);
    if (cofs != null) cofs.cancelCheckpointInsertion(entityId);
    world.delete(entityId);
    return true;
  }

  private int createPlayer(EntitySync sync, PositionP position) {
    PlayerP player = table(sync, ComponentP.PlayerP, new PlayerP());
    if (player == null) return Engine.INVALID_ENTITY;
    String name = player.charName();
    if (name == null || name.isEmpty()) name = "restored-" + sync.entityId();
    CharData data = CharData.createRemote(name, (byte) player.charClass());
    restorePlayerSnapshot(data, player);
    return factory.createPlayer(data, new Vector2(position.x(), position.y()));
  }

  private static void restorePlayerSnapshot(CharData data, PlayerP player) {
    com.riiablo.attributes.StatListRef base = data.getStats().base();
    com.riiablo.attributes.StatListRef aggregate = data.getStats().aggregate();
    put(base, aggregate, Stat.level, player.level());
    put(base, aggregate, Stat.experience, player.experience());
    put(base, aggregate, Stat.newskills, player.skillPoints());
    put(base, aggregate, Stat.statpts, player.statPoints());
    put(base, aggregate, Stat.gold, player.gold());
    put(base, aggregate, Stat.goldbank, player.goldBank());
    data.restoreProgressionRevision(player.progressionRevision());
    data.getItems().restoreEquipmentRevision(player.equipmentRevision());
    for (int i = 0; i < player.skillIdsLength(); i++) {
      data.setSkillLevel(player.skillIds(i), player.skillLevels(i));
    }
    if (player.questRecordsLength() > 0) {
      com.riiablo.engine.server.quest.QuestSnapshot.apply(
          data, player.questRecordsLength(), player::questRecords);
    }
  }

  private static void put(com.riiablo.attributes.StatListRef base,
      com.riiablo.attributes.StatListRef aggregate, short stat, long value) {
    base.put(stat, value);
    aggregate.put(stat, value);
  }

  private int createMonster(EntitySync sync, PositionP position) {
    MonsterP monster = table(sync, ComponentP.MonsterP, new MonsterP());
    if (monster == null) return Engine.INVALID_ENTITY;
    return factory.createMonster(monster.monsterId(), position.x(), position.y(), monster.rank(),
        monster.affixes(), monster.championType(), monster.uniqueId());
  }

  private int createObject(EntitySync sync, PositionP position) {
    ObjectP object = table(sync, ComponentP.ObjectP, new ObjectP());
    if (object == null) return Engine.INVALID_ENTITY;
    int entityId = factory.createStaticObjectByClassId(object.objectId(), position.x(), position.y());
    if (entityId >= 0 && world.getMapper(Object.class).has(entityId)) {
      Object component = world.getMapper(Object.class).get(entityId);
      component.mode = (byte) object.mode();
      component.stateFlags = (byte) object.stateFlags();
    }
    return entityId;
  }

  private int createItem(EntitySync sync, PositionP position) {
    ItemP itemPayload = table(sync, ComponentP.ItemP, new ItemP());
    if (itemPayload == null || itemPayload.dataLength() == 0) return Engine.INVALID_ENTITY;
    ByteBuffer itemBytes = itemPayload.dataAsByteBuffer();
    byte[] bytes = new byte[itemBytes.remaining()];
    itemBytes.get(bytes);
    com.riiablo.item.Item item = itemReader.readItem(ByteInput.wrap(bytes));
    int entityId = factory.createItem(item, position.x(), position.y());
    if (entityId >= 0 && world.getMapper(Item.class).has(entityId)) {
      Item component = world.getMapper(Item.class).get(entityId);
      component.dropOwnerId = itemPayload.dropOwnerId();
      component.dropOwnerUntilMillis = itemPayload.dropOwnerUntilMillis();
      component.dropPartyId = itemPayload.dropPartyId();
      component.dropPartyUntilMillis = itemPayload.dropPartyUntilMillis();
      component.partyShareGold = itemPayload.partyShareGold();
    }
    return entityId;
  }

  private int createWarp(EntitySync sync, PositionP position) {
    WarpP warp = table(sync, ComponentP.WarpP, new WarpP());
    if (warp == null) return Engine.INVALID_ENTITY;
    return factory.createWarp(warp.index(), position.x(), position.y());
  }

  private int createMissile(EntitySync sync, PositionP position) {
    MissileP missile = table(sync, ComponentP.MissileP, new MissileP());
    if (missile == null) return Engine.INVALID_ENTITY;
    Vector2 direction = vector(sync, ComponentP.AngleP);
    if (direction.isZero()) direction = vector(sync, ComponentP.VelocityP);
    if (direction.isZero()) direction.set(1f, 0f);
    int entityId = factory.createMissile(missile.missileId(), direction,
        new Vector2(position.x(), position.y()), missile.ownerId());
    if (entityId >= 0 && world.getMapper(Missile.class).has(entityId)) {
      Missile component = world.getMapper(Missile.class).get(entityId);
      component.range = missile.range();
      component.skillId = missile.skillId();
      component.damageLevel = missile.damageLevel();
      component.nativeFrame = Math.max(0, missile.nativeFrame());
      component.nativeLifetimeFrames = Math.max(0, missile.nativeLifetimeFrames());
    }
    return entityId;
  }

  private static Vector2 vector(EntitySync sync, byte type) {
    if (type == ComponentP.AngleP) {
      AngleP angle = table(sync, type, new AngleP());
      return angle == null ? new Vector2() : new Vector2(angle.x(), angle.y());
    }
    VelocityP velocity = table(sync, type, new VelocityP());
    return velocity == null ? new Vector2() : new Vector2(velocity.x(), velocity.y());
  }

  @SuppressWarnings("unchecked")
  private static <T extends com.google.flatbuffers.Table> T table(EntitySync sync, byte type, T value) {
    for (int i = 0; i < sync.componentTypeLength(); i++) {
      if (sync.componentType(i) == type) return (T) sync.component(value, i);
    }
    return null;
  }
}
