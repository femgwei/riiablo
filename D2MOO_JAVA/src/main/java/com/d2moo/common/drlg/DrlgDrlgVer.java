package com.d2moo.common.drlg;

import com.d2moo.common.util.D2Log;
import com.d2moo.common.util.D2Pool;

/**
 * Drlg 顶点模块
 * 对应 C++ 文件：DrlgDrlgVer.cpp
 */
public class DrlgDrlgVer {
    
    /**
     * D2Common.0x6FD782A0
     * 分配顶点
     */
    public static D2DrlgVertexStrc allocVertex(Object memPool, byte direction) {
        D2DrlgVertexStrc vertex = D2Pool.callocStrcPool(memPool, D2DrlgVertexStrc.class);
        if (vertex == null) {
            vertex = new D2DrlgVertexStrc();
        }
        vertex.setNDirection(direction);
        return vertex;
    }
    
    /**
     * D2Common.0x6FD782D0
     * 创建顶点
     * 
     * 功能：
     * 1. 调整坐标（根据方向）
     * 2. 创建基础顶点（4个角）
     * 3. 处理房间数据，插入额外顶点
     */
    public static void createVertices(Object memPool, D2DrlgVertexStrc[] ppVertices, 
            D2DrlgCoord pDrlgCoord, byte direction, D2DrlgOrth pDrlgRoomData) {
        if (ppVertices == null || ppVertices.length == 0 || pDrlgCoord == null) {
            return;
        }
        
        // 1. 调整坐标（根据方向）
        int nPosX = pDrlgCoord.getNTileXPos();
        int nPosY = pDrlgCoord.getNTileYPos();
        // Native DRLGVER_CreateVertices temporarily decrements both extents
        // before creating the four corner vertices.  Coordinates describe
        // inclusive grid-cell bounds: an 80-tile level becomes cells 0..9
        // after the caller divides by 8, not 0..10.  Using the full extent
        // put the right and bottom edges outside the outdoor grid, leaving an
        // open border which the corner flood-fill then marked entirely blank.
        int nWidth = pDrlgCoord.getNTileWidth() - 1;
        int nHeight = pDrlgCoord.getNTileHeight() - 1;
        
        // 根据方向调整坐标（简化实现，实际可能需要更复杂的变换）
        // 方向值：0=无旋转, 1=90度, 2=180度, 3=270度
        int nAdjustedX = nPosX;
        int nAdjustedY = nPosY;
        int nAdjustedWidth = nWidth;
        int nAdjustedHeight = nHeight;
        
        if (direction == 1 || direction == 3) {
            // 90度或270度旋转，交换宽度和高度
            int temp = nAdjustedWidth;
            nAdjustedWidth = nAdjustedHeight;
            nAdjustedHeight = temp;
        }
        
        // 2. 创建基础顶点（4个角）
        D2DrlgVertexStrc pFirstVertex = allocVertex(memPool, direction);
        pFirstVertex.setNPosX(nAdjustedX);
        pFirstVertex.setNPosY(nAdjustedY);
        
        D2DrlgVertexStrc pSecondVertex = allocVertex(memPool, direction);
        pSecondVertex.setNPosX(nAdjustedX + nAdjustedWidth);
        pSecondVertex.setNPosY(nAdjustedY);
        pFirstVertex.setPNext(pSecondVertex);
        
        D2DrlgVertexStrc pThirdVertex = allocVertex(memPool, direction);
        pThirdVertex.setNPosX(nAdjustedX + nAdjustedWidth);
        pThirdVertex.setNPosY(nAdjustedY + nAdjustedHeight);
        pSecondVertex.setPNext(pThirdVertex);
        
        D2DrlgVertexStrc pFourthVertex = allocVertex(memPool, direction);
        pFourthVertex.setNPosX(nAdjustedX);
        pFourthVertex.setNPosY(nAdjustedY + nAdjustedHeight);
        pThirdVertex.setPNext(pFourthVertex);
        
        // 形成循环链表
        pFourthVertex.setPNext(pFirstVertex);
        
        // 3. The native routine can splice room-data vertices into this
        // polygon.  The old Java approximation inserted arbitrary diagonal
        // edges, which makes the outdoor border walker non-convergent.  Keep
        // the guaranteed orthogonal rectangle until that splice algorithm is
        // ported faithfully; retain a diagnostic so this limitation is
        // visible in fixed-seed traces.
        if (pDrlgRoomData != null && pDrlgRoomData.getPBox() != null) {
            D2Log.debug("DRLG_VERTEX roomData splice deferred box=(%d,%d %dx%d)",
                    pDrlgRoomData.getPBox().getNTileXPos(), pDrlgRoomData.getPBox().getNTileYPos(),
                    pDrlgRoomData.getPBox().getNTileWidth(), pDrlgRoomData.getPBox().getNTileHeight());
        }
        
        ppVertices[0] = pFirstVertex;
    }
    
    /**
     * D2Common.0x6FD786C0
     * 释放顶点
     * 
     * 功能：
     * 1. 遍历顶点链表
     * 2. 释放每个顶点节点
     * 3. 清空指针
     */
    public static void freeVertices(Object memPool, D2DrlgVertexStrc[] ppVertices) {
        if (ppVertices == null || ppVertices[0] == null) {
            return;
        }
        
        D2DrlgVertexStrc vertex = ppVertices[0];
        D2DrlgVertexStrc start = vertex;
        int maxIterations = 1000; // 防止无限循环
        int iterations = 0;
        
        while (vertex != null && iterations < maxIterations) {
            D2DrlgVertexStrc next = vertex.getPNext();
            
            // 如果形成循环链表，需要检查是否回到起点
            if (next == start && iterations > 0) {
                // 已经遍历完整个循环链表
                D2Pool.freePool(memPool, vertex);
                break;
            }
            
            D2Pool.freePool(memPool, vertex);
            vertex = next;
            iterations++;
        }
        
        ppVertices[0] = null;
    }
    
    /**
     * D2Common.0x6FD78730
     * 获取坐标差
     */
    public static void getCoordDiff(Object pDrlgVertex, int[] pDiffX, int[] pDiffY) {
        if (pDiffX == null || pDiffX.length == 0 || pDiffY == null || pDiffY.length == 0) {
            return;
        }

        if (!(pDrlgVertex instanceof D2DrlgVertexStrc)) {
            pDiffX[0] = 0;
            pDiffY[0] = 0;
            return;
        }

        D2DrlgVertexStrc vertex = (D2DrlgVertexStrc) pDrlgVertex;
        D2DrlgVertexStrc next = vertex.getPNext();

        if (next == null) {
            pDiffX[0] = 0;
            pDiffY[0] = 0;
            return;
        }

        int diffX = next.getNPosX() - vertex.getNPosX();
        int diffY = next.getNPosY() - vertex.getNPosY();

        // 归一化为 -1 / 0 / 1，与 C++ DRLGVER_GetCoordDiff 保持一致
        if (diffX >= 0) {
            diffX = (diffX > 0) ? 1 : 0;
        } else {
            diffX = -1;
        }

        if (diffY >= 0) {
            diffY = (diffY > 0) ? 1 : 0;
        } else {
            diffY = -1;
        }

        pDiffX[0] = diffX;
        pDiffY[0] = diffY;
    }
}
