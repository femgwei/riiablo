# 饥饿（Hunger，242）：S3 咬击与吸取反馈

本轮接在焰爪之后。目标是核对狼人、熊人的咬击动作与吸血/吸蓝展示，并修正影响该反馈的明确结算差异。没有启动原版、使用前台窗口或发送全局输入。

## 数据和源码依据

本机 1.10F 有效技能行：`SrvSt=0`、`SrvDo122`、`anim/seqtrans/monanim=S3`、`UseAttackRate=1`，限制为 wolf/bear，SrcDam=128。calc1=par5（−75% 物理伤害），calc2=dm12（生命吸取），calc3=dm34（法力吸取）；生命、法力各自参数下界 50、上界 200。没有独立 castoverlay、srvoverlay 或可见技能弹体。

公开重建源码 D2MOO `SkillDruid.cpp:SKILLS_SrvDo122_Hunger` 在成功命中时先 `SUNITDMG_FillDamageValues`，随后对已得到的物理伤害加上 calc1 百分比，再增加两种吸取比例、分配 combat 并处理耐久。减伤不是与力量、形态等增伤相加的同一项。

`SUnitDmg.cpp` 吸取结算路径在正物理伤害、有效吸取条件下，对攻击者调用 `UNITS_SetOverlay`。只有生命或法力吸取时使用相应图层；两者兼有时选择其中一个，不能因为两个池都产生恢复就同时虚构两层。资源已满和没有吸取能力也不是同一条件。

这些是本机有效数据和公开重建源码依据，未新增原版实时调用记录。

## 找到并修正的差异

1. **物理伤害惩罚顺序。** 原实现将 −75% 与力量等增强百分比相加；现在 Hunger 专用路径先计算包含增强的物理范围，再加上其 −75% 百分比。例如基础 40、力量加成 100%：原来算成 50，现在为 20；再有 100% 状态增伤时为 30。其他变身近战技能保持各自路径，元素伤害不跟随这项惩罚削减。
2. **联机吸取闪光缺失。** 本地 ElementalHitPresentationSystem 消费 DamageAppliedEvent 已能显示权威选中的 leechOverlay，但该事件没有传到联机客户端。D2GS 现在只把已选中的吸取图层转换成上一轮建立的 UnitOverlayP，按攻击者的可见范围传输。客户端复用已有 ID 映射与 OverlayManager，不重算伤害或随机选择，也不添加第二次本地反馈。
3. **吸取图层的时钟/混合模式。** steallife、stealmana 采用现有原版 Overlay 绘制策略：AnimRate=16 对应 25 FPS，Trans=3 对应 additive。先前通用分支将其播放成 16 FPS/luminosity。图层仍为一次性，不添加持久技能状态。

第二项适用于共用伤害结算产生的吸取反馈，包括普通近战/弹体吸取；只传递视觉意图，不把 DamageAppliedEvent 的扣血事件发往客户端再次执行。当前 server:d2gs 路径得到支持，旧 server:netty 示例服务器未扩展。

## 动画与资源核对

HungerAnimationDataTest 通过生产 DruidShapeShiftResolver、AnimDataResolver，检查逻辑玩家 S3 转换到两种变身的 monster S3，使用对应 COF/AnimData 和所有组成层 DCC 头；检查有效帧数、方向数与攻击关键帧。具体结果保存在测试 XML 的 HUNGER_ANIM/HUNGER_BODY 输出中。

实际读取：狼人 `40S3HTH` 为 10 帧、8 方向，AnimData speed=240；熊人 `TGS3HTH` 的 COF/AnimData 为 10 帧、16 方向、speed=208，组成层 DCC 另有 13 帧。两者都有一个攻击关键帧。熊人身体资源更长不等于应把技能强行延长到 13 帧；本轮按 COF/AnimData 的动作长度核对，不把 DCC 额外帧数当成缺动画的证据。speed 是基础记录值，不代表所有装备/变身加速后的最终速度。

吸取效果位于攻击者：

- steallife 指向 `data/global/overlays/CurseRevVampireHit.dcc`。
- stealmana 指向 `data/global/overlays/VampiricHitMana.dcc`。

两行 Overlay.txt 的 Frames 均为 13、方向 1、AnimRate=16、Trans=3、PreDraw=false，四种高度与偏移为零。测试直接读取本机 DCC 头，并按真实帧数构造无 GPU 的动画对象，不假定表内 Frames 就是文件帧数。

本机读取的两个 DCC 也均为 13 帧、单方向。等级 1、普通难度、Drain=100 的整数伤害夹具中，20 HP 物理命中恢复生命/法力各 14.3984375 HP；Drain=0 时两者均为零；资源已满时实际恢复为零，但权威选出的单个反馈仍存在。这是该夹具结果，不代表所有目标/难度的固定吸取量。

## 无窗口验证

- HungerIntegrationTest：真实 Actioneer 攻击关键帧到 DamageSettlement/LeechSettlement，检查两形态伤害、生命/法力恢复、Drain=0、满资源池仍产生单个权威吸取闪光。夹具最多发起 64 次独立攻击以跨过正常未命中分支，首个成功命中后停止，不将 miss 强改成 hit。
- NativeDruidHungerDataTest：有效回调、限制、公式与增强后再减伤的数值反例。
- HungerPresentationTest：本地 DamageAppliedEvent 与远端 UnitOverlayEvent 使用相同图层、原版时钟和混合模式，图层在攻击者上，重复反馈重启而不叠加成两层，播完释放资源。AssetManager/DCC 绘制对象为 mock，实际 DCC 头另行读取。
- LeechOverlayTransportTest：传递原先选中的单个反馈；按攻击者而非被击中目标筛选可见玩家；恢复量为零时也保留有效反馈；无吸取反馈与 reactive 事件不制造新反馈。
- 回归已有吸取结算、焰爪/狂犬病、变身近战与单位图层包测试。

最终 JDK 17 定向回归 **48 项通过，0 失败、0 错误、0 跳过**：core 44 项，server:d2gs 4 项。两个模块分别运行测试并确认 BUILD SUCCESSFUL，没有启动完整测试集或原版进程。

逐项 XML、日志、有效数据行和源码 SHA256 已归档到 `tools/original-observer/hunger-presentation-validation-2026-11-24.json`。初轮夹具因 `component.Object` 与 `java.lang.Object` 同名导致编译失败，日志保留，未计为测试通过。

## 尚未证明的部分

- 未运行原版或 GPU 绘制，不能据此宣称全方向画面、精确出生/消失帧、声音和动态光照全部一致。
- 伤害范围仍沿用现有 whole-HP 预计算路径。已修正减伤与增伤的组合关系，尚未恢复原版全部 8.8 中间精度、随机取值/暴击/抵抗阶段顺序；不把本轮几个整数反例当作所有伤害场景的精确一致证据。
- 联机验证分为队列/包、接收器与客户端图层测试，没有启动真实双客户端 socket 会话；跨区域、目标死亡/删除时的网络顺序另需验证。
- 未新增完整装备和攻速矩阵；Hunger 的原版版本差异与历史丢失命中问题没有作为本轮已复现结论。
- 职业清单保留 `runtime_status=not_run_in_class_batch`，只更新有界离线/无窗口验证进度。完成此项后继续 Shock Wave（243）。
