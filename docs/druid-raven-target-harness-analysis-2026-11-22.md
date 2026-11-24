# Raven 原版目标生成 harness 复用分析

> 当前已改用独立 `d2game_raven_target.cpp`，不再复用会很快死亡的 Fallen 当持久目标。最新有效证据、已修复差异和旧结论撤回见 [Raven 生命周期修复](druid-raven-lifecycle-fix-2026-11-22.md)。

## 结论

现有 `tools/original-observer/native-harness/d2game_fallen_death.cpp` 已经具备 Raven 目标测试所需的底层能力：

- 在当前 Blood Moor 房间读取碰撞网格并选择可用坐标；
- 通过固定 RVA 调用 D2Game 的单位生成函数，生成指定 `class_id` 的怪物；
- 校验单位所属 game、room、GUID、路径和位置；
- 通过固定模式调用驱动 A1，并逐帧记录模式、位置、动画和 AI 状态；
- 结束时移除本次生成单位并确认清理。

## 不能直接复用的部分

这个 harness 不是 Raven 当前使用的只读采集模式。它会：

1. `DebugActiveProcess` 附加原版进程；
2. 暂停游戏更新线程；
3. 改写远程线程栈和寄存器；
4. 调用 D2Game 内部函数；
5. 直接改变原版单位模式、生命值和 AI 调度。

因此它适合做“受控目标实验”，不适合直接并入 Raven 的只读动画采集器，也不能把生成后的状态当成自然 AI 证据。

## Raven 适配所需改动

建议新增独立 `d2game_raven_target.cpp`，不要修改 Fallen harness 的语义：

- 允许 `CXDruRaven` 专用存档和玩家类型；
- 只生成一个 class 19 或由场景表确认的 Fallen 目标；
- 输出 `target_id/class_id/room/position` 后恢复更新线程；
- 让 Python Raven collector 在目标存活期间只读轮询；
- 目标实验结束后由 harness 清理生成单位；
- 报告中明确标记 `debugger_attached=true`、`remote_execution=true`、`memory_writes=true`。

## 当前状态

已有只读采集已经证明 Raven 的创建、数量上限和客户端模式；本轮又完成了一次受控目标实验。下一步若要测 12+技能等级-1 的啄击预算，应继续把“生成目标”和“Raven 观察”拆成两个明确阶段，并分别记录自然证据与受控干预证据。

## 实际接入尝试

已编译并接入 `druid-raven-target-harness-private-desktop`：允许 `CXDruRaven` 通过现有 Fallen harness 的固定生成路径，并让 Python collector 与 native helper 并行运行。关闭 360 后重试成功，native helper 完成调试附加、生成 `class_id=19` 目标、自然运行 1040 帧并清理；Raven collector 在 16 秒窗口观察到 4 次模式 4（A1）进入。身份和采集样本见 `core/src/test/resources/original/druid-raven-target-1.10f.json`。
