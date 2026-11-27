# 火焰石魔：出生与神圣火焰双层光环核对

本轮处理 FireGolem，技能 94，继续补原版动画证据。此前的石魔共用出生序列和
Fire Golem 的 Holy Fire 授予/伤害脉冲已有实现，不重新把它们当成缺失功能。

本轮先评估了 IronGolem（90）。SrvDo057 必须以地面上已鉴定的金属物品为目标，
当前后台工具只有地面点施法和尸体夹具，没有已验证的金属物品投放/选中夹具。
没有将普通地面点击、预载石魔或 Riiablo 单测冒充原版钢铁石魔召唤。
钢铁石魔保持未采集，本轮改为可安全复用既有夹具的火焰石魔。

## 原版最小化绘制采集

任务 `necromancer-fire-golem-d2gfx`；run `20261126-140334-894a85a2`。
CXNecro，技能 94 / 20 级，隐藏启动和进入场景，采集时 minimized，
使用目标 HWND 的 PostMessageW。没有前台恢复、抢焦点、OS 光标移动或全局输入。

原始记录：

```text
tools/original-observer/broker/20261121-165133-4490d598/runs/20261126-140334-894a85a2/orchestrator/process-session/001-fire-golem-d2gfx-minimized/d2gfx-call-trace.jsonl
```

归一化：`tools/original-observer/necromancer-fire-golem-original-draw-2026-11-26.json`。
共用脚本：`tools/original-observer/normalize_d2gfx_necromancer_golem_trace.py --skill 94`。
测试夹具：`core/src/test/resources/original/necromancer-fire-golem-draw-1.10f.json`。

原生采集器 passed，5363 events，detached=true、cleanup_confirmed=true。
外层仍为 failed / Original exited；退出后快照读取产生 WinError 299。
仅使用已经完整保存的绘制调用作为局部证据，不宣称整个任务通过。

FireGolem 的实际资源 token 是 **G3**，monster class 292，不按技能顺序猜 G4。
筛出 106 次 G3 主体绘制，全部 window_state=minimized：

- S1：29 次，采到 4、6..15 帧，1437..2219ms，屏幕位置 (368,256) 不变。
- NU：6 次，帧 0..1，2250..2375ms。
- WL：36 次，帧 0..7，2453..3781ms，由 (366,256) 移到 (224,168)。
- NU：24 次，帧 0..5，3844..4390ms。
- WL：11 次，帧 0..2，4437..4672ms。

unit_index 是类别而非 GUID；单次施法、PetMax=1 的夹具与一只石魔的阶段衔接相容，
trace 本身不能独立证明唯一实例。断点耗时不是动画帧率或运动速度测量。
主体未采到 S1 0..3、5 和 NU 6..7，不把采样缺帧理解为原版缺帧。

施法者 NE/SC 绘制采到 1,2,3,5,6,7,8,10,12,14。
上一轮修复的 expected SC=10 判定在本次新采集中生效：旁路 mode_ids=[1,10]，
client_animation_skill_mode_observed=true。仍不把非原子的候选内存样本当成完整结构证明。

## 持续光环：表格 12 帧，实际 DCC 19 帧

同时采到 type=6 的 overlay 15 和 16，各 53 次，共 106 次，全部 minimized。
两层都覆盖帧 0..18，1437..4640ms，随单位位置变化；不只在召唤瞬间出现。

实际 1.10f Overlay 表身份与资源核对：

- aura_holyfire_back / AuraHolyFireBack.dcc。
- aura_holyfire_front / AuraHolyFireFront.dcc。

Overlay.txt 标称 Frames=12，实际两份 DCC 均为 19 帧、单方向，原版绘制也使用全部 19 帧。
前后层的 PreDraw 不同，不能用单个覆盖层替代。AnimRate=16、Trans=3，现有原版策略
对应 25 Hz 循环和 additive 混合。Riiablo 从 DCC 获取帧数，没有按表格裁成 12 帧。

资源 SHA-256（与既有本机库存一致）：

- Front: bb4b79e3c43a775dcbb73d7d19310a411801ad142745826aa95b6ae5a155f597。
- Back: 68855c99daa6f17811756921061586b97bbaad7b6821aa815bd078611dd93e13。

Skills.txt 的 sumskill1=holy fire，sumsk1calc=min(ln56,30)，Param5=8、Param6=1。
因此 20 级火焰石魔获得 27 级 Holy Fire，perdelay=50 原生帧。
授予状态、伤害脉冲与前后持续光环是不同环节；不以伤害测试代替可见光环核对。

## 出生资源与无窗口生产链路

原始 AnimData 和 eanimdata 的 G3 记录相同：

- G3S1HTH：16 帧、速率 168；TR DCC 16 帧、1 方向。
- G3WLHTH：8 帧、速率 192；TR DCC 8 帧、8 方向。
- G3NUHTH：8 帧、速率 112；TR DCC 8 帧、8 方向。

扩展 `NecromancerGolemBirthPresentationTest` 的火焰参数用例，真实技能回调进入
ServerSkillSystem、SequenceHandler、AnimStepper、AnimationStepper 和 NecroPet，
验证 S1 结束前不请求跟随、播放到末帧 15、返回 NU 后才允许跟随；
本地出生 25 个 25 Hz 模拟帧，并确认 27 级 Holy Fire 和 50 帧脉冲间隔。
该时长是 Riiablo 生产时钟结果，不冒充原版实测时长。

扩展 `AuraOverlayResourceTest`：真实 StateOverlaySystem / OverlayManager，从权威状态安装
前后双层，检查实际 DCC 长度覆盖原版采样帧、原版混合/速率策略、循环模式；
重复更新不重新开始动画、不加第三层；状态移除时两层都释放。
使用受控 AssetManager/DCC，实体和路径分配也有夹具，不创建窗口、纹理或 GPU。

本轮未确认需要新增的游戏动画修复，保留现有按资源播放的实现，增加原版证据和定向回归。
对 shader 最终输出、叠加颜色、光照/影子、精确轨迹/速度、攻击、命中和死亡/爆炸，
仍需独立原版双端采集，不能由本轮测试推出一致。

## 验证和清理

JDK 17，显式 D2_HOME 与 -Pd2Home 固定到
`G:/BaiduNetdiskDownload/Diablo II 1.10F`，既有 Gradle 缓存。
core 和 server:d2gs 分开运行。
core 27 项、server:d2gs 7 项、Python 采集判定 17 项，共 51 项通过，
没有失败、错误或跳过。D2GS 两个 suite 的本次 XML 已独立归档，避免后续测试覆盖。
最终 XML、命令、准确数量、源文件散列与日志引用：
`tools/original-observer/necromancer-fire-golem-validation-2026-11-26/validation.json`。

收尾检查：broker=ready，active=null、child_pid=null、cleanup_blocked=false；
未发现原版 Game/Diablo II/D2Loader 进程或 1.10F 安装目录下运行的可执行程序。
`git diff --check` 通过。

runtime_status 保持 observed_minimized_d2gfx_partial。
保留所有无关修改、`.tools/`、`.gradle-user/` 和接收器 heartbeat；未提交或推送。
