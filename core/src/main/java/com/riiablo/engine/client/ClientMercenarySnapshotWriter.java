package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.riiablo.engine.server.component.Mercenary;

/** Projects a committed hireling snapshot into the compatibility ECS component. */
final class ClientMercenarySnapshotWriter {
  private ClientMercenarySnapshotWriter() {}

  static boolean apply(ComponentMapper<Mercenary> mapper, int entityId, int ownerId,
      AuthoritativeMercenaryState.Snapshot snapshot) {
    if (mapper == null || snapshot == null) return false;
    boolean existed = mapper.has(entityId);
    Mercenary merc = mapper.create(entityId);
    if (!existed || merc.seed != snapshot.seed) {
      merc.set(ownerId, snapshot.mercType, snapshot.level,
          snapshot.seed, snapshot.nameId);
    }
    merc.ownerId = ownerId;
    merc.mercType = snapshot.mercType;
    merc.hirelingId = snapshot.hirelingId;
    merc.level = snapshot.level;
    merc.seed = snapshot.seed;
    merc.nameId = snapshot.nameId;
    merc.experience = snapshot.experience;
    merc.flags = snapshot.flags;
    merc.progressionRevision = snapshot.progressionRevision;
    merc.equipmentRevision = snapshot.equipmentRevision;
    return true;
  }
}
