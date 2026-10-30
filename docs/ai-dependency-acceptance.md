# AI 依赖第四阶段复跑

第四阶段仍为 PARTIAL。此入口采集固定版本的局部自然地图运行与 GL 截图，不能把它
作为全部 AI 原版一致性、五幕整链或跨电脑视觉验收的 PASS。

从仓库根目录在 PowerShell 运行，Output 必须是尚不存在的目录：

```powershell
./tools/run-ai-dependency-acceptance.ps1 `
  -D2Home 'G:\BaiduNetdiskDownload\Diablo II 1.10F' `
  -Output 'F:\ai-evidence\machine-a' `
  -Revision '<待验证提交完整SHA>' -Seed 1
```

脚本从指定 Git 提交导出 source.zip，在独立 source 目录编译运行，不会把共享工作区
未提交改动混入基线。需要 Git、可运行 Gradle 的 JDK、1.10F MPQ 及可创建 LWJGL2
上下文的显卡驱动；不包含或复制游戏资源。日志、临时角色存档和编译输出均在 Output 下。

- acceptance.json：提交、源码包 SHA256、MPQ 名称/大小/SHA256、机器/JDK、参数与各阶段状态。
- natural-console.log、natural/map-tour.log、natural/ai-summary.txt：seed=1、25Hz、
  地图1/2及非城镇入口200 tick的自然AI观察。审计拒绝观察窗口不足、无行为证据、
  fallback、未审查告警、死亡恢复或未完成巡图。观察成功仍为 OBSERVED_RUNTIME_ONLY。
  如果入口窗口尚无实际怪物行为，使用正常TCP移动走近最多8个已可见的同地图活体敌人，
  从生产共享碰撞网格规划至约6子格外，路线长度限制128子格。不传送、不生成怪物、
  不改AI状态、不发送攻击或调用战斗清障；动态阻挡则停止并尝试下一候选。到达后再
  观察200 tick。入口与接敌窗口都执行相同的无攻击、完整tick与唯一配对审计，
  未遇到可达敌人或仍无实际动作时继续失败，不将“思考过”放宽为通过。
- gl-console.log、gl/rogue-encampment-smoke.png、gl/rogue-encampment-manifest.txt：
  生产 GameScreen 在地图2的854×480截图，以及 GL vendor/renderer/version。
  GL使用专用CampSmoke角色的固定地图种子0x110FCA4D，与TCP观察的Seed参数分开记录。
  完整截图等待角色有可绘制GPU图层、无待加载资源且连续三帧稳定；清单记录实际地图
  种子、角色可绘制图层数和稳定帧数。
  CAPTURED_REQUIRES_VISUAL_REVIEW 只表示已采集；静态截图不能证明整段动作、音效或战斗正确。

自然地图检查失败后仍尝试 GL，全部证据保留；任一检查失败时清单result=FAILED且
脚本最终抛错，两项都完成也只记PARTIAL。原有1×1冒烟默认
尺寸保持不变，可单独用 :desktop:offscreenCamp 的 offscreenWidth/offscreenHeight 属性
调整截图尺寸。

在另一台电脑检出相同提交、安装同版本资源后复跑，并传入第一台的 acceptance.json：

```powershell
./tools/run-ai-dependency-acceptance.ps1 `
  -D2Home 'D:\Games\Diablo II 1.10F' `
  -Output 'D:\ai-evidence\machine-b' `
  -Revision '<相同提交完整SHA>' -Seed 1 `
  -CompareWith 'D:\ai-evidence\machine-a-acceptance.json'
```

比较只检查源码/资源/参数指纹及不同主机名，不会把相同输入认作相同结果，更不会自动
宣布跨电脑通过。需要同时复核两台日志和截图；主机名也不是硬件身份的证明。
第二、三阶段遗留、全部自然路线、门/接缝/特殊技能、死亡复活、规模性能以及与原版的
动态画面对照仍需补验。
# 多种子自然路线自动观察

可使用 `tools/run-ai-natural-matrix.ps1 -D2Home <1.10f目录> -Output <新目录>`
依次运行 seed 1、2、3；用 `-Seeds` 指定唯一的种子列表。所有运行固定到同一提交，
每个种子分别保存源码快照、资源哈希、TCP 日志和验收清单，根目录 `matrix.json`
汇总状态、实际路线 SUMMARY 和失败原因。任一种子失败时，继续收集其他种子证据，
最后以失败结束。矩阵跳过 GL，单次完整入口仍默认运行 GL。

当前矩阵只覆盖 level 1/2 的自然路线变化。成功状态为 `OBSERVED_RUNTIME_ONLY`，
不能推导门、窄道、全部技能、全部怪物资源、性能或原版一致性已验收。

2026-10-30 使用新矩阵脚本对源码提交 `c157ccf6a` 运行 seed 2，证据为
`build/ai-matrix-seed2/matrix.json`。结果 `visited=2/2`、`walks=4`、
`transitions=1`、`detachedJumps=0`、`failures=0`，状态 `OBSERVED_RUNTIME_ONLY`。
这是新增种子的单行实测。

随后在提交 `d2ee7df1c` 上完成默认 seed 1/2/3 全矩阵，证据目录为
`build/ai-matrix-three-seeds`。seed 1、2 状态为 `OBSERVED_RUNTIME_ONLY`；
seed 3 路线同样完成（visited=2/2、walks=4、transitions=1、failures=0），
但两条 `AnimDataResolver` 的 `AMBL2HT` 到 `AMGH2HT` COF 回退警告被审计拒绝，
因此总矩阵为 `FAILED`。未放宽资源回退审计；该资源问题仍待修复。

