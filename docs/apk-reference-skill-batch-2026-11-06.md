# APK 参考技能补齐批次（2026-11-06）

本批按用户要求，先读取既有七职业进度与客户端专项记录，再对照 D2Survivors
APK 恢复出的 JavaScript、怪物动作描述、当前生产代码和 D2MOO。一次集中修改、集中验证、
集中提交。历史记录中的“非视觉 100% / 总进度 97%”描述当时的 gate 范围，不代表
全部原生技能机制均已实现；本次按实际调用链重新核对。

## 参考与判定规则

- APK：`F:/3rd_src/D2Survivors-安卓移植.apk.1`，SHA256
  `9b5f7289bee23c4143f43fd6a818aee3ffbc14927f9063cc09314e52a413ed33`。
- 恢复代码：`F:/3rd_src/D2Survivors_extracted/analysis/readable-www/assets/`。
  原始 52 个 JS 与 APK 哈希一致，格式化副本通过 52/52 AST 等价验证。
- 主体 `itemArt-BOATIR-s.js` 含职业技能定义、怪物行为与动作事件桥。
  完整声明索引：154 主动 cast、66 被动对象、418 怪物记录、527 怪物技能声明、
  2,169 怪物动作记录（582 含事件）。声明数量不等于原版完整实现数量。
- D2MOO：本机 `F:/3rd_src/D2MOO`；1.10f MPQ：
  `G:/BaiduNetdiskDownload/Diablo II 1.10F`。公式、服务端伤害归属、原生动作与路径
  以这些证据为准；APK 的进化技能、自动施法、伤害倍率和玩家跟随螺旋均不用于替换原版。
- APK 未运行；现有静态预览和自动化测试不计作原版/riiablo 同条件窗口逐帧验收。

## 本批修正

- 哨卫：普通陷阱开火更新朝向，播放原生 S2 / `seq_chargesentry`，结束恢复 NU；
  Death Sentry 尸爆使用 S2。表现事务不再次派发 SkillDo，不重复生成伤害弹体。
  Inferno 异常退出清除通道状态。APK 约第 29115 行的陷阱动作链提供表现线索，
  动作选择以原生 MonStats/MonSeq 为准。
- Grim Ward：三种体型 start → 权威 loop → 客户端 stop；loop 使用技能持续时间，
  按剩余帧数 modulo Param1 调用恐惧脉冲。禁止客户端根据 start 再生成同一个 loop。
  对应 D2MOO SrvHit26/27、SrvDo14 和 APK 野蛮人 start/loop/stop 组合。
- 圣光弹：成功治疗分支显式携带 `targetOverlay`；本地与联网客户端消费同一事件，
  普通敌对命中不误贴 healing。协议追加字段对旧包保持空值兼容。
- 祝福之锤：FP16 坐标量化、重复网格点重试，以第几个已接受点推进路径，取代把
  螺旋采样次数直接当作路径点编号。对应 D2Common `PATH_ComputePathBlessedHammer`。
- 通用弹道动画：资源延迟加载/重建时按原生年龄定位动画，保留小数帧和循环范围；
  定位不补发动作回调。CltDo5 地面火焰继续使用自己的随机循环与熄灭时钟。
- 德鲁伊：补齐极地风暴的原生通道、旋风/龙卷风的数量和独立路径初始化、
  旋风眩晕及龙卷风周期范围回调；补齐旋风护甲独立吸收池，按火→冷→电在
  PvP 缩放之后、MDR/抗性/装备吸收之前消费，保留 8.8 池值与重施行为。
- 熔岩巨石：权威出土→滚动、跨格火焰、体型击退、大体型爆炸与地面火焰生命周期；
  callback 跨格状态进入 replay checkpoint v3，兼容 v1/v2。
- 荆棘之灵：源 aura 与投射到友军的 aura 均携带实际 `thorns_percent` stat，
  使现有反伤处理消费数值；移出范围/解散后撤销。

## 已核实无需改动或不能照搬

- 骨矛：APK 每约 0.035 秒生成静止方向拖尾；原版 CltDo18 每次更新生成。
  原生 `bonespear.CltParam1=3` 是 nLoops，不能改成三帧节流。
  实际 MPQ `bonespeartrail` 为 Range=8、AnimLen=9、LoopAnim=0、SubLoop=0、
  SubStart=SubStop=0；原生条件下 nLoops 不延长寿命。本批增加原生行回归，保持生产节奏。
- Fist of the Heavens 延时/权威子弹、Sorceress 冷甲禁止假弹体、已有职业 overlay
  管线和多数怪物动作点均已有实现。本批不依据旧文档的待办标签重复添加。
- APK 的 Blessed Hammer 跟随玩家、随机初相位和连续螺旋，与原版路径不同；
  怪物自定义序列也会压缩事件到中点，不能覆盖原生多事件序列。
- APK 中 61 个怪物声明缺少本地逐皮肤描述（包括部分 Boss）；静态技能分支存在
  不证明完整离线 Boss 动画可运行。WebP 替代 PNG 的资源不能误报缺失。

## 验证与剩余边界

集中测试结果在本批完成时记录。真实窗口逐帧视觉 gate 仍独立保留，不能由 headless
或静态资源断言标记通过。祝福之锤采用原生量化算法及 float 三角采样，但 FOG LUT
各项的二进制尾数尚未逐项比对；弹体的宿主运动仍使用现有位置更新管线。

工作区既有 Actioneer 的目标选择修改与 Sorceress Ghidra snapshot 保留在用户工作区，
不纳入本批提交。
