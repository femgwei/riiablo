# 佣兵雇佣窗口（2026-10-16）

## 已实现

- NPC 的“雇佣”打开候选窗口，不再直接购买首个佣兵。窗口使用原版
  `data\global\ui\MENU\textslid.dc6` 的边框、空心箭头、轨道和金色滑块，
  半透明背景、金币标题、七条双行候选及取消栏按用户提供的原版截图布局。
- 每个候选第一行显示姓名、等级、生命、防御、费用；第二行是技能类别。
  `Hireling.txt.HireDesc` 的 `comb / off / def` 分别查原版 TBL
  `StrMercEx15 / StrMercEx14 / StrMercEx12`。`HireDesc.txt` 的英文描述不是 TBL key。
- 支持点击两行、滚轮、箭头、拖动滑块、上下/PageUp/PageDown、ESC；
  打开时拦截世界点击及键盘移动，离开 NPC 范围自动关闭。
- A1 卡夏、A2 格雷兹、A3 艾席拉、A5 夸尔凯克分别提供对应兵种；
  A4 泰瑞尔仍仅复活，没有虚构的 A4 雇佣池。
- 列表与价格由本地权威系统或 D2GS 提供。选择精确的候选 index + seed，
  扣随身/仓库金币并创建对应技能变体；失败提示不静默。已有佣兵时，
  必须确认替换及旧装备丢失警告；创建/扣款失败保留原佣兵、装备和金币。
- 真实 Hireling Id 与四种兵种 class 分开保存、计算技能、属性及经验，
  D2S 使用原生 Hireling Id。联网发送佣兵组件及权威存档头，客户端绑定主人、
  HUD 和属性；快照早于主人到达会保留待关联，重复头不会反复回满生命。

## 机制依据

- D2MOO `D2Common/src/Monsters/Monsters.cpp::MONSTERS_HirelingInit`：
  原生行、等级上界及 `max(Gold, Gold * (100 + 15 * levelUps) / 100)`。
- D2MOO `D2Game/src/UNIT/SUnitNpc.cpp::sub_6FCC7FA0`：指定候选、替换及付费路径。
- 本地 1.10f MPQ 的 `Hireling.txt`、`HireDesc.txt` 和英文/中文 TBL，
  以及 `D2Client.dll` 的 `Hireables.cpp`、`PopupHireling`、`ui\menu\textslid` 字符串。
- A2 普通 Id 6/7/8 是 Prayer/Defiance/Blessed Aim，噩梦 9/10/11 是
  Thorns/Holy Freeze/Might，地狱 12/13/14 是 Prayer/Defiance/Blessed Aim。
  第二行是对应类别，不是装饰文字，也不是单独的可购买选项。

## 验证与边界

本次 11 个定向测试类共 53 项通过（失败/错误/跳过均为 0），
`:core:test`、`:server:d2gs:compileJava` 与真实 MPQ 窗口离屏渲染构建成功。
已目视核对繁体中文 A2 预览：无 ERROR 文本，七条候选的费用及技能类别可见。
英文 A5 地狱难度预览也已核对，使用原版紧凑列表字体完整显示高等级费用。

定向测试覆盖四 NPC 与三难度原生池、指定技能、费用/生命/防御预览、
随身及仓库扣款、过期/错误 seed、玩家池隔离、活/死佣兵替换回滚、
协议往返、佣兵组件序列化、客户端迟到主人与不重复治疗、D2S 写入/读取后恢复变体。
实体创建在交易集成测试中使用隔离工厂，不将其描述为真实地图或 TCP 端到端验收。

`desktop:offscreenHireMercenaries` 使用真实 MPQ、字体及生产窗口导出 800×600 PNG：
`-Pd2Home=<安装目录> -PvisualOutput=<输出目录> -Planguage=zh_TW`，
可加 `-PhireNpc=150/198/252/515 -PhireDifficulty=0/1/2`。英文使用 `-Planguage=en`。
本机预览保留在忽略目录 `build/hire-ui-verification/`，不提交商业素材。

仍需另机验证实际 NPC 点击、本地/联网出兵、战斗、复活与存盘重进。
客户端与服务端都要更新，旧服务端不会提供新候选协议。

真实 MPQ 回归同时纠正了 A5 城镇常量：拉苏克 511、安亚 512、马拉 513、
尼拉塞克 514、夸尔凯克 515、终场泰瑞尔 521。此前把 `Expansion` 分隔行计入了
文本行号，导致它们比原生 `hcIdx` 大一；服务识别和 Hireling Seller 必须使用后者。
A5 雇佣兵的实体 Class 同时按原版 Hireling.txt 使用 561（act5hire2）。

当前候选生成是每玩家/NPC 最多十二条、按原生行覆盖技能类别并随机增加 0–4 级，
不是 1.10f 全姓名池、NPC 等级上限、单位 RNG/刷新顺序的逐指令复刻。
卡夏低等级及 A5 任务门槛尚未在新付费入口中完整对齐；不能据此宣称整个雇佣机制完全原版化。

旧版项目曾把四种兵种编号直接写入 D2S Hireling Id 字段；这种实验存档的 1–3
与合法原版罗格 Id 无法无歧义区分，本次不猜测迁移。若旧佣兵兵种不符，请先取下装备，
再通过新窗口重新雇佣。原生格式的新存档按真实 Id 恢复。
