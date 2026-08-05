# 更新日志 · Changelog

所有对本项目的显著更改均记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [SemVer](https://semver.org/lang/zh-CN/)。

## [1.0.12-alpha] - 2026-08-05

### 修复

- **清理领地 DLC 旧协议残留**：`gradle.properties` 中残留了早期（0.0.3 之前已废除）的 MIT 声明，实际所有模块早已统一为 Apache-2.0。本次排查许可证一致性时发现并修正该疏漏
- **构建依赖保护**：`.gitignore` 中为 `jiushi_friends/libs/` 和 `jiushi_territory/libs/` 添加例外规则，确保 clone 后可构建 DLC 模块

### 其他

- 加入 MC 中文 MC 百科

## [1.0.11-alpha] - 2026-08-05

### 修复

- **好友接受请求持久化**：`acceptRequest` 补充 `savePending()` 调用，重启后已接受的请求不再残留
- **好友接受请求并发**：`acceptRequest` 加 `synchronized`，与 `sendRequest` 锁策略一致
- **客户端数据 volatile**：`FriendClientData` 和 `TerritoryClientData` 字段加 `volatile`，修复网络线程写入后渲染线程不可见
- **领地白名单线程安全**：`Territory.allowed` 改用 `ConcurrentHashMap.newKeySet()`
- **定时公告计数器溢出**：`AdminManager.tickCounter` 改为 `long`
- **选区跟踪死代码**：删除 `AddonClientEvents` 中冗余赋值逻辑

## [1.0.10-alpha] - 2026-08-05

### 修复

- **购买原子性**：`purchaseItem` 加方法级同步锁，防止并发购买导致透支
- **金币 int 溢出**：`MoneyManager` 内部改用 `long` 计算，结果钳制到 int 安全范围
- **客户端列表线程安全**：`pendingShopListings` 改用 `synchronizedList`，防止跨线程 CME
- **封禁时长溢出**：封禁界面年份乘法改用 `long`，防止溢出导致误判永久封禁
- **ADD 价格无效反馈**：价格 ≤0 时补充调用 `buildAndSendListResponse`，错误提示送达客户端
- **领地计数大小写**：私人领地数量检查改用 `equalsIgnoreCase`
- **领地加载 NPE**：`nextId` 加载时增加 `instanceof Number` 类型保护
- **邀请码并发竞态**：验证路径增加双重 `synchronized(admins)` 保护检查-添加原子性
- **Inventory 部分放入**：购买时设置 `stack.setCount(1)` 再放入背包
- **领地渲染硬编码高度**：改用 `level.getMinBuildHeight()` / `getMaxBuildHeight()`
- **clipText null 保护**：增加 null 检查，防止异常商品名导致渲染崩溃
- **重复封禁检测**：封禁前检查 `isBanned()`，避免重复条目
- **BanManager null 防护**：`ban()` 和 `isBanned()` 增加空名检查
- **坐标解析校验**：`TerritoryScreen` 创建解析增加 `split` 长度检查
- **领地删除大小写**：`deleteTerritory` 改用 `equalsIgnoreCase` 匹配 owner
- **好友请求并发**：`sendRequest` 加 `synchronized`，减少双请求竞态
- **好友双向保存优化**：`acceptRequest` 合并两次 `addFriend` 为一次 `save()`
- **mods.toml 版本上限**：Forge 依赖上限 `[47,48)`，MC 上限 `[1.20.1,1.20.2]`，面板依赖上限 `[1.0.5-alpha,1.1)`

## [1.0.9-alpha] - 2026-08-05

### 修复

- **好友请求反向条目污染**：发送请求时错误地向 pending map 写入双向条目，导致 `getPendingRequests()` 返回错误数据
- **领地 Tick 计数器 int 溢出风险**：改为 long，移除离开领地后不必要的跟踪数据清理抖动
- **爆炸事件 NPE**：`getDamageSource()` 可能返回 null，改为判空后调用
- **活塞世界解析**：改用 `LevelAccessor.dimension().location()` 直接获取，兼容模组维度
- **踢出/封禁 NPE**：增加 `targetPlayer.connection` 空检查，踢除离线玩家给出反馈
- **传送点非法维度崩溃**：`ResourceLocation` 构造加 try-catch，异常时返回提示
- **JSON 加载容错**：SetupManager 邀请码到期时间校验 Number 类型；BanManager 废弃 JSON 双重往返
- **商品下架空声明**：商品不存在时不再误报"已下架"
- **ShopManager 双重锁**：移除 purchaseItem 中冗余的 `synchronized` 外层
- **TPA 布局间距修复**：传送页 TPA 列表与传送点列表间距修正
- **管理员自加钱日志**：MONEY 操作自加时写入审计日志
- **兑换券先扣款**：改为先存储凭证再扣款，防止中途崩溃丢钱
- **权限表改为 ConcurrentHashMap**：避免 synchronizedMap 全局锁瓶颈

## [1.0.8-alpha] - 2026-08-05

### 变更

- **开发者角色重新定位**：`developer` 权限单元从内部调试入口转为公开文档说明的 Alpha/Beta 调试角色。权限功能不变（等同服主），文档明确标注其用途与去留机制。
- 移除旧版固定验证凭证，Beta 阶段将升级为动态 8 位 + 5 分钟一次性授权码。

## [1.0.7-alpha] - 2026-08-04

### 修复

- **修复：普通玩家无法创建任何传送点**（功能性 Bug）
  - 此前 `WarpPacket` 将非管理员请求钳制为"公开"级别，但服务端 `WarpManager.setWarp` 对"公开"级别同样要求管理员权限，两者矛盾，导致普通玩家创建私人/公开传送点均被静默拒绝。
  - 现在仅"官方"传送点要求管理员权限，私人/公开传送点所有玩家均可创建，与产品文档一致。
- **修复：验证限流机制完善**
  - 此前验证限流逻辑不完整，特殊情况下可被绕过。
  - 现在所有验证统一受"5 分钟窗口内最多 5 次"限流保护。
- **修复：已是管理员时浪费邀请码**
  - 此前验证成功路径先消费邀请码再判断玩家是否已是管理员（返回 `"already"`），白白浪费一个有效邀请码。
  - 现在先判断再消费，已是管理员的玩家不消耗邀请码。
- **修复：激活码验证成功后管理面板不自动刷新**
  - `MainScreen.tick()` 中旧验证状态在赋值后才读取，导致 `becameAdmin` 判断恒为 false（死代码）。
  - 现在验证成功后管理标签页会立即重建，无需手动切换标签页。
- **修复：好友面板私聊按钮打开聊天框后立即关闭**
  - `setScreen(new ChatScreen(...))` 之后调用 `onClose()`，后者会将刚打开的聊天框直接关闭。
  - 现在先关闭好友面板再打开聊天框。
- **修复：卖家无法在商店界面下架自己的商品**
  - 下架按钮此前仅对管理员显示，与文档"卖家可下架自己商品"不符。
  - 现在卖家本人（不区分大小写匹配）同样显示下架按钮。
- **修复：好友请求重复状态提示错误**
  - 对方已向自己发送过好友请求时，再次请求会误提示"已向对方发送请求"。
  - 现在明确提示"对方已向你发送过好友请求，请在待处理请求中同意"。
- **修复：传送点覆盖权限与客户端提示不一致**
  - 客户端此前仅允许管理员覆盖同名传送点，服务端却允许所有者本人覆盖。
  - 现在所有者本人也可覆盖自己的传送点，与服务端校验一致。
- **修复：管理员识别大小写敏感**
  - 管理员列表此前以玩家名原始大小写作为键，`"Steve"` 与 `"steve"` 会被视为两个不同玩家，验证/权限判断可能失效。
  - 现在所有管理员判定（`isAdmin`/`isOwner`/`removeAdmin`/`tryAutoAdd`/`directAddAdmin`/邀请码验证）统一大小写不敏感查找，存储保留原始大小写用于显示；限流记录改为小写键控。
- **修复：过期邀请码未即时清理**
  - 过期邀请码仅靠启动时批量清理，验证时仍可能命中已过期的码。
  - 现在验证路径命中过期码会立即删除并计为一次失败尝试。
- **修复：管理员数据并发读写无保护**
  - 管理员列表的读写操作此前缺少统一同步，高并发下有 `ConcurrentModificationException` 风险。
  - 现在所有读写统一 `synchronized` 保护，保存时序列化快照。

### 文档

- **README 全面扩写**：新增"安装与快速开始"流程、"配置与数据文件"说明表、"命令速查"、"已知限制与注意事项"、"开发与构建"指南；修正开篇标语（"零前置依赖"→"DLC 仅依赖核心面板"）与版权章节编号。
- **README 中英双语**：新增完整英文版（13 章全译，覆盖概述/安装/配置/功能/API/性能/架构/命令/已知限制/开发/路线图/法律条款），顶部提供语言切换导航。

### 后续修复（同日）

- **修复：ShopPacket 下架校验与角色查找大小写不敏感**
  - 服务端 `ShopPacket` REMOVE 逻辑中卖家身份比对此前使用 `equals()`，与客户端 `equalsIgnoreCase()` 口径不一致；`getAdmins().getOrDefault()` 为大小写敏感的角色查找。
  - 现在服务端下架校验改为 `equalsIgnoreCase()`，并在 `SetupManager` 中新增公开方法 `getRole()` 进行大小写不敏感的角色查找。
- **文档修正：DLC 定价条款**
  - 删除 DLC 收费分发表述，核心面板及全部 DLC 扩展改为全免费。
  - 修正 Apache-2.0 与中华人民共和国法律条款："中国法律优先" → "Apache-2.0 为主，中国法律强制性规定在适用范围内予以尊重"。
  - 修正版权章节编号跳跃（`### 9.` → `### 8.`，`### 10.` → `### 9.`）。
- **文档修正：拆分数据隐私条款**
  - 将原有的"用户数据与隐私"拆分为两节：7.1（本地面板 — 不上传任何数据）+ 7.2（可选远程管理服务 Beta 规划 — 服主主动订阅、主动上传，非必选）。
  - 同步更新对应英文版本 13.7。

## [1.0.6-alpha] - 2026-07

- 首个 Alpha 预览版本：核心面板 + 好友系统 DLC + 领地扩展 DLC。
- 功能：管理员分级（服主/OP/开发者）· 邀请码验证 · 广播与定时公告 · 金币系统 · 玩家商店 · 兑换券 · 传送点与 TPA · 踢出/封禁 · DLC 扩展 API。
