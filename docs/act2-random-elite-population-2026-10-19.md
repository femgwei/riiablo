# A2 随机冠军、头目与仆从人口生成

## 修复范围

原生 A2 地图最终走 `BaseMapBuilderD2MOD.finish`。原先这里只生成普通群，
没有调用随机精英选择；独立的 `MonsterSpawner` 并未接入此生产入口。
本次将随机精英接入真实地形完成后的延迟 RoomEx 人口队列；共用该入口的 A3–A5
也使用同一规则，A1 的独立人口生成器不在本次修改范围内。

- 保留 `MonStats.MinGrp/MaxGrp`、Fallen/Scarab 单初始成员和每成员的
  `PartyMin/PartyMax`，没有将所有普通群改为 3–5。
- `Levels.MonUMin/MonUMax` 按难度控制区域精英阈值：未到下限时按已处理房间比例
  加压，否则在未到上限时使用原版 `roll % 100 <= 5`（6%）。不是每个房间都有精英。
- 普通难度精英从 `umon` 全列表等权选种；噩梦/地狱从地区已选怪物按 Rarity 选种。
  `placespawn` 替换遵循原版普通选择 chance=20、高难度精英 chance=0 的严格大于比较。
- 从实际 MPQ 读取 `MonUMod`、`MonType`。冠军概率由第 0 行 constants 决定（1.10f 为
  20%）；按难度的 cpick/upick、enabled、MonType 祖先排除和 fPick 过滤词缀。
  当前原生地图使用资料片规则（允许 version=100 条目）。
- 冠军为首领加 1–3 个同种冠军（合计 2–4），首领按 cpick 选变种，其他成员为普通冠军。
  每个成功放置的冠军均计入区域精英计数，故一次冠军群可以越过上限。
- 随机金色头目携带普通/噩梦/地狱 1/2/3 个不重复随机词缀，追加 3–6 个仆从。
  仆从优先使用 minion1，否则同种；只继承 xfer 非零的词缀。
- 保留整数出生选择、20 次首领位置尝试、传送点/出口附近排除和同伴邻近搜索。
  位置失败不硬塞怪物、不虚增精英计数；阻挡可能使实际成员数少于计划数。
- 队列保留 rank/affixes/championType，房间首次激活时传给实体工厂；仆从保留
  packId/minionOwnerId 和运行时头目引用。新 packId 避开已有预设怪群，重复 finish
  不会再次排队，房间再次激活不会再次出生。
- 地区怪种选择改用地图/关卡派生种子，避免依赖共享的 MathUtils RNG 状态。

## 原版依据

先对照本地 D2MOO，再核对 `G:/BaiduNetdiskDownload/Diablo II 1.10F` 的原始
TXT 和 DLL；没有使用独立旧 MonsterSpawner 中的经验概率。

- `D2Game/src/MONSTER/MonsterRegion.cpp::D2GAME_PopulateRoom_6FC67190`：
  密度尝试、精英/普通分支、普通群例外和冠军同伴数量。
- `MonsterChoose.cpp::MONSTERCHOOSE_GetBossSpawnType`（0x6FC62670）：
  区域阈值与 `<=5`；`MONSTERCHOOSE_GetPresetMonsterId`：umon、Rarity、placespawn。
- `MonsterUnique.cpp::sub_6FC6E8D0/sub_6FC6E940/sub_6FC6EC10`：冠军概率、
  词缀数量/权重/资格；`D2GAME_SpawnMinions_6FC6F440`：3–6 仆从、minion1、xfer、owner。
- 原版 D2Game.dll 的 0x6FC62670、0x6FC674D8–0x6FC67570、0x6FC6E8D0
  反汇编也确认了上述概率比较和数量参数。
- 原版 Rocky Waste 常见跳跃者、猫女、秃鹰、投石怪为 1–3；部分骷髅/木乃伊为
  2–4，僵尸为 3–4，虫群为 4–8；scarab1 为 1 加 Party 2–4，即 3–5。
  因此“原版所有普通群都是 3–5”不是本次采用的假设。

## 验证

使用原版 1.10f MPQ。主代码编译与模块边界检查通过。

定向 22 项通过、零失败/跳过：

- `NativeMonsterEliteRulesTest`：阈值比较边界、20% 分界、难度权重、不重复选择、
  xfer、MonType 祖先环和模式/近战过滤。
- `NativeMonsterElitePopulationResourceTest`：冠军 2/4、头目仆从 3/6 的两端，
  无普通 Party 递归追加、完全阻挡不消耗预算、普通群数量仍按 TXT 变化。
  真实 A2 五个沙漠区域（41–45）、seed=1，普通/噩梦/地狱均生成两类精英：
  冠军单位分别 3/10/12，随机头目 5/15/21，仆从 23/68/95。
  这些是 riiablo 本次固定种子的排队数量，不是原版实测人口或保证数量。
- 原有 `NativeRoomMonsterPopulationResourceTest`（A2–A5 桥接、warp、阻挡/Party）、
  `MonsterSpawnIntegerSelectionTest`，以及 `MonsterRoomActivationTest` 的随机精英
  元数据/owner、新房间首次出生、固定超级头目三个用例通过。

首次普通测试编译被既有 Vulture/AI 测试引用已移除的方法阻断；临时 Gradle init
脚本只编译上述类。首次扩大运行 33 项中另有既有
`nativeRoomActivationSleepsAndWakesMonsterAi` 断言失败（应休眠却更新一次）；
该用例无实体工厂、不进入本次修改的人口生成分支，本次未改动 AI 休眠规则。
随后将激活回归限定到上述三个相关用例，22 项全部通过；不宣称全套测试通过。

真实 GL 客户端验证：`:desktop:offscreenCamp -PoffscreenLevel=41`，指定同一 1.10f
安装和独立的 build 下存档目录，输出 `[OFFSCREEN_CAMP] result=PASS act=2 ... frames=6`
及 `[CLIENT_SHUTDOWN] result=PASS`。实际执行 A2 地图加载、碎石荒地房间首次激活和
渲染；本地输出在 `build/a2-elite-smoke`。此冒烟不会逐只悬停核对蓝名/金名或检验
所有词缀技能，仍需用户另一台电脑复测。

## 复测与边界

另一台电脑拉取后重新进入游戏，连续探索碎石荒地、干燥高地、遥远绿洲、遗失城市等
多个区域，观察蓝名冠军和金名头目及仆从。单个普通难度关卡只有少量精英，不能要求
每群、每个房间或每个关卡都出现蓝名；已激活的旧房间不会补刷。

本次修复“随机精英根本未接入生成入口”，不是完整精英系统重写：随机金怪专名拼接、
冠军变种的全部独立属性/染色，以及部分词缀的完整技能效果仍受现有运行时实现限制。
房间队列及实体元数据测试不等同于全部战斗技能或肉眼画面验收。地图随机流也没有宣称
与原版 game/room/unit 三套 seed 的消费顺序逐位一致。
