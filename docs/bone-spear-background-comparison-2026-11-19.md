# 骨矛原版后台比较：飞行、尾迹与动画选择

本轮研究亡灵巫师骨矛的客户端展示，定位到一个影响飞行距离与尾迹间距的速度换算错误并完成修复。原版采集、Riiablo 回放均在后台完成。本轮只覆盖一级、无协同、沿世界坐标 +X 方向的一次施法，尚未完成多目标穿透、伤害和命中特效比较。

## 原版采集与可追溯证据

原版为 `G:\BaiduNetdiskDownload\Diablo II 1.10F`，使用该目录的 `D2v110各版本免CD全功能启动.exe -w -res800`。

批次目录：`tools/original-observer/broker/20261116-014209-3a0c898c/runs/20261119-173202-8642f303/orchestrator/`。

- 独立桌面 `riiablo_spear_6bbefcc1c9b4`；80 次检查均未成为输入桌面，`switched_desktop=false`。前后输入桌面为 Default，分辨率为 1920×1080，前台 HWND 前后均为 1050482。没有调用 SwitchDesktop、前台激活或全局输入函数。
- 测试角色 CXSpear，class 2，骨矛 skill 84、level 1，其余技能为 0，固定地图种子 127897359。角色位于鲜血荒地，经私人桌面窗口定向 PostMessage 施法，保留原版正常客户端 CltDo 和服务器调度。
- 采集服务端帧 470～570，40 条服务端状态、305 条客户端状态、1,923 条渲染探针事件。305 次骨矛及尾迹绘制提交均正常返回，其中 303 次同时观察到实际 DCC 选择。
- 恢复断点、脱离调试和清理均已确认，本批独占游戏已经关闭。没有重复请求 UAC，复用了已授权的常驻 broker。

原始 `worker/capture/missiles.jsonl` 与 `sprite-submissions.jsonl` 分别保存两端单位状态和提交/返回事件；`worker/provenance/` 冻结了本批执行程序、采集器与调度器源码。D2Client 普通导弹提交路径及 D2CMP RVA `0xBFE7` 的被动探针沿用已验证的冰封球流程，没有强制调用绘制函数。

`export_bone_spear.py` 导出 `core/src/test/resources/original/bone-spear-1.10f.json`：

- 状态日志 SHA-256：`1fa65e246662820bfdc991581b53b7f211a7d367a975f277ff824a7455d6e38c`。
- 绘制日志 SHA-256：`a6ef48a7f4c900ca8627512be8e99d67a81485442a1e0f8fa218bde1051cb1bc`。
- 每条绘图记录按 unit_id、game_frame 与客户端状态精确关联，并核对动画游标。两次没有命中实际选择探针的提交保留在原始日志中，未纳入方向/帧 oracle；不能把其默认字段视为真实方向。

## 实测飞行与展示行为

真实 1.10f MPQ 的导弹 ID 为 192 bonespear、216 bonespearexplode、248 bonespeartrail。不要混用工程中概念型 `MissileId.BONE_SPEAR=36`。

本次服务端只有一支 192，客户端有一支 192 和 38 个 248。服务端骨矛帧 479 首次出现、518 最后出现，共 40 个状态；客户端首次出现为帧 480。两端调度异步，首次客户端采样之后存在一次推进两步的情况，不能仅按服务端帧号假设客户端每次只更新一步。

骨矛的原生路径速度为 fp16 `(73728, 0)`，即每 tick 1.125 subtile、每秒 28.125 subtile。表的 Vel=24 不是每秒 24 格。40 帧寿命对应原版计数 total_frames=40、current_frame 从 40 递减到 1；字段名 current_frame 不代表年龄，本文与 fixture 使用 `total_frames-current_frame` 计算已运行帧数。

实际绘制选择为：

