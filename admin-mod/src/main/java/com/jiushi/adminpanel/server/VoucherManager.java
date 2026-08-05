package com.jiushi.adminpanel.server;

import com.jiushi.adminpanel.AdminMod;
import com.jiushi.adminpanel.util.HashUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Map;

/**
 * 兑换券管理器
 * <p>
 * 允许玩家用金币生成可交易的纸质兑换券, 其他玩家右键使用兑换券来兑换金币.
 * 兑换券使用 SHA256 哈希防伪: voucher_code = SHA256(随机串+金额+"JiuShi"盐)
 * 数据持久化到 vouchers.json 用于服务端重启后券仍有效.
 */
public class VoucherManager {

    private static final Gson GSON = new Gson();
    /** 有效兑换券映射: 哈希 → 金额 */
    private static final Map<String, Integer> voucherMap = new java.util.concurrent.ConcurrentHashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static Path configDir;

    public static void init(Path path) {
        configDir = path;
        load();
    }

    /**
     * 创建兑换券
     * 流程: 扣金币 → 生成加密哈希 → 创建纸质物品(NBT含哈希和金额) → 放入背包
     */
    public static void createVoucher(ServerPlayer player, int amount) {
        if (amount <= 0) {
            player.sendSystemMessage(Component.literal("§c金额必须大于0"));
            return;
        }
        int current = MoneyManager.getMoney(player);
        if (current < amount) {
            player.sendSystemMessage(Component.literal("§c余额不足 (当前: " + current + " 币)"));
            return;
        }
        MoneyManager.takeMoney(player, amount);
        String codeId = generateCodeId();
        String rawCode = codeId + amount + "JiuShi";
        String hash = HashUtils.sha256(rawCode);

        voucherMap.put(hash, amount);
        save();

        // 创建纸质券物品
        ItemStack voucher = new ItemStack(Items.PAPER);
        voucher.setHoverName(Component.literal("§6兑换券 §e(" + amount + "币)"));
        CompoundTag tag = voucher.getOrCreateTag();
        tag.putString("voucher_code", hash);
        tag.putInt("voucher_amount", amount);

        if (!player.getInventory().add(voucher)) {
            player.drop(voucher, false); // 背包满则掉落
        }

        player.sendSystemMessage(Component.literal("§a已创建 " + amount + " 币的兑换券"));
    }

    /**
     * 兑换券使用 (右键纸触发)
     * 流程: 读取NBT中的哈希 → 查表验证 → 加金币 + 销毁券
     */
    public static void redeemVoucher(ServerPlayer player, ItemStack stack) {
        if (stack == null || !stack.hasTag()) return;

        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("voucher_code")) return;

        String hash = tag.getString("voucher_code");
        Integer amount = voucherMap.get(hash);

        if (amount == null) {
            player.sendSystemMessage(Component.literal("§c无效的兑换券"));
            return;
        }

        MoneyManager.addMoney(player, amount);
        voucherMap.remove(hash); // 使用后移除 (防止重复兑换)
        save();

        // 销毁1张券
        if (stack.getCount() > 1) {
            stack.shrink(1);
        } else {
            stack.setCount(0);
        }

        player.sendSystemMessage(Component.literal("§a成功兑换 " + amount + " 币"));
    }

    /** 生成唯一ID: CR + 24位随机数字 */
    private static String generateCodeId() {
        StringBuilder sb = new StringBuilder("CR");
        for (int i = 0; i < 24; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    /** 保存兑换券数据到 vouchers.json */
    private static synchronized void save() {
        if (configDir == null) return;
        try {
            File file = configDir.resolve("vouchers.json").toFile();
            file.getParentFile().mkdirs();
            try (Writer w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(voucherMap, w);
            }
        } catch (Exception e) {
            AdminMod.LOGGER.error("Failed to save vouchers", e);
        }
    }

    /** 从 vouchers.json 加载 */
    private static void load() {
        if (configDir == null) return;
        try {
            File file = configDir.resolve("vouchers.json").toFile();
            if (!file.exists()) return;
            try (Reader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                Map<String, Integer> data = GSON.fromJson(r,
                        new TypeToken<Map<String, Integer>>(){}.getType());
                if (data != null) voucherMap.putAll(data);
            }
        } catch (Exception e) {
            AdminMod.LOGGER.error("Failed to load vouchers", e);
        }
    }
}
