# 野蛮人客户端绘制核对：分批进度

## 批次 1：状态特效与起效动画（2026-11-01）

已读取原版 1.10f Skills/States/Overlay 数据，清点技能 126–155 的
动画、客户端 start/do 回调、导弹、施法/命中 Overlay 和状态 Overlay。
测试 `WarCryVisualResourceTest.remainingBarbarianVisualsUseNativeRows` 输出可重复清单。

本批修正以下真实 Overlay 的绘制策略：

- 狂乱：`frenzy`。
- 狂战士：`berserkfront` 和 `berserkback`，保留两层及各自 PreDraw。
- 旋风：`whirlwind`。本批不修改移动、攻击间隔或 SQ 序列。
  特别注意其原版 Trans=0，使用 25% 半透明，不使用狂乱/狂战士的加色混合。
- 大叫、战斗体制、战斗指挥的短暂起效动画：`shoutstart`、
  `battleorderscast`、`battlecommandcast`，仍为一次性动画。

这些行过去可能未命中 aura 标志路径，或因瞬态动画没有 stateId 而走旧策略。
现在无论 aura 标志如何，以上已核对行都使用原版 Overlay.Trans、
`16/(25*AnimRate)` 帧时长及 XY/单位高度偏移。
不为被动技能凭空增加光效，不改变状态来源、持续时间或图像资源。

依据复用此前原版 D2Client Overlay 公共渲染器的静态证据：
`paladin-aura-rendering-2026-11-01.md`、
`barbarian-warcry-rendering-2026-11-01.md`。
关键地址为创建 `0x6FB5BA70`、速率 `0x6FB5BE13`、
绘制 `0x6FB57E2A..4D`；创建函数 REA Evidence：
`ev_a3c71c0f0f068cd4a098a5f761f58c4ea303196e5289f42d8bf2659968e4e0c6`。
本批复用既有二进制证据，没有重新打开/执行原版游戏。

验证：JDK 17，实际 1.10f MPQ，`:core:auraRenderRegression` 定向运行
AuraRenderPolicyTest、AuraOverlayResourceTest、StateOverlaySystemTest、
WarCryVisualResourceTest，共 19 项通过，失败/错误为 0。

## 待逐项核对，不能视为已与原版一致

1. 双手挥击 / 狂乱：原版 CltDo039，以及双手挥击 CltSt027；
   当前客户端缺少显式对应分支。须区分原版音效、手部选择和服务端 SQ 同步职责。
2. 双手投掷：CltSt011 / CltDo042，核对双导弹、手别、资源及重复生成。
3. 跳跃 / 跳跃攻击：CltSt029/030、CltDo043/044，核对 SQ、空中位置、
   落地时机和命中 Overlay；仅已有 Leap 分支不能证明 Leap Attack 一致。
4. 寻找药剂 / 寻找物品：CltSt026/028、CltDo038/040，核对尸体动作及表现时序。
5. 恐怖图腾：CltSt026 / CltDo041，核对三个尸体尺寸的 start/loop 导弹、
   朝向和生命周期；不能按战吼圆波处理。
6. 重击 / 击昏 / 专心 / 狂战士：核对命中表现与 A1 动作、
   `srvoverlay` 应用时机；专心的状态自身没有 Overlay，不能凭名称补画。
7. 被动技能：原版无独立施法 Overlay/客户端回调；仍需核对速度对动作的投影。

判定边界：已完成数据入口清点与本批 Overlay 参数对齐，不等于上述完整技能
动画状态机通过验收。尚未执行原版/riiablo 的逐帧截图或逐像素比较。
