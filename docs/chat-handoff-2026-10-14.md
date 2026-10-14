# 地图修复与巡游交接（2026-10-14）

## 工作约束

- 仓库 F:\3rd_src\riiablo；PowerShell；分支 master。每次代码修改完成需提交并推送 origin master，供另一台电脑实测；极小修改不必编译。
- 当前环境不做真机视觉验收，只能自动测试；不能将资源加载/自动巡游 PASS 当作显示和全部游戏逻辑正确。
- 原版逻辑优先参考 F:\3rd_src\D2MOO；不明确再查 The Phrozen Keep，必要时分析 G:\BaiduNetdiskDownload\Diablo II 1.10F 的原版 EXE/DLL。真实资源环境变量 D2_HOME 指向该目录。
- 五固定种子 1、2、3、12345、54321，加问题种子 127897359；正式 25Hz，100Hz 仅诊断。连续城镇出口必须真实 TCP 步行，不能 Warp、位置包绕路、删怪或关碰撞。用户允许真实 TCP 战斗清障。
- 严格日志审计：不能隐藏/降低警告或宽泛 allowlist 取得 PASS。保留失败日志及版本，不混合修复前后证据。

## 当前提交及工作区

- 669184cc：移除 A5 出城时玩家旁边的错误申克生成路径；已推送。
- 92812e5a：隐藏原生地板碰撞辅助图形；已推送。
- e752701f：A3 丛林无符号位移修复，扩展 A1–A5 资源审计；已推送。
- 0b1690e3 是另一 chat 的 A2/A4/A5 原生 Automap 位图修改，已在当前 master；并非本 chat 的修改。不要误当作未提交残留。
- 创建交接前 git status --short 为空。

## 本 chat 修复与证据

1. A3 DRLG_GenerateJungles 的预设尺寸警告：DrlgOutPlace.sub_6FD83970 错将 C++ unsigned nTemp 的 >>31 译为 Java 有符号右移，使分支多偏移两格。四处改为 >>>31，补 Act3JungleCoordinateParityTest。六种子 792 个地图实例真实 MPQ/DT1 审计通过，failedResolve=0，无 Invalid style 或丛林尺寸 WARNING。
2. A1 资源审计以前遗漏：MapResourceAuditTest 现在包含 A1–A5。A1 城镇/地下预设没有 outdoor nativeTileGrid 时，另用 Act1D2MOOLayoutBridge 原生 DS1 导出并安装审计，不跳过。
3. A5 青色网格：碰撞辅助 floor 的 MAPTILE_HIDDEN 在 TileGrid 渲染安装中丢失，collision.dt1 被绘制。TileGrid 保留两层 floorNativeFlags，D2MooTileApplier 写入，Act1MapBuilderD2MOD.applyTileGridLayers 过滤隐藏地板，保留 bRevealHidden=0x200 的洞穴图形和原生碰撞。依据 D2MOO DrlgRoomTile.cpp::DRLGROOMTILE_InitTileDataFlags。55 项定向、184 项地图回归、6 项资源审计方法通过。无 GL 截图验收。
4. A5 出城立即出现不可攻击督军：game2.log 中 entity=87 是任务代码在 player+3 生成的申克，实际归入城镇 level109（位置 2.360703,95.27321），城镇战斗规则拒绝伤害。Act5QuestSystem 已取消补生成，仅 trackNativeShenkIfPresent；由真实 DS1/RoomEx 激活生成申克。依据 D2MOO A5Q1.cpp::ACT5Q1_OnSiegeBossActivated。真实资源确认唯一 superUnique42 预设在 level110、不在城镇；Act5ShenkPlacementTest 确认反复出城不额外生成 Boss。最新 38 项 A5 任务及资源回归通过。

## 用户真机反馈与待确认

- C:\Users\DELL\Desktop\game.log、p3.png：A5 碰撞辅助网格异常，对应 92812e5a。
- C:\Users\DELL\Desktop\game2.log、p4.png：地面两块黑色方块、出城出现不能攻击的督军。
- 用户最后明确回复：game2.log/p4.png 是修复前的日志和截图。文件时间约 07:04，早于 92812e5a 07:06 的提交。
- 因此黑块修复后的状态尚未验证，不宣称解决或仍复现。用户需拉取 669184cc（及后续）重启，提供新截图/日志。督军错误生成已代码修复，但也需真机确认。
- 排查黑块时暂时加入的 shadow flags 和诊断输出已撤回，没有按猜测过滤普通阴影或墙体。

## 恢复后的长巡游：已结束并失败，当前没有继续运行

用户授权瓦片修复后恢复长巡游。为避免另一 chat 未提交源代码影响，创建了隔离固定版本 worktree：

C:\Users\DELL\.codex\worktrees\act-tour-tile-fix\riiablo

版本 e752701f（注意早于隐藏地板和申克修复），运行 tools/run-map-tour-seeds.ps1，25Hz，五种子计划，输出：

server/d2gs/build/map-tour/tile-fix-e752701f/seed-1-rate-25

- seed1 完成 132/132 加载覆盖，但结果 FAIL；walks=219 transitions=79 detachedJumps=50 combatKills=116 failures=2。
- level25：Missing terrain/collision or blocked entry。
- level117：Movement/route blockage did not resolve，target=(5055,4625)。
- 批次严格停止，seed2/3/12345/54321 未运行。构建/巡游约 1h10m，runner 退出1。不要报告五种子通过或仍在运行。
- 输出包含 console.log、map-tour.log、log-issues.json、failure-level-25.txt、failure-level-117.txt、run-baseline.txt。绝对路径从上面 worktree 拼接。
- 批次控制台另存 C:\Users\DELL\AppData\Local\Temp\riiablo-long-tour-e752701f.log。
- 工作树保持供诊断，勿归档删除以免丢失 ignored build 日志；需要清理时先保存日志。

## 接续建议

1. 先读取此文、docs/map-acceptance-plan.md 及相关代码，检查 git 状态；不要重复已完成修复。
2. 等待/分析最新真机截图与日志，确认 A5 黑块、城门显示和申克位置/攻击是否正常。
3. 分析长巡游 level25 和117 失败包及 log-issues.json，补失败回归、参考 D2MOO 修复，再在新固定提交基线恢复五种子；旧失败目录不能覆盖。
4. 仍有缺失 AI fallback、Attributes 空 base 重置、动态生成移位等非瓦片警告，严格验收未闭环。五城十方向短 TCP 已在较早版本通过，但不代表整套全 ACT 无错。

## 测试入口

- ./gradlew.bat :core:mapRegression --no-daemon --max-workers=1
- ./gradlew.bat :core:mapResourceAcceptance '-PmapAuditSeeds=1,2,3,12345,54321,127897359' --no-daemon --max-workers=1
- ./gradlew.bat :core:test --tests 'com.riiablo.engine.server.quest.Act5*' --tests com.riiablo.map.Act5TownHiddenFloorResourceTest --no-daemon --max-workers=1
- :server:d2gs:headlessTownSeams：五城双向短 TCP，不是132图长巡游。
- tools/run-map-tour-seeds.ps1：正式固定版本批次，脏工作区/复用输出目录/任一失败或未审查警告会停止。
- tools/audit-town-seams.ps1、tools/map-acceptance-audit.ps1、tools/test-map-acceptance-audit.ps1：独立证据审计。
