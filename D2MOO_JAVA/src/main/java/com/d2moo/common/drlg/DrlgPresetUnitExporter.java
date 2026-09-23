package com.d2moo.common.drlg;

/** Receives persistent copies of preset units before a generated DRLG is freed. */
public interface DrlgPresetUnitExporter {
    /**
     * @param levelId level containing the unit
     * @param roomId stable ordinal of the owning RoomEx in the level
     * @param unitType one of {@link D2UnitTypes}
     * @param index class id, or a DS1 preset index when {@code ds1Raw} is true
     * @param mode native unit mode
     * @param x level-local X in subtile coordinates
     * @param y level-local Y in subtile coordinates
     * @param ds1Raw whether {@code index} must be resolved through the DS1 preset table
     * @param spawned native spawn flag; flagged units must not be spawned again
     * @param externalEntity whether the unit is a gameplay object exposed to the ECS bridge
     * @param sourceFile DS1 archive path which authored the unit, when known
     */
    void onPresetUnit(int levelId, int roomId, int unitType, int index, int mode,
            int x, int y, boolean ds1Raw, boolean spawned,
            boolean externalEntity, String sourceFile);
}
