package com.d2moo.common.drlg;

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

        // Native DRLGVER_CreateVertices builds the polygon counter-clockwise,
        // using inclusive right/bottom bounds.  The order is significant to
        // the outdoor border walker and must not be replaced by a rotated
        // rectangle approximation.
        int originX = pDrlgCoord.getNPosX();
        int originY = pDrlgCoord.getNPosY();
        int width = pDrlgCoord.getNWidth() - 1;
        int height = pDrlgCoord.getNHeight() - 1;

        D2DrlgVertexStrc bottomLeft = allocVertex(memPool, direction);
        bottomLeft.setNPosX(originX);
        bottomLeft.setNPosY(originY + height);

        D2DrlgVertexStrc topLeft = allocVertex(memPool, direction);
        topLeft.setNPosX(originX);
        topLeft.setNPosY(originY);
        bottomLeft.setPNext(topLeft);

        D2DrlgVertexStrc topRight = allocVertex(memPool, direction);
        topRight.setNPosX(originX + width);
        topRight.setNPosY(originY);
        topLeft.setPNext(topRight);

        D2DrlgVertexStrc bottomRight = allocVertex(memPool, direction);
        bottomRight.setNPosX(originX + width);
        bottomRight.setNPosY(originY + height);
        topRight.setPNext(bottomRight);
        bottomRight.setPNext(bottomLeft);
        ppVertices[0] = bottomLeft;

        // Splice level-link intervals into their matching polygon edges.  A
        // flagged vertex is the native marker consumed by SetOutGridLinkFlags
        // and later by Act 1 transition/path generation.
        for (D2DrlgOrth roomData = pDrlgRoomData; roomData != null; roomData = roomData.getPNext()) {
            D2DrlgCoord coords = roomData.getPBox() != null ? roomData.getPBox() : pDrlgCoord;
            int coordsWidth = coords.getNWidth() - 1;
            int coordsHeight = coords.getNHeight() - 1;
            D2DrlgVertexStrc edgeStart;
            boolean vertical;
            int intervalStart;
            int intervalEnd;
            int edgeStartCoord;
            int edgeEndCoord;
            int sign;

            switch (roomData.getNDirection()) {
                case 0: // ALTDIR_WEST
                    edgeStart = bottomLeft;
                    vertical = true;
                    intervalStart = coords.getNPosY() + coordsHeight;
                    intervalEnd = coords.getNPosY();
                    edgeStartCoord = bottomLeft.getNPosY();
                    edgeEndCoord = topLeft.getNPosY();
                    sign = -1;
                    break;
                case 1: // ALTDIR_NORTH
                    edgeStart = topLeft;
                    vertical = false;
                    intervalStart = coords.getNPosX();
                    intervalEnd = coords.getNPosX() + coordsWidth;
                    edgeStartCoord = topLeft.getNPosX();
                    edgeEndCoord = topRight.getNPosX();
                    sign = 1;
                    break;
                case 2: // ALTDIR_EAST
                    edgeStart = topRight;
                    vertical = true;
                    intervalStart = coords.getNPosY();
                    intervalEnd = coords.getNPosY() + coordsHeight;
                    edgeStartCoord = topRight.getNPosY();
                    edgeEndCoord = bottomRight.getNPosY();
                    sign = 1;
                    break;
                case 3: // ALTDIR_SOUTH
                    edgeStart = bottomRight;
                    vertical = false;
                    intervalStart = coords.getNPosX() + coordsWidth;
                    intervalEnd = coords.getNPosX();
                    edgeStartCoord = bottomRight.getNPosX();
                    edgeEndCoord = bottomLeft.getNPosX();
                    sign = -1;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid alternate direction: "
                            + roomData.getNDirection());
            }

            D2DrlgVertexStrc marker = edgeStart;
            if (sign * intervalStart > sign * edgeStartCoord) {
                if (sign * intervalStart <= sign * edgeEndCoord) {
                    marker = insertVertexAfter(memPool, marker, direction, vertical, intervalStart);
                    markLevelLink(marker, roomData.isBPreset());
                    if (sign * intervalEnd < sign * edgeEndCoord) {
                        insertVertexAfter(memPool, marker, direction, vertical, intervalEnd);
                    }
                }
            } else if (sign * intervalEnd >= sign * edgeStartCoord) {
                markLevelLink(marker, roomData.isBPreset());
                if (sign * intervalEnd < sign * edgeEndCoord) {
                    insertVertexAfter(memPool, marker, direction, vertical, intervalEnd);
                }
            }
        }

        // The C++ routine returns coordinates local to the level; its caller
        // only converts them from tiles to 8x8 outdoor cells.
        D2DrlgVertexStrc vertex = ppVertices[0];
        do {
            vertex.setNPosX(vertex.getNPosX() - originX);
            vertex.setNPosY(vertex.getNPosY() - originY);
            vertex = vertex.getPNext();
        } while (vertex != ppVertices[0]);
    }

    private static D2DrlgVertexStrc insertVertexAfter(Object memPool, D2DrlgVertexStrc vertex,
            byte direction, boolean vertical, int coordinate) {
        D2DrlgVertexStrc inserted = allocVertex(memPool, direction);
        if (vertical) {
            inserted.setNPosX(vertex.getNPosX());
            inserted.setNPosY(coordinate);
        } else {
            inserted.setNPosX(coordinate);
            inserted.setNPosY(vertex.getNPosY());
        }
        inserted.setPNext(vertex.getPNext());
        vertex.setPNext(inserted);
        return inserted;
    }

    private static void markLevelLink(D2DrlgVertexStrc vertex, boolean preset) {
        vertex.setDwFlags(vertex.getDwFlags() | 1 | (preset ? 2 : 0));
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
