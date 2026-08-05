package com.jiushi.adminpanel.server;

import com.jiushi.adminpanel.AdminMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 名字封禁管理器
 * <p>
 * 解决在线模式(online-mode)下"离线玩家封禁无效"的问题:
 * 原版封禁列表存储 Mojang UUID, 对从未上线过的玩家只能用 Offline UUID 占位, 永远匹配不上.
 * 本管理器以玩家名为唯一键存储封禁记录, 玩家登录时(已解析出真实名字)主动校验, 与 UUID 无关.
 * <p>
 * 数据持久化到 bans.json
 */
public class BanManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** 封禁记录: 玩家名(小写) → 封禁信息 (线程安全) */
    private static final Map<String, BanEntry> bans = new ConcurrentHashMap<>();
    private static Path configDir;

    /** 封禁信息: 原因 + 过期时间戳 (0=永久) */
    public static class BanEntry {
        public String reason;
        public long expiry; // 0 = 永久

        public BanEntry() {}

        public BanEntry(String reason, long expiry) {
            this.reason = reason;
            this.expiry = expiry;
        }
    }

    /** 初始化并加载已有数据 */
    public static void init(Path path) {
        configDir = path;
        load();
    }

    /**
     * 封禁玩家 (按名字, 不区分大小写)
     * @param name   玩家名
     * @param reason 封禁原因
     * @param expiryMs 过期时间戳(毫秒), 0=永久
     */
    public static void ban(String name, String reason, long expiryMs) {
        if (name == null || name.isEmpty()) return;
        bans.put(normalize(name), new BanEntry(reason, expiryMs));
        save();
    }

    /**
     * 判断玩家是否被封禁 (自动清理已过期的记录)
     * @return 封禁信息; 未封禁返回 null
     */
    public static BanEntry isBanned(String name) {
        if (name == null || name.isEmpty()) return null;
        BanEntry entry = bans.get(normalize(name));
        if (entry == null) return null;
        // 过期自动清理
        if (entry.expiry > 0 && entry.expiry < System.currentTimeMillis()) {
            bans.remove(normalize(name));
            save();
            return null;
        }
        return entry;
    }

    /** 解除封禁 */
    public static boolean unban(String name) {
        boolean removed = bans.remove(normalize(name)) != null;
        if (removed) save();
        return removed;
    }

    /** 获取全部封禁记录 (只读副本) */
    public static Map<String, BanEntry> getAllBans() {
        return new HashMap<>(bans);
    }

    /** 统一名字格式: 小写去空格 */
    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }

    /** 保存到 bans.json */
    private static synchronized void save() {
        if (configDir == null) return;
        try {
            File file = configDir.resolve("bans.json").toFile();
            file.getParentFile().mkdirs();
            Map<String, Object> data = new HashMap<>();
            data.put("bans", bans);
            try (Writer w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(data, w);
            }
        } catch (Exception e) {
            AdminMod.LOGGER.error("Failed to save ban data", e);
        }
    }

    /** 从 bans.json 加载 */
    private static void load() {
        if (configDir == null) return;
        try {
            File file = configDir.resolve("bans.json").toFile();
            if (!file.exists()) return;
            try (Reader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                Map<String, Object> data = GSON.fromJson(r,
                        new TypeToken<Map<String, Object>>(){}.getType());
                if (data != null && data.get("bans") instanceof Map<?, ?> raw) {
                    for (var entry : raw.entrySet()) {
                        if (entry.getValue() instanceof Map<?, ?> m) {
                            BanEntry be = new BanEntry(
                                    String.valueOf(m.get("reason")),
                                    m.get("expiry") instanceof Number n ? n.longValue() : 0);
                            bans.put(String.valueOf(entry.getKey()), be);
                        }
                    }
                }
            }
        } catch (Exception e) {
            AdminMod.LOGGER.error("Failed to load ban data", e);
        }
    }
}
