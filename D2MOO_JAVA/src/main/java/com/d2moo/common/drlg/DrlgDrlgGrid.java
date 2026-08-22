package com.d2moo.common.drlg;

import com.d2moo.common.util.D2Log;
import com.d2moo.common.util.D2Pool;

/**
 * Drlg 网格模块
 * 对应 C++ 文件：DrlgDrlgGrid.cpp
 */
public class DrlgDrlgGrid {
    
    // 标志操作枚举
    public enum FlagOperation {
        OR,                    // FLAG_OPERATION_OR
        AND,                   // FLAG_OPERATION_AND
        XOR,                   // FLAG_OPERATION_XOR
        OVERWRITE,             // FLAG_OPERATION_OVERWRITE
        OVERWRITE_IF_ZERO,     // FLAG_OPERATION_OVERWRITE_IF_ZERO
        AND_NEGATED            // FLAG_OPERATION_AND_NEGATED
    }
    
    // 标志操作函数数组
    private static final FlagOperationFunction[] gpfFlagOperations = {
        DrlgDrlgGrid::orFlag,
        DrlgDrlgGrid::andFlag,
        DrlgDrlgGrid::xorFlag,
        DrlgDrlgGrid::overwriteFlag,
        DrlgDrlgGrid::overwriteFlagIfZero,
        DrlgDrlgGrid::andNegatedFlag
    };
    
    @FunctionalInterface
    private interface FlagOperationFunction {
        void apply(int[] flag, int nFlag);
    }
    
    /**
     * D2Common.0x6FD75BA0
     * 覆盖标志
     */
    private static void overwriteFlag(int[] pFlag, int nFlag) {
        pFlag[0] = nFlag;
    }
    
    /**
     * D2Common.0x6FD75BB0
     * OR 标志
     */
    private static void orFlag(int[] pFlag, int nFlag) {
        pFlag[0] |= nFlag;
    }
    
    /**
     * D2Common.0x6FD75BC0
     * AND 标志
     */
    private static void andFlag(int[] pFlag, int nFlag) {
        pFlag[0] &= nFlag;
    }
    
    /**
     * D2Common.0x6FD75BD0
     * XOR 标志
     */
    private static void xorFlag(int[] pFlag, int nFlag) {
        pFlag[0] ^= nFlag;
    }
    
    /**
     * D2Common.0x6FD75BE0
     * 如果为零则覆盖标志
     */
    private static void overwriteFlagIfZero(int[] pFlag, int nFlag) {
        if (pFlag[0] == 0) {
            pFlag[0] = nFlag;
        }
    }
    
    /**
     * D2Common.0x6FD75BF0
     * AND 取反标志
     */
    private static void andNegatedFlag(int[] pFlag, int nFlag) {
        pFlag[0] &= ~nFlag;
    }
    
    /**
     * D2Common.0x6FD75C00
     * 检查网格是否有效
     */
    public static boolean isGridValid(D2DrlgGridStrc pDrlgGrid) {
        return pDrlgGrid != null && pDrlgGrid.getPCellsFlags() != null;
    }
    
    /**
     * D2Common.0x6FD75C20
     * 检查点是否在网格区域内
     */
    public static boolean isPointInsideGridArea(D2DrlgGridStrc pDrlgGrid, int x, int y) {
        if (pDrlgGrid == null) {
            return false;
        }
        return x >= 0 && x < pDrlgGrid.getNWidth() && y >= 0 && y < pDrlgGrid.getNHeight();
    }
    
    /**
     * D2Common.0x6FD75C50
     * 修改网格标志
     */
    public static void alterGridFlag(D2DrlgGridStrc pDrlgGrid, int x, int y, int flag, FlagOperation operation) {
        if (pDrlgGrid == null) {
            return;
        }
        int[] pFlag = pDrlgGrid.getFlagRef(x, y);
        gpfFlagOperations[operation.ordinal()].apply(pFlag, flag);
        pDrlgGrid.setFlagRef(x, y, pFlag);
    }
    
    /**
     * D2Common.0x6FD75C80
     * 获取网格标志指针
     */
    public static int[] getGridFlagsPointer(D2DrlgGridStrc pDrlgGrid, int x, int y) {
        if (pDrlgGrid == null) {
            return new int[1];
        }
        return pDrlgGrid.getFlagRef(x, y);
    }
    
