# 2026-11-17 地狱之火、心灵传动及暴风雪修复

## 依据和修复

- **地狱之火声音叠加**：真实 1.10f `Missiles.txt` 的 infernoflame1/2 都有
  `TravelSound=sorceress_inferno`，对应 `Sounds.txt Loop=1`。此前每次持续施法生成
  火焰导弹都会启动独立循环。改为由施法者的 INFERNO 状态持有一个声音实例；
  延迟加载及暂停期间也不重复启动，状态结束、切换技能、实体删除及世界释放时停止。
  使用同一机制的 arcticblast1/2、druid_arcticblast 一并处理。
- **心灵传动拾取**：D2MOO `SkillSor.cpp:511 SKILLS_SrvDo021_Telekinesis`
  有独立 UNIT_ITEM 分支。允许 scroll、gold、missile potion、missile、potion、key；
  其中 missile 是 ItemTypes 的 `misl`（箭矢/弩矢），不是 `miss`（弓弩武器）。
  补齐物品分支，并修正鼠标按下/持续按住与动作关键帧把无生命属性物品当作死者的拦截。
  使用现有 AuthoritativeItemMoveService，保留归属、距离、鼠标物品和背包容量检查；
  成功后才删除地面实体，待删除实体增加保留标记，避免重复关键帧拾取。
  D2GS 共享库存版本并发送已有库存快照，客户端不自行变更远端库存。
- **暴风雪第二动画下落**：原版 D2Client CltDo 表 `0x6FB91260` 的第 13 项为
  `0x6FB31990`。精确反汇编 `0x6FB31B2D..0x6FB31B71` 显示：
  `step=max(1,CltParam2)`、`height=max(step,CltParam1)`，设置初始 Z=height、
  Z 速度=-step，并把持续帧数设为 height/step。
  `0x6FB5FC80` / `0x6FB5FCB0` 的已有反编译记录确认这是位置/速度的 Z 分量。
  真实 blizzard1/4 均为 120/5，即 24 帧；LoopAnim=1，DCC 分别为 6/8 帧。
  此前替换表现直接在落点播放 6/8 帧，现改为 24 帧垂直下落并循环素材。
  屏幕高度不改变服务端落点、碰撞或伤害。

原版静态目标为 `G:\BaiduNetdiskDownload\Diablo II 1.10F\D2Client.dll`，SHA-256：
`94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282`。
本轮采用 PE 原始字节的 Capstone 精确反汇编，辅以仓库已保留的 helper 反编译结果；
REA CLI 对函数反编译请求返回 invalid_request，没有把该请求当作成功证据。

## 验证范围

- 自动回归覆盖持续声音只启动一次、延迟加载、刷新、切换/退出和实体删除；
  消耗品/弹药可拾取、装备/符文/宝石/书本被拒绝、范围/归属/鼠标占用、金币入账；
  无生命属性物品的真实动作关键帧；暴风雪第二素材的起点、半程、末帧、循环及到期。
- 7 类专项回归共 59 项全部通过，零失败、错误或跳过；D2GS 编译及模块边界检查通过。
- 扩大回归时，炽烈之径 `blazeStateEmitsOnlyAfterActualMovementAndKeepsFractionalDamage`
  的持续帧数断言期望 120、实际 117。使用修改前 HEAD `b5022f5b0` 的 ServerSkillSystem
  覆盖测试类路径后仍复现同一失败，属于既有问题；本轮未修改炽烈之径，不宣称全 core 通过。
- 实机重点：长按地狱之火后松开、反复开关技能；远距离拾取药水/金币且背包满时不丢物；
  暴风雪第二素材从上方下降。尚未做本机真实音频设备或逐帧截图验收。
