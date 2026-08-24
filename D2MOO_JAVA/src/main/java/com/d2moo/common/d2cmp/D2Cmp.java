package com.d2moo.common.d2cmp;

import com.d2moo.common.util.D2Log;
import com.d2moo.common.util.D2FileReader;
import com.d2moo.common.util.D2BinaryReader;

/**
 * D2CMP 瓦片库管理模块
 * 对应 C++ 模块：D2CMP
 * 
 * 注意：这是一个瓦片库管理模块，负责加载和管理 DT1 瓦片库文件
 * 当前实现提供基础框架和接口，实际文件加载需要后续实现
 */
public class D2Cmp {

    private static final int DT1_HEADER_SIZE = 276;
    private static final int DT1_TILE_HEADER_SIZE = 96;
    
    // 瓦片库缓存（占位符，实际需要从文件加载）
    private static D2TileLibrary[] tileLibraryCache;
    private static final java.util.concurrent.ConcurrentMap<String, D2TileLibraryHashStrc>
            namedTileLibraryCache = new java.util.concurrent.ConcurrentHashMap<>();
    
    /**
     * D2Common.0x10087
     * 加载瓦片库槽位（通过文件名）
     * 
     * 注意：此函数使用 null 作为 archive 参数，D2FileReader.readFile 会处理 null archive：
     * - 如果 archive 为 null，D2FileReader 会尝试从文件系统读取文件
     * - 如果需要从 MPQ 存档读取，应使用 loadTileLibrarySlot(Object archive, int nTileLibraryId, int nSlot) 重载
     * 
     * @param tiles 瓦片数组（D2TileLibraryHashStrc* pTiles[32]）
     * @param fileName 文件名
     */
    public static void loadTileLibrarySlot(Object[] tiles, String fileName) {
        loadTileLibrarySlot(null, tiles, fileName);
    }

    /** Loads a named DT1 through an optional archive adapter. */
    public static void loadTileLibrarySlot(Object archive, Object[] tiles, String fileName) {
        if (tiles == null || fileName == null || fileName.isEmpty()) {
            D2Log.warning("D2CMP_10087_LoadTileLibrarySlot: Invalid parameters");
            return;
        }
        
        // 计算文件名哈希以找到合适的槽位
        int hash = fileName.hashCode();
        int slot = Math.abs(hash) % tiles.length;
        
        // 检查该槽位是否已加载相同的文件
        if (tiles[slot] != null) {
            // 如果已存在，检查是否是同一个文件
            if (tiles[slot] instanceof D2TileLibraryHashStrc) {
                D2TileLibraryHashStrc existing = (D2TileLibraryHashStrc) tiles[slot];
                if (fileName.equals(existing.getFileName())) {
                    // 同一个文件，跳过
                    D2Log.debug("D2CMP_10087_LoadTileLibrarySlot: File already loaded: " + fileName);
                    return;
                }
            }
            // 槽位被占用但文件不同，继续查找下一个可用槽位
            for (int i = 0; i < tiles.length; i++) {
                int nextSlot = (slot + i) % tiles.length;
                if (tiles[nextSlot] == null) {
                    slot = nextSlot;
                    break;
                }
            }
        }

        String cacheKey = fileName.replace('/', '\\').toLowerCase(java.util.Locale.ROOT);
        D2TileLibraryHashStrc cachedLibrary = namedTileLibraryCache.get(cacheKey);
        if (cachedLibrary != null) {
            tiles[slot] = cachedLibrary;
            return;
        }
        
        // 解析 DT1 文件
        D2TileLibrary tileLibrary = parseDT1File(archive, fileName);
        if (tileLibrary == null) {
            D2Log.warning("D2CMP_10087_LoadTileLibrarySlot: Failed to parse DT1 file: " + fileName);
            // 即使解析失败，也创建空的哈希表对象，避免后续调用出错
            D2TileLibraryHashStrc tileLibraryHash = new D2TileLibraryHashStrc(fileName);
            tiles[slot] = tileLibraryHash;
            return;
        }
        
        // 创建瓦片库哈希表对象
        D2TileLibraryHashStrc tileLibraryHash = new D2TileLibraryHashStrc(fileName);
        
        // 构建哈希表索引
        buildTileLibraryHashIndex(tileLibraryHash, tileLibrary.getPTiles());
        D2TileLibraryHashStrc racedLibrary = namedTileLibraryCache.putIfAbsent(cacheKey, tileLibraryHash);
        if (racedLibrary != null) {
            tileLibraryHash = racedLibrary;
        }
        
        // 将对象添加到 tiles 数组的合适槽位
        tiles[slot] = tileLibraryHash;
        
        D2Log.debug("D2CMP_10087_LoadTileLibrarySlot: Loaded tile library from file: " + fileName 
                + " at slot: " + slot + ", tiles: " + tileLibrary.getNTiles());
    }
    
