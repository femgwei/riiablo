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
import com.d2moo.common.drlg.DrlgExport;
import com.d2moo.common.util.D2Log;
import com.d2moo.common.util.D2FileReader;
import com.d2moo.common.util.D2MemoryPool;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.LvlTypes;
import com.riiablo.map.Map;

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
        D2LevelIds.LEVEL_MONASTERYGATE,
        D2LevelIds.LEVEL_OUTERCLOISTER,
        D2LevelIds.LEVEL_BARRACKS,
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
        public final int[][] coords;
        /** 0=Stony, 1=Cold, 2=Blood, 3=Town, 4=Burial, 5=Black Marsh,
         * 6=Tamoe, 7=Dark Wood, 8=Underground Passage level 1,
         * 9=Monastery Gate, 10=Outer Cloister, 11=Barracks. Native linked
         * sublevels discovered from actual warp specials follow those entries. */
        public final int[] levelIds;
        /** levelLink[i]：连接到的上一格索引，-1 表示无 */
        public final int[] levelLink;
        /** levelLinkEx[i] */
        public final int[] levelLinkEx;
        /** 城镇出口方向 0–3 (D2MOD rand[0][townIndex])，用于预设选择与路径 */
        public int townDirection;

        public Act1LayoutResult() {
            this(D2MOO_ACT1_LEVEL_IDS.length);
        }

        public Act1LayoutResult(int levelCount) {
            coords = new int[levelCount][4];
            levelIds = new int[levelCount];
            levelLink = new int[levelCount];
            levelLinkEx = new int[levelCount];
            java.util.Arrays.fill(levelLink, -1);
            java.util.Arrays.fill(levelLinkEx, -1);
            // Wilderness chain and detached Underground Passage links.
            if (levelCount > 1) levelLink[1] = 0;
            if (levelCount > 2) levelLink[2] = 1;
            if (levelCount > 3) levelLink[3] = 2;
            if (levelCount > 4) levelLink[4] = 1;
            if (levelCount > 9) {
                levelLink[5] = 6;
                levelLink[6] = 9;
                levelLink[7] = 5;
            } else {
                if (levelCount > 5) levelLink[5] = 7;
                if (levelCount > 6) levelLink[6] = 5;
            }
            if (levelCount > 8) {
                levelLink[8] = 0;
                levelLinkEx[8] = 7;
            }
            // Monastery Gate -> Outer Cloister -> Barracks seamless chain.
            if (levelCount > 10) levelLink[10] = 9;
            if (levelCount > 11) levelLink[11] = 10;
        }
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

            List<Integer> generatedLevelIds = discoverNativeLinkedLevels(drlg);
            Act1LayoutResult result = new Act1LayoutResult(generatedLevelIds.size());

            for (int i = 0; i < generatedLevelIds.size(); i++) {
                int levelId = generatedLevelIds.get(i);
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
                result.levelIds[i] = levelId == D2LevelIds.LEVEL_BURIALGROUNDS
                    ? burialGroundsId : levelId;
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
     * Discovers detached Act I maps from warp specials that were actually
     * selected by native DRLG generation. Levels.txt alone is insufficient:
     * it lists possible routes, while the special wall proves that an entrance
     * exists in this seed. Requiring the reverse Vis route rejects ordinary
     * orientation-10 automap/pop-pad markers.
     */
    static List<Integer> discoverNativeLinkedLevels(D2DrlgStrc drlg) {
        List<Integer> generated = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (int levelId : D2MOO_ACT1_LEVEL_IDS) {
            generated.add(levelId);
            seen.add(levelId);
        }

        for (int cursor = 0; cursor < generated.size(); cursor++) {
            int sourceLevelId = generated.get(cursor);
            Levels.Entry source = Riiablo.files.Levels.get(sourceLevelId);
            if (source == null || source.Vis == null || source.Warp == null) continue;

            Set<Integer> targets = new java.util.LinkedHashSet<>();
            DrlgExport.exportLevelTiles(drlg, sourceLevelId,
                (exportLevelId, layer, tx, ty, tileId) -> {
                    if (layer != DrlgExport.LAYER_WALL) return;
                    int orientation = (tileId >>> 24) & 0xFF;
                    if (orientation != 10 && orientation != 11) return;
                    int riiabloTileId = D2MooTileApplier.toRiiabloTileIndex(tileId);
                    if (!Map.ID.isWarp(riiabloTileId)) return;
                    int mainIndex = (tileId >>> 12) & 0xFFF;
                    int subIndex = tileId & 0xFFF;
                    if (subIndex == 1 || mainIndex < 0
                        || mainIndex >= source.Vis.length
                        || mainIndex >= source.Warp.length
                        || source.Warp[mainIndex] < 0) return;
                    int targetLevelId = source.Vis[mainIndex];
                    Levels.Entry target = Riiablo.files.Levels.get(targetLevelId);
                    if (targetLevelId <= 0 || target == null || target.Act != 0
                        || !hasReverseVis(target, sourceLevelId)) return;
                    targets.add(targetLevelId);
                });

            for (int targetLevelId : targets) {
                if (!seen.add(targetLevelId)) continue;
                try {
                    D2DrlgLevel target = DrlgDrlg.getLevel(drlg, targetLevelId);
                    if (target == null) {
                        D2Log.warning("ACT1_D2MOO_LINKED skip source=%d target=%d: allocation failed",
                            sourceLevelId, targetLevelId);
                        continue;
                    }
                    if (target.getFirstRoomEx() == null) DrlgDrlg.initLevel(target);
                    if (target.getFirstRoomEx() == null || target.getRooms() <= 0
                        || target.getLevelCoords() == null) {
                        D2Log.warning("ACT1_D2MOO_LINKED skip source=%d target=%d: no generated rooms",
                            sourceLevelId, targetLevelId);
                        continue;
                    }
                    if (!hasWarpSpecialTo(drlg, targetLevelId, sourceLevelId)) {
                        D2Log.warning("ACT1_D2MOO_LINKED skip source=%d target=%d: no reverse warp special",
                            sourceLevelId, targetLevelId);
                        continue;
                    }
                    generated.add(targetLevelId);
                    D2DrlgCoord c = target.getLevelCoords();
                    D2Log.debug("ACT1_D2MOO_LINKED source=%d target=%d coord=(%d,%d %dx%d) rooms=%d",
                        sourceLevelId, targetLevelId, c.getNPosX(), c.getNPosY(),
                        c.getNWidth(), c.getNHeight(), target.getRooms());
                } catch (Throwable t) {
                    D2Log.warning("ACT1_D2MOO_LINKED skip source=%d target=%d: %s",
                        sourceLevelId, targetLevelId, t.toString());
                    if (Boolean.getBoolean("riiablo.drlg.stacktrace")) t.printStackTrace();
                }
            }
        }
        D2Log.debug("ACT1_D2MOO_LINKED summary levels=%s", generated.toString());
        return generated;
    }

    private static boolean hasReverseVis(Levels.Entry target, int sourceLevelId) {
        if (target.Vis == null) return false;
        for (int vis : target.Vis) if (vis == sourceLevelId) return true;
        return false;
    }

    private static boolean hasWarpSpecialTo(
            D2DrlgStrc drlg, int levelId, int destinationLevelId) {
        Levels.Entry level = Riiablo.files.Levels.get(levelId);
        if (level == null || level.Vis == null || level.Warp == null) return false;
        boolean[] found = { false };
        DrlgExport.exportLevelTiles(drlg, levelId,
            (exportLevelId, layer, tx, ty, tileId) -> {
                if (found[0] || layer != DrlgExport.LAYER_WALL) return;
                int orientation = (tileId >>> 24) & 0xFF;
                if (orientation != 10 && orientation != 11) return;
                int riiabloTileId = D2MooTileApplier.toRiiabloTileIndex(tileId);
                if (!Map.ID.isWarp(riiabloTileId)) return;
                int mainIndex = (tileId >>> 12) & 0xFFF;
                int subIndex = tileId & 0xFFF;
                if (subIndex != 1 && mainIndex >= 0
                    && mainIndex < level.Vis.length
                    && mainIndex < level.Warp.length
                    && level.Warp[mainIndex] >= 0
                    && level.Vis[mainIndex] == destinationLevelId) {
                    found[0] = true;
                }
            });
        return found[0];
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
