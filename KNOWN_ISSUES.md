# 已知问题 · Known Issues

本文档记录已发现但尚未修复的问题，按严重程度分类。修复后移入 [CHANGELOG.md](./CHANGELOG.md)。

> 最后更新: 2026-08-05 · 基于 v1.0.9-alpha 审计

---

## 高严重度

### [ISSUE-001] 购买与扣款非原子操作，可致透支

**文件**: `admin-mod/.../server/MoneyManager.java:60-70` / `ShopManager.java:117-131`

**描述**: `purchaseItem()` 先调用 `getMoney()` 检查余额，中间经过 NBT 解析，再调用 `takeMoney()` 扣款。两笔并发购买可在余额检查通过后同时扣款，导致余额为负或卖家未收款。

**影响**: 经济系统完整性受损，玩家可透支购买。

---

### [ISSUE-002] 金币余额 int 加法溢出

**文件**: `admin-mod/.../server/MoneyManager.java:63,70,82`

**描述**: `Math.max(0, current + amount)` 使用 `int` 加法。若余额加金额超 `Integer.MAX_VALUE`(~21 亿)，结果回绕为负数，`Math.max(0, negative)` 返回 0，余额被清零。

**影响**: 大额金币操作可能导致经济数据丢失。

---

### [ISSUE-003] 客户端商店列表跨线程读写

**文件**: `admin-mod/.../network/ShopPacket.java:159-167`

**描述**: 网络线程执行 `pendingShopListings.addAll()`，渲染线程通过 `MainScreen.tick()` 读取同一列表。`shopDataReady` 是 `volatile`，但列表本身的 `addAll` 未被同步，可能抛 `ConcurrentModificationException`。

**影响**: 客户端渲染崩溃或商店数据显示错乱。

---

### [ISSUE-004] 封禁时长 int 溢出

**文件**: `admin-mod/.../client/MainScreen.java:311`

**描述**: `(y * 525600 + m * 43200 + h * 60 + min)` 全为 `int` 乘法，年份 ≥4089 时溢出为负数。负值经 `interval * 60000L` 在服务端产生过去的过期时间戳，导致"限时封禁"实际变为永久。

**影响**: 管理员意图临时封禁却造成永久封禁。

---

## 中严重度

### [ISSUE-005] ADD 价格 ≤0 时错误提示不送达

**文件**: `admin-mod/.../network/ShopPacket.java:228-231`

**描述**: 上架物品时若价格 ≤0，设置 `pendingStatusMessages` 后直接 `break` 退出 switch，未调用 `buildAndSendListResponse`，错误提示无法到达客户端 UI。

**影响**: 价格无效时玩家看不到任何反馈。

---

### [ISSUE-006] 私人领地计数大小写敏感

**文件**: `jiushi_territory/.../server/TerritoryManager.java:106-107`

**描述**: `t.owner.equals(owner)` 精确匹配大小写。若玩家改名后大小写不同，可绕过每人 2 个的上限。

**影响**: 领地数量限制可能被绕过。

---

### [ISSUE-007] TerritoryManager 加载时 nextId 为 null 导致 NPE

**文件**: `jiushi_territory/.../server/TerritoryManager.java:228`

**描述**: JSON 中 `nextId` 字段存在但值为 `null` 时，`((Number) null).intValue()` 抛出 NPE。外层 catch 捕获后所有已加载的领地数据丢失。

**影响**: 配置文件损坏时全部领地数据丢失，需手动恢复 JSON。

---

### [ISSUE-008] 邀请码验证并发竞态

**文件**: `admin-mod/.../server/SetupManager.java:153-175`

**描述**: `verifyInviteCode()` 在 `synchronized(attempts)` 内检查 `isAdmin`，但 `admins.put()` 也在同一锁内。另一线程通过 `directAddAdmin` 可绕过检查，导致重复添加或邀请码浪费。

**影响**: 极端并发场景下邀请码被浪费或管理员重复添加。

---

### [ISSUE-009] Inventory.add() 部分放入后仍执行 drop

**文件**: `admin-mod/.../server/ShopManager.java:123`

**描述**: `buyer.getInventory().add(stack)` 返回 `false` 时表示有物品未能放入，但可能已部分放入。随后 `buyer.drop(stack, false)` 将**完整堆叠**丢出，实际获得物品多于应得。

**影响**: 背包空间边界情况可能复制物品。

---

### [ISSUE-010] 领地传送点跨维度渲染高度硬编码

**文件**: `jiushi_territory/.../event/TerritoryRenderEvents.java:65`

