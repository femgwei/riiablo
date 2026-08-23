package com.d2moo.common.monsters;

import com.d2moo.common.datatbls.DataTbls;
import com.d2moo.common.datatbls.D2MonStatsTxt;
import com.d2moo.common.util.D2Log;

/**
 * 怪物模块
 * 对应 C++ 模块：MONSTERS
 * 
 * 注意：这是一个怪物管理模块，用于处理怪物的验证和管理
 */
public class Monsters {

    /** Exact value-returning form of D2Common {@code MONSTERS_ValidateMonsterId}. */
    public static int validateMonsterId(int nMonsterId, int monStatsRecordCount) {
        return nMonsterId >= 0 && nMonsterId < monStatsRecordCount ? nMonsterId : -1;
    }

    /** D2Common {@code MONSTERS_GetHirelingExpForNextLevel}. */
    public static int getHirelingExperienceForNextLevel(int level, int experiencePerLevel) {
        return experiencePerLevel * level * level * (level + 1);
    }

    /** Pure formula used by D2Common {@code MONSTERS_GetHirelingResurrectionCost}. */
    public static int getHirelingResurrectionCost(int level) {
        int cost = 15 * level * level / 2;
        return Math.min(cost, 50_000);
    }
    
    /**
     * 验证怪物ID是否有效
     * 对应 C++ MONSTERS_ValidateMonsterId
     * 
     * 功能：
     * 1. 检查怪物ID是否在有效范围内
     * 2. 检查怪物ID是否在 MonStats.txt 数据表中存在
     * 3. 返回验证结果
     * 
     * @param nMonsterId 怪物ID
     * @return 如果怪物ID有效返回 true，否则返回 false
     */
    public static boolean validateMonsterId(int nMonsterId) {
        // 检查怪物ID是否在有效范围内
        // 注意：怪物ID通常从1开始，0通常表示无效
        if (nMonsterId <= 0) {
            D2Log.debug("MONSTERS_ValidateMonsterId: Invalid monster ID (<= 0): " + nMonsterId);
            return false;
        }
        
        // 检查 MonStats.txt 数据表是否已加载
        int recordCount = DataTbls.getMonStatsTxtRecordCount();
        if (recordCount == 0) {
            // 数据表未加载，无法验证，返回 true（假设有效，避免阻塞）
            // 注意：在实际使用中，应该先加载 MonStats.txt 数据表
            D2Log.debug("MONSTERS_ValidateMonsterId: MonStats.txt not loaded, cannot validate monster ID: " + nMonsterId);
            return true; // 假设有效，避免阻塞功能
        }
        
        // 检查怪物ID是否在数据表范围内
        if (nMonsterId > recordCount) {
            D2Log.debug("MONSTERS_ValidateMonsterId: Monster ID out of range: " + nMonsterId + " (max: " + recordCount + ")");
            return false;
        }
        
        // 尝试从数据表获取怪物记录
        D2MonStatsTxt record = DataTbls.getMonStatsTxtRecord(nMonsterId);
        if (record == null) {
            D2Log.debug("MONSTERS_ValidateMonsterId: Monster ID not found in MonStats.txt: " + nMonsterId);
            return false;
        }
        
        // 检查记录是否有效（ID匹配）
        if (record.getDwId() != nMonsterId) {
            D2Log.debug("MONSTERS_ValidateMonsterId: Monster ID mismatch: " + nMonsterId + " (record ID: " + record.getDwId() + ")");
            return false;
        }
        
        // 怪物ID有效
        D2Log.debug("MONSTERS_ValidateMonsterId: Valid monster ID: " + nMonsterId);
        return true;
    }
    
    /**
     * 获取怪物名称
     * 对应 C++ MONSTERS_GetMonsterName
     * 
     * @param nMonsterId 怪物ID
     * @return 怪物名称，如果不存在返回空字符串
     */
    public static String getMonsterName(int nMonsterId) {
        if (nMonsterId <= 0) {
            return "";
        }
        
        D2MonStatsTxt record = DataTbls.getMonStatsTxtRecord(nMonsterId);
        if (record == null) {
            return "";
        }
        
        return record.getSzName();
    }
    
    /**
     * 获取怪物基础类型
     * 对应 C++ MONSTERS_GetMonsterBase
     * 
     * @param nMonsterId 怪物ID
     * @return 怪物基础类型，如果不存在返回空字符串
     */
    public static String getMonsterBase(int nMonsterId) {
        if (nMonsterId <= 0) {
            return "";
        }
        
        D2MonStatsTxt record = DataTbls.getMonStatsTxtRecord(nMonsterId);
        if (record == null) {
            return "";
        }
        
        return record.getSzBase();
    }
}