- 骨矛主体：BoneSpear DCC，16 方向、每方向 6 帧；本次实际方向 3，动画帧 0～5 循环。
- 尾迹：BoneSpearTrail DCC，32 方向、每方向 9 帧；本次实际方向 3。虽然速度为 0，尾迹仍保留父导弹路径朝向，不能由零速度推导朝向。
- 38 个尾迹均静止，沿 +X 排列，间距为 fp16 73728，即 1.125 格。total_frames=8，每个采到 7 个状态，剩余计数与动画帧分别覆盖 7～1 和 1～7。采样没有覆盖出生瞬间/第 0 帧，不代表这些帧不存在。
- bonespear 的 CltDo=18、CltParam1=3；该参数用于 nLoops，不能据此解释为每 3 帧生成一次尾迹。现有每客户端 tick 创建尾迹的流程与观察到的连续间距相符，但尚未逐指令验证出生回调的绝对时序。

本批未观察到 216，但没有布置受控目标或墙体碰撞，因此不能据此删除命中特效或认定原版不会产生它。

## 已定位并修复的差异

修改前，Riiablo 的通用工厂把表 Vel=24 直接作为每秒世界速度，25 Hz 下得到每 tick 0.96 格，约比本次原版慢 14.67%。寿命相同时会导致飞行距离偏短、尾迹间距偏小。

先将原版状态接入真实生产工厂和移动系统，`BoneSpearOriginalMotionTest` 在修改前明确失败：期望 fp16 速度 73728，实际为 62915。日志为 `tools/original-observer/bone-spear-baseline.log`。

修复仅限定表名 bonespear：

1. 新增 `NativeBoneSpearMotion`，按原生速度尺度和方向量化生成 25 Hz 的 fp16 速度，复用已验证的原生方向工具。
2. `ServerEntityFactory` 为骨矛初始化原生出生格心和精确位置，使用新速度换算，并让朝向对应量化后的速度。
3. `MissileCollisionSystem` 将骨矛纳入现有 fp16 逐 tick 移动路径，避免浮点累积偏移。

没有修改骨魂、其他职业导弹、骨矛伤害、穿透规则或寿命。动画资源/方向/播放时钟在本次采样回放中通过，因此未额外修改其客户端动画实现。

## 回归验证及其含义

`tools/original-observer/bone-spear-fixed-validation.log`：JDK 17、真实 1.10f MPQ、headless，七组共 9 项测试通过，0 失败、0 跳过。

- `BoneSpearOriginalMotionTest`：真实 ServerEntityFactory + MissileCollisionSystem，对照全部 40 条原版服务端状态，核对速度、精确坐标和剩余寿命，检查随后到期删除。
- `BoneSpearOriginalSpriteTest`：303 条真实选择，经生产 DirectionResolver 和 MissileLoader.initializePlayback，对照 DCC 方向、帧、资源维度和实际帧可用性，覆盖主体及全部 38 个尾迹单位。使用真实 DCC 解码，禁用 GPU 纹理上传。
- `NativeNecromancerBoneProjectileDataTest`、`NecromancerClientPresentationTest`、`NecromancerClientLifecycleTest`：现有骨矛/骨魂数据与客户端生命周期回归；包括尾迹为客户端专用、静止、寿命 8 帧的检查。
- `FrozenOrbOriginalMotionTest`、`FrozenOrbOriginalSpriteTest`：共享移动和表现路径的冰封球回归。

方向测试向生产表现组件提供本次施法朝向，帧测试提供原版年龄；这证明给定相同朝向与年龄时的资源选择和时钟计算一致，不等于已完整重放 Riiablo 客户端输入、出生、加载与绘制的端到端时序。38 是本次原版观察值，不是对 Riiablo 每次施法固定产生 38 段的断言。没有进行 GPU 像素比较。

## 后续优先验证

下一步应在同一后台流程中布置两个共线的受控目标，分别记录服务器命中/生命变化、骨矛是否继续飞行、客户端碰撞回调和 216 特效。这样可继续定位穿透与命中展示缺失，而不必推进像素对齐。