    /**
     * D2Common.0x10087
     * 加载瓦片库槽位（通过ID和槽位，旧版本接口）
     * @param archive 存档句柄（占位符）
     * @param nTileLibraryId 瓦片库ID
     * @param nSlot 槽位
     * @return 瓦片库对象，如果加载失败返回 null
     */
    public static D2TileLibrary loadTileLibrarySlot(Object archive, int nTileLibraryId, int nSlot) {
        // 检查缓存
        if (tileLibraryCache != null) {
            for (D2TileLibrary lib : tileLibraryCache) {
                if (lib != null && lib.getNTileLibraryId() == nTileLibraryId && lib.getNSlot() == nSlot) {
                    D2Log.debug("D2CMP_10087_LoadTileLibrarySlot: Found cached tile library, id: " + nTileLibraryId + ", slot: " + nSlot);
                    return lib;
                }
            }
        }
        
        // 根据 nTileLibraryId 和 nSlot 确定文件名
        String fileName = getTileLibraryFileName(nTileLibraryId, nSlot);
        if (fileName == null || fileName.isEmpty()) {
            D2Log.warning("D2CMP_10087_LoadTileLibrarySlot: Cannot determine filename for id: " + nTileLibraryId + ", slot: " + nSlot);
            // 创建空的瓦片库对象，避免后续调用出错
            D2TileLibrary tileLibrary = new D2TileLibrary();
            tileLibrary.setNTileLibraryId(nTileLibraryId);
            tileLibrary.setNSlot(nSlot);
            return tileLibrary;
        }
        
        // 解析 DT1 文件
        D2TileLibrary tileLibrary = parseDT1File(archive, fileName);
        if (tileLibrary == null) {
            D2Log.warning("D2CMP_10087_LoadTileLibrarySlot: Failed to parse DT1 file for id: " + nTileLibraryId + ", slot: " + nSlot);
            // 即使解析失败，也创建空的瓦片库对象，避免后续调用出错
            tileLibrary = new D2TileLibrary();
            tileLibrary.setNTileLibraryId(nTileLibraryId);
            tileLibrary.setNSlot(nSlot);
            return tileLibrary;
        }
        
        // 设置瓦片库ID和槽位
        tileLibrary.setNTileLibraryId(nTileLibraryId);
        tileLibrary.setNSlot(nSlot);
        
        // 添加到缓存
        if (tileLibraryCache == null) {
            tileLibraryCache = new D2TileLibrary[100]; // 假设最多缓存 100 个瓦片库
        }
        for (int i = 0; i < tileLibraryCache.length; ++i) {
            if (tileLibraryCache[i] == null) {
                tileLibraryCache[i] = tileLibrary;
                break;
            }
        }
        
        D2Log.debug("D2CMP_10087_LoadTileLibrarySlot: Loaded tile library, id: " + nTileLibraryId 
                + ", slot: " + nSlot + ", tiles: " + tileLibrary.getNTiles());
        
        return tileLibrary;
    }
    
