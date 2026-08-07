# 九氏管理面板 · Jiushi Admin Panel

> 面向 Minecraft Forge 1.20.1 服务器的一站式管理解决方案 —— 核心一 JAR 四项功能，DLC 扩展仅依赖核心面板。
>
> All-in-one management solution for Minecraft Forge 1.20.1 servers — one core JAR covering four feature areas; DLC addons depend only on the core panel.

| 项目 Item | 信息 Info |
| --- | --- |
| 作者 Author | MA |
| 版本 Version | v1.0.14-alpha |
| 许可协议 License | Apache-2.0 |
| 适用版本 Game | Minecraft 1.20.1 · Forge 47.3.0+ |
| Java | 17 |
| 发布日期 Release | 2026 年 8 月 / August 2026 |

## 语言 Language

- **中文版本** → [点此跳转](#中文版本)
- **English Version** → [jump here](#english-version)

---

## 中文版本

### 目录

- [一、产品概述](#一产品概述)
- [二、核心面板 — 管理功能](#二核心面板--管理功能)
- [三、核心面板 — 经济与商店](#三核心面板--经济与商店)
- [四、核心面板 — 传送系统](#四核心面板--传送系统)
- [五、DLC 扩展 API](#五dlc-扩展-api)
- [六、好友系统（DLC）](#六好友系统dlc)
- [七、领地扩展（DLC）](#七领地扩展dlc)
- [八、性能概况](#八性能概况)
- [九、技术架构](#九技术架构)
- [十、命令速查](#十命令速查)
- [十一、已知限制与注意事项](#十一已知限制与注意事项)
- [十二、开发与构建](#十二开发与构建)
- [十三、更新计划](#十三更新计划)
- [十四、版权声明与许可](#十四版权声明与许可)
- [更新日志 CHANGELOG](./CHANGELOG.md)

---

## 一、产品概述

九氏管理面板是面向 Minecraft Forge 1.20.1 服务器的一站式管理解决方案，由一个核心面板 + 两个 DLC 扩展组成。所有功能集成在单一 GUI 界面中，按 `J` 键打开，无需记忆任何指令。

**核心定位**：国内小/中型私服开箱即用的管理工具箱。替代 FTB Chunks + FTB Teams + Economy + SignShop 等多模组拼凑方案。

### 模组组成

| 模组 | JAR 文件 | 版本 | 大小 | 类型 |
| --- | --- | --- | --- | --- |
| 核心面板 | `_jiushi_admin-1.20.1-1.0.14-alpha.jar` | 1.0.14-alpha | ~97 KB | 必装 |
| 好友系统 | `jiushi_friends-0.1.1-alpha.jar` | 0.1.1-alpha | ~24 KB | DLC |
| 领地扩展 | `jiushi_territory-0.1.2-alpha.jar` | 0.1.2-alpha | ~45 KB | DLC |

### 依赖关系

```
_jiushi_admin (核心面板 · 必装)
├── 依赖: Forge 47+ · MC 1.20.1
├── 导出: AddonRegistry API
├── jiushi_friends (好友扩展 · DLC)
│   └── 强依赖: jiushi_admin >= 1.0.5-alpha
└── jiushi_territory (领地扩展 · DLC)
    └── 强依赖: jiushi_admin >= 1.0.5-alpha
```

### 安装与快速开始

1. **准备环境**：Minecraft 1.20.1 · Forge 47.3.0+ · Java 17
2. **安装 JAR**：将核心面板 JAR 放入服务端与客户端的 `mods/` 文件夹；使用 DLC 时一并放入对应 DLC JAR
3. **启动服务器**：首次启动自动生成配置目录 `config/jiushi_admin/`
4. **初始化服主**：首位拥有 OP 权限的玩家加入服务器时自动成为服主（owner），无需任何配置
5. **添加管理员**：服主在面板"管理"标签页输入目标玩家名（或点击在线玩家）生成 8 位邀请码，目标玩家在面板输入激活码即成为管理员（验证成功自动授予 OP）
6. **打开面板**：按 `J` 键，全部功能在单一 GUI 中完成，无需记忆指令

> 仅装核心面板即可使用管理 / 经济 / 商店 / 传送功能；好友与领地为可选 DLC，按需放入，面板"扩展"标签页会自动识别已安装的扩展。

### 配置与数据文件

所有数据保存在 `config/jiushi_admin/` 目录，均为 UTF-8 JSON，服主可手动编辑（建议停服后修改）：

| 文件 | 内容 | 说明 |
| --- | --- | --- |
| `setup.json` | 管理员名单与角色（owner/admin/developer）+ 待验证邀请码 | 邀请码仅存 SHA-256 哈希，不存明文 |
| `permissions.json` | 管理员细粒度权限（玩家 → 权限名 → 布尔值） | 通常通过 `/admin perm` 管理 |
| `shop.json` | 商店商品列表 + 自增 ID | 物品以 NBT 序列化，保留附魔/命名/耐久 |
| `warps.json` | 传送点列表（坐标 / 维度 / 可见性 / 创建者） | 支持跨维度传送 |
| `bans.json` | 名字封禁库（原因 + 过期时间） | 与服务器原生封禁列表双重生效 |
| `vouchers.json` | 有效兑换券哈希 → 金额 | 服务端重启后券仍可兑换 |
| `friends.json` / `pending_requests.json` | 好友关系与待处理请求（DLC） | 双向存储，玩家名统一小写匹配 |
| `territories.json` | 领地数据：坐标 / 白名单 / 类型（DLC） | 含自增 ID，Y 轴不限制 |

> 经济数据不在此目录：金币存于原版记分板（`JiuShi_money` 计分项），随存档（level.dat）保存。

---

## 二、核心面板 — 管理功能

### 2.1 管理员分级体系

| 角色 | 标识 | 获取方式 | 权限范围 |
| --- | --- | --- | --- |
| 服主 owner | 金色 | 首位 OP 自动获得；邀请码授予 | 全部权限 · 不可被踢/封 · 可管理其他管理员 |
| 开发者 developer | 红色 | 定向授权（Alpha/Beta 调试专用） | 等同于服主 · 用于问题排查 |
| 管理员 OP | 蓝色 | 服主邀请码 · 细粒度权限 | 管理广播 · 踢/封 · 商店管理 · 传送管理 |
| 普通玩家 | 青色 | 默认 | 商店买卖 · 传送 · TPA · 领地 |

> **开发者角色说明**：`developer` 是 Alpha/Beta 测试阶段保留的调试权限单元，权限等同于服主。目的是当服主不在线时，模组作者可直接登录服务器定位 Bug、验证修复或紧急干预。当前使用固定验证凭证（服务端校验，不可伪造），Beta 阶段将升级为 **动态 8 位 + 5 分钟有效期的一次性验证码**，确保只有作者能获取。
>
> 该角色是否保留至正式版，**取决于 A/B 测期间社区接受度**。若服主群体认可这种做法（方便故障排查、减轻维护负担），则正式版继续保留；若普遍反对，则正式版发布时移除，届时已有 developer 权限的玩家将自动还原为普通玩家。

### 2.2 邀请码验证系统

- 服主生成 8 位安全随机码，指定目标玩家，有效期 5 分钟
- 输入错误 5 次后自动锁定 5 分钟
- 开发者验证采用独立校验通道，Beta 阶段升级为动态 8 位 + 5 分钟一次性授权码
- 验证成功后自动授予 OP 权限，面板信息即时刷新

### 2.3 广播与公告

- **即时全服广播**：黄色文字，全体可见
- **定时循环公告**：可设置间隔秒数，绿色文字轮播，随时启停

### 2.4 踢出与封禁

- **踢出**：即时断开连接，可填原因
- **封禁**：支持年/月/时/分钟细粒度临时封禁 + 永久封禁
- **免疫机制**：服主和开发者自动免疫踢出和封禁
- **双重封禁列表**：封禁同时写入服务器原生封禁列表与名字封禁库（`bans.json`），在线模式服务器也能正确拦截未上线过的玩家
- **操作日志**：写入服务端

---

## 三、核心面板 — 经济与商店

### 3.1 金币系统

基于原版记分板（Scoreboard）实现，零额外数据库。自动创建 `JiuShi_money` 计分项，玩家上线显示余额。

| 功能 | 说明 |
| --- | --- |
| 余额查询 | 打开面板商店标签即显示 |
| 玩家转账 | 三步流：选择目标 → 预设金额 / 自定义 → 确认。双方收到通知 |
| 管理员加钱 | 商店界面 +10 / +100 / +1K / +10K 一键操作 |
| 离线转账 | 目标不在线时，资金写入记分板，下次上线自动生效 |

### 3.2 玩家商店

- **上架**：手持物品 + 输入价格 → 物品从手中扣除，进入商店列表
- **购买**：点击"购买" → 自动扣款并转给卖家 → 物品放入背包（满则掉落）
- **下架**：卖家可下架自己商品，管理员可下架任何商品
- 售罄自动下架，商品数据以 NBT 形式保留完整附魔/命名/耐久
- 数据持久化于 `config/jiushi_admin/shop.json`

### 3.3 兑换券系统

- **生成兑换券**：面板输入金额 → 生成纸质兑换券，金额从余额扣除
- **右键兑换**：玩家右键持有兑换券 → 自动兑换，等额金币到账
- **防伪验证**：每张券由 SHA-256 哈希唯一标识，服务端验证，不可伪造
- **适用场景**：活动奖励、离线发放、玩家间实物交易

---

## 四、核心面板 — 传送系统

### 4.1 传送点（Warp）

| 类型 | 创建权限 | 可见范围 | 可覆盖 |
| --- | --- | --- | --- |
| 私人 | 所有玩家 | 仅创建者 | 创建者本人 |
| 公开 | 所有玩家 | 全服 | 创建者本人 |
| 官方 | 仅管理员 | 全服 | 仅管理员 |

- 传送点保存坐标 + 朝向 + 维度，支持跨维度传送
- 删除时二次确认防误删

### 4.2 TPA 请求传送

- 面板选择在线玩家 → 发送请求 → 对方 `/tpa accept` 或 `/tpa deny`
- GUI 和命令行双入口
- 玩家离线自动清理待处理请求

---

## 五、DLC 扩展 API

核心面板提供 `AddonRegistry` 公共 API，第三方模组可通过注册 `AddonEntry` 将自己的 GUI 界面接入面板的"扩展"标签页：

```java
// 在第三方模组中注册扩展
AddonRegistry.register(new AddonEntry(
    "my_addon_id",              // 唯一标识符
    "我的扩展",                  // 标签页中的按钮名称
    () -> Minecraft.getInstance()
               .setScreen(new MyScreen())   // 点击后打开的界面
));
```

> 好友系统和领地扩展均通过此 API 接入。面板启动时自动扫描并展示所有已注册扩展。

---

## 六、好友系统（DLC）

| 功能 | 操作 |
| --- | --- |
| 添加好友 | 输入目标玩家名 → 发送请求 → 对方接收/拒绝 |
| 好友列表 | 显示所有好友 · 在线绿点 · 离线灰圈 |
| 删除好友 | 点击 ❌ 按钮，双向同步解除 |
| 上线通知 | 好友登录时全联系人广播 `§a[好友] xxx 上线了` |
| 下线通知 | 好友退出时全联系人广播 `§7[好友] xxx 下线了` |
| 私聊快捷 | 在线好友旁 ✉ 按钮 → 自动填入 `/msg` 命令 |

好友数据双向存储于 `config/jiushi_admin/friends.json`，服务器重启后自动恢复。

---

## 七、领地扩展（DLC）

### 7.1 领地规则

| 属性 | 私人领地 | 官方领地 |
| --- | --- | --- |
| 创建者 | 所有玩家 | 仅管理员 |
| 数量上限 | 2 个 | 无限制 |
| 尺寸限制 | XZ 差值总和 ≤ 128 | 无限制 |
| 重叠检测 | 创建时自动检测，冲突则拒绝 | 创建时自动检测，冲突则拒绝 |
| 闯入者行为 | 拦截破坏/放置/交互 | 拦截 + 强制冒险模式 |
| 白名单 | 主人可添加允许玩家 | 主人可添加允许玩家 |
| 管理员权限 | 服主可删除任意领地 · 所有操作无视拦截 | 服主可删除任意领地 · 所有操作无视拦截 |

### 7.2 选区操作

1. **开始选区**：在领地创建界面点击"开始选区" → 关闭 GUI 进入世界
2. **设置选点**：蹲下（Shift）+ 左键点击方块设置第一个选点；再次蹲下 + 左键设置第二个选点（自动计算 XZ 差）
3. **取消选区**：蹲下 + 右键取消选区
4. **创建领地**：选区就绪后返回 GUI → 输入领地名称 → 选择"私人"或"官方" → 创建

### 7.3 保护范围

方块破坏 · 方块放置 · 右键交互 · 液体蔓延 · 活塞推入 · 爆炸破坏 均被拦截；冒险模式强制；白名单豁免；服主/OP 豁免。

### 7.4 领地管理

- 领地管理界面列出全部领地：名称、所有者、类型
- 有管理权限的玩家可点击 ❌ 删除
- 数据持久化于 `config/jiushi_admin/territories.json`

---

## 八、性能概况

| 指标 | 数据 |
| --- | --- |
| 内存占用 | 全部数据 < 5 MB |
| CPU Tick | 领地检测：人均 ~0.05ms/tick（200 领地规模） |
| 网络流量 | 开面板时单次传输 ~2-5 KB JSON |
| 磁盘 I/O | 仅管理员操作时同步写入 JSON |
| 推荐配置 | 4 核 4G 内存可承载 20 人无感知 |

---

## 九、技术架构

| 组件 | 技术选型 |
| --- | --- |
| Mod Loader | Forge 47.3.0 · Java 17 |
| 网络通信 | Forge SimpleChannel · 4 条独立频道 · 协议版本校验 |
| 数据持久化 | Gson JSON · `config/jiushi_admin/` |
| 经济系统 | 原版 Scoreboard（无需数据库） |
| 密码学 | SHA-256 + salt · SecureRandom |
| 扩展机制 | 内存静态注册表 · AddonRegistry API |
| 领地渲染 | RenderLevelStageEvent · 无 Mixin 依赖 |

---

## 十、命令速查

| 命令 | 权限 | 说明 |
| --- | --- | --- |
| `/tpa accept` | 所有玩家 | 接受向你发起的传送请求 |
| `/tpa deny` | 所有玩家 | 拒绝向你发起的传送请求 |
| `/admin add <玩家>` | 服主 | 直接添加管理员（自动授予 OP） |
| `/admin remove <玩家>` | 服主 | 移除管理员权限 |
| `/admin list` | 所有玩家 | 查看管理员列表 |
| `/admin perm <玩家> <权限> <true/false>` | 服主 | 设置管理员的细粒度权限 |

> 除上述命令外，其余功能均在面板 GUI（`J` 键）中操作。

---

## 十一、已知限制与注意事项

- **Alpha 阶段**：功能仍在迭代，可能存在未发现的缺陷，运营重要数据前请提前备份
- **领地保护不限制 Y 轴**：领地仅基于 XZ 平面，垂直方向覆盖全高度（-64 ~ 320），无法按层划分
- **流体拦截为近似判断**：目标位置 8 格内有权限玩家时放行液体，多人同区域时可能误放行
- **领地名称大小写不敏感**：`MyLand` 与 `myland` 视为重名，无法同时创建
- **传送点重名处理**：同名传送点仅所有者本人或管理员可覆盖，普通玩家请更换名称
- **兑换券妥善保管**：券的凭证存于服务端 `vouchers.json`，删除配置后旧券将失效
- **管理员识别忽略大小写**：`Steve` 与 `steve` 视为同一管理员，查找均不区分大小写
- **DLC 版本匹配**：好友 / 领地扩展要求核心面板 ≥ 1.0.5-alpha，版本不匹配时 Forge 会拒绝加载

---

## 十二、开发与构建

仓库包含三个独立的 Gradle 子工程：

| 工程 | 目录 | 产物 |
| --- | --- | --- |
| 核心面板 | `admin-mod/` | `_jiushi_admin-1.20.1-<版本>.jar` |
| 好友系统 | `jiushi_friends/` | `jiushi_friends-<版本>.jar` |
| 领地扩展 | `jiushi_territory/` | `jiushi_territory-<版本>.jar` |

构建要求：**JDK 17** · 首次构建需联网下载 ForgeGradle 依赖（国内网络可配置镜像加速，如腾讯云镜像）。

```bash
cd admin-mod
gradlew build       # 产物位于 build/libs/，已自动 reobf
```

> 注意：发版时需同步更新三处版本号 —— `build.gradle`、`src/main/resources/META-INF/mods.toml`、本文档"模组组成"表。

---

## 十三、更新计划

**Beta**

- 开发者动态验证码（8 位 + 5 分钟一次性）
- 领地细分权限（容器/开关/红石）
- 好友 TPA
- 性能优化
- 领地保护补全（流体/活塞/爆炸拦截）
- 商店分页加载

**正式版**

- 根据社区反馈决定 developer 角色去留
- 网页在线管理面板
- 手机远控 App
- DLC 付费商店

**长期**

- Fabric 移植
- 多版本适配
- 国际化（多语言）

---

## 十四、版权声明与许可

### 1. 著作权归属

本模组（九氏管理面板及全部 DLC 扩展，以下合称"本作品"）的源代码、设计、图标、文档及所有组成部分的著作权（Copyright）及相关知识产权，均归属创作者 MA 所有，受《中华人民共和国著作权法》《计算机软件保护条例》及相关国际条约保护。

### 2. 开源许可（Apache License 2.0）

本作品以 Apache License 2.0 协议发布。该协议授予任何获得本作品副本的主体以下权利：

- 自由使用、复制、修改、合并、出版、分发、再许可和/或销售本作品的副本
- 将本作品与其他软件结合或嵌入衍生产品
- 在满足许可条件的前提下将本作品用于商业目的

> **许可条件**：所有副本或实质性使用必须附带完整的 Apache-2.0 许可声明及原始版权声明；修改文件须标注变更。完整许可文本见 apache.org/licenses/LICENSE-2.0。

### 3. 商标权保留

Apache License 2.0 不授予任何商标权、商业标识权或品牌使用权。具体而言：

- "九氏"、"MA"、"JiuShi"、"九氏面板"、"九氏管理面板"、"Jiushi Admin Panel" 及本作品的相关标识均为创作者 MA 的未注册商标或商业标识
- 任何主体在分发、修改或基于本作品开发衍生项目时，不得以任何方式暗示或声称其衍生作品来自 MA 或其官方认可
- 如需使用上述商标或标识，须取得创作者的明确书面授权
- 本条款独立于 Apache-2.0 许可，即使本作品以该协议发布，商标和标识仍归 MA 独占所有

### 4. DLC 扩展声明

核心面板及全部 DLC 扩展（好友系统、领地扩展等）均以 Apache-2.0 协议免费发布，源代码与预编译 JAR 文件均免费分发，均可自由获取使用，无需支付任何费用。

### 5. Alpha 测试阶段声明

> 本作品当前处于 **Alpha 测试阶段**，仅为功能预览及早期测试用途。任何个人或组织下载、安装、运行本作品，即表示其已完整阅读并充分理解本声明全部条款，知晓 Alpha 测试阶段软件可能存在的数据丢失、服务中断、存档损坏、兼容性冲突及其他不可预见的异常风险，并自愿承担由此产生的一切后果。

### 6. Mojang EULA 合规声明

- 本模组为 Minecraft: Java Edition 的第三方模组（Mod），遵循 Mojang Studios 的最终用户许可协议（EULA）及 Minecraft 使用准则（Usage Guidelines）
- 本模组不包含 Minecraft 游戏本体或其任何部分的代码、资源、纹理、模型或音频文件
- 本模组为原创代码作品，不构成 Minecraft 的"修改版"（Modded Version），不修改或绕过 Mojang 认证系统
- 本模组及其作者与 Mojang AB、Microsoft Corporation 及网易公司无任何关联，未经其官方认可、未获其赞助或背书
- 用户须自行购买正版 Minecraft: Java Edition 方可使用本模组

### 7. 用户数据与隐私

#### 7.1 本地面板数据

本模组（九氏管理面板及全部 DLC 扩展）在服务端本地存储以下玩家数据，所有数据仅保存在服务器配置目录（`config/jiushi_admin/`）中，不会主动上传至任何第三方服务器：

| 数据类型 | 存储位置 |
| --- | --- |
| 管理员名单与角色 | `setup.json` |
| 玩家经济数据 | Minecraft 原版记分板系统 |
| 商店交易记录 | `shop.json` |
| 好友关系数据 | `friends.json` |
| 领地坐标与白名单 | `territories.json` |
| 封禁记录 | Minecraft 原生封禁系统 |

#### 7.2 可选远程管理服务（Beta 阶段规划）

未来 Beta 阶段将推出可选订阅服务"九氏远程管理"，允许服主通过手机或网页远程访问服务器（发送指令、查看状态、备份存档等）。此项服务需要服主主动订阅并部署配套 companion mod，届时部分数据（如备份存档）将由服主主动上传至由作者租赁的云服务器。

**本服务非必选项**：服主完全可以在不订阅的情况下正常使用本地面板的全部功能。订阅仅代表服主自愿委托作者提供云基础设施托管，数据上传行为出于服主自身意愿，相应数据安全责任由云服务提供商与服主共同承担。

该服务目前处于规划阶段，尚未实装。上线前将另行发布完整的《远程服务隐私政策》及数据存储细则。

服务器管理员（服主）有义务在服务器规则中告知玩家上述数据收集范围，并对本地存储的玩家数据安全负责，不得将玩家数据用于服务器运营之外的任何目的。以上义务的履行主体为服务器管理员而非本模组作者，作者对此不承担任何责任。

### 8. 法律适用与管辖

本作品及本声明的解释、效力及争议解决，适用中华人民共和国法律。因本作品产生的争议，双方应友好协商解决；协商不成的，任何一方可向创作者所在地有管辖权的人民法院提起诉讼。

本作品以 Apache License 2.0 为主要开源许可协议。如 Apache-2.0 条款与中华人民共和国法律存在无法调和的冲突，以 Apache-2.0 条款为准，中华人民共和国法律的强制性规定在适用范围内予以尊重。

### 9. 第三方开源组件

| 组件 | 许可协议 | 用途 |
| --- | --- | --- |
| Gson | Apache License 2.0 | JSON 序列化与反序列化 |
| Minecraft Forge | LGPL-2.1 | 模组加载框架 |
| Java SE 17 | Oracle Binary Code License | 运行环境 |

上述组件的完整许可文本可在其各自的官方仓库中获取。本作品通过 Gradle 依赖管理引入上述组件，不包含其源代码副本。

---

九氏管理面板 · MA Admin Panel
Minecraft 1.20.1 Forge 47.3.0 · © 2026 MA 保留所有权利

---

# English Version

## Overview

Jiushi Admin Panel is an all-in-one management solution for Minecraft Forge 1.20.1 servers, consisting of one core panel plus two DLC addons. All features are integrated into a single GUI, opened with the `J` key — no commands to memorize.

**Core positioning**: an out-of-the-box admin toolbox for small/medium private servers, replacing multi-mod stacks such as FTB Chunks + FTB Teams + Economy + SignShop.

### Mod Composition

| Mod | JAR File | Version | Size | Type |
| --- | --- | --- | --- | --- |
| Core Panel | `_jiushi_admin-1.20.1-1.0.14-alpha.jar` | 1.0.14-alpha | ~97 KB | Required |
| Friends | `jiushi_friends-0.1.1-alpha.jar` | 0.1.1-alpha | ~24 KB | DLC |
| Territory | `jiushi_territory-0.1.2-alpha.jar` | 0.1.2-alpha | ~45 KB | DLC |

### Dependency Graph

```
_jiushi_admin (Core Panel · Required)
├── Depends: Forge 47+ · MC 1.20.1
├── Exports: AddonRegistry API
├── jiushi_friends (Friends Addon · DLC)
│   └── hard dependency: jiushi_admin >= 1.0.5-alpha
└── jiushi_territory (Territory Addon · DLC)
    └── hard dependency: jiushi_admin >= 1.0.5-alpha
```

### Installation & Quick Start

1. **Environment**: Minecraft 1.20.1 · Forge 47.3.0+ · Java 17
2. **Install JARs**: put the core panel JAR into the `mods/` folder of both the server and the client; add the DLC JARs as well if you use them
3. **Start the server**: the config directory `config/jiushi_admin/` is created automatically on first launch
4. **Initialize the owner**: the first player with OP permissions automatically becomes the server owner when joining — no configuration required
5. **Add admins**: on the ADMIN tab, the owner types a player name (or clicks an online player) to generate an 8-character invite code; the target player enters the code in the panel to become an admin (OP is granted automatically)
6. **Open the panel**: press `J` — everything is done in the single GUI

> Installing only the core panel gives you management / economy / shop / teleport features. Friends and Territory are optional DLCs — just drop them into `mods/`; the EXTENSIONS tab picks them up automatically.

### Config & Data Files

All data lives in `config/jiushi_admin/` as UTF-8 JSON files. Server owners may edit them manually (best while the server is stopped):

| File | Contents | Notes |
| --- | --- | --- |
| `setup.json` | Admin roster & roles (owner/admin/developer) + pending invite codes | Invite codes stored as SHA-256 hashes only, never plaintext |
| `permissions.json` | Fine-grained permissions (player → permission → boolean) | Usually managed via `/admin perm` |
| `shop.json` | Shop listings + auto-increment ID | Items stored as NBT, preserving enchantments/names/durability |
| `warps.json` | Warp list (position / dimension / visibility / creator) | Cross-dimension teleport supported |
| `bans.json` | Name-based ban store (reason + expiry) | Works alongside the vanilla ban list |
| `vouchers.json` | Valid voucher hashes → amounts | Vouchers remain valid after server restart |
| `friends.json` / `pending_requests.json` | Friend relations & pending requests (DLC) | Stored bidirectionally; player names matched case-insensitively |
| `territories.json` | Territory data: coords / whitelist / type (DLC) | Auto-increment ID; Y axis not restricted |

> Economy data is NOT stored here: coins live in the vanilla scoreboard (`JiuShi_money` objective) and are saved with the world (level.dat).

---

## 1. Management Features (Core Panel)

### 1.1 Admin Role Hierarchy

| Role | Tag | How to obtain | Permissions |
| --- | --- | --- | --- |
| Owner | Gold | First OP automatically; invite-code grant | Everything · immune to kick/ban · manage other admins |
| Developer | Red | Authorized access (Alpha/Beta debugging only) | Same as owner · used for issue diagnosis |
| Admin (OP) | Blue | Owner invite code · fine-grained permissions | Broadcast · kick/ban · shop management · teleport management |
| Regular player | Cyan | Default | Shop buy/sell · teleport · TPA · territory |

> **Developer role note**: `developer` is a debug permission unit reserved during Alpha/Beta testing, with permissions equivalent to owner. It allows the mod author to log in and directly diagnose bugs, verify fixes, or intervene in emergencies when the server owner is offline. Currently uses a fixed verification credential (server-side validated, unforgeable); the Beta phase will upgrade to a **dynamic 8-character, 5-minute one-time verification code**, ensuring only the author can obtain access.
>
> Whether this role stays in the release version **depends on community acceptance during Alpha/Beta**. If server owners find it useful (easier troubleshooting, reduced maintenance burden), it will be kept in the release. If widely opposed, it will be removed at release, at which point existing developer accounts will be downgraded to regular players.

### 1.2 Invite Code System

- The owner generates an 8-character secure random code bound to a target player, valid for 5 minutes
- 5 wrong attempts lock the verifier for 5 minutes
- Developer verification uses a separate channel; Beta phase will upgrade to a dynamic 8-character, 5-minute one-time code
- Successful verification grants OP automatically; the panel refreshes instantly

### 1.3 Broadcast & Announcements

- **Instant server-wide broadcast**: yellow text, visible to everyone
- **Timed rotating announcements**: green text, configurable interval in seconds, start/stop anytime

### 1.4 Kick & Ban

- **Kick**: instant disconnect with an optional reason
- **Ban**: fine-grained temporary bans (years/months/hours/minutes) + permanent ban
- **Immunity**: owner and developer are immune to both kick and ban
- **Dual ban lists**: bans are written to both the vanilla ban list and the name-based ban store (`bans.json`), so online-mode servers correctly block players who have never logged in before
- **Operation logs**: written server-side

---

## 2. Economy & Shop (Core Panel)

### 2.1 Coin System

Built on the vanilla Scoreboard — zero extra database. The `JiuShi_money` objective is created automatically; balances are shown when players join.

| Feature | Description |
| --- | --- |
| Balance check | shown in the SHOP tab |
| Player transfer | 3-step flow: pick target → preset/custom amount → confirm. Both sides are notified |
| Admin top-up | one-click +10 / +100 / +1K / +10K buttons in the shop |
| Offline transfer | funds written to the scoreboard, applied on the target's next login |

### 2.2 Player Shop

- **List**: hold an item + enter a price → the item leaves your hand and appears in the shop
- **Buy**: click Buy → payment deducted and forwarded to the seller → item goes to inventory (dropped if full)
- **Delist**: sellers can delist their own items; admins can delist anything
- Sold-out listings are removed automatically; items are stored as NBT, keeping full enchantments/names/durability
- Persisted in `config/jiushi_admin/shop.json`

### 2.3 Voucher System

- **Create**: enter an amount in the panel → a paper voucher is generated, amount deducted from balance
- **Redeem**: right-click the paper voucher → equal coins credited
- **Anti-forgery**: every voucher is identified by a SHA-256 hash and validated server-side
- **Use cases**: event rewards, offline distribution, player-to-player trades

---

## 3. Teleport System (Core Panel)

### 3.1 Warps

| Type | Create | Visible to | Overwrite |
| --- | --- | --- | --- |
| Private | All players | Creator only | Creator |
| Public | All players | Everyone | Creator |
| Official | Admins only | Everyone | Admins only |

- Warps store position + look direction + dimension; cross-dimension teleport supported
- Deletion requires a second confirmation click

### 3.2 TPA Requests

- Pick an online player in the panel → send a request → the target replies with `/tpa accept` or `/tpa deny`
- Dual entry: GUI and command line
- Pending requests are cleaned up automatically when players log off

---

## 4. DLC Extension API

The core panel exposes the public `AddonRegistry` API. Third-party mods can register an `AddonEntry` to hook their own GUI into the panel's EXTENSIONS tab:

```java
// Register an addon in a third-party mod
AddonRegistry.register(new AddonEntry(
    "my_addon_id",             // unique identifier
    "My Addon",                // button label in the tab
    () -> Minecraft.getInstance()
               .setScreen(new MyScreen())   // screen opened on click
));
```

> Both Friends and Territory plug in through this API. The panel scans and displays all registered addons on startup.

---

## 5. Friends System (DLC)

| Feature | Action |
| --- | --- |
| Add friend | enter a player name → send a request → accept/deny |
| Friend list | all friends · green dot online · grey circle offline |
| Remove friend | click ❌, removed on both sides |
| Online notify | broadcast `§a[好友] xxx 上线了` to all contacts |
| Offline notify | broadcast `§7[好友] xxx 下线了` |
| Quick DM | ✉ button next to online friends → pre-fills the `/msg` command |

Friend data is stored bidirectionally in `config/jiushi_admin/friends.json` and restored automatically after a server restart.

---

## 6. Territory Addon (DLC)

### 6.1 Territory Rules

| Property | Private Territory | Official Territory |
| --- | --- | --- |
| Creator | All players | Admins only |
| Max count | 2 per player | Unlimited |
| Size limit | XZ span sum ≤ 128 | Unlimited |
| Overlap check | auto on create, conflicts rejected | auto on create, conflicts rejected |
| Intruder behavior | blocks break/place/interact | blocks break/place/interact + forced Adventure mode |
| Whitelist | owner can allow players | owner can allow players |
| Admin power | owner server-owner can delete any territory · all ops ignore blocking | owner server-owner can delete any territory · all ops ignore blocking |

### 6.2 Selection Workflow

1. **Start selection**: click "Start Selection" in the create screen → GUI closes, back to the world
2. **Set points**: sneak (Shift) + left-click a block for the first point; sneak + left-click again for the second point (XZ span auto-computed)
3. **Cancel selection**: sneak + right-click
4. **Create territory**: return to the GUI → enter a name → choose "Private" or "Official" → create

### 6.3 Protection Scope

Block breaking · block placing · right-click interaction · fluid spread · piston push-in · explosion damage are all blocked; Adventure mode is forced; whitelisted players and server owner/OPs are exempt.

### 6.4 Territory Management

- The manage screen lists all territories: name, owner, type
- Players with management rights can delete via the ❌ button
- Persisted in `config/jiushi_admin/territories.json`

---

## 7. Performance Overview

| Metric | Data |
| --- | --- |
| Memory | all data < 5 MB |
| CPU per tick | territory checks ≈ 0.05 ms per player (with 200 territories) |
| Network | ~2-5 KB JSON per panel open |
| Disk I/O | synchronous JSON writes only on admin operations |
| Recommended setup | 4 cores / 4 GB RAM serves 20 players unnoticeably |

---

## 8. Technical Architecture

| Component | Choice |
| --- | --- |
| Mod loader | Forge 47.3.0 · Java 17 |
| Networking | Forge SimpleChannel · 4 independent channels · protocol version check |
| Persistence | Gson JSON · `config/jiushi_admin/` |
| Economy | vanilla Scoreboard (no database) |
| Cryptography | SHA-256 + salt · SecureRandom |
| Extension mechanism | in-memory static registry · AddonRegistry API |
| Territory rendering | RenderLevelStageEvent · no Mixin dependency |

---

## 9. Command Reference

| Command | Permission | Description |
| --- | --- | --- |
| `/tpa accept` | All players | Accept an incoming teleport request |
| `/tpa deny` | All players | Deny an incoming teleport request |
| `/admin add <player>` | Owner | Directly add an admin (grants OP) |
| `/admin remove <player>` | Owner | Remove an admin |
| `/admin list` | All players | List all admins |
| `/admin perm <player> <perm> <true/false>` | Owner | Set fine-grained admin permissions |

> All other features are operated through the panel GUI (`J` key).

---

## 10. Known Limitations & Notes

- **Alpha stage**: features are still iterating; back up your world before heavy use
- **Territory protection ignores the Y axis**: XZ-plane only, covering the full height (-64 to 320); cannot be split by layer
- **Fluid blocking is approximate**: fluid passes if an authorized player is within 8 blocks, so false-positives are possible in crowded areas
- **Territory names are case-insensitive**: `MyLand` and `myland` conflict
- **Warp name conflicts**: only the warp's owner or an admin may overwrite an existing warp
- **Keep vouchers safe**: voucher credentials live in the server's `vouchers.json`; deleting config invalidates old vouchers
- **Admin matching is case-insensitive**: `Steve` and `steve` are treated as the same admin
- **DLC version matching**: Friends/Territory require the core panel ≥ 1.0.5-alpha; Forge refuses to load on mismatch

---

## 11. Development & Build

The repository contains three independent Gradle sub-projects:

| Project | Directory | Artifact |
| --- | --- | --- |
| Core panel | `admin-mod/` | `_jiushi_admin-1.20.1-<version>.jar` |
| Friends | `jiushi_friends/` | `jiushi_friends-<version>.jar` |
| Territory | `jiushi_territory/` | `jiushi_territory-<version>.jar` |

Build requirements: **JDK 17** · first build downloads ForgeGradle dependencies (a mirror such as Tencent Cloud is recommended in China).

```bash
cd admin-mod
gradlew build       # artifacts land in build/libs/, already reobfuscated
```

> When releasing, update the version in three places: `build.gradle`, `src/main/resources/META-INF/mods.toml`, and the "Mod Composition" table in this document.

---

## 12. Roadmap

**Beta**

- Developer dynamic verification code (8-char, 5-minute one-time)
- Fine-grained territory permissions (containers/switches/redstone)
- Friend TPA
- Performance optimization
- Territory protection completion (fluid/piston/explosion interception)
- Shop pagination loading

**Release**

- Decide developer role fate based on community feedback
- Web-based online admin panel
- Mobile remote-control app
- Paid DLC store

**Long term**

- Fabric port
- Multi-version support
- Internationalization (i18n)

---

## 13. License & Legal

### 13.1 Copyright

The copyright (and related intellectual property rights) of this mod (Jiushi Admin Panel and all DLC addons, collectively "this Work"), including source code, design, icons, documentation and all components, belongs exclusively to the creator MA, and is protected by the Copyright Law of the People's Republic of China, the Regulations for the Protection of Computer Software and relevant international treaties.

### 13.2 Open Source License (Apache License 2.0)

This Work is released under the Apache License 2.0, which grants anyone who obtains a copy of this Work the right to:

- freely use, copy, modify, merge, publish, distribute, sublicense and/or sell copies of the Work
- combine or embed the Work with other software or derivative products
- use the Work for commercial purposes, subject to the license conditions

> **License conditions**: every copy or substantial use must include the full Apache-2.0 license notice and the original copyright notice; modified files must indicate changes. The full license text is available at apache.org/licenses/LICENSE-2.0.

### 13.3 Trademark Reservation

The Apache License 2.0 does not grant any trademark, trade-dress or brand rights. Specifically:

- "九氏", "MA", "JiuShi", "九氏面板", "九氏管理面板", "Jiushi Admin Panel" and related marks of this Work are unregistered trademarks or commercial identifiers of the creator MA
- No party may imply or claim that their derivative works originate from MA or are endorsed by MA when distributing, modifying or developing derivative projects based on this Work
- Written authorization from the creator is required to use the above trademarks or identifiers
- This clause is independent of the Apache-2.0 license: even though this Work is released under that license, the trademarks and identifiers remain exclusively owned by MA

### 13.4 DLC Addon Statement

The core panel and all DLC addons (Friends, Territory, etc.) are released free of charge under Apache-2.0 — both source code and pre-compiled JARs are distributed at no cost and may be freely obtained and used by anyone.

### 13.5 Alpha Testing Disclaimer

> This Work is currently in **Alpha testing stage**, intended for feature preview and early testing only. Any individual or organization that downloads, installs or runs this Work acknowledges having read and understood all terms of this disclaimer, is aware of the risks of data loss, service interruption, world corruption, compatibility conflicts and other unforeseeable anomalies in Alpha software, and voluntarily assumes all consequences.

### 13.6 Mojang EULA Compliance

- This mod is a third-party mod for Minecraft: Java Edition, compliant with Mojang Studios' End User License Agreement (EULA) and the Minecraft Usage Guidelines
- This mod contains no code, assets, textures, models or audio from Minecraft itself
- This is an original code work, not a "modified version" of Minecraft, and does not modify or bypass Mojang's authentication system
- This mod and its author have no affiliation with Mojang AB, Microsoft Corporation or NetEase, and are not officially recognized, sponsored or endorsed by them
- Users must purchase the legitimate Minecraft: Java Edition to use this mod

### 13.7 User Data & Privacy

#### 13.7.1 Local Panel Data

This mod (Jiushi Admin Panel and all DLC addons) stores the following player data locally on the server, exclusively in the server config directory (`config/jiushi_admin/`), and never proactively uploads it to any third-party server:

| Data type | Storage location |
| --- | --- |
| Admin roster and roles | `setup.json` |
| Player economy data | Minecraft vanilla scoreboard |
| Shop transaction records | `shop.json` |
| Friend relations | `friends.json` |
| Territory coordinates and whitelists | `territories.json` |
| Ban records | Minecraft vanilla ban system |

#### 13.7.2 Optional Remote Management Service (Beta Roadmap)

A future Beta release will offer an optional subscription service, "Jiushi Remote Management", allowing server owners to access their servers remotely via mobile or web (issuing commands, viewing status, backing up saves, etc.). This service requires the server owner to actively subscribe and deploy a companion mod, at which point certain data (e.g., backup saves) will be actively uploaded by the server owner to cloud servers leased by the author.

**This service is entirely optional**: server owners may use all local panel features in full without subscribing. A subscription merely represents the server owner's voluntary decision to commission the author for cloud infrastructure hosting; data uploads result from the server owner's own choice, and corresponding data security responsibilities are shared between the cloud service provider and the server owner.

This service is currently in the planning stage and has not yet been implemented. A complete Remote Service Privacy Policy and data storage details will be published separately before launch.

Server administrators are obliged to inform players of the above data collection scope in the server rules, and are responsible for the security of locally stored player data. These obligations are borne by the server administrator, not the mod author; the author accepts no liability in this regard.

### 13.8 Governing Law & Jurisdiction

The interpretation, validity and dispute resolution of this Work and this notice are governed by the laws of the People's Republic of China. Disputes arising from this Work shall be resolved through friendly negotiation; if negotiation fails, either party may file a lawsuit with a court of competent jurisdiction at the creator's location.

This Work is primarily licensed under the Apache License 2.0. In the event of an irreconcilable conflict between Apache-2.0 and mandatory provisions of PRC law, the Apache-2.0 terms shall prevail, while the mandatory provisions of PRC law shall be respected within their applicable scope.

### 13.9 Third-Party Open Source Components

| Component | License | Purpose |
| --- | --- | --- |
| Gson | Apache License 2.0 | JSON serialization & deserialization |
| Minecraft Forge | LGPL-2.1 | Mod loading framework |
| Java SE 17 | Oracle Binary Code License | Runtime environment |

Full license texts of the above components are available in their respective official repositories. This Work introduces these components via Gradle dependency management and does not include copies of their source code.

---

## Changelog

See [CHANGELOG.md](./CHANGELOG.md) for the full update history.

---

Jiushi Admin Panel · MA Admin Panel
Minecraft 1.20.1 Forge 47.3.0 · © 2026 MA. All rights reserved.
