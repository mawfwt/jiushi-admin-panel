package com.jiushi.adminpanel.server;

import com.jiushi.adminpanel.AdminMod;
import com.jiushi.adminpanel.util.HashUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.*;

/**
 * 管理员注册/验证管理器
 * <p>
 * 管理员体系:
 * <ul>
 *   <li><b>owner</b> - 服主, 首次OP玩家自动晋升, 可生成邀请码/添加管理员/设置权限</li>
 *   <li><b>admin</b> - 管理员(OP), 通过邀请码激活, 拥有基本管理功能</li>
 * </ul>
 * <p>
 * 流程: 服主生成8位邀请码 → 分享给目标玩家 → 玩家在面板输入验证 → 晋升为管理员
 */
public class SetupManager {

    private static final Gson GSON = new Gson();
    /** 邀请码字符集 (Base32去除了易混淆字符: I, O, 1, 0) */
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    /** 邀请码长度 */
    private static final int CODE_LENGTH = 8;
    /** 邀请码有效期: 5分钟 */
    private static final long CODE_EXPIRY_MS = 300000;
    /** 验证码最大尝试次数 */
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    /** 封禁尝试窗口: 5分钟 */
    private static final long VERIFY_BLOCK_DURATION_MS = 300000;

    /** 管理员列表: 玩家名(原始大小写保留) → 角色类型 */
    private static final Map<String, String> admins = Collections.synchronizedMap(new LinkedHashMap<>());
    /** 待验证的邀请码: SHA256哈希 → CodeEntry(目标玩家+过期时间) */
    private static final Map<String, CodeEntry> pendingCodes = new java.util.concurrent.ConcurrentHashMap<>();
    /** 验证尝试记录: 玩家名(小写) → 尝试时间列表 (用于限流) */
    private static final Map<String, List<Long>> verifyAttempts = new java.util.concurrent.ConcurrentHashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();
    /** 配置文件目录路径 */
    private static Path configDir;

    /** 邀请码条目: 目标玩家名 + 过期时间 */
    public static class CodeEntry {
        public String target;
        public long expiry;

        public CodeEntry() {}
    }

    /** 初始化管理器并加载已有数据 */
    public static void init(Path path) {
        configDir = path;
        load();
    }

    /** 判断玩家是否为管理员 (任意等级) */
    public static boolean isAdmin(String playerName) {
        return containsPlayerIgnoreCase(admins, playerName);
    }

    /** 判断玩家是否拥有最高权限 */
    public static boolean isOwner(String playerName) {
        return "owner".equals(getRole(playerName)) || "developer".equals(getRole(playerName));
    }

    /** 大小写不敏感获取玩家角色, 未注册返回 null */
    public static String getRole(String playerName) {
        return getIgnoreCase(admins, playerName);
    }

    /** 是否已有管理员完成注册 (面板是否已初始化) */
    public static boolean isAdminVerified() {
        return !admins.isEmpty();
    }

    /**
     * 玩家加入时自动尝试添加为管理员.
     * 首次加入的OP玩家自动成为owner(服主), 其他OP自动成为admin
     */
    public static void tryAutoAdd(String playerName, boolean hasOp) {
        String role = null;
        synchronized (admins) {
            if (containsPlayerIgnoreCase(admins, playerName)) return;
            if (!hasOp) return;
            role = admins.isEmpty() ? "owner" : "admin";
            admins.put(playerName, role);
        }
        save();
        AdminMod.LOGGER.info("Auto-added {} as {}", playerName, role);
    }

    /**
     * 为指定玩家生成邀请码.
     * 邀请码 = 8位Base32随机字符, 存储其 SHA256(code + "JiuShi"盐) 用于验证
     */
    public static String invitePlayer(String targetName) {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        String code = sb.toString();
        String hash = HashUtils.sha256(code + "JiuShi");
        CodeEntry entry = new CodeEntry();
        entry.target = targetName;
        entry.expiry = System.currentTimeMillis() + CODE_EXPIRY_MS;
        pendingCodes.put(hash, entry);
        save();
        return code;
    }

    /** 服主直接添加管理员 (无需邀请码) */
    public static void directAddAdmin(String playerName, String addedBy) {
        if (!isOwner(addedBy)) return;
        if (isOwner(playerName)) return;
        synchronized (admins) {
            admins.put(playerName, "admin");
        }
        save();
        AdminMod.LOGGER.info("{} directly added {} as admin", addedBy, playerName);
    }

    /** 清理过期的邀请码 */
    public static void cleanupExpiredCodes() {
        long now = System.currentTimeMillis();
        pendingCodes.values().removeIf(e -> e.expiry < now);
    }

