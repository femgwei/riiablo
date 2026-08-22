package com.riiablo.map.d2moo;

import com.d2moo.common.datatbls.DataTbls;
import com.d2moo.common.datatbls.D2LevelDefBin;
import com.d2moo.common.datatbls.D2LevelTypesTxt;
import com.d2moo.common.drlg.D2DrlgAct;
import com.d2moo.common.drlg.D2C_Acts;
import com.d2moo.common.drlg.D2DrlgCoord;
import com.d2moo.common.drlg.D2DrlgLevel;
import com.d2moo.common.drlg.D2DrlgPresetInfoStrc;
import com.d2moo.common.drlg.D2DrlgStrc;
import com.d2moo.common.drlg.D2LevelIds;
import com.d2moo.common.drlg.D2DrlgTypes;
import com.d2moo.common.drlg.DrlgDrlg;
import com.d2moo.common.util.D2Log;
import com.d2moo.common.util.D2FileReader;
import com.d2moo.common.util.D2MemoryPool;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.LvlTypes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 使用 D2MOO_JAVA 的 createLevelConnections 生成 Act1 荒野布局，
 * 供 Act1MapBuilderD2MOD 使用，替代自实现的回溯放置逻辑。
 */
public final class Act1D2MOOLayoutBridge {

    /** Act 1 levels consumed by the current native-layout bridge. */
    private static final int[] D2MOO_ACT1_LEVEL_IDS = {
        D2LevelIds.LEVEL_STONYFIELD,
        D2LevelIds.LEVEL_COLDPLAINS,
        D2LevelIds.LEVEL_BLOODMOOR,
        D2LevelIds.LEVEL_ROGUEENCAMPMENT,
        D2LevelIds.LEVEL_BURIALGROUNDS,
        D2LevelIds.LEVEL_BLACKMARSH,
        D2LevelIds.LEVEL_TAMOEHIGHLAND,
        D2LevelIds.LEVEL_DARKWOOD,
        D2LevelIds.LEVEL_UNDERGROUNDPASSAGELVL1,
    };

    /**
     * 布局 + DRLG 结构，用于导出 TileGrid 后再释放 drlg。
     */
    public static final class LayoutAndDrlg {
        public final Act1LayoutResult result;
        public final D2DrlgStrc drlg;

        public LayoutAndDrlg(Act1LayoutResult result, D2DrlgStrc drlg) {
            this.result = result;
            this.drlg = drlg;
        }
    }

    /**
     * 布局结果：坐标与城镇出口方向，供 Act1MapBuilderD2MOD 填入 LevelLinkData。
     */
    public static final class Act1LayoutResult {
        /** 每格 [x, y, width, height] 单位 tile，与 D2DrlgCoord 一致 */
        public final int[][] coords = new int[9][4];
        /** 0=Stony, 1=Cold, 2=Blood, 3=Town, 4=Burial, 5=Black Marsh,
         * 6=Tamoe, 7=Dark Wood, 8=Underground Passage level 1. */
        public final int[] levelIds = new int[9];
        /** levelLink[i]：连接到的上一格索引，-1 表示无 */
        public final int[] levelLink = new int[] { -1, 0, 1, 2, 1, 7, 5, -1, 0 };
        /** levelLinkEx[i] */
        public final int[] levelLinkEx = new int[] { -1, -1, -1, -1, -1, -1, -1, -1, 7 };
        /** 城镇出口方向 0–3 (D2MOD rand[0][townIndex])，用于预设选择与路径 */
        public int townDirection;
    }

