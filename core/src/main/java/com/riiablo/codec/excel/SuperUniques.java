package com.riiablo.codec.excel;

/**
 * SuperUniques.txt 数据表
 * 
 * 定义超级暗金怪物（Super Unique Monsters）及其属性。
 * 超级暗金怪是固定出现在特定位置的 boss 怪物，拥有固定的基础特殊能力，
 * 在噩梦和地狱难度下会额外获得一到两个能力。
 * 
 * 典型例子：Corpsefire（尸火）、Pindleskin（平得尔斯金）、Nihlathak（尼拉塞克）
 * 
 * 参考：D2MOD D2SuperUniquesTxt 结构
 */
@Excel.Binned
public class SuperUniques extends Excel<SuperUniques.Entry> {
  public static class Entry extends Excel.Entry {
    @Override
    public String toString() {
      return Superunique;
    }
    
    /** 
     * 超级暗金怪的唯一标识符
     * 用于 MonPreset.txt 的 'Place' 字段
     */
    @Key
    @Column
    public String Superunique;
    
    /** 
     * 显示名称（引用字符串表）
     */
    @Column
    public String Name;
    
    /** 
     * 基础怪物类型
     * 指向 MonStats.txt 中的 Id 列
     */
    @Column(format = "Class")
    public String MonClass;
    
    /** 
     * 硬编码索引 (0-65 有特殊逻辑)
     * 大于65的值为自定义超级暗金怪
     */
    @Column
    public int hcIdx;
    
    /** 
     * 怪物音效集（来自 MonSounds.txt）
     */
    @Column
    public String MonSound;
    
    /** 
     * 特殊能力修改器（来自 MonUMod.txt）
     * Mod1, Mod2, Mod3
     */
    @Column(startIndex = 1, endIndex = 4, format = "Mod%d")
    public int[] Mod;
    
    /** 
     * 最小小怪数量
     */
    @Column
    public int MinGrp;
    
    /** 
     * 最大小怪数量
     */
    @Column
    public int MaxGrp;
    
    /** 
     * 是否为资料片内容
     */
    @Column
    public boolean EClass;
    
    /** 
     * 自动定位
     * true = 精确出现在 DS1 文件指定位置
     * false = 在指定位置附近随机范围内生成
     */
    @Column
    public boolean AutoPos;
    
    /** 
     * 是否可以在同一游戏中多次生成
     */
    @Column
    public boolean Stacks;
    
    /**
     * 是否可替换
     */
    @Column
    public boolean Replaceable;
    
    /** 
     * 宝藏类（普通/噩梦/地狱难度）
     */
    @Column(format = "TC")
    public String TC;
    
    @Column(format = "TC(N)")
    public String TCNightmare;
    
    @Column(format = "TC(H)")
    public String TCHell;
    
    /** 
     * 颜色变换索引（普通/噩梦/地狱难度）
     */
    @Column(format = "Utrans")
    public String Utrans;
    
    @Column(format = "Utrans(N)")
    public String UtransNightmare;
    
    @Column(format = "Utrans(H)")
    public String UtransHell;
  }
}
