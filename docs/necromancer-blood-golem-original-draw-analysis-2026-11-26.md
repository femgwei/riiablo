# 鲜血石魔：原版出生、待机和行走动画核对

继续技能 85（BloodGolem）。此前四种石魔的 S1 -> NU 出生序列已在 2026-11-25 修复，
本轮补原版动画证据，并将黏土/鲜血石魔验证合并为参数化生产链路测试。
没有因为清单缺少原版记录就将它当作此前没有实现的技能。

## 原版采集与实际观察

任务 `necromancer-blood-golem-d2gfx`，run `20261126-134950-874b1584`。
CXNecro，鲜血石魔技能 85 / 20 级，hidden 启动和进入场景，采集时 minimized，
使用既有目标窗口 PostMessageW 施法，不恢复前台、不移动 OS 光标、不发送全局输入。

原始 trace：

```text
tools/original-observer/broker/20261121-165133-4490d598/runs/20261126-134950-874b1584/orchestrator/process-session/001-blood-golem-d2gfx-minimized/d2gfx-call-trace.jsonl
```

归一化：`tools/original-observer/necromancer-blood-golem-original-draw-2026-11-26.json`。
共用脚本：`tools/original-observer/normalize_d2gfx_necromancer_golem_trace.py --skill 85`。
测试夹具：`core/src/test/resources/original/necromancer-blood-golem-draw-1.10f.json`，
保存 trace 路径、SHA-256、实际观测帧和证据边界。

原生采集器 passed，5362 events，detached=true、cleanup_confirmed=true。
外层 process-session/broker failed：进程退出；action 在退出后读取快照时报 WinError 299。
不将有效局部采样改写为整个任务成功，保留原始结果文件。

筛出 `G2` / monster class 290 的 131 次绘制，全部 window_state=minimized：

- S1：29 次，帧 5..15，1500..2360ms，位置固定 (368,256)。
- NU：4 次，帧 0，2407..2485ms，位置在 (366,256)..(368,256)。
- WL：46 次，帧 0..7，2563..3516ms，位置由 (356,254) 到 (134,232)。
- NU：52 次，帧 0..3，3563..4719ms，位置在 (126,232)..(128,232)。

原版样本明确包含出生末帧 15，以及出生后的短暂待机，再行走、再待机。
小幅屏幕位置变化包含相机/子格绘制因素，不能全部解释为单位世界位移。
unit_index 是怪物类别而非唯一 GUID，单次施法/PetMax=1 的夹具与一个石魔的阶段衔接相容，
trace 本身没有唯一实例标识。软件断点耗时不能换算成真实移动速率或动画帧率。

施法者 NE/SC 实际绘制采到 1,3,5,6,7,9,11,13 帧；旁路只读样本的 mode_id 包含 1、10。
出生首帧 0..4、攻击、命中/吸血可见反馈、死亡和清理没有被这次原版样本覆盖。

## 修正采集报告对施法动作的误判

process-session 过去无条件使用 `mode_id == 18`（SQ，原本用于旋风）判断是否观察到技能动作。
亡灵巫师本轮及此前白名单技能使用 SC=10，因此旧报告里 false 不等于未施法。
本轮增加 `expected_capture_skill_mode`，对已审核的亡灵巫师采集技能选择 SC=10，
保留其他既有 SQ 采集行为，新增 expected_mode_id 让之后报告可审查。

Python 回归覆盖亡灵巫师技能白名单与旋风边界；原始鲜血石魔报告仍保留旧 false，
归一化额外以实际 NE/SC draw 记录证明施法动作被采到。未重写既有 run 的结果或错误状态。

## 资源和 Riiablo 生产时钟

Skills.txt：SrvDo056、summon=BloodGolem、Summode=S1、施法动作 SC；
monanim=xx 是怪物使用该技能的列，不能替代新召唤石魔的 Summode=S1。
没有配置独立 castoverlay、cltmissile、sumoverlay，出生外观来自 G2 的躯干 DCC。

原始 AnimData 与 eanimdata 对应记录一致：

- G2S1HTH：16 帧、速率 160，躯干 DCC 16 帧/1 方向。
- G2WLHTH：8 帧、速率 184，躯干 DCC 8 帧/8 方向。
- G2NUHTH：4 帧、速率 56，躯干 DCC 4 帧/8 方向。

鲜血石魔与黏土石魔虽然同为 16 帧出生资源，但原始速率分别为 160 与 128，
不能给两者套用同一个固定出生时长。当前生产动画表驱动的实现能保留这种差别。

`NecromancerGolemBirthPresentationTest` 从真实技能回调进入 ServerSkillSystem、
CofManager、AnimDataResolver、SequenceHandler、AnimStepper、AnimationStepper，
并运行真实 NecroPet。实体创建和路径请求用受控夹具，不创建 GPU 或窗口。
验证原版采到的 S1 帧均可播放，保留末帧 15，S1 结束前无跟随请求，之后允许跟随。
鲜血石魔本地出生为 26 个 25 Hz 模拟帧；黏土为 32。该计数来自 Riiablo，不冒充原版实测。

本轮未确认新的客户端动画缺陷；既有出生修复保留，增加两种石魔共享的证据回归，
避免复制两份同构测试与归一化实现。

## 资源目录纠正与验证

初次未固定目录的 core 回归沿用环境 D2_HOME=`G:/BaiduNetdiskDownload/Diablo II`。
其中动画用例通过，但原始鲜血石魔伤害分担 Param5 检查读到 0，预期 1.10f 为 10。
这暴露了安装目录混用；没有据此修改游戏伤害数值或把断言改成 0。

最终 core 与 D2GS 必须显式使用
`-Pd2Home=G:/BaiduNetdiskDownload/Diablo II 1.10F`，并记录相同 D2_HOME，JDK 17，
core / server:d2gs 分开执行。最终逐项 XML、日志、准确测试数量与命令保存于
`tools/original-observer/necromancer-blood-golem-validation-2026-11-26/validation.json`。
最终 core 37 项、D2GS 7 项、Python 17 项，合计 61 项通过，0 失败、错误、跳过。
`git diff --check` 通过。结束时 broker ready、active/child_pid 为空、cleanup_blocked=false，
未发现残留原版进程。
本轮同时重新验证黏土参数用例；前轮未固定目录的回归不当作已指定 1.10f 安装的证据。

生命联动、召唤额度、尸体保留只是 Riiablo 无窗口回归；未声明已完成原版攻击/死亡动画对比。
runtime_status 保持 `observed_minimized_d2gfx_partial`。
尚未覆盖最终 GPU 输出、光照/影子、精确运动轨迹、攻击命中和死亡清理。

保留无关修改、`.tools/`、`.gradle-user/` 与接收器 heartbeat；未提交或推送。
