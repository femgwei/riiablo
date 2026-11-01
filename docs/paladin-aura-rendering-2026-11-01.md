# 光环绘制参数对齐 1.10f（2026-11-01）

## 范围

对齐 `States.txt aura=1` 的持久 Overlay 绘制。不改变光环属性、范围、伤害、
状态同步及多光环轮换；非 aura Overlay 保留原策略。

## 原版依据

原版 `D2Client.dll` SHA-256：
`94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282`。

- `0x6FB5BA70` 创建 Overlay，读取 X/Yoffset 和 Height1–4；
  `D2Common #10430 / UNITS_GetOverlayHeight`：玩家返回 1，怪物返回
  `MonStats2.OverlayHeight - 1`，所以玩家使用 Height2。
- `0x6FB5BE13..0x6FB5BE1F`：读取行 +0x6C 的 AnimRate，左移 4 位保存；
  `0x6FB57F63..0x6FB57F75`：每次更新累加；绘制帧右移 8 位。
  因而每帧持续时间为 `16 / (25 * AnimRate)` 秒，不是 `1 / AnimRate`。
- `0x6FB57D55` 按 PreDraw 分层；`0x6FB57E2A..0x6FB57E4D` 把保存的
  X/Yoffset 加到单位坐标，并将行 +0x7C 的 Trans 直接传给绘制接口。
  Java 的屏幕坐标 Y 向上，因此应用 `-Yoffset - selectedHeight`。
- `Overlay.Trans` 是 D2Gfx DrawMode，不是 Missiles.Trans。
  D2MOO `source/D2Gfx/include/DrawMode.h` 和现有原版 D2Glide 核验说明
  draw mode 3 使用源色 + 目标色（ONE, ONE）。复用已经存在的 ADDITIVE
  路径，调色板索引 0 被遮罩，不再用亮度生成半透明 alpha。

上述是静态二进制/源码证据，不是原版运行时截图或逐像素一致性证明。
REA 证据保留在本机忽略文件 `.gradle/rea-paladin-aura-20261101-evidence.json`。
其中创建函数伪代码 Evidence 为
`ev_a3c71c0f0f068cd4a098a5f761f58c4ea303196e5289f42d8bf2659968e4e0c6`；
反编译存在寄存器/参数恢复问题，字段及调用参数以原始指令为准。

## 实现

- OverlayManager 根据同步状态的 aura 标志选择 AuraRenderPolicy。
- 保留原 DCC 文件、DCC 帧边界偏移及 PreDraw 前后层级；不另画几何圆环。
- 光环使用原版速率和加法混合；位置加上表偏移与单位高度。
- 清理/复用 Overlay 时清除 nativeAura 标记，避免影响后续非光环效果。
- 实际 MPQ 中 Might 前/后层 Xoffset=4；Prayer/Defiance 等 AnimRate=8，
  对应 12.5 FPS；AnimRate=16 对应 25 FPS。检查到的 aura Overlay 均为 Trans=3。

## 验证

使用 JDK 17：

```powershell
.\gradlew.bat :core:auraRenderRegression '-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F' --no-daemon
```

专用 source set 避开尚在迁移的旧 UI 测试。覆盖速率、坐标/高度、混合参数，
真实 MPQ 状态及 DCC 存在性，以及原有状态 Overlay 生命周期；另由真实
OverlayManager + mock DCC/AssetManager 验证生产加载路径，测试不创建 GPU 纹理。
本机 JDK 17 实测 3 类共 13 项，失败/错误/跳过均为 0；生产代码编译和模块边界检查通过。

仍需另一台电脑实机确认：力量环与脚底对齐，祈祷/反抗播放速度，信念前后层，
明暗地面上的颜色叠加。没有运行原版与 riiablo 的逐帧截图对照，不能声称
所有调色板、后端及光照条件下已通过逐像素验收。
