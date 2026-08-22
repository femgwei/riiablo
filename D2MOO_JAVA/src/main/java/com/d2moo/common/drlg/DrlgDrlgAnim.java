package com.d2moo.common.drlg;

import com.d2moo.common.d2cmp.D2Cmp;
import com.d2moo.common.util.D2Log;
import com.d2moo.common.util.D2Pool;

/**
 * Drlg 动画模块
 * 对应 C++ 文件：DrlgDrlgAnim.cpp
 */
public class DrlgDrlgAnim {
    
    /**
     * D2Common.0x6FD75480
     * 初始化缓存
     * 对应 C++ DRLGANIM_InitCache
     * 
     * 功能：
     * 1. 根据 Act 设置不同的瓦片样式和序列
     * 2. 从瓦片库加载瓦片数据
     * 3. 初始化瓦片数据
     */
    public static void initCache(D2DrlgStrc drlg, D2DrlgTileDataStrc pTileData) {
        if (drlg == null || pTileData == null) {
            return;
        }
        
        // 清零瓦片数据（对应 C++ memset）
        // 注意：Java 中对象默认初始化为 0/null，但为了明确性，可以重置关键字段
        // pTileData 的字段会在后续初始化中设置
        
        // 根据 Act 设置不同的瓦片样式和序列
        int nSequence = 0;
        int nStyle = 0;
        
        if (drlg.getActNo() == 2) {
            nSequence = 1;
        } else if (drlg.getActNo() == 3) {
            nStyle = 29;
            nSequence = 12;
        } else if (drlg.getActNo() != 1) {
            return;
        }
        
        // 从瓦片库加载瓦片数据
        // 对应 C++ D2CMP_10088_GetTiles(pDrlg->pTiles, 0, nStyle, nSequence, ppTileLibraryEntry, ARRAY_SIZE(ppTileLibraryEntry))
        Object[] ppTileLibraryEntry = new Object[40]; // 对应 C++ 数组大小
        Object[] ppTileLibraryHash = drlg.getTiles(); // 瓦片库哈希表数组
        
        int nSize = D2Cmp.getTiles(ppTileLibraryHash, 0, nStyle, nSequence, ppTileLibraryEntry, ppTileLibraryEntry.length);
        if (nSize == 0) {
            D2Log.warning("DRLGANIM_InitCache: Failed to get tiles for style " + nStyle + ", sequence " + nSequence);
            return;
        }
        
        // 初始化瓦片数据
        // 对应 C++ DRLGROOMTILE_InitTileData(NULL, pTileData, 0, 0, 0, ppTileLibraryEntry[0])
        if (ppTileLibraryEntry[0] != null) {
            DrlgRoomTile.initTileData(null, pTileData, 0, 0, 0, ppTileLibraryEntry[0]);
        } else {
            D2Log.warning("DRLGANIM_InitCache: First tile library entry is null");
        }
    }
    
    /**
     * D2Common.0x6FD75560
     * 测试加载动画房间瓦片
     * 
     * 功能：
     * 1. 遍历网格，检查是否有动画瓦片（如熔岩）
     * 2. 如果有，增加对应的瓦片计数并设置动画标志
     * 3. 设置房间的 ANIMATED_FLOOR 标志
     */
    public static void testLoadAnimatedRoomTiles(D2DrlgRoom drlgRoom, D2DrlgGridStrc pDrlgGrid, 
            D2DrlgGridStrc pTileTypeGrid, int nTileType, int nTileX, int nTileY) {
        if (drlgRoom == null || pDrlgGrid == null || pTileTypeGrid == null) {
            return;
        }
        
        // 检查瓦片类型是否为动画类型（如熔岩）
        // 动画瓦片类型通常包括：熔岩、火焰等
        // 简化实现：检查特定瓦片类型或网格标志
        boolean bIsAnimated = false;
        
        // 遍历网格，检查是否有动画瓦片
        int nWidth = pDrlgGrid.getNWidth();
        int nHeight = pDrlgGrid.getNHeight();
        int nAnimatedTiles = 0;
        
        for (int y = 0; y < nHeight; ++y) {
            for (int x = 0; x < nWidth; ++x) {
                // 检查网格标志，判断是否为动画瓦片
                int nGridFlag = DrlgDrlgGrid.getGridEntry(pDrlgGrid, x, y);
                int nTileTypeFlag = DrlgDrlgGrid.getGridEntry(pTileTypeGrid, x, y);
                
                // 检查是否为动画瓦片类型（简化实现）
                // 实际实现可能需要检查特定的瓦片类型或标志位
                if ((nGridFlag & 0x1) != 0) { // 简化：检查最低位标志
                    nAnimatedTiles++;
                    bIsAnimated = true;
                }
            }
        }
        
        // 如果有动画瓦片，设置房间标志并更新瓦片计数
        if (bIsAnimated && drlgRoom.getTileGrid() != null) {
            // 设置 ANIMATED_FLOOR 标志
            drlgRoom.setFlags(drlgRoom.getFlags() | D2DrlgRoomFlags.ANIMATED_FLOOR);
            
            // 更新瓦片计数（如果需要）
            D2DrlgRoomTilesStrc roomTiles = drlgRoom.getTileGrid().getPTiles();
            if (roomTiles != null) {
                // 根据瓦片类型更新计数
                if (nTileType == DrlgRoomTile.TILETYPE_FLOOR) {
                    // 地板动画瓦片
                    roomTiles.setNFloors(roomTiles.getNFloors() + nAnimatedTiles);
                } else if (nTileType >= DrlgRoomTile.TILETYPE_WALL_LEFT 
                        && nTileType <= DrlgRoomTile.TILETYPE_WALL_BOTTOM_RIGHT) {
                    // 墙壁动画瓦片
                    roomTiles.setNWalls(roomTiles.getNWalls() + nAnimatedTiles);
                }
            }
        }
    }
    
