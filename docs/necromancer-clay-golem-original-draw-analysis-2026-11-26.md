# 黏土石魔：原版出生、行走与待机绘制核对

本轮继续技能 75（Clay Golem），补充原版运行时动画证据。此前的
`necromancer-golem-birth-presentation-analysis-2026-11-25.md` 已修复四种石魔缺失的
`Sequence(S1 -> NU)`，本轮没有把它当成从未研究的技能，也没有重复修改该链路。

## 原版最小化采集

任务：`necromancer-clay-golem-d2gfx`。
Run：`20261126-133613-51cb099a`。
夹具：CXNecro，技能 75 / 20 级；复用原接收器，hidden 启动并进入场景，
采集时保持 minimized，通过目标窗口 PostMessageW 施法，无前台恢复或全局输入。
orchestrator 的亡灵巫师夹具增加技能 75 白名单；其他采集器限制保持原样。

原始绘制记录：

```text
tools/original-observer/broker/20261121-165133-4490d598/runs/20261126-133613-51cb099a/orchestrator/process-session/001-clay-golem-d2gfx-minimized/d2gfx-call-trace.jsonl
```

归一化：`tools/original-observer/necromancer-clay-golem-original-draw-2026-11-26.json`。
脚本：`tools/original-observer/normalize_d2gfx_necromancer_clay_golem_trace.py`。
测试夹具：`core/src/test/resources/original/necromancer-clay-golem-draw-1.10f.json`，
包含原始 trace 路径、SHA-256、实际观测阶段和限制。

原生 D2Gfx 采集器记录 5388 个事件，状态 passed，detached=true、cleanup_confirmed=true。
外层 process-session 与 broker 任务 failed，原因为 `Original exited`。
因此只记录局部有效的绘制证据，不把整个任务称为成功。

筛出 `G1` 黏土石魔类别（unit_index=289），共 112 次绘制，全部 window_state=minimized：

- S1：47 次，采到帧 4..13，1343..2312ms，屏幕位置保持 (368,256)。
- WL：28 次，采到帧 0..7，2359..3187ms，位置由 (366,256) 移到 (526,240)。
- NU：37 次，采到帧 0..2，3218..4000ms，位置保持 (526,240)。

这是该石魔类别先原地出生、再移动、最后待机的运行时证据；unit_index 是怪物类别而非唯一 GUID，
该 trace 本身不能独立证明所有绘制属于同一实例。单次施法、PetMax=1 的夹具与一个石魔的阶段衔接相容。
观察中没有单独的“特效弹体”来代替出生动作。
施法者 NE/SC 采到帧 1..14。旁路只读动画样本识别 NESCHTH，16 帧、速度 256、动作标记在帧 8。
未将该候选内存样本当成完整客户端结构证明。

这些时间含软件断点开销，不能据此推算原版每秒帧数或移动速度。S1 首尾 0..3、14..15
未被采到；NU 帧 3 未被采到。采样缺帧不等于原版不播放这些帧。

## 本机原版资源和 Riiablo 生产时钟

Skills.txt：SrvDo056、summon=ClayGolem、Summode=S1、pettype=golem、PetMax=1，
施法动作为 SC。该技能没有配置 castoverlay、cltmissile 或 sumoverlay；原版采样中的
出生主体是 G1/S1 的躯干资源，不能仅因没有额外弹体就判定特效缺失。

本机 MPQ 的原始 AnimData 与当前 eanimdata 相应条目一致：

- G1S1HTH：16 帧、动画速率 128；TR DCC 16 帧、1 方向。
- G1WLHTH：8 帧、动画速率 128；TR DCC 8 帧、8 方向。
- G1NUHTH：4 帧、动画速率 40；TR DCC 4 帧、8 方向。

相应 COF 的帧数与动画表一致，实际采到的原版帧都在资源范围内。
出生 DCC 为单方向，原始 draw-call 中的路径方向值不应直接当成 DCC 方向下标。

新增 `ClayGolemBirthPresentationTest`，通过真实 SkillDoEvent、ServerSkillSystem、
CofManager、AnimDataResolver、SequenceHandler、AnimStepper、AnimationStepper 和 NecroPet
验证出生序列。实体分配用受控工厂，素材直接从 MPQ 读取，不创建窗口、纹理或 GPU 绘制。
路径请求用桩隔离地图；只验证 AI 接管条件，不以它宣称真实寻路或移动速率一致。

后续鲜血石魔核对已将这个测试合并为 `NecromancerGolemBirthPresentationTest` 的黏土参数用例，
并将归一化实现共用至 `normalize_d2gfx_necromancer_golem_trace.py`；原脚本名保留兼容入口。
本页测试数量和归档是本轮当时的结果。

资源目录补记：后续鲜血石魔回归发现当时命令继承了另一套安装的 D2_HOME。
本页 39 项结果不作为“明确指定 1.10f 安装”的测试证据。鲜血石魔轮已固定
`-Pd2Home=G:/BaiduNetdiskDownload/Diablo II 1.10F` 重跑共享测试的黏土参数用例，
上述 G1 资源帧数、速率和 32 tick 出生结论再次通过。新证据见
`necromancer-blood-golem-validation-2026-11-26/validation.json`；本页原版采集来自指定 1.10f loader，不受此影响。

验证内容：

- 实际技能回调安装 S1 -> NU，出生阶段不带攻击 Casting，也不额外生成石魔。
- S1 保留期间 NecroPet 不发出跟随路径请求。
- Riiablo 客户端时钟能播放原版采样的 S1 帧，且播放到自身资源尾帧才离开出生阶段。
- 出生结束后允许 AI 跟随主人。
- 资源检查同时覆盖原版实际观察到的 WL 和 NU 帧。

这条受控链路的出生阶段为 32 个 25 Hz 模拟帧，客户端出现帧 0..15；
这来自 Riiablo 生产时钟验证，不能冒充原版实测时长。

本轮未确认需要新增的客户端动画修复；已有出生序列修复与这次原版观察的阶段顺序相容。
完整 GPU 绘制、像素、光照、影子、起步方向、准确运动速度、攻击/命中和死亡/替换仍未完成原版双端对比。

## 验证归档与范围

JDK 17，Gradle 既有缓存；core 和 server:d2gs 分开执行。
Core 5 个类共 32 项、D2GS 2 个类共 7 项，合计 39 项通过，0 失败、错误、跳过。
Core 同时回归复活、石魔唯一额度、召唤生命周期和 SequenceHandler。
最终测试类、数量、XML 和日志引用保存在
`tools/original-observer/necromancer-clay-golem-validation-2026-11-26/validation.json`。

runtime_status 保持 `observed_minimized_d2gfx_partial`；不因有界回归通过就升级为完整动画一致。
结束检查：broker ready、active/child_pid 为空、cleanup_blocked=false，未发现残留原版进程。
保留其他工作区修改、接收器 heartbeat、`.tools/` 和 `.gradle-user/`。未提交或推送。
