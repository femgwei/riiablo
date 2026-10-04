# dark-magic 技能视觉验收清单

更新时间：2026-10-05

## 当前状态

- 非视觉核对清单：**100%**。
- 技能移植严格总进度：约 **97%**；剩余项目必须在真实客户端渲染窗口中逐帧确认。
- dark-magic Assassin exact-ID 视觉验收：**0/10**；捕获/批量比较工具已就绪，但尚无可计入的双端真实窗口帧。
- 本清单只记录尚未被 ECS、真实双客户端实体 gate 或静态资源检查替代的画面验收，不会覆盖用户已验证的 Amazon 数值实现。
- `animationFallback=false`、共享实体和伤害结果只能证明权威行为，不等价于旧版客户端画面、关键帧时序和视觉持续时间一致。

## 当前素材审计

截至 2026-10-05，工作区中发现的相关 PNG 均不能作为双端视觉验收输入：

- `build/video-analysis/2026-10-04_21-29-33/contact-4fps.png`、`decay-8fps.png`、`impact-12fps.png` 是单套视频 contact sheet，没有原版/riiablo来源、技能 ID、地图 seed、技能等级或 MPQ 版本元数据。
- 根目录 `iceexplode-*` 与 `skill-viewer-window-*` 是 sprite/window 调试素材，不是同条件原版/riiablo帧对。
- `screenshots/` 下的项目截图没有对应的技能场景标识和另一端配对帧。

这些素材可以用于调试资源或动画，但当前统一排除在 `pass` 和 10 项视觉完成数之外；拿到另一台电脑的真实窗口捕获后，必须按捕获计划重新归档。

## 验收原则

1. 原版与 riiablo 使用同一 1.10f MPQ、同一地图 seed、同一技能等级、同一装备和同一目标布置。
2. 固定分辨率、缩放、帧率和录制速度；每个场景至少录制施法前 30 帧、技能全生命周期和结束后 30 帧。
3. 逐帧比较：施法起始帧、首个视觉对象出现帧、每次 pulse/波次间隔、碰撞/爆炸帧、消失帧、重连恢复帧。
4. 记录 owner 与 observer 两端；若只有 ECS/日志而没有客户端窗口截图，状态只能记为 `auto-only`。
5. 发现差异时先保存原版/riiablo截图和日志，再定位 COF/keyframe、Overlay、客户端 missile 行或渲染资源；不要直接改 Amazon 生产公式。

## 待验收场景

- [ ] **Wake of Fire Sentry (262)**：陷阱出现、两波方向、波次间隔、逐帧墙体停止、波次结束和陷阱消失。
- [ ] **Inferno Sentry (272)**：通道起始帧、持续时间、pulse cadence、方向追踪、墙前停止、目标后方不显示伤害视觉、到期回收。
- [ ] **Death Sentry (276)**：尸体爆炸起始/范围视觉、Skill2 闪电 fallback、墙体阻挡、重复尸体不复活、重连后的剩余视觉生命周期。
- [ ] **Blade Shield (277)**：前/后 Overlay 对齐、首次 pulse、`perdelay` cadence、最后 pulse、到期 fade、owner 离区/死亡后的清理。
- [ ] **Blade Fury (266)**：held-input 重入、每次 keyframe 发刃间隔、`bladefragment1` 与 helper missile 的资源/方向/碰撞视觉差异、墙体和重连。
- [ ] **Fire Trauma (251)**：air→ground→explosion 三段出现顺序、null-hit 后子对象、爆炸范围和一次性消失。
- [ ] **Shock Field (256)、Blade Sentinel (257)、Charged Bolt Sentry (261)**：放置视觉、目标锁定/导弹方向、墙体/null-hit、控制器到期和重连。
- [ ] **Lightning Sentry (271)**：放置视觉、目标锁定、闪电路径与命中时序、墙体阻挡、shot budget、到期和重连。
- [ ] **其他职业区域/状态技能**：仅在对应矩阵记录仍标记 `visual pending` 时执行；优先检查 Overlay/DCC 帧数、动画速率、首末关键帧和状态移除后的残留画面。

## 每个场景的记录格式

```text
skill=<id/name>
mpq=1.10f
seed=<map seed>
level=<skill level>
original_capture=<path>
riiablo_capture=<path>
start_frame=<n>
first_visual_frame=<n>/<n>
pulse_or_wave_frames=<original>/<riiablo>
collision_or_explosion_frame=<original>/<riiablo>
expiry_frame=<original>/<riiablo>
owner_observer_match=<yes/no>
reconnect_match=<yes/no/not-applicable>
result=<pass/fail/auto-only>
notes=<resource/keyframe difference>
```

## 自动差异报告

取得两套同条件 PNG 帧后，可先运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\compare-skill-visual-frames.ps1 `
  -OriginalDir .\captures\original\wake-of-fire `
  -RiiabloDir .\captures\riiablo\wake-of-fire `
  -OutputPath .\captures\reports\wake-of-fire.tsv
```

工具优先按文件名中的 `frame-12`、`f12` 等帧号配对；没有显式帧号时才退回目录排序索引。
报告帧缺失、尺寸不一致、像素变化比例，并在命令输出中给出帧数、帧率、估算时长和尺寸摘要。
可用 `-FrameRate 25` 覆盖默认帧率。`different` 只表示像素发生变化，不能单独证明技能语义、
伤害或关键帧正确；仍需按本清单逐帧记录首帧、pulse、碰撞、到期和重连结果。

10 个 exact-ID 的目录、优先级、场景和已核对的预期资源链见
[`dark-magic-visual-capture-plan.tsv`](dark-magic-visual-capture-plan.tsv)。将截图放入
`captures/dark-magic/original/<slug>` 与 `captures/dark-magic/riiablo/<slug>` 后，可批量生成报告：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\compare-skill-visual-capture-set.ps1
```

批量结果中的 `awaiting-human-review` 只表示两套帧可比较，不代表视觉验收通过。需要在另一台电脑上
强制检查所有捕获齐全时追加 `-RequireComparableCaptures`；任何缺帧、尺寸不一致或比较失败都会使命令失败。
批量工具默认还会校验计划是否完整包含 251、256、257、261、262、266、271、272、276、277，
以及每项的正数技能等级、非负地图 seed、正数帧率和非空 `expected_resources`；资源链也会写入汇总报告。
若要测试自定义子集，显式传入 `-ExpectedSkillIds`。
若要同时强制校验两端采集条件，在每个场景目录放置 `capture.json`（模板见
[`dark-magic-visual-capture-metadata.example.json`](dark-magic-visual-capture-metadata.example.json)），
并追加 `-RequireMetadata`。工具会拒绝来源、MPQ、技能等级、地图 seed、帧率、分辨率或缩放不一致的帧集。
也可以用初始化脚本按计划创建目录和元数据（不会覆盖已有 `capture.json`）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\new-skill-visual-capture.ps1 `
  -SkillId 262 -Source original-1.10f -MapSeed 1 -SkillLevel 20
powershell -NoProfile -ExecutionPolicy Bypass `
  -File .\tools\new-skill-visual-capture.ps1 `
  -SkillId 262 -Source riiablo -MapSeed 1 -SkillLevel 20
```

## 完成门槛

- `pass`：原版与 riiablo 的关键帧、持续时间、波次/pulse 间隔、墙体/null-hit 视觉和重连生命周期均一致。
- `fail`：任一关键帧偏移、视觉持续时间不一致、墙后出现错误视觉、到期残留或重连复活。
- `auto-only`：所有非视觉 gate 通过，但尚未取得真实窗口截图；不能计入最后 3%。
