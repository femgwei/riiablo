# Raven 命中致盲与客户端特效顺序修复

> 后续：普通血量目标的硬/软受击已核对并修复，见 [受击门槛记录](druid-raven-hit-recovery-fix-2026-11-23.md)。

## 本轮结论

找到了两个缺失步骤和一个展示顺序差异，并已修复：

1. Riiablo 的 Raven 没有原版实际使用的 `item_stupidity=-1` 事件属性。
2. 普通怪物近战命中后没有执行对应的致盲回调，所以即使有伤害，也不会产生 Dim Vision 状态及其客户端特效。
3. 已有状态显示代码会同时安装 Dim Vision 的起始和持续特效；原版本轮观测为先 69、再 129。现在持续标记等待起始动画被 OverlayManager 正常回收后再安装，包括等待异步资源加载的阶段。

本次不是只补一张贴图。实际 ECS 回归贯通了召唤、AI 选目标、A1 攻击关键帧、成功命中、致盲状态、StateP 序列化、客户端特效及服务端到期后的特效清理。未命中不会产生伤害或触发致盲。

## 新的原版后台证据

运行 `20261123-085128-369b3f76`，技能进程内为 `[221,1]`。复用 broker PID 25512，在独立非输入桌面自动进入 Blood Moor；没有切桌面、前台激活或全局输入。

- Raven GUID 21：出生期 level=98、tohit=1057、基础 mindamage/maxdamage=0、`item_stupidity=-1`。旧 helper 将最后一项 DWORD 写成 4294967295；导出时按有符号 stat 归一化，helper 后续也改为有符号输出。
- 固定 class 0 骷髅 GUID 20：HP/maxHP=10000，hpregen 从 1250 改为 0，删除目标 AI think。只干预目标，不强制 Raven 模式、命中、伤害或状态。
- frame 724：成功啄击，损失 781 个 8.8 定点 HP 单位；本次命中前没有 Dim Vision，近战结算返回时目标拥有 state 23，技能 71、等级 8、owner type=1/GUID=21、expiry=1249，初始期限为 525 tick。
- frame 794：未命中，伤害为 0，已有致盲的等级和 expiry 不变。
- frame 864：成功命中，损失 695 定点单位，现有状态仍为等级 8、expiry=1249。本次未采集该回调的完整随机流，不能据此推导所有刷新/覆盖规则。

客户端共 503 个有效固定目标样本，无目标反馈读取拒绝。观测顺序是无状态/无特效，随后 state 23 位变为真且出现 overlay 69，再转为 overlay 129。第一次盲状态样本在 collector 的 elapsed_ms=9040；持续特效第一次样本为 9388。这些是轮询时刻，不是精确动画边界；不与服务端 frame 直接对齐。所试读的客户端计数器为 0，未作为时钟使用。

客户端接收到了状态位，但没有服务端那份完整诅咒 stat list。不能要求客户端也一定有技能等级/expire list 才算收到视觉状态。

原版服务端和客户端目标模式在此轮始终为 NU（1），未出现 GH。目标 maxHP 被放大到 10000，单次伤害很小；这只能确认该受控场景没有硬受击动画，不能推论普通低血量怪物也应如此。Riiablo 通用受击阈值/软受击策略需另行核对，本次未修改。

两侧时钟没有强行对齐。该实验没有 GPU 画面、像素或完整堆栈证据。原版调试器已恢复并 detach，目标已移除，自有游戏已关闭，broker 返回 ready。

## 实现

`ServerSkillSystem.spawnDruidSummon` 为原版 `druidhawk` 的 SrvDo114 补上签名明确的 `item_stupidity=-1` 基线。当前工厂尚未加载完整 MonProp 事件属性系统，因此这里是 stock 1.10 Raven 的定向补齐，不宣称支持所有 mod 的 MonProp 重定义。负值是有效事件值，不能用 `<=0` 把它过滤掉。

`NativeBlindTargetApplier` 参照 D2MOO 的 `SKILLITEM_EventFunc09_Stupidity` 和 `SKILLS_ApplyDimVisionToTarget`，使用攻击者原有随机流，按等级差和属性值计算命中后的致盲机会，再由随机余量确定 Dim Vision 等级。后续复用技能 71 的时长、难度除数和诅咒状态实现，记录 Raven 为来源并标记同步。独特/超级独特、不可切换 AI、不可攻击、死亡、不可打断及客户端快照目标不会被施加该状态。

`Actioneer` 在成功、未格挡的普通近战命中路径中调用该事件，位于无伤害提前退出分支之前。当前接线覆盖怪物近战，包括 Raven；没有在这一轮扩大到玩家装备或导弹事件路径，也没有重新实现整个物品事件调度器。

`StateOverlaySystem` 对 Dim Vision 延迟安装持续特效，等待 `OverlayManager.isOneShotActive` 返回 false。该查询检查实际持有的非持续效果，资源尚未加载完成时也保持等待，实体移除/效果退休复用现有资源回收逻辑。没有用固定毫秒定时器替代动画生命周期。其他诅咒/增益的展示策略保持原有规则。

本轮确认资源身份、先后阶段和清理链路；没有确认两端 DCC 每帧时钟、所有方向、空间位置和延迟严格相同。

## 证据和验证入口

- 精简原版证据：`core/src/test/resources/original/druid-raven-feedback-1.10f.json`。
- 导出器：`tools/original-observer/export_druid_raven_feedback.py`，验证身份、命中/未命中、HP 差、首次状态产生、客户端资源转换及清理状态，并保存原始数据和当轮 helper/collector 的 SHA-256。
- 原始证据所在：`tools/original-observer/broker/20261121-165133-4490d598/runs/20261123-085128-369b3f76/orchestrator/worker`。
- 新增/扩充回归：`NativeBlindTargetApplierTest`、`DruidSummonIntegrationTest`、`DruidRavenOriginalParityTest`、`StateOverlaySystemTest`、`AuraOverlayResourceTest`。
- 修改前回归明确失败在 Raven 事件属性期望 -1、实际 0，日志 `druid-raven-blind-before-fix-2026-11-23.log`；后续覆盖了实际命中、未命中、客户端 wire 和资源阶段。
- Python 采集离线回归 4 项通过，含客户端状态位与完整 stat list 的区别、expiry/技能等级提取。

最终 Java 定向回归 **60 项通过，0 失败/错误**：德鲁伊召唤 14、致盲回调 3、原版 Raven 证据 5、普通怪物近战 5、状态特效 13、真实 OverlayManager 资源生命周期 5、诅咒数据 4、诅咒 AI 7、Raven AI 4。最终日志 `tools/original-observer/druid-raven-feedback-sequence-validation-2026-11-23.log`。真实 OverlayManager 用例覆盖了资源尚未加载、起始动画活动中、动画退休后才出现持续标记三个阶段；使用模拟 DCC 资源，不需要启动前台窗口。C++ x86 helper 最终版本编译成功，Python 模块语法检查通过。

## 后续范围

无需重复采集“能否扣血/是否有致盲”。优先用真实普通血量目标核对硬 GH 与软受击条件，再处理 Raven 的盘旋、接近、攻击间隔和方向动画。高等级数量上限仍需纠正旧存档夹具后独立验证；不能把这一轮的单只等级 1 命中采集计为整个技能或德鲁伊职业全部完成。
