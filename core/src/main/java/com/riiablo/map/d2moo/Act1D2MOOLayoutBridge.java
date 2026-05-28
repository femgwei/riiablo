package com.riiablo.map.d2moo;

import com.d2moo.common.datatbls.DataTbls;
import com.d2moo.common.datatbls.D2LevelDefBin;
import com.d2moo.common.drlg.D2DrlgAct;
import com.d2moo.common.drlg.D2DrlgCoord;
import com.d2moo.common.drlg.D2DrlgLevel;
import com.d2moo.common.drlg.D2DrlgPresetInfoStrc;
import com.d2moo.common.drlg.D2DrlgStrc;
import com.d2moo.common.drlg.D2LevelIds;
import com.d2moo.common.drlg.DrlgDrlg;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Levels;

/**
 * 使用 D2MOO_JAVA 的 createLevelConnections 生成 Act1 荒野布局，
 * 供 Act1MapBuilderD2MOD 使用，替代自实现的回溯放置逻辑。
 */
public final class Act1D2MOOLayoutBridge {

    /** D2MOO Act1 荒野链接顺序: Stony(4), Cold(3), Blood(2), Town(1), Burial(17) */
    private static final int[] D2MOO_ACT1_LEVEL_IDS = {
        D2LevelIds.LEVEL_STONYFIELD,
        D2LevelIds.LEVEL_COLDPLAINS,
        D2LevelIds.LEVEL_BLOODMOOR,
        D2LevelIds.LEVEL_ROGUEENCAMPMENT,
        D2LevelIds.LEVEL_BURIALGROUNDS,
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
        public final int[][] coords = new int[5][4];
        /** 对应 riiablo 的 level id：0=Stony, 1=Cold, 2=Blood, 3=Town, 4=Burial(使用 burialGroundsId) */
        public final int[] levelIds = new int[5];
        /** levelLink[i]：连接到的上一格索引，-1 表示无 */
        public final int[] levelLink = new int[] { -1, 0, 1, 2, 1 };
        /** levelLinkEx[i] */
        public final int[] levelLinkEx = new int[] { -1, -1, -1, -1, -1 };
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
        try {
            D2LevelDefBin[] cache = buildLevelDefCache(diff, burialGroundsId);
            if (cache == null) return null;
            DataTbls.setLevelDefBinCache(cache);

            D2DrlgAct act = new D2DrlgAct();
            act.setAct((byte) 1);
            act.setTownId(D2LevelIds.LEVEL_ROGUEENCAMPMENT);
            act.setPMemPool(new Object());

            D2DrlgStrc drlg = DrlgDrlg.allocDrlg(
                act,
                (byte) 1,
                null,
                seed,
                D2LevelIds.LEVEL_ROGUEENCAMPMENT,
                0,
                null,
                (byte) diff,
                null,
                null
            );
            if (drlg == null) {
                DataTbls.setLevelDefBinCache(null);
                return null;
            }

            Act1LayoutResult result = new Act1LayoutResult();
            result.levelIds[0] = D2LevelIds.LEVEL_STONYFIELD;
            result.levelIds[1] = D2LevelIds.LEVEL_COLDPLAINS;
            result.levelIds[2] = D2LevelIds.LEVEL_BLOODMOOR;
            result.levelIds[3] = D2LevelIds.LEVEL_ROGUEENCAMPMENT;
            result.levelIds[4] = burialGroundsId;

            for (int i = 0; i < 5; i++) {
                int levelId = D2MOO_ACT1_LEVEL_IDS[i];
                D2DrlgLevel level = DrlgDrlg.getLevel(drlg, levelId);
                if (level == null) {
                    DrlgDrlg.freeDrlg(drlg);
                    DataTbls.setLevelDefBinCache(null);
                    return null;
                }
                D2DrlgCoord c = level.getLevelCoords();
                if (c == null) {
                    DrlgDrlg.freeDrlg(drlg);
                    DataTbls.setLevelDefBinCache(null);
                    return null;
                }
                result.coords[i][0] = c.getNPosX();
                result.coords[i][1] = c.getNPosY();
                result.coords[i][2] = c.getNWidth();
                result.coords[i][3] = c.getNHeight();

                if (levelId == D2LevelIds.LEVEL_ROGUEENCAMPMENT) {
                    D2DrlgPresetInfoStrc preset = level.getPreset();
                    if (preset != null) {
                        result.townDirection = preset.getNDirection();
                    }
                }
            }

            return new LayoutAndDrlg(result, drlg);
        } catch (Throwable t) {
            DataTbls.setLevelDefBinCache(null);
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
        DataTbls.setLevelDefBinCache(null);
        return layoutAndDrlg.result;
    }

    /**
     * 从 Riiablo.files.Levels 构建 D2MOO 所需的 LevelDef 缓存。
     */
    private static D2LevelDefBin[] buildLevelDefCache(int diff, int burialGroundsId) {
        int[] ids = { 1, 2, 3, 4, 17 };
        D2LevelDefBin[] cache = new D2LevelDefBin[ids.length];
        for (int i = 0; i < ids.length; i++) {
            int rid = ids[i] == 17 ? burialGroundsId : ids[i];
            Levels.Entry entry = Riiablo.files.Levels.get(rid);
            if (entry == null) return null;
            D2LevelDefBin bin = new D2LevelDefBin();
            bin.setDwLevelId(ids[i]);
            bin.setDwDrlgType(entry.DrlgType);
            bin.setDwLevelType(entry.LevelType);
            bin.setDwOffsetX(entry.OffsetX);
            bin.setDwOffsetY(entry.OffsetY);
            int[] sx = new int[] { entry.SizeX[0], entry.SizeX[1], entry.SizeX[2] };
            int[] sy = new int[] { entry.SizeY[0], entry.SizeY[1], entry.SizeY[2] };
            bin.setDwSizeX(sx);
            bin.setDwSizeY(sy);
            cache[i] = bin;
        }
        return cache;
    }

    private Act1D2MOOLayoutBridge() {}
}
