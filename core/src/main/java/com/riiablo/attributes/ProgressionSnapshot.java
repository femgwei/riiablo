package com.riiablo.attributes;

/** Immutable player or mercenary progression state at one committed revision. */
public final class ProgressionSnapshot {
  public final int entityId;
  public final long revision, experience;
  public final int level;
  public final long equipmentRevision;
  public final boolean mercenary;

  private ProgressionSnapshot(int entityId, long revision, long experience, int level,
      long equipmentRevision, boolean mercenary) {
    this.entityId = entityId;
    this.revision = revision;
    this.experience = experience;
    this.level = level;
    this.equipmentRevision = equipmentRevision;
    this.mercenary = mercenary;
  }

  public static ProgressionSnapshot commit(int entityId, long revision, long experience, int level,
      EquipmentSnapshot equipment, boolean mercenary) {
    if (entityId < 0 || revision < 0 || experience < 0 || level < 1)
      throw new IllegalArgumentException("invalid progression state");
    if (equipment == null) throw new NullPointerException("equipment");
    return new ProgressionSnapshot(entityId, revision, experience, level,
        equipment.revision(), mercenary);
  }
}
