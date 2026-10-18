# 2026-10-19 治疗者音效与卷轴书容量核对

## 治疗者音效

D2MOO `D2GAME_NPC_HealPlayer_6FCCB080` 在恢复资源或清除状态后调用
`SUNIT_AttachSound(pNpc, 10, 0)`。这里的 10 是事件编号，并非 Sounds.txt 索引。

直接检查本机原版 1.10f `D2Client.dll`：

- 0x2C 包处理函数 `0x6FAB39D0` 将包内事件编号交给 `0x6FB51920`。
- 事件 10 经 `0x6FB51EA4` 的映射字节和 `0x6FB51E60` 的跳转表进入 `0x6FB5197A`。
- 该分支以 `ECX=0xA71`（2673）调用音效播放函数 `0x6FB55820`。
- 原版 Sounds.txt 对应 `shrine_refill`，文件为 `object\shrine\refill.wav`。

`ClientNpc` 已将误用的 `cursor_convert_ready` 改为 `shrine_refill`。
此次只修正音效选择；现有每次打开治疗者对话就播放的触发策略继续保留，原版的
“实际恢复后才播放”条件仍需后续权威治疗事件接线。

## 鉴定书与回城书

本机原版 1.10f MPQ 的 Misc.txt 中，`ibk` 与 `tbk` 均为
`minstack=1, maxstack=20, spawnstack=5`。5 表示生成时已有的卷轴数量，最大容量为 20。
D2MOO `ITEMS_GetTotalMaxStack` 使用 `maxstack + item_extra_stack`（上限 511），
不是 `spawnstack`。riiablo 现有 `ItemData.maxStack` 与此一致，无需改变容量或初始数量。

## 验证

- 读取本机 1.10f MPQ 的 Misc.txt、Sounds.txt，核对上述字段和音效映射。
- 使用本机现有构建，通过 JShell 调用实际 `ItemGenerator`、`CharData` 和
  `ItemData.mergeCursorIntoStack`：两种书初始均为 5，逐张补充至 20 成功；第 21 张
  拒绝合并，卷轴保留在光标，数量不丢失。
- 本次代码仅替换音效键并更新注释；按用户要求未运行编译。原版治疗音效的实际听觉效果
  以及用户反馈的容量操作场景，待另一台电脑复测。