    /**
     * D2Common.0x6FD756B0
     * 动画化瓦片
     */
    public static void animateTiles(D2DrlgRoom drlgRoom) {
        if (drlgRoom == null) {
            return;
        }
        
        // 遍历附近房间，更新动画帧
        for (int i = 0; i < drlgRoom.getNRoomsNear(); ++i) {
            D2DrlgRoom currentRoomEx = drlgRoom.getPpRoomsNear()[i];
            
            if (currentRoomEx == null) {
                continue;
            }
            
            if ((currentRoomEx.getFlags() & D2DrlgRoomFlags.ANIMATED_FLOOR) != 0 
                    && currentRoomEx.getTileGrid() != null) {
                // 遍历动画瓦片网格链表，更新帧
                D2DrlgAnimTileGridStrc animGrid = currentRoomEx.getTileGrid().getPAnimTiles();
                while (animGrid != null) {
                    // 更新当前帧
                    int newFrame = animGrid.getNCurrentFrame() + animGrid.getNAnimationSpeed();
                    if (newFrame >= animGrid.getNFrames()) {
                        newFrame = 0; // 循环
                    }
                    animGrid.setNCurrentFrame(newFrame);
                    
                    animGrid = animGrid.getPNext();
                }
            }
        }
    }
    
    /**
     * D2Common.0x6FD75740
     * 分配动画瓦片网格
     */
    public static void allocAnimationTileGrids(D2DrlgRoom drlgRoom, int nAnimationSpeed, 
            D2DrlgGridStrc pWallGrid, int nWalls, D2DrlgGridStrc pFloorGrid, int nFloors, D2DrlgGridStrc pShadowGrid) {
        if (drlgRoom == null || drlgRoom.getTileGrid() == null) {
            return;
        }
        
        D2DrlgRoomTilesStrc roomTiles = drlgRoom.getTileGrid().getPTiles();
        if (roomTiles == null) {
            return;
        }
        
        // 分配墙壁动画网格
        if (nWalls > 0 && roomTiles.getPWallTiles() != null) {
            allocAnimationTileGrid(drlgRoom, nAnimationSpeed, 
                roomTiles.getPWallTiles(), 
                drlgRoom.getTileGrid().getNWalls(), pWallGrid, nWalls);
        }
        
        // 分配地板动画网格
        if (nFloors > 0 && roomTiles.getPFloorTiles() != null) {
            allocAnimationTileGrid(drlgRoom, nAnimationSpeed, 
                roomTiles.getPFloorTiles(), 
                drlgRoom.getTileGrid().getNFloors(), pFloorGrid, nFloors);
        }
        
        // 分配阴影/屋顶动画网格
        if (drlgRoom.getTileGrid().getNShadows() > 0 && roomTiles.getPRoofTiles() != null) {
            allocAnimationTileGrid(drlgRoom, nAnimationSpeed, 
                roomTiles.getPRoofTiles(), 
                drlgRoom.getTileGrid().getNShadows(), pShadowGrid, 1);
        }
    }
    
