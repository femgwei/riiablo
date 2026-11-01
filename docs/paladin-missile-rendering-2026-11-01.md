# 圣光弹、天堂之拳与祝福之锤绘制核对

## 本轮修正

`handofgod`（天堂之拳目标特效）和 `healing`（治疗特效）现在使用已经
核验的原版 Overlay 绘制策略：原 DCC、表中位置/高度和 PreDraw、原版动画
时钟以及 Trans=3 的加法混合。不改变伤害或服务器技能逻辑。

两行在本机 1.10f MPQ 中均为 AnimRate=16、Trans=3，因此帧间隔应为
16/(25*16)=0.04 秒；旧策略为 1/16 秒且使用 LUMINOSITY。
天堂之拳由 SkillCastHandler 的 SrvDo=80 分支读取 srvoverlay 并附着到目标。

原版通用 Overlay 路径证据复用
[光环绘制核验](paladin-aura-rendering-2026-11-01.md)：
`0x6FB5BA70` 创建，`0x6FB5BE13..1F` 初始化时钟，
`0x6FB57F63..75` 推进，`0x6FB57E2A..4D` 应用坐标及 Trans。
创建证据：`ev_a3c71c0f0f068cd4a098a5f761f58c4ea303196e5289f42d8bf2659968e4e0c6`。
这是通用 Overlay 路径，非光环专属绘制函数。

## 测试

PaladinMissilePresentationTest 两项通过：真实 MPQ 行和 DCC 存在性，
生产 OverlayManager 的时钟/混合/非持久生命周期，以及圣光弹、天堂之拳
散射弹、祝福之锤的 DCC、动画参数和现有原版 missile 混合映射。
测试未创建 GPU 纹理，不代表已做原版逐帧或逐像素对照。

## 尚未完成的分支

- 圣光弹 CltHit=9 的治疗触发条件尚未确认；本轮不按经验添加目标分支。
  本次 REA 读取 CltHit 表 `0x6FB913F4` 得到 `0x6FB36570`，下一项为
  `0x6FB366D0`（证据 `ev_e83b980cfdec04154f5bd78b0f5e1551c9b676874e05b18cc7b45b36d490e2bf`）。
  Ghidra 默认分析未恢复这两个函数，伪代码请求失败；不能把表指针当作行为证据。
- 祝福之锤当前使用连续浮点螺旋点。D2MOO
  `source/D2Common/src/Path/PathMisc.cpp` 的
  `PATH_ComputePathBlessedHammer_6FDAB3C0` 使用 FP16 原点、512 角度查表、
  每步距离增加 9600、整数网格量化并跳过重复点，直到填满路径。
  两者存在轨迹算法差异，不能声明原版一致；仍需核验 Fog #10083/#10084
  查表和路径推进后再替换，避免仅用另一种近似掩盖差异。
- 天堂之拳散射/命中时序尚未完成原版客户端分支的全面核对。

本轮 REA 会话导出到忽略文件
`.gradle/rea-paladin-missiles-20261101-evidence.json`。