    /**
     * 验证玩家输入的邀请码
     *
     * @return 目标玩家名 → 验证成功; null → 验证失败; "already" → 已是管理员
     */
    public static String verifyInviteCode(String codeText, String playerName) {
        cleanupExpiredCodes();
        long now = System.currentTimeMillis();
        String playerKey = playerName.toLowerCase(Locale.ROOT);

        List<Long> attempts = verifyAttempts.computeIfAbsent(playerKey, k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (attempts) {
            attempts.removeIf(t -> t < now - VERIFY_BLOCK_DURATION_MS);
            if (attempts.size() >= MAX_VERIFY_ATTEMPTS) {
                return null;
            }

            synchronized (admins) {
                if (containsPlayerIgnoreCase(admins, playerName)) {
                    verifyAttempts.remove(playerKey);
                    return "already";
                }
            }

            String hash = HashUtils.sha256(codeText + "JiuShi");

            CodeEntry entry = pendingCodes.get(hash);
            if (entry == null) {
                attempts.add(now);
                return null;
            }
            if (!entry.target.equalsIgnoreCase(playerName)) {
                attempts.add(now);
                return null;
            }
            if (entry.expiry < now) {
                pendingCodes.remove(hash);
                attempts.add(now);
                return null;
            }
            pendingCodes.remove(hash);
            synchronized (admins) {
                if (containsPlayerIgnoreCase(admins, playerName)) {
                    verifyAttempts.remove(playerKey);
                    return "already";
                }
                admins.put(playerName, "admin");
            }
            verifyAttempts.remove(playerKey);
            save();
            return playerName;
        }
    }

    /** 移除管理员 (只有服主可操作) */
    public static boolean removeAdmin(String name, String byWho) {
        if (isOwner(name)) return false;
        if (!isOwner(byWho)) return false;
        boolean removed;
        synchronized (admins) {
            removed = removeIgnoreCase(admins, name);
        }
        if (removed) {
            PermissionManager.removePlayer(name);
            save();
        }
        return removed;
    }

    /** 获取管理员列表快照 (副本, 避免并发遍历 CME) */
    public static Map<String, String> getAdmins() {
        synchronized (admins) {
            return new LinkedHashMap<>(admins);
        }
    }

    /** 大小写不敏感判断Map中是否存在key */
    private static boolean containsPlayerIgnoreCase(Map<String, String> map, String name) {
        if (name == null) return false;
        synchronized (map) {
            for (String key : map.keySet()) {
                if (key.equalsIgnoreCase(name)) return true;
            }
        }
        return false;
    }

    /** 大小写不敏感获取Map中的值 */
    private static String getIgnoreCase(Map<String, String> map, String name) {
        if (name == null) return null;
        synchronized (map) {
            for (var e : map.entrySet()) {
                if (e.getKey().equalsIgnoreCase(name)) return e.getValue();
            }
        }
        return null;
    }

    /** 大小写不敏感从Map中移除 */
    private static boolean removeIgnoreCase(Map<String, String> map, String name) {
        if (name == null) return false;
        synchronized (map) {
            for (var it = map.keySet().iterator(); it.hasNext(); ) {
                String key = it.next();
                if (key.equalsIgnoreCase(name)) {
                    it.remove();
                    return true;
                }
            }
        }
        return false;
    }

    /** 保存管理员和邀请码数据到 setup.json */
    private static synchronized void save() {
        if (configDir == null) return;
        try {
            File dir = configDir.toFile();
            if (!dir.exists() && !dir.mkdirs()) {
                AdminMod.LOGGER.error("Failed to create config dir: {}", dir.getAbsolutePath());
                return;
            }
            File file = configDir.resolve("setup.json").toFile();
            Map<String, Object> data = new HashMap<>();
            synchronized (admins) {
                data.put("admins", new LinkedHashMap<>(admins));
            }
            Map<String, Map<String, Object>> codes = new HashMap<>();
            for (var e : pendingCodes.entrySet()) {
                Map<String, Object> ce = new HashMap<>();
                ce.put("target", e.getValue().target);
                ce.put("expiry", e.getValue().expiry);
                codes.put(e.getKey(), ce);
            }
            data.put("pendingCodes", codes);
            try (Writer w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(data, w);
            }
            AdminMod.LOGGER.info("Saved setup: {} admins to {}", admins.size(), file.getAbsolutePath());
        } catch (Exception e) {
            AdminMod.LOGGER.error("Failed to save setup", e);
        }
    }

    /** 从 setup.json 加载管理员和邀请码数据 */
    private static void load() {
        if (configDir == null) return;
        try {
            File file = configDir.resolve("setup.json").toFile();
            if (!file.exists()) {
                AdminMod.LOGGER.info("No setup.json found at {}", file.getAbsolutePath());
                return;
            }
            try (Reader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                Map<String, Object> data = GSON.fromJson(r,
                        new TypeToken<Map<String, Object>>(){}.getType());
                if (data.containsKey("admins")) {
                    Map<String, Object> am = (Map<String, Object>) data.get("admins");
                    if (am != null) {
                        synchronized (admins) {
                            admins.clear();
                            for (var e : am.entrySet()) {
                                admins.put(e.getKey(), String.valueOf(e.getValue()));
                            }
                        }
                    }
                    AdminMod.LOGGER.info("Loaded {} admins", admins.size());
                } else {
                    AdminMod.LOGGER.info("Old format setup.json, deleting and starting fresh");
                    try { file.delete(); } catch (Exception ignored) {}
                }
                if (data.containsKey("pendingCodes")) {
                    pendingCodes.clear();
                    Map<String, Object> pc = (Map<String, Object>) data.get("pendingCodes");
                    if (pc != null) {
                        for (var e : pc.entrySet()) {
                            Map<String, Object> ce = (Map<String, Object>) e.getValue();
                            CodeEntry entry = new CodeEntry();
                            entry.target = String.valueOf(ce.get("target"));
                            Object exp = ce.get("expiry");
                            if (exp instanceof Number) {
                                entry.expiry = ((Number) exp).longValue();
                            } else {
                                continue;
                            }
                            pendingCodes.put(e.getKey(), entry);
                        }
                    }
                }
            }
        } catch (Exception e) {
            AdminMod.LOGGER.error("Failed to load setup", e);
        }
    }
}