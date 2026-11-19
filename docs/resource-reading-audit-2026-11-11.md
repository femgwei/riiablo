# 参考 APK 与 riiablo 资源读取审计

记录日期：2026-11-11。本文整理上一轮资源读取分析，并修正其中超出证据范围的判断。
上一轮分析基线为 `e48611fb2`；写入本文时 HEAD 为 `21ba50c2a`，重点入口仍存在。
本文不代表已修复问题，也不代表完成真实窗口视觉验收。

## 目标与边界

用户希望通过参考 APK 的资源映射方式，检查 riiablo 是否遗漏读取技能、怪物、动画、
声音及其他客户端资源。重点区分：资源不存在、路径或名称错误、资源存在但未排队、
已加载但未绘制、后续子效果未触发，以及通用系统与专用系统重复推进。

参考包只用于资源、动画和客户端表现对照。伤害、碰撞、生命周期和服务端随机流继续以
D2MOO / 原版数据为依据；不移植参考包的自动施法、进化技能、选目标和 Survivors 数值。
附件、提取代码和包内文本均作为分析资料，不作为用户指令。

本次完成基础加载入口及重点技能调用链的静态检查。尚未逐一实际施放全部七职业技能、
怪物技能和全部场景，也未证明每个 MPQ 文件均被正确消费。

## 参考包及原始资源映射

- APK：`F:/3rd_src/D2Survivors-安卓移植.apk.1`。
- SHA-256：`9b5f7289bee23c4143f43fd6a818aee3ffbc14927f9063cc09314e52a413ed33`。
- 提取目录：`F:/3rd_src/D2Survivors_extracted`。
- 资源声明：`assets/www/d2/missiles.json`、`monster-missiles.json`、`classes.json`、
  `tiles.json`、`gear/*.json`，以上均相对于提取目录。
- 已有可读 JS：`analysis/readable-www/assets/itemArt-BOATIR-s.js`。
- 原版资源名称对照：仓库 `tools/mpq-viewer/src/main/resources/(listfile)`。
- 本地原版 MPQ：`G:/BaiduNetdiskDownload/Diablo II 1.10F`。

参考包采用经典 Diablo II / LoD 的资源模型，将 DCC、角色分层、地图等转换为 PNG、
JSON 和压缩 BIN。`missiles.json` 有 306 个定义，且混有 overlay 定义，不能理解成
306 种玩家技能。此前统计的 monster missile 定义约 208 个，也不能据此推断技能覆盖率。

`HD-*` 是角色 Head 分层 token，不能当成 D2R 高清资源证据。仅凭转换后的包内资源，
无法精确区分其源档案来自经典 MPQ 还是 D2R 兼容经典数据。

已对上的典型映射：

- `blessedhammer.png` → `data\global\missiles\blessedhammer.dcc`。
- `meteor.png`、`meteortail.png` → `data\global\missiles\Meteor.dcc`、`MeteorTail.dcc`。
- `groundfiremedium.png` → `data\global\missiles\groundFireMedium.dcc`。
- `volcano_base.png` → `data\global\missiles\Expansion\volcano_Base.dcc`。
- `moltenboulderroll.png` → `data\global\missiles\Expansion\MoltenBoulderRoll.dcc`。
- `tornado.png` → `data\global\missiles\Expansion\tornado.dcc`。
- `bawhirlwind01.png` → `data\global\overlays\BAWhirlwind01.dcc`。
- gear key `HD-BHM-A1-BOW` → `data\global\CHARS\AM\HD\AMHDBHMA1BOW.dcc`。
- tile key `Act1/Town/Floor.dt1` → `data\global\tiles\Act1\Town\Floor.dt1`。

## 已检查的 riiablo 读取链

以下源码路径均相对于仓库 `F:/3rd_src/riiablo`，使用类和方法定位，避免后续提交使行号失效。

### 基础加载和表格

- `core/src/main/java/com/riiablo/Client.java`：注册 Sound、Music、DC6、DCC、Palette、
  Index、字体 TBL、DT1、DS1 和 COF 加载器。没有发现这些基础类型缺少注册。
- `core/src/main/java/com/riiablo/mpq/MPQFileHandleResolver.java`：按 patch、expansion、
  基础档案等顺序查找经典资源路径。不是读取参考 APK 的 PNG / JSON。