    /**
     * 使用 D2MOO_JAVA 生成 Act1 荒野布局，并返回 DRLG 结构以便导出 TileGrid。
     * 调用方负责在导出完成后调用 DrlgDrlg.freeDrlg(layoutAndDrlg.drlg) 并清理 DataTbls 缓存。
     *
     * @param seed      地图种子
     * @param diff      难度 0/1/2
     * @param burialGroundsId 从 Levels.txt 解析的 Burial Grounds 的 Id
     * @return 成功时返回 LayoutAndDrlg（含 result 与 drlg），失败时返回 null
     */
    public static LayoutAndDrlg getLayoutAndDrlg(int seed, int diff, int burialGroundsId) {
        D2DrlgStrc drlg = null;
        try {
            D2LevelDefBin[] cache = buildLevelDefCache(diff, burialGroundsId);
            if (cache == null) return null;
            DataTbls.setLevelDefBinCache(cache);
            DataTbls.setLevelTypesTxtCache(buildLevelTypesCache());

            D2DrlgAct act = new D2DrlgAct();
            // D2C_Acts is 0-based: Act I is 0.
            act.setAct(D2C_Acts.ACT_I);
            act.setTownId(D2LevelIds.LEVEL_ROGUEENCAMPMENT);
            act.setPMemPool(new D2MemoryPool());

            D2FileReader.ArchiveReader archive = Act1D2MOOLayoutBridge::readArchiveFile;

            // Native D2Common loads these tables before DRLG generation.
            // Without LvlSub every outdoor room remains the same 0x40002
            // base floor and the export loses paths and substitutions.
            DataTbls.loadLvlPrestTxt(archive, 0);
            DataTbls.loadLvlSubTxt(archive);
            DataTbls.loadLvlMazeTxt(archive);
            DataTbls.loadLvlWarpTxt(archive);

            drlg = DrlgDrlg.allocDrlg(
                act,
                D2C_Acts.ACT_I,
                archive,
                seed,
                D2LevelIds.LEVEL_ROGUEENCAMPMENT,
                0,
                null,
                (byte) diff,
                null,
                null
            );
            if (drlg == null) {
                releaseDataTables();
                return null;
            }

            // allocDrlg initializes the town only. Outdoor levels must be
            // explicitly initialized before their room/tile chains can be
            // exported.
            for (int levelId : D2MOO_ACT1_LEVEL_IDS) {
                if (levelId == D2LevelIds.LEVEL_ROGUEENCAMPMENT) continue;
                D2DrlgLevel level = DrlgDrlg.getLevel(drlg, levelId);
                if (level == null) {
                    D2Log.error("ACT1_D2MOO_INIT missing level=%d seed=%d diff=%d", levelId, seed, diff);
                    DrlgDrlg.freeDrlg(drlg);
                    releaseDataTables();
                    return null;
                }
                D2Log.debug("ACT1_D2MOO_INIT level=%d type=%d beforeRooms=%d", levelId,
                    level.getDrlgType(), level.getRooms());
                DrlgDrlg.initLevel(level);
                D2Log.debug("ACT1_D2MOO_INIT level=%d afterRooms=%d firstRoom=%s", levelId,
                    level.getRooms(), level.getFirstRoomEx() != null ? "yes" : "no");
            }

            Act1LayoutResult result = new Act1LayoutResult();
            result.levelIds[0] = D2LevelIds.LEVEL_STONYFIELD;
            result.levelIds[1] = D2LevelIds.LEVEL_COLDPLAINS;
            result.levelIds[2] = D2LevelIds.LEVEL_BLOODMOOR;
            result.levelIds[3] = D2LevelIds.LEVEL_ROGUEENCAMPMENT;
            result.levelIds[4] = burialGroundsId;
            result.levelIds[5] = D2LevelIds.LEVEL_BLACKMARSH;
            result.levelIds[6] = D2LevelIds.LEVEL_TAMOEHIGHLAND;
            result.levelIds[7] = D2LevelIds.LEVEL_DARKWOOD;
            result.levelIds[8] = D2LevelIds.LEVEL_UNDERGROUNDPASSAGELVL1;

            for (int i = 0; i < D2MOO_ACT1_LEVEL_IDS.length; i++) {
                int levelId = D2MOO_ACT1_LEVEL_IDS[i];
                D2DrlgLevel level = DrlgDrlg.getLevel(drlg, levelId);
                if (level == null) {
                    DrlgDrlg.freeDrlg(drlg);
                    releaseDataTables();
                    return null;
                }
                D2DrlgCoord c = level.getLevelCoords();
                if (c == null) {
                    DrlgDrlg.freeDrlg(drlg);
                    releaseDataTables();
                    return null;
                }
                result.coords[i][0] = c.getNPosX();
                result.coords[i][1] = c.getNPosY();
                result.coords[i][2] = c.getNWidth();
                result.coords[i][3] = c.getNHeight();

                D2Log.debug("ACT1_D2MOO_LAYOUT level=%d type=%d coord=(%d,%d %dx%d) rooms=%d",
                    levelId, level.getDrlgType(), c.getNPosX(), c.getNPosY(),
                    c.getNWidth(), c.getNHeight(), level.getRooms());

                if (levelId == D2LevelIds.LEVEL_ROGUEENCAMPMENT) {
                    D2DrlgPresetInfoStrc preset = level.getPreset();
                    if (preset != null) {
                        result.townDirection = preset.getNDirection();
                    }
                }
            }

            return new LayoutAndDrlg(result, drlg);
        } catch (Throwable t) {
            D2Log.error("ACT1_D2MOO_LAYOUT failed seed=%d diff=%d burial=%d: %s",
                seed, diff, burialGroundsId, t.toString());
            if (Boolean.getBoolean("riiablo.drlg.stacktrace")) t.printStackTrace();
            if (drlg != null) DrlgDrlg.freeDrlg(drlg);
            releaseDataTables();
            return null;
        }
    }