    /**
     * D2Common.0x10088
     * 按类型/风格/序列从瓦片库哈希数组查询瓦片，填充到 pTileList，返回数量。
     * 对应 C++ D2CMP_10088_GetTiles(ppTileLibraryHash, nType, nStyle, nSequence, pTileList, nTileListSize)。
     * 
     * 功能：
     * 1. 遍历瓦片库哈希数组
     * 2. 在每个哈希表中查找匹配类型/风格/序列的瓦片
     * 3. 将找到的瓦片添加到 pTileList
     * 4. 返回找到的瓦片数量
     */
    public static int getTiles(Object[] ppTileLibraryHash, int nType, int nStyle, int nSequence,
            Object[] pTileList, int nTileListSize) {
        if (ppTileLibraryHash == null || pTileList == null || nTileListSize <= 0) {
            return 0;
        }
        
        int nFoundTiles = 0;
        
        // 遍历瓦片库哈希数组
        for (Object tileLibraryHashObj : ppTileLibraryHash) {
            if (tileLibraryHashObj == null) {
                continue;
            }
            
            if (tileLibraryHashObj instanceof D2TileLibraryHashStrc) {
                D2TileLibraryHashStrc tileLibraryHash = (D2TileLibraryHashStrc) tileLibraryHashObj;
                
                // 遍历哈希节点数组
                D2TileLibraryHashNodeStrc[] pNodes = tileLibraryHash.getPNodes();
                if (pNodes != null) {
                    for (D2TileLibraryHashNodeStrc node : pNodes) {
                        if (node == null) {
                            continue;
                        }
                        
                        // 遍历节点链表（处理哈希冲突）
                        D2TileLibraryHashNodeStrc currentNode = node;
                        while (currentNode != null && nFoundTiles < nTileListSize) {
                            // 检查瓦片类型、风格、序列是否匹配
                            if (currentNode.getNType() == nType 
                                    && currentNode.getNStyle() == nStyle 
                                    && currentNode.getNSequence() == nSequence) {
                                
                                // 从引用链表中获取所有匹配的瓦片数据
                                D2TileLibraryHashRefStrc pRef = currentNode.getPRef();
                                while (pRef != null && nFoundTiles < nTileListSize) {
                                    Object pTile = pRef.getPTile();
                                    if (pTile != null) {
                                        // 将瓦片添加到列表
                                        pTileList[nFoundTiles] = pTile;
                                        nFoundTiles++;
                                    }
                                    
                                    // 移动到引用链表的下一个节点
                                    pRef = pRef.getPPrev();
                                }
                            }
                            
                            // 移动到哈希冲突链表的下一个节点
                            // 注意：当前实现中，哈希冲突通过 pPrev 字段链接
                            // 如果 pPrev 不为 null，说明有冲突，继续遍历
                            if (currentNode.getPPrev() != null) {
                                currentNode = currentNode.getPPrev();
                            } else {
                                break; // 没有更多冲突节点
                            }
                        }
                        
                        if (nFoundTiles >= nTileListSize) {
                            break; // 列表已满
                        }
                    }
                }
            }
        }
        
        return nFoundTiles;
    }

    /**
     * D2Common.0x10088
     * 获取瓦片
     * @param tileLibrary 瓦片库对象
     * @param nTileId 瓦片ID
     * @param nSequence 序列号
     * @return 瓦片数据，如果不存在返回 null
     */
    public static D2TileData getTiles(D2TileLibrary tileLibrary, int nTileId, int nSequence) {
        if (tileLibrary == null) {
            return null;
        }
        // 从瓦片库中查找对应的瓦片（瓦片库未实现时返回 null）
        D2TileData[] tiles = tileLibrary.getPTiles();
        if (tiles != null) {
            for (D2TileData tile : tiles) {
                if (tile != null && tile.getNTileId() == nTileId && tile.getNSequence() == nSequence) {
                    return tile;
                }
            }
        }
        return null;
    }
    
