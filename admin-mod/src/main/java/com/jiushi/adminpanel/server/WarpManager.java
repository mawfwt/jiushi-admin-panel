package com.jiushi.adminpanel.server;

import com.jiushi.adminpanel.AdminMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 传送点管理器
 * <p>
 * 传送点有三种可见性级别:
 * <ul>
 *   <li>PRIVATE(0) - 私人, 仅创建者可见</li>
 *   <li>PUBLIC(1) - 公开, 所有玩家可见可传送</li>
 *   <li>OFFICIAL(2) - 官方, 仅管理员可创建, 所有玩家可见</li>
 * </ul>
 * <p>
 * 数据持久化到 warps.json.
 * 传送点支持跨维度传送.
 */
public class WarpManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** 传送点映射: 名称 → 传送点数据 (线程安全) */
    private static final Map<String, WarpPoint> warps = Collections.synchronizedMap(new LinkedHashMap<>());
    /** 配置文件目录 */
    private static Path configDir;

    /** 可见性常量 */
    public static final int PRIVATE = 0;
    public static final int PUBLIC = 1;
    public static final int OFFICIAL = 2;

    /** 传送点数据类: 包含位置/维度/朝向/可见性/创建者 */
    public static class WarpPoint {
        public String name;
        public String dimension;    // 维度 ResourceLocation 字符串
        public double x, y, z;
        public float yaw, pitch;
        public int visibility;
        public String owner;        // 创建者玩家名

        public WarpPoint() {}

        /** 从玩家当前位置创建传送点 */
        public WarpPoint(String name, ServerPlayer player, int visibility) {
            this.name = name;
            this.dimension = player.level().dimension().location().toString();
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
            this.yaw = player.getYRot();
            this.pitch = player.getXRot();
            this.visibility = visibility;
            this.owner = player.getName().getString();
        }
    }

    /** 初始化并加载已有传送点数据 */
    public static void init(Path path) {
        configDir = path;
        load();
    }

    /**
     * 获取玩家可见的传送点列表.
     * 过滤规则: 官方/公开 → 所有人可见; 私人 → 仅创建者可见.
     * 排序: 按可见性降序 (官方优先) 再按名称字母排序
     */
    public static Map<String, WarpPoint> getAllWarps(ServerPlayer viewer) {
        synchronized (warps) {
            return warps.entrySet().stream()
                .filter(e -> {
                    int v = e.getValue().visibility;
                    String owner = e.getValue().owner;
                    String viewerName = viewer.getName().getString();
                    if (v == OFFICIAL || v == PUBLIC) return true;
                    return owner != null && owner.equals(viewerName);
                })
                .sorted((a, b) -> {
                    int va = a.getValue().visibility;
                    int vb = b.getValue().visibility;
                    if (va != vb) return Integer.compare(vb, va); // 高可见性排前
                    return a.getKey().compareTo(b.getKey());
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (x, y) -> x, LinkedHashMap::new));
        }
    }

    /** 获取全部传送点快照 (副本, 避免并发遍历 CME) */
    public static Map<String, WarpPoint> getRawWarps() {
        synchronized (warps) {
            return new LinkedHashMap<>(warps);
        }
    }

    /** 设置传送点. 仅允许管理员设置官方/公开传送点 */
    public static void setWarp(String name, ServerPlayer player, int visibility) {
        if (visibility == OFFICIAL || visibility == PUBLIC) {
            if (!SetupManager.isAdmin(player.getName().getString())) {
                return; // 非管理员不能设置官方/公开传送点
            }
        }
        warps.put(name, new WarpPoint(name, player, visibility));
        save();
    }

    /** 删除指定传送点 */
    public static void removeWarp(String name) {
        warps.remove(name);
        save();
    }

    /** 获取指定传送点数据 */
    public static WarpPoint getWarp(String name) {
        return warps.get(name);
    }

    /** 保存到 warps.json */
    private static synchronized void save() {
        if (configDir == null) return;
        try {
            File file = configDir.resolve("warps.json").toFile();
            file.getParentFile().mkdirs();
            try (Writer w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(warps, w);
            }
        } catch (Exception e) {
            AdminMod.LOGGER.error("Failed to save warps", e);
        }
    }

    /** 从 warps.json 加载 */
    private static void load() {
        if (configDir == null) return;
        try {
            File file = configDir.resolve("warps.json").toFile();
            if (!file.exists()) return;
            try (Reader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                Map<String, WarpPoint> data = GSON.fromJson(r,
                        new com.google.gson.reflect.TypeToken<Map<String, WarpPoint>>(){}.getType());
                if (data != null) warps.putAll(data);
            }
        } catch (Exception e) {
            AdminMod.LOGGER.error("Failed to load warps", e);
        }
    }
}