# 骨矛尾迹“晃动”：1.10f 与本机 1.14 的离线核对

用户反馈原版尾迹似乎会晃动，Riiablo 过于规整，并说明目测参照是 1.14。本轮只读取资源和已有 1.10f 原版运行记录，没有启动、激活任何游戏，也没有根据目测添加随机偏移。

## 核对对象与方法

- `G:\BaiduNetdiskDownload\Diablo II 1.10F\Game.exe`：文件版本 `1, 0, 10, 39`。
- `G:\BaiduNetdiskDownload\Diablo II\Game.exe`：文件版本 `1.14.0.64`。这是本机可用的 1.14 安装，不能据此确定用户目测的其他安装或渲染包装器配置。
- 新工具 `tools/original-observer/BoneSpearAssetProbe.java` 使用仓库原有 MPQ 覆盖顺序读取有效资源，对 DCC 字节做 SHA-256，并用 Riiablo 的 DCC 解码器在 headless 环境统计各方向、各帧的索引像素。不上传 GPU 纹理。
- 通过 `asset-probe.init.gradle` 的 `:core:boneSpearAssetProbe` 任务运行，传入 `probeHomeA`、`probeHomeB`、`probeOutput` 三个项目属性。两个版本必须分别提供安装目录，输出到指定 JSON 文件，不写入游戏目录。
- 输出为 `tools/original-observer/bone-spear-assets-1.10f-vs-1.14.json`；运行记录为同目录的 `bone-spear-assets-validation.log`。

## 已确认的事实

两套安装的以下有效 DCC 文件逐字节一致：

- `data/global/missiles/BoneSpear.dcc`：SHA-256 `dfdee607b0e01d04d230437a824d1d121f0fe27720cbfc70a77c6dc193db32c1`。
- `data/global/missiles/BoneSpearTrail.dcc`：SHA-256 `c49b6324cb6cf367200c20df298f7cfe6049ed93ad2ae0c845609cbef95f41eb`。

两套有效 Missiles.txt 中 bonespear、bonespearexplode、bonespeartrail 三行的命名列取值全部一致。骨矛仍为 Vel 24、Range 40、6 帧循环；尾迹仍为 Vel 0、Range 8、AnimLen 9、非循环、animrate 1024、RandStart 空值。这排除了本机两套安装间“尾迹 DCC 文件或这三行参数不同”的解释，不能排除客户端执行代码、混合模式或显示调度差异。

尾迹不是静态图片。32 个方向中，每个方向的可见帧 1～7 都有 7 种不同的解码像素图案。以已采样方向 3 为例，帧 1～7 的非零索引像素质心 x 由约 2.09 变为 -9.36，y 由约 -36.21 变为 -42.48；像素轮廓也在伸展，帧 8 全透明。这是素材内部的逐帧形变，不能把质心变化直接当作单位锚点在随机摆动，也不能把非零索引像素数当作最终混合后的亮度。

## 现有 1.10f 实测不支持随机位置扰动

原版批次仍为 `20261119-173202-8642f303`，源日志与散列见 `bone-spear-background-comparison-2026-11-19.md`。重新按真实绘制记录分组，帧 488～518 有 31 个同时覆盖 7 段尾迹和矛头的完整采样时刻。将每段尾迹位置减去同帧矛头位置，组合其动画帧与实际 DCC 方向，31 个相对布局签名完全相同。

这说明该次一级、单方向、稳定飞行阶段的尾迹，在矛头参考系下就是规则的年龄梯队：每段静止，矛头每 tick 前进，尾迹依年龄播放相应素材帧。单段素材会变化，不意味着整条成熟尾迹每 tick 都有新的随机形状。用户目测“晃动”仍可能指不同现象，当前没有采集到 1.14 的运行时证据来归因。

## Riiablo 的验证范围和后续

现有完整管线回归已检查尾迹年龄、动画帧、显示延迟与位置关系。它没有验证最终 GPU 合成后的形态、亮度、屏幕像素偏移，也没有对照 1.14 的原生绘制调用。本轮没有修改生产动画行为；尤其不能为了更接近目测效果而无依据添加横向噪声或随机初始帧。

要继续定位，应先在 1.14 的后台运行中采集同一方向的尾迹单位年龄、动画游标、实际 DCC 方向及绘制时的坐标/偏移与混合参数，再与 1.10f 和 Riiablo 区分比较。1.10f 的 D2Client/D2CMP 地址不能直接用于 1.14 合并后的 Game.exe，必须先重新定位并验证探针。本文仅完成离线资源核对，不宣称已解决或否定用户观察到的观感差异。
