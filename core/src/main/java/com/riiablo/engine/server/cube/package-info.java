/**
 * 赫拉迪克方块系统 - 基于 D2MOD HoradricCube 模块移植
 * 
 * <p>管理赫拉迪克方块的合成配方和执行。
 * 
 * <p>主要组件：
 * <ul>
 *   <li>{@link com.riiablo.engine.server.cube.HoradricCube} - 方块管理器</li>
 *   <li>{@link com.riiablo.engine.server.cube.CubeRecipe} - 配方定义</li>
 *   <li>{@link com.riiablo.engine.server.cube.CubeRecipeType} - 配方类型</li>
 * </ul>
 * 
 * <p>支持的配方类型：
 * <ul>
 *   <li>宝石升级：碎裂->裂开->普通->无瑕->完美</li>
 *   <li>符文升级：低级符文合成高级符文</li>
 *   <li>药水合成：小药水合成大药水</li>
 *   <li>任务物品：牛关传送门等</li>
 *   <li>洗点Token：四种精华合成</li>
 * </ul>
 * 
 * <p>参考：D2MOD/source/D2Common/src/DataTbls/HoradricCube.cpp
 * 
 * @author riiablo team
 */
package com.riiablo.engine.server.cube;