    /**
     * D2Common.0x6FD75CA0
     * 获取网格条目
     */
    public static int getGridEntry(D2DrlgGridStrc pDrlgGrid, int x, int y) {
        if (pDrlgGrid == null) {
            return 0;
        }
        return pDrlgGrid.getFlag(x, y);
    }
    
    /**
     * D2Common.0x6FD75CC0
     * 修改所有网格标志
     */
    public static void alterAllGridFlags(D2DrlgGridStrc pDrlgGrid, int flag, FlagOperation operation) {
        if (pDrlgGrid == null) {
            return;
        }
        for (int nY = 0; nY < pDrlgGrid.getNHeight(); ++nY) {
            for (int nX = 0; nX < pDrlgGrid.getNWidth(); ++nX) {
                alterGridFlag(pDrlgGrid, nX, nY, flag, operation);
            }
        }
    }
    
    /**
     * D2Common.0x6FD75D20
     * 修改边缘网格标志
     * 
     * C++ 原始代码：
     * int* pFlagsFirstRow = &pDrlgGrid->pCellsFlags[pDrlgGrid->pCellsRowOffsets[0]];
     * int* pFlagsLastRow = &pDrlgGrid->pCellsFlags[pDrlgGrid->pCellsRowOffsets[pDrlgGrid->nHeight - 1]];
     * 
     * for (int i = 0; i < pDrlgGrid->nWidth; ++i) {
     *     gpfFlagOperations[eOperation](&pFlagsFirstRow[i], nFlag);
     *     gpfFlagOperations[eOperation](&pFlagsLastRow[i], nFlag);
     * }
     * 
     * for (int i = 1; i < pDrlgGrid->nHeight; ++i) {
     *     const int nCurRowOffset = pDrlgGrid->pCellsRowOffsets[i];
     *     gpfFlagOperations[eOperation](&pDrlgGrid->pCellsFlags[nCurRowOffset + 0], nFlag);
     *     gpfFlagOperations[eOperation](&pDrlgGrid->pCellsFlags[nCurRowOffset + pDrlgGrid->nWidth - 1], nFlag);
     * }
     */
    public static void alterEdgeGridFlags(D2DrlgGridStrc pDrlgGrid, int flag, FlagOperation operation) {
        if (pDrlgGrid == null || pDrlgGrid.getPCellsFlags() == null || pDrlgGrid.getPCellsRowOffsets() == null) {
            return;
        }
        
        int[] pCellsFlags = pDrlgGrid.getPCellsFlags();
        int[] pCellsRowOffsets = pDrlgGrid.getPCellsRowOffsets();
        int nWidth = pDrlgGrid.getNWidth();
        int nHeight = pDrlgGrid.getNHeight();
        
        if (nHeight == 0) {
            return;
        }
        
        // 修改第一行和最后一行
        int firstRowOffset = pCellsRowOffsets[0];
        int lastRowOffset = pCellsRowOffsets[nHeight - 1];
        
        for (int i = 0; i < nWidth; ++i) {
            applyFlagOperation(pCellsFlags, firstRowOffset + i, flag, operation);
            applyFlagOperation(pCellsFlags, lastRowOffset + i, flag, operation);
        }
        
        // 修改第一列和最后一列（跳过第一行和最后一行，因为它们已经处理过了）
        for (int i = 1; i < nHeight; ++i) {
            int nCurRowOffset = pCellsRowOffsets[i];
            applyFlagOperation(pCellsFlags, nCurRowOffset + 0, flag, operation);
            applyFlagOperation(pCellsFlags, nCurRowOffset + nWidth - 1, flag, operation);
        }
    }
    
    /**
     * 应用标志操作（辅助方法）
     */
    private static void applyFlagOperation(int[] pFlags, int index, int flag, FlagOperation operation) {
        if (pFlags == null || index < 0 || index >= pFlags.length) {
            return;
        }
        
        switch (operation) {
            case OR:
                pFlags[index] |= flag;
                break;
            case AND:
                pFlags[index] &= flag;
                break;
            case XOR:
                pFlags[index] ^= flag;
                break;
            case OVERWRITE:
                pFlags[index] = flag;
                break;
            case OVERWRITE_IF_ZERO:
                if (pFlags[index] == 0) {
                    pFlags[index] = flag;
                }
                break;
            case AND_NEGATED:
                pFlags[index] &= ~flag;
                break;
        }
    }
    
