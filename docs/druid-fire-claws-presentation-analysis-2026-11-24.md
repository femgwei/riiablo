# 焰爪（Fire Claws，239）：补齐命中目标的技能闪光

本轮接在狂犬病之后，检查狼人、熊人攻击动作和命中特效。全程离线读表、读资源和无窗口测试；未启动原版或发送全局输入。

## 问题与依据

本机 1.10F 的有效 Skills 行指定 `SrvSt58`、`SrvDo002`、`anim=A1`、`srvoverlay=fire_hit`，允许 wolf/bear 两种形态，没有独立的技能可见弹体或 castoverlay。

公开重建源码 D2MOO 的 `SkillDruid.cpp:SKILLS_SrvSt58_FireClaws` 准备命中记录和附加火焰伤害；`Skills.cpp:SKILLS_SrvDo002_Kick_PowerStrike_MonIceSpear_Impale_Bash_Stun_Concentrate_BearSmite_Vengeance_Berserk_FireClaws` 从已准备的目标 combat 记录读取 `SUCCESSFULHIT`，再调用 `UNITS_SetOverlay(target, SrvOverlay, 0)`。

`SUnitDmg.cpp:SUNITDMG_GetResultFlags` 的 block/dodge 等分支清除 `SUCCESSFULHIT`。因此这层闪光依据成功命中，不能由施放事件无条件触发，也不能以最终扣血大于零作为条件。

修复前 Riiablo 的 SrvSt58 准备/关键帧结算已存在，但没有消费该技能的 `srvoverlay`。本地通用火元素命中表现使用 `fire_explode`，它与额外的 `fire_hit` 不是同一项，不能全局替换。

这里使用的是本机 MPQ 数据与公开重建源码，不是官方原始源码或本轮原版运行时调用记录。

## 修改

1. 在 `Actioneer.resolveFireClaws` 消费预计算 combat 时，根据成功命中、未格挡和目标存活，发送一次 `UnitOverlayEvent`。它在本次伤害结算前发出，致死命中也能产生闪光；未命中、格挡、已死目标以及重复关键帧不产生新闪光。
2. 从 `NativeSkills` 读取 `srvoverlay`。项目的精简 `Skills.Entry` 不包含这个字段，没有为 Fire Claws 硬编码资源替代原表。
3. 新增末尾追加的 `UnitOverlayP` 联合包，不改变已有 packet 类型编号。D2GS 按被击中目标的可见范围发送；客户端映射服务器目标 ID，再通过既有事件阶段交给 OverlayManager。未知目标或空效果被丢弃，不创建虚假单位。
4. OverlayManager 消费独立命中特效事件，不制造第二次伤害。`fire_hit` 采用已有原版 Overlay 时钟与绘制策略：AnimRate=16 对应 25 FPS，Trans=3 对应 additive，保留表中高度/偏移与前后绘制设置。
5. 同一目标再次命中时重启该短闪光，保留独立的状态图层；播完或目标删除后释放资源，不新增永久状态。

这是当前 `server:d2gs` 与 Riiablo 客户端的协议扩展，两端需要使用同一版构建。没有扩展旧的 `server:netty` 示例服务器，也没有把这个独立事件当成所有伤害、所有技能的通用改造。

## 实际资源与动画

有效 Overlay 行 `fire_hit` 的 Filename=FireHit、Frames=8、AnimRate=16、Trans=3、PreDraw=false，四种高度和 X/Y 偏移均为零。

**真实 MPQ 中的 `data/global/overlays/FireHit.dcc` 是 12 帧、单方向，与表中的 Frames=8 不同。** 首轮测试按表误期望 8 帧，直接读 DCC 头后发现差异。最终夹具使用真实资源帧数 12；并未修改资源或把表头当作实际文件头。当前 Riiablo 一次性 Overlay 仍按载入资源的帧数完成播放，原版是否以表中 Frames 截断及精确消失相位尚未运行时验证。

`FireClawsAnimationDataTest` 通过生产 DruidShapeShiftResolver 和 AnimDataResolver 检查逻辑玩家 A1 转换到 monster A1；分别解析狼人 `40A1HTH` 与熊人 `TGA1HTH` 的有效 AnimData、COF 和所有组成层 DCC 头。验证动作不是空白回退，帧数匹配，并包含单个攻击关键帧。具体帧数、速度及资源路径保存在测试 XML 的输出中。

实际读取结果：狼人 A1 为 13 帧、8 方向，AnimData speed=256；熊人 A1 为 12 帧、16 方向，speed=224。这里的 speed 是基础 AnimData 值，不代表含装备、变身技能加速后的最终攻击周期。

## 验证与证据

`DruidFeralMaulTest` 扩展真实 SrvSt58 到攻击关键帧路径，覆盖两形态成功命中、未命中、格挡、零伤害成功命中、致死命中、已死目标以及重复关键帧。已有伤害计算与变身限制测试同时回归。

`FireClawsPresentationTest` 使用真实 Artemis/OverlayManager/OverlayStepper，检查事件到图层、25 FPS/additive、真实 DCC 头、一次性清理、连续命中刷新、共存状态以及待加载时删除。AssetManager 和绘制用 DCC 对象为 mock；真实资源头另外从 MPQ 读取，未执行 GPU 绘制。

`UnitOverlayPacketTest` 验证远端目标 ID 不同于本地 ID 时的映射、事件阶段交接与未知目标丢弃；`UnitOverlayTransportTest` 验证 D2GS 打包与跨关卡过滤。未运行真实 socket 双端游戏会话，队列和包/接收器分段集成测试不代表完整线上场景已验收。

逐项结果、日志、资源表摘录及源码 SHA256 汇总于 `tools/original-observer/fire-claws-presentation-validation-2026-11-24.json`。首轮字段编译失败日志、首轮资源帧数断言失败的 XML 均保留。最终通过数量以该验证 JSON 为准。

另一次组合命令误将 `--tests` 只应用到最后一个 Gradle 任务，core 因而跑了未筛选测试，遇到缺失 `.d2i` 素材等无关失败后中断。保留 `fire-claws-final-validation-2026-11-24.log`，没有将目录里先前遗留的 XML 冒充该次完整结果。随后分别运行 core 与 server:d2gs 的定向测试。

最终 JDK 17 定向验证共 **40 项通过，0 失败、0 错误、0 跳过**：core 38 项，server:d2gs 2 项。包括零伤害成功命中、致死命中和已死目标的补充用例。职业清单只更新有界静态/无窗口验证进度。

## 边界与后续

- 未新增原版后台采样，职业清单的 runtime_status 仍为 `not_run_in_class_batch`。
- 本轮定位并补齐了明确缺失的目标技能覆盖层，不宣称焰爪全方向、全装备攻击速度、所有音效或全部伤害细节与原版完全一致。
- 尚未验证技能闪光在网络死亡/删除快照的先后顺序、目标跨区域切换及新进入可见范围时的出生帧。
- 尚未验证调色后的 GPU 画面、动态光照、实际遮挡和原版精确播放长度；InitRadius/Radius/RGB 不等于本轮已实现了原版动态光源。
- 完成当前有界修复后继续未核对技能，不为像素对齐反复延长这一项。

下一项为饥饿（Hunger，242）；240 Twister 与 241 Vines 已有各自的前期证据，不重复从头处理。
