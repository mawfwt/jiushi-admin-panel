package com.jiushi.territory.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 领地数据管理器
 * <p>
 * 领地规则:
 * <ul>
 *   <li>私人领地: XZ轴差总和不超 {@value #MAX_SIZE}, 每人上限 {@value #MAX_PERSONAL} 个</li>
 *   <li>官方领地: 仅OP可创建, 无尺寸/数量限制</li>
 * </ul>
 * <p>
 * 权限判定:
 * <ul>
 *   <li>拥有者(owner) → 全部权限</li>
 *   <li>白名单(allowed) → 破坏/放置/交互权限</li>
 *   <li>服主/开发者(SetupManager.isOwner) → 所有领地全部权限</li>
 *   <li>闯入者 → 无权限, 官方领地强制冒险模式</li>
 * </ul>
 * <p>
 * 数据持久化到 territories.json
 */
public class TerritoryManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TerritoryManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** 领地映射: ID字符串 → 领地数据 (线程安全) */
    private static final Map<String, Territory> territories = new ConcurrentHashMap<>();
    /** 个人领地上限 */
    private static final int MAX_PERSONAL = 2;
    /** 个人领地最大 XZ 差总和 */
    private static final int MAX_SIZE = 128;
    /** 配置文件目录 */
    private static Path configDir;
    /** 自增领地ID */
    private static final AtomicInteger nextId = new AtomicInteger(1);

    /** 领地实体类 */
    public static class Territory {
        public int id;
        public String name;
        public String owner;        // 所有者玩家名
        public String world;        // 所属世界 ResourceLocation
        public int x1, z1, x2, z2; // 选区对角坐标 (仅XZ, Y不限)
        public boolean official;    // 是否为官方领地
        public Set<String> allowed = ConcurrentHashMap.newKeySet(); // 权限白名单

        /** 判断坐标是否在此领地范围内 (仅XZ平面) */
        public boolean contains(String worldCheck, BlockPos pos) {
            if (!this.world.equals(worldCheck)) return false;
            int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
            int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
            return pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }

        /** 计算领地 XZ 轴差总和 */
        public int sizeXZ() {
            return Math.abs(x2 - x1) + Math.abs(z2 - z1);
        }
    }

    /** 初始化并加载已有领地数据 */
    public static void init() {
        configDir = FMLPaths.CONFIGDIR.get().resolve("jiushi_admin");
        load();
    }

    /**
     * 创建领地
     * @return null=成功, 否则返回错误消息字符串
     */
    public static synchronized String createTerritory(String name, ServerPlayer player,
                                          BlockPos start, BlockPos end, boolean official) {
        String owner = player.getGameProfile().getName();
        // 唯一名称检查
        if (getTerritoryByName(name) != null) return "§c领地名称已存在";
        if (official) {
            // 服务端权限校验: 仅OP或服主/开发者可创建官方领地 (防伪造包绕过, 客户端隐藏按钮不可信)
            boolean isOp = player.getServer().getPlayerList().isOp(player.getGameProfile());
            if (!isOp && !com.jiushi.adminpanel.server.SetupManager.isOwner(player.getGameProfile().getName())) {
                return "§c你没有权限创建官方领地";
            }
        } else {
            // 尺寸限制
            int size = Math.abs(end.getX() - start.getX()) + Math.abs(end.getZ() - start.getZ());
            if (size > MAX_SIZE) return "§c领地XZ差异总和不能超过 " + MAX_SIZE;
            // 数量限制
            int count = 0;
            for (Territory t : territories.values()) {
                if (t.owner.equalsIgnoreCase(owner) && !t.official) count++;
            }
            if (count >= MAX_PERSONAL) return "§c个人领地数量已达上限 (" + MAX_PERSONAL + ")";
        }
        // 重叠检测
        for (Territory t : territories.values()) {
            if (t.world.equals(player.level().dimension().location().toString())) {
                if (overlaps(t, start.getX(), start.getZ(), end.getX(), end.getZ())) {
                    return "§c领地与 " + t.name + " 重叠";
                }
            }
        }
        Territory t = new Territory();
        t.id = nextId.getAndIncrement();
        t.name = name;
        t.owner = owner;
        t.world = player.level().dimension().location().toString();
        t.x1 = start.getX(); t.z1 = start.getZ();
        t.x2 = end.getX(); t.z2 = end.getZ();
        t.official = official;
        territories.put(String.valueOf(t.id), t);
        save();
        return null;
    }

    /** 删除领地 (仅owner或服主可删除) */
    public static String deleteTerritory(int id, ServerPlayer player) {
        Territory t = territories.get(String.valueOf(id));
        if (t == null) return "§c领地不存在";
        String name = player.getGameProfile().getName();
        boolean isAdmin = t.owner.equalsIgnoreCase(name)
                || com.jiushi.adminpanel.server.SetupManager.isOwner(name);
        if (!isAdmin) return "§c你没有权限删除此领地";
        territories.remove(String.valueOf(id));
        save();
        return null;
    }

    /**
     * 判断玩家在指定位置是否有交互权限.
     * 优先级: 不在领地内=true → 服主/开发者=true → owner=true → 白名单=true → 否则false
     */
    public static boolean canInteract(ServerPlayer player, BlockPos pos) {
        Territory found = getTerritoryAt(player.level().dimension().location().toString(), pos);
        if (found == null) return true; // 不在任何领地 → 有权限
        String name = player.getGameProfile().getName();
        if (com.jiushi.adminpanel.server.SetupManager.isOwner(name)) return true;
        if (found.owner.equalsIgnoreCase(name)) return true;
        if (allowedContains(found.allowed, name)) return true;
        return false;
    }

    /** 获取指定世界坐标所在领地 (没有返回null) */
    public static Territory getTerritoryAt(String world, BlockPos pos) {
        for (Territory t : territories.values()) {
            if (t.contains(world, pos)) return t;
        }
        return null;
    }

    /** 按名称查找领地 (不区分大小写) */
    public static Territory getTerritoryByName(String name) {
        for (Territory t : territories.values()) {
            if (t.name.equalsIgnoreCase(name)) return t;
        }
        return null;
    }

    /** 获取所有领地 (只读) */
    public static Collection<Territory> getAllTerritories() {
        return Collections.unmodifiableCollection(territories.values());
    }

    /** 添加玩家到领地白名单 */
    public static void addAllowed(int id, String name) {
        Territory t = territories.get(String.valueOf(id));
        if (t != null) { t.allowed.add(name); save(); }
    }

    /** 从领地白名单移除玩家 */
    public static void removeAllowed(int id, String name) {
        Territory t = territories.get(String.valueOf(id));
        if (t != null) { t.allowed.remove(name); save(); }
    }

    /** 大小写不敏感判断白名单是否包含指定玩家 */
    private static boolean allowedContains(Set<String> allowed, String name) {
        for (String a : allowed) {
            if (a.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /** AABB矩形重叠检测 (仅XZ平面) */
    public static boolean overlaps(Territory a, int x1, int z1, int x2, int z2) {
        int aMinX = Math.min(a.x1, a.x2), aMaxX = Math.max(a.x1, a.x2);
        int aMinZ = Math.min(a.z1, a.z2), aMaxZ = Math.max(a.z1, a.z2);
        int bMinX = Math.min(x1, x2), bMaxX = Math.max(x1, x2);
        int bMinZ = Math.min(z1, z2), bMaxZ = Math.max(z1, z2);
        return !(aMaxX < bMinX || aMinX > bMaxX || aMaxZ < bMinZ || aMinZ > bMaxZ);
    }

    /** 保存到 territories.json */
    private static void save() {
        if (configDir == null) return;
        try {
            File file = configDir.resolve("territories.json").toFile();
            file.getParentFile().mkdirs();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("nextId", nextId.get());
            data.put("territories", new ArrayList<>(territories.values()));
            try (Writer w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(data, w);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save territory data", e);
        }
    }

    /** 从 territories.json 加载 */
    private static void load() {
        if (configDir == null) return;
        try {
            File file = configDir.resolve("territories.json").toFile();
            if (!file.exists()) return;
            try (Reader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                Map<String, Object> data = GSON.fromJson(r,
                        new TypeToken<Map<String, Object>>() {}.getType());
                if (data.containsKey("nextId")) {
                    Object nid = data.get("nextId");
                    if (nid instanceof Number) {
                        nextId.set(((Number) nid).intValue());
                    }
                }
                if (data.containsKey("territories")) {
                    String json = GSON.toJson(data.get("territories"));
                    Territory[] arr = GSON.fromJson(json, Territory[].class);
                    if (arr != null) {
                        for (Territory t : arr) {
                            // 反序列化后确保allowed集合不为null
                            if (t.allowed == null) t.allowed = ConcurrentHashMap.newKeySet();
                            else {
                                Set<String> safe = ConcurrentHashMap.newKeySet();
                                safe.addAll(t.allowed);
                                t.allowed = safe;
                            }
                            territories.put(String.valueOf(t.id), t);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load territory data", e);
        }
    }
}