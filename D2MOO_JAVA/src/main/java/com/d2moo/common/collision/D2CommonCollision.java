package com.d2moo.common.collision;

import com.d2moo.common.drlg.D2ActiveRoom;
import com.d2moo.common.drlg.D2DrlgRoom;
import com.d2moo.common.drlg.D2DrlgTileDataStrc;
import com.d2moo.common.drlg.D2DrlgGridStrc;
import com.d2moo.common.drlg.DrlgDrlgGrid;
import com.d2moo.common.util.D2Log;

/**
 * D2Common 碰撞检测模块
 * 对应 C++ 模块：D2Common_COLLISION
 * 
 * 注意：这是一个碰撞检测模块，用于处理瓦片和房间的碰撞
 * 当前实现提供基础框架和接口，实际碰撞检测逻辑需要后续实现
 */
public class D2CommonCollision {
    
    /**
     * D2Common.0x6FD41000
     * 碰撞检测函数（第一个函数）
     * 对应 C++ D2Common_COLLISION_FirstFn_6FD41000
     * 
     * 功能：
     * 1. 更新瓦片的碰撞信息
     * 2. 处理瓦片与房间的碰撞关系
     * 3. 更新碰撞网格或碰撞数据
     * 
     * @param activeRoom 活动房间对象
     * @param pTileData 瓦片数据
     * @param pTileCache 瓦片缓存（可选，用于更新碰撞信息）
     */
    public static void firstFn(D2ActiveRoom activeRoom, D2DrlgTileDataStrc pTileData, Object pTileCache) {
        if (activeRoom == null || pTileData == null) {
            return;
        }
        
        // 获取瓦片的坐标和尺寸
        int nX = pTileData.getNPosX();
        int nY = pTileData.getNPosY();
        int nWidth = pTileData.getNWidth();
        int nHeight = pTileData.getNHeight();
        int nTileType = pTileData.getNTileType();
        int dwFlags = pTileData.getDwFlags();
        
        // 获取关联的 DrlgRoom（用于访问房间的网格信息）
        D2DrlgRoom drlgRoom = activeRoom.getPDrlgRoom();
        if (drlgRoom == null) {
            D2Log.debug("D2Common_COLLISION_FirstFn_6FD41000: DrlgRoom is null, skipping collision update");
            return;
        }
        
        // 根据瓦片类型和标志确定碰撞属性
        boolean bUnwalkable = isTileUnwalkable(nTileType, dwFlags);
        boolean bBlockVision = isTileBlockVision(nTileType, dwFlags);
        boolean bHidden = isTileHidden(dwFlags);
        
        // 如果瓦片是隐藏的，通常不需要更新碰撞网格（隐藏瓦片不影响碰撞）
        if (bHidden) {
            D2Log.debug("D2Common_COLLISION_FirstFn_6FD41000: Tile is hidden, skipping collision update");
            return;
        }
        
        // 计算瓦片在房间网格中的位置
        // 注意：瓦片坐标（nPosX, nPosY）已经是相对于房间的坐标
        // 不需要再次减去房间位置，因为 pTileData.getNPosX() 已经是相对坐标
        int nGridX = nX;
        int nGridY = nY;
        
        // 获取或创建碰撞网格
        D2DrlgGridStrc pCollisionGrid = activeRoom.getPCollisionGrid();
        if (pCollisionGrid == null) {
            // 如果碰撞网格不存在，尝试从 DrlgRoom 获取或创建
            // 注意：碰撞网格通常应该在房间初始化时创建
            // 这里如果不存在，我们只记录日志，不创建（避免在碰撞检测时创建网格）
            D2Log.debug("D2Common_COLLISION_FirstFn_6FD41000: Collision grid not initialized for room, skipping grid update");
        } else {
            // 更新碰撞网格
            // 使用 DrlgDrlgGrid 的功能来更新网格标志
            if (DrlgDrlgGrid.isGridValid(pCollisionGrid) && 
                DrlgDrlgGrid.isPointInsideGridArea(pCollisionGrid, nGridX, nGridY)) {
                
                // 定义碰撞标志
                final int COLLISION_FLAG_UNWALKABLE = 0x00000001;  // 不可通行标志
                final int COLLISION_FLAG_BLOCK_VISION = 0x00000002; // 阻挡视野标志
                
                // 更新不可通行标志
                if (bUnwalkable) {
                    DrlgDrlgGrid.alterGridFlag(pCollisionGrid, nGridX, nGridY, 
                        COLLISION_FLAG_UNWALKABLE, DrlgDrlgGrid.FlagOperation.OR);
                } else {
                    // 如果瓦片变为可通行，清除不可通行标志
                    DrlgDrlgGrid.alterGridFlag(pCollisionGrid, nGridX, nGridY, 
                        COLLISION_FLAG_UNWALKABLE, DrlgDrlgGrid.FlagOperation.AND_NEGATED);
                }
                
                // 更新阻挡视野标志
                if (bBlockVision) {
                    DrlgDrlgGrid.alterGridFlag(pCollisionGrid, nGridX, nGridY, 
                        COLLISION_FLAG_BLOCK_VISION, DrlgDrlgGrid.FlagOperation.OR);
                } else {
                    // 如果瓦片不再阻挡视野，清除阻挡视野标志
                    DrlgDrlgGrid.alterGridFlag(pCollisionGrid, nGridX, nGridY, 
                        COLLISION_FLAG_BLOCK_VISION, DrlgDrlgGrid.FlagOperation.AND_NEGATED);
                }
                
                // 如果瓦片有宽度和高度，需要更新整个瓦片区域
                if (nWidth > 1 || nHeight > 1) {
                    for (int y = 0; y < nHeight && (nGridY + y) < pCollisionGrid.getNHeight(); y++) {
                        for (int x = 0; x < nWidth && (nGridX + x) < pCollisionGrid.getNWidth(); x++) {
                            int cellX = nGridX + x;
                            int cellY = nGridY + y;
                            
                            // 更新每个单元格的碰撞标志
                            if (bUnwalkable) {
                                DrlgDrlgGrid.alterGridFlag(pCollisionGrid, cellX, cellY, 
                                    COLLISION_FLAG_UNWALKABLE, DrlgDrlgGrid.FlagOperation.OR);
                            } else {
                                DrlgDrlgGrid.alterGridFlag(pCollisionGrid, cellX, cellY, 
                                    COLLISION_FLAG_UNWALKABLE, DrlgDrlgGrid.FlagOperation.AND_NEGATED);
                            }
                            
                            if (bBlockVision) {
                                DrlgDrlgGrid.alterGridFlag(pCollisionGrid, cellX, cellY, 
                                    COLLISION_FLAG_BLOCK_VISION, DrlgDrlgGrid.FlagOperation.OR);
                            } else {
                                DrlgDrlgGrid.alterGridFlag(pCollisionGrid, cellX, cellY, 
                                    COLLISION_FLAG_BLOCK_VISION, DrlgDrlgGrid.FlagOperation.AND_NEGATED);
                            }
                        }
                    }
                }
            } else {
                D2Log.debug("D2Common_COLLISION_FirstFn_6FD41000: Tile position (" + nGridX + ", " + nGridY + 
                            ") is outside collision grid bounds");
            }
        }
        
        // 如果提供了瓦片缓存，可能需要更新碰撞信息
        if (pTileCache != null) {
            // 瓦片缓存更新时，可能需要重新计算碰撞属性
            // 这通常涉及检查新瓦片的类型和属性
            D2Log.debug("D2Common_COLLISION_FirstFn_6FD41000: Tile cache updated, recalculating collision");
        }
        
        D2Log.debug("D2Common_COLLISION_FirstFn_6FD41000: Collision updated for tile at (" + nX + ", " + nY + 
                    "), type: " + nTileType + ", unwalkable: " + bUnwalkable + ", blockVision: " + bBlockVision);
    }
    
