# 野性狂暴 / 撞槌：蓄力光球的客户端显示

本轮处理 Feral Rage（232、状态 120）和 Maul（233、状态 117）的蓄力光球。二者共享原版客户端 `States.setfunc=16`、`remfunc=11`，适合一起修正。本轮未启动原版进程、切换前台或发送全局输入。

## 确认的问题

Riiablo 原来的通用状态覆盖层逻辑把 `overlay1..4` 当成四个同时存在的图层。这里实际是两组数字索引范围的端点：前景最小/最大、背景最小/最大。因此原实现同时叠放最小与最大尺寸，并且不会根据蓄力选择中间尺寸。

此外，这些覆盖层之前使用通用 `1/AnimRate` 时钟与 luminosity 混合；原版的覆盖层时钟按 `AnimRate << 4` 推进八位小数帧，`AnimRate=16` 对应 25 FPS，`Trans=3` 对应 additive。此次改为使用项目已有的原版覆盖层绘制策略，包含帧速、混合和高度偏移。

## 本机 1.10F 二进制依据

目标为 `G:/BaiduNetdiskDownload/Diablo II 1.10F/D2Client.dll`，SHA-256：

`94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282`

使用现有 PE/Capstone 工具离线读取文件。REA MCP 未注册且 native backend 访问失败，本轮没有更改配置；以下是离线二进制证据，不是 REA 实时执行证据。

- ImageBase：`0x6FAA0000`。
- Set 分发表 `0x6FB93EE0`，索引 16 指向 `0x6FB4CDA0`（RVA `0xACDA0`）。
- Remove 分发表 `0x6FB93F60`，索引 11 指向 `0x6FB4CF50`。
- Set 函数读取状态属性 169（`skill_frenzy`）、350（`modifierlist_skill`）、351（`modifierlist_level`），并求值技能 Calc2。
- 两组端点按 Overlay 表的数字索引计算包含两端的范围。

令 `F=frontLast-frontFirst+1`，`B=backLast-backFirst+1`，则：

```text
maximum = min(Calc2(modifierlist_level), F)
charges = min(skill_frenzy, F)
frontOffset = (charges - 1) * F / maximum
backOffset  = (charges - 1) * B / maximum
```

除法为整数除法，后景使用与前景相同的分母。原版资源前、后各五档；技能一级最大蓄力为 3 时，显示档位依次为 **1、2、4**，不能改成线性插值的 1、3、5。最大蓄力大于五时，显示大小最多到第五档。

原函数移除未选中的档位，已有的选中覆盖层继续播放；移除函数清理前、后两组范围。原始指令、分发表引用与解释保存在 `tools/original-observer/feral-maul-static-1.10f-2026-11-23.json`，可用同目录的 `analyze_feral_maul_static.py` 重现，脚本要求 DLL 哈希一致。

## 实现

- 新增 `NativeFeralMaulPresentation`，从状态的技能 ID、技能等级、蓄力值选择前后两个覆盖层。
- `StateOverlaySystem` 在通用状态显示之前处理这两个状态，避免把端点全部挂上。
- `OverlayManager.reconcilePersistent` 只替换该状态未选中的图层，保留同档位动画对象和资源引用。
- `Overlay.usesNativeRendering` 为两种状态启用已有原版覆盖层绘制策略。前后层仍通过 `PreDraw` 在角色绘制前后分别绘制。
- 保留已有服务端命中、伤害、吸血、眩晕、蓄力公式；本轮没有改动它们。

## 验证与边界

`FeralMaulPresentationTest` 的六项测试在真实 Artemis World 中运行覆盖层管理、状态消费和帧推进：一级档位、五档切换、同档刷新不重启动画、过期/移除、无效技能与零蓄力，以及权威 StateSerializer 快照在远端角色重建光球。DCC 解码/AssetManager 在该夹具中使用 mock，不能据此宣称实际 GPU 画面已对齐。

`PresentationAssetLifecycleTest.selectedPairReconciliationReleasesOldSizeAndRetainsOtherOwners` 使用真实 AssetManager，分别测试资源尚未加载和已经加载时切换选中层，检查旧档位释放、同档位对象保留、其他状态共享引用保留，以及清除/退出后的引用归零。其余相关 Overlay 生命周期与技能数值、命中、状态同步测试一并回归。

扩大执行整个资源生命周期测试类时，发现八项地面闪光、任务标记与区域提示测试夹具没有注入 `ClientGameplayContext`，均在初始化时空指针失败；这些不是本轮光球修正通过的证据。原始失败 XML 保留在 `feral-maul-test-results-2026-11-23/broad`。随后聚焦运行技能、状态覆盖层与相关资源生命周期方法，最终数量和源码哈希见 `tools/original-observer/feral-maul-presentation-validation-2026-11-23.json`。

尚未新增原版运行时光球创建/清理采样，未验证生产窗口中的实际球体轨迹、角色遮挡或 GPU 输出。本轮属于有二进制依据的客户端实现修正与无窗口回归，职业清单保留原版运行时待采集状态，不标记完整视觉一致。