    /**
     * 使用 D2MOO_JAVA 生成 Act1 荒野布局。
     *
     * @param seed      地图种子
     * @param diff      难度 0/1/2
     * @param burialGroundsId 从 Levels.txt 解析的 Burial Grounds 的 Id
     * @return 成功时返回布局结果，失败时返回 null（调用方回退到自实现放置）
     */
    public static Act1LayoutResult getLayout(int seed, int diff, int burialGroundsId) {
        LayoutAndDrlg layoutAndDrlg = getLayoutAndDrlg(seed, diff, burialGroundsId);
        if (layoutAndDrlg == null) return null;
        DrlgDrlg.freeDrlg(layoutAndDrlg.drlg);
        releaseDataTables();
        return layoutAndDrlg.result;
    }

    /**
     * D2 tables store DS1 names relative to data/global/tiles, while DT1 and
     * table paths are commonly already rooted at data/. Try the path as given
     * first, then the DS1 tiles root without changing D2MOO_JAVA's archive API.
     */
    static byte[] readArchiveFile(String fileName) {
        if (Riiablo.mpqs == null || fileName == null || fileName.isEmpty()) return null;
        String normalized = fileName.replace('\\', '/');
        String lower = normalized.toLowerCase(java.util.Locale.ROOT);
        String[] candidates = lower.startsWith("data/")
            ? new String[] { normalized }
            : new String[] { normalized, "data/global/tiles/" + normalized };
        for (String candidate : candidates) {
            com.badlogic.gdx.files.FileHandle handle = Riiablo.mpqs.resolve(candidate);
            if (handle != null && handle.exists()) return handle.readBytes();
        }
        return null;
    }

    /** Releases the static D2MOO table caches populated by this bridge. */
    public static void releaseDataTables() {
        DataTbls.setLevelDefBinCache(null);
        DataTbls.setLevelTypesTxtCache(null);
        DataTbls.unloadLvlPrestTxt();
        DataTbls.unloadLvlSubTxt();
        DataTbls.unloadLvlMazeTxt();
        DataTbls.unloadLvlWarpTxt();
    }