    /**
     * D2Common.0x6FD76230
     * 初始化网格单元格
     * 
     * C++ 原始代码：
     * pDrlgGrid->pCellsFlags = (int*)D2_CALLOC_POOL(pMemPool, sizeof(int) * width * height);
     * pDrlgGrid->pCellsRowOffsets = (int*)D2_CALLOC_POOL(pMemPool, sizeof(int) * height);
     * 
     * Java 实现：使用类型安全的数组分配方法，无需强制类型转换
     */
    public static void initializeGridCells(Object memPool, D2DrlgGridStrc pDrlgGrid, int width, int height) {
        if (pDrlgGrid == null) {
            return;
        }
        
        pDrlgGrid.setNWidth(width);
        pDrlgGrid.setNHeight(height);
        
        // 使用内存池分配 int 数组（类型安全，无需强制转换）
        pDrlgGrid.setPCellsFlags(D2Pool.callocIntArrayPool(memPool, width * height));
        pDrlgGrid.setPCellsRowOffsets(D2Pool.callocIntArrayPool(memPool, height));
        
        for (int i = 0; i < height; ++i) {
            pDrlgGrid.getPCellsRowOffsets()[i] = i * width;
        }
    }
    
    /**
     * D2Common.0x6FD763E0
     * 释放网格
     */
    public static void freeGrid(Object memPool, D2DrlgGridStrc pDrlgGrid) {
        if (pDrlgGrid == null) {
            return;
        }
        
        // 若使用内存池，可调用 D2Pool.freePool 登记释放（Java 仍由 GC 回收）
        if (memPool != null) {
            if (pDrlgGrid.getPCellsFlags() != null) {
                com.d2moo.common.util.D2Pool.freePool(memPool, pDrlgGrid.getPCellsFlags());
            }
            if (pDrlgGrid.getPCellsRowOffsets() != null) {
                com.d2moo.common.util.D2Pool.freePool(memPool, pDrlgGrid.getPCellsRowOffsets());
            }
        }
        pDrlgGrid.setPCellsFlags(null);
        pDrlgGrid.setPCellsRowOffsets(null);
    }
    
    /**
     * D2Common.0x6FD76410
     * 重置网格
     */
    public static void resetGrid(D2DrlgGridStrc pDrlgGrid) {
        if (pDrlgGrid == null) {
            return;
        }
        pDrlgGrid.setPCellsFlags(null);
        pDrlgGrid.setPCellsRowOffsets(null);
        pDrlgGrid.setNWidth(0);
        pDrlgGrid.setNHeight(0);
    }
    
    /**
     * D2Common.0x6FD76310
     * 分配新单元格标志
     * 
     * C++ 原始代码：
     * pDrlgGrid->nWidth = pDrlgCoord->nWidth;
     * pDrlgGrid->nHeight = pDrlgCoord->nHeight;
     * pDrlgGrid->pCellsFlags = &pCellPos[pDrlgCoord->nPosX + nWidth * pDrlgCoord->nPosY];
     * pDrlgGrid->pCellsRowOffsets = (int32_t*)D2_ALLOC_POOL(pMemPool, sizeof(int) * pDrlgCoord->nHeight);
     * 
     * 注意：pCellsFlags 指向 pCellPos 数组的某个位置，不是新分配的内存
     */
    public static void fillNewCellFlags(Object memPool, D2DrlgGridStrc pDrlgGrid, int[] pCellPos, 
            D2DrlgCoord pDrlgCoord, int width) {
        if (pDrlgGrid == null || pDrlgCoord == null || pCellPos == null) {
            return;
        }
        
        // 设置网格宽度和高度
        pDrlgGrid.setNWidth(pDrlgCoord.getNWidth());
        pDrlgGrid.setNHeight(pDrlgCoord.getNHeight());
        
        // 计算起始索引：pDrlgCoord->nPosX + nWidth * pDrlgCoord->nPosY
        int startIndex = pDrlgCoord.getNPosX() + width * pDrlgCoord.getNPosY();
        
        // 验证索引有效性
        if (startIndex < 0 || startIndex >= pCellPos.length) {
            return;
        }
        
        // Native keeps a view into pCellPos with the source row stride. Java
        // arrays cannot represent an offset view, so copy each source row into
        // a compact grid and use the compact width for row offsets. A single
        // contiguous copy corrupts every row after the first when width is
        // larger than pDrlgCoord.nWidth.
        int gridWidth = pDrlgCoord.getNWidth();
        int gridHeight = pDrlgCoord.getNHeight();
        if (gridWidth < 0 || gridHeight < 0) {
            return;
        }
        int nCells = Math.multiplyExact(gridWidth, gridHeight);
        int lastRowStart = startIndex + Math.max(0, gridHeight - 1) * width;
        if (lastRowStart < 0 || lastRowStart > pCellPos.length - gridWidth) {
            return;
        }

        int[] pCellsFlags = D2Pool.callocIntArrayPool(memPool, nCells);
        for (int y = 0; y < gridHeight; y++) {
            System.arraycopy(pCellPos, startIndex + y * width,
                    pCellsFlags, y * gridWidth, gridWidth);
        }
        pDrlgGrid.setPCellsFlags(pCellsFlags);
        
        // 分配行偏移数组
        int[] pCellsRowOffsets = D2Pool.callocIntArrayPool(memPool, pDrlgCoord.getNHeight());
        
        // The copied storage is compact, so its stride is gridWidth.
        for (int i = 0; i < gridHeight; ++i) {
            pCellsRowOffsets[i] = i * gridWidth;
        }
        
        pDrlgGrid.setPCellsRowOffsets(pCellsRowOffsets);
        
        // 设置 unk0x10 标志为 1（表示已初始化）
        pDrlgGrid.setUnk0x10(1);
    }
    
