package com.riiablo.engine.server.party;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;

import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;

/** Keeps PartyMember snapshots synchronized with the authoritative ECS player. */
@All({Player.class, Position.class, AttributesWrapper.class, MapWrapper.class})
public class PartyMemberSyncSystem extends IteratingSystem {
  @Wire(name = "partyManager")
  protected PartyManager partyManager;

  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<MapWrapper> mMapWrapper;

  @Override
  protected void process(int entityId) {
    if (partyManager.getPartyId(entityId) == Party.INVALID_ID) return;

    Attributes attrs = mAttributesWrapper.get(entityId).attrs;
    MapWrapper map = mMapWrapper.get(entityId);
    Position position = mPosition.get(entityId);
    int levelId = map.zone != null && map.zone.level != null ? map.zone.level.Id : -1;
    int hp = stat(attrs, Stat.hitpoints);
    partyManager.updateMember(entityId,
        Math.max(1, stat(attrs, Stat.level)),
        hp, stat(attrs, Stat.maxhp),
        stat(attrs, Stat.mana), stat(attrs, Stat.maxmana),
        levelId, Math.round(position.position.x), Math.round(position.position.y), hp > 0);
  }

  private static int stat(Attributes attrs, short stat) {
    StatRef ref = attrs != null ? attrs.get(stat, StatRef.obtain()) : null;
    if (ref == null) return 0;
    return ref.entry() != null && ref.entry().ValShift == 8
        ? Math.round(ref.asFixed()) : ref.asInt();
  }
}