    /**
     * 获取瓦片（通过瓦片库ID和槽位）
     * @param archive 存档句柄
     * @param nTileLibraryId 瓦片库ID
     * @param nSlot 槽位
     * @param nTileId 瓦片ID
     * @param nSequence 序列号
     * @return 瓦片数据，如果不存在返回 null
     */
    public static D2TileData getTiles(Object archive, int nTileLibraryId, int nSlot, int nTileId, int nSequence) {
        D2TileLibrary tileLibrary = loadTileLibrarySlot(archive, nTileLibraryId, nSlot);
        if (tileLibrary == null) {
            return null;
        }
        
        return getTiles(tileLibrary, nTileId, nSequence);
    }
    
    /**
     * 释放瓦片库
     * @param tileLibrary 瓦片库对象
     */
    public static void freeTileLibrary(D2TileLibrary tileLibrary) {
        if (tileLibrary == null) {
            return;
        }
        
        // 从缓存中移除
        if (tileLibraryCache != null) {
            for (int i = 0; i < tileLibraryCache.length; i++) {
                if (tileLibraryCache[i] == tileLibrary) {
                    tileLibraryCache[i] = null;
                    break;
                }
            }
        }
    }
    
    /**
     * 根据瓦片库ID和槽位获取文件名
     * 这是一个框架实现，实际的文件名映射需要从数据表加载
     * 
     * @param nTileLibraryId 瓦片库ID
     * @param nSlot 槽位
     * @return 文件名，如果无法确定则返回 null
     */
    private static String getTileLibraryFileName(int nTileLibraryId, int nSlot) {
        // 注意：实际的文件名映射应该从数据表（如 LvlPrestTxt）加载
        // 当前实现：基于常见的瓦片库ID和槽位模式提供基本映射
        
        // 基本的文件名映射表（占位符，实际需要从数据表加载）
        // 格式：Act{ActNo}\\{Area}\\{Type}.dt1
        // 例如：Act1\\Town\\Floor.dt1
        
        // 根据 nTileLibraryId 确定 Act 和区域
        // 这是一个简化的映射，实际应该从数据表查询
        int nAct = (nTileLibraryId >> 8) & 0xFF; // 假设高字节是 Act
        int nArea = (nTileLibraryId >> 4) & 0x0F; // 假设中间位是区域
        int nType = nTileLibraryId & 0x0F; // 假设低字节是类型
        
        // 根据槽位确定瓦片类型（Floor, Wall, Shadow 等）
        String tileType = getTileTypeFromSlot(nSlot);
        
        // 根据 Act 确定 Act 目录
        String actDir = "Act" + (nAct + 1); // Act 编号从 1 开始
        
        // 根据区域确定区域目录（简化映射）
        String areaDir = getAreaDirFromId(nArea);
        
        // 构建文件名
        String fileName = "DATA\\GLOBAL\\Tiles\\" + actDir + "\\" + areaDir + "\\" + tileType + ".dt1";
        
        D2Log.debug("D2CMP_GetTileLibraryFileName: Generated filename for id: " + nTileLibraryId 
                + ", slot: " + nSlot + " -> " + fileName);
        
        return fileName;
    }
    
    /**
     * 根据槽位获取瓦片类型名称
     * @param nSlot 槽位
     * @return 瓦片类型名称（如 Floor, Wall, Shadow）
     */
    private static String getTileTypeFromSlot(int nSlot) {
        // 常见的槽位映射（占位符，实际需要从数据表查询）
        switch (nSlot) {
            case 0: return "Floor";
            case 1: return "Wall";
            case 2: return "Shadow";
            case 3: return "Roof";
            case 4: return "Lower";
            case 5: return "Upper";
            default: return "Floor"; // 默认返回 Floor
        }
    }
    
    /**
     * 根据区域ID获取区域目录名称
     * @param nArea 区域ID
     * @return 区域目录名称
     */
    private static String getAreaDirFromId(int nArea) {
        // 常见的区域映射（占位符，实际需要从数据表查询）
        switch (nArea) {
            case 0: return "Town";
            case 1: return "Wilderness";
            case 2: return "Cave";
            case 3: return "Crypt";
            case 4: return "Tomb";
            case 5: return "Desert";
            case 6: return "Jungle";
            case 7: return "Kurast";
            case 8: return "Mesa";
            case 9: return "Lava";
            case 10: return "Barricade";
            case 11: return "Siege";
            default: return "Town"; // 默认返回 Town
        }
    }
    