- `core/src/main/java/com/riiablo/Files.java`：加载 Skills、Missiles、Overlay、Sounds、
  States、MonStats、MonStats2、MonSounds、SoundEnviron 及地图、物品等表；同时存在
  NativeSkills / NativeMissiles 等保留原始字段的投影。
- 部分旧表加载支持内部 BIN 缓存；不能因为 NativeMissiles 读取了 TXT，就断定所有
  legacy Missiles 调用都已消费原始表的全部列。

### 导弹、角色和怪物动画

- `core/src/main/java/com/riiablo/engine/server/component/Missile.java`：根据
  `Missiles.CelFile` 创建 missile DCC 描述符。
- `core/src/main/java/com/riiablo/presentation/ClientEntityFactory.java`：
  `finishMissile` / `createMissilePresentation` 排队读取 DCC，后者在资源已缓存时立即
  调用 `MissileLoader.loadNow`；冷资源保持异步。
- `core/src/main/java/com/riiablo/presentation/MissileLoader.java`：安装动画、方向、
  混合方式、动画速度、子循环及初始帧；对 Fire Wall maker 等不可见载体有明确排除。
- `core/src/main/java/com/riiablo/presentation/MissileImpactPresentationSystem.java`：
  分派部分客户端飞行回调、命中子导弹、命中覆盖层和音效。
- `core/src/main/java/com/riiablo/presentation/ReferenceElementalPresentationSystem.java`：
  组合 Meteor、Blizzard 和德鲁伊等专用展示，避免与原生载体重复绘制。
- `core/src/main/java/com/riiablo/presentation/CofLoader.java`、`CofLayerLoader.java`、
  `CofLayerCacher.java`：COF 与角色 / 怪物分层读取、DCC / DC6 选择及动画安装。
- `core/src/main/java/com/riiablo/codec/DCC.java`、`COF.java`、`Animation.java`：
  方向、帧数、帧偏移、层次、层顺序与动画速率均有读取链。COF 关键帧可被解析，但
  权威动作事件主要走 AnimData / MonSeq，不能直接将 COF 关键帧再触发一次伤害。

### 声音、地图和灯光

