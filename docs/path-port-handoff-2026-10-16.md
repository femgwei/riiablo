# D2MOO 寻路迁移续聊交接（2026-10-16）

用户要求保存进度、归档旧聊天并在新聊天继续。本文是新聊天的起点；无需重做第二十批。

## 当前基线

- 工作目录 `F:\3rd_src\riiablo`，PowerShell，分支 master；多人聊天共享同一工作目录。
  交接时 HEAD 为 `c80a934cb`，近期 NPC/Minion 提交属于其他聊天，不回退、不混入专项修改。
  每次开始、提交前重新检查 status 和暂存区，保留其他聊天的未提交修改。
- 第二十批代码 `bd2fdc2dd5ddbf45845a2e633904036914244831`，文档
  `09b9faeb405bbbba6f1cdd9b0b88c672a776fcf6`，均已推送 origin/master。
- 第二十批完成 type16 随机 IDA、live owner RNG 和专用 cutoff。Search 支持
  0/1/2/3/5/6/7/13/15/16；0/16 需房间投影，16 另需真实共享 owner seed。
- 最终寻路/碰撞回归 453 项、39 类，失败/错误/跳过 0；包含六项匹配原 DLL 的静态数据 oracle。
  独立递归 300 组随机查询/路径/最终 seed 对照包含在一项 JUnit 中，不是 DLL 动态执行。
  日志 `core/build/round20-full-final.log`；XML
  `.gradle/path-port/round20-full/core/test-results/pathPortTest`。
- 普通生产仍 `Map → AStarPathFinder`，原生有状态请求链未安装到 ECS 实际移动消费。
  不能宣称寻路完成、完整原生等价或性能优于现有实现。

## 用户已确认的继续方向

下一轮为第二十一批，优先 **type8/11 服务端/客户端击退入口**：

1. 阅读 `D2PathTypes`、`D2PathDispatch`、现有击退 helper/测试，以及进度文档最新章节。
2. 对照 D2MOO `Path` 源码和本机 1.10f D2Common.dll；表地址 type8=`0x6FDAB270`，
   type11=`0x6FDAB240`。先核对实际控制流、状态字段及调用链，不凭名称推测。
3. 在支持域明确的前提下实现、补相关回归，保持其他已优化寻路/碰撞/房间/步进行为。
4. 只提交本轮文件，推送 origin/master 并核验；同步交接和进度记录。

尚未实现第二十一批代码，不要把工作包列表当成已经完成的任务。

## 剩余范围：7 个工作包，不是固定 7 轮

1. type8/11：服务端和客户端击退入口。
2. type9/12：跳跃、后退转向入口。
3. 矩形碰撞网格、跨房间递归与边界核对。
4. 真实实体身份、单位元数据、房间图、共享 RNG 的生产映射。
5. 生产搜索分派及移动消费：nDist、索引推进、耗尽续算、类型恢复。
6. 真实地图综合验收：门、窄道、跨房间、动态单位阻挡、技能移动。
7. 性能对照、受控切换、用户另一台电脑实机验证。

前三包偏移植收尾，后四包偏生产接入/验收。此计数是规划粒度，不是精确工期或完成百分比。
完整飞弹专用请求链另算；type16 表名 MISSILE_STREAM 不代表本轮接通了飞弹 dispatcher。

## 不变的用户要求与证据源

- 代码改完须 git commit 并 push，用户在另一台电脑验证；极小改动可不编译，算法变化跑相关回归。
- 暗黑机制先查 D2MOO；不明确再分析原 DLL，必要时搜 The Phrozen Keep，不能只凭经验。
- D2MOO 仓库 `F:\3rd_src\D2MOO`，前几轮固定读取
  `origin/master=0c0bbd1e6bef9181a3da1c046523af622a925416`，未 fetch；若更新基线须明确记录，
  不 reset 其他工作。源文件优先 `git show origin/master:source/D2Common/src/Path/...`。
- 原版目录 `G:\BaiduNetdiskDownload\Diablo II 1.10F`，含 D2Common.dll/D2Client.dll 和 exe。
  D2Common SHA256=`B9D1A27C6A35992932C7C47438D02E5269F878E899F6E614D507902CECD1BEAB`。
  objdump=`C:\Strawberry\c\bin\objdump.exe`；静态反汇编/表核对不等于动态函数差分。
- 保持近期寻路、碰撞、房间、步进优化；不加 1.13+ target-room gate，不将 ordinary exact 自动转 partial。
- 原生 IDA：跨 contour 保留 cache，unsigned cached>=new 含相等；child 方向从 parent 当前方向起算；
  type16 每次 advance（含耗尽前未使用推进）都 roll live seed，root 设置不 roll，失败/异常不回滚。
  type0 不消费 seed；保留通用 findPath API 旧语义。安全域拒绝 padding>50000/回绕/cache越界，
  不模拟 DLL 内存破坏。生产不应在未经验收时整体切换。
- 文件编辑用 apply_patch；不要主动创建子代理。共享进度文件存在别人的修改时按 hunk 提交，
  不用 `git add .`，不清理其他聊天的暂存或未跟踪文件。

## 复验命令

```powershell
$env:D2_HOME = 'G:\BaiduNetdiskDownload\Diablo II 1.10F'
$env:D2_TEST = $env:D2_HOME
.\gradlew.bat -I docs/path-port-regression.init.gradle :core:pathPortTest -PpathRun=round21-full -PpathTestScope=path --console=plain
```

pathRun 必须使用新标签避免与其他聊天构建输出冲突；pathTestScope 只隔离测试源码，主源码仍完整编译。
先运行相关定向测试，再全范围回归；按 XML 统计实际 tests/failures/errors/skipped，不沿用旧数字冒充新结果。
推送输出需脱敏潜在 URL 凭证：

```powershell
git push origin master 2>&1 | ForEach-Object { $_.ToString() -replace 'https://[^/@\s]+@', 'https://[redacted]@' }
```

详细历史见 `docs/current-chat-ownership.md`、`docs/d2moo-upstream-port-2026-10-15.md`、
`docs/path-collision-upstream-comparison-2026-10-15.md` 和 `docs/d2moo-progress-roadmap.md`。
current-chat-ownership 顶部 dark-magic 技能进度是另一工作流，不是寻路完成度。
