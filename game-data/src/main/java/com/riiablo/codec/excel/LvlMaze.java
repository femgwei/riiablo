package com.riiablo.codec.excel;

/**
 * LvlMaze.txt - 迷宫类型地图配置
 * 
 * 用于洞穴、地牢、监狱等迷宫类型地图的生成参数
 * 参考 D2MOD: D2LvlMazeTxt
 */
@Excel.Binned
public class LvlMaze extends Excel<LvlMaze.Entry> {
  public static class Entry extends Excel.Entry {
    @Override
    public String toString() {
      return "LvlMaze[LevelId=" + LevelId + "]";
    }

    /**
     * 关联的 Level ID（对应 Levels.txt 中的 Id）
     */
    @Column
    @Key
    public int LevelId;

    /**
     * 房间数量（按难度：Normal, Nightmare, Hell）
     * 参考 D2MOD: dwRooms[3]
     */
    @Column(format = "Rooms%s", values = {"", "(N)", "(H)"}, endIndex = 3)
    public int Rooms[];

    /**
     * 迷宫尺寸 X（tiles）
     * 参考 D2MOD: dwSizeX
     */
    @Column
    public int SizeX;

    /**
     * 迷宫尺寸 Y（tiles）
     * 参考 D2MOD: dwSizeY
     */
    @Column
    public int SizeY;

    /**
     * 合并标志（用于某些特殊迷宫）
     * 参考 D2MOD: dwMerge
     */
    @Column
    public int Merge;
  }

  /**
   * 根据 LevelId 查找对应的 LvlMaze 记录
   * 参考 D2MOD: DATATBLS_GetLvlMazeTxtRecordFromLevelId
   */
  public Entry getByLevelId(int levelId) {
    for (Excel.Entry entry : orderedEntries) {
      if (entry instanceof Entry) {
        Entry mazeEntry = (Entry) entry;
        if (mazeEntry.LevelId == levelId) {
          return mazeEntry;
        }
      }
    }
    return null;
  }
}
