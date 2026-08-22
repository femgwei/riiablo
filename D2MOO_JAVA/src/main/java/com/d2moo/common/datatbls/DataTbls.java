package com.d2moo.common.datatbls;

import com.d2moo.common.drlg.D2LvlWarpTxt;
import com.d2moo.common.util.D2Log;
import com.d2moo.common.util.D2FileReader;
import com.d2moo.common.util.D2BinaryReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 数据表工具类
 * 对应 C++ 模块：DATATBLS
 * 
 * 注意：这是一个数据表查询模块，实际数据需要从数据文件加载
 * 当前实现提供基础框架和接口，实际数据加载需要后续实现
 */
public class DataTbls {
    
    // 数据表缓存（占位符，实际需要从文件加载）
    private static D2LevelTypesTxt[] levelTypesTxtCache;
    private static D2LvlWarpTxt[] lvlWarpTxtCache;
    private static D2LevelDefBin[] levelDefBinCache;
    private static D2LvlPrestTxt[] lvlPrestTxtCache;
    private static D2LvlMazeTxt[] lvlMazeTxtCache;
    private static D2LvlSubTxt[] lvlSubTxtCache;
    private static D2LevelsTxt[] levelsTxtCache;
    private static D2MonStatsTxt[] monStatsTxtCache;
    private static D2SuperUniquesTxt[] superUniquesTxtCache;
    
    /**
     * D2Common.0x6FD61460 (#10023)
     * 获取关卡类型文本记录
     * @param nLevelType 关卡类型ID
     * @return 关卡类型文本记录，如果不存在返回 null
     */
    public static D2LevelTypesTxt getLevelTypesTxtRecord(int nLevelType) {
        // 检查数据表是否已加载
        if (levelTypesTxtCache == null) {
            // 数据表未加载，返回 null（不输出警告，因为这是正常的）
            return null;
        }
        
        // 简单的线性查找（实际应该使用哈希表或索引）
        for (D2LevelTypesTxt record : levelTypesTxtCache) {
            if (record != null && record.getDwLevelType() == nLevelType) {
                return record;
            }
        }
        
        // 记录不存在
        return null;
    }

    /** Installs an externally decoded LvlTypes table (for example, riiablo's). */
    public static void setLevelTypesTxtCache(D2LevelTypesTxt[] cache) {
        levelTypesTxtCache = cache;
    }
    
    /**
     * D2Common.0x6FD60D90 (#10010)
     * 获取关卡定义记录
     * @param nLevelId 关卡ID
     * @return 关卡定义记录，如果不存在返回 null
     */
    public static D2LevelDefBin getLevelDefRecord(int nLevelId) {
        // 检查数据表是否已加载
        if (levelDefBinCache == null) {
            // 数据表未加载，返回 null（不输出警告，因为这是正常的）
            return null;
        }
        
        // 简单的线性查找（实际应该使用哈希表或索引）
        for (D2LevelDefBin record : levelDefBinCache) {
            if (record != null && record.getDwLevelId() == nLevelId) {
                return record;
            }
        }
        
        // 记录不存在
        return null;
    }
    
    /**
     * D2Common.0x6FD61B50 (#10024)
     * 获取关卡预设文本记录
     * @param nId 预设ID
     * @return 关卡预设文本记录，如果不存在返回 null
     */
    public static D2LvlPrestTxt getLvlPrestTxtRecord(int nId) {
        // 检查数据表是否已加载
        if (lvlPrestTxtCache == null) {
            // 数据表未加载，返回 null（不输出警告，因为这是正常的）
            return null;
        }
        
        // 简单的线性查找
        for (D2LvlPrestTxt record : lvlPrestTxtCache) {
            if (record != null && record.getDwDef() == nId) {
                return record;
            }
        }
        
        // 记录不存在
        return null;
    }
    
    /**
     * 从关卡ID获取关卡预设文本记录
     * @param nLevelId 关卡ID
     * @return 关卡预设文本记录，如果不存在返回 null
     */
    public static D2LvlPrestTxt getLvlPrestTxtRecordFromLevelId(int nLevelId) {
        // 检查数据表是否已加载
        if (lvlPrestTxtCache == null) {
            // 数据表未加载，返回 null（不输出警告，因为这是正常的）
            return null;
        }
        
        // 简单的线性查找
        for (D2LvlPrestTxt record : lvlPrestTxtCache) {
            if (record != null && record.getDwLevelId() == nLevelId) {
                return record;
            }
        }
        
        // 记录不存在
        return null;
    }
    
    /**
     * D2Common.0x6FD60DC0
     * 从关卡ID和方向获取传送点文本记录
     * @param nLevelId 关卡ID
     * @param szDirection 方向字符
     * @return 传送点文本记录，如果不存在返回 null
     */
    public static D2LvlWarpTxt getLvlWarpTxtRecordFromLevelIdAndDirection(int nLevelId, char szDirection) {
        // 检查数据表是否已加载
        if (lvlWarpTxtCache == null) {
            // 数据表未加载，返回 null（不输出警告，因为这是正常的）
            return null;
        }
        
        // 查找匹配的传送点记录
        for (D2LvlWarpTxt record : lvlWarpTxtCache) {
            if (record != null && record.getDwLevelId() == nLevelId) {
                String direction = record.getSzDirection();
                if (direction != null && direction.length() > 0 && direction.charAt(0) == szDirection) {
                    return record;
                }
            }
        }
        
        // 记录不存在
        return null;
    }
    
