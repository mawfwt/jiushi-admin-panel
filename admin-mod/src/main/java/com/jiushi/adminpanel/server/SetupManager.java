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
 * 三级管理员体系:
 * <ul>
 *   <li><b>developer</b> - 面板开发者, 通过硬编码master key验证, 拥有服主权限</li>
 *   <li><b>owner</b> - 服主, 首次OP玩家自动晋升, 可生成邀请码/添加管理员/设置权限</li>
 *   <li><b>admin</b> - 管理员(OP), 通过邀请码激活, 拥有基本管理功能</li>
 * </ul>
 * <p>
 * 流程: 服主生成8位邀请码 → 分享给目标玩家 → 玩家在面板输入验证 → 晋升为管理员
 */
public class SetupManager {

    private static final Gson GSON = new Gson();
    /** 硬编码的开发者密钥 SHA256 哈希 (原文: ??? + "JiuShi" 盐) */
    private static final String MASTER_KEY_HASH = "d6b81d28afd95de58f4b6f21b1b5e79f96df44853f1e865c7307731f0d81c543";
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

    /** 管理员列表: 玩家名 → 角色类型 (owner/admin/developer) */
    private static final Map<String, String> admins = Collections.synchronizedMap(new LinkedHashMap<>());
    /** 待验证的邀请码: SHA256哈希 → CodeEntry(目标玩家+过期时间) */
    private static final Map<String, CodeEntry> pendingCodes = new java.util.concurrent.ConcurrentHashMap<>();
    /** 验证尝试记录: 玩家名 → 尝试时间列表 (用于限流) */
    private static final Map<String, List<Long>> verifyAttempts = new java.util.concurrent.ConcurrentHashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();
    /** 配置文件目录路径 */
    private static Path configDir;

    /** 邀请码条目: 目标玩家名 + 过期时间 */
    public static class CodeEntry {
        public String target;
        public long expiry;
    }

    /** 初始化管理器并加载已有数据 */
    public static void init(Path path) {
        configDir = path;
        load();
    }

    /** 判断玩家是否为管理员 (任意等级) */
    public static boolean isAdmin(String playerName) {
        return admins.containsKey(playerName);
    }

    /** 判断玩家是否为服主/开发者 (拥有最高权限) */
    public static boolean isOwner(String playerName) {
        String role = admins.get(playerName);
        return "owner".equals(role) || "developer".equals(role);
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
        if (admins.containsKey(playerName)) return;
        if (!hasOp) return;
        // 第一个OP自动成为服主
        String role = admins.isEmpty() ? "owner" : "admin";
        admins.put(playerName, role);
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
        // 哈希存储, 不存明文
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
        admins.put(playerName, "admin");
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

        // 检查是否为开发者密钥
        String hash = HashUtils.sha256(codeText + "JiuShi");
        if (MASTER_KEY_HASH.equals(hash)) {
            admins.put(playerName, "developer");
            verifyAttempts.remove(playerName);
            save();
            AdminMod.LOGGER.info("Master key used by {}", playerName);
            return playerName;
        }

        // 限流: 5分钟窗口内最多5次尝试验证
        List<Long> attempts = verifyAttempts.computeIfAbsent(playerName, k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (attempts) {
            attempts.removeIf(t -> t < now - VERIFY_BLOCK_DURATION_MS);
            if (attempts.size() >= MAX_VERIFY_ATTEMPTS) {
                return null; // 被限流
            }

            // 尝试匹配邀请码
            CodeEntry entry = pendingCodes.get(hash);
            if (entry == null) {
                attempts.add(now);
                return null; // 验证码无效或已过期
            }
            // 验证目标玩家是否匹配 (不区分大小写)
            if (!entry.target.equalsIgnoreCase(playerName)) {
                attempts.add(now);
                return null; // 目标不匹配
            }
            // 验证成功才移除邀请码
            pendingCodes.remove(hash);
            if (admins.containsKey(playerName)) return "already";
            admins.put(playerName, "admin");
            verifyAttempts.remove(playerName); // 成功后清除限流记录
            save();
            return entry.target;
        }
    }

    /** 移除管理员 (只有服主可操作, 服主/开发者不可移除) */
    public static boolean removeAdmin(String name, String byWho) {
        if (isOwner(name)) return false;
        if (!isOwner(byWho)) return false;
        admins.remove(name);
        PermissionManager.removePlayer(name); // 同步清除权限
        save();
        return true;
    }

    /** 获取管理员列表快照 (副本, 避免并发遍历 CME) */
    public static Map<String, String> getAdmins() {
        synchronized (admins) {
            return new LinkedHashMap<>(admins);
        }
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
            data.put("admins", admins);
            // 序列化待验证邀请码
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
                    admins.clear();
                    Map<String, Object> am = (Map<String, Object>) data.get("admins");
                    if (am != null) {
                        for (var e : am.entrySet()) {
                            admins.put(e.getKey(), String.valueOf(e.getValue()));
                        }
                    }
                    AdminMod.LOGGER.info("Loaded {} admins", admins.size());
                } else {
                    // 旧版本数据格式 → 删除重建
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
                            entry.expiry = ((Number) exp).longValue();
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