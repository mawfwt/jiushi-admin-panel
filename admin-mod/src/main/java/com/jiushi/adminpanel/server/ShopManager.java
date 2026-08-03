package com.jiushi.adminpanel.server;

import com.jiushi.adminpanel.AdminMod;
import com.jiushi.adminpanel.server.MoneyManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 玩家商店管理器
 * <p>
 * 管理商品的存储/查询/交易/下架. 商品数据结构包含: 卖家名/NBT序列化的物品/价格/数量.
 * 数据持久化到 shop.json, 使用自增ID确保商品唯一标识.
 */
public class ShopManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** 商品列表 (线程安全) */
    private static final List<ShopListing> listings = Collections.synchronizedList(new ArrayList<>());
    /** 自增商品ID */
    private static final AtomicInteger nextId = new AtomicInteger(1);
    /** 配置文件目录 */
    private static Path configDir;

    /** 购买结果枚举 */
    public enum PurchaseResult {
        SUCCESS,            // 购买成功
        SOLD_OUT,           // 已售罄
        NOT_FOUND,          // 商品不存在
        INSUFFICIENT_FUNDS, // 余额不足
        DATA_ERROR          // NBT数据错误
    }

    /** 商品条目: 包含物品NBT序列化, 用于持久化存储 */
    public static class ShopListing {
        public int id;              // 唯一ID
        public String sellerName;   // 卖家名
        public String itemName;     // 物品显示名
        public String itemNbt;      // 物品NBT序列化字符串
        public int price;           // 售价
        public int quantity;        // 数量

        public ShopListing() {}

        /** 从实际物品创建商品条目, 序列化NBT并固定单件数量 */
        public ShopListing(int id, String sellerName, ItemStack stack, int price) {
            this.id = id;
            this.sellerName = sellerName;
            this.itemName = stack.getDisplayName().getString();
            net.minecraft.nbt.CompoundTag tag = stack.serializeNBT();
            tag.putInt("Count", 1); // 单件存储
            this.itemNbt = tag.toString();
            this.price = price;
            this.quantity = stack.getCount();
        }
    }

    /** 初始化管理器并加载已有数据 */
    public static void init(Path path) {
        configDir = path;
        load();
    }

    /** 获取商品列表快照 (副本, 避免并发遍历 CME) */
    public static List<ShopListing> getAllListings() {
        synchronized (listings) {
            return new ArrayList<>(listings);
        }
    }

    /** 上架物品: 验证物品非空 + 价格>0 */
    public static boolean addListing(ServerPlayer seller, ItemStack stack, int price) {
        if (stack.isEmpty()) return false;
        if (price <= 0) return false;
        String sellerName = seller.getName().getString();
        if (sellerName == null || sellerName.isEmpty()) return false;
        listings.add(new ShopListing(nextId.getAndIncrement(), sellerName, stack, price));
        save();
        return true;
    }

    /** 按ID查找商品 */
    public static ShopListing getListing(int id) {
        synchronized (listings) {
            return listings.stream().filter(l -> l.id == id).findFirst().orElse(null);
        }
    }

    /** 下架商品 (移除后保存) */
    public static boolean removeListing(int id) {
        boolean removed = listings.removeIf(l -> l.id == id);
        if (removed) save();
        return removed;
    }

    /**
     * 购买商品.
     * 流程: 查找商品 → 检查库存/余额 → 扣款+转账给卖家 → 还原NBT为物品 → 放入背包 → 减库存
     */
    public static PurchaseResult purchaseItem(ServerPlayer buyer, int listingId) {
        synchronized (listings) {
            ShopListing listing = getListing(listingId);
            if (listing == null) return PurchaseResult.NOT_FOUND;
            if (listing.quantity <= 0 || listing.price <= 0) return PurchaseResult.SOLD_OUT;

            int balance = MoneyManager.getMoney(buyer);
            if (balance < listing.price) return PurchaseResult.INSUFFICIENT_FUNDS;

            try {
                // 从NBT还原物品
                net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.TagParser.parseTag(listing.itemNbt);
                ItemStack stack = ItemStack.of(tag);
                // 尝试放入背包, 满则掉落在地
                if (!buyer.getInventory().add(stack)) {
                    buyer.drop(stack, false);
                }
            } catch (Exception e) {
                AdminMod.LOGGER.error("Failed to parse item NBT for listing {}", listingId, e);
                return PurchaseResult.DATA_ERROR;
            }

            // 扣钱 → 转账给卖家
            MoneyManager.takeMoney(buyer, listing.price);
            MoneyManager.addMoneyByName(buyer.getServer(), listing.sellerName, listing.price);

            // 减库存, 售罄则移除
            listing.quantity--;
            if (listing.quantity <= 0) {
                listings.remove(listing);
            }
            save();
            return PurchaseResult.SUCCESS;
        }
    }

    /** 保存商品数据到 shop.json */
    private static synchronized void save() {
        if (configDir == null) return;
        try {
            File file = configDir.resolve("shop.json").toFile();
            file.getParentFile().mkdirs();
            Map<String, Object> data = new HashMap<>();
            data.put("nextId", nextId.get());
            data.put("listings", listings);
            try (Writer w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(data, w);
            }
        } catch (Exception e) {
            AdminMod.LOGGER.error("Failed to save shop data", e);
        }
    }

    /** 从 shop.json 加载商品数据 */
    private static void load() {
        if (configDir == null) return;
        try {
            File file = configDir.resolve("shop.json").toFile();
            if (!file.exists()) return;
            try (Reader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                Map<String, Object> data = GSON.fromJson(r,
                        new TypeToken<Map<String, Object>>(){}.getType());
                if (data.containsKey("nextId")) {
                    Object nextIdObj = data.get("nextId");
                    if (nextIdObj instanceof Double) {
                        nextId.set(((Double) nextIdObj).intValue());
                    } else if (nextIdObj instanceof Number) {
                        nextId.set(((Number) nextIdObj).intValue());
                    }
                }
                if (data.containsKey("listings")) {
                    String json = GSON.toJson(data.get("listings"));
                    ShopListing[] arr = GSON.fromJson(json, ShopListing[].class);
                    listings.clear();
                    if (arr != null) Collections.addAll(listings, arr);
                }
            }
        } catch (Exception e) {
            AdminMod.LOGGER.error("Failed to load shop data", e);
        }
    }
}
