# Codex 任务交接基线

更新时间：2026-09-15  
仓库：`F:/3rd_src/riiablo`  
分支：`master`

## 事实基线

- 当前 HEAD：`c297c6ecf1f0f542cfa05d5e7c3dc668d6e81712`
- `origin/master` 已指向同一 commit；远程验证命令：
  `git ls-remote origin refs/heads/master`
- 工作区没有已跟踪文件修改；存在大量历史未跟踪 `.log` 测试产物，必须保留，
  不要删除、重命名或加入提交。
- 当前 Chat 负责全部模块：地图、战斗、技能、物品、任务、NPC、网络、存档和 UI；
  不再假设存在独立战斗 Chat。

## 最近已提交工作

- `c297c6ec`：更新攻击动画缓存提交记录。
- `dd2b9af4`：缓存本地玩家攻击动画资源帧。
- `99d8757b`、`bbbd02c2`：修复连续攻击模式切换造成的动画闪烁。
- `8f0d8c33`、`84e052cf`：Automap 进度记录，以及 Options、城镇揭示、缩放和实体标记。

## 已确认的行为

### Automap

- CENTER 是全屏地图；TOP_LEFT/TOP_RIGHT 是半尺寸小地图，内容比例也为全屏的 1/2。
- 城镇进入时全 Zone 揭示；野外和地下区域按 RoomEx 探索揭示。
- 地形、玩家、队友和 NPC 使用统一 `AutomapProjection.worldToAutomap` 坐标投影。
- 地形和有数据的对象/怪物优先使用 `MaxiMap.dc6` 原生 cell；玩家、队友及没有
  原生 cell 的 NPC 使用几何回退，不应把回退图形误认为 DC6 图标。
- 原生 DC6 绘制失败时必须保留几何回退，避免实体在地图上消失。

### 攻击动画

- `ModeChangeEvent.restart` 区分同模式重启动画和真正 COF 切换。
- 同模式重启保留已加载 DCC，仅重置帧；本地玩家常用模式切换也尽量复用已加载 DCC。
- 服务端攻击关键帧、伤害和导弹时序未在动画修复中改变。

## 已通过的验证

- Automap 专项测试：
  `./gradlew.bat :core:test --tests 'com.riiablo.engine.client.automap.*' --no-daemon`
- 序列动画测试：
  `./gradlew.bat :core:test --tests 'com.riiablo.engine.server.SequenceHandlerTest' --no-daemon`
- 真实 1.10f 隐藏 Automap：
  `./gradlew.bat :desktop:offscreenAutomapDc6 -Pd2Home="G:\\BaiduNetdiskDownload\\Diablo II 1.10F" --no-daemon`
  结果：`terrain=600 entities=58 fallback=1 visiblePixels=17910`，`OFFSCREEN_CAMP PASS`。
- 远程 commit 已用 `git ls-remote` 验证，尽管 Git credential storage 曾报告锁文件存在。

## 新 Chat 开始时必须执行

```text
git status --short --branch
git log -5 --oneline --decorate
git rev-parse HEAD
git ls-remote origin refs/heads/master
```

然后阅读本文件、`docs/current-chat-ownership.md`、`docs/d2moo-progress-roadmap.md` 和
`docs/d2moo-minimal-port-priority.md`。如果聊天摘要与 Git 或文档冲突，以 Git 和文档为准。

## 下一步建议

1. 先做 Automap 三种模式的离屏像素/包围盒比例回归，确认玩家、队友、NPC marker 与
   DC6 地形坐标重合。
2. 做连续普通攻击、Throw/标枪和攻击结束回 NU 的离屏动画回归；若首次模式切换仍闪烁，
   仅预加载本地玩家常用 NU/WL/RN/A1/A2/TH/SC 资源，不扩大全量怪物预加载。
3. 完成后更新本文件和两份进度文档，运行定向测试，提交并推送；不要重复已通过的历史门槛。

## 变更范围声明

本次交接只新增/更新文档，不修改地图、战斗、技能、网络或生成网络文件。