    /**
     * 加载关卡类型文本表
     * 对应 C++ DATATBLS_LoadLevelTypesTxt
     * 
     * @param archive 存档句柄
     */
    public static void loadLevelTypesTxt(Object archive) {
        String fileName = "DATA\\GLOBAL\\EXCEL\\LevelTypes.txt";
        
        // 读取文件数据
        byte[] fileData = D2FileReader.readFile(archive, fileName);
        if (fileData == null || fileData.length == 0) {
            D2Log.warning("DATATBLS_LoadLevelTypesTxt: Failed to read file: " + fileName);
            return;
        }
        
        // 解析文本表（TXT 格式通常是制表符分隔的文本文件）
        List<String[]> rows = D2TxtFileParser.parseTxtFile(fileData);
        if (rows.isEmpty()) {
            D2Log.warning("DATATBLS_LoadLevelTypesTxt: No data rows found in file");
            return;
        }
        
        // 第一行是表头，跳过
        List<D2LevelTypesTxt> records = new ArrayList<>();
        
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (row.length == 0) {
                continue;
            }
            
            D2LevelTypesTxt record = new D2LevelTypesTxt();
            
            // 解析字段（根据实际 TXT 文件格式调整）
            // LevelType, File1, File2, ..., Act, Expansion, Beta
            int colIndex = 0;
            if (colIndex < row.length) {
                record.setDwLevelType(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // 解析文件名数组（最多 32 个）
            String[] files = new String[32];
            for (int fileIndex = 0; fileIndex < 32 && colIndex < row.length; fileIndex++) {
                files[fileIndex] = D2TxtFileParser.parseString(row[colIndex++], null);
            }
            record.setSzFile(files);
            
            // Act
            if (colIndex < row.length) {
                record.setDwAct(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // Expansion
            if (colIndex < row.length) {
                record.setDwExpansion(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // Beta
            if (colIndex < row.length) {
                record.setDwBeta(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            records.add(record);
        }
        
        levelTypesTxtCache = records.toArray(new D2LevelTypesTxt[0]);
        D2Log.debug("DATATBLS_LoadLevelTypesTxt: Loaded " + levelTypesTxtCache.length + " records");
    }
    
    /**
     * 卸载关卡类型文本表
     */
    public static void unloadLevelTypesTxt() {
        levelTypesTxtCache = null;
    }
    
    /**
     * 加载关卡定义二进制表
     * 对应 C++ DATATBLS_LoadLevelDefsBin
     * 
     * @param archive 存档句柄
     */
    public static void loadLevelDefsBin(Object archive) {
        String fileName = "DATA\\GLOBAL\\EXCEL\\Levels.bin";
        
        // 读取文件数据
        byte[] fileData = D2FileReader.readFile(archive, fileName);
        if (fileData == null || fileData.length == 0) {
            D2Log.warning("DATATBLS_LoadLevelDefsBin: Failed to read file: " + fileName);
            return;
        }
        
        // 解析二进制表（BIN 格式通常是二进制结构数组）
        // Diablo 2 的 BIN 文件格式：
        // - 文件头：可能包含记录数量或其他元数据
        // - 数据记录：固定大小的结构体数组
        // 
        // 注意：Levels.bin 的结构体大小需要根据实际文件格式确定
        // 当前实现：使用 D2BinaryReader 解析二进制数据
        
        List<D2LevelDefBin> records = new ArrayList<>();
        
        // 尝试解析记录（假设结构体大小为固定值，需要根据实际文件格式调整）
        // 典型的 D2LevelDefBin 结构体大小约为 200+ 字节
        final int RECORD_SIZE = 256; // 需要根据实际文件格式调整
        
        int offset = 0;
        while (offset + RECORD_SIZE <= fileData.length) {
            try {
                D2LevelDefBin record = new D2LevelDefBin();
                
                // 解析字段（根据实际 BIN 文件格式调整）
                // 注意：字段顺序和大小需要与 C++ 结构体对齐
                record.setDwLevelId(D2BinaryReader.readInt32(fileData, offset)); offset += 4;
                record.setDwDrlgType(D2BinaryReader.readInt32(fileData, offset)); offset += 4;
                record.setDwLevelType(D2BinaryReader.readInt32(fileData, offset)); offset += 4;
                record.setDwPopulate(D2BinaryReader.readInt32(fileData, offset)); offset += 4;
                record.setDwLogicals(D2BinaryReader.readInt32(fileData, offset)); offset += 4;
                record.setDwOutdoors(D2BinaryReader.readInt32(fileData, offset)); offset += 4;
                record.setDwAnimate(D2BinaryReader.readInt32(fileData, offset)); offset += 4;
                record.setDwKillEdge(D2BinaryReader.readInt32(fileData, offset)); offset += 4;
                record.setDwFillBlanks(D2BinaryReader.readInt32(fileData, offset)); offset += 4;
                // dwSizeX 和 dwSizeY 是数组，按难度索引（3个值）
                record.setDwSizeX(0, D2BinaryReader.readInt32(fileData, offset)); offset += 4;
                record.setDwSizeX(1, D2BinaryReader.readInt32(fileData, offset)); offset += 4;
                record.setDwSizeX(2, D2BinaryReader.readInt32(fileData, offset)); offset += 4;
                record.setDwSizeY(0, D2BinaryReader.readInt32(fileData, offset)); offset += 4;
                record.setDwSizeY(1, D2BinaryReader.readInt32(fileData, offset)); offset += 4;
                record.setDwSizeY(2, D2BinaryReader.readInt32(fileData, offset)); offset += 4;
                record.setDwAutoMap(D2BinaryReader.readInt32(fileData, offset)); offset += 4;
                record.setDwScan(D2BinaryReader.readInt32(fileData, offset)); offset += 4;
                record.setDwPops(D2BinaryReader.readInt32(fileData, offset)); offset += 4;
                record.setDwPopPad(D2BinaryReader.readInt32(fileData, offset)); offset += 4;
                record.setDwFiles(D2BinaryReader.readInt32(fileData, offset)); offset += 4;
                record.setDwFileId(D2BinaryReader.readInt32(fileData, offset)); offset += 4;
                record.setDwDt1Mask(D2BinaryReader.readInt32(fileData, offset)); offset += 4;
                record.setDwBeta(D2BinaryReader.readInt32(fileData, offset)); offset += 4;
                
                // 解析传送门数组（8 个整数）
                int[] warp = new int[8];
                for (int i = 0; i < 8; i++) {
                    warp[i] = D2BinaryReader.readInt32(fileData, offset);
                    offset += 4;
                }
                record.setDwWarp(warp);
                
                records.add(record);
                
                // 跳过剩余字节到下一个记录
                offset = (offset / RECORD_SIZE + 1) * RECORD_SIZE;
            } catch (Exception e) {
                D2Log.warning("DATATBLS_LoadLevelDefsBin: Failed to parse record at offset " + offset + ": " + e.getMessage());
                break;
            }
        }
        
        levelDefBinCache = records.toArray(new D2LevelDefBin[0]);
        D2Log.debug("DATATBLS_LoadLevelDefsBin: Loaded " + levelDefBinCache.length + " records");
    }
    
    /**
     * 卸载关卡定义二进制表
     */
    public static void unloadLevelDefsBin() {
        levelDefBinCache = null;
    }

    /**
     * 注入关卡定义缓存（供外部如 riiablo 从 Levels.txt 填充后传入）
     */
    public static void setLevelDefBinCache(D2LevelDefBin[] cache) {
        levelDefBinCache = cache;
    }
    
    /**
     * 加载关卡预设文本表
     * 对应 C++ DATATBLS_LoadLvlPrestTxt
     * 
     * @param archive 存档句柄
     * @param a2 参数2（可能用于指定加载选项或版本）
     */
    public static void loadLvlPrestTxt(Object archive, int a2) {
        String fileName = "DATA\\GLOBAL\\EXCEL\\LvlPrest.txt";
        
        // 读取文件数据
        byte[] fileData = D2FileReader.readFile(archive, fileName);
        if (fileData == null || fileData.length == 0) {
            D2Log.warning("DATATBLS_LoadLvlPrestTxt: Failed to read file: " + fileName);
            return;
        }
        
        // 解析文本表（TXT 格式通常是制表符分隔的文本文件）
        List<String[]> rows = D2TxtFileParser.parseTxtFile(fileData);
        if (rows.isEmpty()) {
            D2Log.warning("DATATBLS_LoadLvlPrestTxt: No data rows found in file");
            return;
        }
        
        Map<String, Integer> columns = createColumnIndex(rows.get(0));
        List<D2LvlPrestTxt> records = new ArrayList<>();
        
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (row.length == 0) {
                continue;
            }
            
            D2LvlPrestTxt record = new D2LvlPrestTxt();
            
            record.setDwDef(parseColumnInt(row, columns, "Def", 0));
            record.setDwLevelId(parseColumnInt(row, columns, "LevelId", 0));
            record.setDwPopulate(parseColumnInt(row, columns, "Populate", 0));
            record.setDwLogicals(parseColumnInt(row, columns, "Logicals", 0));
            record.setDwOutdoors(parseColumnInt(row, columns, "Outdoors", 0));
            record.setDwAnimate(parseColumnInt(row, columns, "Animate", 0));
            record.setDwKillEdge(parseColumnInt(row, columns, "KillEdge", 0));
            record.setDwFillBlanks(parseColumnInt(row, columns, "FillBlanks", 0));
            record.setDwExpansion(parseColumnInt(row, columns, "Expansion", 0));
            record.setNAnimSpeed(parseColumnInt(row, columns, "AnimSpeed", 0));
            record.setDwSizeX(parseColumnInt(row, columns, "SizeX", 0));
            record.setDwSizeY(parseColumnInt(row, columns, "SizeY", 0));
            record.setDwAutoMap(parseColumnInt(row, columns, "AutoMap", 0));
            record.setDwScan(parseColumnInt(row, columns, "Scan", 0));
            record.setDwPops(parseColumnInt(row, columns, "Pops", 0));
            record.setDwPopPad(parseColumnInt(row, columns, "PopPad", 0));
            record.setDwFiles(parseColumnInt(row, columns, "Files", 0));
            record.setDwDt1Mask(parseColumnInt(row, columns, "Dt1Mask", 0));

            String[] files = new String[6];
            for (int fileIndex = 0; fileIndex < files.length; fileIndex++) {
                files[fileIndex] = parseColumnString(row, columns, "File" + (fileIndex + 1), null);
            }
            record.setSzFile(files);
            record.setDwBeta(parseColumnInt(row, columns, "Beta", 0));
            
            records.add(record);
        }
        
        lvlPrestTxtCache = records.toArray(new D2LvlPrestTxt[0]);
        D2Log.debug("DATATBLS_LoadLvlPrestTxt: Loaded " + lvlPrestTxtCache.length + " records, a2: " + a2);
    }
    
    /**
     * 卸载关卡预设文本表
     */
    public static void unloadLvlPrestTxt() {
        lvlPrestTxtCache = null;
    }
    
    /**
     * 加载传送点文本表
     * 对应 C++ DATATBLS_LoadLvlWarpTxt
     * 
     * @param archive 存档句柄
     */
    public static void loadLvlWarpTxt(Object archive) {
        String fileName = "DATA\\GLOBAL\\EXCEL\\LvlWarp.txt";
        
        // 读取文件数据
        byte[] fileData = D2FileReader.readFile(archive, fileName);
        if (fileData == null || fileData.length == 0) {
            D2Log.warning("DATATBLS_LoadLvlWarpTxt: Failed to read file: " + fileName);
            return;
        }
        
        // 解析文本表（TXT 格式通常是制表符分隔的文本文件）
        List<String[]> rows = D2TxtFileParser.parseTxtFile(fileData);
        if (rows.isEmpty()) {
            D2Log.warning("DATATBLS_LoadLvlWarpTxt: No data rows found in file");
            return;
        }
        
        // 第一行是表头，跳过
        List<D2LvlWarpTxt> records = new ArrayList<>();
        
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (row.length == 0) {
                continue;
            }
            
            D2LvlWarpTxt record = new D2LvlWarpTxt();
            
            // 解析字段（根据实际 TXT 文件格式调整）
            // Level, Warp, SelectX, SelectY, SelectDX, SelectDY, ExitWalkX, ExitWalkY, OffsetX, OffsetY, LitVersion, Tiles, Direction
            int colIndex = 0;
            if (colIndex < row.length) {
                record.setDwLevelId(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            // Warp 字段可能不存在，跳过
            if (colIndex < row.length) {
                colIndex++; // 跳过 Warp 字段
            }
            if (colIndex < row.length) {
                record.setDwSelectX(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwSelectY(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwSelectDX(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwSelectDY(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwExitWalkX(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwExitWalkY(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwOffsetX(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwOffsetY(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwLitVersion(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwTiles(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setSzDirection(D2TxtFileParser.parseString(row[colIndex++], ""));
            }
            
            records.add(record);
        }
        
        lvlWarpTxtCache = records.toArray(new D2LvlWarpTxt[0]);
        D2Log.debug("DATATBLS_LoadLvlWarpTxt: Loaded " + lvlWarpTxtCache.length + " records");
    }
    
    /**
     * 卸载传送点文本表
     */
    public static void unloadLvlWarpTxt() {
        lvlWarpTxtCache = null;
    }
    
    /**
     * 加载关卡文本表
     * 对应 C++ DATATBLS_LoadLevelsTxt
     * 
     * @param archive 存档句柄
     */
    public static void loadLevelsTxt(Object archive) {
        String fileName = "DATA\\GLOBAL\\EXCEL\\Levels.txt";
        
        // 读取文件数据
        byte[] fileData = D2FileReader.readFile(archive, fileName);
        if (fileData == null || fileData.length == 0) {
            D2Log.warning("DATATBLS_LoadLevelsTxt: Failed to read file: " + fileName);
            return;
        }
        
        // 解析文本表（TXT 格式通常是制表符分隔的文本文件）
        List<String[]> rows = D2TxtFileParser.parseTxtFile(fileData);
        if (rows.isEmpty()) {
            D2Log.warning("DATATBLS_LoadLevelsTxt: No data rows found in file");
            return;
        }
        
        // Levels.txt 第一行是表头，跳过
        if (rows.size() < 2) {
            D2Log.warning("DATATBLS_LoadLevelsTxt: File contains only header, no data rows");
            return;
        }
        
        List<D2LevelsTxt> records = new ArrayList<>();
        
        // 从第二行开始解析数据（第一行是表头）
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (row.length == 0) {
                continue; // 跳过空行
            }
            
            D2LevelsTxt record = new D2LevelsTxt();
            int colIndex = 0;
            
            // 解析字段（根据实际 Levels.txt 文件格式调整字段顺序）
            // 注意：字段顺序和数量需要根据实际文件格式确定
            // 这里实现一个通用的解析框架，支持常见字段
            
            // LevelId
            if (colIndex < row.length) {
                record.setDwLevelId(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // Name
            if (colIndex < row.length) {
                record.setSzName(D2TxtFileParser.parseString(row[colIndex++], ""));
            }
            
            // Id
            if (colIndex < row.length) {
                record.setDwId(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // Pal
            if (colIndex < row.length) {
                record.setDwPal(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // Act
            if (colIndex < row.length) {
                record.setDwAct(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // Teleport
            if (colIndex < row.length) {
                record.setDwTeleport(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // Rain
            if (colIndex < row.length) {
                record.setDwRain(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // Mud
            if (colIndex < row.length) {
                record.setDwMud(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // NoPer
            if (colIndex < row.length) {
                record.setDwNoPer(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // IsInside
            if (colIndex < row.length) {
                record.setDwIsInside(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // DrawEdges
            if (colIndex < row.length) {
                record.setDwDrawEdges(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // DrlgType
            if (colIndex < row.length) {
                record.setDwDrlgType(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // LevelType
            if (colIndex < row.length) {
                record.setDwLevelType(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // SubType
            if (colIndex < row.length) {
                record.setDwSubType(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // SubTheme
            if (colIndex < row.length) {
                record.setDwSubTheme(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // SubWaypoint
            if (colIndex < row.length) {
                record.setDwSubWaypoint(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // SubShrine
            if (colIndex < row.length) {
                record.setDwSubShrine(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // Vis0-Vis7 (可见性数组)
            if (colIndex < row.length) {
                record.setDwVis0(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwVis1(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwVis2(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwVis3(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwVis4(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwVis5(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwVis6(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwVis7(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // SizeX, SizeY
            if (colIndex < row.length) {
                record.setDwSizeX(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwSizeY(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // SizeX_N, SizeY_N
            if (colIndex < row.length) {
                record.setDwSizeX_N(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwSizeY_N(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // OffsetX, OffsetY
            if (colIndex < row.length) {
                record.setDwOffsetX(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwOffsetY(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // Depend
            if (colIndex < row.length) {
                record.setDwDepend(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // Quest, QuestDiff
            if (colIndex < row.length) {
                record.setDwQuest(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwQuestDiff(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // Layer
            if (colIndex < row.length) {
                record.setDwLayer(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // Map
            if (colIndex < row.length) {
                record.setDwMap(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // MonLvl1, MonLvl2, MonLvl3
            if (colIndex < row.length) {
                record.setDwMonLvl1(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwMonLvl2(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwMonLvl3(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // MonLvl1Ex, MonLvl2Ex, MonLvl3Ex
            if (colIndex < row.length) {
                record.setDwMonLvl1Ex(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwMonLvl2Ex(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwMonLvl3Ex(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // MonDen, MonDen_N, MonDen_H, MonDen_NH
            if (colIndex < row.length) {
                record.setDwMonDen(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwMonDen_N(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwMonDen_H(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwMonDen_NH(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // MonUMin, MonUMax
            if (colIndex < row.length) {
                record.setDwMonUMin(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwMonUMax(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // MonWndr, MonSpcWalk
            if (colIndex < row.length) {
                record.setDwMonWndr(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwMonSpcWalk(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // NumMon
            if (colIndex < row.length) {
                record.setDwNumMon(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // Beta
            if (colIndex < row.length) {
                record.setDwBeta(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            records.add(record);
        }
        
        levelsTxtCache = records.toArray(new D2LevelsTxt[0]);
        D2Log.debug("DATATBLS_LoadLevelsTxt: Loaded " + levelsTxtCache.length + " records");
    }
    
    /**
     * 卸载关卡文本表
     * 对应 C++ DATATBLS_UnloadLevelsTxt
     */
    public static void unloadLevelsTxt() {
        // 清理关卡文本表相关的缓存
        levelsTxtCache = null;
        D2Log.debug("DATATBLS_UnloadLevelsTxt: Unloaded levels text table");
    }
    
    /**
     * 获取关卡文本记录
     * @param nLevelId 关卡ID
     * @return 关卡文本记录，如果不存在返回 null
     */
    public static D2LevelsTxt getLevelsTxtRecord(int nLevelId) {
        // 检查数据表是否已加载
        if (levelsTxtCache == null) {
            // 数据表未加载，返回 null（不输出警告，因为这是正常的）
            return null;
        }
        
        // 简单的线性查找（实际应该使用哈希表或索引）
        for (D2LevelsTxt record : levelsTxtCache) {
            if (record != null && record.getDwLevelId() == nLevelId) {
                return record;
            }
        }
        
        // 记录不存在
        return null;
    }
    
    /**
     * 获取 MonStatsTxt 记录数量
     * @return MonStatsTxt 记录数量
     */
    /**
     * 加载怪物统计文本表
     * 对应 C++ DATATBLS_LoadMonStatsTxt
     * 
     * @param archive 存档句柄
     */
    public static void loadMonStatsTxt(Object archive) {
        String fileName = "DATA\\GLOBAL\\EXCEL\\MonStats.txt";
        
        // 读取文件数据
        byte[] fileData = D2FileReader.readFile(archive, fileName);
        if (fileData == null || fileData.length == 0) {
            D2Log.warning("DATATBLS_LoadMonStatsTxt: Failed to read file: " + fileName);
            return;
        }
        
        // 解析文本表
        List<String[]> rows = D2TxtFileParser.parseTxtFile(fileData);
        if (rows.isEmpty()) {
            D2Log.warning("DATATBLS_LoadMonStatsTxt: No data rows found in file");
            return;
        }
        
        // MonStats.txt 第一行是表头，跳过
        if (rows.size() < 2) {
            D2Log.warning("DATATBLS_LoadMonStatsTxt: File contains only header, no data rows");
            return;
        }
        
        List<D2MonStatsTxt> records = new ArrayList<>();
        
        // 从第二行开始解析数据（第一行是表头）
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (row.length == 0) {
                continue; // 跳过空行
            }
            
            D2MonStatsTxt record = new D2MonStatsTxt();
            int colIndex = 0;
            
            // 解析字段（根据实际 MonStats.txt 文件格式调整字段顺序）
            // 注意：MonStats.txt 包含大量字段，这里实现主要字段的解析
            
            // Id
            if (colIndex < row.length) {
                record.setDwId(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // Name
            if (colIndex < row.length) {
                record.setSzName(D2TxtFileParser.parseString(row[colIndex++], ""));
            }
            
            // Base
            if (colIndex < row.length) {
                record.setSzBase(D2TxtFileParser.parseString(row[colIndex++], ""));
            }
            
            // MinGrp, MaxGrp
            if (colIndex < row.length) {
                record.setDwMinGrp(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwMaxGrp(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // Level, LevelEx
            if (colIndex < row.length) {
                record.setDwLevel(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwLevelEx(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // MinHP, MaxHP
            if (colIndex < row.length) {
                record.setDwMinHP(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwMaxHP(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // AC, Exp
            if (colIndex < row.length) {
                record.setDwAC(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwExp(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            records.add(record);
        }
        
        monStatsTxtCache = records.toArray(new D2MonStatsTxt[0]);
        D2Log.debug("DATATBLS_LoadMonStatsTxt: Loaded " + monStatsTxtCache.length + " records");
    }
    
    /**
     * 卸载怪物统计文本表
     */
    public static void unloadMonStatsTxt() {
        monStatsTxtCache = null;
        D2Log.debug("DATATBLS_UnloadMonStatsTxt: Unloaded monster stats text table");
    }
    
    /**
     * 获取 MonStatsTxt 记录数量
     * @return MonStatsTxt 记录数量
     */
    public static int getMonStatsTxtRecordCount() {
        // 检查数据表是否已加载
        if (monStatsTxtCache == null) {
            // 数据表未加载，返回 0（不输出警告，因为这是正常的）
            return 0;
        }
        
        return monStatsTxtCache.length;
    }
    
    /**
     * 获取怪物统计文本记录
     * @param nId 怪物ID
     * @return 怪物统计文本记录，如果不存在返回 null
     */
    public static D2MonStatsTxt getMonStatsTxtRecord(int nId) {
        // 检查数据表是否已加载
        if (monStatsTxtCache == null) {
            // 数据表未加载，返回 null（不输出警告，因为这是正常的）
            return null;
        }
        
        // 简单的线性查找（实际应该使用哈希表或索引）
        for (D2MonStatsTxt record : monStatsTxtCache) {
            if (record != null && record.getDwId() == nId) {
                return record;
            }
        }
        
        // 记录不存在
        return null;
    }
    
    /**
     * 获取 SuperUniquesTxt 记录数量
     * @return SuperUniquesTxt 记录数量
     */
    /**
     * 加载超级唯一怪物文本表
     * 对应 C++ DATATBLS_LoadSuperUniquesTxt
     * 
     * @param archive 存档句柄
     */
    public static void loadSuperUniquesTxt(Object archive) {
        String fileName = "DATA\\GLOBAL\\EXCEL\\SuperUniques.txt";
        
        // 读取文件数据
        byte[] fileData = D2FileReader.readFile(archive, fileName);
        if (fileData == null || fileData.length == 0) {
            D2Log.warning("DATATBLS_LoadSuperUniquesTxt: Failed to read file: " + fileName);
            return;
        }
        
        // 解析文本表
        List<String[]> rows = D2TxtFileParser.parseTxtFile(fileData);
        if (rows.isEmpty()) {
            D2Log.warning("DATATBLS_LoadSuperUniquesTxt: No data rows found in file");
            return;
        }
        
        // SuperUniques.txt 第一行是表头，跳过
        if (rows.size() < 2) {
            D2Log.warning("DATATBLS_LoadSuperUniquesTxt: File contains only header, no data rows");
            return;
        }
        
        List<D2SuperUniquesTxt> records = new ArrayList<>();
        
        // 从第二行开始解析数据（第一行是表头）
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (row.length == 0) {
                continue; // 跳过空行
            }
            
            D2SuperUniquesTxt record = new D2SuperUniquesTxt();
            int colIndex = 0;
            
            // 解析字段（根据实际 SuperUniques.txt 文件格式调整字段顺序）
            
            // Id
            if (colIndex < row.length) {
                record.setDwId(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // Name
            if (colIndex < row.length) {
                record.setSzName(D2TxtFileParser.parseString(row[colIndex++], ""));
            }
            
            // Class
            if (colIndex < row.length) {
                record.setSzClass(D2TxtFileParser.parseString(row[colIndex++], ""));
            }
            
            // HcIdx
            if (colIndex < row.length) {
                record.setDwHcIdx(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // MonSound
            if (colIndex < row.length) {
                record.setDwMonSound(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // Mod1, Mod2, Mod3
            if (colIndex < row.length) {
                record.setDwMod1(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwMod2(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwMod3(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // MinGrp, MaxGrp
            if (colIndex < row.length) {
                record.setDwMinGrp(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwMaxGrp(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // EClass
            if (colIndex < row.length) {
                record.setDwEClass(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // AutoPos
            if (colIndex < row.length) {
                record.setDwAutoPos(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // Stacks, StacksPer, StacksMin, StacksMax
            if (colIndex < row.length) {
                record.setDwStacks(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwStacksPer(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwStacksMin(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwStacksMax(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // TC, TCEx
            if (colIndex < row.length) {
                record.setDwTC(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            if (colIndex < row.length) {
                record.setDwTCEx(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // Beta
            if (colIndex < row.length) {
                record.setDwBeta(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            records.add(record);
        }
        
        superUniquesTxtCache = records.toArray(new D2SuperUniquesTxt[0]);
        D2Log.debug("DATATBLS_LoadSuperUniquesTxt: Loaded " + superUniquesTxtCache.length + " records");
    }
    
    /**
     * 卸载超级唯一怪物文本表
     */
    public static void unloadSuperUniquesTxt() {
        superUniquesTxtCache = null;
        D2Log.debug("DATATBLS_UnloadSuperUniquesTxt: Unloaded super uniques text table");
    }
    
    /**
     * 获取 SuperUniquesTxt 记录数量
     * @return SuperUniquesTxt 记录数量
     */
    public static int getSuperUniquesTxtRecordCount() {
        // 检查数据表是否已加载
        if (superUniquesTxtCache == null) {
            // 数据表未加载，返回 0（不输出警告，因为这是正常的）
            return 0;
        }
        
        return superUniquesTxtCache.length;
    }
    
    /**
     * 获取超级唯一怪物文本记录
     * @param nId 超级唯一怪物ID
     * @return 超级唯一怪物文本记录，如果不存在返回 null
     */
    public static D2SuperUniquesTxt getSuperUniquesTxtRecord(int nId) {
        // 检查数据表是否已加载
        if (superUniquesTxtCache == null) {
            // 数据表未加载，返回 null（不输出警告，因为这是正常的）
            return null;
        }
        
        // 简单的线性查找（实际应该使用哈希表或索引）
        for (D2SuperUniquesTxt record : superUniquesTxtCache) {
            if (record != null && record.getDwId() == nId) {
                return record;
            }
        }
        
        // 记录不存在
        return null;
    }
    
    /**
     * D2Common.0x6FD626F0
     * 获取关卡子文本记录
     * @param nSubType 子类型ID（D2C_LevelSubstitutionType）
     * @return 关卡子文本记录，如果不存在返回 null
     */
    public static D2LvlSubTxt getLvlSubTxtRecord(int nSubType) {
        // 检查数据表是否已加载
        if (lvlSubTxtCache == null) {
            // 数据表未加载，返回 null（不输出警告，因为这是正常的）
            return null;
        }
        
        // 简单的线性查找（实际应该使用索引）
        for (D2LvlSubTxt record : lvlSubTxtCache) {
            if (record != null && record.getDwType() == nSubType) {
                return record;
            }
        }
        
        // 记录不存在
        return null;
    }
    
    /**
     * 获取下一个相同类型的关卡子文本记录
     * 用于模拟 C++ 中的指针递增（++pLvlSubTxtRecord）
     * @param currentRecord 当前记录
     * @param nSubType 子类型ID
     * @return 下一个相同类型的记录，如果不存在返回 null
     */
    public static D2LvlSubTxt getNextLvlSubTxtRecord(D2LvlSubTxt currentRecord, int nSubType) {
        if (lvlSubTxtCache == null || currentRecord == null) {
            return null;
        }
        
        // 查找当前记录在数组中的位置
        int currentIndex = -1;
        for (int i = 0; i < lvlSubTxtCache.length; ++i) {
            if (lvlSubTxtCache[i] == currentRecord) {
                currentIndex = i;
                break;
            }
        }
        
        if (currentIndex == -1) {
            return null;
        }
        
        // Native ++pLvlSubTxtRecord advances exactly one row and the caller's
        // while condition stops at the first different Type. Do not skip over
        // intervening types looking for another matching row.
        int nextIndex = currentIndex + 1;
        if (nextIndex >= lvlSubTxtCache.length) return null;
        D2LvlSubTxt next = lvlSubTxtCache[nextIndex];
        return next != null && next.getDwType() == nSubType ? next : null;
    }
    
    /**
     * 加载关卡子文本表
     * 对应 C++ DATATBLS_LoadLvlSubTxt
     * 
     * @param archive 存档句柄
     */
    public static void loadLvlSubTxt(Object archive) {
        String fileName = "DATA\\GLOBAL\\EXCEL\\LvlSub.txt";
        
        // 读取文件数据
        byte[] fileData = D2FileReader.readFile(archive, fileName);
        if (fileData == null || fileData.length == 0) {
            D2Log.warning("DATATBLS_LoadLvlSubTxt: Failed to read file: " + fileName);
            return;
        }
        
        // 解析文本表（TXT 格式通常是制表符分隔的文本文件）
        List<String[]> rows = D2TxtFileParser.parseTxtFile(fileData);
        if (rows.isEmpty()) {
            D2Log.warning("DATATBLS_LoadLvlSubTxt: No data rows found in file");
            return;
        }
        
        Map<String, Integer> columns = createColumnIndex(rows.get(0));
        List<D2LvlSubTxt> records = new ArrayList<>();
        
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (row.length == 0) {
                continue;
            }
            
            D2LvlSubTxt record = new D2LvlSubTxt();
            
            record.setDwType(parseColumnInt(row, columns, "Type", 0));
            record.setSzFile(parseColumnString(row, columns, "File", ""));
            record.setDwCheckAll(parseColumnInt(row, columns, "CheckAll", 0));
            record.setDwBordType(parseColumnInt(row, columns, "BordType", -1));
            record.setDwDt1Mask(parseColumnInt(row, columns, "Dt1Mask", 0));
            record.setDwGridSize(parseColumnInt(row, columns, "GridSize", 0));
            record.setDwExpansion(parseColumnInt(row, columns, "Expansion", 0));

            for (int index = 0; index < 5; index++) {
                record.setNProb(index, parseColumnInt(row, columns, "Prob" + index, 0));
                record.setNTrials(index, parseColumnInt(row, columns, "Trials" + index, 0));
                record.setNMax(index, parseColumnInt(row, columns, "Max" + index, 0));
            }
            
            // 注意：pDrlgFile、pTileTypeGrid、pWallGrid、pFloorGrid、pShadowGrid 需要后续加载 DS1 文件
            // 这些字段不在 TXT 文件中，需要在加载 DS1 文件后设置
            
            records.add(record);
        }
        
        lvlSubTxtCache = records.toArray(new D2LvlSubTxt[0]);
        D2Log.debug("DATATBLS_LoadLvlSubTxt: Loaded " + lvlSubTxtCache.length + " records");
    }
    
    /**
     * 卸载关卡子文本表
     * 对应 C++ DATATBLS_UnloadLvlSubTxt
     */
    public static void unloadLvlSubTxt() {
        lvlSubTxtCache = null;
    }
    
    /**
     * D2Common.0x6FD62020 (#10025)
     * 从关卡ID获取迷宫文本记录
     * 对应 C++ DATATBLS_GetLvlMazeTxtRecordFromLevelId
     * 
     * @param nLevelId 关卡ID
     * @return 迷宫文本记录，如果不存在返回 null
     */
    public static D2LvlMazeTxt getLvlMazeTxtRecordFromLevelId(int nLevelId) {
        // 检查数据表是否已加载
        if (lvlMazeTxtCache == null) {
            // 数据表未加载，返回 null（不输出警告，因为这是正常的）
            return null;
        }
        
        // 简单的线性查找（实际应该使用哈希表或索引）
        for (D2LvlMazeTxt record : lvlMazeTxtCache) {
            if (record != null && record.getDwLevelId() == nLevelId) {
                return record;
            }
        }
        
        // 记录不存在
        return null;
    }
    
    /**
     * 从关卡ID获取迷宫记录（转换为 D2MazeRecord）
     * 这是一个辅助方法，将 D2LvlMazeTxt 转换为 D2MazeRecord
     * 
     * @param nLevelId 关卡ID
     * @return 迷宫记录，如果不存在返回 null
     */
    public static com.d2moo.common.drlg.D2MazeRecord getMazeRecord(int nLevelId) {
        D2LvlMazeTxt txtRecord = getLvlMazeTxtRecordFromLevelId(nLevelId);
        if (txtRecord == null) {
            return null;
        }
        
        // 转换为 D2MazeRecord
        com.d2moo.common.drlg.D2MazeRecord mazeRecord = new com.d2moo.common.drlg.D2MazeRecord();
        mazeRecord.setDwLevelId(txtRecord.getDwLevelId());
        mazeRecord.setDwRooms(txtRecord.getDwRooms().clone());
        mazeRecord.setDwSizeX(txtRecord.getDwSizeX());
        mazeRecord.setDwSizeY(txtRecord.getDwSizeY());
        mazeRecord.setDwMerge(txtRecord.getDwMerge());
        
        return mazeRecord;
    }
    
    /**
     * 加载迷宫文本表
     * 对应 C++ DATATBLS_LoadLvlMazeTxt
     * 
     * @param archive 存档句柄
     */
    public static void loadLvlMazeTxt(Object archive) {
        String fileName = "DATA\\GLOBAL\\EXCEL\\LvlMaze.txt";
        
        // 读取文件数据
        byte[] fileData = D2FileReader.readFile(archive, fileName);
        if (fileData == null || fileData.length == 0) {
            D2Log.warning("DATATBLS_LoadLvlMazeTxt: Failed to read file: " + fileName);
            return;
        }
        
        // 解析文本表（TXT 格式通常是制表符分隔的文本文件）
        List<String[]> rows = D2TxtFileParser.parseTxtFile(fileData);
        if (rows.isEmpty()) {
            D2Log.warning("DATATBLS_LoadLvlMazeTxt: No data rows found in file");
            return;
        }
        
        // 第一行是表头，跳过
        List<D2LvlMazeTxt> records = new ArrayList<>();
        
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (row.length == 0) {
                continue;
            }
            
            D2LvlMazeTxt record = new D2LvlMazeTxt();
            
            // 解析字段（根据实际 TXT 文件格式调整）
            // LevelId, Rooms1-3 (普通、噩梦、地狱), SizeX, SizeY, Merge
            int colIndex = 0;
            if (colIndex < row.length) {
                record.setDwLevelId(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // 解析房间数量数组（3 个值：普通、噩梦、地狱）
            for (int roomIndex = 0; roomIndex < 3 && colIndex < row.length; roomIndex++) {
                record.setDwRooms(roomIndex, D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // SizeX
            if (colIndex < row.length) {
                record.setDwSizeX(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // SizeY
            if (colIndex < row.length) {
                record.setDwSizeY(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            // Merge
            if (colIndex < row.length) {
                record.setDwMerge(D2TxtFileParser.parseInt(row[colIndex++], 0));
            }
            
            records.add(record);
        }
        
        lvlMazeTxtCache = records.toArray(new D2LvlMazeTxt[0]);
        D2Log.debug("DATATBLS_LoadLvlMazeTxt: Loaded " + lvlMazeTxtCache.length + " records");
    }
    
    /**
     * 卸载迷宫文本表
     * 对应 C++ DATATBLS_UnloadLvlMazeTxt
     */
    public static void unloadLvlMazeTxt() {
        lvlMazeTxtCache = null;
    }

    private static Map<String, Integer> createColumnIndex(String[] header) {
        Map<String, Integer> columns = new HashMap<>();
        if (header == null) return columns;
        for (int i = 0; i < header.length; i++) {
            String name = header[i];
            if (name == null) continue;
            name = name.trim().toLowerCase(Locale.ROOT);
            if (!name.isEmpty()) columns.put(name, i);
        }
        return columns;
    }

    private static String parseColumnString(String[] row, Map<String, Integer> columns,
            String name, String defaultValue) {
        Integer index = columns.get(name.toLowerCase(Locale.ROOT));
        if (index == null || index < 0 || index >= row.length) return defaultValue;
        return D2TxtFileParser.parseString(row[index], defaultValue);
    }

    private static int parseColumnInt(String[] row, Map<String, Integer> columns,
            String name, int defaultValue) {
        String value = parseColumnString(row, columns, name, null);
        return value == null ? defaultValue : D2TxtFileParser.parseInt(value, defaultValue);
    }
}