    /**
     * 初始化 D2CMP 模块
     */
    public static void initialize() {
        // 注意：模块初始化可以在这里加载数据表映射等
        // 当前实现为简化版本，实际可以根据需要加载数据表
        D2Log.debug("D2CMP_Initialize: Module initialization");
    }
    
    /**
     * 清理 D2CMP 模块
     */
    public static void cleanup() {
        // 清理所有缓存的瓦片库
        if (tileLibraryCache != null) {
            for (D2TileLibrary lib : tileLibraryCache) {
                if (lib != null) {
                    freeTileLibrary(lib);
                }
            }
            tileLibraryCache = null;
        }
    }
    
    /**
     * D2Common.0x10081
     * 获取瓦片稀有度
     * @param pTileLibraryEntry 瓦片库条目
     * @return 稀有度值（如果瓦片库未实现，返回默认值 1）
     */
    public static int getTileRarity(Object pTileLibraryEntry) {
        if (pTileLibraryEntry == null) {
            return 1;
        }

        if (pTileLibraryEntry instanceof D2TileData) {
            return ((D2TileData) pTileLibraryEntry).getNRarity();
        }

        return 1;
    }

    /** D2Common #10079. */
    public static int getTileFlags(Object pTileLibraryEntry) {
        return pTileLibraryEntry instanceof D2TileData
                ? ((D2TileData) pTileLibraryEntry).getDwFlags() : 0;
    }
    
    /**
     * D2Common.0x10078
     * 获取瓦片风格（从瓦片库条目）
     * @param pTileLibraryEntry 瓦片库条目（可能是 D2TileData 或其他类型）
     * @return 风格值，如果未实现返回 0
     */
    public static int getTileStyle(Object pTileLibraryEntry) {
        if (pTileLibraryEntry == null) {
            return 0;
        }
        
        // 如果条目是 D2TileData，直接获取瓦片ID（作为风格）
        if (pTileLibraryEntry instanceof D2TileData) {
            D2TileData tileData = (D2TileData) pTileLibraryEntry;
            return tileData.getNTileId();
        }
        
        // 注意：实际应该从其他类型的瓦片库条目中获取风格
        // 当前实现返回默认值 0
        return 0;
    }
    
    /**
     * D2Common.0x10082
     * 获取瓦片序列（从瓦片库条目）
     * @param pTileLibraryEntry 瓦片库条目（可能是 D2TileData 或其他类型）
     * @return 序列值，如果未实现返回 0
     */
    public static int getTileSequence(Object pTileLibraryEntry) {
        if (pTileLibraryEntry == null) {
            return 0;
        }
        
        // 如果条目是 D2TileData，直接获取序列号
        if (pTileLibraryEntry instanceof D2TileData) {
            D2TileData tileData = (D2TileData) pTileLibraryEntry;
            return tileData.getNSequence();
        }
        
        // 注意：实际应该从其他类型的瓦片库条目中获取序列
        // 当前实现返回默认值 0
        return 0;
    }
    
    /**
     * 解析 DT1 文件
     * DT1 文件格式：
     * - 文件头：版本、瓦片数量等
     * - 瓦片数据块：每个瓦片包含图像数据、属性等
     * 
     * @param archive 存档句柄
     * @param fileName 文件名
     * @return 瓦片库对象，如果解析失败返回 null
     */
    private static D2TileLibrary parseDT1File(Object archive, String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }

