package com.jiushi.adminpanel.server;

import com.jiushi.adminpanel.AdminMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collections;

/**
 * 权限管理器
 * <p>
 * 为每个管理员提供细粒度权限控制.
 * 服主(owner)拥有全部权限, 不受权限检查限制.
 * 管理员(admin)默认无任何细粒度权限, 需服主通过 /admin perm 命令授予.
 * <p>
 * 数据结构: 玩家名 → (权限名 → true/false)
 * <p>
 * 数据持久化到 permissions.json
 */
public class PermissionManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** 权限数据: 玩家名 → (权限名 → 布尔值) */
    private static final Map<String, Map<String, Boolean>> permissions = new ConcurrentHashMap<>();
    private static Path configDir;

    public static void init(Path path) {
        configDir = path;
        load();
    }

    /**
     * 检查玩家是否有指定权限.
     * 服主直接返回 true.
     * 非管理员返回 false.
     */
    public static boolean can(String playerName, String perm) {
        if (!SetupManager.isAdmin(playerName)) return false;
        if (SetupManager.isOwner(playerName)) return true;
        Map<String, Boolean> perms = permissions.get(playerName);
        if (perms == null) return false;
        return perms.getOrDefault(perm, false);
    }

    /** 设置指定玩家的权限 */
    public static void setPermission(String playerName, String perm, boolean value) {
        permissions.computeIfAbsent(playerName, k -> new LinkedHashMap<>()).put(perm, value);
        save();
    }

    /** 移除玩家时清理其权限数据 */
    public static void removePlayer(String playerName) {
        permissions.remove(playerName);
        save();
    }

    /** 获取指定玩家的所有权限 */
    public static Map<String, Boolean> getPermissions(String playerName) {
        return permissions.getOrDefault(playerName, Collections.emptyMap());
    }

    /** 保存到 permissions.json */
    private static synchronized void save() {
        if (configDir == null) return;
        try {
            File file = configDir.resolve("permissions.json").toFile();
            file.getParentFile().mkdirs();
            try (BufferedWriter w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(permissions, w);
            }
        } catch (Exception e) {
            AdminMod.LOGGER.error("Failed to save permissions", e);
        }
    }

    /** 从 permissions.json 加载 */
    private static void load() {
        if (configDir == null) return;
        try {
            File file = configDir.resolve("permissions.json").toFile();
            if (!file.exists()) return;
            try (BufferedReader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                Map<String, Map<String, Boolean>> data = GSON.fromJson(r,
                    new TypeToken<Map<String, Map<String, Boolean>>>(){}.getType());
                if (data != null) permissions.putAll(data);
            }
        } catch (Exception e) {
            AdminMod.LOGGER.error("Failed to load permissions", e);
        }
    }
}