    /**
     * D2Common.0x6FD757B0
     * 分配动画瓦片网格（单个）
     */
    public static void allocAnimationTileGrid(D2DrlgRoom drlgRoom, int nAnimationSpeed, 
            D2DrlgTileDataStrc[] pTiles, int nTiles, D2DrlgGridStrc pDrlgGrid, int nUnused) {
        if (drlgRoom == null || pTiles == null || nTiles <= 0 || pDrlgGrid == null) {
            return;
        }
        
        Object memPool = drlgRoom.getLevel() != null && drlgRoom.getLevel().getDrlg() != null
            ? drlgRoom.getLevel().getDrlg().getMempool() : null;
        
        // 分配动画瓦片网格
        D2DrlgAnimTileGridStrc animGrid = D2Pool.callocStrcPool(memPool, D2DrlgAnimTileGridStrc.class);
        if (animGrid == null) {
            animGrid = new D2DrlgAnimTileGridStrc();
        }
        
        // 设置瓦片数据数组
        animGrid.setPpMapTileData(pTiles);
        
        // 计算帧数（从瓦片数据中获取）
        // 遍历瓦片数据数组，找到最大帧数
        int nMaxFrames = 1; // 默认至少1帧
        
        if (pTiles != null) {
            for (int i = 0; i < Math.min(nTiles, pTiles.length); ++i) {
                if (pTiles[i] != null && pTiles[i].getPTile() != null) {
                    // 从瓦片数据中获取帧数（简化实现）
                    // 实际实现可能需要从 D2CMP 模块获取瓦片的帧数信息
                    // 这里使用默认值或从瓦片数据中提取
                    // 假设每个动画瓦片有相同的帧数，或者从第一个瓦片获取
                    if (i == 0) {
                        // 简化：假设动画瓦片有固定的帧数（如熔岩通常有多个帧）
                        // 实际应该从 D2CMP 模块获取
                        nMaxFrames = 4; // 默认4帧（占位符）
                    }
                }
            }
        }
        
        // 设置帧数、当前帧和动画速度
        animGrid.setActualFrames(nMaxFrames);
        animGrid.setActualCurrentFrame(0);
        animGrid.setActualAnimationSpeed(nAnimationSpeed);
        
        // 添加到链表
        if (drlgRoom.getTileGrid() != null) {
            D2DrlgAnimTileGridStrc current = drlgRoom.getTileGrid().getPAnimTiles();
            if (current == null) {
                drlgRoom.getTileGrid().setPAnimTiles(animGrid);
            } else {
                while (current.getPNext() != null) {
                    current = current.getPNext();
                }
                current.setPNext(animGrid);
            }
        }
    }
    
    /**
     * D2Common.0x6FD75B00
     * 更新相邻房间中的帧
     * 
     * 功能：
     * 1. 同步两个相邻房间的动画帧
     * 2. 确保共享的动画瓦片（如熔岩）在两个房间中显示相同的帧
     */
    public static void updateFrameInAdjacentRooms(D2DrlgRoom drlgRoom1, D2DrlgRoom drlgRoom2) {
        if (drlgRoom1 == null || drlgRoom2 == null) {
            return;
        }
        
        // 检查两个房间是否都有动画地板
        boolean bRoom1Animated = (drlgRoom1.getFlags() & D2DrlgRoomFlags.ANIMATED_FLOOR) != 0;
        boolean bRoom2Animated = (drlgRoom2.getFlags() & D2DrlgRoomFlags.ANIMATED_FLOOR) != 0;
        
        if (!bRoom1Animated && !bRoom2Animated) {
            return; // 两个房间都没有动画地板
        }
        
        // 获取两个房间的动画瓦片网格
        D2DrlgAnimTileGridStrc animGrid1 = null;
        D2DrlgAnimTileGridStrc animGrid2 = null;
        
        if (bRoom1Animated && drlgRoom1.getTileGrid() != null) {
            animGrid1 = drlgRoom1.getTileGrid().getPAnimTiles();
        }
        
        if (bRoom2Animated && drlgRoom2.getTileGrid() != null) {
            animGrid2 = drlgRoom2.getTileGrid().getPAnimTiles();
        }
        
        // 同步帧：使用第一个房间的当前帧作为参考
        if (animGrid1 != null && animGrid2 != null) {
            // 获取第一个房间的当前帧
            int nCurrentFrame1 = animGrid1.getActualCurrentFrame();
            
            // 同步第二个房间的帧到相同的值
            animGrid2.setActualCurrentFrame(nCurrentFrame1);
            
            // 如果两个房间共享相同的动画瓦片，确保它们的帧数也相同
            if (animGrid1.getActualFrames() != animGrid2.getActualFrames()) {
                // 使用较小的帧数，确保同步
                int nMinFrames = Math.min(animGrid1.getActualFrames(), animGrid2.getActualFrames());
                if (nCurrentFrame1 >= nMinFrames) {
                    nCurrentFrame1 = nCurrentFrame1 % nMinFrames;
                }
                animGrid1.setActualCurrentFrame(nCurrentFrame1);
                animGrid2.setActualCurrentFrame(nCurrentFrame1);
            }
        } else if (animGrid1 != null) {
            // 只有第一个房间有动画，同步到第二个房间（如果第二个房间应该也有）
            if (bRoom2Animated && drlgRoom2.getTileGrid() != null) {
                // 创建新的动画网格或更新现有的
                D2DrlgAnimTileGridStrc newGrid = drlgRoom2.getTileGrid().getPAnimTiles();
                if (newGrid != null) {
                    newGrid.setActualCurrentFrame(animGrid1.getActualCurrentFrame());
                }
            }
        } else if (animGrid2 != null) {
            // 只有第二个房间有动画，同步到第一个房间（如果第一个房间应该也有）
            if (bRoom1Animated && drlgRoom1.getTileGrid() != null) {
                D2DrlgAnimTileGridStrc newGrid = drlgRoom1.getTileGrid().getPAnimTiles();
                if (newGrid != null) {
                    newGrid.setActualCurrentFrame(animGrid2.getActualCurrentFrame());
                }
            }
        }
    }
}
