package com.jiushi.friends.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 好友数据管理器
 * <p>
 * 数据结构:
 * <ul>
 *   <li><b>friends</b>: 玩家名(小写) → 好友名集合(小写) (双向存储)</li>
 *   <li><b>pending</b>: 目标玩家名(小写) → 待处理请求发送者集合(小写)</li>
 * </ul>
 * <p>
 * 大小写策略: 所有键与集合元素统一存储为小写, 避免 "Steve" 与 "steve" 被视为两个不同玩家.
 * 显示时通过 {@link #getFriendName} 从在线玩家列表还原真实大小写.
 * <p>
 * 数据持久化到两文件: friends.json 和 pending_requests.json
 * <p>
 * 所有操作都是线程安全的 (ConcurrentHashMap + ConcurrentHashMap.newKeySet)
 */
public class FriendManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(FriendManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** 好友关系: 玩家(小写) → 好友列表(小写) */
    private static final Map<String, Set<String>> friends = new ConcurrentHashMap<>();
    /** 待处理请求: 目标玩家(小写) → 请求者集合(小写) */
    private static final Map<String, Set<String>> pending = new ConcurrentHashMap<>();
    /** 配置目录 (复用 admin 的 config/jiushi_admin/) */
    private static Path configDir;

    /** 初始化: 设置配置目录并加载数据 */
    public static void init() {
        configDir = FMLPaths.CONFIGDIR.get().resolve("jiushi_admin");
        load();       // 加载好友关系
        loadPending();// 加载待处理请求
    }

    /**
     * 发送好友请求
     * @return 0=成功, 1=不能添加自己, 2=已经是好友, 3=你已经发送过请求, 4=对方已发送请求给你
     */
    public static int sendRequest(String from, String target) {
        from = norm(from);
        target = norm(target);
        if (from.equals(target)) return 1; // 不能加自己 (小写比较)
        if (isFriend(from, target)) return 2; // 已经是好友
        // 检查对方是否已经向自己发送了请求 (只读, 不创建新条目)
        Set<String> existingFromTarget = pending.get(from);
        if (existingFromTarget != null && existingFromTarget.contains(target)) return 4;
        // 检查自己是否已经向对方发送了请求
        Set<String> pendingList = pending.computeIfAbsent(target, k -> ConcurrentHashMap.newKeySet());
        if (pendingList.contains(from)) return 3;
        pendingList.add(from);
        savePending();
        return 0;
    }

    /** 接受好友请求: 移除pending → 双向添加好友 → 保存 */
    public static boolean acceptRequest(String player, String from) {
        player = norm(player);
        from = norm(from);
        Set<String> pendingList = pending.get(player);
        if (pendingList == null || !pendingList.contains(from)) return false;
        pendingList.remove(from);
        addFriend(player, from); // 双向添加
        addFriend(from, player);
        savePending();
        return true;
    }

    /** 拒绝好友请求: 仅清理pending, 不加好友 */
    public static boolean denyRequest(String player, String from) {
        player = norm(player);
        from = norm(from);
        Set<String> pendingList = pending.get(player);
        if (pendingList == null || !pendingList.contains(from)) return false;
        pendingList.remove(from);
        savePending();
        return true;
    }

    /** 获取指定玩家的待处理请求列表 (小写, 只读) */
    public static Set<String> getPendingRequests(String player) {
        Set<String> list = pending.get(norm(player));
        return list != null ? Collections.unmodifiableSet(list) : Collections.emptySet();
    }

    /** 检查指定玩家是否有待处理请求 */
    public static boolean hasPendingRequest(String player) {
        Set<String> list = pending.get(norm(player));
        return list != null && !list.isEmpty();
    }

    /** 内部双向添加好友 (统一小写, 自动去重) */
    private static void addFriend(String player, String target) {
        player = norm(player);
        target = norm(target);
        if (player.equals(target)) return;
        Set<String> list = friends.computeIfAbsent(player, k -> ConcurrentHashMap.newKeySet());
        if (list.contains(target)) return;
        list.add(target);
        save();
    }

    /** 删除好友: 双向移除 (小写匹配) */
    public static boolean removeFriend(String player, String target) {
        player = norm(player);
        target = norm(target);
        Set<String> list = friends.get(player);
        if (list == null || !list.contains(target)) return false;
        list.remove(target);
        Set<String> targetList = friends.get(target);
        if (targetList != null) targetList.remove(player);
        save();
        return true;
    }

    /** 获取指定玩家的好友列表 (小写, 只读) */
    public static Set<String> getFriends(String player) {
        Set<String> list = friends.get(norm(player));
        return list != null ? Collections.unmodifiableSet(list) : Collections.emptySet();
    }

    /** 判断二人是否为好友 (小写匹配) */
    public static boolean isFriend(String player, String target) {
        Set<String> list = friends.get(norm(player));
        return list != null && list.contains(norm(target));
    }

    /**
     * 将小写好友名还原为在线玩家的真实大小写 (用于UI显示).
     * 若不在线则返回原小写名.
     */
    public static String getFriendName(String name) {
        if (name == null) return "";
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (var p : server.getPlayerList().getPlayers()) {
                if (p.getGameProfile().getName().equalsIgnoreCase(name)) {
                    return p.getGameProfile().getName(); // 真实大小写
                }
            }
        }
        return name;
    }

    /** 统一小写格式 */
    private static String norm(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    /** 保存好友关系到 friends.json */
    private static void save() {
        if (configDir == null) return;
        try {
            File file = configDir.resolve("friends.json").toFile();
            file.getParentFile().mkdirs();
            Map<String, List<String>> data = new HashMap<>();
            for (var e : friends.entrySet()) {
                data.put(e.getKey(), new ArrayList<>(e.getValue()));
            }
            try (Writer w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(data, w);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save friends", e);
        }
    }

    /** 从 friends.json 加载好友关系 */
    private static void load() {
        if (configDir == null) return;
        try {
            File file = configDir.resolve("friends.json").toFile();
            if (!file.exists()) return;
            try (Reader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                Map<String, List<String>> data = GSON.fromJson(r,
                        new TypeToken<Map<String, List<String>>>() {}.getType());
                if (data != null) {
                    friends.clear();
                    for (var e : data.entrySet()) {
                        friends.put(norm(e.getKey()), ConcurrentHashMap.newKeySet());
                        for (String v : e.getValue()) {
                            friends.get(norm(e.getKey())).add(norm(v));
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load friends", e);
        }
    }

    /** 保存待处理请求到 pending_requests.json */
    private static void savePending() {
        if (configDir == null) return;
        try {
            File file = configDir.resolve("pending_requests.json").toFile();
            file.getParentFile().mkdirs();
            Map<String, List<String>> data = new HashMap<>();
            for (var e : pending.entrySet()) {
                data.put(e.getKey(), new ArrayList<>(e.getValue()));
            }
            try (Writer w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(data, w);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save pending requests", e);
        }
    }

    /** 从 pending_requests.json 加载待处理请求 */
    private static void loadPending() {
        if (configDir == null) return;
        try {
            File file = configDir.resolve("pending_requests.json").toFile();
            if (!file.exists()) return;
            try (Reader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                Map<String, List<String>> data = GSON.fromJson(r,
                        new TypeToken<Map<String, List<String>>>() {}.getType());
                if (data != null) {
                    pending.clear();
                    for (var e : data.entrySet()) {
                        pending.put(norm(e.getKey()), ConcurrentHashMap.newKeySet());
                        for (String v : e.getValue()) {
                            pending.get(norm(e.getKey())).add(norm(v));
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load pending requests", e);
        }
    }

    /**
     * 玩家离开时清理所有相关引用:
     * 1. 移除该玩家自己的好友列表和待处理请求
     * 2. 从所有其他玩家的好友列表和待处理请求中移除该玩家
     */
    public static void removeAllReferences(String player) {
        player = norm(player);
        friends.remove(player);
        for (var list : friends.values()) {
            list.remove(player);
        }
        pending.remove(player);
        for (var list : pending.values()) {
            list.remove(player);
        }
        save();
        savePending();
    }
}
