# 怪物 AI 移植依赖与进度

## 本批范围与计数

目标是 1.10f 原先缺少专用实现的 37 类 AI。前批 8 类已提交；本批补齐余下 29 类。
实现计数为 37/37，缺少专用处理器的数量为 0。这个计数表示决策与所需服务端入口已落地，不表示已经完成每个原版分支的逐帧等价证明或图形客户端验收。
用户后续要求整批实现后统一测试，取代此前逐项不跑测试的安排。

本批 29 类：AbyssKnight、Ancient、BaalThrone、BaalToStairs、BatDemon、CatapultSpotter、ClawViperEx、DarkWanderer、FetishBlowgun、FingerMage、GenericSpawner、HighPriest、Imp、InvisoSpawner、MinionSpawner、Mosquito、Nihlathak、OblivionKnight、Overseer、QuillMother、ReanimatedHorde、Regurgitator、SiegeBeast、SiegeTower、SuicideMinion、SuccubusWitch、ThornHulk、VileMother、WillOWisp。

Baboon、Scarab 在本批前已有专用类；新增实表检查同时覆盖这两类及原先缺少的 37 类，不将它们重复计入 37。

## 依赖处理结果

- D01 动态路径：Pathfinder 安装原生 Toward/orbit/chase 路点，记录步数和目标分配身份，移动目标重寻路；原生动作不以普通 A* 结果替代。相关概率、等待和动作锁由各 AI 消费。
- D02 状态与命令：NativeAiState 记录 MonsterData 的模式记忆；3/19 不再解释成 Terror/Taunt。命令有队列、目标/所有者身份和截止帧；Fallen、Minion 逐条消费，CryHelp 分发到所属 minion。
- D03 生成上下文：地图保存本局区域怪物选择，房间保存生成随机流；Nest、MinionSpawner 的选类/出生模式/坐标由 Casting 保存，服务端回调调用工厂并设置所有权和奖励标志。
- D04 骑乘：Imp、SiegeTower、SiegeBeast 共享候选载具和分配身份；附着后跟随、卸载碰撞，宿主消失后清理身份并恢复单位标志；骑乘技能按 imp1 表读取。
- D05 剧情：DarkWanderer 的 InitFn43、退场和 InitFn59 计时进入正常服务器更新；Act3QuestSystem 从 PassiveSystem 改为 BaseSystem。Baal 共用现有五波任务时钟，技能 286 生成真实 SrvHit54 控制弹体，携带波次与 owner 身份以拒绝迟到回调；同实体重建为 stairs，传送门创建成功后才移除。Ancient 分三个处理器；本批实表中出现的 12 个 MonSeq 定义均可执行，混合移动/攻击模式切换、冲锋接近等待与持续喷火调度各自处理；Leap Attack 补齐起跳、落地和单次攻击。CatapultSpotter 同时查询活动单位与房间保存的死亡 catapult 记录。
- D06 死亡与尸体：SuicideMinion 的倒计时和死亡后 4 帧爆炸接入死亡更新，表现弹体不重复伤害；Regurgitator 接入吞尸回调；ReanimatedHorde 的基础类修饰符、复生次数和等待保存在 Monster 生命周期中。
- D07 特殊状态与移动预检：原先把 BatDemon Unk029 称为受击回调有误；该函数是特殊 AI 状态切换回调。实现挂顶保持、特殊控制切换和回血增量撤销。ClawViperEx 使用技能移动预检；Imp 传送绕过玩家专属关卡传送限制。
- 小技能依赖：补充 ImpInferno126、CryHelp128、Imp129、Whip131、FireMissile132、Pregnant133、Stomp134、Spawner135 的服务器入口。未重构通用伤害/反伤系统。

## 验证与边界

统一验证的命令、数量、结果见 [本批工作记录](ai-remaining-20261017-work.md)。测试包含真实安装数据、三难度加载、概率边界、动作锁、宿主释放、任务计时、MonSeq 动作点与技能回调。

1.10f 的 Siege Beast Stomp 表行 srvmissilea 为空时，原版 SrvDo134 直接失败。实现保留这个条件；测试分别验证原表无效分支和临时有效配置下的一次共享伤害掷点，不修改安装数据。

仍需区分实现完成与原版精度验收：尚未进行自然地图/图形客户端全场景验收；通用移动、碰撞及伤害沿用当前引擎，未证明与原版固定点逐帧一致。生成 overlay、其他 sumumod 组合、MonSeq 之外的动画表现及完整特殊状态矩阵不在本批测试的等价证明范围中。DarkWanderer afterglow 已拆分为原版独立的 +27 帧 MONUMOD 生成事件与 Objects.txt FrameCnt[1] 驱动的 ENDANIM 模式事件，仍需真实地图复核；任务随机流尚未接入全局 game-owned quest seed。WillOWisp 仪式逻辑已移植参考源码，但不能仅据测试声称该彩蛋属于未修改的零售 1.10f。

没有保留“缺少专用 AI 类而回退 GenericMonster”的待实现条目；上述精度/表现验证限制也不计作已通过验收。