    /**
     * D2Common.0x6FD762B0
     * 填充网格
     */
    public static void fillGrid(D2DrlgGridStrc pDrlgGrid, int width, int height, int[] pCellPos, 
            int[] pCellRowOffsets) {
        if (pDrlgGrid == null) {
            return;
        }
        pDrlgGrid.setNWidth(width);
        pDrlgGrid.setNHeight(height);
        pDrlgGrid.setPCellsFlags(pCellPos);
        pDrlgGrid.setPCellsRowOffsets(pCellRowOffsets);
    }
    
    /**
     * D2Common.0x6FD76380
     * 分配单元格偏移和标志
     */
    public static void assignCellsOffsetsAndFlags(D2DrlgGridStrc pDrlgGrid, int[] pCellPos, 
            D2DrlgCoord pDrlgCoord, int width, int[] pCellFlags) {
        if (pDrlgGrid == null || pDrlgCoord == null) {
            return;
        }
        
        int nHeight = pDrlgCoord.getNHeight();
        int nWidth = pDrlgCoord.getNWidth();
        
        // 初始化行偏移
        int[] pRowOffsets = new int[nHeight];
        for (int i = 0; i < nHeight; ++i) {
            pRowOffsets[i] = i * width;
        }
        
        // 分配单元格标志数组
        int[] pFlags = new int[width * nHeight];
        
        // 复制单元格标志
        if (pCellFlags != null) {
            int copySize = Math.min(pCellFlags.length, pFlags.length);
            System.arraycopy(pCellFlags, 0, pFlags, 0, copySize);
        }
        
        pDrlgGrid.setPCellsFlags(pFlags);
        pDrlgGrid.setPCellsRowOffsets(pRowOffsets);
        pDrlgGrid.setNWidth(width);
        pDrlgGrid.setNHeight(nHeight);
    }
    