        byte[] fileData = readDT1FileData(archive, fileName);
        if (fileData == null || fileData.length == 0) {
            D2Log.warning("D2CMP_ParseDT1File: Failed to read DT1 file: " + fileName);
            return null;
        }
        return parseDT1FileData(fileData, fileName);
    }

    /**
     * Parses DT1 metadata from an in-memory file.
     *
     * <p>A DT1 contains a 276-byte library header followed by a contiguous
     * array of 96-byte tile headers. Pixel blocks live elsewhere in the file
     * and are not needed by DRLG tile selection.</p>
     */
    public static D2TileLibrary parseDT1FileData(byte[] fileData, String fileName) {
        if (fileData == null || fileData.length < DT1_HEADER_SIZE) {
            D2Log.warning("D2CMP_ParseDT1File: Invalid or truncated DT1 file: " + fileName);
            return null;
        }

        int version1 = D2BinaryReader.readInt32(fileData, 0);
        int version2 = D2BinaryReader.readInt32(fileData, 4);
        int tileCount = D2BinaryReader.readInt32(fileData, 268);
        int tileOffset = D2BinaryReader.readInt32(fileData, 272);
        if (tileCount <= 0 || tileCount > 10000) {
            D2Log.warning("D2CMP_ParseDT1File: Invalid tile count " + tileCount + ": " + fileName);
            return null;
        }

        long tileHeadersEnd = (long) tileOffset + (long) tileCount * DT1_TILE_HEADER_SIZE;
        if (tileOffset < DT1_HEADER_SIZE || tileHeadersEnd > fileData.length) {
            D2Log.warning("D2CMP_ParseDT1File: Tile headers exceed file bounds: " + fileName
                    + " offset=" + tileOffset + " count=" + tileCount
                    + " length=" + fileData.length);
            return null;
        }

        D2TileData[] tiles = new D2TileData[tileCount];
        int offset = tileOffset;
        for (int i = 0; i < tileCount; i++, offset += DT1_TILE_HEADER_SIZE) {
            D2TileData tile = parseDT1TileHeader(fileData, offset);
            if (tile == null) {
                D2Log.warning("D2CMP_ParseDT1File: Failed to parse tile " + i
                        + " at offset " + offset + ": " + fileName);
                return null;
            }
            tiles[i] = tile;
        }

        D2TileLibrary tileLibrary = new D2TileLibrary();
        tileLibrary.setFileName(fileName);
        tileLibrary.setNTiles(tileCount);
        tileLibrary.setPTiles(tiles);
        D2Log.debug("D2CMP_ParseDT1File: Parsed DT1 file: " + fileName
                + ", version=" + version1 + "." + version2
                + ", tileOffset=" + tileOffset + ", tiles=" + tileCount);
        return tileLibrary;
    }

    private static D2TileData parseDT1TileHeader(byte[] fileData, int offset) {
        if (!D2BinaryReader.hasEnoughData(fileData, offset, DT1_TILE_HEADER_SIZE)) {
            return null;
        }

        D2TileData tile = new D2TileData();
        tile.setDwFlags(D2BinaryReader.readUInt16(fileData, offset + 6));
        tile.setNHeight(D2BinaryReader.readInt32(fileData, offset + 8));
        tile.setNWidth(D2BinaryReader.readInt32(fileData, offset + 12));
        tile.setNPosY(D2BinaryReader.readInt32(fileData, offset + 16));
        tile.setNOrientation(D2BinaryReader.readInt32(fileData, offset + 20));
        tile.setNTileId(D2BinaryReader.readInt32(fileData, offset + 24));
        tile.setNSequence(D2BinaryReader.readInt32(fileData, offset + 28));
        tile.setNRarity(D2BinaryReader.readInt32(fileData, offset + 32));
        return tile;
    }
    
    /**
     * 读取 DT1 文件数据
     * @param archive 存档句柄（MPQ 存档，可为 null）
     * @param fileName 文件名
     * @return 文件数据，如果读取失败返回 null
     */
    private static byte[] readDT1FileData(Object archive, String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            D2Log.warning("D2CMP_ReadDT1FileData: Invalid file name");
            return null;
        }
        
        // 使用文件读取工具类读取文件
        byte[] fileData = D2FileReader.readFile(archive, fileName);
        
        if (fileData == null || fileData.length == 0) {
            D2Log.warning("D2CMP_ReadDT1FileData: Failed to read DT1 file: " + fileName);
            return null;
        }
        
        D2Log.debug("D2CMP_ReadDT1FileData: Successfully read DT1 file: " + fileName + " (size: " + fileData.length + " bytes)");
        return fileData;
    }
    
    /**
     * 构建瓦片库哈希表索引
     * 根据瓦片的类型、风格、序列构建哈希表，以便快速查询
     * 
     * @param tileLibraryHash 瓦片库哈希表
     * @param tiles 瓦片数据数组
     */
    private static void buildTileLibraryHashIndex(D2TileLibraryHashStrc tileLibraryHash, D2TileData[] tiles) {
        if (tileLibraryHash == null || tiles == null) {
            return;
        }
        
        // 哈希表大小为 128
        final int HASH_TABLE_SIZE = 128;
        
        // 遍历所有瓦片
        for (D2TileData tile : tiles) {
            if (tile == null) {
                continue;
            }
            
            // 从瓦片数据中获取类型、风格、序列
            // 注意：根据实际 DT1 格式，这些值可能存储在不同的字段中
            int nType = tile.getNOrientation(); // 使用方向作为类型（根据实际格式调整）
            int nStyle = tile.getNTileId(); // 使用瓦片ID作为风格（根据实际格式调整）
            int nSequence = tile.getNSequence();
            
            // 计算哈希值（使用类型、风格、序列的组合）
            int hash = ((nType << 16) | (nStyle << 8) | nSequence) % HASH_TABLE_SIZE;
            if (hash < 0) {
                hash = -hash;
            }
            
            // 获取哈希槽位
            D2TileLibraryHashNodeStrc[] pNodes = tileLibraryHash.getPNodes();
            if (pNodes == null) {
                pNodes = new D2TileLibraryHashNodeStrc[HASH_TABLE_SIZE];
                tileLibraryHash.setPNodes(pNodes);
            }
            
            // 查找或创建哈希节点
            D2TileLibraryHashNodeStrc currentNode = pNodes[hash];
            D2TileLibraryHashNodeStrc matchingNode = null;
            
            // 查找匹配的节点（相同的类型、风格、序列）
            while (currentNode != null) {
                if (currentNode.getNType() == nType 
                        && currentNode.getNStyle() == nStyle 
                        && currentNode.getNSequence() == nSequence) {
                    matchingNode = currentNode;
                    break;
                }
                // 移动到下一个节点（如果有链表）
                // 注意：当前实现中节点没有 pNext 字段，需要根据实际结构调整
                break; // 临时：避免无限循环
            }
            
            // 如果没有找到匹配的节点，创建新节点
            if (matchingNode == null) {
                D2TileLibraryHashNodeStrc newNode = new D2TileLibraryHashNodeStrc();
                newNode.setNType(nType);
                newNode.setNStyle(nStyle);
                newNode.setNSequence(nSequence);
                
                // 创建引用节点
                D2TileLibraryHashRefStrc ref = new D2TileLibraryHashRefStrc();
                ref.setPTile(tile);
                newNode.setPRef(ref);
                
                // 插入到哈希表（处理冲突：使用链表或覆盖）
                if (pNodes[hash] == null) {
                    pNodes[hash] = newNode;
                } else {
                    // 如果有冲突，将新节点链接到现有节点（需要根据实际结构调整）
                    // 当前实现：简单覆盖（实际应该使用链表）
                    newNode.setPPrev(pNodes[hash]);
                    pNodes[hash] = newNode;
                }
            } else {
                // 如果找到匹配的节点，添加新的引用
                D2TileLibraryHashRefStrc newRef = new D2TileLibraryHashRefStrc();
                newRef.setPTile(tile);
                
                // 将新引用添加到引用链表的末尾
                D2TileLibraryHashRefStrc lastRef = matchingNode.getPRef();
                while (lastRef != null && lastRef.getPPrev() != null) {
                    lastRef = lastRef.getPPrev();
                }
                if (lastRef != null) {
                    newRef.setPPrev(lastRef);
                }
                matchingNode.setPRef(newRef);
            }
        }
        
        D2Log.debug("D2CMP_BuildTileLibraryHashIndex: Built hash index for " + tiles.length + " tiles");
    }
}