**描述**: `AABB(minX, -64, minZ, maxX+1, 320, maxZ+1)` 硬编码主世界高度。地狱 (0~128) 和自定义维度的领地边框渲染不正确。

**影响**: 非主世界领地边框视觉效果错误。

---

### [ISSUE-011] clipText 无 null 保护

**文件**: `admin-mod/.../client/MainScreen.java:839`

**描述**: `clipText(text, maxPixels)` 直接调用 `font.width(text)` 而未检查 `text` 是否为 null。若商店商品名异常为 null，渲染线程 NPE。

**影响**: 异常商品数据导致面板渲染崩溃。

---

### [ISSUE-012] 死亡重生后冒险模式恢复值过期

**文件**: `jiushi_territory/.../event/TerritoryEvents.java:205-207`

**描述**: 玩家进入官方领地时记录原始 gamemode。若玩家期间死亡重生（可能改变 gamemode），离开领地时恢复的是旧值而非当前实际模式。

**影响**: 极端情况下玩家 gamemode 被错误覆盖。

---

## 低严重度

### [ISSUE-013] 重复封禁产生重复条目

**文件**: `admin-mod/.../network/AdminPacket.java:130`

**描述**: 对已封禁玩家再次封禁时，`getBans().add()` 追加新条目而非替换，封禁列表产生重复。

---

### [ISSUE-014] BanManager null 键存储

**文件**: `admin-mod/.../server/BanManager.java:91-92`

**描述**: `normalize(null)` 返回空字符串 `""`，导致封禁记录存储在无法查找的键下。

---

### [ISSUE-015] TerritoryScreen 坐标解析缺长度校验

**文件**: `jiushi_territory/.../client/TerritoryScreen.java:229`

**描述**: `parts[2].split(",")` 未校验结果长度，畸形数据包可抛 `ArrayIndexOutOfBoundsException`。

---

### [ISSUE-016] deleteTerritory 所有者匹配大小写敏感

**文件**: `jiushi_territory/.../server/TerritoryManager.java:137`

**描述**: `t.owner.equals(name)` 精确匹配大小写，与管理员识别 `equalsIgnoreCase` 口径不一致。

---

### [ISSUE-017] 领地查询 O(n) 线性扫描

**文件**: `jiushi_territory/.../server/TerritoryManager.java:160-165`

**描述**: 每次方块交互都线性遍历全部领地做 AABB 检测。领地数量多时成为性能瓶颈。

---

### [ISSUE-018] 存盘失败静默无告警

**涉及文件**: WarpManager, ShopManager, SetupManager, VoucherManager, BanManager, PermissionManager, FriendManager, TerritoryManager

**描述**: 所有 `save()` 方法在磁盘写入失败时仅打印日志，不通知管理员。服务端继续运行，内存修改重启后丢失。

---

### [ISSUE-019] 好友请求并发竞态

**文件**: `jiushi_friends/.../server/FriendManager.java:57-63`

**描述**: `isFriend` 检查与 `pending.add` 之间无原子性保护，两个玩家可能同时向对方发送请求并同时接受。

---

### [ISSUE-020] 接受好友请求触发两次存盘

**文件**: `jiushi_friends/.../server/FriendManager.java:76-78`

**描述**: `acceptRequest` 调用两次 `addFriend`（双向），每次内部调用 `save()`，同一操作写盘两次。

---

### [ISSUE-021] mods.toml 版本范围无上限

**涉及文件**: 全部 3 个 `mods.toml`

**描述**: 依赖版本声明全部使用 `[47,)` / `[1.0.5-alpha,)` 等无上限范围。未来 Forge 大版本或管理面板破坏性更新时不会阻止加载。

---

### [ISSUE-022] Territory 主类命名误导

**文件**: `jiushi_territory/.../ShopAddonMod.java`

**描述**: 领地扩展模组的主类名为 `ShopAddonMod`，与商店无关，易混淆。

---

### [ISSUE-023] 商店大 NBT 物品可能超出封包限制

**文件**: `admin-mod/.../server/ShopManager.java:58-61`

**描述**: 完整 NBT 序列化后通过网络包发送。嵌套容器（潜影盒套潜影盒）可能产生 MB 级数据，超出 Forge 封包上限导致客户端被踢。

---

### [ISSUE-024] MoneyManager 各方法独立加锁但复合操作不受保护

**文件**: `admin-mod/.../server/MoneyManager.java:32,60,67`

**描述**: `getMoney`、`addMoney`、`takeMoney` 各自独立 `synchronized`，但组合操作（查余额→扣款）跨越两次加锁，中间状态可能被其他线程修改。