    /**
     * D2Common.0x6FD75DE0
     * 未命名函数，设置顶点之间的网格标志
     */
    public static void sub_6FD75DE0(D2DrlgGridStrc pDrlgGrid, D2DrlgVertexStrc pDrlgVertex, 
            int flag, FlagOperation operation, boolean alterNextVertex) {
        if (pDrlgGrid == null || pDrlgVertex == null || pDrlgVertex.getPNext() == null) {
            return;
        }
        
        D2DrlgVertexStrc next = pDrlgVertex.getPNext();
        
        // 计算起点和终点
        int startX = pDrlgVertex.getNPosX();
        int startY = pDrlgVertex.getNPosY();
        int endX = next.getNPosX();
        int endY = next.getNPosY();
        
        // 使用 Bresenham 算法在两点之间设置标志
        int dx = Math.abs(endX - startX);
        int dy = Math.abs(endY - startY);
        int sx = startX < endX ? 1 : -1;
        int sy = startY < endY ? 1 : -1;
        int err = dx - dy;
        
        int x = startX;
        int y = startY;
        
        while (true) {
            if (isPointInsideGridArea(pDrlgGrid, x, y)) {
                alterGridFlag(pDrlgGrid, x, y, flag, operation);
            }
            
            if (x == endX && y == endY) {
                break;
            }
            
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
        
        if (alterNextVertex && next.getPNext() != null) {
            sub_6FD75DE0(pDrlgGrid, next, flag, operation, false);
        }
    }
    
    /**
     * D2Common.0x6FD75F10
     * 设置顶点网格标志
     */
    public static void setVertexGridFlags(D2DrlgGridStrc pDrlgGrid, D2DrlgVertexStrc pDrlgVertex, int flag) {
        if (pDrlgGrid == null || pDrlgVertex == null) {
            return;
        }
        
        // 遍历顶点链表，设置每个顶点的网格标志
        D2DrlgVertexStrc current = pDrlgVertex;
        D2DrlgVertexStrc start = current;
        int count = 0;
        int maxCount = 1000; // 防止无限循环
        
        while (current != null && count < maxCount) {
            if (isPointInsideGridArea(pDrlgGrid, current.getNPosX(), current.getNPosY())) {
                alterGridFlag(pDrlgGrid, current.getNPosX(), current.getNPosY(), flag, FlagOperation.OR);
            }
            
            current = current.getPNext();
            if (current == start) {
                break; // 循环链表
            }
            count++;
        }
    }
    
    /**
     * D2Common.0x6FD75F60
     * 未命名函数，设置顶点和坐标之间的网格标志
     */
    public static void sub_6FD75F60(D2DrlgGridStrc pDrlgGrid, D2DrlgVertexStrc pDrlgVertex, 
            D2DrlgCoord pDrlgCoord, int flag, FlagOperation operation, int size) {
        if (pDrlgGrid == null || pDrlgVertex == null || pDrlgVertex.getPNext() == null || pDrlgCoord == null) {
            return;
        }
        
        D2DrlgVertexStrc next = pDrlgVertex.getPNext();
        int x = pDrlgVertex.getNPosX();
        int y = pDrlgVertex.getNPosY();
        
        int xDiff = next.getNPosX() - x;
        int yDiff = next.getNPosY() - y;
        
        int xInc = xDiff >= 0 ? 1 : -1;
        int yInc = yDiff >= 0 ? 1 : -1;
        
        if (xDiff < 0) xDiff = -xDiff;
        if (yDiff < 0) yDiff = -yDiff;
        
        int indexX = x - pDrlgCoord.getNPosX();
        int indexY = y - pDrlgCoord.getNPosY();
        
        // 根据 C++ 代码逻辑实现
        if (xDiff >= yDiff) {
            // X 方向为主
            for (int i = 0; i < size; i++) {
                int checkY = y + i;
                if (DrlgDrlgRoom.areXYInsideCoordinates(pDrlgCoord, x, checkY)) {
                    alterGridFlag(pDrlgGrid, indexX, indexY + i, flag, operation);
                }
            }
            
            int check = 0;
            for (int j = 0; j < xDiff; j++) {
                x += xInc;
                check += yDiff;
                
                if (check > xDiff) {
                    y += yInc;
                    check -= xDiff;
                }
                
                indexX = x - pDrlgCoord.getNPosX();
                indexY = y - pDrlgCoord.getNPosY();
                
                for (int i = 0; i < size; i++) {
                    int checkY = y + i;
                    if (DrlgDrlgRoom.areXYInsideCoordinates(pDrlgCoord, x, checkY)) {
                        alterGridFlag(pDrlgGrid, indexX, indexY + i, flag, operation);
                    }
                }
            }
        } else {
            // Y 方向为主
            for (int i = 0; i < size; i++) {
                int checkX = x + i;
                if (DrlgDrlgRoom.areXYInsideCoordinates(pDrlgCoord, checkX, y)) {
                    alterGridFlag(pDrlgGrid, indexX + i, indexY, flag, operation);
                }
            }
            
            int check = 0;
            for (int j = 0; j < yDiff; j++) {
                y += yInc;
                check += xDiff;
                
                if (check > yDiff) {
                    x += xInc;
                    check -= yDiff;
                }
                
                indexX = x - pDrlgCoord.getNPosX();
                indexY = y - pDrlgCoord.getNPosY();
                
                for (int i = 0; i < size; i++) {
                    int checkX = x + i;
                    if (DrlgDrlgRoom.areXYInsideCoordinates(pDrlgCoord, checkX, y)) {
                        alterGridFlag(pDrlgGrid, indexX + i, indexY, flag, operation);
                    }
                }
            }
        }
    }
    
}
