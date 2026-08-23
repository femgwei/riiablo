package com.d2moo.common.drlg;

/**
 * DRLG 瓦片导出回调接口，供外部（如 riiablo）消费 D2MOO_JAVA 生成的瓦片数据。
 * 对应“一次性打通 D2MOO_JAVA → 渲染端 TileGrid”的导出契约。
 */
public interface DrlgTileExporter {

    /** D2MapTileFlags::MAPTILE_HIDDEN. */
    int FLAG_HIDDEN = 0x000008;

    /**
     * 每生成一个瓦片时调用。
     *
     * @param levelId D2 关卡 ID（如 LEVEL_BLOODMOOR=2）
     * @param layer   层：0=floor, 1=wall, 2=shadow
     * @param tx      关卡内 tile X（相对关卡左上角）
     * @param ty      关卡内 tile Y（相对关卡左上角）
     * @param tileId  打包的瓦片 ID（orientation&lt;&lt;24 | style&lt;&lt;12 | sequence），或渲染端约定格式
     */
    void onTile(int levelId, int layer, int tx, int ty, int tileId);

    /**
     * Extended callback retaining native visibility flags. Consumers that do
     * not need them keep the original five-argument functional interface.
     */
    default void onTile(int levelId, int layer, int tx, int ty, int tileId, int flags) {
        onTile(levelId, layer, tx, ty, tileId);
    }
}