    /**
     * 从 Riiablo.files.Levels 构建 D2MOO 所需的 LevelDef 缓存。
     */
    private static D2LevelDefBin[] buildLevelDefCache(int diff, int burialGroundsId) {
        List<D2LevelDefBin> records = new ArrayList<>();
        Set<Integer> seenIds = new HashSet<>();
        for (Levels.Entry entry : Riiablo.files.Levels) {
            if (entry == null) continue;
            final int sourceId = entry.Id;
            // Riiablo and D2MOO use the same ids for Act 1 in the current
            // data set; keep the explicit burial remap for forks that do not.
            final int d2Id = sourceId == burialGroundsId
                ? D2LevelIds.LEVEL_BURIALGROUNDS : sourceId;
            if (!seenIds.add(d2Id)) continue;
            if (entry.SizeX == null || entry.SizeY == null
                || entry.SizeX.length < 3 || entry.SizeY.length < 3) {
                D2Log.warning("ACT1_D2MOO_LEVELDEF skip id=%d: missing SizeX/SizeY", sourceId);
                continue;
            }
            final int drlgType;
            try {
                drlgType = toD2MooDrlgType(entry.DrlgType);
            } catch (IllegalArgumentException ignored) {
                D2Log.warning("ACT1_D2MOO_LEVELDEF skip id=%d: unsupported DrlgType=%d",
                    sourceId, entry.DrlgType);
                continue;
            }
            D2LevelDefBin bin = new D2LevelDefBin();
            bin.setDwLevelId(d2Id);
            bin.setDwDrlgType(drlgType);
            bin.setDwLevelType(entry.LevelType);
            bin.setDwQuestFlag(entry.QuestFlag);
            bin.setDwQuestFlagEx(entry.QuestFlagEx);
            bin.setDwLayer(entry.Layer);
            bin.setDwOffsetX(entry.OffsetX);
            bin.setDwOffsetY(entry.OffsetY);
            bin.setDwDepend(entry.Depend);
            bin.setDwSubType(entry.SubType);
            bin.setDwSubTheme(entry.SubTheme);
            bin.setDwSubWaypoint(entry.SubWaypoint);
            bin.setDwSubShrine(entry.SubShrine);
            bin.setDwVis(entry.Vis != null ? entry.Vis.clone() : new int[8]);
            bin.setDwWarp(entry.Warp != null ? entry.Warp.clone() : new int[8]);
            bin.setNIntensity((byte) entry.Intensity);
            bin.setNRed((byte) entry.Red);
            bin.setNGreen((byte) entry.Green);
            bin.setNBlue((byte) entry.Blue);
            bin.setDwPortal(entry.Portal ? 1 : 0);
            bin.setDwPosition(entry.Position ? 1 : 0);
            bin.setDwSaveMonsters(entry.SaveMonsters ? 1 : 0);
            bin.setDwLOSDraw(entry.LOSDraw ? 1 : 0);
            int[] sx = new int[] { entry.SizeX[0], entry.SizeX[1], entry.SizeX[2] };
            int[] sy = new int[] { entry.SizeY[0], entry.SizeY[1], entry.SizeY[2] };
            bin.setDwSizeX(sx);
            bin.setDwSizeY(sy);
            records.add(bin);
            D2Log.debug("ACT1_D2MOO_LEVELDEF id=%d sourceId=%d type=%d levelType=%d size=(%d,%d)/(%d,%d)/(%d,%d) depend=%d subtype=%d subtheme=%d",
                d2Id, sourceId, bin.getDwDrlgType(), bin.getDwLevelType(),
                sx[0], sy[0], sx[1], sy[1], sx[2], sy[2],
                bin.getDwDepend(), bin.getDwSubType(), bin.getDwSubTheme());
        }
        D2Log.debug("ACT1_D2MOO_LEVELDEF cacheRecords=%d burialSourceId=%d", records.size(), burialGroundsId);
        return records.toArray(new D2LevelDefBin[0]);
    }

    private static D2LevelTypesTxt[] buildLevelTypesCache() {
        java.util.ArrayList<D2LevelTypesTxt> cache = new java.util.ArrayList<>();
        for (LvlTypes.Entry source : Riiablo.files.LvlTypes) {
            if (source == null) continue;
            D2LevelTypesTxt target = new D2LevelTypesTxt();
            target.setDwLevelType(source.Id);
            target.setDwAct(source.Act);
            target.setDwExpansion(source.Expansion ? 1 : 0);
            target.setDwBeta(source.Beta ? 1 : 0);
            if (source.File != null) {
                for (int i = 0; i < source.File.length && i < 32; i++) {
                    String path = source.File[i];
                    if (path == null || path.isEmpty()) continue;
                    if (!path.regionMatches(true, 0, "DATA\\", 0, 5)) {
                        path = "DATA\\GLOBAL\\Tiles\\" + path;
                    }
                    target.setSzFile(i, path);
                }
            }
            cache.add(target);
        }
        D2Log.debug("ACT1_D2MOO_LVLTYPES records=%d", cache.size());
        return cache.toArray(new D2LevelTypesTxt[0]);
    }

    /** Convert the riiablo Levels.txt value to the D2MOO 1-based contract. */
    private static int toD2MooDrlgType(int value) {
        switch (value) {
            case 1: return D2DrlgTypes.DRLGTYPE_MAZE;
            case 2: return D2DrlgTypes.DRLGTYPE_PRESET;
            case 3: return D2DrlgTypes.DRLGTYPE_OUTDOOR;
            default:
                throw new IllegalArgumentException("Unsupported Levels.DrlgType=" + value);
        }
    }

    private Act1D2MOOLayoutBridge() {}
}