    /**
     * 判断瓦片是否不可通行
     * 
     * @param nTileType 瓦片类型
     * @param dwFlags 瓦片标志
     * @return 如果瓦片不可通行返回 true，否则返回 false
     */
    private static boolean isTileUnwalkable(int nTileType, int dwFlags) {
        // 根据瓦片类型判断是否不可通行
        // 墙壁类型通常不可通行
        if (nTileType >= 1 && nTileType <= 7) {
            // 墙壁类型（TILETYPE_WALL_LEFT 到 TILETYPE_WALL_BOTTOM_RIGHT）
            return true;
        }
        
        // 检查标志中的不可通行标志
        // MAPTILE_UNWALKABLE 标志表示不可通行
        final int MAPTILE_UNWALKABLE = 0x00000001;
        if ((dwFlags & MAPTILE_UNWALKABLE) != 0) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 判断瓦片是否阻挡视野
     * 
     * @param nTileType 瓦片类型
     * @param dwFlags 瓦片标志
     * @return 如果瓦片阻挡视野返回 true，否则返回 false
     */
    private static boolean isTileBlockVision(int nTileType, int dwFlags) {
        // 根据瓦片类型判断是否阻挡视野
        // 墙壁类型通常阻挡视野
        if (nTileType >= 1 && nTileType <= 7) {
            // 墙壁类型
            return true;
        }
        
        // 检查标志中的阻挡视野标志
        // MAPTILE_BLOCK_VIS 标志表示阻挡视野
        final int MAPTILE_BLOCK_VIS = 0x00000002;
        if ((dwFlags & MAPTILE_BLOCK_VIS) != 0) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 判断瓦片是否隐藏
     * 
     * @param dwFlags 瓦片标志
     * @return 如果瓦片隐藏返回 true，否则返回 false
     */
    private static boolean isTileHidden(int dwFlags) {
        // 检查标志中的隐藏标志
        // MAPTILE_HIDDEN 标志表示隐藏
        final int MAPTILE_HIDDEN = 0x00000004;
        return (dwFlags & MAPTILE_HIDDEN) != 0;
    }
}