本轮未验证的范围还包括其他方向、技能等级和施法速度、Slow Missiles 效果、协同伤害、出生首帧的精确相位及撞墙消失。这些不能由当前一次无目标飞行采样推断为已与原版一致。

## 补充：矛头套在尾迹内的出生显示时序

用户指出 Riiablo 的矛头与尾迹前后位置过近，看起来像矛头套在尾迹内。重新分析同批原版日志，无需再次启动游戏，得到以下关系：

- 主体仍存在时共有 238 组同帧主体/尾迹位置。全部满足 `head.x_fixed - trail.x_fixed = head.velocity_x * trail_age`，其中 `trail_age=total_frames-current_frame`，年龄覆盖 1～7。
- 37 个同时存在主体和尾迹的采样时刻，最近可见尾迹与矛头始终相差 1.125 格。原始绘制记录连同未命中 DCC 选择的首次尾迹提交，也没有尾迹年龄 0 的绘制。
- 例如服务端帧 482：客户端主体 raw x=296607744，最近尾迹 raw x=296534016，差 73728，即一 tick 的位移。尾迹年龄为 1、动画帧为 1。该差值是两者的锚点距离，不表示两个素材的可见像素必须完全分离。

Riiablo 的本地生产顺序是 MissileCollisionSystem 推进主体，随后 MissileImpactPresentationSystem 在主体当前坐标创建静止尾迹，再由 MissileLoader 和 AnimationStepper 加载、推进并交给绘制订阅。旧实现允许刚创建、nativeFrame=0 的尾迹当帧安装 AnimationWrapper/BBoxWrapper；缓存已加载时 ClientEntityFactory.createMissilePresentation 的 loadNow 也能立即安装它。此时主体与最新可见尾迹锚点相同，确实会产生用户描述的重叠。

此前的 BoneSpearOriginalSpriteTest 只向表现函数输入原版年龄、核对选择结果，没有运行移动到加载的完整顺序，因此未覆盖这一错误。新增 BoneSpearOriginalTrailTest，按生产顺序组合真实工厂、移动、尾迹回调、加载器和动画推进器，使用真实 DCC，仅替换资源就绪状态和禁用 GPU 上传。修改前复现两个明确失败：

1. `newborn trail must not be drawn at the head anchor`：年龄 0 的尾迹已拥有绘制组件。
2. `head clock expected 1 but was 2`：主体已运行一帧，首次加载按年龄跳到第 1 帧后，同 tick 的 AnimationStepper 又推进到第 2 帧。

失败日志为 `tools/original-observer/bone-spear-trail-before.log`。本次限定骨矛的修正为：

- MissileLoader 暂不为年龄 0 的 bonespeartrail 安装绘制组件，包括缓存已就绪的 loadNow 路径；下一次客户端更新后才加载，不人为后移坐标。
- MissileImpactPresentationSystem 让 bonespeartrail 的年龄和 8 帧寿命不依赖 DCC 就绪，否则等待第 1 帧显示会与等待资源加载形成死锁，冷加载也可能延长尾迹。
- AnimationStepper 对本地权威骨矛和骨矛尾迹，按已推进的 nativeFrame 选择播放位置，避免加载后重复推进。该主体时钟修正未扩展到网络副本，网络插值与快照时序仍需独立验证。

新增回归检查最近可见尾迹始终落后一个 tick、各年龄尾迹的锚点距离与原版关系一致，以及资源已缓存、延迟 5 tick 就绪、一直未就绪三种情况下的动画/到期。未进行屏幕观感或 GPU 像素复核；这些证据定位并约束的是造成重叠的客户端时序错误。

修复后 `tools/original-observer/bone-spear-trail-fixed.log` 为 BUILD SUCCESSFUL，十组共 23 项测试全部通过，0 失败、0 跳过；包括新增完整尾迹流程 2 项、前述骨矛/冰封球回归、MissileAnimationCatchupTest 6 项和 FingerMageTrailLifecycleTest 6 项。测试在 headless 环境运行，没有启动或激活前台游戏。
