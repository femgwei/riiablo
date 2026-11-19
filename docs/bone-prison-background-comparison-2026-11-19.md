# 骨牢原版后台比较：单位状态与骨头素材

目标是核对亡灵巫师骨牢的客户端展示：骨头素材、排列、朝向和消失过程。本文的“怪物单位”仅指引擎内部数据类型，不表示画面应该显示普通怪物。骨头可以被攻击，原版把每段骨头登记为 type 1 单位，素材仍是骨头。

## 证据与边界

原版批次：`tools/original-observer/broker/20261116-014209-3a0c898c/runs/20261119-164730-e51b4876/orchestrator/`。

- 独立且未显示的 Windows 桌面，125 次检查均未成为输入桌面；输入桌面前后均为 Default，分辨率前后均为 1920×1080。前台 HWND 前后不同，因此不宣称前台应用前后相同；程序没有调用 SwitchDesktop 或前台激活函数。
- 独立裸装 `CXPrison`，亡灵巫师，骨牢 1 级，无协同。固定一个新建骷髅目标并停用它的 AI 事件，在原版正常服务端游戏线程通过目标模式进入施法，再由原有调度器执行。
- 连续 681 个服务端 tick，两端各 8,076 条状态，均观察到 12 个新骨牢段。采集器已恢复断点、脱离，测试游戏已关闭。
- `worker/provenance/` 保留该批工具源码与程序；`worker/capture/units.jsonl` 是原始状态。`summarize_bone_prison.py` 导出 `core/src/test/resources/original/bone-prison-states-1.10f.json`，保留源 SHA-256、位置偏移、模式转换与死亡动画游标。

**首批仅采集状态，不是实际绘图提交。** 单位游标不能证明 DCC 的最终选择、绘制层、遮挡、混合或像素。服务端目标模式也没有执行本地客户端技能输入流程，因此不能用本批数据否认客户端另有生成特效。后续实际绘制采集见下节。

## 已确认的原版行为

12 段均为 class 344，对应表项 `bonewall`、模型 token `BW`，不是 `boneprison1..4`（后者属于另一种骨牢机制）。按路径高 16 位取整数格，位置相对目标为：

```text
(-1,-4), (1,-4), (3,-3), (4,-1), (4,1), (3,3),
(-1,4), (1,4), (-3,3), (-4,-1), (-4,1), (-3,-3)
```

路径低字为 0x8000，表示格心；不应把固定坐标直接除 65536 后的额外 0.5 当作偏移错误。

本次所有段在服务端帧 460 首次出现，初始模式 NU；1070 转 DT，1085 转 DD。即本批首次出现后 610 tick 开始死亡、死亡动画持续 15 tick。骨头尸体在采集结束 1132 帧仍在，未测最终清除时间。不能将“尸体仍在”认作“骨牢仍存活”。

真实 MPQ 的 `BW` COF：NU 4 方向/1 帧，DT 4 方向/15 帧，DD 4 方向/1 帧。客户端状态的模式帧数和这些资源一致。`BonePrisonOriginalStateTest` 检查资源和捕获状态，不能被称为画面一致性测试。

## 与 Riiablo 代码的差异

1. **错误的外观类型轮换，已修复。** 修改前 `ServerSkillSystem.spawnNecromancerBonePrison` 用 `summon.hcIdx + index % 4` 选类型，得到 344～347。真实表的 345、346、347 是 `councilmember1/2/3`，模型 `HP`（议会成员），不是骨头变体；NU 有 7 帧/8 方向。现已让所有段使用技能表指定的同一 `bonewall/BW` 行，与原版本批一致。错误类型还会使只识别 `bonewall` 的骨墙碰撞逻辑漏掉这些段。
2. **服务端朝向初始化与客户端方向必须分开。** 原版服务端 12 段的路径方向各异，指向中心；修改前 Riiablo 的 `createBonePrisonSegment` 接收 `facing` 参数却没有设置 `Angle`。现已初始化当前朝向与目标朝向。后续绘制采样确认 NU/DT 的 DCC 方向均为 0，因此 `DirectionResolver` 对技能 88、有效 token BW、NU/DT 模式立即选择方向 0，保留服务端朝向。不能把服务端指向中心直接转换为骨头素材方向；骨墙技能、其他召唤物和未采集的 DD 模式不应用这条覆盖规则。
3. **到期时间/过程需核对。** Riiablo `SummonedPetSystem` 按生成后 600 帧直接进入通用召唤物过期处理；原版本批 610 帧后进入 DT，再经过 15 帧进入 DD。D2MOO 的 BoneWall AI 初始化绝对期限，并每 15 tick 检查严格小于当前帧的条件，能解释期限与实际死亡帧不相同，但不能仅凭该源码假设本次初始化事件的具体调度。
4. **控制关系的适用范围不同。** 原版 SrvDo062 建立首段与其余段的关系；Riiablo 把骨牢 `pet.boneWall=false`，现有控制段死亡清理分支只处理 `boneWall=true`。这一代码差异需要单独击杀首段验证，当前没有实测伤害或联动清理。