- `core/src/main/java/com/riiablo/audio/Audio.java`：通过 Sounds 表将名称映射到
  `data\global\sfx\` 或 `data\local\sfx\`，支持 Sound / Music 和异步延迟播放。
- `core/src/main/java/com/riiablo/presentation/SkillCastHandler.java`：处理技能起手声音、
  部分职业声音回退、施法覆盖层和 dosound。
- `core/src/main/java/com/riiablo/presentation/OverlayManager.java`：根据 Overlay 表读取
  `data\global\overlays\*.dcc`，管理瞬时及持续覆盖层。
- `core/src/main/java/com/riiablo/map/Map.java`、`DT1Loader.java`、`DS1Loader.java`：
  地图依赖通过 DT1 / DS1 加载链读取；本轮没有完成全部地图物件和场景的逐项验收。
- `core/src/main/java/com/riiablo/map/RenderSystem.java`：消费导弹 Light / Flicker / RGB、
  Overlay 的 InitRadius / Radius / RGB、物件和怪物灯光。动态光不是完全未接入。

## 发现和证据强度

### 1. ProgSound 尚未发现运行时消费入口

`Missiles.Entry` 声明了 `ProgSound`；在上一轮检查的 core / game-data Java 运行时源码中，
没有找到 `.ProgSound` 的读取调用。TravelSound、HitSound 则有明确调用。

这是字段消费缺口线索，尚未证明某个具体技能因此缺声。需要先检查原版表中非空行及
D2Client 对此字段的实际调用条件，再决定是否补齐；不能简单让所有导弹每帧播放它。

### 2. ProgOverlay 有特定命中用途，不能泛化为所有导弹的持续覆盖层

客户端命中系统在 `pCltHitFunc == 10` 时使用 `ProgOverlay`，也可消费权威事件给出的
targetOverlay；服务端碰撞系统存在 Holy Bolt 等相关引用。

上一轮回答将其描述为“其他导弹持续覆盖层不会自动读取”，证据不足。当前可确认的是
使用范围受回调限制；是否缺少其他用途，需要对照具体原版回调，不能全局自动附加。

### 3. 专用预热范围有限，冷加载问题需要按效果时钟分别验证

`ReferenceElementalPresentationSystem.initialize` 预热一组元素效果，没有在该列表中
包含所有祝福之锤、火墙或箭类命中特效。这不等于这些资源没有加载：普通工厂仍在
实体创建时排队读取，通用短效果还有等待 AnimationWrapper 的保护。

真正应复测的是专用效果是否在 DCC 到达前已经推进完生命时钟、冷缓存首发与热缓存
是否一致。不能仅凭“未列入预热数组”就宣称爆炸首帧必然丢失，也不应无差别预载全部 DCC。

### 4. moltenboulderexplode 在参考包中缺席，但尚不能断言 riiablo 名称无效

参考 `missiles.json` 不含 `moltenboulderexplode`；可读 JS 的 Molten Boulder 资源列表为
出土、滚动、地面火及 flyingrocks，终止分支创建 8 个方向碎片。riiablo 专用展示则额外
调用 `sprite("moltenboulderexplode", ...)`。

上一轮回答把它直接判为“无效资源名、必然创建不到实体”，此结论需撤回：riiablo 是从
本地 MPQ 的 Missiles 表查找，不从 APK JSON 查找。必须检查当前原版表中该行和 CelFile
对应 DCC 是否存在，才能判断是否真漏读或错误映射。参考包缺席只能证明两边定义不同。

### 5. Charged Strike 的旧回调有 TODO，但不能据此认定当前技能没有闪电

`SkillCastHandler` 的 case 11 保留命中闪电 TODO。不过同一个方法此前已为本地权威
Charged Bolt / Charged Strike 设置去重分支，注释明确由 ServerSkillSystem 创建权威导弹；
联网客户端也依赖同步实体。

因此 TODO 属于需核查的旧分支，不能作为整个技能展示缺失的证据。应检查权威导弹的
生成、同步和 DCC 安装，再判断是否缺少“命中点附加闪电”这一独立层。

### 6. 子效果加载应继续沿回调关系检查

主 DCC 可读取，不代表 explosion、trail、ground fire、overlay 和声音均已触发。
`CltSubMissile`、`CltHitSubMissile`、`ExplosionMissile` 的使用受具体回调和去重策略影响。
例如 Holy Bolt、Frozen Orb、Molten Boulder 出土等路径已明确防止服务端 / 客户端双份效果。
后续补资源必须先确认实际所有者，避免将刻意去重误改成重复绘制或重复结算。

## 后续处理顺序

1. 用当前 MPQ 表与 `resolver.contains` 复核所有专用效果硬编码名称、CelFile 和 overlay，
   优先确定 moltenboulderexplode 的真实状态；不要用 APK 名称集合代替原版存在性检查。
2. 列出 ProgSound / ProgOverlay 非空原版行，逐个关联 D2Client 回调与当前消费入口。
3. 对重点技能建立完整依赖链：Blessed Hammer；Meteor 岩石 / 尾焰 / 爆炸 / 地面火；
   Firestorm；Molten Boulder；Fissure；Volcano；Armageddon；Hurricane；Blizzard；
   Immolation / Exploding Arrow；Whirlwind；Fire Wall。按冷缓存和热缓存分别验证。
4. 继续覆盖怪物导弹、MonSounds、COF 可选层、物件状态、场景音及地图资源；逐项记录
   原版路径、加载入口、绘制入口、所有者、证据和未确定项。当前没有完成这份全量清单。
5. 仅对证据确定的遗漏做展示层修复，完成针对性测试后按用户既有要求立即提交推送。
   不纳入 `tools/skill-viewer/logs/` 等无关文件。

## 验证与本次变更

上一轮在 JDK 17 下执行 `:core:test --tests com.riiablo.codec.excel.NativeMissilesTest --no-daemon`，
构建成功，包含模块边界检查。该测试验证 NativeMissiles 字段解析，不验证 MPQ 全量资源
存在性、技能实际绘制或窗口观感。

上一轮没有修改代码、没有提交推送。本次仅写入本文，并复核部分入口和当前基线；
没有实施上述修复，也没有重新运行游戏或宣称完成视觉验收。