几何偏移与请求数量一致。原有 `NecromancerBoneWallIntegrationTest` 只检查段数，并未检查每段的资源类型，因此没有暴露议会成员误生成的问题。现已增强该测试：对照原版 fixture 检查全部 12 个位置，每段必须是 class 344/token BW，并检查当前与目标朝向均指向中心。

## 修复验证

`bone-prison-art-fixed-validation.log` 记录本次四组测试共 9 项，全部通过、无跳过：`NecromancerBoneWallIntegrationTest` 3 项、`BonePrisonOriginalStateTest` 2 项、`NativeNecromancerBoneWallDataTest` 3 项、`DiabPrisonDataTest` 1 项。测试在 JDK 17 和真实 1.10f MPQ 上运行。

本次修改了外观类型选择、服务端朝向初始化和采样覆盖模式的客户端方向。尚未修改 600 帧到期或首段死亡规则，也不据此宣称整项技能已经与原版一致。

## 实际骨头图层后台采集

后续批次 `20261119-171434-f31bd09a`，根目录同样位于常驻 broker 的 `runs/` 下。121 次检查均未成为输入桌面，前后输入桌面为 Default、分辨率为 1920×1080；已恢复断点、脱离调试并关闭本批独占的游戏。

在原有调度器继续运行期间，仅观察以下调用，不强制调用绘制函数：

- D2Client RVA `0xBADED`：普通分层单位提交到 D2Gfx #10072；ESI 关联客户端单位，栈参数关联该层 cell context。
- D2CMP RVA `0xBFE7`：关联同一 cell context 的实际 DCC 方向选择，读取方向数与帧数。
- D2Client RVA `0xBADF2`：提交调用已返回。只有提交、选择、返回三者索引匹配的记录才导出 fixture。

本批捕获 708 次提交、708 次 DCC 选择、708 次正常返回，覆盖全部 12 段：

- NU：528 次，实际资源 `BW/TR/BWTRlitNUhth.dcc`，4 方向、1 帧，选择方向 0、帧 0。
- DT：180 次，实际资源 `BW/TR/BWTRlitDThth.dcc`，4 方向、15 帧；全部 12 段均覆盖帧 0～14，选择方向均为 0。

`bone-layer-submissions.jsonl` 保留 cell context 原始字、单位、帧、图层与三类事件；`summarize_bone_prison_layers.py` 导出 `core/src/test/resources/original/bone-prison-layers-1.10f.json`，保留原始日志 SHA-256。运行时原程序与工具源码冻结在各批 `worker/provenance/`，后续工具改动不改变历史证据。

`BonePrisonOriginalLayerTest` 将这些实际选择作为独立方向 oracle，对真实 MPQ 解码后的 COF/DCC 核对资源、方向数、帧数及帧可用性，并运行生产 `DirectionResolver`，检查从其他方向立即恢复为原版方向且不改变服务端 Angle。测试未重放完整死亡生命周期，因此不把资源中存在 15 帧称为 Riiablo 已按原版时序完成死亡。

新增图层测试后，`bone-prison-layer-validation.log` 记录六组共 12 项测试全部通过、无跳过（前述 9 项，另加图层测试 2 项、Frozen Orb 原版方向回归 1 项），构建耗时 1 分 35 秒。全程 headless，没有前台游戏窗口。

边界：只观察普通分层提交路径，不能据此认定 S1 或其他分支绝无绘制；本批没有采到 DD 的对应提交，也没有执行本地 CltDo。骨头出生的额外特效、其他绘制分支、尸体绘制和完整到期流程仍需后续确认。当前结论已从单位状态推进到真实 TR 层资源/方向选择，但不是完整画面一致性声明。
